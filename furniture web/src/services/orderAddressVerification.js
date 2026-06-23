const knownVerificationSources = [
  "google-address-validation",
  "local-postal-region",
  "remote-address-verification",
  "backend-address-verification",
];
const knownAddressSources = ["saved", "new"];
const knownStatuses = ["verified", "suggested", "unverified"];
const knownChoices = ["original", "suggested"];
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
const LOCAL_POSTAL_REGION_SOURCE = "local-postal-region";
const BACKEND_ADDRESS_VERIFICATION_SOURCE = "backend-address-verification";

const compactAddressLine = (address = {}) =>
  [
    address.street,
    address.apartment,
    [address.city, [address.state, address.postalCode].filter(Boolean).join(" ")].filter(Boolean).join(", "),
  ]
    .map((part) => String(part || "").trim())
    .filter(Boolean)
    .join(", ");

const scopedLabelKey = (scope, value, knownValues) => `${scope}.${knownValues.includes(value) ? value : "unknown"}`;
const sourceWarningKey = (source) => {
  if (source === LOCAL_POSTAL_REGION_SOURCE) return "orders.addressVerification.localPostalRegionWarning";
  if (source === BACKEND_ADDRESS_VERIFICATION_SOURCE) return "orders.addressVerification.localOnlyVerificationWarning";
  return "";
};

export const buildOrderAddressVerificationSummary = (audit) => {
  if (!audit) return null;

  const source = audit.source || "unknown";
  const addressSource = audit.addressSource || "unknown";
  const status = audit.status || "unknown";
  const reason = audit.reason || "unknown";
  const choice = audit.choice || "unknown";
  const providerStatus = audit.providerStatus || "";

  return {
    source,
    sourceLabelKey: scopedLabelKey("orders.addressVerification.verificationSources", source, knownVerificationSources),
    addressSource,
    addressSourceLabelKey: scopedLabelKey("orders.addressVerification.addressSources", addressSource, knownAddressSources),
    status,
    statusLabelKey: scopedLabelKey("orders.addressVerification.statuses", status, knownStatuses),
    reason,
    reasonLabelKey: scopedLabelKey("orders.addressVerification.reasons", reason, knownReasons),
    choice,
    choiceLabelKey: scopedLabelKey("orders.addressVerification.choices", choice, knownChoices),
    original: compactAddressLine(audit.originalAddress),
    suggested: compactAddressLine(audit.suggestedAddress),
    selected: compactAddressLine(audit.selectedAddress),
    confirmedAt: audit.confirmedAt || "",
    providerResponseId: audit.providerResponseId || "",
    providerStatus,
    providerStatusLabelKey: providerStatus
      ? scopedLabelKey("orders.addressVerification.providerStatuses", providerStatus, knownProviderStatuses)
      : "",
    warningKey: status === "unverified" ? "orders.addressVerification.warning" : "",
    sourceWarningKey: sourceWarningKey(source),
    providerWarningKey: providerStatus === "fallback" ? "orders.addressVerification.providerFallbackWarning" : "",
  };
};
