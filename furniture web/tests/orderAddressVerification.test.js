import { describe, expect, it } from "vitest";
import { buildOrderAddressVerificationSummary } from "../src/services/orderAddressVerification.js";

describe("order address verification summary", () => {
  it("keeps original, suggested, selected, and confirmation metadata for order detail review", () => {
    expect(
      buildOrderAddressVerificationSummary({
        source: "remote-address-verification",
        addressSource: "saved",
        status: "suggested",
        reason: "postal-region-mismatch",
        choice: "suggested",
        confirmedAt: "2026-06-16T10:00:00.000Z",
        providerResponseId: "provider-123",
        providerStatus: "fallback",
        originalAddress: {
          street: "12 Main Street",
          apartment: "Suite 4",
          city: "Brooklyn",
          state: "CA",
          postalCode: "10001",
        },
        suggestedAddress: {
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
        selectedAddress: {
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      }),
    ).toEqual({
      source: "remote-address-verification",
      sourceLabelKey: "orders.addressVerification.verificationSources.remote-address-verification",
      addressSource: "saved",
      addressSourceLabelKey: "orders.addressVerification.addressSources.saved",
      status: "suggested",
      statusLabelKey: "orders.addressVerification.statuses.suggested",
      reason: "postal-region-mismatch",
      reasonLabelKey: "orders.addressVerification.reasons.postal-region-mismatch",
      choice: "suggested",
      choiceLabelKey: "orders.addressVerification.choices.suggested",
      original: "12 Main Street, Suite 4, Brooklyn, CA 10001",
      suggested: "12 MAIN ST, New York, NY 10001",
      selected: "12 MAIN ST, New York, NY 10001",
      confirmedAt: "2026-06-16T10:00:00.000Z",
      providerResponseId: "provider-123",
      providerStatus: "fallback",
      providerStatusLabelKey: "orders.addressVerification.providerStatuses.fallback",
      warningKey: "",
      sourceWarningKey: "",
      providerWarningKey: "orders.addressVerification.providerFallbackWarning",
    });
  });

  it("returns null when an order has no address verification audit", () => {
    expect(buildOrderAddressVerificationSummary(null)).toBeNull();
  });

  it("labels backend and remote verifier reasons for order audit review", () => {
    expect(
      buildOrderAddressVerificationSummary({
        source: "remote-address-verification",
        addressSource: "new",
        status: "suggested",
        reason: "backend-standardized",
        choice: "original",
      }),
    ).toMatchObject({
      reason: "backend-standardized",
      reasonLabelKey: "orders.addressVerification.reasons.backend-standardized",
    });
  });

  it("flags unverified and fallback audits with order detail warnings", () => {
    expect(
      buildOrderAddressVerificationSummary({
        source: "remote-address-verification",
        addressSource: "new",
        status: "unverified",
        reason: "google-unverified",
        choice: "original",
        providerStatus: "fallback",
      }),
    ).toMatchObject({
      warningKey: "orders.addressVerification.warning",
      providerWarningKey: "orders.addressVerification.providerFallbackWarning",
    });
  });

  it("warns when the order address audit came from local ZIP region data only", () => {
    expect(
      buildOrderAddressVerificationSummary({
        source: "local-postal-region",
        addressSource: "new",
        status: "verified",
        choice: "original",
        selectedAddress: {
          street: "123 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      }),
    ).toMatchObject({
      source: "local-postal-region",
      sourceWarningKey: "orders.addressVerification.localPostalRegionWarning",
      warningKey: "",
      providerWarningKey: "",
    });
  });

  it("labels and warns when the order address audit came from backend fallback verification only", () => {
    expect(
      buildOrderAddressVerificationSummary({
        source: "backend-address-verification",
        addressSource: "new",
        status: "suggested",
        reason: "backend-standardized",
        choice: "suggested",
      }),
    ).toMatchObject({
      source: "backend-address-verification",
      sourceLabelKey: "orders.addressVerification.verificationSources.backend-address-verification",
      sourceWarningKey: "orders.addressVerification.localOnlyVerificationWarning",
    });
  });
});
