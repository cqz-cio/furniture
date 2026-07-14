import { describe, expect, it } from "vitest";
import {
  addLocalWishlistItem,
  clearLocalWishlist,
  LOCAL_WISHLIST_STORAGE_KEY,
  readLocalWishlist,
  removeLocalWishlistItem,
  updateLocalWishlistItemQuantity,
} from "../src/services/localWishlist.js";

const createStorage = () => {
  const store = new Map();
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
};

describe("local wishlist", () => {
  it("stores cart items once by sku id", () => {
    const storage = createStorage();
    const item = {
      id: 12,
      skuId: 99,
      name: "Cloud Sofa",
      subtitle: "Deep modular sofa",
      price: 2599,
      cover: "cover.jpg",
      quantity: 3,
      delivery: "Ships in 3-7 days",
      dimensions: "92W x 96D x 78H cm",
      color: "Wheat",
      fabric: "Performance Linen",
      marketPrice: 3299,
      source: "yudao",
    };

    const first = addLocalWishlistItem(item, storage);
    const second = addLocalWishlistItem({ ...item, quantity: 1 }, storage);

    expect(first.added).toBe(true);
    expect(second.added).toBe(false);
    expect(readLocalWishlist(storage)).toEqual([
      {
        id: 12,
        skuId: 99,
        name: "Cloud Sofa",
        subtitle: "Deep modular sofa",
        price: 2599,
        cover: "cover.jpg",
        delivery: "Ships in 3-7 days",
        dimensions: "92W x 96D x 78H cm",
        color: "Wheat",
        fabric: "Performance Linen",
        marketPrice: 3299,
        source: "yudao",
        quantity: 3,
      },
    ]);
    expect(JSON.parse(storage.getItem(LOCAL_WISHLIST_STORAGE_KEY))).toHaveLength(1);
  });

  it("removes wishlist items by sku id", () => {
    const storage = createStorage();
    addLocalWishlistItem({ id: 12, skuId: 99, name: "Cloud Sofa", price: 2599 }, storage);
    addLocalWishlistItem({ id: 13, skuId: 100, name: "Oak Chair", price: 899 }, storage);

    const nextItems = removeLocalWishlistItem(99, storage);

    expect(nextItems).toEqual([
      {
        id: 13,
        skuId: 100,
        name: "Oak Chair",
        subtitle: "",
        price: 899,
        cover: "",
        delivery: "",
        dimensions: "",
        color: "",
        fabric: "",
        marketPrice: 0,
        source: "local",
        quantity: 1,
      },
    ]);
    expect(readLocalWishlist(storage)).toHaveLength(1);
  });

  it("updates wishlist item quantity with a minimum of one", () => {
    const storage = createStorage();
    addLocalWishlistItem({ id: 12, skuId: 99, name: "Cloud Sofa", price: 2599 }, storage);

    updateLocalWishlistItemQuantity(99, 3, storage);
    const nextItems = updateLocalWishlistItemQuantity(99, 0, storage);

    expect(nextItems[0].quantity).toBe(1);
    expect(readLocalWishlist(storage)[0].quantity).toBe(1);
  });

  it("clears saved wishlist items after remote merge", () => {
    const storage = createStorage();
    addLocalWishlistItem({ id: 12, skuId: 99, name: "Cloud Sofa", price: 2599 }, storage);

    clearLocalWishlist(storage);

    expect(readLocalWishlist(storage)).toEqual([]);
    expect(storage.getItem(LOCAL_WISHLIST_STORAGE_KEY)).toBeNull();
  });
});
