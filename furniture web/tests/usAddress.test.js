import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  buildAddressConfirmationRecord,
  createUsPostalRegionIndex,
  getUsStateOptions,
  resolveUsPostalRegion,
  verifyUsCheckoutAddressWithProvider,
  verifyUsCheckoutAddress,
  YUDAO_US_DEFAULT_AREA_ID,
} from "../src/services/usAddress.js";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("US checkout address helpers", () => {
  it("keeps the Yudao default US area id configurable", () => {
    const source = readSource("../src/services/usAddress.js");

    expect(Number.isFinite(YUDAO_US_DEFAULT_AREA_ID)).toBe(true);
    expect(source).toContain("VITE_YUDAO_US_DEFAULT_AREA_ID");
  });

  it("provides state options for US checkout forms", () => {
    expect(getUsStateOptions()).toEqual(
      expect.arrayContaining([
        { code: "CA", name: "California" },
        { code: "NY", name: "New York" },
      ]),
    );
  });

  it("resolves known ZIP codes to city and state suggestions", () => {
    expect(resolveUsPostalRegion("10001-1234")).toMatchObject({
      city: "New York",
      state: "NY",
      postalCode: "10001",
    });
  });

  it("builds a ZIP lookup from imported postal region rows", () => {
    const importedIndex = createUsPostalRegionIndex([
      { zip: "94105-1234", city: "san francisco", state_id: "ca" },
      { postal_code: "02116", primary_city: "Boston", state: "MA" },
    ]);

    expect(resolveUsPostalRegion("94105", importedIndex)).toEqual({
      city: "San Francisco",
      state: "CA",
      postalCode: "94105",
    });
    expect(resolveUsPostalRegion("02116", importedIndex)).toEqual({
      city: "Boston",
      state: "MA",
      postalCode: "02116",
    });
  });

  it("verifies and standardizes a matching US address", () => {
    expect(
      verifyUsCheckoutAddress({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "123 main street",
        city: "New York",
        state: "NY",
        postalCode: "10001",
        phone: "555-0100",
      }),
    ).toMatchObject({
      status: "verified",
      requiresConfirmation: false,
      suggestedAddress: {
        street: "123 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    });
  });

  it("suggests the ZIP-matched city and state when the user input conflicts", () => {
    expect(
      verifyUsCheckoutAddress({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "123 main street",
        city: "Brooklyn",
        state: "CA",
        postalCode: "10001",
        phone: "555-0100",
      }),
    ).toMatchObject({
      status: "suggested",
      reason: "postal-region-mismatch",
      requiresConfirmation: true,
      suggestedAddress: {
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    });
  });

  it("marks incomplete or unknown addresses as unverified for explicit confirmation", () => {
    expect(verifyUsCheckoutAddress({ street: "", postalCode: "99999" })).toMatchObject({
      status: "unverified",
      requiresConfirmation: true,
      suggestedAddress: null,
    });
  });

  it("builds an audit record for the address the user chose", () => {
    const verification = {
      ...verifyUsCheckoutAddress({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "123 main street",
        city: "Brooklyn",
        state: "CA",
        postalCode: "10001",
        phone: "555-0100",
      }),
      source: "google-address-validation",
      deliverable: true,
      providerResponseId: "google-response-1",
      metadata: {
        responseId: "google-response-1",
      },
    };

    expect(buildAddressConfirmationRecord(verification, "suggested")).toMatchObject({
      source: "google-address-validation",
      status: "suggested",
      reason: "postal-region-mismatch",
      choice: "suggested",
      deliverable: true,
      providerResponseId: "google-response-1",
      metadata: {
        responseId: "google-response-1",
      },
      selectedAddress: {
        city: "New York",
        state: "NY",
      },
    });
  });

  it("records whether the confirmed address came from a saved address or a new entry", () => {
    const verification = {
      ...verifyUsCheckoutAddress({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "123 main street",
        city: "New York",
        state: "NY",
        postalCode: "10001",
        phone: "555-0100",
      }),
      source: "google-address-validation",
    };

    expect(buildAddressConfirmationRecord(verification, "original", { addressSource: "saved" })).toMatchObject({
      source: "google-address-validation",
      addressSource: "saved",
      choice: "original",
    });
  });

  it("keeps provider fallback status in the buyer confirmation audit record", () => {
    const verification = {
      ...verifyUsCheckoutAddress({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "123 main street",
        city: "New York",
        state: "NY",
        postalCode: "10001",
        phone: "555-0100",
      }),
      source: "local-postal-region",
      providerStatus: "fallback",
    };

    expect(buildAddressConfirmationRecord(verification, "original")).toMatchObject({
      source: "local-postal-region",
      providerStatus: "fallback",
      choice: "original",
    });
  });

  it("uses an external verifier result without coupling checkout to a provider", async () => {
    const verification = await verifyUsCheckoutAddressWithProvider(
      {
        firstName: "Ada",
        lastName: "Lovelace",
        street: "1600 amphitheatre parkway",
        city: "Mountain View",
        state: "CA",
        postalCode: "94043",
        phone: "555-0100",
      },
      {
        name: "google-address-validation",
        verifyAddress: async (_input, localVerification) => ({
          status: "suggested",
          reason: "external-standardized",
          requiresConfirmation: true,
          suggestedAddress: {
            ...localVerification.originalAddress,
            street: "1600 AMPHITHEATRE PKWY",
            city: "Mountain View",
            state: "CA",
            postalCode: "94043",
          },
        }),
      },
    );

    expect(verification).toMatchObject({
      source: "google-address-validation",
      status: "suggested",
      reason: "external-standardized",
      requiresConfirmation: true,
      originalAddress: {
        street: "1600 AMPHITHEATRE PKWY",
        city: "Mountain View",
      },
      suggestedAddress: {
        street: "1600 AMPHITHEATRE PKWY",
        city: "Mountain View",
      },
    });
  });

  it("falls back to local ZIP verification when the external verifier fails", async () => {
    const verification = await verifyUsCheckoutAddressWithProvider(
      {
        firstName: "Ada",
        lastName: "Lovelace",
        street: "123 main street",
        city: "New York",
        state: "NY",
        postalCode: "10001",
        phone: "555-0100",
      },
      {
        name: "unavailable-provider",
        verifyAddress: async () => {
          throw new Error("provider down");
        },
      },
    );

    expect(verification).toMatchObject({
      source: "local-postal-region",
      providerStatus: "fallback",
      status: "verified",
      suggestedAddress: {
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    });
  });
});
