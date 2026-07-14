import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readYudaoSession, writeYudaoSession } from "../src/services/authSession.js";
import {
  createEmailCaptchaChallenge,
  loginByEmailPassword,
  loginByTradeAccount,
  logoutMember,
  registerByEmail,
  requestEmailSignInLink,
  sendEmailRegistrationCode,
  sendTradeLoginCode,
  submitTradeApplication,
  uploadTradeApplicationAttachment,
  verifyEmailCaptchaChallenge,
} from "../src/services/yudaoAuthApi.js";
import { getRemoteCartItems } from "../src/services/yudaoCartApi.js";
import {
  getYudaoAppApiBase,
  getYudaoAppTenantId,
  isYudaoAuthError,
  isYudaoBusinessError,
  isYudaoNetworkError,
  refreshMemberToken,
  requestYudao,
} from "../src/services/yudaoRequest.js";

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
    vi.unstubAllEnvs();
  });

  it("requestEmailSignInLink posts email with no auth token", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await requestEmailSignInLink("buyer@example.com", { storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-secure-link`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({ email: "buyer@example.com" });
  });

  it("loginByEmailPassword posts email credentials and persists the returned session", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(newSession));

    const result = await loginByEmailPassword(
      { email: "buyer@example.com", password: "fake-pass-123" },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-login`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      email: "buyer@example.com",
      password: "fake-pass-123",
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
        password: "fake-pass-123",
        code: "123456",
        emailOptIn: true,
        privacyAccepted: true,
      },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/email-register`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",
      password: "fake-pass-123",
      code: "123456",
      emailOptIn: true,
      privacyAccepted: true,
    });
    expect(result).toEqual(newSession);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("sendEmailRegistrationCode posts a public email code request", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await sendEmailRegistrationCode("ada@example.com", { storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/send-email-code`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      email: "ada@example.com",
      scene: 5,
    });
  });

  it("sendEmailRegistrationCode can include a captcha verification token", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await sendEmailRegistrationCode("ada@example.com", {
      storage,
      captchaVerification: "captcha-token",
    });

    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({
      email: "ada@example.com",
      scene: 5,
      captchaVerification: "captcha-token",
    });
  });

  it("creates and verifies the email captcha challenge without auth", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        challengeId: "challenge-1",
        instruction: "Enter the captcha",
        imageBase64: "data:image/png;base64,fake",
        captchaType: "MATH",
      })
    );
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ captchaVerification: "captcha-token" }));

    await expect(createEmailCaptchaChallenge({ storage })).resolves.toMatchObject({
      challengeId: "challenge-1",
      imageBase64: "data:image/png;base64,fake",
    });
    await expect(
      verifyEmailCaptchaChallenge({ challengeId: "challenge-1", code: "8" }, { storage })
    ).resolves.toEqual({ captchaVerification: "captcha-token" });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/member/auth/email-captcha/challenge`);
    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty("Authorization");
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE}/member/auth/email-captcha/verify`);
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      challengeId: "challenge-1",
      code: "8",
    });
  });

  it("loginByTradeAccount posts trade account details and persists the returned session", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(newSession));

    const result = await loginByTradeAccount(
      { tradeId: "TRADE-100", email: "designer@example.com", code: "123456" },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/trade-login`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      tradeId: "TRADE-100",
      email: "designer@example.com",
      code: "123456",
    });
    expect(result).toEqual(newSession);
    expect(readYudaoSession(storage)).toEqual(newSession);
  });

  it("sendTradeLoginCode posts trade account details without auth", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse(true));

    await sendTradeLoginCode(
      { tradeId: "TRADE-100", email: "designer@example.com", captchaVerification: "captcha-token" },
      { storage }
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/trade-login-code`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual({
      tradeId: "TRADE-100",
      email: "designer@example.com",
      captchaVerification: "captcha-token",
    });
  });

  it("submitTradeApplication posts a public trade application payload", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ id: 88, status: 0 }));

    const payload = {
      businessName: "Studio Ada",
      country: "United States",
      primaryEmail: "designer@example.com",
      authorizedUsers: [{ firstName: "Ada", lastName: "Lovelace", email: "designer@example.com" }],
      businessDocuments: [{ name: "license.pdf", url: "https://cdn.example/license.pdf" }],
      taxDocuments: [],
    };
    const result = await submitTradeApplication(payload, { storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/auth/trade-application`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(JSON.parse(init.body)).toEqual(payload);
    expect(result).toEqual({ id: 88, status: 0 });
  });

  it("uploadTradeApplicationAttachment posts a public multipart file upload", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse("https://cdn.example/license.pdf"));
    const file = new File(["fake-license"], "license.pdf", { type: "application/pdf" });

    const result = await uploadTradeApplicationAttachment(file, { storage });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/infra/file/upload`);
    expect(init.method).toBe("POST");
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(init.headers).not.toHaveProperty("Content-Type");
    expect(init.body).toBeInstanceOf(FormData);
    expect(init.body.get("file")).toBe(file);
    expect(init.body.get("directory")).toBe("trade/application");
    expect(result).toEqual({
      name: "license.pdf",
      url: "https://cdn.example/license.pdf",
    });
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
    expect(init.headers["tenant-id"]).toBe("121");
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
    expect(init.headers["tenant-id"]).toBe("121");
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
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers).not.toHaveProperty("Authorization");
  });

  it("requestYudao reads the token from the provided storage when token is omitted", async () => {
    writeYudaoSession(oldSession, storage);
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ ok: true }));

    await requestYudao("/example", { storage });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["tenant-id"]).toBe("121");
    expect(init.headers.Authorization).toBe("Bearer fake-old-access-token");
  });

  it("allows callers to disable or override the app tenant header", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ ok: true }));
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ ok: true }));

    await requestYudao("/without-tenant", { tenantId: "", storage });
    await requestYudao("/with-custom-tenant", { tenantId: "9", storage });

    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty("tenant-id");
    expect(fetchMock.mock.calls[1][1].headers["tenant-id"]).toBe("9");
  });

  it("reads legacy Yudao storefront env names when canonical app env names are absent", () => {
    vi.stubEnv("VITE_YUDAO_APP_API_BASE", "");
    vi.stubEnv("VITE_YUDAO_APP_TENANT_ID", "");
    vi.stubEnv("VITE_YUDAO_API_BASE_URL", "https://legacy.example/app-api/");
    vi.stubEnv("VITE_YUDAO_TENANT_ID", "88");

    expect(getYudaoAppApiBase()).toBe("https://legacy.example/app-api");
    expect(getYudaoAppTenantId()).toBe("88");
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
    expect(fetchMock.mock.calls[1][1].headers["tenant-id"]).toBe("121");
    expect(fetchMock.mock.calls[1][1].headers).not.toHaveProperty("Authorization");
    expect(fetchMock.mock.calls[2][1].headers["tenant-id"]).toBe("121");
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

  it("classifies Yudao business, auth, and network errors", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ code: 500, msg: "fake business failure", data: { field: "sku" } }),
    });
    await expect(requestYudao("/business", { storage })).rejects.toMatchObject({
      kind: "business",
      code: 500,
      data: { field: "sku" },
    });

    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ code: 401, msg: "fake auth failure" }),
    });
    await expect(requestYudao("/auth", { storage })).rejects.toMatchObject({
      kind: "auth",
      code: 401,
      status: 401,
    });

    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await expect(requestYudao("/network", { storage })).rejects.toMatchObject({
      kind: "network",
    });
  });

  it("exposes Yudao error type guards", async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 500, msg: "fake business failure" }),
      })
      .mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const businessError = await requestYudao("/business", { storage }).catch((error) => error);
    const networkError = await requestYudao("/network", { storage }).catch((error) => error);

    expect(isYudaoBusinessError(businessError)).toBe(true);
    expect(isYudaoNetworkError(networkError)).toBe(true);
    expect(isYudaoAuthError({ kind: "auth", code: 401 })).toBe(true);
  });
});
