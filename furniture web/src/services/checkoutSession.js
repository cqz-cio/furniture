import { getCartTotals } from "./localCart.js";
import { YUDAO_US_DEFAULT_AREA_ID } from "./usAddress.js";

export const DEFAULT_DELIVERY_TYPE = 1;

const supportedAddressVerificationSources = new Set([
  "google-address-validation",
  "remote-address-verification",
  "local-postal-region",
  "backend-address-verification",
]);
const supportedAddressSources = new Set(["new", "saved"]);
const supportedAddressVerificationStatuses = new Set(["verified", "suggested", "unverified"]);
const supportedAddressVerificationChoices = new Set(["original", "suggested"]);

export const hasYudaoProductIdentity = (item = {}) => Boolean(item.spuId || item.id);

export const canUseYudaoCheckout = (items) =>
  items.length > 0 &&
  items.every((item) => item.source === "yudao" && item.cartId && item.skuId && hasYudaoProductIdentity(item));

const compactAddressLine = (address = {}) =>
  [address.street, [address.city, [address.state, address.postalCode].filter(Boolean).join(" ")].filter(Boolean).join(", ")]
    .map((part) => String(part || "").trim())
    .filter(Boolean)
    .join(", ");

export const buildAddressConfirmationRemark = (confirmation) => {
  if (!confirmation) return "";

  const parts = [
    `status=${confirmation.status || "unknown"}`,
    `choice=${confirmation.choice || "original"}`,
    confirmation.addressSource ? `addressSource=${confirmation.addressSource}` : "",
    confirmation.reason ? `reason=${confirmation.reason}` : "",
    compactAddressLine(confirmation.selectedAddress) ? `selected=${compactAddressLine(confirmation.selectedAddress)}` : "",
  ].filter(Boolean);

  return parts.length ? `Address confirmation: ${parts.join("; ")}` : "";
};

const normalizeAuditAddress = (address) => {
  if (!address) return null;

  return Object.fromEntries(Object.entries({
    street: String(address.street || "").trim(),
    apartment: String(address.apartment || "").trim(),
    city: String(address.city || "").trim(),
    state: String(address.state || "").trim(),
    postalCode: String(address.postalCode || "").trim(),
  }).filter(([, value]) => value));
};

const hasAuditText = (value) => String(value || "").trim().length > 0;
const hasSupportedAuditValue = (value, supportedValues) => supportedValues.has(String(value || "").trim().toLowerCase());

const hasConfirmedAuditAddress = (address) =>
  ["street", "city", "state", "postalCode"].every((key) => hasAuditText(address?.[key]));

export const buildAddressVerificationAudit = (confirmation) => {
  if (!confirmation) return null;

  const selectedAddress = normalizeAuditAddress(confirmation.selectedAddress);
  if (
    !hasAuditText(confirmation.source)
    || !hasAuditText(confirmation.addressSource)
    || !hasAuditText(confirmation.status)
    || !hasAuditText(confirmation.choice)
    || !hasAuditText(confirmation.confirmedAt)
    || !hasSupportedAuditValue(confirmation.source, supportedAddressVerificationSources)
    || !hasSupportedAuditValue(confirmation.addressSource, supportedAddressSources)
    || !hasSupportedAuditValue(confirmation.status, supportedAddressVerificationStatuses)
    || !hasSupportedAuditValue(confirmation.choice, supportedAddressVerificationChoices)
    || !hasConfirmedAuditAddress(selectedAddress)
  ) {
    return null;
  }

  return {
    source: confirmation.source || "unknown",
    addressSource: confirmation.addressSource || "unknown",
    status: confirmation.status || "unknown",
    reason: confirmation.reason || "",
    choice: confirmation.choice || "original",
    deliverable: confirmation.deliverable === true,
    confirmedAt: confirmation.confirmedAt || "",
    providerResponseId: confirmation.providerResponseId || confirmation.metadata?.responseId || "",
    providerStatus: confirmation.providerStatus || "",
    originalAddress: normalizeAuditAddress(confirmation.originalAddress),
    suggestedAddress: normalizeAuditAddress(confirmation.suggestedAddress),
    selectedAddress,
  };
};

export const buildYudaoOrderPayload = (items, options = {}) => {
  if (!canUseYudaoCheckout(items)) {
    throw new Error("Live Yudao orders require remote cart items with cartId, skuId and product identity.");
  }

  const addressVerification = buildAddressVerificationAudit(options.addressConfirmation);

  return {
    items: items.map((item) => ({
      skuId: item.skuId,
      count: item.quantity,
      cartId: item.cartId,
    })),
    pointStatus: false,
    deliveryType: options.deliveryType || DEFAULT_DELIVERY_TYPE,
    addressId: options.addressId,
    ...(addressVerification ? { addressVerification } : {}),
    remark: [options.remark, buildAddressConfirmationRemark(options.addressConfirmation)].filter(Boolean).join("\n"),
  };
};

export const buildYudaoAddressPayload = (form = {}, options = {}) => {
  const name = [form.firstName, form.lastName].map((part) => String(part || "").trim()).filter(Boolean).join(" ");
  const detailAddress = [form.street, form.apartment, form.city, [form.state, form.postalCode].filter(Boolean).join(" ")]
    .map((part) => String(part || "").trim())
    .filter(Boolean)
    .join(", ");
  const addressVerification = buildAddressVerificationAudit(options.addressConfirmation);

  return {
    name,
    mobile: String(form.phone || "").trim(),
    areaId: Number(form.areaId || YUDAO_US_DEFAULT_AREA_ID),
    detailAddress,
    defaultStatus: true,
    ...(addressVerification ? { addressVerification } : {}),
  };
};

export const buildConfirmedShippingAddressInput = (shippingForm = {}, selectedAddress = null) => ({
  ...shippingForm,
  ...(selectedAddress || {}),
  firstName: shippingForm.firstName,
  lastName: shippingForm.lastName,
  phone: shippingForm.phone,
  areaId: selectedAddress?.areaId || shippingForm.areaId,
});

export const buildLocalCheckoutSummary = (items) => ({
  ...getCartTotals(items),
  items,
});

export const getCheckoutMode = (items, token) => {
  if (items.length === 0) return "empty";
  if (!canUseYudaoCheckout(items)) return "local-preview";
  return token ? "yudao" : "token-required";
};

export const getCheckoutPresentation = (mode) => {
  const states = {
    yudao: {
      title: "Review Your Order",
      message: "Confirm your delivery address and create the connected catalog order.",
      cta: "Create Connected Order",
      canSubmit: true,
    },
    "token-required": {
      title: "Connect Your Account",
      message: "Connect an account token before creating a live order.",
      cta: "Add Token To Continue",
      canSubmit: false,
    },
    "local-preview": {
      title: "Review Your Selections",
      message: "This bag contains preview-only items. Demo, local, or membership preview items are not persisted to Yudao and cannot create a live Yudao order.",
      cta: "Review Only",
      canSubmit: false,
    },
    empty: {
      title: "Your Bag Is Empty",
      message: "Browse the gallery and add pieces before beginning checkout.",
      cta: "Return To Gallery",
      canSubmit: false,
    },
  };

  return states[mode] || states.empty;
};

export const getCheckoutReturnPath = () => "/checkout";

export const getSelectedAddressId = (selectedAddressId, defaultAddress) => selectedAddressId || defaultAddress?.id;

const splitSavedAddressName = (name = "") => {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
  return {
    firstName: parts.slice(0, -1).join(" ") || parts[0] || "",
    lastName: parts.length > 1 ? parts[parts.length - 1] : "",
  };
};

export const savedAddressToShippingForm = (address = {}) => {
  const raw = address.raw || {};
  const detailAddress = String(address.detailAddress || raw.detailAddress || "").trim();
  const detailParts = detailAddress.split(",").map((part) => part.trim()).filter(Boolean);
  const areaParts = String(address.areaName || raw.areaName || "").split(",").map((part) => part.trim()).filter(Boolean);
  const statePostal = detailParts[detailParts.length - 1] || "";
  const name = splitSavedAddressName(address.name || raw.name);
  const postalCode = String(raw.postalCode || statePostal.match(/\b\d{5}(?:-\d{4})?\b/)?.[0] || "").trim();
  const state = String(raw.region || raw.state || statePostal.match(/\b[A-Z]{2}\b/)?.[0] || areaParts[1] || "").trim();
  const street = String(raw.street || raw.address1 || detailParts[0] || detailAddress).trim();
  const apartment = String(raw.apartment || raw.address2 || (detailParts.length > 3 ? detailParts.slice(1, -2).join(", ") : "")).trim();

  return {
    firstName: name.firstName,
    lastName: name.lastName,
    country: "United States",
    street,
    apartment,
    city: String(raw.city || areaParts[0] || (detailParts.length > 2 ? detailParts[detailParts.length - 2] : "") || "").trim(),
    state,
    postalCode,
    phone: address.mobile || raw.mobile || "",
    areaId: raw.areaId || "",
  };
};

export const getOrderDetailPath = (id, payOrderId = "") => {
  const search = new URLSearchParams();
  search.set("id", String(id));
  if (payOrderId) search.set("payOrderId", String(payOrderId));
  return `/orders?${search.toString()}`;
};
