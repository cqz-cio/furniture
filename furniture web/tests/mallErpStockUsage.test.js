import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const cloud = new URL("../../yudao电商管理平台前后端/yudao-cloud/", import.meta.url);
const read = (path) => readFileSync(new URL(path, cloud), "utf8");

describe("storefront ERP stock integration", () => {
  it("overlays app product responses without replacing mall merchandising fields", () => {
    const source = read("yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuController.java");
    expect(source).toContain("MallErpProductApi mallErpProductApi");
    expect(source).toContain("mallErpProductApi.getSellableStock(sku.getId())");
    expect(source).toContain("sku.setStock");
    expect(source).not.toContain("setPrice(stock");
    expect(source).not.toContain("setPicUrl(stock");
  });

  it("validates cart and settlement quantities through the ERP API", () => {
    const cart = read("yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/CartServiceImpl.java");
    const price = read("yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/price/TradePriceServiceImpl.java");
    expect(cart).toContain("mallErpProductApi.getSellableStock(skuId)");
    expect(cart).not.toContain("count > sku.getStock()");
    expect(price).toContain("mallErpProductApi.validateSellableStock");
    expect(price).not.toContain("count > sku.getStock()");
  });
});
