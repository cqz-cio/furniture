import { describe, expect, it } from "vitest";
import {
  buildRealAccountModuleSnapshot,
  getCartItemReadiness,
  getCartReadiness,
  getYudaoProductIdentity,
  isRealYudaoProduct,
} from "../src/services/realAccountReadiness.js";

describe("real account readiness helpers", () => {
  const liveCartItems = [
    {
      source: "yudao",
      id: 101,
      spuId: 101,
      skuId: 201,
      cartId: 301,
      quantity: 1,
    },
  ];

  it("recognizes real Yudao products by source, SPU, and SKU identity", () => {
    expect(isRealYudaoProduct({ source: "yudao", id: 10, skuId: 20 })).toBe(true);
    expect(isRealYudaoProduct({ source: "yudao", id: 10, skus: [{ id: 21 }] })).toBe(true);
    expect(getYudaoProductIdentity({ source: "yudao", id: 10, skus: [{ id: 21 }] })).toEqual({
      spuId: 10,
      skuId: 21,
    });
    expect(isRealYudaoProduct({ source: "demo", id: 10, skuId: 20 })).toBe(false);
    expect(isRealYudaoProduct({ source: "yudao", id: 10 })).toBe(false);
  });

  it("reports cart item issues that block real account checkout", () => {
    expect(getCartItemReadiness(liveCartItems[0])).toEqual({
      skuId: 201,
      cartId: 301,
      source: "yudao",
      ready: true,
      issues: [],
    });

    expect(getCartItemReadiness({ source: "local", skuId: "", quantity: 1 })).toMatchObject({
      ready: false,
      issues: ["source-not-yudao", "missing-spu-id", "missing-sku-id", "missing-cart-id"],
    });
  });

  it("requires token and real cart item identifiers before live order creation", () => {
    expect(getCartReadiness(liveCartItems, "token")).toMatchObject({
      checkoutMode: "yudao",
      ready: true,
      canCreateLiveOrder: true,
      blockingReasons: [],
    });

    expect(getCartReadiness(liveCartItems, "")).toMatchObject({
      checkoutMode: "token-required",
      ready: false,
      canCreateLiveOrder: false,
      blockingReasons: ["missing-token"],
    });

    expect(getCartReadiness([{ ...liveCartItems[0], cartId: "" }], "token").blockingReasons).toContain(
      "item-1:missing-cart-id",
    );
  });

  it("classifies current modules into ready, partial, and blocked readiness states", () => {
    expect(
      buildRealAccountModuleSnapshot({
        token: "token",
        productCount: 1,
        cartItems: liveCartItems,
        hasOrderReadApi: true,
        hasBillingReadApi: true,
        hasAccountProfileApi: true,
        hasAddressBookApi: true,
        hasWishlistApi: true,
        hasMembershipApi: true,
        hasGiftRegistryApi: true,
        hasTradeApi: false,
      }),
    ).toMatchObject({
      productCatalog: "ready",
      cart: "ready",
      checkout: "ready",
      orders: "ready",
      billing: "ready",
      accountProfile: "ready",
      addressBook: "ready",
      wishlist: "ready",
      membership: "ready",
      giftRegistry: "ready",
      tradeProgram: "blocked",
    });

    expect(
      buildRealAccountModuleSnapshot({
        token: "",
        productCount: 0,
        cartItems: [{ source: "demo", skuId: "demo" }],
        hasWishlistApi: true,
      }),
    ).toMatchObject({
      productCatalog: "blocked",
      cart: "partial",
      checkout: "partial",
      wishlist: "partial",
    });
  });
});
