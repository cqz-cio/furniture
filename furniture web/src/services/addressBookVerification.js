const knownStatuses = ["verified", "suggested", "unverified", "missing"];
const knownChoices = ["original", "suggested"];
const LOCAL_POSTAL_REGION_SOURCE = "local-postal-region";
const BACKEND_ADDRESS_VERIFICATION_SOURCE = "backend-address-verification";
const knownReasons = [
  "postal-region-mismatch",
  "missing-required-fields",
  "unknown-postal-code",
  "google-address-complete",
  "google-review-required",
  "google-unverified",
  "backend-standardized",
  "remote-standardized",
  "cass-standardized",
];
const knownProviderStatuses = ["fallback"];

const scopedLabelKey = (scope, value, knownValues) => `${scope}.${knownValues.includes(value) ? value : "unknown"}`;
const sourceWarningKey = (source) => {
  if (source === LOCAL_POSTAL_REGION_SOURCE) {
    return "membership.account.addressBook.verification.localPostalRegionWarning";
  }
  if (source === BACKEND_ADDRESS_VERIFICATION_SOURCE) {
    return "membership.account.addressBook.verification.localOnlyVerificationWarning";
  }
  return "";
};

export const buildAddressBookVerificationSummary = (audit) => {
  const status = audit?.status || "missing";
  const choice = audit?.choice || "unknown";
  const reason = audit?.reason || "unknown";
  const providerStatus = audit?.providerStatus || "";
  const source = audit?.source || "";

  return {
    source,
    status,
    statusLabelKey: scopedLabelKey("membership.account.addressBook.verification.statuses", status, knownStatuses),
    choice,
    choiceLabelKey: scopedLabelKey("membership.account.addressBook.verification.choices", choice, knownChoices),
    reason,
    reasonLabelKey: scopedLabelKey("membership.account.addressBook.verification.reasons", reason, knownReasons),
    confirmedAt: audit?.confirmedAt || "",
    providerStatus,
    providerStatusLabelKey: providerStatus
      ? scopedLabelKey("membership.account.addressBook.verification.providerStatuses", providerStatus, knownProviderStatuses)
      : "",
    warningKey:
      status === "missing"
        ? "membership.account.addressBook.verification.missingWarning"
        : status === "unverified"
          ? "membership.account.addressBook.verification.warning"
          : "",
    sourceWarningKey: sourceWarningKey(source),
    providerWarningKey:
      providerStatus === "fallback" ? "membership.account.addressBook.verification.providerFallbackWarning" : "",
  };
};
