# Yudao + Furniture Web Phase B 总执行计划书

## 目标

Phase B 的目标是把家具前台与 Yudao 商城后台打通到一个可演示、可继续迭代的小闭环：

1. 后台在 Yudao 商品中心维护商品名称、图片、价格、库存、上下架状态。
2. 家具前台商品列表与商品详情优先读取 Yudao App API。
3. 购物车支持远程 Yudao 购物车；未登录或后端不可用时保留本地购物车回退。
4. 增加结算页、订单创建入口、订单列表/订单详情基础页面。
5. 所有改动遵守 `docs/yudao-integration/code-boundary-safety.md`。
6. 每个实现任务按 `docs/superpowers/plans/2026-06-02-yudao-furniture-commerce-phase-b.md` 执行。

## 当前状态

已确认现有代码具备以下基础：

- 家具前台仓库：`D:\code\furniture web`
- Yudao 管理平台与后端：`D:\code\yudao电商管理平台前后端`
- 前台已有 `src/services/yudaoClient.js`，已封装商品、购物车接口。
- 前台已有 `src/services/localCart.js`，可做本地购物车回退。
- 前台已有 `src/components/CartDrawer.vue`，购物车抽屉已接入 `src/App.vue`。
- `/sofas-plp` 已优先读取 Yudao 商品列表。
- `/sofa-pdp?id=商品ID` 已优先读取 Yudao 商品详情。
- Yudao 后端已确认存在以下 App API：
  - `/app-api/product/spu/page`
  - `/app-api/product/spu/get-detail`
  - `/app-api/trade/cart/list`
  - `/app-api/trade/cart/add`
  - `/app-api/trade/cart/update-count`
  - `/app-api/trade/cart/delete`
  - `/app-api/trade/order/settlement`
  - `/app-api/trade/order/create`
  - `/app-api/trade/order/page`
  - `/app-api/trade/order/get-detail`
  - `/app-api/member/address/list`
  - `/app-api/member/address/get-default`

## 总体架构

前台保持独立 Vue/Vite 项目，不把 Yudao 管理后台嵌入家具网页。Yudao 作为商品、购物车、订单、地址数据源；家具网页只通过 App API 消费数据。

数据流：

```mermaid
flowchart LR
  Admin["Yudao 后台商品管理"] --> ProductDB["Yudao 商品/库存数据"]
  ProductDB --> AppAPI["Yudao App API"]
  AppAPI --> Catalog["家具前台商品列表/详情"]
  Catalog --> Cart["购物车"]
  Cart --> Checkout["结算页"]
  Checkout --> Order["Yudao 订单"]
  Order --> OrderPages["家具前台订单列表/详情"]
```

## 里程碑

### M1 商品与购物车稳定化

验收标准：

- 商品列表、详情仍可从 Yudao 拉取。
- 后端不可用时能显示 demo 商品。
- 本地购物车与 Yudao 购物车转换规则有测试覆盖。
- `npm.cmd test` 通过。

### M2 会员令牌与地址读取

验收标准：

- 前台可以保存/清除 Yudao App 用户 token。
- 已登录时可以读取默认地址与地址列表。
- 未登录时页面提示需要配置 token，不触发远程下单。
- 不在代码中硬编码 token。

### M3 结算页

验收标准：

- 购物车抽屉中的 Checkout 进入 `/checkout`。
- 结算页显示购物车商品、数量、小计。
- Yudao 购物车商品调用 `/trade/order/settlement` 获取后端价格。
- 本地 demo 商品只显示本地结算预览，不创建远程订单。

### M4 创建订单

验收标准：

- 使用 Yudao 购物车项、默认地址、配送方式 `EXPRESS = 1` 创建订单。
- 创建成功后跳转 `/orders/:id` 或等价详情页。
- 创建失败展示后端错误信息，不清空购物车。

### M5 订单列表与详情

验收标准：

- `/orders` 显示当前用户订单分页。
- `/orders?id=订单ID` 或 `/order-detail?id=订单ID` 显示订单详情。
- 页面可展示订单号、状态、商品、总价、支付订单号。

## 不做的范围

Phase B 不实现以下内容：

- 不修改 Yudao Java 后端。
- 不重构 Yudao 管理后台。
- 不接真实支付网关。
- 不实现完整会员注册登录流程。
- 不实现优惠券、积分、秒杀、拼团、售后。
- 不修改 `dist/`、`node_modules/` 作为业务交付内容。

## 执行顺序

1. 先执行 `harness/phase-b/run-harness.ps1 -SkipBuild`，确认当前工作区边界状态。
2. 按详细计划 Task 1 到 Task 7 顺序实现。
3. 每个任务必须先写测试，再写实现。
4. 每个任务完成后运行：
   - `npm.cmd test`
   - `npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir`
   - `powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1`
5. 任何越界文件改动先停止，按安全规范处理。

## 验收命令

```powershell
cd "D:\code\furniture web"
npm.cmd test
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

## 交付物

- 总执行计划书：`docs/yudao-integration/phase-b-execution-plan.md`
- 详细实现计划：`docs/superpowers/plans/2026-06-02-yudao-furniture-commerce-phase-b.md`
- 代码边界安全规范：`docs/yudao-integration/code-boundary-safety.md`
- 开发指南：`docs/yudao-integration/development-guide.md`
- Harness 工程目录：`harness/phase-b/`

