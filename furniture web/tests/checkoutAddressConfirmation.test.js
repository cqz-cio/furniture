import { describe, expect, it } from "vitest";
import { buildCheckoutAddressConfirmationSummary } from "../src/services/checkoutAddressConfirmation.js";

describe("checkout address confirmation summary", () => {
  it("summarizes the buyer-confirmed address before payment", () => {
    expect(
      buildCheckoutAddressConfirmationSummary({
        addressSource: "new",
        status: "unverified",
        reason: "unknown-postal-code",
        choice: "original",
        selectedAddress: {
          street: "900 Market Street",
          apartment: "Apt 12",
          city: "San Francisco",
          state: "CA",
          postalCode: "94103",
        },
      }),
    ).toEqual({
      source: "",
      addressSource: "new",
      addressSourceLabelKey: "checkout.addressConfirmation.addressSources.new",
      status: "unverified",
      statusLabelKey: "checkout.addressConfirmation.statuses.unverified",
      reason: "unknown-postal-code",
      reasonLabelKey: "checkout.addressConfirmation.reasons.unknown-postal-code",
      choice: "original",
      choiceLabelKey: "checkout.addressConfirmation.choices.original",
      providerStatus: "",
      providerStatusLabelKey: "",
      providerWarningKey: "",
      selected: "900 Market Street, Apt 12, San Francisco, CA 94103",
      sourceWarningKey: "",
      warningKey: "checkout.addressConfirmation.warning",
    });
  });

  it("returns null before the buyer confirms an address", () => {
    expect(buildCheckoutAddressConfirmationSummary(null)).toBeNull();
  });

  it("surfaces provider fallback status before payment submission", () => {
    expect(
      buildCheckoutAddressConfirmationSummary({
        addressSource: "new",
        status: "verified",
        choice: "original",
        providerStatus: "fallback",
        selectedAddress: {
          street: "123 Main Street",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      }),
    ).toMatchObject({
      providerStatus: "fallback",
      providerStatusLabelKey: "checkout.addressConfirmation.providerStatuses.fallback",
      providerWarningKey: "checkout.addressConfirmation.providerFallbackWarning",
      warningKey: "",
    });
  });

  it("warns when the address was only checked against local ZIP region data", () => {
    expect(
      buildCheckoutAddressConfirmationSummary({
        source: "local-postal-region",
        addressSource: "new",
        status: "verified",
        choice: "original",
        selectedAddress: {
          street: "123 Main Street",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      }),
    ).toMatchObject({
      source: "local-postal-region",
      sourceWarningKey: "checkout.addressConfirmation.localPostalRegionWarning",
      warningKey: "",
      providerWarningKey: "",
    });
  });

  it("warns when the address was only standardized by the backend fallback verifier", () => {
    expect(
      buildCheckoutAddressConfirmationSummary({
        source: "backend-address-verification",
        addressSource: "new",
        status: "suggested",
        choice: "suggested",
        selectedAddress: {
          street: "123 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      }),
    ).toMatchObject({
      source: "backend-address-verification",
      sourceWarningKey: "checkout.addressConfirmation.localOnlyVerificationWarning",
    });
  });

  it("labels remote verifier reasons instead of collapsing them to unknown", () => {
    expect(
      buildCheckoutAddressConfirmationSummary({
        addressSource: "saved",
        status: "suggested",
        reason: "google-review-required",
        choice: "suggested",
        selectedAddress: {
          street: "1600 AMPHITHEATRE PKWY",
          city: "Mountain View",
          state: "CA",
          postalCode: "94043-1351",
        },
      }),
    ).toMatchObject({
      reason: "google-review-required",
      reasonLabelKey: "checkout.addressConfirmation.reasons.google-review-required",
    });
  });
});
