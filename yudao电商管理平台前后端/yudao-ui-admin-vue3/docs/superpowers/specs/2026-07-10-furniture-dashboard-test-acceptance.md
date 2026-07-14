# 家具电商数据看板测试与验收方案

> 版本：2.0
> 日期：2026-07-10
> 原则：安全边界和指标口径优先于视觉；金额以租户币种最小单位断言；所有统计日固定为 `Asia/Shanghai`。

## 1. 测试范围与发布门禁

覆盖以下完整链路：

1. 家具前台在分析同意规则下生成或清除访客、会话和事件。
2. 网关执行精确 Origin、站点租户绑定、伪造头清理、体积限制和多级限流。
3. Statistics 服务使用服务端时间、HMAC、`eventId` 幂等和服务端 5 秒去重保存事件。
4. 交易服务保存订单成本快照，并正确区分仅退款与退货退款。
5. 聚合任务生成站点日聚合、商品日聚合、覆盖状态和分源水位。
6. 管理接口按 `SITE`/`PRODUCT` 返回汇总、趋势、阶段规模、关注项、商品分页和导出。
7. 管理后台正确处理默认 30 个完整自然日、筛选 URL、请求竞态、状态、权限、无障碍和响应式。
8. 数据库迁移、密钥轮换、性能、发布和回滚完成演练。

任一安全边界、租户隔离、金额口径、利润权限或未知数据伪装为 0 的用例失败，均为阻断上线问题。

## 2. 自动化测试与执行命令

### 2.1 家具前台

新增或扩展：

- `furniture web/tests/behaviorTracking.test.js`
- `furniture web/tests/dashboardTrackingIntegration.test.js`

必须覆盖：

```js
it('does not create IDs or send events before required analytics consent', () => {})
it('creates IDs and sends events after consent', () => {})
it('stops tracking and clears IDs after consent is withdrawn', () => {})
it('rotates a session after thirty minutes without a business event', () => {})
it('rotates a visitor ID when its configured TTL reaches at most 180 days', () => {})
it('reuses eventId for one network retry', () => {})
it('uses client suppression only as an optimization', () => {})
it('never sends tenantId, userId, occurredAt or deviceType', () => {})
it('sends only a normalized path and referrer host', () => {})
it('does not send public ADD_TO_CART and only attaches consented identity headers to the real cart request', () => {})
it('tracking rejection never rejects the commerce action', () => {})
```

运行：

```powershell
npm test -- behaviorTracking.test.js dashboardTrackingIntegration.test.js
npm test
npm run build
```

期望：全部测试 0 失败，构建退出码 0。

### 2.2 网关和 Statistics 服务

新增或扩展：

- `BehaviorEventGatewaySecurityTest.java`
- `BehaviorEventServiceImplTest.java`
- `BehaviorIdentityHasherTest.java`
- `DashboardAggregationServiceImplTest.java`
- `DashboardQueryServiceImplTest.java`
- `DashboardStatisticsControllerTest.java`
- `DashboardExportServiceTest.java`

运行：

```powershell
mvn -pl yudao-gateway,yudao-module-mall/yudao-module-statistics-server -am -DskipITs test
```

期望：网关和统计模块测试 0 失败。

### 2.3 交易服务

修改或扩展：

- `TradeOrderConvertTest.java`
- `TradeOrderUpdateServiceImplTest.java`
- `CartServiceImplTest.java`
- `CartAddedEventListenerTest.java`
- 售后服务中仅退款与退货退款的现有测试类

运行：

```powershell
mvn -pl yudao-module-mall/yudao-module-trade-server -am -DskipITs test
```

期望：新订单成本快照、退款归日、仅退款不恢复库存、退货退款冲回成本、购物车提交后服务端事件和 Statistics 故障不回滚加购全部通过。

### 2.4 管理后台

新增或扩展：

- `scripts/check-dashboard-contract.mjs`
- 看板 query/URL 状态单元测试
- 各区块请求竞态和权限单元测试
- 看板键盘与可访问性自动化测试

`check-dashboard-contract.mjs` 只承担静态边界检查，不能替代行为测试。静态检查至少确认：

- `/dashboard` 已加入 `allowedMenuPaths`，组件名统一为 `FurnitureDashboard`。
- API 使用 `scope`、`startDate`、`endDate`、`stage-overview`、`attention`，不再使用 `times[0]`、`times[1]` 或 `/funnel`。
- 页面使用现有 `Echart` 和 Element Plus，不导入 React、Radix 或 shadcn 运行时。
- 四类权限分别绑定基础查询、利润查询、普通导出和利润导出。

运行：

```powershell
pnpm check:dashboard
pnpm test
pnpm ts:check
pnpm build:local
```

期望：全部命令退出码 0。

## 3. 固定指标数据集

测试时钟固定为 `2026-07-10T10:30:00+08:00`，租户为 `121`，业务时区为 `Asia/Shanghai`，配置币种为 `USD`、最小单位位数为 2。

### 3.1 行为事件

聚合日为 `2026-07-01`，该日流量覆盖状态为 `COMPLETE`。

| 事件 | 数量 | 去重说明 |
|---|---:|---|
| `HOME_VIEW` | 10 | 访客 A 4 次、B 3 次、C 3 次 |
| `PRODUCT_DETAIL_VIEW` SPU-101 | 12 | A 5 次、B 4 次、C 3 次 |
| `PRODUCT_DETAIL_VIEW` SPU-102 | 8 | A 3 次、D 5 次 |
| `ADD_TO_CART` SPU-101 | 4 | A 3 次、B 1 次；加购访客 2 |
| `ADD_TO_CART` SPU-102 | 2 | D 2 次；加购访客 1 |
| `CHECKOUT_START` | 3 | A、B、D 各 1 个会话 |

预期：

- 首页 PV=10，首页 UV=3。
- 全站商品详情 PV=20，按自然日全站访客去重后 UV=4。
- SPU-101：PV=12、UV=3、加购次数=4、加购访客=2、加购率=`2/3=66.67%`。
- SPU-102：PV=8、UV=2、加购次数=2、加购访客=1、加购率=`1/2=50.00%`。
- 开始结算会话数=3。

### 3.2 订单与售后

| 订单 | 买家 | 商品 | 数量 | 单件成本 | 支付金额 | 售后 |
|---|---|---|---:|---:|---:|---|
| O-1 | A | SPU-101 | 2 | 3000 | 10000 | 无 |
| O-2 | B | SPU-101 | 1 | 3000 | 5000 | 退货退款 5000，恢复库存 |
| O-3 | D | SPU-102 | 1 | 4000 | 8000 | 仅退款 1000，不退货、不恢复库存 |

金额单位为 USD cents。预期站点指标：

- 支付订单数=3，支付买家数=3，支付件数=4。
- 支付销售额=23000，退款额=6000，净销售额=17000。
- 成本：O-1 为 6000，O-2 退货成本冲回为 0，O-3 仍为 4000，总成本=10000。
- 商品毛利=7000，毛利率=`7000/17000=41.18%`。
- 站点 PV 规模比=`3/10=30.00%`，站点 UV 规模比=`3/3=100.00%`。
- 支付客单价=`23000/3=7667` cents，按币种最小单位四舍五入；不使用会受跨期退款扭曲的净销售额。
- 退款率=`6000/23000=26.09%`。

预期商品指标：

- SPU-101 支付订单数=2、支付件数=3、净销售额=10000、成本=6000、毛利=4000、PV 转化率=`2/12=16.67%`。
- SPU-102 支付订单数=1、支付件数=1、净销售额=7000、成本=4000、毛利=3000、PV 转化率=`1/8=12.50%`。

### 3.3 水位和状态

固定水位：

- `trafficAsOf=2026-07-10T10:28:00+08:00`
- `tradeAsOf=2026-07-10T10:29:00+08:00`
- `refundAsOf=2026-07-10T10:27:00+08:00`
- `costAsOf=2026-07-10T10:29:00+08:00`

预期 `asOf=2026-07-10T10:27:00+08:00`，由最早相关水位决定；`trafficDataStatus=COMPLETE`、`freshnessStatus=FRESH`。

## 4. 埋点安全合同测试

### 4.1 精确 Origin 与站点租户绑定

测试配置允许 Origin `https://shop.example.test`、Host `shop.example.test`，映射租户 121。

| 编号 | 请求 | 预期 |
|---|---|---|
| SEC-01 | 精确 Origin、Host、合法匿名请求 | 写入租户 121 |
| SEC-02 | 外部携带 `tenant-id: 999` | 外部租户头被移除；只能按站点绑定到 121，不能写入 999 |
| SEC-03 | Origin `https://shop.example.test.evil.test` | 网关拒绝 |
| SEC-04 | Origin 使用未配置端口 | 网关拒绝 |
| SEC-05 | 缺失 Origin | 网关拒绝 |
| SEC-06 | `Origin: null` | 网关拒绝 |
| SEC-07 | 会员令牌租户 999、站点租户 121 | 拒绝，不写事件 |
| SEC-08 | SPU 属于租户 999 | 拒绝且不泄露跨租户商品是否存在 |
| SEC-09 | 直接从公网访问 Statistics 服务 | 网络策略拒绝，只有网关路由可达 |
| SEC-10 | 伪造内部用户或租户头 | 网关清除，不能改变安全上下文 |
| SEC-11 | 公共追踪请求伪造 `ADD_TO_CART` 或 `eventSource=SERVER_CART` | 参数错误，不写加购事件 |

同时验证预检和实际 POST 使用同一精确 Origin 白名单，错误响应不反射任意 Origin。

### 4.2 服务端时间

- 冻结服务端时间为 `2026-07-01T23:59:59.900+08:00`，写入事件必须归属 7 月 1 日。
- 冻结为 `2026-07-02T00:00:00.000+08:00`，必须归属 7 月 2 日。
- 请求额外伪造 `occurredAt`、`deviceType`、`userId`、`tenantId` 或其他非合同字段时必须返回参数错误，不得通过忽略字段形成第二种客户端行为。
- 数据库 `occurred_at` 与服务端接收时钟一致，不使用浏览器时间。

### 4.3 HMAC 和自然日边界轮换

必须覆盖：

- 相同租户、相同原始 UUID、相同密钥版本得到同一 64 位 HMAC。
- 不同租户或不同密钥版本得到不同 HMAC。
- 数据库、日志和响应中不存在原始 `visitorId`、`sessionId` 或 HMAC 密钥。
- 生产 profile 缺少当前密钥或版本时应用启动失败；不存在生产默认密钥。
- 轮换配置在上海时区日中发布时，当日后续新事件仍使用原版本。
- `2026-07-01T23:59:59.999+08:00` 使用版本 V1，`2026-07-02T00:00:00.000+08:00` 起统一使用 V2。
- 同一租户同一上海自然日聚合数据中只能出现一个写入密钥版本，禁止日内混版导致 UV 翻倍。
- 旧日期重算仍能读取相应旧版本密钥；旧密钥不得在原始事件保留和重算窗口结束前销毁。

### 4.4 `eventId` 幂等和服务端 5 秒去重

| 编号 | 场景 | 预期 |
|---|---|---|
| DEDUP-01 | 相同 `(tenantId,eventId)` 请求两次 | 一条记录，两次均返回成功 |
| DEDUP-02 | 不同 eventId、相同服务端复合键，间隔 4.999 秒 | 只计一条 |
| DEDUP-03 | 不同 eventId、相同复合键，间隔 5.001 秒 | 计两条 |
| DEDUP-04 | 相同访客但 SPU 或 SKU 不同 | 各自计数 |
| DEDUP-05 | 相同 eventId 在 5 秒后重试 | 仍只计一条 |
| DEDUP-06 | 非 eventId 唯一键数据库异常 | 返回失败并监控，不吞异常 |
| DEDUP-07 | 20 个并发不同 eventId、相同复合键 | Redis 原子窗口只允许一条进入业务计数 |
| DEDUP-08 | 公共去重 Redis 不可用 | 临时失败、不入库、告警并使覆盖为 PARTIAL；前台业务不受影响 |

5 秒窗口必须使用服务端时间和原子 `SET NX`/TTL，客户端抑制关闭时上述规则仍成立。`SERVER_CART` 只验证服务端 eventId 幂等，不因公共 Redis 窗口误丢不同真实加购。

### 4.5 体积、限流、质量和日志

- 8 KB 以内合法请求通过；超过 8 KB 在网关拒绝。
- 单 IP 第 121 次/分钟、单访客第 121 次/分钟、单租户第 6001 次/分钟被限流。
- 不断改变 `eventId` 不能绕过 IP、访客或租户限流。
- Bot、内部 CIDR、服务端签名测试流量和异常高频事件分别记录安全原因，不进入业务指标。
- 客户端伪造测试标记不能排除真实事件。
- 应用日志、网关日志和链路追踪扫描不得出现原始 UUID、Token、请求体、完整 IP、完整 Referrer 或完整 User-Agent。
- 真实加购只有 Trade 事务提交后产生一条 `eventSource=SERVER_CART` 事件；事务回滚不产生，重复内网 RPC 幂等，Statistics 故障不改变购物车成功结果。购物车专用分析身份头不进入网关、Trade 或异常日志。

## 5. 分析同意与覆盖偏差测试

| 编号 | 策略与操作 | 预期 |
|---|---|---|
| CONSENT-01 | 目标地区要求同意，用户未同意 | 不创建 visitor/session ID，不发任何事件 |
| CONSENT-02 | 用户同意 | 创建标识，从同意时刻开始上报 |
| CONSENT-03 | 用户撤回同意 | 立即停止上报并清除本地标识 |
| CONSENT-04 | 同意策略关闭 | 按配置首次访问创建标识 |
| CONSENT-05 | 未同意状态 | 不启用 cookieless、临时 ID 或不可识别汇总替代模式 |
| CONSENT-06 | 访客标识达到租户配置 TTL（最长 180 天） | 生成新 UUID 和创建时间，不继续使用过期标识 |

管理端在流量指标和口径帮助中必须展示：“流量指标仅代表已同意分析的可测量访问，可能存在覆盖偏差”。不得把未同意访问估算成 PV/UV。

## 6. 范围、日期和比较期测试

### 6.1 默认日期

时钟固定为 `2026-07-10T10:30:00+08:00`：

- 首次进入默认 `startDate=2026-06-10`、`endDate=2026-07-09`、`compare=true`。
- 参考期为 `2026-05-11` 至 `2026-06-09`。
- 选择今日得到 `startDate=endDate=2026-07-10`、`compare=false`、状态 `PARTIAL`，页面显示“未完整日”和“截至 10:xx”。
- 今日强行传 `compare=true` 时 API 返回参数错误。
- 默认/自定义完整日区间若包含埋点启用前或采集缺失日期，`compare=true` 请求仍成功：财务比较可返回，流量及相关比较为 `null` 并标记 `PARTIAL`/`UNAVAILABLE`，页面不能整体报错。
- 浏览器设置为 UTC、America/Los_Angeles 和 Asia/Shanghai 时，上述 URL 日期完全一致。
- 自定义 2026-07-01 至 2026-07-30 恰好统计 30 个上海自然日；结束日事件包含到 7 月 30 日 23:59:59.999。
- 包含首尾超过 366 天时前后端都拒绝。

### 6.2 `SITE`/`PRODUCT`

| 编号 | 请求 | 预期 |
|---|---|---|
| SCOPE-01 | `scope=SITE` 无商品参数 | 返回首页和全站指标 |
| SCOPE-02 | `scope=SITE&categoryId=91` | 参数错误 |
| SCOPE-03 | `scope=SITE&spuId=101` | 参数错误 |
| SCOPE-04 | `scope=PRODUCT&categoryId=91` | 只返回分类商品指标，不返回首页指标 |
| SCOPE-05 | `scope=PRODUCT&spuId=101` | 返回 SPU-101 指标，不返回首页指标 |
| SCOPE-06 | 商品属于分类 | 两参数可同时使用 |
| SCOPE-07 | 商品不属于分类 | 参数错误，不静默覆盖 |
| SCOPE-08 | 跨租户商品或分类 | 拒绝且数据不串租户 |

### 6.3 阶段规模而非漏斗

- `SITE` 返回首页、商品详情、加购、结算、支付买家五项，全部 `applicability=APPLICABLE`。
- `PRODUCT` 只计算商品详情、加购和支付买家；首页和无商品维度的 `CHECKOUT_START` 返回 `value=null`、`applicability=NOT_APPLICABLE` 和解释。
- `PRODUCT` 趋势响应和图表不包含首页 PV/UV。
- 两种 scope 都返回 `cohortAligned=false`、`unit`、`dedupeScope` 和固定口径说明。
- API 和页面均不存在 `previousConversionPercent`、`largestDropStep`、“归因漏斗”或“最大流失”措辞。
- 构造直接进入商品页、跨日和匿名转登录数据时，不计算任何可能超过 100% 的逐层转化率。

## 7. 数据状态、水位和空值测试

| 编号 | 数据情况 | API | 页面 |
|---|---|---|---|
| DATA-01 | 已确认采集正常且当天无访问 | PV/UV 为 0、状态 `COMPLETE` | 零轴和 0 值 |
| DATA-02 | 区间早于 `trafficDataAvailableFrom` | PV/UV、相关转化率为 `null`、状态 `PARTIAL` 或 `UNAVAILABLE` | `—` 与“流量未采集”，趋势断线 |
| DATA-03 | 区间内一天采集缺失 | 缺失日为 `null`、状态 `PARTIAL` | 断线，不补 0 |
| DATA-04 | 今日 | 状态 `PARTIAL` | “未完整日” |
| DATA-05 | 最新相关水位延迟 10 分钟 1 秒 | `freshnessStatus=DELAYED` | “数据更新延迟”非阻断提示 |
| DATA-06 | 最新相关水位延迟 20 分钟 1 秒 | `freshnessStatus=STALE` | 严重提示，保留并标明上次成功数据 |
| DATA-07 | 单个接口失败 | 其他接口成功 | 仅失败区块原位重试 |
| DATA-08 | 无利润权限 | 利润字段不存在 | 不显示利润控件，不显示 0 |

每个接口单独断言 `snapshotId`、`asOf`、`lastSuccessfulRunAt`、`watermarks`。`asOf` 必须等于该接口所依赖水位的最早值，页头使用当前成功区块的最早 `asOf`，不能只取 summary。

## 8. 指标、退款和利润质量测试

### 8.1 固定数据集

第 3 节所有数值必须精确匹配。额外断言：

- 率类比较返回 `changePercentagePoints`，计数/金额返回 `changeAmount`、`changePercent`。
- 参考值为 0 时不返回无穷大或伪造百分比，页面显示“无可比数据”。
- 构造区间前浏览、区间内支付使规模比超过 100% 的场景，API 保留结果、页面显示非 cohort 口径，不截断为 100%。
- 净销售额小于等于 0 时 `grossMarginPercent=null`。
- 所有商品行的加购率、PV 转化率、UV 转化率和退款率都能从同一行字段复算。

### 8.2 利润质量

| 场景 | 精确明细 | 估算明细 | 缺失成本 | 期望状态 | 利润值 |
|---|---:|---:|---:|---|---|
| 全部新订单 | 3 | 0 | 0 | `EXACT` | 数值 |
| 新旧订单混合 | 2 | 1 | 0 | `MIXED` | 数值并显示“约” |
| 全部历史回填 | 0 | 3 | 0 | `ESTIMATED` | 数值并显示“约” |
| 任一成本缺失 | 2 | 0 | 1 | `INCOMPLETE` | `costAmount`、`grossProfit`、`grossMarginPercent` 全部 `null` |
| 区间无支付成本入账或退货成本冲回 | 0 | 0 | 0 | `NOT_APPLICABLE` | 利润率不适用；仅退款可产生负毛利但不产生成本移动 |

每种状态同时断言 `exactCostItemCount`、`estimatedCostItemCount`、`missingCostItemCount`、`knownCostAmount`、`costCoveragePercent`。三类数量按成本移动行计算：支付日计成本入账，退货成功日计成本冲回，同一订单明细可跨日各参与一次。页面不允许只通过颜色表达质量。

`costCoveragePercent` 按已知行数除以三类总行数计算；无成本移动时为 `null`，不能显示 100%，也不能解释为金额覆盖率或估算准确率。

### 8.3 支付与退款归日

- 支付订单和下单成本按 `trade_order.pay_time` 归日。
- 退款和退货成本冲回按 `trade_after_sale.refund_time` 归日，不回写支付日。
- 仅退款减少净销售额但不恢复库存、不减少成本。
- 退货退款只有在售后完成、方式为 `RETURN_AND_REFUND` 且退货数量明确时恢复库存和冲回对应成本。
- 多次部分退款、跨午夜退款、超额异常数据、取消和退款未成功状态均有独立断言。
- 构造“当期退款来自历史支付且大于当期支付销售额”的合法跨期场景，退款率可超过 100%、净销售额可为负；支付客单价仍按当期支付销售额/支付订单数计算，页面不得截断或误报。
- 每日低峰滚动修复最近 7 天后，迟到退款进入正确退款日且重复重算结果不变。

## 9. 关注项、商品表和导出测试

### 9.1 运营关注项

- 阈值从租户配置读取并完整返回，页面显示“规则提示，不代表自动诊断”。
- `SITE` 首屏按相同日期、无商品筛选显示“全部商品关注”，其规则不使用首页流量；`PRODUCT` 才沿用分类/SPU。
- `HIGH_TRAFFIC_LOW_CONVERSION`、`HIGH_REFUND`、`LOW_OR_NEGATIVE_MARGIN`、`MISSING_COST` 使用固定数据分别命中和不命中边界值。
- 样本量或支付销售额低于门槛时不误报。
- 流量覆盖不完整时，高流量低转化进入 `notEvaluated` 而不是返回 0 个命中；利润 `INCOMPLETE` 时低毛利不评估但成本缺失仍命中。
- 无利润权限时 API 不计算、不返回利润类关注项及命中数。
- 所有有权限规则均已评估且无命中才显示“当前规则下未发现关注项”；存在未评估规则时显示原因，不给出虚假安全结论。
- 点击关注项后 URL 切到 `scope=PRODUCT`、写入正确 `riskType`、分页回到 1，并定位商品表；清除后恢复普通列表。

### 9.2 商品表现表

- 默认按 `detailPv desc`，商品列和操作列固定。
- 分类约束商品候选；下架/删除商品有状态且不会打开无效编辑入口。
- 所有允许排序字段使用白名单；任意 SQL 片段、未知字段和无权限利润字段被拒绝。
- 当前期、参考期和变化字段一致；关闭比较后行内不残留参考数据。
- 快速筛选和普通排序、分页、关键字共同生效，URL 可复现。
- 分类、上架状态和库存明确标注为当前值；商品改类后历史指标按当前分类归组，页面和导出说明不把它误称为历史分类快照。

### 9.3 USD 配置币种与导出

- 租户 121 响应 `currencyCode=USD`、`currencyMinorUnit=2`，页面将 23000 显示为 `$230.00` 或等价的明确 USD 表达，不出现 `¥` 或“元”。
- 将测试租户币种配置替换为另一 ISO 4217 币种时，API、页面和导出跟随配置，不依赖 USD 硬编码。
- 普通导出不含成本、毛利或利润质量；利润导出同时校验利润查询和利润导出权限。
- “数据”表金额为数值单元格且列头标注币种；“说明”表包含生成/截至时间、上海时区、币种、scope、日期、参考期、筛选、排序、状态和公式。
- 输入以 `=cmd`、`+SUM`、`-1+1`、`@x`、制表符或回车开头的商品名称，Excel 中不能成为公式。
- 第 10,001 行或同用户 10 分钟第 4 次导出被拒绝并给出缩小范围提示。
- 审计记录包含操作人、租户、权限类型、筛选摘要、行数、文件哈希、追踪编号和结果，不包含导出内容。

## 10. 权限验收

| 角色 | 基础查询 | 利润查询 | 普通导出 | 利润导出 | 预期 |
|---|---|---|---|---|---|
| 管理员 | 是 | 是 | 是 | 是 | 全部能力 |
| 普通运营 | 是 | 否 | 否 | 否 | 只看流量、订单、收入、退款 |
| 运营导出角色 | 是 | 否 | 是 | 否 | 普通导出不含利润 |
| 财务查看角色 | 是 | 是 | 否 | 否 | 可看利润但不能导出 |
| 财务导出角色 | 是 | 是 | 是 | 是 | 可导出利润 |
| 无权限用户 | 否 | 否 | 否 | 否 | 无菜单且直接访问拒绝 |

验收同时检查网络响应、序列化对象、页面 DOM 和 Excel，确保无利润权限时不存在敏感字段，而不是仅靠 CSS 隐藏。商品、订单和售后深链继续服从目标模块权限。

## 11. URL、筛选联动和请求竞态验收

### 11.1 URL

规范 URL 必须包含适用的：

`scope`、`startDate`、`endDate`、`compare`、`categoryId`、`spuId`、`riskType`、`keyword`、`sortField`、`sortOrder`、`pageNo`、`pageSize`。

验收：

- 刷新、复制 URL 到新标签页和浏览器前进/后退均恢复同一筛选与表格状态。
- 无参数 URL 自动写入 `SITE` 和默认 30 个完整日。
- 切换到 `SITE` 清除商品专属参数；切换分类清除不属于该分类的 SPU 并提示。
- 日期、scope、分类、商品、关注类型或关键字变化时 `pageNo` 重置为 1。
- 非法枚举、越界日期或冲突商品分类恢复为安全默认并显示提示，不发无效请求。

### 11.2 竞态

构造请求 A 比请求 B 晚返回：

1. 选择分类 A，触发所有相关请求。
2. 立即选择分类 B，取消 A 并触发 B。
3. 强制 A 在 B 后返回。

预期：

- 旧请求通过 `AbortController` 取消；即使无法取消，序号或 `queryKey` 也会丢弃 A。
- 卡片、趋势、阶段规模、关注项和商品表最终全部显示 B，不能混合 A/B。
- 单区块失败不取消其他区块；原位重试保留当前筛选和焦点。
- 商品搜索至少 300ms 防抖；手动刷新绕过 30 秒缓存。

## 12. 无障碍与响应式验收

### 12.1 自动化

- axe 或等价工具无 serious/critical 问题。
- 所有表单控件具有可见标签和可计算名称。
- ECharts 启用 `aria`，每个图表存在“查看数据表”入口。
- 异步加载、延迟、失败和重试结果使用 `aria-live="polite"`。
- 上涨、下降、缺失、估算、过期和风险不只通过颜色表达。

### 12.2 键盘和屏幕阅读器

仅使用键盘完成：

1. `SITE`/`PRODUCT` 切换。
2. 快捷日期、自定义日期、分类和商品选择。
3. 图表模式和可聚焦 Tooltip。
4. 关注项定位与清除。
5. 表格排序、分页、列设置和商品操作。
6. 刷新、错误重试和有权限的导出。

焦点顺序与视觉顺序一致；筛选抽屉关闭、区块重试和导出完成后焦点返回合理位置。屏幕阅读器能读出指标名、当前值、比较、日期范围、币种、覆盖状态和利润质量。

### 12.3 响应式

在 1440、1280、1024、768、390px 及浏览器 200% 缩放下验收：

- 无核心控件遮挡、裁切或不可触达。
- 商品列和操作列固定后不遮挡中间列，横向滚动有可见且可访问提示。
- 大数字、长商品名、`INCOMPLETE` 文案和 USD 金额不会撑破卡片。
- 筛选抽屉没有焦点陷阱，关闭后返回触发按钮。

## 13. 唯一性能验收

设计、API 和本方案只使用以下门槛：

| 项目 | 数据条件 | P95 / 时限 |
|---|---|---:|
| 埋点接口 | 校验、HMAC、服务端去重和单条入库；不含公网网络延迟 | `<= 200ms` |
| 汇总、趋势、阶段规模、关注项 | 30 个完整自然日 | `<= 800ms` |
| 商品分页 | 10,000 个 SPU 日聚合、默认 20 条 | `<= 1.5s` |
| 当日聚合单轮 | 10,000,000 条 180 日事件、1,000,000 条订单明细 | `< 4 分钟` |
| 正常数据水位延迟 | 定时任务正常 | `<= 10 分钟` |

执行要求：

- 记录数据库规格、应用实例规格、数据生成脚本、预热次数、并发数、样本数和 P95 计算方法。
- 分别测试 `SITE`、`PRODUCT`、`compare=true`、利润可见、无利润权限、366 天边界和最坏允许排序。
- 聚合 SQL 必须命中租户/类型/时间或租户/SPU/类型/时间索引；商品分页不得对原始事件做逐商品相关子查询。
- 任何文档、测试脚本或发布票据保留与本节不同的旧冲突门槛，均视为合同未清理完成。

## 14. 数据库迁移与任务验收

1. 在生产结构副本执行迁移一次，退出码 0。
2. 再执行同一迁移，退出码 0。
3. 行为事件、日聚合、商品统计和订单成本字段类型与设计一致，金额字段使用 `bigint`/Java `Long`。
4. 菜单、`FurnitureDashboard`、四类权限、聚合任务和清理任务各只有一份。
5. 历史回填不覆盖已有成本；缺失成本保持空，不写 0。
6. 默认只为配置启用租户 121 采集和聚合；其他租户任务不运行。
7. 今日和昨日每 5 分钟重算、最近 7 天滚动修复均幂等；跨午夜迟到数据进入正确日期。
8. 原始事件只有在保留期已到、聚合水位已越过且校验完成后物理删除。
9. 迁移前后订单、商品、购物车和支付核心表行数不减少。

## 15. 人工业务验收用例

### 15.1 前台和安全

| 编号 | 操作 | 预期 |
|---|---|---|
| EVT-01 | 要求同意的地区，未同意访问首页 | 不创建 ID、不发事件 |
| EVT-02 | 同意后首次进入首页 | 产生 1 条绑定租户 121 的 `HOME_VIEW` |
| EVT-03 | 5 秒内重复首页处理 | 服务端不计第二条 |
| EVT-04 | 5 秒后刷新 | 新增 1 条 |
| EVT-05 | 打开有效商品 | 数据成功后产生 `PRODUCT_DETAIL_VIEW` |
| EVT-06 | 打开无效或跨租户商品 | 不产生事件 |
| EVT-07 | 真实加购成功 | Trade 事务提交后保存一条 `SERVER_CART` 事件及 SPU、SKU、数量；浏览器不另发公共加购事件 |
| EVT-08 | 本地预览或接口失败 | 不产生真实加购，业务仍可用 |
| EVT-09 | 有效结算 | 产生 `CHECKOUT_START`，随后正常导航 |
| EVT-10 | 追踪接口 500/429/超时 | 浏览、加购、结算和下单不受影响 |
| EVT-11 | 撤回同意 | 停止上报并清除本地 ID |
| EVT-12 | 修改 Origin/tenant-id/内部头 | 请求被拒绝或只能使用安全站点上下文 |

### 15.2 管理后台

| 编号 | 检查 | 预期 |
|---|---|---|
| UI-01 | 首次进入 | `SITE`、截至昨日 30 个完整日、上一周期比较 |
| UI-02 | 选择今日 | 部分数据、截至时间、无比较 |
| UI-03 | 切换 `PRODUCT` | 出现分类/商品筛选，首页指标隐藏 |
| UI-04 | 冲突分类与商品 | 清除并提示或 API 拒绝，不静默覆盖 |
| UI-05 | 查看阶段规模 | 固定非 cohort 说明，无漏斗转化和最大流失 |
| UI-06 | `PRODUCT` 阶段规模 | 首页/结算不适用，只展示商品可归属阶段 |
| UI-07 | 在 SITE/PRODUCT 查看关注项 | SITE 显示全部商品关注，PRODUCT 沿用商品筛选；阈值、规则说明、定位和清除正确 |
| UI-08 | 流量缺失日 | 断线和原因，不显示 0 |
| UI-09 | 水位延迟 11/21 分钟 | 分别显示延迟/严重提示 |
| UI-10 | 成本缺失 | 利润为 `—`，显示缺失数，不显示部分利润 |
| UI-11 | 无利润权限 | 页面、网络和导出都无利润数据 |
| UI-12 | 商品表排序分页 | 参数、顺序、比较和 URL 一致 |
| UI-13 | 快速切换筛选 | 无旧响应覆盖和跨筛选混合 |
| UI-14 | 导出 | USD、说明表、筛选排序、权限和质量一致 |
| UI-15 | 单接口失败 | 仅该区块重试，其他区块保留 |
| UI-16 | 键盘、读屏、缩放和响应式 | 符合第 12 节 |

## 16. 最终验收门禁

以下全部满足才允许上线：

- 家具前台定向测试、全量测试和构建通过。
- 网关、交易服务和 Statistics 服务测试通过。
- 精确 Origin、站点租户绑定、跨租户拒绝、HMAC、服务端时间、5 秒去重和日志脱敏有自动化证据。
- HMAC 轮换在上海自然日边界演练通过，同一自然日无混版。
- 管理端静态合同、行为测试、类型检查、构建、URL/竞态和无障碍测试通过。
- 固定指标数据集、退款归日、仅退款不恢复库存和五种利润质量全部精确匹配。
- `SITE`/`PRODUCT`、阶段规模、状态/水位、关注项、USD 币种和四类权限人工验收通过。
- 数据库迁移在生产结构副本连续执行两次通过，启用租户和任务范围正确。
- 第 13 节唯一性能门槛全部通过，正常聚合水位延迟不超过 10 分钟。
- 导出行数/频率、公式注入、审计和利润权限通过。
- 发布与回滚手册完成演练，记录执行人、时间、配置摘要和证据链接。
