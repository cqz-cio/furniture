const knownAddressSources = ["saved", "new"];
const knownStatuses = ["verified", "suggested", "unverified"];
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
const knownChoices = ["original", "suggested"];
const knownProviderStatuses = ["fallback"];
const LOCAL_POSTAL_REGION_SOURCE = "local-postal-region";
const BACKEND_ADDRESS_VERIFICATION_SOURCE = "backend-address-verification";

const sourceWarningKey = (source) => {
  if (source === LOCAL_POSTAL_REGION_SOURCE) return "checkout.addressConfirmation.localPostalRegionWarning";
  if (source === BACKEND_ADDRESS_VERIFICATION_SOURCE) return "checkout.addressConfirmation.localOnlyVerificationWarning";
  return "";
};

const scopedLabelKey = (scope, value, knownValues) => `${scope}.${knownValues.includes(value) ? value : "unknown"}`;

const compactAddressLine = (address = {}) =>
  [
    address.street,
    address.apartment,
    [address.city, [address.state, address.postalCode].filter(Boolean).join(" ")].filter(Boolean).join(", "),
  ]
    .map((part) => String(part || "").trim())
    .filter(Boolean)
    .join(", ");

export const buildCheckoutAddressConfirmationSummary = (confirmation) => {
  if (!confirmation) return null;

  const addressSource = confirmation.addressSource || "unknown";
  const status = confirmation.status || "unknown";
  const reason = confirmation.reason || "unknown";
  const choice = confirmation.choice || "unknown";
  const providerStatus = confirmation.providerStatus || "";
  const source = confirmation.source || "";

  return {
    source,
    addressSource,
    addressSourceLabelKey: scopedLabelKey("checkout.addressConfirmation.addressSources", addressSource, knownAddressSources),
    status,
    statusLabelKey: scopedLabelKey("checkout.addressConfirmation.statuses", status, knownStatuses),
    reason,
    reasonLabelKey: scopedLabelKey("checkout.addressConfirmation.reasons", reason, knownReasons),
    choice,
    choiceLabelKey: scopedLabelKey("checkout.addressConfirmation.choices", choice, knownChoices),
    providerStatus,
    providerStatusLabelKey: providerStatus
      ? scopedLabelKey("checkout.addressConfirmation.providerStatuses", providerStatus, knownProviderStatuses)
      : "",
    selected: compactAddressLine(confirmation.selectedAddress),
    sourceWarningKey: sourceWarningKey(source),
    providerWarningKey: providerStatus === "fallback" ? "checkout.addressConfirmation.providerFallbackWarning" : "",
    warningKey: status === "unverified" ? "checkout.addressConfirmation.warning" : "",
  };
};
