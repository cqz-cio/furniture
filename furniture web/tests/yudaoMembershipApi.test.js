import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createYudaoMembershipCheckoutIntent,
  getYudaoMembershipProfile,
} from "../src/services/yudaoMembershipApi.js";

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

describe("Yudao membership API module", () => {
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

  it("reads the persistent membership profile from the member membership endpoint", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        status: "active_annual",
        planName: "Annual Membership",
        memberId: "OAKVED-MEMBER-88",
        accountEmail: "ada@example.com",
        memberEmail: "ada@example.com",
        startedAt: "2026-07-01T00:00:00",
        expiresAt: "2027-07-01T00:00:00",
        autoRenew: true,
      }),
    );

    await expect(getYudaoMembershipProfile({ storage })).resolves.toMatchObject({
      status: "active_annual",
      planName: "Annual Membership",
      memberId: "OAKVED-MEMBER-88",
      autoRenew: true,
    });
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE}/member/membership/get`);
  });

  it("creates a membership checkout intent without activating benefits", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        planCode: "annual_membership",
        checkoutPath: "/checkout/auth?intent=membership",
        requiresPayment: true,
      }),
    );

    await expect(createYudaoMembershipCheckoutIntent({ planCode: "annual_membership" }, { storage })).resolves.toEqual({
      planCode: "annual_membership",
      checkoutPath: "/checkout/auth?intent=membership",
      requiresPayment: true,
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/membership/checkout-intent`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ planCode: "annual_membership" });
  });
});
