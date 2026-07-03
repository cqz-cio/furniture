import { canUseYudaoCheckout, getCheckoutMode } from "./checkoutSession.js";

export const REAL_ACCOUNT_MODULE_STATUS = {
  ready: "ready",
  partial: "partial",
  blocked: "blocked",
};

export const REAL_ACCOUNT_MODULES = [
  "productCatalog",
  "cart",
  "checkout",
  "orders",
  "billing",
  "accountProfile",
  "addressBook",
  "wishlist",
  "membership",
  "giftRegistry",
  "tradeProgram",
];

export const hasYudaoToken = (token) => Boolean(String(token || "").trim());

export const getYudaoProductIdentity = (product = {}) => ({
  spuId: product.spuId || product.id || product.raw?.id || "",
  skuId: product.skuId || product.raw?.skuId || product.skus?.[0]?.id || product.raw?.skus?.[0]?.id || "",
});

export const isRealYudaoProduct = (product = {}) => {
  const identity = getYudaoProductIdentity(product);
  return product.source === "yudao" && Boolean(identity.spuId) && Boolean(identity.skuId);
};

export const getCartItemReadiness = (item = {}) => {
  const identity = getYudaoProductIdentity(item);
  const issues = [];

  if (item.source !== "yudao") issues.push("source-not-yudao");
  if (!identity.spuId) issues.push("missing-spu-id");
  if (!identity.skuId) issues.push("missing-sku-id");
  if (!item.cartId) issues.push("missing-cart-id");

  return {
    skuId: identity.skuId || item.skuId || "",
    cartId: item.cartId || "",
    source: item.source || "",
    ready: issues.length === 0,
    issues,
  };
};

export const getCartReadiness = (items = [], token = "") => {
  const itemChecks = items.map(getCartItemReadiness);
  const checkoutMode = getCheckoutMode(items, token);
  const blockingReasons = [];

  if (!items.length) blockingReasons.push("cart-empty");
  if (!hasYudaoToken(token)) blockingReasons.push("missing-token");
  itemChecks.forEach((item, index) => {
    item.issues.forEach((issue) => blockingReasons.push(`item-${index + 1}:${issue}`));
  });
  if (!canUseYudaoCheckout(items)) blockingReasons.push("checkout-mode-not-yudao");

  return {
    checkoutMode,
    ready: checkoutMode === "yudao" && blockingReasons.length === 0,
    canCreateLiveOrder: checkoutMode === "yudao" && canUseYudaoCheckout(items),
    itemChecks,
    blockingReasons,
  };
};

export const buildRealAccountModuleSnapshot = ({
  token = "",
  productCount = 0,
  cartItems = [],
  hasOrderReadApi = false,
  hasBillingReadApi = false,
  hasAccountProfileApi = false,
  hasAddressBookApi = false,
  hasWishlistApi = false,
  hasMembershipApi = false,
  hasGiftRegistryApi = false,
  hasTradeApi = false,
} = {}) => {
  const signedIn = hasYudaoToken(token);
  const cartReadiness = getCartReadiness(cartItems, token);
  const productReady = Number(productCount) > 0;

  const status = (ready, partial = false) => {
    if (ready) return REAL_ACCOUNT_MODULE_STATUS.ready;
    return partial ? REAL_ACCOUNT_MODULE_STATUS.partial : REAL_ACCOUNT_MODULE_STATUS.blocked;
  };

  return {
    productCatalog: status(productReady),
    cart: status(cartReadiness.itemChecks.length > 0 && cartReadiness.itemChecks.every((item) => item.ready), cartItems.length > 0),
    checkout: status(cartReadiness.ready, cartReadiness.checkoutMode !== "empty"),
    orders: status(signedIn && hasOrderReadApi, hasOrderReadApi),
    billing: status(signedIn && hasBillingReadApi, hasBillingReadApi),
    accountProfile: status(signedIn && hasAccountProfileApi, hasAccountProfileApi),
    addressBook: status(signedIn && hasAddressBookApi, hasAddressBookApi),
    wishlist: status(signedIn && hasWishlistApi, hasWishlistApi),
    membership: status(signedIn && hasMembershipApi, hasMembershipApi),
    giftRegistry: status(signedIn && hasGiftRegistryApi, hasGiftRegistryApi),
    tradeProgram: status(signedIn && hasTradeApi, hasTradeApi),
  };
};
