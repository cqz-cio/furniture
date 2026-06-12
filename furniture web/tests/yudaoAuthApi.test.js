import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readYudaoSession } from "../src/services/authSession.js";
import { loginByEmailPassword, requestEmailSignInLink } from "../src/services/yudaoAuthApi.js";

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

describe("Yudao auth API module", () => {
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

  it("exposes public email sign-in requests without an Authorization header", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await requestEmailSignInLink("buyer@example.com", { storage });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-secure-link`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({ email: "buyer@example.com" });
  });

  it("persists sessions returned by email password login", async () => {
    const session = {
      userId: 1002,
      accessToken: "fake-new-access-token",
      refreshToken: "fake-new-refresh-token",
      expiresTime: "2026-06-03T19:00:00",
    };
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(session));

    const result = await loginByEmailPassword(
      { email: "buyer@example.com", password: "fake-pass-123" },
      { storage }
    );

    expect(result).toEqual(session);
    expect(readYudaoSession(storage)).toEqual(session);
  });
});
