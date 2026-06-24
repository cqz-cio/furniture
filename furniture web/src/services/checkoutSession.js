import { getCartTotals } from "./localCart.js";

export const DEFAULT_DELIVERY_TYPE = 1;

export const canUseYudaoCheckout = (items) =>
  items.length > 0 && items.every((item) => item.source === "yudao" && item.cartId && item.skuId);

export const buildYudaoOrderPayload = (items, options = {}) => ({
  items: items.map((item) => ({
    skuId: item.skuId,
    count: item.quantity,
    cartId: item.cartId,
  })),
  pointStatus: false,
  deliveryType: options.deliveryType || DEFAULT_DELIVERY_TYPE,
  addressId: options.addressId,
  remark: options.remark || "",
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
      message: "Review your Oakved selections before final account checkout is connected.",
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

export const getOrderDetailPath = (id) => `/orders?id=${encodeURIComponent(id)}`;
