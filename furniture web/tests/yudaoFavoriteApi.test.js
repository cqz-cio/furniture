import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createFavorite,
  deleteFavorite,
  getFavoriteCount,
  getFavoriteExists,
  getRemoteWishlistItems,
  syncLocalWishlistToRemote,
  updateFavoriteCount,
} from "../src/services/yudaoFavoriteApi.js";

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

describe("Yudao favorite API module", () => {
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

  it("posts favorite creation to the product favorite endpoint", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(77));

    await createFavorite(
      {
        spuId: 12345,
        skuId: 54321,
        quantity: 2,
        name: "Cloud Sofa",
        cover: "/sofa.jpg",
        price: 2599,
        marketPrice: 3299,
        color: "Wheat",
        fabric: "Textured Linen",
        dimensions: "92W",
        delivery: "Ships in 3-7 days",
      },
      { storage },
    );

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/product/favorite/create`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(JSON.parse(init.body)).toEqual({
      spuId: 12345,
      skuId: 54321,
      count: 2,
      spuName: "Cloud Sofa",
      picUrl: "/sofa.jpg",
      price: 259900,
      marketPrice: 329900,
      color: "Wheat",
      fabric: "Textured Linen",
      dimensions: "92W",
      delivery: "Ships in 3-7 days",
    });
  });

  it("deletes favorites with the backend request body shape", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await deleteFavorite({ spuId: 12345, skuId: 54321 }, { storage });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/product/favorite/delete`);
    expect(init.method).toBe("DELETE");
    expect(JSON.parse(init.body)).toEqual({ spuId: 12345, skuId: 54321 });
  });

  it("keeps legacy delete by SPU compatible for older callers", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await deleteFavorite(12345, { storage });

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ spuId: 12345 });
  });

  it("updates favorite quantity with SPU and SKU identity", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await updateFavoriteCount({ spuId: 12345, skuId: 54321 }, 3, { storage });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/product/favorite/update-count`);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({ spuId: 12345, skuId: 54321, count: 3 });
  });

  it("loads favorite pages and maps them into wishlist rows", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        total: 1,
        list: [
          {
            id: 9,
            spuId: 88,
            skuId: 8801,
            count: 4,
            spuName: "Oak Chair",
            picUrl: "/chair.jpg",
            price: 89900,
            color: "Charcoal",
            fabric: "Performance weave",
            dimensions: "84W",
            delivery: "Ships next week",
          },
        ],
      }),
    );

    const page = await getRemoteWishlistItems({ pageNo: 2, pageSize: 12 }, { storage });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/product/favorite/page?pageNo=2&pageSize=12`);
    expect(page).toMatchObject({
      total: 1,
      list: [
        {
          favoriteId: 9,
          id: 88,
          skuId: 8801,
          name: "Oak Chair",
          price: 899,
          color: "Charcoal",
          fabric: "Performance weave",
          dimensions: "84W",
          delivery: "Ships next week",
          quantity: 4,
          source: "yudao",
        },
      ],
    });
  });

  it("checks favorite existence and count through backend endpoints", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(3));

    await expect(getFavoriteExists(88, { storage })).resolves.toBe(true);
    await expect(getFavoriteCount({ storage })).resolves.toBe(3);

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/product/favorite/exits?spuId=88`);
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE}/product/favorite/get-count`);
  });

  it("syncs local wishlist rows to backend favorites by SPU id", async () => {
    fetchMock.mockResolvedValue(mockYudaoResponse(1));

    const result = await syncLocalWishlistToRemote(
      [
        { id: 88, skuId: 8801, name: "Oak Chair" },
        { spuId: 89, skuId: 8901, name: "Cloud Sofa" },
        { skuId: 9001, name: "Missing SPU" },
      ],
      { storage },
    );

    expect(result).toEqual({ attempted: 2, succeeded: 2, failedItems: [] });
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${API_BASE}/product/favorite/create`,
      `${API_BASE}/product/favorite/create`,
    ]);
    expect(fetchMock.mock.calls.map(([, init]) => JSON.parse(init.body))).toEqual([
      { spuId: 88, skuId: 8801, count: 1, spuName: "Oak Chair" },
      { spuId: 89, skuId: 8901, count: 1, spuName: "Cloud Sofa" },
    ]);
  });

  it("reports wishlist rows that failed to sync with a backend business error", async () => {
    fetchMock
      .mockResolvedValueOnce(mockYudaoResponse(1))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 500, msg: "favorite validation failed" }),
      });

    const failedItem = { spuId: 89, skuId: 8901, name: "Cloud Sofa" };
    const result = await syncLocalWishlistToRemote(
      [
        { id: 88, skuId: 8801, name: "Oak Chair" },
        failedItem,
      ],
      { storage },
    );

    expect(result).toEqual({ attempted: 2, succeeded: 1, failedItems: [failedItem] });
  });
});
