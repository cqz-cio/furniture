import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  addYudaoGiftRegistryItem,
  createYudaoGiftRegistry,
  deleteYudaoGiftRegistryItem,
  getMyYudaoGiftRegistry,
  getPublicYudaoGiftRegistry,
  searchPublicYudaoGiftRegistries,
  updateYudaoGiftRegistry,
  updateYudaoGiftRegistryItem,
} from "../src/services/yudaoGiftRegistryApi.js";

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

describe("Yudao gift registry API module", () => {
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

  it("creates a persistent gift registry through the member endpoint", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        id: 88,
        publicCode: "registry-avery-2026",
        registrantName: "Avery Stone",
        email: "avery@example.com",
        eventType: "Wedding",
        eventDate: "2026-10-01",
        visibility: "public",
        status: "active",
      }),
    );

    await expect(
      createYudaoGiftRegistry(
        {
          event: { type: "Wedding", date: "2026-10-01" },
          registrants: { primaryName: "Avery Stone", email: "avery@example.com" },
          privacy: { visibility: "public" },
        },
        { storage },
      ),
    ).resolves.toMatchObject({
      id: 88,
      publicCode: "registry-avery-2026",
      registrants: { primaryName: "Avery Stone", email: "avery@example.com" },
      event: { type: "Wedding", date: "2026-10-01" },
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/gift-registry/create`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toMatchObject({
      eventType: "Wedding",
      eventDate: "2026-10-01",
      registrantName: "Avery Stone",
      email: "avery@example.com",
      visibility: "public",
    });
  });

  it("reads, updates, searches and publishes registries through stable backend paths", async () => {
    fetchMock
      .mockResolvedValueOnce(mockYudaoResponse({ id: 88, publicCode: "registry-avery-2026" }))
      .mockResolvedValueOnce(mockYudaoResponse({ id: 88, publicCode: "registry-avery-2026" }))
      .mockResolvedValueOnce(mockYudaoResponse({ list: [{ id: 88, publicCode: "registry-avery-2026" }], total: 1 }))
      .mockResolvedValueOnce(mockYudaoResponse({ id: 88, publicCode: "registry-avery-2026" }));

    await getMyYudaoGiftRegistry({ storage });
    await updateYudaoGiftRegistry({ id: 88, privacy: { visibility: "invite_only" } }, { storage });
    await searchPublicYudaoGiftRegistries({ keyword: "Avery", eventMonth: "2026-10" }, { storage });
    await getPublicYudaoGiftRegistry("registry-avery-2026", { storage });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${API_BASE}/member/gift-registry/my`,
      `${API_BASE}/member/gift-registry/update`,
      `${API_BASE}/member/gift-registry/search?keyword=Avery&eventMonth=2026-10&pageNo=1&pageSize=10`,
      `${API_BASE}/member/gift-registry/public/registry-avery-2026`,
    ]);
    expect(fetchMock.mock.calls[1][1].method).toBe("PUT");
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toMatchObject({
      id: 88,
      visibility: "invite_only",
    });
  });

  it("manages registry items with real SPU and SKU identity", async () => {
    fetchMock
      .mockResolvedValueOnce(mockYudaoResponse({ id: 501, spuId: 1001, skuId: 2001, quantityRequested: 2 }))
      .mockResolvedValueOnce(mockYudaoResponse({ id: 501, quantityRequested: 3, priority: "high" }))
      .mockResolvedValueOnce(mockYudaoResponse(true));

    await addYudaoGiftRegistryItem(
      {
        registryId: 88,
        spuId: 1001,
        skuId: 2001,
        productName: "Walnut Single Sofa",
        quantityRequested: 2,
      },
      { storage },
    );
    await updateYudaoGiftRegistryItem({ id: 501, quantityRequested: 3, priority: "high" }, { storage });
    await deleteYudaoGiftRegistryItem(501, { storage });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${API_BASE}/member/gift-registry/item/add`,
      `${API_BASE}/member/gift-registry/item/update`,
      `${API_BASE}/member/gift-registry/item/delete?id=501`,
    ]);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({
      registryId: 88,
      spuId: 1001,
      skuId: 2001,
    });
  });
});
