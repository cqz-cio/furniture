import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readYudaoSession, writeYudaoSession } from "../src/services/authSession.js";
import {
  getRemoteCartItems,
  loginByEmailPassword,
  loginByTradeAccount,
  logoutMember,
  refreshMemberToken,
  requestYudao,
  registerByEmail,
  requestEmailSignInLink,
} from "../src/services/yudaoClient.js";

const API_BASE = "http://127.0.0.1:48080/app-api";

const createStorage = () => {
  const store = new Map();
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
};

const oldSession = {
  userId: 1001,
  accessToken: "fake-old-access-token",
  refreshToken: "fake-old-refresh-token",
  expiresTime: "2026-06-03T18:00:00",
};

const newSession = {
  userId: 1002,
  accessToken: "fake-new-access-token",
  refreshToken: "fake-new-refresh-token",
  expiresTime: "2026-06-03T19:00:00",
};

const mockYudaoResponse = (data) => ({
  ok: true,
  json: async () => ({ code: 0, data }),
});

describe("Yudao member auth client", () => {
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

  it("requestEmailSignInLink posts email with no auth token", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await requestEmailSignInLink("buyer@example.com", { storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-secure-link`);
    expect(init.method).toBe("POST");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({ email: "buyer@example.com" });
  });

  it("loginByEmailPassword posts email credentials and persists the returned session", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(newSession));

    const result = await loginByEmailPassword(
      { email: "buyer@example.com", password: "fake-member-password" },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-login`);
    expect(init.method).toBe("POST");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      email: "buyer@example.com",
      password: "fake-member-password",
    });
    expect(result).toEqual(newSession);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("registerByEmail posts account details and persists the returned session", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(newSession));

    const result = await registerByEmail(
      {
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        password: "fake-member-password",
        emailOptIn: true,
        privacyAccepted: true,
      },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-register`);
    expect(init.method).toBe("POST");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",
      password: "fake-member-password",
      emailOptIn: true,
      privacyAccepted: true,
    });
    expect(result).toEqual(newSession);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("loginByTradeAccount posts trade account details and persists the returned session", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(newSession));

    const result = await loginByTradeAccount(
      { tradeId: "TRADE-100", email: "designer@example.com" },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/trade-login`);
    expect(init.method).toBe("POST");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      tradeId: "TRADE-100",
      email: "designer@example.com",
    });
    expect(result).toEqual(newSession);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("refreshMemberToken posts the encoded refresh token without Authorization and persists the new session", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(newSession));

    const result = await refreshMemberToken("old refresh/token", { storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(
      `${API_BASE}/member/auth/refresh-token?refreshToken=${encodeURIComponent("old refresh/token")}`
    );
    expect(init.method).toBe("POST");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(result).toEqual(newSession);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("logoutMember posts with the stored access token and clears the session", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await logoutMember({ storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/logout`);
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer fake-old-access-token");
    expect(readYudaoSession(storage)).toBe(null);
  });

  it("logoutMember clears the session when the backend returns an HTTP error", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({ code: 500, msg: "fake failure" }),
    });

    await expect(logoutMember({ storage })).rejects.toThrow("Yudao HTTP 500");

    expect(readYudaoSession(storage)).toBe(null);
  });

  it("logoutMember clears the session when the backend returns a business error", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ code: 401, msg: "fake auth failure" }),
    });

    await expect(logoutMember({ storage })).rejects.toThrow("fake auth failure");

    expect(readYudaoSession(storage)).toBe(null);
  });

  it("requestYudao does not fall back to the storage token when token is explicitly blank", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ ok: true }));

    await requestYudao("/example", { token: "", storage });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/example`);
    expect(init.headers).not.toHaveProperty("Authorization");
  });

  it("requestYudao reads the token from the provided storage when token is omitted", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ ok: true }));

    await requestYudao("/example", { storage });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer fake-old-access-token");
  });

  it("refreshes the token and retries once when Yudao returns an auth failure result", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 401, msg: "fake auth expired" }),
      })
      .mockResolvedValueOnce(mockYudaoResponse(newSession))
      .mockResolvedValueOnce(mockYudaoResponse({ validList: [], invalidList: [] }));

    await expect(getRemoteCartItems({ storage })).resolves.toEqual([]);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer fake-old-access-token");
    expect(fetchMock.mock.calls[1][0]).toBe(
      `${API_BASE}/member/auth/refresh-token?refreshToken=fake-old-refresh-token`
    );
    expect(fetchMock.mock.calls[1][1].headers).not.toHaveProperty("Authorization");
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer fake-new-access-token");
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("clears the session and does not retry the original request when token refresh fails", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 401, msg: "fake auth expired" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 400, msg: "fake refresh failed" }),
      });

    await expect(getRemoteCartItems({ storage })).rejects.toThrow("fake refresh failed");

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(readYudaoSession(storage)).toBe(null);
  });

  it("retries with the refreshed token even when callers provided a stale Authorization header", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 401, msg: "fake auth expired" }),
      })
      .mockResolvedValueOnce(mockYudaoResponse(newSession))
      .mockResolvedValueOnce(mockYudaoResponse({ ok: true }));

    await requestYudao("/example", {
      storage,
      headers: { Authorization: "Bearer fake-stale-header-token" },
    });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer fake-stale-header-token");
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer fake-new-access-token");
  });

  it("refreshes and retries when an HTTP 401 response includes a Yudao auth failure result", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({ code: 401, msg: "fake http auth expired" }),
      })
      .mockResolvedValueOnce(mockYudaoResponse(newSession))
      .mockResolvedValueOnce(mockYudaoResponse({ ok: true }));

    await expect(requestYudao("/example", { storage })).resolves.toEqual({ ok: true });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("keeps the refreshed session when the retried original request fails", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 401, msg: "fake auth expired" }),
      })
      .mockResolvedValueOnce(mockYudaoResponse(newSession))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 500, msg: "fake retry failed" }),
      });

    await expect(requestYudao("/example", { storage })).rejects.toThrow("fake retry failed");

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });
});
