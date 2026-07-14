import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createRemoteAddressVerificationProvider,
  normalizeRemoteAddressVerification,
} from "../src/services/addressVerificationProvider.js";

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

const localVerification = {
  status: "verified",
  reason: "",
  requiresConfirmation: false,
  originalAddress: {
    firstName: "Ada",
    lastName: "Lovelace",
    street: "1600 AMPHITHEATRE PKWY",
    city: "Mountain View",
    state: "CA",
    postalCode: "94043",
    phone: "555-0100",
    country: "United States",
  },
  suggestedAddress: {
    firstName: "Ada",
    lastName: "Lovelace",
    street: "1600 AMPHITHEATRE PKWY",
    city: "Mountain View",
    state: "CA",
    postalCode: "94043",
    phone: "555-0100",
    country: "United States",
  },
};

describe("address verification provider", () => {
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

  it("stays disabled when no backend proxy path is configured", () => {
    expect(createRemoteAddressVerificationProvider({ path: "" })).toBeNull();
  });

  it("uses the member address verification endpoint by default", async () => {
    fetchMock.mockResolvedValueOnce(mockYudaoResponse({ status: "verified" }));
    const provider = createRemoteAddressVerificationProvider({ storage });

    expect(provider).not.toBeNull();
    await provider.verifyAddress(localVerification.originalAddress, localVerification);

    const [url] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/address/verify`);
  });

  it("loads the backend address verification provider status", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        mode: "fallback",
        fallbackActive: true,
        providers: [
          { source: "google-address-validation", enabled: false, reason: "missing-api-key" },
          { source: "backend-address-verification", enabled: true, fallback: true },
        ],
      }),
    );
    const provider = createRemoteAddressVerificationProvider({ storage });

    await expect(provider.getStatus()).resolves.toMatchObject({
      mode: "fallback",
      fallbackActive: true,
      providers: [
        { source: "google-address-validation", enabled: false, reason: "missing-api-key" },
        { source: "backend-address-verification", enabled: true, fallback: true },
      ],
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/address/verification-status`);
    expect(init.method).toBe("GET");
  });

  it("posts the entered address to the configured backend proxy path", async () => {
    fetchMock.mockResolvedValueOnce(
      mockYudaoResponse({
        status: "suggested",
        reason: "cass-standardized",
        suggestedAddress: {
          street: "1600 AMPHITHEATRE PKWY",
          city: "Mountain View",
          state: "CA",
          postalCode: "94043-1351",
        },
      }),
    );
    const provider = createRemoteAddressVerificationProvider({
      path: "/member/address/verify",
      storage,
    });

    await expect(
      provider.verifyAddress(
        {
          firstName: "Ada",
          lastName: "Lovelace",
          street: "1600 amphitheatre parkway",
          city: "Mountain View",
          state: "CA",
          postalCode: "94043",
          phone: "555-0100",
        },
        localVerification,
      ),
    ).resolves.toMatchObject({
      source: "remote-address-verification",
      status: "suggested",
      reason: "cass-standardized",
      requiresConfirmation: true,
      suggestedAddress: {
        street: "1600 AMPHITHEATRE PKWY",
        postalCode: "94043-1351",
      },
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE}/member/address/verify`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({
      address: {
        firstName: "Ada",
        lastName: "Lovelace",
        street: "1600 amphitheatre parkway",
        city: "Mountain View",
        state: "CA",
        postalCode: "94043",
        phone: "555-0100",
      },
      localVerification,
    });
  });

  it("normalizes alternate backend response shapes into checkout verification", () => {
    expect(
      normalizeRemoteAddressVerification(
        {
          providerResponseId: "google-response-1",
          providerStatus: "fallback",
          deliverable: true,
          standardizedAddress: {
            street: "1 MAIN ST",
            city: "New York",
            state: "NY",
            postalCode: "10001-0001",
          },
        },
        localVerification,
        "smarty-us-street",
      ),
    ).toMatchObject({
      source: "smarty-us-street",
      status: "suggested",
      reason: "remote-standardized",
        requiresConfirmation: true,
        providerResponseId: "google-response-1",
        providerStatus: "fallback",
        originalAddress: localVerification.originalAddress,
        suggestedAddress: {
        street: "1 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001-0001",
      },
    });
  });
});
