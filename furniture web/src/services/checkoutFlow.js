export const CHECKOUT_STEP_KEYS = [
  "details",
  "custom-check",
  "shipping-address",
  "address-verification",
  "payment",
  "review",
  "place-order",
  "delivery-notes",
];

export const hasCustomItems = (items = []) =>
  items.some((item) => Boolean(item.isCustom || item.customization || item.customOrder));

const hasText = (value) => Boolean(String(value || "").trim());

export const getAddressVerification = (address = {}) => {
  if (!hasText(address.line1) || !hasText(address.postalCode)) {
    return {
      status: "missing",
      issue: "Shipping address is required before payment.",
      suggestedAddress: null,
    };
  }

  if (!String(address.postalCode).includes("-")) {
    return {
      status: "issue",
      issue: "Address needs verification before payment.",
      suggestedAddress: {
        ...address,
        postalCode: `${address.postalCode}-0000`,
      },
    };
  }

  return {
    status: "verified",
    issue: "",
    suggestedAddress: null,
  };
};

export const getCustomOrderNotice = (items = []) => {
  const required = hasCustomItems(items);

  return {
    required,
    title: required ? "Custom Order Notice" : "Custom Item Check",
    message: required
      ? "Custom merchandise requires review of final sale, production and delivery timing before payment."
      : "No custom merchandise requires additional acknowledgement.",
  };
};

const getStepStatus = ({
  key,
  customNotice,
  addressVerification,
  paymentMethod,
  cardComplete,
  termsAccepted,
  readyForPayment,
}) => {
  if (key === "custom-check") return customNotice.required ? "attention" : "complete";
  if (key === "shipping-address") return addressVerification.status === "missing" ? "open" : "complete";
  if (key === "address-verification") return addressVerification.status === "verified" ? "complete" : "attention";
  if (key === "payment") return readyForPayment && paymentMethod && cardComplete ? "complete" : "open";
  if (key === "review") return readyForPayment && termsAccepted ? "complete" : "open";
  if (key === "place-order") return readyForPayment && paymentMethod && cardComplete && termsAccepted ? "complete" : "open";
  return "complete";
};

export const buildCheckoutFlow = (items = [], options = {}) => {
  const rawVerification = getAddressVerification(options.address);
  const addressVerification =
    rawVerification.status === "issue" && options.useSuggestedAddress
      ? { ...rawVerification, status: "verified", issue: "" }
      : rawVerification;
  const customNotice = getCustomOrderNotice(items);
  const readyForPayment = addressVerification.status === "verified" && (!customNotice.required || options.customNoticeAccepted);
  const stepContext = {
    customNotice,
    addressVerification,
    readyForPayment,
    paymentMethod: options.paymentMethod || "",
    cardComplete: Boolean(options.cardComplete),
    termsAccepted: Boolean(options.termsAccepted),
  };

  return {
    steps: CHECKOUT_STEP_KEYS.map((key) => ({ key, status: getStepStatus({ key, ...stepContext }) })),
    customNotice,
    addressVerification,
    readyForPayment,
    payment: {
      method: options.paymentMethod || "card",
      cardComplete: Boolean(options.cardComplete),
    },
    agreements: {
      termsAccepted: Boolean(options.termsAccepted),
      autoRenewalAccepted: Boolean(options.autoRenewalAccepted),
    },
  };
};

export const canPlaceCheckoutOrder = (flow = {}) =>
  Boolean(flow.readyForPayment && flow.payment?.cardComplete && flow.agreements?.termsAccepted);
