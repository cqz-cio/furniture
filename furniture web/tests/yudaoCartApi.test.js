import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { addCartItem, getRemoteCartItems } from "../src/services/yudaoCartApi.js";

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

describe("Yudao cart API module", () => {
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

  it("posts cart additions to the trade cart endpoint", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(77));

    await addCartItem(12345, 2, { storage });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/trade/cart/add`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(JSON.parse(init.body)).toEqual({ skuId: 12345, count: 2 });
  });

  it("carries gift registry context when adding registry gifts to the remote cart", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(78));

    await addCartItem(12345, 1, {
      storage,
      registryContext: {
        registryId: 88,
        registryItemId: 501,
      },
    });

    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({
      skuId: 12345,
      count: 1,
      registryId: 88,
      registryItemId: 501,
    });
  });

  it("maps remote valid and invalid cart rows into local cart items", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        validList: [
          {
            id: 501,
            count: 3,
            selected: true,
            spu: { id: 10, name: "Linen sofa", picUrl: "/sofa.jpg" },
            sku: { id: 20, price: 129900, marketPrice: 149900, stock: 8 },
          },
        ],
        invalidList: [
          {
            id: 502,
            count: 1,
            selected: false,
            spu: { id: 11, name: "Oak chair" },
            sku: { id: 21, price: 49900, stock: 0 },
          },
        ],
      })
    );

    const items = await getRemoteCartItems({ storage });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/trade/cart/list`);
    expect(items).toMatchObject([
      { cartId: 501, spuId: 10, skuId: 20, name: "Linen sofa", quantity: 3, price: 1299, selected: true },
      { cartId: 502, spuId: 11, skuId: 21, name: "Oak chair", quantity: 1, price: 499, selected: false },
    ]);
  });
});
