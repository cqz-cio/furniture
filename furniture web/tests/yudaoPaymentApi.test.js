import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getPayOrder, submitPayOrder } from "../src/services/yudaoPaymentApi.js";

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

describe("Yudao payment API module", () => {
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

  it("submits a Yudao pay order payload", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ status: "submitted", displayMode: "url" }));

    await expect(
      submitPayOrder(
        {
          id: 7002,
          channelCode: "mock",
          channelExtras: {},
          displayMode: "url",
          returnUrl: "https://shop.example/orders?id=902",
        },
        { storage },
      ),
    ).resolves.toMatchObject({ status: "submitted" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/pay/order/submit`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({
      id: 7002,
      channelCode: "mock",
      channelExtras: {},
      displayMode: "url",
      returnUrl: "https://shop.example/orders?id=902",
    });
  });

  it("rejects non-numeric pay order ids before submitting payment", async () => {
    await expect(
      submitPayOrder(
        {
          id: "PAY/7002",
          channelCode: "mock",
          channelExtras: {},
          displayMode: "url",
          returnUrl: "https://shop.example/orders?id=902",
        },
        { storage },
      ),
    ).rejects.toThrow("Invalid Yudao pay order id");

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("loads a Yudao pay order by id", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ id: 7002, status: 10 }));

    await expect(getPayOrder(7002, { storage })).resolves.toMatchObject({ id: 7002, status: 10 });

    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/pay/order/get?id=7002`);
  });

  it("rejects empty pay order references before loading payment status", async () => {
    await expect(getPayOrder("  ", { storage })).rejects.toThrow("Invalid Yudao pay order reference");

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("loads a Yudao pay order number with the no query field", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ id: 7002, no: "PAY/7002", status: 10 }));

    await expect(getPayOrder("PAY/7002", { storage })).resolves.toMatchObject({ id: 7002, status: 10 });

    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/pay/order/get?no=${encodeURIComponent("PAY/7002")}`);
  });

  it("asks Yudao to sync waiting pay orders when requested", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ id: 7002, status: 10 }));

    await expect(getPayOrder("PAY/7002", { storage, sync: true })).resolves.toMatchObject({ id: 7002, status: 10 });

    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/pay/order/get?no=${encodeURIComponent("PAY/7002")}&sync=true`);
  });
});
