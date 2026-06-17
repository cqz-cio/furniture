# Shopping Checkout Closure Checklist

更新时间：2026-06-17

这份清单用于收口当前“购物到收货信息到支付”的开发阶段。它区分三类内容：

- 已开发完成：代码里已经有前后端能力，并通过当前相关测试或检查脚本验证。
- 上线前必须配置：开发链路已预留，但真实生产能力依赖外部服务或后台配置。
- 后续增强：不是当前阶段闭环的必要条件，可以后续排期。

## 已开发完成

### 前台购物与结算

- 购物车商品进入 checkout 后，会根据 Yudao 登录状态判断是否可创建远程订单。
- 结算页区分 shipping 和 payment 两个阶段。
- 未填写完整收货信息时，不能进入地址核对。
- 未完成地址二次确认时，不能提交远程订单。
- 支付前会检查支付方式、卡信息、条款确认、会员续费确认和支付渠道配置。
- 创建订单成功后，会保存订单详情入口；如果支付提交失败，用户可以通过错误恢复入口去订单详情继续处理。
- Yudao 支付提交结果支持 `url` 跳转和 `form` HTML 表单提交两类展示模式，覆盖支付宝 PC/WAP 等常见渠道返回形态。
- 支付渠道回跳后，订单页会解析支付状态并提供刷新状态、继续支付等恢复动作。
- 对未知支付回跳状态，例如 `processing`，现在会显示未知结果提示并保留恢复入口。

### 地址核对

- 新地址和已保存地址都会在当前 checkout 中重新核对，不直接信任历史记录。
- 地址核对结果会生成 `addressVerification` 审计数据，并随订单创建提交到后端。
- 地址核对来源、状态、用户选择、确认时间、服务响应编号、兜底状态等信息会在订单详情中展示。
- 远程地址核对服务不可用时，会展示兜底提示，提醒用户仔细确认街道信息。
- 修改收货地址会清空旧地址核对记录，避免旧审核结果继续被使用。

### 后端订单

- 快递订单创建时要求带地址核对审计记录。
- 后端会校验地址核对记录中的最终确认地址与当前收货地址匹配。
- 地址核对不匹配时，订单创建会失败并返回专用错误码。
- 前台会把该错误映射为“重新核对地址”的恢复动作。

### 后台管理

- 管理后台订单列表和详情展示地址核对状态、来源和服务兜底状态。
- 后台支持按地址核对状态、来源、服务状态筛选订单。
- 后台修改订单地址后，会提示原地址核对记录失效。
- 后台发货时，如果地址核对缺失、服务兜底、本地邮编区域核对、后台简化核对或未核对，需要运营人工确认后才能发货。
- 后端发货接口也会校验人工确认，不能只绕过前端直接调用接口。
- 风险地址被人工确认后，订单操作日志会留下“地址风险已人工复核”的记录。

## 上线前必须配置

### 真实支付

- 配置真实 Yudao 支付渠道，并设置前台 `VITE_YUDAO_PAY_CHANNEL_CODE`。
- 前台构建环境必须确认 `VITE_YUDAO_PAY_CHANNEL_CODE` 与 Yudao 后台支付渠道编码一致。
- 前台环境变量可从 `furniture web/.env.example` 复制后按部署环境填写，不能把真实密钥写入前端仓库。
- 在 Yudao 后台确认支付应用、支付渠道、回调地址、退款规则均可用。
- 用真实或沙箱支付渠道跑通：创建订单、URL 跳转支付、HTML 表单支付、支付成功回跳、支付失败回跳、取消支付、继续支付、状态同步。
- 如果接入海外信用卡，优先使用 Stripe Checkout、Adyen Drop-in 等渠道托管或渠道 SDK 托管的支付界面；当前 checkout 页面里的信用卡输入框只能作为占位体验，不能承载真实卡号收集。
- 海外信用卡密钥、webhook secret、商户私钥必须只放后端运行环境或安全配置中心；前端只能暴露渠道允许公开的 publishable key。
- 海外信用卡回调必须验签，并校验订单号、支付单 id、金额、币种、渠道交易号和 webhook event id 幂等。
- 系统不得保存完整卡号、CVV、磁道信息或原始敏感支付凭证；最多保存卡品牌、尾号 last4、渠道交易号、金额、币种、状态和支付时间等非敏感账务字段。

### 真实地址核对

- 接入正式地址核对服务，例如 Google Address Validation、USPS/CASS 或合规第三方。
- 如果使用 Google Address Validation，在后端运行配置中设置 `yudao.member.address-verification.google.api-key`。
- 美国地址需要 CASS 处理时，确认后端 `yudao.member.address-verification.google.enable-usps-cass` 保持启用。
- 后端 Google 地址核对客户端默认连接超时 `3000ms`、读取超时 `5000ms`，可通过 `yudao.member.address-verification.google.connect-timeout-millis` 和 `yudao.member.address-verification.google.read-timeout-millis` 调整。
- 后端本地配置样例已写在 `yudao-server/src/main/resources/application-local.yaml` 和 `yudao-module-member/yudao-module-member-server/src/main/resources/application-local.yaml`，实际部署时通过 `YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY` 注入真实 key。
- 前台地址核对默认调用 `/member/address/verify` 和 `/member/address/verification-status`；只有部署路径变化时才覆盖 `VITE_ADDRESS_VERIFICATION_PATH` 或 `VITE_ADDRESS_VERIFICATION_STATUS_PATH`。
- 明确服务不可用时是否允许继续下单；当前开发结果是允许兜底，但会提示用户和后台运营二次确认。
- 不建议长期依赖自建美国地址库作为权威校验源；自建库只适合做兜底、提示或粗筛。

### 基础数据

- Yudao 区域、物流、配送模板、支付渠道、商品 SKU、库存、会员商品等基础数据需要补齐。
- 当前 Yudao 示例地址和区域数据不能当作真实生产地址库。
- 美国地址核对不应靠手工补全全国地址，可靠性和实时性都不足。

## 后续增强

- 接入更完整的地址标准化展示，例如地址组件级差异、高亮修改字段、确认理由选择。
- 将后台人工复核动作扩展为独立审计表，记录操作人、操作时间、原因和原始核对快照。
- 支持更多支付方式，例如信用卡真实 token、礼品卡、会员余额、组合支付。
- 支持支付 webhook 和前台轮询之间的更严格一致性校验。
- 增加端到端自动化测试，覆盖真实浏览器中的 checkout 到支付回跳流程。

## 当前停工边界

本阶段不再继续扩大购物链路开发范围。当前代码已经覆盖“用户填写/选择地址 -> 地址核对和确认 -> 创建订单 -> 支付提交/恢复 -> 后台发货前复核”的主闭环。

接下来如果要继续推进，应优先做真实服务配置和端到端验收，而不是继续增加本地兜底逻辑。

## 已运行验证

- `npm.cmd test -- tests/checkoutPayment.test.js tests/ordersRecovery.test.js tests/checkoutRecovery.test.js tests/checkoutFlowPage.test.js tests/checkoutErrors.test.js`
- `node scripts\check-trade-order-address-verification.mjs`
- `mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeOrderUpdateServiceImplTest,AppTradeOrderCreateReqVOTest,TradeOrderConvertTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
