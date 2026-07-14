import { beforeEach, describe, expect, it, vi } from "vitest";

const memoryStorage = () => {
  const values = new Map();
  return {
    getItem: (key) => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
    clear: () => values.clear(),
    get length() { return values.size; },
  };
};

beforeEach(() => {
  vi.resetModules();
  vi.unstubAllEnvs();
  vi.stubGlobal("localStorage", memoryStorage());
  vi.stubGlobal("sessionStorage", memoryStorage());
  vi.stubGlobal("crypto", { randomUUID: vi.fn(() => "uuid-1") });
  vi.stubGlobal("fetch", vi.fn());
});

describe("analytics privacy gate", () => {
  it("is disabled by default and creates no identifiers", async () => {
    const analytics = await import("../src/services/analytics.js");
    await analytics.trackHomeView();
    expect(fetch).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("requires consent before identifiers, requests, and cart headers", async () => {
    vi.stubEnv("VITE_BEHAVIOR_TRACKING_ENABLED", "true");
    vi.stubEnv("VITE_ANALYTICS_CONSENT_REQUIRED", "true");
    const analytics = await import("../src/services/analytics.js");
    expect(analytics.analyticsIdentityHeaders()).toEqual({});
    expect(localStorage.length).toBe(0);
    analytics.setAnalyticsConsent({ granted: true, evidence: "cmp-proof" });
    const headers = analytics.analyticsIdentityHeaders(1000);
    expect(headers["x-analytics-visitor-id"]).toBeTruthy();
    expect(headers["x-analytics-session-id"]).toBeTruthy();
    expect(headers["x-analytics-consent-evidence"]).toBe("cmp-proof");
    analytics.setAnalyticsConsent({ granted: false });
    expect(analytics.analyticsIdentityHeaders()).toEqual({});
    expect(localStorage.getItem("oakved_analytics_visitor")).toBeNull();
    expect(sessionStorage.getItem("oakved_session_id")).toBeNull();
  });

  it("rejects granted consent without server-verifiable evidence", async () => {
    vi.stubEnv("VITE_BEHAVIOR_TRACKING_ENABLED", "true");
    vi.stubEnv("VITE_ANALYTICS_CONSENT_REQUIRED", "true");
    const analytics = await import("../src/services/analytics.js");
    analytics.setAnalyticsConsent({ granted: true, evidence: "" });

    expect(analytics.analyticsIdentityHeaders()).toEqual({});
    expect(await analytics.trackHomeView()).toBe(false);
    expect(fetch).not.toHaveBeenCalled();
    expect(localStorage.getItem("oakved_analytics_visitor")).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it("retries once with the same event id", async () => {
    vi.stubEnv("VITE_BEHAVIOR_TRACKING_ENABLED", "true");
    vi.stubEnv("VITE_ANALYTICS_CONSENT_REQUIRED", "false");
    fetch.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce({ ok: true });
    const analytics = await import("../src/services/analytics.js");
    await analytics.trackProductDetailView(88);
    expect(fetch).toHaveBeenCalledTimes(2);
    const first = JSON.parse(fetch.mock.calls[0][1].body);
    const second = JSON.parse(fetch.mock.calls[1][1].body);
    expect(second.eventId).toBe(first.eventId);
    expect(first.spuId).toBe(88);
    expect(first).not.toHaveProperty("userId");
    expect(first).not.toHaveProperty("clientTime");
    expect(first).not.toHaveProperty("consentGranted");
  });
});
