import { describe, expect, it } from "vitest";
import { buildAddressBookVerificationSummary } from "../src/services/addressBookVerification.js";

describe("address book verification summary", () => {
  it("summarizes a saved address verification audit without claiming deliverability", () => {
    expect(
      buildAddressBookVerificationSummary({
        source: "remote-address-verification",
        status: "suggested",
        choice: "suggested",
        reason: "remote-standardized",
        confirmedAt: "2026-06-16T10:00:00.000Z",
        providerStatus: "fallback",
      }),
    ).toEqual({
      source: "remote-address-verification",
      status: "suggested",
      statusLabelKey: "membership.account.addressBook.verification.statuses.suggested",
      choice: "suggested",
      choiceLabelKey: "membership.account.addressBook.verification.choices.suggested",
      reason: "remote-standardized",
      reasonLabelKey: "membership.account.addressBook.verification.reasons.remote-standardized",
      confirmedAt: "2026-06-16T10:00:00.000Z",
      providerStatus: "fallback",
      providerStatusLabelKey: "membership.account.addressBook.verification.providerStatuses.fallback",
      warningKey: "",
      sourceWarningKey: "",
      providerWarningKey: "membership.account.addressBook.verification.providerFallbackWarning",
    });
  });

  it("flags previously confirmed unverified addresses for review", () => {
    expect(
      buildAddressBookVerificationSummary({
        status: "unverified",
        choice: "original",
        reason: "google-unverified",
      }),
    ).toMatchObject({
      statusLabelKey: "membership.account.addressBook.verification.statuses.unverified",
      warningKey: "membership.account.addressBook.verification.warning",
    });
  });

  it("warns when a saved address was only checked against local ZIP region data", () => {
    expect(
      buildAddressBookVerificationSummary({
        source: "local-postal-region",
        status: "verified",
        choice: "original",
        reason: "",
      }),
    ).toMatchObject({
      source: "local-postal-region",
      sourceWarningKey: "membership.account.addressBook.verification.localPostalRegionWarning",
      warningKey: "",
      providerWarningKey: "",
    });
  });

  it("warns when a saved address was only standardized by backend fallback verification", () => {
    expect(
      buildAddressBookVerificationSummary({
        source: "backend-address-verification",
        status: "suggested",
        choice: "suggested",
        reason: "backend-standardized",
      }),
    ).toMatchObject({
      source: "backend-address-verification",
      sourceWarningKey: "membership.account.addressBook.verification.localOnlyVerificationWarning",
    });
  });

  it("marks saved addresses without verification metadata as not reviewed", () => {
    expect(buildAddressBookVerificationSummary(null)).toEqual({
      source: "",
      status: "missing",
      statusLabelKey: "membership.account.addressBook.verification.statuses.missing",
      choice: "unknown",
      choiceLabelKey: "membership.account.addressBook.verification.choices.unknown",
      reason: "unknown",
      reasonLabelKey: "membership.account.addressBook.verification.reasons.unknown",
      confirmedAt: "",
      providerStatus: "",
      providerStatusLabelKey: "",
      warningKey: "membership.account.addressBook.verification.missingWarning",
      sourceWarningKey: "",
      providerWarningKey: "",
    });
  });
});
