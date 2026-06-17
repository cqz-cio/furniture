# 支付安全边界代码落地说明

本文记录当前已经在 Yudao 支付链路中补上的安全边界代码，作为 `payment-flow-security-boundaries.md` 的工程实现补充。

## 已落地的后端边界

1. APP 支付入口必须登录。
   - 未登录用户不能查询支付单。
   - 未登录用户不能提交支付单。
   - 钱包支付在未登录时不会创建钱包或写入钱包参数。

2. APP 支付入口必须校验订单归属。
   - 订单存在 `userId` 时，只允许订单所属用户查询和提交。
   - 越权访问统一返回空数据，不泄露订单信息。

3. 支付提交前必须校验订单状态。
   - 支付订单不存在时拒绝。
   - 已支付订单拒绝重复提交。
   - 非待支付订单拒绝提交。
   - 已过期订单拒绝提交。
   - 金额为空或小于等于 0 的支付订单拒绝提交第三方支付。
   - 已存在成功的支付拓展单，或渠道查询结果已经成功时，拒绝再次创建新的有效支付。

4. 支付提交前必须校验渠道。
   - 支付应用必须有效。
   - 支付渠道必须有效且已配置。
   - 找不到 `PayClient` 时拒绝提交。
   - 创建支付拓展单时记录 `channelId` 和 `channelCode`，后续回调必须与它们一致。

5. 支付成功回调必须做一致性校验。
   - `outTradeNo` 必须能找到支付拓展单。
   - 回调进入的 `PayChannelDO` 必须匹配支付拓展单的 `channelId` 和 `channelCode`。
   - 支付拓展单必须处于待支付，或是同一拓展单的重复成功回调。
   - 主支付单必须存在。
   - 主支付单必须处于待支付，或是同一拓展单的重复成功回调。
   - 渠道返回金额非空时，必须等于支付订单金额。
   - 成功回调必须带渠道交易号 `channelOrderNo`。
   - 同一 `channelId + channelOrderNo` 不能绑定到其它支付订单。

6. 支付成功更新保持事务一致性。
   - 校验全部通过后，才更新支付拓展单成功。
   - 再更新主支付单成功。
   - 重复成功回调不重复创建支付通知任务。
   - 任一校验失败时不写入半截状态。

7. 渠道响应 DTO 已支持金额字段。
   - `PayOrderRespDTO.price` 使用分为单位。
   - 支付宝回调和查询解析 `total_amount`。
   - 微信 v2 回调和查询使用 `totalFee`。
   - 微信 v3 回调和查询使用 `amount.total`。
   - Mock、钱包、支付宝条码、微信条码即时成功返回会带上金额。
   - 历史渠道无法解析金额时保持为空，服务层仅在非空时强校验。

## 关键代码位置

- `yudao-module-pay-api/src/main/java/cn/iocoder/yudao/module/pay/enums/ErrorCodeConstants.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/controller/app/order/AppPayOrderController.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/service/order/PayOrderServiceImpl.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/framework/pay/core/client/dto/order/PayOrderRespDTO.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/framework/pay/core/client/impl/alipay/AbstractAlipayPayClient.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/framework/pay/core/client/impl/weixin/AbstractWxPayClient.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/framework/pay/core/client/impl/wallet/WalletPayClient.java`
- `yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/framework/pay/core/client/impl/mock/MockPayClient.java`

## 已补测试

- `AppPayOrderControllerTest`
  - 未登录查询支付单返回空。
  - 未登录提交支付单返回空。
  - 未登录钱包支付不产生钱包副作用。

- `PayOrderServiceTest`
  - 0 元或非正金额支付单拒绝提交第三方支付。
  - 成功回调渠道不匹配时拒绝入账。
  - 成功回调金额不匹配时拒绝入账。
  - 成功回调缺少渠道交易号时拒绝入账。
  - 渠道交易号已经绑定其它订单时拒绝入账。
  - 正常成功回调和重复成功回调保持幂等。

## 后续接海外信用卡渠道时必须继续补齐

1. Stripe/Adyen/Braintree 等真实渠道 adapter 必须填充 `PayOrderRespDTO.price`。
2. 如果渠道返回币种，后续需要在 DTO 和订单模型中增加 `currency` 并做强校验。
3. Webhook 必须校验签名、事件 id 幂等、渠道交易号唯一、金额和币种一致。
4. 信用卡卡号、CVV、完整持卡人敏感信息不能进入我们自己的接口、日志或数据库。
