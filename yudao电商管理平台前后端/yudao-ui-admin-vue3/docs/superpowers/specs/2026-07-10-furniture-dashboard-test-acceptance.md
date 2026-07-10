# 家具电商数据看板测试与验收方案

> 日期：2026-07-10
> 原则：指标口径测试优先于视觉测试；所有金额以分断言；所有时间固定为 Asia/Shanghai。

## 1. 测试范围

覆盖以下链路：

1. 家具前台生成访客、会话和行为事件。
2. 统计服务校验、去重、哈希和保存事件。
3. 交易服务保存订单成本快照。
4. 聚合任务生成站点日聚合和商品日聚合。
5. 管理接口计算汇总、趋势、漏斗和商品分页。
6. 管理后台展示、筛选、排序和导出。
7. 数据库迁移、权限、发布和回滚。

## 2. 自动化测试文件

### 2.1 家具前台

新增：

- `furniture web/tests/behaviorTracking.test.js`
- `furniture web/tests/dashboardTrackingIntegration.test.js`

核心测试：

```js
it("reuses visitor id and rotates session id per tab session", () => {})
it("suppresses the same event key for five seconds", () => {})
it("keeps event id stable across one retry", () => {})
it("tracks HOME_VIEW when route becomes root", () => {})
it("tracks PRODUCT_DETAIL_VIEW only after valid product data resolves", () => {})
it("tracks ADD_TO_CART only after remote cart succeeds", () => {})
it("does not track local preview cart fallback", () => {})
it("tracking rejection never rejects the commerce action", () => {})
```

运行：

```powershell
npm test -- behaviorTracking.test.js dashboardTrackingIntegration.test.js
npm run build
```

期望：Vitest 0失败，Vite构建退出码0。

### 2.2 交易服务

修改：

- `TradeOrderConvertTest.java`
- `TradeOrderUpdateServiceImplTest.java`

核心测试：

```java
@Test
void convertOrderItem_copiesSkuCostSnapshot() {
    TradePriceCalculateRespBO.OrderItem source = new TradePriceCalculateRespBO.OrderItem()
            .setSkuId(10L).setSpuId(20L).setCount(2).setCostPrice(3500);
    TradeOrderItemDO item = TradeOrderConvert.INSTANCE.convert(source);
    assertEquals(3500, item.getCostPrice());
    assertFalse(item.getCostEstimated());
}
```

运行：

```powershell
mvn -pl yudao-module-mall/yudao-module-trade-server -am -DskipITs test
```

期望：交易模块测试0失败，新订单明细成本快照断言通过。

### 2.3 统计服务

新增：

- `BehaviorEventServiceImplTest.java`
- `DashboardAggregationServiceImplTest.java`
- `DashboardQueryServiceImplTest.java`
- `DashboardStatisticsControllerTest.java`

必须覆盖：

- 匿名事件正常保存，登录事件关联服务端用户编号。
- 原始访客ID和会话ID不落库，只保存64位哈希。
- 同一 `(tenantId,eventId)` 重复请求只保留一条。
- 商品详情事件缺失 `spuId` 时校验失败。
- 客户端时间偏差超过24小时使用服务器时间。
- 日区间使用左闭右开，午夜事件只属于新一天。
- 重算同一天两次得到完全相同的聚合值。
- 重算能吸收迟到支付和迟到退款。
- 站点与商品转化率分母为0时返回0。
- 商品订单数按订单去重，不按件数。
- 数据质量四种状态全部有测试。
- 趋势接口补齐无数据日期。
- 商品排序字段白名单拒绝任意SQL字段。
- 查询权限和导出权限独立。

运行：

```powershell
mvn -pl yudao-module-mall/yudao-module-statistics-server -am -DskipITs test
```

期望：统计模块测试0失败。

### 2.4 管理后台

新增轻量契约检查：

- `scripts/check-dashboard-contract.mjs`
- `package.json` 脚本 `check:dashboard`

检查内容：

- `/dashboard` 已加入 `allowedMenuPaths`。
- API字段包含 `homePv`、`productDetailPv`、`paidOrderCount`、`grossProfit`。
- 页面包含7/30/90天、日期范围、分类、商品和上一周期筛选。
- 页面使用现有 `Echart` 和 Element Plus，不导入 React/shadcn/Radix。
- 导出按钮绑定 `statistics:dashboard:export`。

运行：

```powershell
pnpm check:dashboard
pnpm ts:check
pnpm build:local
```

期望：三条命令退出码0。

## 3. 固定指标数据集

自动化聚合测试使用租户121、日期2026-07-01。

### 3.1 行为事件

| 事件 | 数量 | 去重说明 |
|---|---:|---|
| HOME_VIEW | 10 | 访客A 4次、B 3次、C 3次 |
| PRODUCT_DETAIL_VIEW SPU-101 | 12 | A 5次、B 4次、C 3次 |
| PRODUCT_DETAIL_VIEW SPU-102 | 8 | A 3次、D 5次 |
| ADD_TO_CART SPU-101 | 4 | A 3次、B 1次 |
| ADD_TO_CART SPU-102 | 2 | D 2次 |
| CHECKOUT_START | 3 | A、B、D各1次 |

预期流量：

- 首页 PV=10，首页 UV=3。
- 商品详情 PV=20，全站商品详情 UV=4。
- SPU-101 PV=12、UV=3、加购次数=4、加购访客=2。
- SPU-102 PV=8、UV=2、加购次数=2、加购访客=1。
- 开始结算会话数=3；`statistics_traffic_daily.checkout_start_count` 按 `session_hash` 去重，不是原始事件条数。

### 3.2 订单与售后

| 订单 | 买家 | 商品 | 数量 | 单件成本 | 支付金额 | 售后 |
|---|---|---|---:|---:|---:|---|
| O-1 | A | SPU-101 | 2 | 3000 | 10000 | 无 |
| O-2 | B | SPU-101 | 1 | 3000 | 5000 | 退货退款5000，恢复库存 |
| O-3 | D | SPU-102 | 1 | 4000 | 8000 | 仅退款1000，不退货 |

预期站点指标：

- 支付订单数=3，支付买家数=3，支付件数=4。
- 支付销售额=23000，退款额=6000，净销售额=17000。
- 成本：O-1为6000，O-2退货成本归零，O-3仍为4000，总成本=10000。
- 商品毛利=7000，毛利率=`7000/17000=41.18%`。
- 站点PV转化率=`3/10=30.00%`。
- 站点UV转化率=`3/3=100.00%`。
- 客单价=`17000/3=5666`，整数分按四舍五入。
- 退款率=`6000/23000=26.09%`。

预期商品指标：

- SPU-101支付订单数=2、支付件数=3、净销售额=10000、成本=6000、毛利=4000、PV转化率=`2/12=16.67%`。
- SPU-102支付订单数=1、支付件数=1、净销售额=7000、成本=4000、毛利=3000、PV转化率=`1/8=12.50%`。

## 4. 利润数据质量测试

| 场景 | 精确明细 | 估算明细 | 缺失成本 | 期望状态 |
|---|---:|---:|---:|---|
| 全部新订单 | 3 | 0 | 0 | EXACT |
| 新旧订单混合 | 2 | 1 | 0 | MIXED |
| 全部历史回填 | 0 | 3 | 0 | ESTIMATED |
| 任一成本缺失 | 2 | 0 | 1 | INCOMPLETE |
| 区间无订单 | 0 | 0 | 0 | EXACT |

页面对 `MIXED`、`ESTIMATED`、`INCOMPLETE` 必须显示解释性标签；不允许只通过颜色表达状态。

## 5. 埋点验收用例

| 编号 | 操作 | 预期 |
|---|---|---|
| EVT-01 | 未登录首次打开首页 | 新增1条HOME_VIEW，user_id为空 |
| EVT-02 | 5秒内重复执行相同首页处理 | 不新增第二条 |
| EVT-03 | 5秒后刷新首页 | 新增1条HOME_VIEW |
| EVT-04 | 打开有效商品详情 | 数据加载成功后新增PRODUCT_DETAIL_VIEW |
| EVT-05 | 打开不存在商品 | 不新增PRODUCT_DETAIL_VIEW |
| EVT-06 | 未登录点击真实加购并被认证拦截 | 不新增ADD_TO_CART |
| EVT-07 | 登录后真实加购成功 | 新增ADD_TO_CART并保存SPU、SKU、数量 |
| EVT-08 | 后端不可用，使用本地预览购物车 | 不新增真实ADD_TO_CART，页面仍可操作 |
| EVT-09 | 点击结算且购物车有效 | 新增CHECKOUT_START，随后正常导航 |
| EVT-10 | 埋点接口返回500 | 用户业务流程不受影响 |

## 6. 管理后台验收用例

| 编号 | 检查 | 预期 |
|---|---|---|
| UI-01 | 有权限用户登录 | 左侧出现独立“数据看板” |
| UI-02 | 无权限用户登录 | 不出现菜单，直接访问被拒绝 |
| UI-03 | 首次进入 | 默认最近30天，对比前30天 |
| UI-04 | 切换7/30/90天 | 所有区块同时刷新 |
| UI-05 | 自定义日期超过366天 | 前端阻止并提示 |
| UI-06 | 选择分类 | 卡片、图表、漏斗、表格共同过滤 |
| UI-07 | 选择商品 | 指标和趋势切换为该商品口径 |
| UI-08 | 表格排序分页 | 请求参数和展示顺序正确 |
| UI-09 | 导出 | Excel与当前筛选排序一致 |
| UI-10 | 单个接口失败 | 仅对应区块显示重试 |
| UI-11 | lastUpdatedAt超过20分钟 | 显示数据更新延迟 |
| UI-12 | 手机宽度 | 筛选进入抽屉，表格可横向滚动 |

## 7. 性能验收

测试数据：180天行为事件1000万条、商品5000个、订单明细100万条。

| 项目 | 标准 |
|---|---|
| 埋点接口P95 | 小于100ms，不含网关网络延迟 |
| 汇总接口P95 | 小于800ms |
| 30天趋势P95 | 小于800ms |
| 商品分页P95 | 小于1200ms |
| 当日聚合 | 小于4分钟，避免与5分钟周期重叠 |
| 管理页面首次可交互 | 本地生产构建环境小于3秒 |

执行 `EXPLAIN ANALYZE` 时，事件聚合必须命中 `idx_tenant_type_time` 或 `idx_tenant_spu_type_time`，商品分页不得对原始事件做逐商品相关子查询。

## 8. 数据库迁移验收

1. 在生产结构副本执行迁移一次，退出码0。
2. 再执行同一迁移，退出码0。
3. 两张新表、所有新增列和唯一索引存在。
4. 菜单、权限按钮和任务各只有一份。
5. 历史回填不覆盖已有 `cost_price`。
6. 回填后缺失成本数量可准确查询。
7. 迁移前后订单、商品、购物车和支付核心表行数不减少。

## 9. 最终验收门禁

以下项目全部满足才允许上线：

- 家具前台定向测试、全量测试和构建通过。
- 交易服务与统计服务测试通过。
- 管理端契约检查、类型检查和构建通过。
- 数据库迁移在副本上连续执行两次通过。
- 固定指标数据集所有数值精确匹配。
- 权限、导出、响应式和失败状态人工验收通过。
- 聚合延迟小于10分钟。
- 发布与回滚手册完成演练并记录执行人和时间。
