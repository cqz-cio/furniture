import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getAddressList,
  getMemberProfile,
  requestEmailVerificationLink,
  updateMemberProfile,
} from "../src/services/yudaoMemberApi.js";

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

describe("Yudao member API module", () => {
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

  it("maps member address rows from the address list endpoint", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse([
        {
          id: 301,
          name: "Ada Lovelace",
          mobile: "555-0100",
          areaName: "Boston, MA",
          detailAddress: "12 Main St",
        },
      ])
    );

    const addresses = await getAddressList({ storage });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/member/address/list`);
    expect(addresses).toEqual([
      {
        id: 301,
        name: "Ada Lovelace",
        mobile: "555-0100",
        areaName: "Boston, MA",
        detailAddress: "12 Main St",
        label: "Ada Lovelace - 555-0100 - Boston, MA 12 Main St",
        raw: {
          id: 301,
          name: "Ada Lovelace",
          mobile: "555-0100",
          areaName: "Boston, MA",
          detailAddress: "12 Main St",
        },
      },
    ]);
  });

  it("maps member profiles and posts profile updates", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        id: 88,
        nickname: "Ada",
        name: "Ada Lovelace",
        email: "ada@example.com",
        mobile: "555-0100",
        areaId: 10,
        areaName: "Boston, MA",
        sex: 2,
        emailVerified: 1,
      })
    );
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await expect(getMemberProfile({ storage })).resolves.toMatchObject({
      id: 88,
      nickname: "Ada",
      emailVerified: true,
    });
    await updateMemberProfile({ nickname: "Ada L.", areaId: 10 }, { storage });

    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/member/user/get`);
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE}/member/user/update`);
    expect(fetchMock.mock.calls[1][1].method).toBe("PUT");
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      nickname: "Ada L.",
      areaId: 10,
    });
  });

  it("posts email verification requests to the member user endpoint", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await requestEmailVerificationLink("ada@example.com", { storage });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/user/send-email-verify-link`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ email: "ada@example.com" });
  });
});
