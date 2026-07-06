import { describe, expect, it } from "vitest";
import {
  buildRealAccountModuleSnapshot,
  getAccountProfileReadiness,
  getAddressBookReadiness,
  getBillingReadiness,
  getCartItemReadiness,
  getCartReadiness,
  getTradeProgramReadiness,
  getWishlistReadiness,
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

  it("blocks Trade readiness when the seeded trade profile belongs to another account", () => {
    expect(
      getTradeProgramReadiness({
        token: "token",
        hasTradeApi: true,
        tradeProfile: { userId: 2, tradeId: "RH-TRADE-99999" },
        expectedUserId: "1",
        expectedTradeId: "RH-TRADE-10086",
      }),
    ).toMatchObject({
      ready: false,
      partial: false,
      issues: ["trade-id-mismatch", "user-id-mismatch"],
    });

    expect(
      buildRealAccountModuleSnapshot({
        token: "token",
        productCount: 1,
        cartItems: liveCartItems,
        hasTradeApi: true,
        tradeProfile: { userId: 2, tradeId: "RH-TRADE-99999" },
        expectedUserId: "1",
        expectedTradeId: "RH-TRADE-10086",
      }),
    ).toMatchObject({
      tradeProgram: "blocked",
    });
  });

  it("does not mark billing ready from order rows that lack payment evidence", () => {
    expect(
      getBillingReadiness({
        token: "token",
        hasBillingReadApi: true,
        billingRecords: [{ id: 7001, no: "SO-7001" }],
      }),
    ).toMatchObject({
      ready: false,
      partial: true,
      issues: ["missing-payment-amount", "missing-payment-status"],
    });

    expect(
      buildRealAccountModuleSnapshot({
        token: "token",
        productCount: 1,
        cartItems: liveCartItems,
        hasBillingReadApi: true,
        billingRecords: [{ id: 7001, no: "SO-7001" }],
      }),
    ).toMatchObject({
      billing: "partial",
    });
  });

  it("does not mark account profile ready without a contact field", () => {
    expect(
      getAccountProfileReadiness({
        token: "token",
        hasAccountProfileApi: true,
        profile: { id: 1 },
        expectedUserId: "1",
      }),
    ).toMatchObject({
      ready: false,
      partial: true,
      issues: ["missing-contact"],
    });

    expect(
      buildRealAccountModuleSnapshot({
        token: "token",
        productCount: 1,
        cartItems: liveCartItems,
        hasAccountProfileApi: true,
        accountProfile: { id: 1 },
        expectedUserId: "1",
      }),
    ).toMatchObject({
      accountProfile: "partial",
    });
  });

  it("does not mark address book ready when saved addresses lack delivery fields", () => {
    expect(
      getAddressBookReadiness({
        token: "token",
        hasAddressBookApi: true,
        addresses: [{ id: 501, name: "Launch Buyer" }],
        expectedAddressId: "501",
      }),
    ).toMatchObject({
      ready: false,
      partial: true,
      issues: ["missing-address-mobile", "missing-address-detail"],
    });

    expect(
      buildRealAccountModuleSnapshot({
        token: "token",
        productCount: 1,
        cartItems: liveCartItems,
        hasAddressBookApi: true,
        addresses: [{ id: 501, name: "Launch Buyer" }],
        expectedAddressId: "501",
      }),
    ).toMatchObject({
      addressBook: "partial",
    });
  });

  it("does not mark wishlist ready when seeded product identifiers are missing", () => {
    expect(
      getWishlistReadiness({
        token: "token",
        hasWishlistApi: true,
        wishlistRecords: [{ id: 601, spuId: 11, skuId: 21 }],
        expectedSpuId: "10",
        expectedSkuId: "20",
      }),
    ).toMatchObject({
      ready: false,
      partial: false,
      issues: ["seeded-wishlist-row-missing"],
    });

    expect(
      buildRealAccountModuleSnapshot({
        token: "token",
        productCount: 1,
        cartItems: liveCartItems,
        hasWishlistApi: true,
        wishlistRecords: [{ id: 601, spuId: 11, skuId: 21 }],
        expectedWishlistSpuId: "10",
        expectedWishlistSkuId: "20",
      }),
    ).toMatchObject({
      wishlist: "blocked",
    });
  });
});
