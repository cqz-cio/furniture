import { describe, expect, it } from "vitest";
import { addLocalCartItem, removeLocalCartItem, updateLocalCartItemQuantity } from "../src/services/localCart.js";
import { mapSpuToProduct, unwrapYudaoResult } from "../src/services/yudaoClient.js";

describe("yudao integration models", () => {
  it("unwraps successful yudao CommonResult responses", () => {
    expect(unwrapYudaoResult({ code: 0, data: { ok: true } })).toEqual({ ok: true });
    expect(unwrapYudaoResult({ data: [1, 2, 3] })).toEqual([1, 2, 3]);
  });

  it("throws the backend message for failed yudao responses", () => {
    expect(() => unwrapYudaoResult({ code: 401, msg: "Unauthorized" })).toThrow("Unauthorized");
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
    });
  });

  it("keeps local cart quantities stable and removes items", () => {
    const sofa = { id: 12, skuId: 99, name: "Cloud Sofa", price: 2599, cover: "cover.jpg" };
    const cart = addLocalCartItem([], sofa, 2);
    const merged = addLocalCartItem(cart, sofa, 3);
    const updated = updateLocalCartItemQuantity(merged, 99, 1);
    const removed = removeLocalCartItem(updated, 99);

    expect(merged).toHaveLength(1);
    expect(merged[0].quantity).toBe(5);
    expect(updated[0].quantity).toBe(1);
    expect(removed).toEqual([]);
  });
});
