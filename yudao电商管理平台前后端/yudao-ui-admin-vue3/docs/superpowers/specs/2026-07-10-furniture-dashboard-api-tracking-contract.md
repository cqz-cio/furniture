# 家具电商数据看板 API 与埋点协议

> 版本：1.0
> 日期：2026-07-10
> 时区：Asia/Shanghai

## 1. 通用约定

- 家具前台接口前缀：`/app-api`。
- 管理后台接口前缀：`/admin-api`。
- 后端 Controller 内部路径不包含上述网关前缀。
- 金额请求和响应均为整数“分”；管理后台显示时转换为元。
- 百分比响应使用数值类型，`10.25` 表示 `10.25%`。
- 日期区间参数为左闭右开：`times[0] <= time < times[1]`。
- 管理后台最大查询跨度366天，默认最近30个完整/当前自然日。
- `CommonResult` 成功结构为 `{ "code": 0, "data": {}, "msg": "" }`，其中 `data` 由各接口定义。

## 2. 埋点事件

### 2.1 客户端标识

家具前台维护：

- `oakved_visitor_id`：localStorage，UUID v4，除非用户清理浏览器数据否则长期存在。
- `oakved_session_id`：sessionStorage，UUID v4，浏览器标签页会话内存在。
- `eventId`：每次业务事件新建UUID v4，重试沿用同一个值。

客户端发送原始 UUID，服务端结合租户级盐值计算哈希后落库。任何接口响应都不返回哈希值。

### 2.2 事件触发规则

| 事件 | 触发时机 | 必填业务字段 | 不触发情况 |
|---|---|---|---|
| HOME_VIEW | 当前路由变为 `/`，包含首次进入、内部返回、刷新 | `pagePath='/'` | 5秒内相同会话相同事件重复触发 |
| PRODUCT_DETAIL_VIEW | `/product?id={spuId}` 商品详情接口成功且商品有效 | `spuId`, `pagePath='/product'` | 接口失败、商品无效、纯接口重试 |
| ADD_TO_CART | `/trade/cart/add` 成功 | `spuId`, `skuId`, `quantity` | 本地预览购物车、接口失败、未登录拦截 |
| CHECKOUT_START | 用户点击结算且即将进入结算路由 | `pagePath` | 购物车为空或流程校验失败 |

客户端5秒抑制键：

`eventType + ':' + (spuId || '') + ':' + pagePath`

服务端唯一键 `(tenant_id,event_id)` 负责网络重试幂等，不能代替客户端5秒抑制。

## 3. 上报行为事件

### 3.1 请求

`POST /app-api/statistics/behavior/track`

认证：允许匿名；存在会员令牌时后端从安全上下文读取 `userId`。

限流：同一租户和客户端IP每分钟120次。

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
  "referrerHost": "www.google.com",
  "deviceType": "DESKTOP",
  "occurredAt": "2026-07-10T10:30:15.123+08:00"
}
```

字段约束：

| 字段 | 类型 | 约束 |
|---|---|---|
| eventId | string | 必填，UUID，最大64字符 |
| eventType | enum | HOME_VIEW、PRODUCT_DETAIL_VIEW、ADD_TO_CART、CHECKOUT_START |
| visitorId | string | 必填，UUID，最大64字符 |
| sessionId | string | 必填，UUID，最大64字符 |
| spuId | integer | 商品详情、加购必填，正数 |
| skuId | integer | 加购必填，正数 |
| quantity | integer | 加购必填，1至999 |
| pagePath | string | 必填，以`/`开头，最大255字符，不允许协议和域名 |
| referrerHost | string/null | 仅域名，最大255字符 |
| deviceType | enum | DESKTOP、MOBILE、TABLET、OTHER |
| occurredAt | ISO-8601 | 必填，带时区；偏差超过24小时改用服务器时间 |

成功或重复事件都返回：

```json
{ "code": 0, "data": true, "msg": "" }
```

重复事件不返回错误，防止客户端持续重试。

### 3.2 客户端发送策略

统一复用 `requestYudao`，让匿名请求携带 `tenant-id`，登录请求同时携带会员令牌。这里不使用 `sendBeacon`，因为浏览器不能为 `sendBeacon` 设置芋道需要的租户和认证请求头；使用 `fetch keepalive` 可以在不阻塞页面的情况下保留这些请求头。

```js
import { requestYudao } from "./yudaoRequest.js";

export const sendBehaviorEvent = (event) => {
  void requestYudao("/statistics/behavior/track", {
    method: "POST",
    body: JSON.stringify(event),
    keepalive: true,
  }).catch(() => undefined);
  return true;
};
```

上报失败不阻断浏览、加购、结算或下单；不弹出用户可见错误。

## 4. 管理后台公共查询参数

```ts
export interface DashboardQuery {
  times: [string, string]
  categoryId?: number
  spuId?: number
  compare?: boolean
}
```

序列化示例：

`times[0]=2026-06-11 00:00:00&times[1]=2026-07-11 00:00:00&compare=true`

规则：

- `spuId` 存在时优先于 `categoryId`。
- `compare=true` 时比较紧邻当前区间之前的等长区间。
- 日期缺失、顺序错误或跨度超过366天返回参数校验错误。

## 5. 汇总指标接口

`GET /admin-api/statistics/dashboard/summary`

权限：`statistics:dashboard:query`

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
      "productConversionPercent": 3.76,
      "productUvConversionPercent": 5.57,
      "addCartRatePercent": 11.80,
      "averageOrderValue": 225479,
      "refundRatePercent": 4.86,
      "profitDataQuality": "EXACT",
      "lastUpdatedAt": "2026-07-10T10:35:00+08:00"
    },
    "reference": {
      "homePv": 11920,
      "paidOrderCount": 342,
      "grossProfit": 30180000,
      "profitDataQuality": "MIXED",
      "lastUpdatedAt": "2026-06-10T23:55:00+08:00"
    }
  },
  "msg": ""
}
```

所有未列出的 `reference` 字段与 `value` 结构一致。分母为0时百分比返回 `0`；前端环比基准为0时显示“无可比数据”，不显示无限大。

利润质量规则：

- `INCOMPLETE`：`missingCostItemCount > 0`。
- `MIXED`：同时存在精确和估算成本。
- `ESTIMATED`：有成本的订单明细全部为估算。
- `EXACT`：所有相关订单明细均有精确成本；区间无订单时也返回 `EXACT`。

## 6. 每日趋势接口

`GET /admin-api/statistics/dashboard/trend`

权限：`statistics:dashboard:query`

额外参数：`granularity=DAY`，一期仅接受 `DAY`。

```json
{
  "code": 0,
  "data": [
    {
      "day": "2026-07-09",
      "homePv": 430,
      "homeUv": 281,
      "productDetailPv": 350,
      "productDetailUv": 226,
      "paidOrderCount": 13,
      "paidItemCount": 17,
      "paidRevenue": 3150000,
      "refundAmount": 120000,
      "netRevenue": 3030000,
      "costAmount": 1840000,
      "grossProfit": 1190000,
      "grossMarginPercent": 39.27,
      "siteConversionPercent": 3.02,
      "productConversionPercent": 3.71
    }
  ],
  "msg": ""
}
```

区间内没有数据的日期仍返回一行且所有数值为0，保证图表时间轴连续。

## 7. 转化漏斗接口

`GET /admin-api/statistics/dashboard/funnel`

权限：`statistics:dashboard:query`

```json
{
  "code": 0,
  "data": {
    "steps": [
      { "key": "HOME_UV", "name": "首页访客", "value": 8200, "previousConversionPercent": 100.00 },
      { "key": "PRODUCT_DETAIL_UV", "name": "商品详情访客", "value": 6100, "previousConversionPercent": 74.39 },
      { "key": "ADD_CART_USER", "name": "加购访客", "value": 720, "previousConversionPercent": 11.80 },
      { "key": "CHECKOUT_SESSION", "name": "开始结算", "value": 510, "previousConversionPercent": 70.83 },
      { "key": "PAID_BUYER", "name": "支付买家", "value": 340, "previousConversionPercent": 66.67 }
    ],
    "largestDropStep": "ADD_CART_USER"
  },
  "msg": ""
}
```

## 8. 商品表现分页接口

`GET /admin-api/statistics/dashboard/product-page`

权限：`statistics:dashboard:query`

在公共参数基础上支持：

- `keyword`：商品名称模糊搜索，或输入完整 SPU 编号精确搜索。
- `pageNo`：从1开始。
- `pageSize`：默认20，最大100。
- `sortingFields[0].field`：允许 `detailPv`、`detailUv`、`paidOrderCount`、`netRevenue`、`grossProfit`、`grossMarginPercent`、`productConversionPercent`、`refundRatePercent`。
- `sortingFields[0].order`：`asc` 或 `desc`。

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
        "detailPv": 1520,
        "detailUv": 980,
        "addCartCount": 132,
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
        "profitDataQuality": "EXACT"
      }
    ],
    "total": 1
  },
  "msg": ""
}
```

## 9. 商品导出接口

`GET /admin-api/statistics/dashboard/export-product-excel`

权限：`statistics:dashboard:export`

参数与商品分页接口一致但忽略 `pageNo`、`pageSize`，保留筛选和排序。文件名：

`商品表现_20260611_20260710.xlsx`

Excel列顺序：SPU编号、商品名称、分类、详情PV、详情UV、加购次数、加购率、支付订单数、支付件数、支付销售额、退款额、净销售额、成本、毛利、毛利率、PV转化率、UV转化率、退款率、利润数据质量。

## 10. 错误处理

| 场景 | 行为 |
|---|---|
| 埋点重复eventId | 返回成功，不重复插入 |
| 埋点字段不符合事件规则 | 返回参数校验错误 |
| 埋点超过限流 | 返回芋道统一限流错误；家具前台静默忽略 |
| 管理端无查询权限 | HTTP/业务层拒绝访问 |
| 日期跨度超过366天 | 返回参数校验错误 |
| 聚合数据超过20分钟未更新 | 接口仍返回数据，前端显示延迟提示 |
| 单个看板区块接口失败 | 其他区块保留成功结果，失败区块单独重试 |

## 11. 兼容性约束

- 现有 `/statistics/product/*` 接口继续保留。
- `product_spu.browse_count` 继续作为旧累计字段，但不参与新看板。
- 不安装 React、Tailwind、Radix 或 shadcn 运行时。
- 管理端复用 Vue 3、Element Plus、现有 `Echart`、Axios封装和权限指令。
