import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (absolutePath) => readFileSync(absolutePath, "utf8").replace(/\r\n/g, "\n");

const backendRoot = "D:/code/yudao电商管理平台前后端/yudao-cloud";
const memberApiRoot = `${backendRoot}/yudao-module-member/yudao-module-member-api/src/main/java/cn/iocoder/yudao/module/member/api`;
const memberServerRoot = `${backendRoot}/yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member`;
const tradeServerRoot = `${backendRoot}/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade`;

describe("backend Gift Registry purchase writeback", () => {
  it("persists registry context from cart to order items", () => {
    const appCartAddReq = readSource(`${tradeServerRoot}/controller/app/cart/vo/AppCartAddReqVO.java`);
    const cartDO = readSource(`${tradeServerRoot}/dal/dataobject/cart/CartDO.java`);
    const cartMapper = readSource(`${tradeServerRoot}/dal/mysql/cart/CartMapper.java`);
    const orderItemDO = readSource(`${tradeServerRoot}/dal/dataobject/order/TradeOrderItemDO.java`);
    const orderConvert = readSource(`${tradeServerRoot}/convert/order/TradeOrderConvert.java`);
    const priceReqBO = readSource(`${tradeServerRoot}/service/price/bo/TradePriceCalculateReqBO.java`);
    const priceRespBO = readSource(`${tradeServerRoot}/service/price/bo/TradePriceCalculateRespBO.java`);
    const priceHelper = readSource(`${tradeServerRoot}/service/price/calculator/TradePriceCalculatorHelper.java`);

    for (const source of [appCartAddReq, cartDO, orderItemDO, priceReqBO, priceRespBO]) {
      expect(source).toContain("registryId");
      expect(source).toContain("registryItemId");
    }
    expect(orderConvert).toContain("setRegistryId(cart.getRegistryId())");
    expect(orderConvert).toContain("setRegistryItemId(cart.getRegistryItemId())");
    expect(priceHelper).toContain("setRegistryId(item.getRegistryId())");
    expect(priceHelper).toContain("setRegistryItemId(item.getRegistryItemId())");
    expect(cartMapper).toContain("selectByUserIdAndSkuIdAndRegistryItemId");
    expect(cartMapper).toContain("isNull(CartDO::getRegistryItemId)");
  });

  it("exposes a member API and trade order handler to update quantityPurchased after payment", () => {
    const apiPath = `${memberApiRoot}/giftregistry/MemberGiftRegistryApi.java`;
    const apiImplPath = `${memberServerRoot}/api/giftregistry/MemberGiftRegistryApiImpl.java`;
    const dtoPath = `${memberApiRoot}/giftregistry/dto/MemberGiftRegistryPurchaseRecordReqDTO.java`;
    const handlerPath = `${tradeServerRoot}/service/order/handler/TradeGiftRegistryOrderHandler.java`;

    for (const path of [apiPath, apiImplPath, dtoPath, handlerPath]) {
      expect(existsSync(path)).toBe(true);
    }

    expect(readSource(apiPath)).toContain("recordPurchase");
    expect(readSource(apiImplPath)).toContain("recordPurchasedItems");
    expect(readSource(handlerPath)).toContain("afterPayOrder");
    expect(readSource(handlerPath)).toContain("getRegistryItemId");
  });

  it("adds local infra migrations for cart and order registry context columns", () => {
    const sql = readSource(`${backendRoot}/sql/mysql/trade-gift-registry-context.sql`);

    expect(sql).toContain("TABLE_NAME = 'trade_cart'");
    expect(sql).toContain("TABLE_NAME = 'trade_order_item'");
    expect(sql).toContain("ADD COLUMN `registry_id`");
    expect(sql).toContain("ADD COLUMN `registry_item_id`");
  });
});
