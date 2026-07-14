import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getYudaoTradeProfile,
  normalizeYudaoTradeProfile,
  submitTradeApplication,
  uploadTradeApplicationAttachment,
} from "../src/services/yudaoTradeApi.js";

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

describe("Yudao Trade API module", () => {
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

  it("normalizes member profile trade identity into an active trade profile", () => {
    expect(
      normalizeYudaoTradeProfile({
        id: 88,
        email: "designer@example.com",
        tradeId: " RH-TRADE-10086 ",
      }),
    ).toMatchObject({
      userId: 88,
      email: "designer@example.com",
      tradeId: "RH-TRADE-10086",
      status: "active",
      active: true,
    });
  });

  it("reads the logged-in member profile as the trade account status source", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        id: 88,
        email: "designer@example.com",
        tradeId: "RH-TRADE-10086",
      }),
    );

    await expect(getYudaoTradeProfile({ storage })).resolves.toMatchObject({
      tradeId: "RH-TRADE-10086",
      status: "active",
      active: true,
    });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/member/user/get`);
  });

  it("submits the public trade application through the Trade API module", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ id: 1001, status: "pending" }));

    await expect(submitTradeApplication({ businessName: "Studio Oak" }, { storage })).resolves.toMatchObject({
      id: 1001,
      status: "pending",
    });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/trade-application`);
    expect(options.method).toBe("POST");
    expect(options.headers.Authorization).toBeUndefined();
    expect(JSON.parse(options.body)).toMatchObject({ businessName: "Studio Oak" });
  });

  it("uploads trade application attachments through the Trade API module", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse("https://cdn.example.com/license.pdf"));
    const file = new File(["license"], "license.pdf", { type: "application/pdf" });

    await expect(uploadTradeApplicationAttachment(file, { storage })).resolves.toEqual({
      name: "license.pdf",
      url: "https://cdn.example.com/license.pdf",
    });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/infra/file/upload`);
    expect(options.method).toBe("POST");
    expect(options.headers.Authorization).toBeUndefined();
    expect(options.headers["Content-Type"]).toBeUndefined();
    expect(options.body).toBeInstanceOf(FormData);
  });
});
