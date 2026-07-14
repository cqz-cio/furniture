# 家具电商数据看板 API 与埋点协议

> 版本：2.0
> 日期：2026-07-10
> 业务时区：`Asia/Shanghai`
> 默认展示币种：`USD`，以租户配置为准

## 1. 通用约定

### 1.1 网关、时间和金额

- 家具前台只通过网关前缀 `/app-api` 访问埋点接口；Statistics 服务不得直接暴露公网。
- 管理后台接口前缀为 `/admin-api`；Controller 内部路径不包含网关前缀。
- 所有统计自然日均按 `Asia/Shanghai` 解释。管理接口使用包含首尾的 `startDate`、`endDate`，后端转换为 `[startDate 00:00:00, endDate + 1 day 00:00:00)`。
- 浏览器本地时区不得改变查询边界。前端不得通过 `new Date('YYYY-MM-DD')` 的浏览器时区推导统计日。
- 金额存储和接口传输使用租户币种的最小货币单位，Java 使用 `Long`。响应通过 `currencyCode` 和 `currencyMinorUnit` 说明显示规则。
- 当前家具租户默认 `currencyCode=USD`、`currencyMinorUnit=2`。不得在组件中硬编码人民币符号或“元”。
- 百分比响应使用数值类型，`10.25` 表示 `10.25%`。率类周期变化使用百分点 `changePercentagePoints`；计数和金额使用 `changeAmount`、`changePercent`。
- `CommonResult` 成功结构为 `{ "code": 0, "data": {}, "msg": "" }`。

### 1.2 默认日期和比较期

- 管理端首次进入时默认 `endDate=Asia/Shanghai 昨日`，`startDate=endDate-29 days`，即最近 30 个完整自然日。
- 默认 `compare=true`，参考期为当前区间之前紧邻的等长完整自然日区间。
- “今日”使用 `startDate=endDate=Asia/Shanghai 今日`，固定标记为部分数据，默认且一期保持 `compare=false`。
- `compare=true` 只接受不含今日的完整自然日区间；包含今日时返回参数错误，避免把未完成日与完整日直接比较。区间含埋点上线前或采集缺失日期时请求仍成功，但流量及相关比较返回 `null`、覆盖状态返回 `PARTIAL`/`UNAVAILABLE`，不能让默认 30 日页面在上线初期整体报错。
- 最大查询跨度为 366 个包含首尾的自然日。

### 1.3 数据范围

```ts
export type DashboardScope = 'SITE' | 'PRODUCT'

export interface DashboardQuery {
  scope: DashboardScope
  startDate: string // YYYY-MM-DD
  endDate: string   // YYYY-MM-DD
  compare?: boolean
  categoryId?: number
  spuId?: number
}
```

- `SITE` 表示全站经营，只展示全站流量、订单、收入、退款和站点规模比，不接受 `categoryId`、`spuId`。
- `PRODUCT` 表示商品分析，允许分类或 SPU 筛选，不返回首页 PV/UV 和站点转化率。
- `PRODUCT` 同时传入 `categoryId`、`spuId` 时，SPU 必须属于该分类和当前租户，否则返回参数错误；不得静默让 `spuId` 覆盖 `categoryId`。
- 商品详情 UV 按商品去重，商品 UV 求和可能大于全站商品详情 UV；一张订单可包含多个 SPU，商品支付订单数求和也可能大于站点订单数。
- 分类筛选使用 SPU 查询时的当前分类，不回放历史分类；商品表的 `status`、`availableStock` 同样是当前值。页面和导出说明必须标注“当前”，商品改类后历史统计会随当前分类重新归组。

## 2. 通用响应元数据

除文件下载外，所有管理端看板接口都返回自己的 `meta`，不能用 summary 的更新时间替代其他区块的新鲜度。

```ts
export type DataCoverageStatus = 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE'
export type FreshnessStatus = 'FRESH' | 'DELAYED' | 'STALE'

export interface DashboardSourceWatermarks {
  trafficAsOf?: string
  tradeAsOf?: string
  refundAsOf?: string
  costAsOf?: string
}

export interface DashboardMeta {
  scope: DashboardScope
  startDate: string
  endDate: string
  timezone: 'Asia/Shanghai'
  currencyCode: string
  currencyMinorUnit: number
  snapshotId: string
  asOf: string | null
  lastSuccessfulRunAt: string | null
  trafficDataStatus: DataCoverageStatus
  trafficDataAvailableFrom: string | null
  freshnessStatus: FreshnessStatus
  lagSeconds: number | null
  watermarks: DashboardSourceWatermarks
  profitVisible: boolean
  comparison: {
    enabled: boolean
    startDate?: string
    endDate?: string
  }
}
```

规则：

- `asOf` 为当前接口实际使用的相关数据源水位中的最早值。
- `snapshotId` 是后端依据“租户、规范化查询、聚合版本及本接口实际使用的分源水位”生成的稳定指纹，不包含用户或访客标识。相同查询和水位得到相同值；任一相关水位变化后必须变化，用于识别区块是否来自同一数据快照，不用于鉴权。
- 正常更新延迟不超过 10 分钟。`lagSeconds > 600` 返回 `DELAYED`，页面显示“数据更新延迟”；`lagSeconds > 1200` 返回 `STALE`，页面显示严重提示并明确受影响区块。
- `COMPLETE` 表示请求区间的流量日期均已可信采集；`PARTIAL` 表示包含今日、埋点启用前日期或局部缺口；`UNAVAILABLE` 表示没有可用于计算流量指标的可信数据。
- `PARTIAL`、`UNAVAILABLE` 日期的 PV/UV 和以其为分母的转化率返回 `null`，不得返回业务 0。
- 已确认采集正常且业务活动为零时才返回数值 `0`。
- 无利润权限时 `profitVisible=false`，成本、毛利、毛利率、成本质量计数等字段在序列化前完全移除，而不是只由前端隐藏。

## 3. 埋点事件和客户端标识

### 3.1 客户端标识

- `oakved_visitor_id`、`oakved_visitor_id_created_at`：分析同意策略关闭时首次访问创建；策略开启时仅在用户同意后创建。标识使用 UUID v4，最长 180 天后轮换；租户配置的事件保留期更短时，TTL 取更短值，不得无限期保存在 `localStorage`。
- `oakved_session_id`、`oakved_session_last_active_at`：保存在 `sessionStorage`。白名单业务事件更新活动时间；连续 30 分钟没有业务事件后生成新会话。
- `eventId`：每次业务事件新建 UUID v4；同一网络重试必须复用原值。
- 用户撤回分析同意时立即停止上报并删除上述本地标识。
- 客户端不得发送 `userId`、租户编号、业务发生时间或设备类型。
- 在依法需要分析同意的地区，未同意前一期不创建任何持久分析标识，也不发送任何行为事件；一期不实现 cookieless、临时标识或不可识别汇总替代模式。
- 因未同意而未采集的访问无法进入 PV/UV 和转化率。接口通过覆盖状态与起始日期披露可测量范围，页面固定提示“流量指标仅代表已同意分析的可测量访问，可能存在覆盖偏差”。

### 3.2 事件触发规则

| 事件 | 触发时机 | 必填业务字段 | 不触发情况 |
|---|---|---|---|
| `HOME_VIEW` | 当前标准化路由变为 `/`，包含首次进入、内部返回、刷新 | `pagePath='/'` | 5 秒内相同复合键重复触发 |
| `PRODUCT_DETAIL_VIEW` | `/product?id={spuId}` 商品接口成功且商品有效 | `spuId`, `pagePath='/product'` | 接口失败、无效商品、纯接口重试 |
| `ADD_TO_CART` | Trade 购物车写事务提交后，通过内网 API 记录 | `userId`, `spuId`, `skuId`, `quantity`，可选访客/会话 | 本地预览、接口失败、事务回滚；公共追踪请求一律不接受 |
| `CHECKOUT_START` | 购物车校验通过并即将进入真实结算路由 | `pagePath` | 购物车为空或校验失败 |

支付成功和退款不依赖前端埋点，必须以后端订单支付时间、退款成功时间为准。

首页、商品详情、加购和结算事件均属于可被客户端影响的运营观测数据，不作为计费、结算或审计依据；其中 `ADD_TO_CART` 只有在真实购物车接口成功后才上报，仍不得当作财务事实。

## 4. 埋点安全合同

### 4.1 网关站点与租户绑定

- 公网仅开放网关上的 `POST /app-api/statistics/behavior/track` 和对应预检请求。
- 网关维护“精确 Origin + 受控 Host -> 启用租户”映射；一期默认只启用家具租户 `121`。
- 匿名客户端无权选择租户。网关移除外部 `tenant-id` 和内部用户头，再注入不可由公网伪造的内部租户上下文。
- 存在会员 Bearer Token 时，令牌租户必须与站点映射租户一致；不一致时拒绝请求。
- SPU、SKU 校验必须同时约束站点绑定租户和 `deleted=0`；SKU 必须属于请求 SPU。
- Statistics 服务、管理接口、任务接口和数据库只允许内网访问，不得绕过网关站点绑定直接调用公网追踪 Controller。

### 4.2 精确 Origin

- CORS 只允许配置中的完整 Origin，包括 scheme、host 和 port；不使用通配符、后缀匹配或字符串 `contains`。
- 浏览器追踪请求的 `Origin` 必须与站点映射完全一致；拒绝缺失 Origin、`Origin: null`、相似后缀域名和未配置端口。
- 网关预检与实际 POST 使用同一白名单；错误响应不反射任意 Origin，也不携带宽泛凭据设置。
- 本期使用 Bearer Token，不使用 Cookie 鉴权；未来若改为 Cookie，必须另行设计 CSRF 防护。

### 4.3 服务端时间、HMAC 和密钥轮换

- 请求不接受 `occurredAt`。`occurred_at` 使用 Statistics 服务端接收时间，并按 `Asia/Shanghai` 归属自然日。
- 设备类型由服务端根据截断后的 User-Agent 分类，分类后立即丢弃原始 User-Agent，不落库、不写日志。
- 服务端使用 `HMAC-SHA256(keyVersionSecret, tenantId + ':' + rawId)` 分别摘要 `visitorId`、`sessionId`，以 64 位十六进制保存；普通 SHA-256 加公开盐不符合合同。
- 生产环境没有当前 HMAC 主密钥或密钥版本时服务必须拒绝启动，不提供生产默认值，不记录密钥或原始 UUID。
- HMAC 新版本只能在 `Asia/Shanghai` 自然日边界生效。同一租户同一自然日的所有新事件必须使用同一 `hashKeyVersion`，不得在日内混用版本导致 UV 重复。
- 旧版本密钥只为保留期内重算和受控回滚保留；新写入从生效日零点统一使用新版本。密钥销毁必须晚于相关原始事件保留和重算窗口。
- 旧版本密钥还用于可验证访客删除请求的摘要检索；相关原始事件和删除请求处理完之前不得销毁。

### 4.4 幂等、5 秒去重和限流

- 唯一键 `(tenant_id, event_id)` 负责网络重试幂等。仅捕获该唯一键的 `DuplicateKeyException` 并返回成功；其他数据库异常必须失败并计入监控。
- 服务端按“租户 + visitor HMAC + eventType + 标准化 pagePath + spuId + skuId”计算复合键。同一复合键在 5 秒窗口内只计一条；客户端抑制仅是性能优化，不能替代服务端规则。
- 5 秒规则不使用客户端时间。超过 5 秒的真实刷新可形成新 PV。
- 公共事件使用 Redis 原子 `SET NX` 与 5 秒 TTL 实现并发安全窗口；去重存储不可用时拒绝本次公共写入、告警并使相关流量覆盖标为 `PARTIAL`，不能静默退化为仅依赖可变化的 `eventId`。Trade 服务端加购按服务端事件 ID 幂等，不依赖公共 5 秒键。
- 限流键只使用服务端绑定租户、可信代理解析后的客户端 IP 和 visitor HMAC；不得使用 `eventId` 或请求体可任意变化字段逃避限流。
- 默认单 IP 120 次/分钟、单访客 120 次/分钟、单租户 6000 次/分钟，并设置全局熔断阈值。
- 请求体最大 8 KB。任何被限流或质量排除的事件不进入业务指标，但按原因码进入聚合监控。

### 4.5 日志与质量

- 日志和链路追踪不得记录请求体、原始 UUID、Bearer Token、完整 IP、完整 Referrer 或完整 User-Agent。
- 原始事件使用 `trafficQuality=ACCEPTED|BOT|INTERNAL|TEST|RATE_EXCLUDED` 和安全的 `exclusionReason`；排除事件不进入业务指标。
- 内部自动化流量只能通过服务端签名测试标记识别，客户端自报测试标记无效。
- 原始事件同时记录 `eventSource=PUBLIC_WEB|SERVER_CART`。只有经 Trade 事务提交后产生的 `SERVER_CART` 事件可计入加购指标，公共请求不能自行声明来源。

## 5. 上报行为事件

### 5.1 公共网页追踪

`POST /app-api/statistics/behavior/track`

认证：允许匿名；有 Bearer Token 时从安全上下文读取会员编号。

```json
{
  "eventId": "7a355ec8-5652-4bc0-a41b-84505bc73daa",
  "eventType": "PRODUCT_DETAIL_VIEW",
  "visitorId": "4e62510f-3b8f-4782-9020-c3ae010867f0",
  "sessionId": "68bfc4c6-196c-4b57-a9bd-8d368ddd505a",
  "spuId": 2048,
  "skuId": null,
  "quantity": null,
  "pagePath": "/product",
  "referrerHost": "www.example.com"
}
```

| 字段 | 类型 | 约束 |
|---|---|---|
| `eventId` | string | 必填，UUID v4，最大 64 字符 |
| `eventType` | enum | `HOME_VIEW`、`PRODUCT_DETAIL_VIEW`、`CHECKOUT_START`；公共 `ADD_TO_CART` 非法 |
| `visitorId` | string | 必填，UUID v4，最大 64 字符 |
| `sessionId` | string | 必填，UUID v4，最大 64 字符 |
| `spuId` | long | 商品详情、加购必填，正数且属于绑定租户 |
| `skuId` | long | 加购必填，正数且属于该 SPU |
| `quantity` | int | 加购必填，1 至 999 |
| `pagePath` | string | 必填，标准化绝对路径，最大 255 字符；禁止协议、域名、查询串和片段 |
| `referrerHost` | string/null | 仅主机名，最大 255 字符；禁止协议、路径、用户信息和控制字符 |

请求 VO 采用严格字段白名单。出现 `occurredAt`、`deviceType`、`userId`、`tenantId` 或其他未定义字段时返回参数错误，不允许通过“忽略未知字段”形成不一致客户端合同。

成功接收、`eventId` 幂等重复或服务端 5 秒重复均返回：

```json
{ "code": 0, "data": true, "msg": "" }
```

客户端统一复用 `requestYudao` 和 `fetch keepalive`，超时 2 秒，最多重试 1 次且复用原 `eventId`。追踪失败必须被家具前台静默隔离，不得阻断浏览、加购、结算或下单。

### 5.2 Trade 服务端加购事件

Trade 的真实购物车写事务提交后，通过内网 `StatisticsBehaviorApi.recordCartAdded` 记录加购：

```ts
interface CartBehaviorRecordRequest {
  eventId: string       // Trade 服务生成的 UUID
  userId: number        // 从认证上下文取得
  spuId: number
  skuId: number
  quantity: number
  visitorId?: string    // 仅在用户同意且购物车请求携带受控分析身份头时存在
  sessionId?: string
}
```

- 租户通过既有内网 RPC 租户上下文传递，不是 DTO 字段；Statistics 再校验用户、SPU、SKU 同租户归属。
- 家具前台不得另发公共 `ADD_TO_CART`，只可在同意与总开关允许时把访客/会话 UUID 放入购物车请求的专用头。网关、Trade 访问日志和异常日志必须删除这些头的原文。
- Trade 使用事务提交后事件异步调用；Statistics 超时或失败只增加失败指标与告警，不改变已成功的购物车响应。`consentRequired=true` 时，缺少同意后才会生成的访客/会话标识就不得发布分析事件；`consentRequired=false` 只有在发布单已有书面依据时，才允许以认证用户构建稳定去重身份作为回退。该选择由服务端站点配置决定，不能由请求布尔值切换。
- Statistics 保存 `eventSource=SERVER_CART`；公共来源、客户端伪造内部 API 或重复 RPC 不得重复计入。

## 6. 汇总指标接口

`GET /admin-api/statistics/dashboard/summary`

权限：基础接口需要 `statistics:dashboard:query`；利润字段另需 `statistics:dashboard:profit-query`。

请求参数使用第 1.3 节的 `DashboardQuery`。

```json
{
  "code": 0,
  "data": {
    "value": {
      "homePv": 12500,
      "homeUv": 8200,
      "productDetailPv": 9700,
      "productDetailUv": 6100,
      "addCartCount": 960,
      "addCartUserCount": 720,
      "paidOrderCount": 365,
      "paidBuyerCount": 340,
      "paidItemCount": 488,
      "paidRevenue": 86500000,
      "refundAmount": 4200000,
      "netRevenue": 82300000,
      "costAmount": 49380000,
      "grossProfit": 32920000,
      "grossMarginPercent": 40.00,
      "sitePvConversionPercent": 2.92,
      "siteUvConversionPercent": 4.15,
      "addCartRatePercent": 11.80,
      "averageOrderValue": 236986,
      "refundRatePercent": 4.86,
      "profitDataQuality": "EXACT",
      "exactCostItemCount": 488,
      "estimatedCostItemCount": 0,
      "missingCostItemCount": 0,
      "knownCostAmount": 49380000,
      "costCoveragePercent": 100.00
    },
    "reference": {},
    "changes": {
      "netRevenue": { "changeAmount": 3100000, "changePercent": 3.91 },
      "refundRatePercent": { "changePercentagePoints": 0.42 }
    },
    "meta": {
      "scope": "SITE",
      "startDate": "2026-06-10",
      "endDate": "2026-07-09",
      "timezone": "Asia/Shanghai",
      "currencyCode": "USD",
      "currencyMinorUnit": 2,
      "snapshotId": "dashboard-121-20260710T023500Z",
      "asOf": "2026-07-10T10:35:00+08:00",
      "lastSuccessfulRunAt": "2026-07-10T10:36:12+08:00",
      "trafficDataStatus": "COMPLETE",
      "trafficDataAvailableFrom": "2026-06-01",
      "freshnessStatus": "FRESH",
      "lagSeconds": 72,
      "watermarks": {
        "trafficAsOf": "2026-07-10T10:35:00+08:00",
        "tradeAsOf": "2026-07-10T10:36:00+08:00",
        "refundAsOf": "2026-07-10T10:36:00+08:00",
        "costAsOf": "2026-07-10T10:36:00+08:00"
      },
      "profitVisible": true,
      "comparison": {
        "enabled": true,
        "startDate": "2026-05-11",
        "endDate": "2026-06-09"
      }
    }
  },
  "msg": ""
}
```

字段规则：

- `SITE` 返回首页指标和站点转化率；`PRODUCT` 返回商品详情、商品加购、商品订单和商品转化，不返回首页指标或站点转化率。
- 站点/商品转化均是同区间规模比而非 cohort 归因，受跨期浏览、直接访问和跨设备影响，可超过 100%；服务端不得截断，页面必须显示口径提示。
- `reference` 与 `value` 使用相同适用字段；`compare=false` 时 `reference=null`、`changes=null`。
- 分母为 0 时业务率返回 `0`；流量覆盖不完整或不可用时以流量为分母的率返回 `null`。
- `averageOrderValue` 为支付销售额除以支付订单数后按币种最小单位四舍五入，不使用净销售额；退款率按退款成功日金额除以当期支付销售额，允许超过 100%。
- `profitDataQuality=INCOMPLETE` 时 `costAmount`、`grossProfit`、`grossMarginPercent` 必须为 `null`；`knownCostAmount` 只用于解释覆盖率。
- 区间没有支付成本入账或退货成本冲回记录时 `profitDataQuality=NOT_APPLICABLE`，不得返回 `EXACT`；仅退款不产生成本冲回，成本可为 0，毛利随净销售额为负，毛利率仍按净销售额不大于 0 的规则返回 `null`。
- 净销售额小于等于 0 时 `grossMarginPercent=null`。

利润质量枚举：

- `EXACT`：相关订单明细全部使用下单快照成本。
- `MIXED`：同时存在精确和历史估算成本。
- `ESTIMATED`：有成本的明细全部为历史估算。
- `INCOMPLETE`：至少一条订单明细缺失成本。
- `NOT_APPLICABLE`：区间没有需要评估成本完整性的支付成本入账或退货成本冲回记录。

`exactCostItemCount`、`estimatedCostItemCount`、`missingCostItemCount` 的单位统一为“成本移动行”，不是商品件数：支付成本在支付日计一行，退货成本冲回在退款成功日再计一行；同一订单明细跨日退货可在两个日期各参与一次，因此三者与 `paidItemCount` 不要求相等。

`costCoveragePercent=(exactCostItemCount+estimatedCostItemCount)/(exact+estimated+missing)×100%`；分母为 0 时返回 `null`。这是成本移动行覆盖率，不是金额覆盖率，也不代表历史估算成本的准确度。

## 7. 每日趋势接口

`GET /admin-api/statistics/dashboard/trend`

权限与汇总接口相同。额外参数：`granularity=DAY`，一期仅接受 `DAY`。

```json
{
  "code": 0,
  "data": {
    "points": [
      {
        "day": "2026-07-09",
        "referenceDay": "2026-06-09",
        "value": {
          "homePv": 430,
          "homeUv": 281,
          "productDetailPv": 350,
          "productDetailUv": 226,
          "paidOrderCount": 13,
          "netRevenue": 3030000,
          "grossProfit": 1190000,
          "siteConversionPercent": 3.02
        },
        "reference": {},
        "trafficDataStatus": "COMPLETE",
        "freshnessStatus": "FRESH",
        "profitDataQuality": "EXACT"
      }
    ],
    "meta": {}
  },
  "msg": ""
}
```

- `compare=true` 时每个点返回等位置的 `referenceDay` 和 `reference`；前端不得自行猜测参考日。
- 已确认正常采集但业务为零的日期补数值 0。
- 埋点启用前、采集缺失或不可用日期返回 `null` 与 `trafficDataStatus`，图表必须断线。
- `freshnessStatus=STALE` 时仍可返回上次成功值，但点和区块必须显式标记过期；覆盖状态与新鲜度是两个独立维度。
- `SITE` 和 `PRODUCT` 的字段适用规则与汇总接口一致；利润字段按权限在服务端移除。

## 8. 路径阶段规模接口

`GET /admin-api/statistics/dashboard/stage-overview`

权限：`statistics:dashboard:query`。支持 `SITE` 和 `PRODUCT`，但两种范围的适用阶段不同。

```json
{
  "code": 0,
  "data": {
    "cohortAligned": false,
    "interpretation": "各阶段独立去重，不构成同一批访客的归因漏斗",
    "steps": [
      { "key": "HOME_UV", "name": "首页访客", "value": 8200, "unit": "DAILY_VISITOR", "dedupeScope": "DAY", "applicability": "APPLICABLE" },
      { "key": "PRODUCT_DETAIL_UV", "name": "商品详情访客", "value": 6100, "unit": "DAILY_VISITOR", "dedupeScope": "DAY_PRODUCT", "applicability": "APPLICABLE" },
      { "key": "ADD_CART_USER", "name": "加购访客", "value": 720, "unit": "DAILY_VISITOR", "dedupeScope": "DAY_PRODUCT", "applicability": "APPLICABLE" },
      { "key": "CHECKOUT_SESSION", "name": "开始结算", "value": 510, "unit": "SESSION", "dedupeScope": "SESSION", "applicability": "APPLICABLE" },
      { "key": "PAID_BUYER", "name": "支付买家", "value": 340, "unit": "BUYER", "dedupeScope": "PAYMENT_DAY", "applicability": "APPLICABLE" }
    ],
    "meta": {}
  },
  "msg": ""
}
```

- 接口不返回逐层转化率、`largestDropStep` 或任何 cohort 归因字段。
- 页面名称必须是“路径阶段规模”或“阶段规模”，并固定展示 `interpretation`；不得命名为漏斗或最大流失。
- 任一阶段数据不可用时 `value=null` 并返回相应状态，不得用 0 代替未知。
- `SITE` 返回首页、商品详情、加购、结算和支付买家五个阶段，均为 `APPLICABLE`。
- `PRODUCT` 只计算商品详情、加购和支付买家。首页没有商品归因，`CHECKOUT_START` 也没有 SPU 维度，因此这两步必须返回 `value=null`、`applicability=NOT_APPLICABLE` 和解释文案；不得沿用全站值或据此计算比例。
- `PRODUCT` 趋势同样不得返回或绘制首页 PV/UV。

## 9. 运营关注项接口

`GET /admin-api/statistics/dashboard/attention`

权限：`statistics:dashboard:query`。接口始终按商品统计并返回 `meta.scope=PRODUCT`：页面处于 `SITE` 时用相同日期、无分类/SPU 调用，展示“全部商品关注”；页面处于 `PRODUCT` 时允许沿用当前分类或 SPU。不得用首页流量参与商品规则分母。

风险类型：

- `HIGH_TRAFFIC_LOW_CONVERSION`：详情 PV 不低于租户阈值且商品 PV 转化率低于阈值。
- `HIGH_REFUND`：支付订单数和支付销售额均不低于租户样本阈值，且退款率高于阈值。
- `LOW_OR_NEGATIVE_MARGIN`：利润质量不是 `INCOMPLETE`，毛利小于 0 时直接命中；否则只有支付订单数不低于租户样本阈值且毛利率低于阈值时命中。
- `MISSING_COST`：`missingCostItemCount > 0`。

```json
{
  "code": 0,
  "data": {
    "notice": "规则提示，不代表自动诊断",
    "thresholds": {
      "highTrafficMinPv": 100,
      "lowConversionMaxPercent": 1.00,
      "highRefundMinOrderCount": 10,
      "highRefundMinRevenue": 100000,
      "highRefundMinPercent": 10.00,
      "lowMarginMinOrderCount": 5,
      "lowMarginMaxPercent": 10.00
    },
    "items": [
      {
        "riskType": "HIGH_TRAFFIC_LOW_CONVERSION",
        "title": "高流量低转化",
        "matchedProductCount": 3,
        "suggestedAction": "检查价格、库存和商品详情内容"
      }
    ],
    "notEvaluated": [],
    "meta": {}
  },
  "msg": ""
}
```

- 阈值来自租户配置，必须随响应返回，禁止前端另存一套阈值。
- `SITE` 页的关注区块必须标注“全部商品关注”；点击后把主页面切到 `PRODUCT`、应用对应 `riskType` 并定位商品表。
- 无利润权限时不计算、不返回 `LOW_OR_NEGATIVE_MARGIN`、`MISSING_COST`，也不泄露其命中数量。
- 流量覆盖非 `COMPLETE` 时不评估 `HIGH_TRAFFIC_LOW_CONVERSION`；利润为 `INCOMPLETE` 时不评估 `LOW_OR_NEGATIVE_MARGIN`，但仍可评估 `MISSING_COST`。未评估规则写入 `notEvaluated`，每项包含 `riskType`、受控 `reasonCode` 和运营文案。
- 只有所有有权限规则均已评估且无命中时，才返回空 `items` 和固定文案“当前规则下未发现关注项”；存在 `notEvaluated` 时页面必须显示“部分规则未评估”及原因，不得把未知显示成 0 个异常。

## 10. 商品表现分页接口

`GET /admin-api/statistics/dashboard/product-page`

权限：`statistics:dashboard:query`；利润字段另需 `statistics:dashboard:profit-query`。固定要求 `scope=PRODUCT`。

在公共参数基础上支持：

- `keyword`：商品名称模糊搜索，或完整 SPU 编号精确搜索；最大 100 字符。
- `riskType`：第 9 节风险类型。
- `pageNo`：从 1 开始。
- `pageSize`：默认 20，最大 100。
- `sortField`：`detailPv`、`detailUv`、`addCartUserCount`、`paidOrderCount`、`netRevenue`、`grossProfit`、`grossMarginPercent`、`productConversionPercent`、`refundRatePercent`。
- `sortOrder`：`asc` 或 `desc`。

所有排序字段通过后端枚举映射，SQL 禁止拼接客户端原始字段。无利润权限却请求利润排序或利润风险类型时返回权限错误。

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "spuId": 2048,
        "name": "Cloud Modular Sofa",
        "picUrl": "https://cdn.example.com/sofa.jpg",
        "categoryId": 91,
        "categoryName": "Sofas",
        "status": "ON_SALE",
        "availableStock": 32,
        "detailPv": 1520,
        "detailUv": 980,
        "addCartCount": 132,
        "addCartUserCount": 101,
        "addCartRatePercent": 10.31,
        "paidOrderCount": 48,
        "paidBuyerCount": 46,
        "paidItemCount": 61,
        "paidRevenue": 16800000,
        "refundAmount": 800000,
        "netRevenue": 16000000,
        "costAmount": 9500000,
        "grossProfit": 6500000,
        "grossMarginPercent": 40.63,
        "productConversionPercent": 3.16,
        "productUvConversionPercent": 4.69,
        "refundRatePercent": 4.76,
        "profitDataQuality": "EXACT",
        "exactCostItemCount": 61,
        "estimatedCostItemCount": 0,
        "missingCostItemCount": 0,
        "knownCostAmount": 9500000,
        "costCoveragePercent": 100.00,
        "trafficDataStatus": "COMPLETE",
        "riskTypes": [],
        "comparison": {
          "detailPv": { "referenceValue": 1410, "changeAmount": 110, "changePercent": 7.80 },
          "productConversionPercent": { "referenceValue": 3.72, "changePercentagePoints": -0.56 }
        }
      }
    ],
    "total": 1,
    "meta": {}
  },
  "msg": ""
}
```

- 所有比率必须可由同一行返回字段复算。
- `INCOMPLETE` 时利润字段为 `null`，不能按伪造利润排序。
- `status`、`availableStock` 是查询时的当前商品状态，不是所选历史区间的日快照，页面必须标注“当前”；下架或删除商品保留历史统计时必须返回明确状态，前端不得提供无权限或已失效的编辑链接。

## 11. 商品导出接口

`GET /admin-api/statistics/dashboard/export-product-excel`

请求参数与商品分页一致但忽略 `pageNo`、`pageSize`，另支持 `includeProfit=true|false`。

- 普通导出需要 `statistics:dashboard:export`，不包含成本和利润字段。
- `includeProfit=true` 同时需要 `statistics:dashboard:profit-query` 和 `statistics:dashboard:profit-export`。
- 导出严格复用当前筛选与排序。单次最多 10,000 行，单用户每 10 分钟最多 3 次；超过时返回可操作提示，要求缩小日期或商品范围。
- 金额以 `currencyCode` 主单位输出为数值单元格，列名标注币种；不得硬编码“元”。
- 工作簿包含“数据”和“说明”两张表。“说明”记录生成时间、数据截至时间、业务时区、币种、日期与参考期、scope、分类、商品、关注类型、排序、质量说明和公式口径。
- 文本单元格对以 `=`、`+`、`-`、`@`、制表符或回车开头的内容做公式注入转义。
- 文件名：`product-performance_{currencyCode}_{startDate}_{endDate}.xlsx`。
- 审计记录操作人、租户、权限类型、筛选摘要、行数、文件哈希、追踪编号和结果，不记录文件内容。

## 12. 权限矩阵

| 能力 | 权限 | 行为 |
|---|---|---|
| 查看流量、订单、收入、退款 | `statistics:dashboard:query` | 所有看板接口的基础字段 |
| 查看成本、毛利、利润质量 | `statistics:dashboard:profit-query` | 后端序列化利润字段；前端再按权限展示 |
| 普通商品导出 | `statistics:dashboard:export` | 不包含成本和利润 |
| 利润商品导出 | `statistics:dashboard:profit-export` + 利润查询权限 | 包含成本、毛利和质量字段 |

- 管理员默认拥有全部权限，但仍保留授权审计。
- 运营角色默认只有基础查询权限。
- 财务或经营管理角色经审批获得利润权限；查看页面或利润不会自动授予导出权限。
- 所有接口、排序、关注项、导出和商品深链保留租户隔离及目标模块自身权限。

## 13. 管理端 URL、并发和状态合同

### 13.1 URL 可复现

以下状态同步到 `/dashboard` 的 URL query：

`scope`、`startDate`、`endDate`、`compare`、`categoryId`、`spuId`、`riskType`、`keyword`、`sortField`、`sortOrder`、`pageNo`、`pageSize`。

- 首次进入无参数 URL 时写入默认 30 个完整自然日和 `scope=SITE` 的规范参数。
- URL 恢复时先完成参数校验；非法枚举、越界日期或商品分类冲突恢复为安全默认并给出可见提示。
- 切换分类后，若当前商品不属于新分类，清除 `spuId` 并提示，不发送冲突请求。
- 点击关注项切换到 `scope=PRODUCT`、写入 `riskType`、重置 `pageNo=1` 并定位商品表。

### 13.2 请求竞态

- 筛选变更后，summary、trend、stage-overview、attention、product-page 各自使用 `AbortController` 取消旧请求，并维护单调递增请求序号或规范化 `queryKey`。
- 只有与当前 `queryKey` 完全一致的最新响应可以写入状态；迟到响应不得覆盖新筛选。
- 各区块独立加载和失败，单个接口失败不取消其他接口。刷新时保留上次成功数据，但必须标明其原筛选和水位。
- 手动刷新跳过 30 秒短缓存；同一用户相同规范查询可使用 30 秒短缓存。
- 商品搜索至少 300ms 防抖；切换 scope、日期、分类、商品或风险类型时商品分页重置为第 1 页。

### 13.3 空、错和权限状态

- 真实无业务数据：显示数值 0 或“当前条件下暂无数据”。
- 未采集、部分采集、过期或接口错误：显示 `—` 和原因，趋势使用断点。
- 无利润权限：不渲染利润控件，响应也不含利润字段；不得伪装成 0。
- 区块错误提供原位重试；重试成功后焦点保留在原区块。

## 14. 无障碍消费合同

- 所有筛选项必须有可见标签，键盘可完成 scope、日期、分类、商品、排序、分页、刷新、关注项定位和导出。
- ECharts 开启 `aria`，并提供“查看数据表”入口；图例同时使用文字、线型或点型，不以颜色作为唯一信号。
- 图表 Tooltip 必须可由键盘聚焦触发；数据缺失、真实 0、估算、过期和涨跌均有文本说明。
- 异步加载、更新延迟、错误和重试结果通过 `aria-live="polite"` 通知；抽屉关闭和重试后焦点返回触发位置。
- 390px 及 200% 缩放下核心操作可见；商品表固定商品列和操作列，横向滚动有可感知提示。

## 15. 错误处理

| 场景 | 行为 |
|---|---|
| `eventId` 唯一键重复 | 返回成功，不重复插入 |
| 服务端 5 秒复合键重复 | 返回成功，不计第二条 |
| 公共 5 秒去重存储不可用 | 临时失败且不写入，前台静默；监控告警并标记流量覆盖缺口 |
| 其他数据库异常 | 返回失败并监控，不伪装为幂等 |
| Origin 缺失、`null` 或不在精确白名单 | 网关拒绝，不进入 Statistics 服务 |
| 匿名请求伪造租户或会员租户不匹配 | 拒绝并记录安全结果码 |
| 商品/SKU 不属于绑定租户 | 参数或权限错误，不泄露其他租户资源存在性 |
| 请求包含客户端时间、设备、用户或租户等非合同字段 | 参数错误，不使用该字段 |
| 埋点超过体积或限流 | 网关拒绝；家具前台静默隔离 |
| `SITE` 带商品筛选 | 参数错误 |
| `PRODUCT` 商品不属于分类 | 参数错误，不静默覆盖 |
| 日期跨度超过 366 天或比较不完整区间 | 参数错误 |
| 数据延迟超过 10 分钟 | 返回 `DELAYED`，页面提示 |
| 数据延迟超过 20 分钟 | 返回 `STALE`，页面严重提示并保留上次成功数据 |
| 单个看板区块失败 | 其他区块保留成功结果，失败区块独立重试 |
| 无利润权限请求利润排序/关注/导出 | 后端拒绝，不降级为隐式成功 |

## 16. 唯一性能门槛

以下阈值是设计、API 和测试验收唯一采用的性能门槛，不再保留其他冲突数值：

| 项目 | 数据条件 | P95 / 时限 |
|---|---|---:|
| 埋点接口 | 仅校验、HMAC、服务端去重和单条入库，不含公网网络延迟 | `<= 200ms` |
| 汇总、趋势、阶段规模、关注项 | 30 个完整自然日 | `<= 800ms` |
| 商品分页 | 10,000 个 SPU 日聚合数据、默认 20 条 | `<= 1.5s` |
| 当日聚合单轮 | 10,000,000 条 180 日事件、1,000,000 条订单明细基准库 | `< 4 分钟` |
| 正常数据水位延迟 | 定时任务正常 | `<= 10 分钟` |

- 交互查询不得对原始事件执行逐商品相关子查询；必须命中租户、事件类型、时间或租户、SPU、事件类型、时间索引。
- 366 天、`compare=true`、利润可见和无利润权限都必须单独压测，不得只测最小响应。
- 同一性能环境、数据集、预热次数和统计方法必须记录在验收证据中。

## 17. 兼容性约束

- 现有 `/statistics/product/*` 接口和 `product_spu.browse_count` 保留，但不参与新看板。
- 管理端复用 Vue 3、Element Plus、现有 `Echart`、Axios 封装和权限指令，不安装 React、Tailwind、Radix 或 shadcn 运行时。
- 路径阶段接口使用 `/stage-overview`；旧草案中的 `/funnel`、`previousConversionPercent`、`largestDropStep` 不属于 2.0 合同。
- 旧草案中的 `times[0]`、`times[1]`、客户端 `occurredAt`、客户端 `deviceType` 和匿名客户端自由 `tenant-id` 不属于 2.0 合同。
