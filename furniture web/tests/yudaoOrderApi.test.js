import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createOrder, getOrderDetail, getOrderPage, settleOrder } from "../src/services/yudaoOrderApi.js";

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

const orderRow = {
  id: 901,
  no: "SO-901",
  status: 20,
  payStatus: true,
  payPrice: 129900,
  payOrderId: 7001,
  createTime: "2026-06-12 12:00:00",
  items: [
    {
      id: 1,
      skuId: 201,
      spuName: "Linen sofa",
      picUrl: "/sofa.jpg",
      count: 2,
      price: 64950,
      marketPrice: 79900,
    },
  ],
};

describe("Yudao order API module", () => {
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

  it("loads order settlement with encoded item query fields and maps prices", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        price: { payPrice: 129900, totalPrice: 149900, deliveryPrice: 0 },
        items: [{ skuId: 201, count: 2, spuName: "Linen sofa", picUrl: "/sofa.jpg", payPrice: 129900 }],
      })
    );

    const settlement = await settleOrder(
      {
        items: [{ skuId: 201, count: 2, cartId: 501 }],
        pointStatus: false,
        deliveryType: 1,
        addressId: 301,
      },
      { storage }
    );

    const [url] = fetchMock.mock.calls[0];
    expect(url).toContain(`${API_BASE}/trade/order/settlement?`);
    expect(url).toContain("items%5B0%5D.skuId=201");
    expect(url).toContain("items%5B0%5D.count=2");
    expect(url).toContain("items%5B0%5D.cartId=501");
    expect(url).toContain("pointStatus=false");
    expect(url).toContain("deliveryType=1");
    expect(url).toContain("addressId=301");
    expect(settlement).toMatchObject({
      payPrice: 1299,
      totalPrice: 1499,
      deliveryPrice: 0,
      items: [{ skuId: 201, count: 2, name: "Linen sofa", payPrice: 1299 }],
    });
  });

  it("posts order creation payloads and returns the Yudao pay order id", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ id: 901, payOrderId: 7001 }));

    await expect(createOrder({ addressId: 301, items: [{ skuId: 201, count: 2 }] }, { storage })).resolves.toEqual({
      id: 901,
      payOrderId: 7001,
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/trade/order/create`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ addressId: 301, items: [{ skuId: 201, count: 2 }] });
  });

  it("maps order pages and detail responses", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ list: [orderRow], total: 1 }));
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(orderRow));

    await expect(getOrderPage({ pageNo: 2, pageSize: 5 }, { storage })).resolves.toMatchObject({
      total: 1,
      list: [{ id: 901, no: "SO-901", payPrice: 1299 }],
    });
    await expect(getOrderDetail("901", { storage })).resolves.toMatchObject({
      id: 901,
      no: "SO-901",
      items: [{ skuId: 201, name: "Linen sofa", price: 649.5 }],
    });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/trade/order/page?pageNo=2&pageSize=5`);
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE}/trade/order/get-detail?id=901`);
  });

  it("does not request Yudao order detail with a non-numeric order id", async () => {
    await expect(getOrderDetail("SO/901", { storage })).resolves.toBeNull();

    expect(fetchMock).not.toHaveBeenCalled();
  });
});
