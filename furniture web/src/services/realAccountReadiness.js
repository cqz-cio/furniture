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

const normalizeComparable = (value) => String(value ?? "").trim();

const hasOwn = (object, key) => Object.prototype.hasOwnProperty.call(object || {}, key);

export const getAccountProfileReadiness = ({
  token = "",
  hasAccountProfileApi = false,
  profile,
  expectedUserId = "",
} = {}) => {
  const signedIn = hasYudaoToken(token);
  const hasProfile = Boolean(profile && typeof profile === "object");
  const userId = normalizeComparable(profile?.userId ?? profile?.id);
  const expectedUser = normalizeComparable(expectedUserId);
  const hasContact = Boolean(normalizeComparable(profile?.email) || normalizeComparable(profile?.mobile));
  const issues = [];

  if (!signedIn) issues.push("missing-token");
  if (!hasAccountProfileApi && !hasProfile) issues.push("missing-profile-api");

  if (hasProfile) {
    if (!userId) issues.push("missing-user-id");
    if (expectedUser && userId && userId !== expectedUser) issues.push("user-id-mismatch");
    if (!hasContact) issues.push("missing-contact");
  } else if (expectedUser) {
    issues.push("missing-profile");
  }

  const hasBlockingMismatch = issues.some((issue) => issue.endsWith("-mismatch"));
  return {
    ready: issues.length === 0,
    partial: !hasBlockingMismatch && (hasAccountProfileApi || hasProfile),
    issues,
    userId,
  };
};

export const getBillingReadiness = ({ token = "", hasBillingReadApi = false, billingRecords } = {}) => {
  const signedIn = hasYudaoToken(token);
  const records = Array.isArray(billingRecords) ? billingRecords : null;
  const issues = [];

  if (!signedIn) issues.push("missing-token");
  if (!hasBillingReadApi) issues.push("missing-billing-api");

  if (records) {
    if (!records.length) issues.push("billing-records-empty");
    const hasPaymentAmount = records.some((record) => hasOwn(record, "payPrice") || hasOwn(record, "totalPrice"));
    const hasPaymentStatus = records.some((record) => hasOwn(record, "payStatus") || hasOwn(record, "status"));
    if (!hasPaymentAmount) issues.push("missing-payment-amount");
    if (!hasPaymentStatus) issues.push("missing-payment-status");
  }

  return {
    ready: issues.length === 0,
    partial: hasBillingReadApi || Boolean(records?.length),
    issues,
  };
};

const getAddressId = (address = {}) => normalizeComparable(address.addressId ?? address.id);

const hasAddressDetail = (address = {}) =>
  Boolean(normalizeComparable(address.detailAddress) || normalizeComparable(address.address) || normalizeComparable(address.areaName));

export const getAddressBookReadiness = ({
  token = "",
  hasAddressBookApi = false,
  addresses,
  expectedAddressId = "",
} = {}) => {
  const signedIn = hasYudaoToken(token);
  const rows = Array.isArray(addresses) ? addresses : null;
  const expectedAddress = normalizeComparable(expectedAddressId);
  const seededRow = expectedAddress && rows ? rows.find((address) => getAddressId(address) === expectedAddress) : null;
  const address = seededRow || rows?.[0] || null;
  const issues = [];

  if (!signedIn) issues.push("missing-token");
  if (!hasAddressBookApi && !rows) issues.push("missing-address-api");
  if (rows && !rows.length) issues.push("address-book-empty");
  if (expectedAddress && rows && !seededRow) issues.push("seeded-address-missing");

  if (address) {
    if (!getAddressId(address)) issues.push("missing-address-id");
    if (!normalizeComparable(address.name)) issues.push("missing-address-recipient");
    if (!normalizeComparable(address.mobile)) issues.push("missing-address-mobile");
    if (!hasAddressDetail(address)) issues.push("missing-address-detail");
  }

  return {
    ready: issues.length === 0,
    partial: hasAddressBookApi || Boolean(rows?.length),
    issues,
  };
};

const wishlistRowMatches = (row = {}, expectedSpuId = "", expectedSkuId = "") =>
  (!expectedSpuId || normalizeComparable(row.spuId || row.id) === expectedSpuId) &&
  (!expectedSkuId || normalizeComparable(row.skuId) === expectedSkuId);

export const getWishlistReadiness = ({
  token = "",
  hasWishlistApi = false,
  wishlistRecords,
  expectedSpuId = "",
  expectedSkuId = "",
} = {}) => {
  const signedIn = hasYudaoToken(token);
  const rows = Array.isArray(wishlistRecords) ? wishlistRecords : null;
  const expectedSpu = normalizeComparable(expectedSpuId);
  const expectedSku = normalizeComparable(expectedSkuId);
  const issues = [];

  if (!signedIn) issues.push("missing-token");
  if (!hasWishlistApi && !rows) issues.push("missing-wishlist-api");
  if (rows && !rows.length) issues.push("wishlist-empty");
  if ((expectedSpu || expectedSku) && rows && !rows.some((row) => wishlistRowMatches(row, expectedSpu, expectedSku))) {
    issues.push("seeded-wishlist-row-missing");
  }

  const hasSeedMismatch = issues.includes("seeded-wishlist-row-missing");
  return {
    ready: issues.length === 0,
    partial: !hasSeedMismatch && (hasWishlistApi || Boolean(rows?.length)),
    issues,
  };
};

export const getTradeProgramReadiness = ({
  token = "",
  hasTradeApi = false,
  tradeProfile,
  expectedUserId = "",
  expectedTradeId = "",
} = {}) => {
  const signedIn = hasYudaoToken(token);
  const hasProfile = Boolean(tradeProfile && typeof tradeProfile === "object");
  const userId = normalizeComparable(tradeProfile?.userId ?? tradeProfile?.id);
  const tradeId = normalizeComparable(tradeProfile?.tradeId);
  const expectedUser = normalizeComparable(expectedUserId);
  const expectedTrade = normalizeComparable(expectedTradeId);
  const issues = [];

  if (!signedIn) issues.push("missing-token");
  if (!hasTradeApi && !hasProfile) issues.push("missing-trade-api");

  if (hasProfile) {
    if (!tradeId) issues.push("missing-trade-id");
    if (expectedTrade && tradeId !== expectedTrade) issues.push("trade-id-mismatch");
    if (expectedUser && !userId) issues.push("missing-user-id");
    if (expectedUser && userId && userId !== expectedUser) issues.push("user-id-mismatch");
  } else if (expectedUser || expectedTrade) {
    issues.push("missing-trade-profile");
  }

  const hasBlockingMismatch = issues.some((issue) => issue.endsWith("-mismatch"));
  return {
    ready: issues.length === 0,
    partial: !hasBlockingMismatch && (hasTradeApi || hasProfile),
    issues,
    userId,
    tradeId,
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
  accountProfile,
  addresses,
  billingRecords,
  wishlistRecords,
  tradeProfile,
  expectedUserId = "",
  expectedAddressId = "",
  expectedWishlistSpuId = "",
  expectedWishlistSkuId = "",
  expectedTradeId = "",
} = {}) => {
  const signedIn = hasYudaoToken(token);
  const cartReadiness = getCartReadiness(cartItems, token);
  const productReady = Number(productCount) > 0;
  const accountProfileReadiness = getAccountProfileReadiness({
    token,
    hasAccountProfileApi,
    profile: accountProfile,
    expectedUserId,
  });
  const billingReadiness = getBillingReadiness({
    token,
    hasBillingReadApi,
    billingRecords,
  });
  const addressBookReadiness = getAddressBookReadiness({
    token,
    hasAddressBookApi,
    addresses,
    expectedAddressId,
  });
  const wishlistReadiness = getWishlistReadiness({
    token,
    hasWishlistApi,
    wishlistRecords,
    expectedSpuId: expectedWishlistSpuId,
    expectedSkuId: expectedWishlistSkuId,
  });
  const tradeReadiness = getTradeProgramReadiness({
    token,
    hasTradeApi,
    tradeProfile,
    expectedUserId,
    expectedTradeId,
  });

  const status = (ready, partial = false) => {
    if (ready) return REAL_ACCOUNT_MODULE_STATUS.ready;
    return partial ? REAL_ACCOUNT_MODULE_STATUS.partial : REAL_ACCOUNT_MODULE_STATUS.blocked;
  };

  return {
    productCatalog: status(productReady),
    cart: status(cartReadiness.itemChecks.length > 0 && cartReadiness.itemChecks.every((item) => item.ready), cartItems.length > 0),
    checkout: status(cartReadiness.ready, cartReadiness.checkoutMode !== "empty"),
    orders: status(signedIn && hasOrderReadApi, hasOrderReadApi),
    billing: status(billingReadiness.ready, billingReadiness.partial),
    accountProfile: status(accountProfileReadiness.ready, accountProfileReadiness.partial),
    addressBook: status(addressBookReadiness.ready, addressBookReadiness.partial),
    wishlist: status(wishlistReadiness.ready, wishlistReadiness.partial),
    membership: status(signedIn && hasMembershipApi, hasMembershipApi),
    giftRegistry: status(signedIn && hasGiftRegistryApi, hasGiftRegistryApi),
    tradeProgram: status(tradeReadiness.ready, tradeReadiness.partial),
  };
};
