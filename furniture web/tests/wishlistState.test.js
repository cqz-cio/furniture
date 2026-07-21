import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { addLocalWishlistItem } from "../src/services/localWishlist.js";
import {
  createWishlistIdentitySet,
  isWishlistItemSaved,
  loadWishlistIdentityState,
  withWishlistItemSaved,
} from "../src/services/wishlistState.js";
import { writeYudaoToken } from "../src/services/yudaoRequest.js";

const API_BASE = "http://127.0.0.1:48080/app-api";

const createStorage = () => {
  const store = new Map();
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
};

const mockYudaoResponse = (data) => ({
  ok: true,
  json: async () => ({ code: 0, data }),
});

describe("wishlist identity state", () => {
  let fetchMock;
  let storage;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    storage = createStorage();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("matches saved products by SKU and SPU identity", () => {
    const state = createWishlistIdentitySet([{ spuId: 88, skuId: 8801 }]);

    expect(isWishlistItemSaved({ id: 88, skuId: 8801 }, state)).toBe(true);
    expect(isWishlistItemSaved({ id: 88, skuId: 9901 }, state)).toBe(true);
    expect(isWishlistItemSaved({ id: 77, skuId: 7701 }, state)).toBe(false);
  });

  it("treats a product that is still loading as not saved", () => {
    expect(isWishlistItemSaved(null, new Set())).toBe(false);
    expect(createWishlistIdentitySet([null])).toEqual(new Set());
    expect(withWishlistItemSaved(new Set(), null)).toEqual(new Set());
  });

  it("loads local wishlist state when the visitor is not authenticated", async () => {
    addLocalWishlistItem({ id: 88, skuId: 8801, name: "Oak Chair" }, storage);

    const state = await loadWishlistIdentityState({ storage });

    expect(state.source).toBe("local");
    expect(isWishlistItemSaved({ id: 88, skuId: 8801 }, state.keys)).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("loads remote wishlist state for authenticated Yudao visitors", async () => {
    writeYudaoToken("member-token", storage);
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        total: 1,
        list: [{ id: 9, spuId: 88, skuId: 8801, spuName: "Oak Chair", price: 89900 }],
      }),
    );

    const state = await loadWishlistIdentityState({ storage });

    expect(state.source).toBe("yudao");
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/product/favorite/page?pageNo=1&pageSize=200`);
    expect(isWishlistItemSaved({ id: 88, skuId: 8801 }, state.keys)).toBe(true);
  });

  it("reports a remote wishlist state error for authenticated Yudao visitors", async () => {
    writeYudaoToken("member-token", storage);
    addLocalWishlistItem({ id: 88, skuId: 8801, name: "Oak Chair" }, storage);
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ code: 500, msg: "favorite service down" }),
    });

    const state = await loadWishlistIdentityState({ storage });

    expect(state).toMatchObject({
      source: "local",
      statusKey: "wishlist.remoteUnavailable",
      remoteUnavailable: true,
    });
    expect(state.error.message).toContain("favorite service down");
    expect(isWishlistItemSaved({ id: 88, skuId: 8801 }, state.keys)).toBe(true);
  });

  it("reports expired wishlist identity auth as sign-in required instead of remote unavailable", async () => {
    writeYudaoToken("expired-token", storage);
    addLocalWishlistItem({ id: 88, skuId: 8801, name: "Oak Chair" }, storage);
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ code: 401, msg: "token expired" }),
    });

    const state = await loadWishlistIdentityState({ storage });

    expect(state).toMatchObject({
      source: "local",
      statusKey: "wishlist.signInRequired",
      remoteUnavailable: false,
      authRequired: true,
    });
    expect(state.error.message).toContain("token expired");
    expect(isWishlistItemSaved({ id: 88, skuId: 8801 }, state.keys)).toBe(true);
  });

  it("optimistically marks a product as saved for immediate UI feedback", () => {
    const state = withWishlistItemSaved(new Set(), { id: 88, skuId: 8801 });

    expect(isWishlistItemSaved({ id: 88, skuId: 8801 }, state)).toBe(true);
  });
});
