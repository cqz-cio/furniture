import { describe, expect, it } from "vitest";
import { addLocalCartItem, removeLocalCartItem, updateLocalCartItemQuantity } from "../src/services/localCart.js";
import {
  AUTH_TOKEN_STORAGE_KEY,
  readYudaoToken,
  unwrapYudaoResult,
  writeYudaoToken,
} from "../src/services/yudaoRequest.js";
import {
  mapAddressResponse,
  mapOrderDetail,
  mapOrderPage,
  mapSettlementResponse,
  mapSpuToProduct,
} from "../src/services/yudaoMappers.js";

describe("yudao integration models", () => {
  it("unwraps successful yudao CommonResult responses", () => {
    expect(unwrapYudaoResult({ code: 0, data: { ok: true } })).toEqual({ ok: true });
    expect(unwrapYudaoResult({ data: [1, 2, 3] })).toEqual([1, 2, 3]);
  });

  it("throws the backend message for failed yudao responses", () => {
    expect(() => unwrapYudaoResult({ code: 401, msg: "Unauthorized" })).toThrow("Unauthorized");
  });

  it("keeps yudao business error metadata on thrown errors", () => {
    try {
      unwrapYudaoResult({ code: 1004003014, msg: "Captcha required", data: { reason: "ip" } });
      throw new Error("Expected unwrapYudaoResult to throw");
    } catch (error) {
      expect(error.message).toBe("Captcha required");
      expect(error.code).toBe(1004003014);
      expect(error.data).toEqual({ reason: "ip" });
    }
  });

  it("maps product SPU records into furniture storefront products", () => {
    const product = mapSpuToProduct({
      id: 12,
      name: "Cloud Sofa",
      introduction: "Deep modular sofa",
      picUrl: "https://cdn.example/cover.jpg",
      sliderPicUrls: ["https://cdn.example/1.jpg"],
      price: 259900,
      marketPrice: 329900,
      stock: 8,
      productType: "sofa",
      detailConfig: {
        productType: "sofa",
        collection: "ADMIN CLOUD COLLECTION",
        heroNote: "Configured in admin",
      },
      skus: [{ id: 99, price: 259900, stock: 8, picUrl: "https://cdn.example/sku.jpg" }],
    });

    expect(product).toMatchObject({
      id: 12,
      skuId: 99,
      name: "Cloud Sofa",
      price: 2599,
      marketPrice: 3299,
      cover: "https://cdn.example/cover.jpg",
      gallery: ["https://cdn.example/1.jpg"],
      stock: 8,
      productType: "sofa",
      detailConfig: {
        productType: "sofa",
        collection: "ADMIN CLOUD COLLECTION",
        heroNote: "Configured in admin",
      },
    });
  });

  it("keeps local cart quantities stable and removes items", () => {
    const sofa = {
      id: 12,
      skuId: 99,
      name: "Cloud Sofa",
      price: 2599,
      cover: "cover.jpg",
      delivery: "White-glove delivery",
      dimensions: "92W x 96D x 78H cm",
      material: "wood",
    };
    const cart = addLocalCartItem([], sofa, 2);
    const merged = addLocalCartItem(cart, sofa, 3);
    const updated = updateLocalCartItemQuantity(merged, 99, 1);
    const removed = removeLocalCartItem(updated, 99);

    expect(merged).toHaveLength(1);
    expect(merged[0].quantity).toBe(5);
    expect(merged[0]).toMatchObject({
      delivery: "White-glove delivery",
      dimensions: "92W x 96D x 78H cm",
      material: "Wood finish",
    });
    expect(updated[0].quantity).toBe(1);
    expect(removed).toEqual([]);
  });

  it("maps yudao address responses for checkout selection", () => {
    expect(
      mapAddressResponse({
        id: 9,
        name: "Ada",
        mobile: "15500000000",
        areaName: "Shanghai",
        detailAddress: "Road 1",
      }),
    ).toEqual({
      id: 9,
      name: "Ada",
      mobile: "15500000000",
      areaName: "Shanghai",
      detailAddress: "Road 1",
      label: "Ada - 15500000000 - Shanghai Road 1",
      addressVerification: null,
      addressVerificationSummary: {
        source: "",
        status: "missing",
        statusLabelKey: "membership.account.addressBook.verification.statuses.missing",
        choice: "unknown",
        choiceLabelKey: "membership.account.addressBook.verification.choices.unknown",
        reason: "unknown",
        reasonLabelKey: "membership.account.addressBook.verification.reasons.unknown",
        confirmedAt: "",
        providerStatus: "",
        providerStatusLabelKey: "",
        warningKey: "membership.account.addressBook.verification.missingWarning",
        sourceWarningKey: "",
        providerWarningKey: "",
      },
      raw: expect.any(Object),
    });
  });

  it("maps settlement totals and items from yudao order settlement response", () => {
    const settlement = mapSettlementResponse({
      price: { payPrice: 259900, totalPrice: 329900, deliveryPrice: 0 },
      items: [{ skuId: 88, count: 1, spuName: "Cloud Sofa", picUrl: "cover.jpg", payPrice: 259900 }],
    });

    expect(settlement).toEqual({
      payPrice: 2599,
      totalPrice: 3299,
      deliveryPrice: 0,
      items: [{ skuId: 88, count: 1, name: "Cloud Sofa", cover: "cover.jpg", payPrice: 2599 }],
      raw: expect.any(Object),
    });
  });

  it("maps order page and detail responses into storefront orders", () => {
    const page = mapOrderPage({
      list: [
        {
          id: 1,
          no: "O1",
          status: 10,
          payPrice: 120000,
          items: [{ spuName: "Sofa", price: 90000, originalPrice: 120000, productType: "merchandise" }],
        },
      ],
      total: 1,
    });
    const detail = mapOrderDetail({ id: 1, no: "O1", status: 10, payPrice: 120000, payOrderId: 99, items: [] });

    expect(page.total).toBe(1);
    expect(page.list[0]).toMatchObject({ id: 1, no: "O1", payPrice: 1200 });
    expect(page.list[0].items[0]).toMatchObject({
      name: "Sofa",
      price: 900,
      regularPrice: 1200,
      memberPrice: 900,
      category: "merchandise",
    });
    expect(detail).toMatchObject({ id: 1, no: "O1", payPrice: 1200, payOrderId: 99 });
  });

  it("reads and writes the yudao app token using the shared storage key", () => {
    const storage = new Map();
    const fakeStorage = {
      getItem: (key) => storage.get(key),
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
    };

    writeYudaoToken(" abc ", fakeStorage);
    expect(storage.get(AUTH_TOKEN_STORAGE_KEY)).toBe("abc");
    expect(readYudaoToken(fakeStorage)).toBe("abc");

    writeYudaoToken("", fakeStorage);
    expect(storage.has(AUTH_TOKEN_STORAGE_KEY)).toBe(false);
  });
});
