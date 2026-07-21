import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getAllProducts, getProductDetail, getProductPage } from "../src/services/yudaoProductApi.js";

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

const productRow = {
  id: 101,
  name: "Belgian linen sofa",
  introduction: "Deep seat",
  picUrl: "/sofa.jpg",
  sliderPicUrls: ["/sofa-1.jpg", ""],
  salesCount: 12,
  skus: [{ id: 201, price: 249900, marketPrice: 299900, stock: 8 }],
};

describe("Yudao product API module", () => {
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

  it("loads paged SPU products and maps them into local product cards", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ list: [productRow], total: 1 }));

    const page = await getProductPage({ pageNo: 2, pageSize: 12, keyword: "" }, { storage });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/product/spu/page?pageNo=2&pageSize=12`);
    expect(page).toMatchObject({
      total: 1,
      list: [
        {
          id: 101,
          skuId: 201,
          name: "Belgian linen sofa",
          subtitle: "Deep seat",
          cover: "/sofa.jpg",
          gallery: ["/sofa-1.jpg"],
          price: 2499,
          marketPrice: 2999,
          stock: 8,
          salesCount: 12,
          source: "yudao",
        },
      ],
    });
  });

  it("loads product detail by encoded SPU id", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(productRow));

    const product = await getProductDetail("sofa/101", { storage });

    expect(fetchMock.mock.calls[0][0]).toBe(
      `${API_BASE}/product/spu/get-detail?id=${encodeURIComponent("sofa/101")}`
    );
    expect(product).toMatchObject({
      id: 101,
      skuId: 201,
      name: "Belgian linen sofa",
      price: 2499,
    });
  });

  it("loads every backend page instead of silently stopping at the first 24 products", async () => {
    fetchMock
      .mockResolvedValueOnce(mockYudaoResponse({ list: [{ ...productRow, id: 101 }, { ...productRow, id: 102 }], total: 3 }))
      .mockResolvedValueOnce(mockYudaoResponse({ list: [{ ...productRow, id: 103 }], total: 3 }));

    const page = await getAllProducts({ pageSize: 2 }, { storage });

    expect(page.list.map((product) => product.id)).toEqual([101, 102, 103]);
    expect(page.total).toBe(3);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${API_BASE}/product/spu/page?pageNo=1&pageSize=2`,
      `${API_BASE}/product/spu/page?pageNo=2&pageSize=2`,
    ]);
  });
});
