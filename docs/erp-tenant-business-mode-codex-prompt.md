# 可直接复制给另一个 Codex 会话的开发提示词

将下面完整代码块复制到以 `D:\code` 为工作区的 Codex 会话。

```text
请在 D:\code 的 Yudao ERP 项目中完整实现“按租户业务模式切换商品库存字段”。不要只给方案，要完成代码、数据库迁移、自动测试、手工验收和提交。

一、开始前必须遵守

1. 先完整阅读 D:\code\AGENTS.md。
2. 目标分支是 codex/agent-rag；不要修改、合并、reset 或推送 main。
3. 先检查当前分支、HEAD、工作树和统一运行器状态。保留全部用户改动和未跟踪文件，不要 clean、checkout 或删除。
4. 当前 codex/agent-rag 可能领先远端，不能覆盖现有提交。
5. 完成并验证后只提交本任务文件到 codex/agent-rag。除非我明确要求，否则不要 push、不要创建 PR。
6. 数据库和本地运行必须使用 D:\code\.runtime\bin\oakved.ps1；不要绕过数据库门禁直接启动前后端或手工改运行数据库。
7. 完整阅读设计文档：
   D:\code\docs\erp-tenant-business-mode-development-design.md
   该文档是本任务的产品和技术基线。

二、业务背景

- Oakved家具：租户 ID 121，ToC/B2C 零售网站，需要库存。
- Vanz家具：租户 ID 162，ToB/B2B 询盘网站，ERP 商品管理页面不应显示库存。
- 当前右上角租户切换已经通过 visit-tenant-id 和 TenantVisitContextInterceptor 切换 TenantContextHolder，并刷新当前页。
- 商品列表、SKU 表单和详情中的库存字段目前固定显示。

三、最终目标

1. 给 system_tenant 增加 business_mode：
   - B2C：零售型，inventoryEnabled=true。
   - B2B：询盘型，inventoryEnabled=false。
2. 所有已有租户默认 B2C；迁移中把 162 设置为 B2B，把 121 明确保持为 B2C。
3. 租户管理页面能查看、创建和编辑业务模式。
4. 新增“当前有效租户业务配置”接口。必须从 TenantContextHolder.getRequiredTenantId() 读取有效租户，正确支持 visit-tenant-id，不能接受前端传 tenantId。
5. ERP 商品列表、商品新增、编辑和详情根据 inventoryEnabled 显示不同 UI。
6. 前端和 Java 业务代码禁止硬编码 121、162、租户名称或域名；这些 ID 只允许出现在迁移初始化数据中。
7. 保留共享库存数据库字段和后端库存链路，不能破坏 ToC。

四、明确 UI 行为

B2C：

- 保持“库存”“ERP 库存”“已售罄”“警戒库存”。
- 页签仍是“出售中、仓库中、已售罄、警戒库存、回收站”。
- 商品表单仍叫“价格库存”。
- SKU 编辑和详情继续显示库存。

B2B：

- 隐藏商品列表“库存”。
- 隐藏商品列表“ERP 库存”。
- 隐藏“已售罄”和“警戒库存”，只保留 tab code 0、1、4。
- tab 0 显示“展示中”，tab 1 显示“未展示”，tab 4 显示“回收站”。
- “销售状态”改为“展示状态”。
- “价格库存”改为“价格与规格”。
- SKU 编辑、批量设置和详情隐藏库存。
- 不执行库存前端校验。
- 价格、销量、ERP 编码、ERP 状态、最后同步、单个同步和全量同步全部保持。

五、数据保护要求

1. 不删除 product_spu.stock 或 product_sku.stock。
2. 不把 ProductSkuSaveReqVO.stock 改成 nullable，不取消后端 @NotNull。
3. 新建 B2B 商品时，隐藏库存内部默认值使用 0。
4. 修改已有 B2B 商品时必须保留原库存。
5. SkuList.vue 被促销等页面复用；新增 showStock 时默认必须为 true。
6. 当前 batchAdd 会复制整行临时数据。showStock=false 时，绝不能把临时 stock: 0 复制到已有 SKU；复制前排除 stock 或使用可见字段白名单。
7. 禁止在提交前把所有 B2B SKU 的 stock 统一改成 0。

六、数据库与后端

1. 迁移目录：
   yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations

2. 先重新枚举当前迁移。设计检查时已有 V001-V024，但不能假定下一个必然是 V025。

3. 追加连续编号的 V{next}__tenant_business_mode.sql，不修改旧迁移：
   - business_mode varchar(16) NOT NULL DEFAULT 'B2C'
   - tenant 162 -> B2B
   - tenant 121 -> B2C

4. 更新 system 模块 H2 测试表：
   yudao-module-system/yudao-module-system-server/src/test/resources/sql/create_tables.sql

5. 用项目生成器更新 oakved-baseline.sql，不手工编辑生成文件。

6. 设计检查时 furniture web/tests/dbMigrations.test.js 曾只期望到 V020，而目录已有 V024。先记录基线，再按实现时真实目录修正连续版本；不要隐藏预先存在的问题。

7. 新增 TenantBusinessModeEnum：
   - 字符串值 B2C、B2B。
   - 建议实现 ArrayValuable<String> 供 @InEnum 使用。
   - 集中提供库存能力判断。

8. 给 TenantDO、TenantSaveReqVO、TenantRespVO 增加 businessMode：
   - SaveReqVO 必填且只允许 B2C/B2B。
   - 新租户前端和数据库都默认 B2C。
   - TenantRespVO 的租户导出可增加“业务模式”。

9. 新增接口：
   GET /system/tenant/current-business-profile

   响应：
   {
     "tenantId": 162,
     "businessMode": "B2B",
     "inventoryEnabled": false
   }

   要求：
   - 使用 TenantContextHolder.getRequiredTenantId()。
   - 不接受 tenantId 请求参数。
   - 不加 @TenantIgnore。
   - 不加 @PermitAll。
   - 任意已登录 ERP 用户可读取，不要求 system:tenant:query。
   - 租户不存在明确失败。
   - inventoryEnabled 映射集中实现。

10. 后端测试：
   - 创建 B2B 租户正确持久化。
   - 更新 B2C 到 B2B 正确持久化。
   - 非法模式校验失败。
   - B2B profile 返回 inventoryEnabled=false。
   - B2C profile 返回 inventoryEnabled=true。

七、ERP 前端

1. src/api/system/tenant/index.ts：
   - TenantVO 增加 businessMode。
   - 增加 TenantBusinessProfile。
   - 增加 getCurrentTenantBusinessProfile()。

2. TenantForm.vue：
   - ToC（零售型） -> B2C。
   - ToB（询盘型） -> B2B。
   - 新租户默认 B2C，字段必填。

3. system/tenant/index.vue：
   - 增加“业务模式”列。

4. 建议增加 src/hooks/web/useTenantBusinessProfile.ts：
   - 暴露 loading、loaded、businessMode、inventoryEnabled。
   - 商品列表和商品表单顶层各加载一次，再通过 props 传给子组件。
   - SkuList 不得自行请求接口。
   - Profile 未加载完成前不要先显示库存。
   - 切换访问租户后必须重新请求，不能复用旧 profile。
   - 请求失败时不要按租户 ID硬编码兜底。

5. src/views/mall/product/spu/index.vue：
   - 两个库存列由 inventoryEnabled 控制。
   - B2C tabs = 0,1,2,3,4。
   - B2B tabs = 0,1,4。
   - B2B 文案为“展示中、未展示、回收站”和“展示状态”。
   - 如果 B2B 加载时 tabType 为 2 或 3，先重置到 0 再请求。
   - getTabsCount 可继续返回五类，前端只显示允许项。
   - 处理现有 onMounted/onActivated 与 keep-alive，不能在 profile 未就绪时发出错误请求。

6. src/views/mall/product/spu/form/index.vue：
   - 加载 profile。
   - B2B 页签名“价格与规格”，B2C “价格库存”。
   - 将 inventoryEnabled 传给 SkuForm。

7. src/views/mall/product/spu/form/SkuForm.vue：
   - 将 showStock 传给单规格、多规格批量设置和规格列表三个 SkuList。
   - B2B ruleConfig 排除 stock。
   - B2B 错误文案不能仍写“库存价格不完善”。
   - 新生成 SKU 的隐藏库存初始化为 0。

8. src/views/mall/product/spu/components/SkuList.vue：
   - 新增 showStock，默认 true。
   - 编辑和详情库存列均受 showStock 控制。
   - 促销等既有调用方未传时行为不变。
   - 修复 showStock=false 时 batchAdd 覆盖 stock。

八、本期不做

- 不改变购物车、结算、订单或 ERP 库存服务。
- 不移除商品 VO 的库存字段。
- 不隐藏价格、销量或 ERP 同步信息。
- 不改 Vanz 网站。
- 不做动态商品 Excel 导出列。
- 不重构无关 N+1 查询。

九、契约测试

建议在 D:\code\furniture web\tests 新增 tenantBusinessModeAdmin.test.js，覆盖：

- 迁移包含 business_mode、B2B/B2C、121/162 初始化。
- TenantDO/SaveReqVO/RespVO 包含 businessMode。
- current-business-profile 使用 TenantContextHolder，不接收 tenantId。
- 商品列表库存列受 inventoryEnabled 控制。
- B2B tabs 排除 2/3。
- SkuList showStock 默认 true。
- 编辑和详情库存列均受 showStock 控制。
- showStock=false 时 batchAdd 不复制 stock。
- 商品前端不存在按 121/162 判断的业务代码。

十、验证

先运行本任务相关基线测试，再改代码。完成后至少运行：

1. 在 D:\code\furniture web：
   - npm.cmd run verify:db-migrations
   - npm.cmd run build:db-baseline
   - 新增契约测试
   - dbMigrations.test.js
   - databaseSafetyWorkflow.test.js
   - mallErpAdminVisibility.test.js

2. 在 yudao-cloud：
   - Maven 定向运行 system tenant 相关测试。
   - 至少包含 TenantServiceImplTest 和新增 profile 测试。

3. 在 yudao-ui-admin-vue3：
   - pnpm.cmd ts:check
   - pnpm.cmd build:local

4. 本地运行：
   - 先用 D:\code\.runtime\bin\oakved.ps1 status 确认分支、commit、worktree、数据库、迁移版本和进程。
   - 使用统一启动器启动正确的 codex/agent-rag 工作树。

5. 手工验收：
   - 162：无库存列、ERP 库存、售罄/预警页签；新增/编辑/详情无库存。
   - 编辑已有 162 商品，只批量改价格，保存前后库存完全一致。
   - 新建 162 商品无需填库存即可保存，内部兼容值为 0。
   - 121：库存相关 UI 全部恢复且可编辑。
   - 121/162 连续切换至少两轮，不能出现旧 profile。
   - ERP 编码、状态、最后同步、单个同步、全量同步保持可用。

十一、完成报告

完成后提供：

1. 结果摘要。
2. 修改文件清单。
3. 迁移编号及内容。
4. 自动测试命令和结果。
5. 两租户手工验收结果。
6. 明确说明是否验证 B2B 编辑不会清零库存。
7. commit hash。
8. 未完成项或风险；没有就写“无”。

不要停在设计阶段；在安全和权限允许范围内完成实现、验证和提交。
```
