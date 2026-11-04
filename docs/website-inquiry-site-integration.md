# Trendz Green — 官网询盘独立站点隔离接入（T2）

本文档描述 ERP 侧为 Trendz Green 等新品牌提供的**独立站点隔离**询盘入口，以及
T1（官网 Worker）与 T2（ERP）之间的上游协议。旧 VANZ 入口保持不变。

## 1. 端点

- 旧入口（保留，不动）：`POST /app-api/system/website-inquiry/notify`
  - 头：`X-Vanz-Inquiry-Secret`，租户锁定 162（VANZ），由 `WebsiteInquiryProperties` 强制。
- 新站点隔离入口：`POST /app-api/system/website-inquiry/site-notify`
  - 头：`X-Site-Inquiry-Site`、`X-Site-Inquiry-Secret`、`tenant-id`。

> 路由前缀说明：ERP 使用 `yudao.web.app-api.prefix = /app-api` 为
> `**.controller.app.**` 包自动加前缀。Controller 上的
> `@RequestMapping("/system/website-inquiry")` 实际对外路径为
> `/app-api/system/website-inquiry/**`（经网关 `Path=/app-api/system/**` 透传）。
> T1 Worker 的 `INQUIRY_UPSTREAM_URL` 需指向完整 `/app-api/...` 路径（启用投递时核对）。

## 2. 请求契约

```
POST /app-api/system/website-inquiry/site-notify
Headers:
  Content-Type: application/json
  X-Site-Inquiry-Site:   <站点键，服务端解析，例如 trendzgreen>
  X-Site-Inquiry-Secret: <站点共享密钥，服务端专属>
  tenant-id:             <站点服务端解析出的租户编号>
Body (JSON):
  externalInquiryId  必填  64
  name               必填  60
  email              必填  120
  companyName              80
  subject            必填  100
  message                  4000（多行保留）
  category                 60
  inquiryType        必填  quote | samples | custom | catalog
  sourcePage               255
  locale                   32
  utmSource / utmMedium / utmCampaign  各 100
```

站点键、共享密钥与租户编号都只通过**请求头 + 服务端配置**解析；请求体无法选择
站点或租户（未知 JSON 字段会被 Jackson 忽略）。

## 3. 响应契约

- 成功（已入库）：`HTTP 200 { "code": 0, "data": <inquiryId>, "msg": "" }`
- 业务失败：`HTTP 200 { "code": <非 0>, "msg": "..." }`（`ServiceException` → 非 0 code）
- 参数校验失败：`HTTP 200 { "code": 400, "msg": "..." }`

入库是唯一成功判定；站内信与邮件都是入库后的旁路，失败不回滚、不改变成功状态，
且绝不向客户端承诺邮件已发送。

## 4. 安全与隔离模型

按顺序校验，任一失败都在**入库/通知之前**返回非 0：

1. 站点键解析：未知/缺失站点 → 鉴权失败。
2. 未启用站点 → 未启用错误。
3. 保留租户守卫：站点配置的租户不得为 162（VANZ）/121（Oakved），启动校验也拒绝。
4. 密钥比较：`MessageDigest.isEqual` 恒定时间比较，错误/跨站点密钥 → 鉴权失败。
5. 租户上下文：`tenant-id` 头（框架 `TenantContextWebFilter` → `TenantContextHolder`）
   必须等于站点服务端解析出的租户；伪造/跨租户 → 鉴权失败。
6. 幂等：同一站点租户 + 同一 `externalInquiryId` 重复提交返回原记录
   （DB 唯一键 `uk_crm_clue_tenant_external (tenant_id, external_inquiry_id, deleted)`），
   不产生第二条记录；跨租户请求因租户拦截器隔离，无法读到其他租户记录。
7. 站点密钥复用：两个启用站点不得共用同一密钥，且站点密钥不得复用旧 VANZ 密钥
   （启动校验拒绝）。
8. 站点站内信模板不得复用旧 VANZ 模板（`vanz_website_inquiry`）；缺少独立模板时
   通知旁路安全关闭（不触发通知），启动校验拒绝复用 VANZ 模板。
9. 一个启用站点映射一个租户：两个启用站点不得映射同一租户（启动校验拒绝），
   避免同一 `externalInquiryId` 在同一租户内跨站点串单。

## 5. 配置示例（默认全禁用，占位符无秘密）

```yaml
yudao:
  website-inquiry:
    # 旧 VANZ 配置（保留，不在此展开）
    # ...
    # 新站点隔离配置：默认空 = 全禁用
    sites:
      trendzgreen:
        enabled: false                 # 默认关闭，真实租户/密钥未建立
        tenant-id: null                # 未建立；留空，仅翻转 enabled 也会安全失败
        shared-secret: ""              # 未建立；留空（<24 字符），仅翻转 enabled 也会安全失败
        notify-enabled: false          # 站内信默认关闭
        mail-enabled: false            # 邮件默认关闭（避免后台重试意外开启）
        template-code: ""              # 无默认值，禁止回落到 vanz_website_inquiry
```

启用投递需要**单独的未来授权**：创建真实 Trendz Green 租户、配置真实密钥与站点
映射，并显式把 `enabled` 置为 true。本任务（T2）只交付代码与隔离测试，不建租户、
不写生产配置、不发真实邮件。启动校验要求启用站点 `tenant-id` 为正且非保留租户、
`shared-secret` ≥24 字符，因此示例的空配置即便只把 `enabled` 翻成 true 也会安全失败。

## 6. 持久化与字段表示

- `category` 与 `inquiryType` 通过 CRM 持久化到 `crm_clue.category` /
  `crm_clue.inquiry_type`（迁移源文件
  `sql/mysql/migrations/V054__website_inquiry_category_type.sql`）。
- 迁移机制（已核实）：`yudao-server/pom.xml` 资源配置把
  `sql/mysql/migrations/V*.sql` 打进 `yudao-server.jar` 的 `db/migration`，后端启动时
  Flyway（`flyway-core`/`flyway-mysql`）读取并应用尚未成功的版本。**本任务未执行该迁移、
  未访问生产数据库**；部署前须确认 Flyway 已应用 V054（即 `crm_clue` 已含
  `category`/`inquiry_type` 列）。新列为 `NOT NULL DEFAULT ''`，历史行自动获得空默认值，
  兼容旧路径与既有数据。
- 多行 `message` 经 `normalizeMultiline` 保留换行（`\r\n`/`\r` 归一到 `\n`），
  完整内容入库，站内信/邮件预览另行截断。
- 旧 VANZ 路径不设置 `category`/`inquiryType`（保持旧行为），新站点路径必须设置。

## 7. 测试

- `WebsiteInquirySitePropertiesTest` — 站点配置启动校验（保留租户、密钥长度、跨站点复用）。
- `WebsiteInquirySiteServiceImplTest` — 站点隔离服务（未知/禁用/错误密钥/伪造租户/幂等/旁路失败）。
- `AppWebsiteInquirySiteControllerTest` — 真实请求头绑定 + 完整协议链路（MockMvc + 租户过滤器）。
- `CrmClueServiceImplTest`（扩展）— category/inquiryType 持久化映射。
- `WebsiteInquiryTenantDbTest` — 真实 MyBatis Mapper、租户拦截器与临时 H2 数据库：跨租户相同编号独立入库、租户内幂等、唯一键约束、跨租户更新/删除拒绝、无租户上下文拒绝、字段回读。
- 旧 `WebsiteInquiryServiceImplTest` / `WebsiteInquiryPropertiesTest` 回归不变。

测试使用合成租户（如 900001）、mock/stub 及临时 H2 内存数据库。2026-10-30 本任务相关 9 个测试类共 64 项通过；并非整个 ERP 的全部测试。H2 验证实际 Mapper 与租户插件，但不替代生产 MySQL/Flyway 迁移演练。不建真实租户、不写生产数据库、不请求真实 ERP、不发送真实通知/邮件。
