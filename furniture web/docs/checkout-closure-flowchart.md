# 购物闭环流程图（可放大查看）

```mermaid
flowchart TD
  U[用户在 Checkout 页面] --> FE1[CheckoutPage.vue<br/>填写/选择收货信息]
  FE1 --> FE2[checkoutSession.js<br/>构建 shippingForm 快照 + 本地必填校验]
  FE2 --> CKT{地址是否已有有效核验快照?}
  CKT -->|否/已变更| AV[app-api/member/address/verify]
  CKT -->|是且未变更| PAY[进入支付前校验]
  AV --> AWR{核验返回}
  AWR -->|CONFIRMED| AR[addressVerification = 高可信]
  AWR -->|SUSPECT/INVALID/FAILED| AWL[核验失败/低置信，进入提示]
  AR --> S1[AddressReviewPanel]
  AWL --> S1
  S1 --> UX{用户确认}
  UX -->|用原地址| ADDR1[selectedAddress = shippingForm]
  UX -->|用核验建议| ADDR2[selectedAddress = verifiedAddress]
  ADDR1 --> BLD[buildConfirmedShippingAddressInput]
  ADDR2 --> BLD
  BLD --> PAYG[app-api/trade/order/create]
  PAYG --> ORDER{订单类型}
  ORDER -->|amount=0| ZERO[0元订单直达订单页]
  ORDER -->|amount>0| SUBMIT[app-api/pay/order/submit]
  SUBMIT --> PG[第三方支付渠道]
  PG --> RET[前端支付返回处理]
  RET -->|success| ORD[订单页订单详情 / 状态=Paid]
  RET -->|failed| ORD
  RET -->|cancel/unknown| ORD
  ORG[app-api/trade/order/get-detail/list] --> ORD

  ERR[支付回调/异常] --> ORC[后端支付回调校验]
  ORC --> ORC2[订单状态归档 + 幂等保护]
```

## 关键中间件与数据点

- **前端请求封装**：`yudaoRequest`（鉴权、tenant、错误规范化、响应映射）
- **网关 / 会话层**：登录态与租户上下文透传，订单支付上下文回传（`orderId / merchantOrderId / returnUrl`）
- **地址核验服务**：远程核验 + 本地回退，结果记录为 `addressVerification` 快照
- **交易服务层**：订单创建、支付提交参数约束、返回/回调验真与状态归档
- **订单页恢复层**：`order/get-detail/list` 驱动支付完成后的可恢复与风控态展示

## 节点数据传输摘要
- 入参地址：`shippingForm`（姓名/电话/国家/省市州/邮编/街道）
- 核验产物：`verificationId, source, addressStatus, confidence, suggestedAddress, warnings`
- 用户选择：`addressVerification.choice`（原地址/建议地址）与快照持久化
- 下单：`shippingAddress + addressVerification + cart/price + payChannel`
- 支付：`orderId/merchantOrderId + 回调参数 + 状态字段`
- 回调：订单归属与金额一致性校验通过后落库
