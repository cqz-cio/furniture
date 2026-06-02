# Phase B 开发指南

## 工作目录

```powershell
cd "D:\code\furniture web"
```

## 基础命令

```powershell
npm.cmd test
npm.cmd run build
npm.cmd run dev
```

PowerShell 环境下使用 `npm.cmd`，不要直接使用 `npm`，因为本机脚本执行策略可能拦截 `npm.ps1`。

## 环境变量

默认 Yudao App API 地址：

```text
http://127.0.0.1:48080/app-api
```

可通过 Vite 环境变量覆盖：

```powershell
$env:VITE_YUDAO_APP_API_BASE="http://127.0.0.1:48080/app-api"
npm.cmd run dev
```

前台读取 token 的 localStorage key：

```text
YUDAO_APP_TOKEN
```

在浏览器控制台可临时设置：

```javascript
localStorage.setItem("YUDAO_APP_TOKEN", "你的 Yudao App 用户 token");
```

清除 token：

```javascript
localStorage.removeItem("YUDAO_APP_TOKEN");
```

## Yudao 后台商品维护流程

1. 启动 Yudao 后端与管理后台。
2. 登录 Yudao 管理后台。
3. 进入商城商品中心。
4. 新增或编辑 SPU。
5. 填写商品名称、封面图、轮播图、简介、详情、SKU 价格、市场价、库存。
6. 确认商品状态为上架。
7. 刷新家具前台 `/sofas-plp`。

前台读取的是 Yudao App API，所以只有上架商品会进入前台商品列表。

## 前台页面规划

Phase B 页面如下：

- `/sofas-plp`：商品列表。
- `/sofa-pdp?id=商品ID`：商品详情。
- `/checkout`：结算页。
- `/orders`：订单列表。
- `/orders?id=订单ID`：订单详情。

当前项目没有引入 Vue Router，继续使用 `src/App.vue` 中已有的轻量路径路由方式。

## 服务层规划

`src/services/yudaoClient.js` 负责：

- Yudao API base URL。
- token header。
- `CommonResult` 解包。
- 商品 mapper。
- 购物车 mapper。
- 地址 mapper。
- 订单结算 mapper。
- 订单列表/详情 mapper。

`src/services/localCart.js` 负责：

- 本地购物车增删改。
- 本地购物车持久化。
- 本地购物车金额汇总。

`src/services/checkoutSession.js` 负责：

- 把购物车条目转为 Yudao settlement/create payload。
- 判断是否可远程结算。
- 构造本地 demo 结算预览。

## 结算规则

### 远程结算

远程结算必须满足：

- 所有结算商品都是 `source === "yudao"`。
- 每个商品都有 `cartId`。
- localStorage 中存在 `YUDAO_APP_TOKEN`。
- Yudao 返回默认地址或用户选择了地址。

请求结构：

```json
{
  "items": [
    {
      "skuId": 1001,
      "count": 2,
      "cartId": 3001
    }
  ],
  "pointStatus": false,
  "deliveryType": 1,
  "addressId": 2001
}
```

### 本地预览

本地 demo 商品只显示本地金额预览，不调用 `/trade/order/create`。

## UI 规则

- 保持现有 RH 风格，不做营销落地页。
- 结算页是工作流页面，布局要清晰、紧凑、可扫描。
- 购物车按钮进入结算页，空购物车时禁用或提示。
- 错误文案必须具体，比如“需要 Yudao App token 才能创建订单”。
- 不用大面积彩色装饰，不引入新的图标库。

## 测试规则

每个任务执行顺序：

```powershell
npm.cmd test
```

构建验证：

```powershell
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
```

边界验证：

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

## 调试规则

如果 Yudao 接口失败，按顺序检查：

1. Yudao 后端是否启动。
2. `VITE_YUDAO_APP_API_BASE` 是否指向 `/app-api`。
3. 浏览器 localStorage 是否有 `YUDAO_APP_TOKEN`。
4. 商品是否已上架。
5. 用户是否有默认地址。
6. 商品是否配置配送方式和运费模板。

## 交付前检查

交付前必须确认：

- `npm.cmd test` 通过。
- Vite 临时构建通过。
- harness 边界检查通过。
- 没有新增 `dist/**`、`node_modules/**` 作为交付改动。
- 没有修改 Yudao 后端或管理后台。
- 新增行为有测试覆盖。

