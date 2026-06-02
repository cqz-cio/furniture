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

export const getCheckoutReturnPath = () => "/checkout";

export const getSelectedAddressId = (selectedAddressId, defaultAddress) => selectedAddressId || defaultAddress?.id;

export const getOrderDetailPath = (id) => `/orders?id=${encodeURIComponent(id)}`;
