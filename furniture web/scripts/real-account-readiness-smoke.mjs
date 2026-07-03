import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  buildRealAccountModuleSnapshot,
  getCartReadiness,
  getYudaoProductIdentity,
  isRealYudaoProduct,
} from "../src/services/realAccountReadiness.js";
import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.production";
export const OPTIONAL_READINESS_STEP_UNAVAILABLE = { optionalUnavailable: true };

export const parseRealAccountReadinessArgs = (argv = []) => {
  const options = {
    envFile: DEFAULT_ENV_FILE,
    checkOrder: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[index + 1] || DEFAULT_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--env-file=")) {
      options.envFile = arg.slice("--env-file=".length);
    } else if (arg === "--check-order") {
      options.checkOrder = true;
    }
  }

  return options;
};

const readEnvFile = (envFile) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) {
    throw new Error(`Production env file not found: ${envPath}`);
  }
  return parseEnvFileContent(readFileSync(envPath, "utf8"));
};

const firstValue = (...values) => values.find((value) => String(value || "").trim()) || "";

const required = (value, key) => {
  const normalized = String(value || "").trim();
  if (!normalized) throw new Error(`${key} is required for real account readiness smoke.`);
  if (isPlaceholder(normalized)) throw new Error(`${key} must be replaced with a real account smoke value.`);
  return normalized;
};

const isPositiveInteger = (value) => /^[1-9]\d*$/.test(String(value || "").trim());

const requirePositiveInteger = (value, key) => {
  const normalized = required(value, key);
  if (!isPositiveInteger(normalized)) throw new Error(`${key} must be a positive integer.`);
  return normalized;
};

const optionalPositiveInteger = (value, key) => {
  const normalized = String(value || "").trim();
  if (!normalized) return "";
  if (isPlaceholder(normalized)) throw new Error(`${key} must be replaced with a real account smoke value.`);
  if (!isPositiveInteger(normalized)) throw new Error(`${key} must be a positive integer.`);
  return normalized;
};

const isPlaceholder = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  return normalized.includes("<") || normalized.includes(">") || normalized.includes("replace-me");
};

const isDocumentationDomain = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "example.com" || normalized.endsWith(".example.com") || normalized.endsWith(".example");
};

const requireLaunchUrl = (value, key) => {
  const normalized = required(value, key).replace(/\/$/, "");
  try {
    const url = new URL(normalized);
    if (!["http:", "https:"].includes(url.protocol)) {
      throw new Error(`${key} must be an absolute http(s) URL.`);
    }
    if (isDocumentationDomain(url.hostname)) {
      throw new Error(`${key} must not use a documentation/example domain`);
    }
  } catch (error) {
    if (error.message.includes("absolute http(s) URL")) throw error;
    if (error.message.includes("documentation/example domain")) throw error;
    throw new Error(`${key} must be an absolute http(s) URL.`);
  }
  return normalized;
};

const requireLaunchEmail = (value, key) => {
  const normalized = required(value, key);
  const domain = normalized.includes("@") ? normalized.split("@").pop() : "";
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(normalized)) {
    throw new Error(`${key} must be a valid email address.`);
  }
  if (isDocumentationDomain(domain)) {
    throw new Error(`${key} must not use a documentation/example domain`);
  }
  return normalized;
};

export const buildRealAccountReadinessConfig = (options = {}, runtimeEnv = process.env) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const fileEnv = readEnvFile(envFile);

  return {
    baseUrl: requireLaunchUrl(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL,
        runtimeEnv.YUDAO_SMOKE_BASE_URL,
        fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL,
        fileEnv.YUDAO_SMOKE_BASE_URL,
        fileEnv.VITE_YUDAO_APP_API_BASE,
      ),
      "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL",
    ),
    tenantId: requirePositiveInteger(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID,
        runtimeEnv.YUDAO_SMOKE_TENANT_ID,
        fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID,
        fileEnv.YUDAO_SMOKE_TENANT_ID,
        fileEnv.VITE_YUDAO_APP_TENANT_ID,
      ),
      "YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID",
    ),
    token: required(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_TOKEN,
        runtimeEnv.YUDAO_SMOKE_TOKEN,
        fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_TOKEN,
        fileEnv.YUDAO_SMOKE_TOKEN,
      ),
      "YUDAO_REAL_ACCOUNT_SMOKE_TOKEN",
    ),
    adminToken: required(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_ADMIN_TOKEN, fileEnv.YUDAO_REAL_ACCOUNT_ADMIN_TOKEN),
      "YUDAO_REAL_ACCOUNT_ADMIN_TOKEN",
    ),
    adminBaseUrl: requireLaunchUrl(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL, fileEnv.YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL),
      "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL",
    ),
    adminTenantId: requirePositiveInteger(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID,
        runtimeEnv.YUDAO_SMOKE_TENANT_ID,
        fileEnv.YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID,
        fileEnv.YUDAO_SMOKE_TENANT_ID,
      ),
      "YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID",
    ),
    userId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_USER_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_USER_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_USER_ID",
    ),
    giftRegistryPublicCode: required(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE,
        fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE,
      ),
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE",
    ),
    tradeId: required(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID",
    ),
    tradeEmail: requireLaunchEmail(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL),
      "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL",
    ),
    cartId: optionalPositiveInteger(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_CART_ID,
        runtimeEnv.YUDAO_ORDER_SMOKE_CART_ID,
        fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_CART_ID,
        fileEnv.YUDAO_ORDER_SMOKE_CART_ID,
      ),
      "YUDAO_REAL_ACCOUNT_SMOKE_CART_ID",
    ),
    skuId: optionalPositiveInteger(
      firstValue(
        runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID,
        runtimeEnv.YUDAO_ORDER_SMOKE_SKU_ID,
        fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID,
        fileEnv.YUDAO_ORDER_SMOKE_SKU_ID,
      ),
      "YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID",
    ),
    addressId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID",
    ),
    wishlistSpuId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID",
    ),
    wishlistSkuId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID",
    ),
    membershipStatus: required(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS),
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS",
    ),
    membershipPlanCode: required(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE),
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE",
    ),
    giftRegistryItemSpuId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID",
    ),
    giftRegistryItemSkuId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID",
    ),
    spuId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID",
    ),
    orderId: requirePositiveInteger(
      firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID),
      "YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID",
    ),
    checkOrder:
      options.checkOrder === true ||
      String(firstValue(runtimeEnv.YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER, fileEnv.YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER)).toLowerCase() ===
        "true",
  };
};

export const buildRealAccountReadinessPlan = (config) => [
  {
    name: "product-catalog-page",
    method: "GET",
    path: "/product/spu/page?pageNo=1&pageSize=1",
    required: true,
  },
  ...(config.spuId
    ? [
        {
          name: "product-detail",
          method: "GET",
          path: `/product/spu/get-detail?id=${encodeURIComponent(config.spuId)}`,
          required: true,
        },
      ]
    : []),
  {
    name: "cart-list",
    method: "GET",
    path: "/trade/cart/list",
    required: true,
  },
  {
    name: "order-page",
    method: "GET",
    path: "/trade/order/page?pageNo=1&pageSize=1",
    required: true,
  },
  ...(config.checkOrder && config.orderId
    ? [
        {
          name: "order-detail",
          method: "GET",
          path: `/trade/order/get-detail?id=${encodeURIComponent(config.orderId)}`,
          required: true,
        },
      ]
    : []),
  {
    name: "member-profile",
    method: "GET",
    path: "/member/user/get",
    required: true,
  },
  {
    name: "member-address-list",
    method: "GET",
    path: "/member/address/list",
    required: true,
  },
  {
    name: "wishlist-page",
    method: "GET",
    path: "/product/favorite/page?pageNo=1&pageSize=1",
    required: true,
  },
  {
    name: "membership-profile",
    method: "GET",
    path: "/member/membership/get",
    required: false,
  },
  {
    name: "gift-registry-my",
    method: "GET",
    path: "/member/gift-registry/my",
    required: false,
  },
  {
    name: "membership-admin-page",
    method: "GET",
    path: `/member/membership/page?pageNo=1&pageSize=1&userId=${encodeURIComponent(config.userId)}`,
    required: true,
    scope: "admin",
  },
  {
    name: "gift-registry-admin-page",
    method: "GET",
    path: `/member/gift-registry/page?pageNo=1&pageSize=1&userId=${encodeURIComponent(config.userId)}&publicCode=${encodeURIComponent(
      config.giftRegistryPublicCode,
    )}`,
    required: true,
    scope: "admin",
  },
  {
    name: "trade-application-admin-page",
    method: "GET",
    path: `/member/trade-application/page?pageNo=1&pageSize=1&primaryEmail=${encodeURIComponent(config.tradeEmail)}`,
    required: true,
    scope: "admin",
  },
];

const requestLive = async (config, step) => {
  const isAdminStep = step.scope === "admin";
  const response = await fetch(`${isAdminStep ? config.adminBaseUrl : config.baseUrl}${step.path}`, {
    method: step.method,
    headers: {
      "Content-Type": "application/json",
      "tenant-id": isAdminStep ? config.adminTenantId : config.tenantId,
      Authorization: `Bearer ${isAdminStep ? config.adminToken : config.token}`,
    },
  });
  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.code !== 0) {
    const error = new Error(`Real account readiness failed: ${step.method} ${step.path} ${JSON.stringify(payload)}`);
    error.step = step;
    throw error;
  }

  return payload.data;
};

const normalizeProductPage = (data = {}) => ({
  list: Array.isArray(data.list) ? data.list : [],
  total: Number(data.total || 0),
});

const normalizeCartRows = (cartResponse = {}) => [
  ...(cartResponse.validList || []).map((row) => ({ ...row, source: "yudao" })),
  ...(cartResponse.invalidList || []).map((row) => ({ ...row, source: "yudao" })),
].map((row) => ({
  source: "yudao",
  cartId: row.id,
  spuId: row.spu?.id || row.spuId,
  skuId: row.sku?.id || row.skuId,
  quantity: Number(row.count) || 1,
}));

const hasAvailableResult = (results = {}, key) =>
  Object.prototype.hasOwnProperty.call(results, key) && results[key]?.optionalUnavailable !== true;

const hasPersistedRecord = (record, identityKeys = []) => {
  if (!record || typeof record !== "object" || record.optionalUnavailable === true) return false;
  return identityKeys.some((key) => String(record[key] ?? "").trim());
};

const hasPagedRecords = (page = {}) => {
  if (!page || typeof page !== "object") return false;
  if (Array.isArray(page.list) && page.list.length > 0) return true;
  return Number(page.total || 0) > 0;
};

const hasListRecords = (list) => Array.isArray(list) && list.length > 0;

const normalizeComparable = (value) => String(value ?? "").trim();
const normalizeEmailComparable = (value) => normalizeComparable(value).toLowerCase();

const pageHasMatchingRecord = (page = {}, predicate) => Array.isArray(page?.list) && page.list.some(predicate);

const findGiftRegistryAdminRow = (page = {}, expectedUserId = "", expectedPublicCode = "") =>
  Array.isArray(page?.list)
    ? page.list.find(
        (row) =>
          (!expectedUserId || normalizeComparable(row.userId) === expectedUserId) &&
          (!expectedPublicCode || normalizeComparable(row.publicCode) === expectedPublicCode),
      )
    : null;

const recordMatchesId = (record = {}, expectedId = "") =>
  [record.id, record.orderId].some((value) => normalizeComparable(value) === expectedId);

const rowMatchesEmail = (row = {}, expectedEmail = "") =>
  [row.primaryEmail, row.email, row.memberEmail, row.contactEmail].some(
    (value) => normalizeEmailComparable(value) === expectedEmail,
  );

const productDetailHasSku = (product = {}, expectedSkuId = "") => {
  if (!expectedSkuId) return true;
  const detail = product || {};
  const skus = [
    ...(Array.isArray(detail.skus) ? detail.skus : []),
    ...(Array.isArray(detail.raw?.skus) ? detail.raw.skus : []),
  ];
  return [detail.skuId, detail.raw?.skuId, ...skus.map((sku) => sku?.id || sku?.skuId)].some(
    (value) => normalizeComparable(value) === expectedSkuId,
  );
};

const registryHasSeededItem = (registry = {}, expectedSpuId = "", expectedSkuId = "") =>
  Array.isArray(registry?.items) &&
  registry.items.some(
    (item) =>
      (!expectedSpuId || normalizeComparable(item.spuId || item.id) === expectedSpuId) &&
      (!expectedSkuId || normalizeComparable(item.skuId) === expectedSkuId),
  );

export const evaluateRealAccountReadiness = (results = {}, token = "", expected = {}) => {
  const productPage = normalizeProductPage(results["product-catalog-page"]);
  const productDetail = results["product-detail"];
  const memberProfile = results["member-profile"] || {};
  const giftRegistry = results["gift-registry-my"] || {};
  const cartItems = normalizeCartRows(results["cart-list"]);
  const cartReadiness = getCartReadiness(cartItems, token);
  const productIdentity = productDetail ? getYudaoProductIdentity({ source: "yudao", ...productDetail }) : null;
  const productDetailReady = productDetail ? isRealYudaoProduct({ source: "yudao", ...productDetail }) : true;
  const expectedUserId = normalizeComparable(expected.userId || memberProfile.id || memberProfile.userId);
  const expectedGiftRegistryPublicCode = normalizeComparable(expected.giftRegistryPublicCode || giftRegistry.publicCode);
  const expectedTradeId = normalizeComparable(expected.tradeId || memberProfile.tradeId);
  const expectedOrderId = normalizeComparable(expected.orderId);
  const expectedCartId = normalizeComparable(expected.cartId);
  const expectedSkuId = normalizeComparable(expected.skuId);
  const expectedSpuId = normalizeComparable(expected.spuId);
  const expectedAddressId = normalizeComparable(expected.addressId);
  const expectedWishlistSpuId = normalizeComparable(expected.wishlistSpuId);
  const expectedWishlistSkuId = normalizeComparable(expected.wishlistSkuId);
  const expectedMembershipStatus = normalizeComparable(expected.membershipStatus);
  const expectedMembershipPlanCode = normalizeComparable(expected.membershipPlanCode);
  const expectedGiftRegistryItemSpuId = normalizeComparable(expected.giftRegistryItemSpuId);
  const expectedGiftRegistryItemSkuId = normalizeComparable(expected.giftRegistryItemSkuId);
  const expectedTradeEmail = normalizeEmailComparable(expected.tradeEmail);
  const moduleSnapshot = buildRealAccountModuleSnapshot({
    token,
    productCount: productPage.total || productPage.list.length,
    cartItems,
    hasOrderReadApi: hasPagedRecords(results["order-page"]),
    hasBillingReadApi: hasPagedRecords(results["order-page"]),
    hasAccountProfileApi: hasPersistedRecord(results["member-profile"], ["id", "userId", "email", "mobile"]),
    hasAddressBookApi: hasListRecords(results["member-address-list"]),
    hasWishlistApi: hasPagedRecords(results["wishlist-page"]),
    hasMembershipApi:
      hasAvailableResult(results, "membership-profile") &&
      hasPersistedRecord(results["membership-profile"], ["id", "memberId", "userId", "status", "planCode"]),
    hasGiftRegistryApi:
      hasAvailableResult(results, "gift-registry-my") &&
      hasPersistedRecord(results["gift-registry-my"], ["id", "publicCode", "status"]),
    hasTradeApi: Boolean(String(memberProfile.tradeId || "").trim()),
  });

  const failures = [];
  if (!productPage.list.length) failures.push("product-catalog-empty");
  if (!productDetailReady) failures.push(`product-detail-missing-identity:${JSON.stringify(productIdentity)}`);
  if (productDetail && expectedSpuId && normalizeComparable(productIdentity?.spuId) !== expectedSpuId) {
    failures.push("product-detail-seeded-product-missing");
  }
  if (productDetail && expectedSkuId && !productDetailHasSku(productDetail, expectedSkuId)) {
    failures.push("product-detail-seeded-sku-missing");
  }
  if (!cartReadiness.ready) failures.push(...cartReadiness.blockingReasons);
  if (
    (expectedCartId || expectedSkuId) &&
    !cartItems.some(
      (row) =>
        (!expectedCartId || normalizeComparable(row.cartId) === expectedCartId) &&
        (!expectedSkuId || normalizeComparable(row.skuId) === expectedSkuId),
    )
  ) {
    failures.push("cart-seeded-row-missing");
  }
  const addressRows = Array.isArray(results["member-address-list"]) ? results["member-address-list"] : [];
  if (
    expectedAddressId &&
    !addressRows.some((row) => [row.id, row.addressId].some((value) => normalizeComparable(value) === expectedAddressId))
  ) {
    failures.push("address-book-seeded-address-missing");
  }
  if (
    (expectedWishlistSpuId || expectedWishlistSkuId) &&
    !pageHasMatchingRecord(
      results["wishlist-page"],
      (row) =>
        (!expectedWishlistSpuId || normalizeComparable(row.spuId || row.id) === expectedWishlistSpuId) &&
        (!expectedWishlistSkuId || normalizeComparable(row.skuId) === expectedWishlistSkuId),
    )
  ) {
    failures.push("wishlist-seeded-row-missing");
  }
  if (
    expectedOrderId &&
    !pageHasMatchingRecord(results["order-page"], (row) => recordMatchesId(row, expectedOrderId))
  ) {
    failures.push("order-page-seeded-order-missing");
  }
  if (
    expected.checkOrder &&
    expectedOrderId &&
    !recordMatchesId(results["order-detail"], expectedOrderId)
  ) {
    failures.push("order-detail-seeded-order-missing");
  }
  if (
    expectedUserId &&
    ![memberProfile.id, memberProfile.userId].some((value) => normalizeComparable(value) === expectedUserId)
  ) {
    failures.push("app-profile-seeded-user-mismatch");
  }
  const membershipProfile = results["membership-profile"] || {};
  if (
    expectedUserId &&
    normalizeComparable(membershipProfile.userId) &&
    normalizeComparable(membershipProfile.userId) !== expectedUserId
  ) {
    failures.push("app-membership-seeded-user-mismatch");
  }
  if (expectedMembershipStatus && normalizeComparable(membershipProfile.status) !== expectedMembershipStatus) {
    failures.push("app-membership-seeded-status-mismatch");
  }
  if (expectedMembershipPlanCode && normalizeComparable(membershipProfile.planCode) !== expectedMembershipPlanCode) {
    failures.push("app-membership-seeded-plan-mismatch");
  }
  if (expectedGiftRegistryPublicCode && normalizeComparable(giftRegistry.publicCode) !== expectedGiftRegistryPublicCode) {
    failures.push("app-gift-registry-seeded-record-mismatch");
  }
  if (
    expectedUserId &&
    normalizeComparable(giftRegistry.userId) &&
    normalizeComparable(giftRegistry.userId) !== expectedUserId
  ) {
    failures.push("app-gift-registry-seeded-user-mismatch");
  }
  if (
    (expectedGiftRegistryItemSpuId || expectedGiftRegistryItemSkuId) &&
    !registryHasSeededItem(giftRegistry, expectedGiftRegistryItemSpuId, expectedGiftRegistryItemSkuId)
  ) {
    failures.push("app-gift-registry-seeded-item-missing");
  }
  if (expectedTradeId && normalizeComparable(memberProfile.tradeId) !== expectedTradeId) {
    failures.push("app-trade-seeded-id-mismatch");
  }
  if (!hasPagedRecords(results["membership-admin-page"])) failures.push("admin-membership-page-empty");
  else {
    const membershipAdminRow = Array.isArray(results["membership-admin-page"]?.list)
      ? results["membership-admin-page"].list.find((row) => !expectedUserId || normalizeComparable(row.userId) === expectedUserId)
      : null;
    if (expectedUserId && !membershipAdminRow) {
      failures.push("admin-membership-seeded-user-missing");
    } else if (membershipAdminRow) {
      if (expectedMembershipStatus && normalizeComparable(membershipAdminRow.status) !== expectedMembershipStatus) {
        failures.push("admin-membership-seeded-status-mismatch");
      }
      if (expectedMembershipPlanCode && normalizeComparable(membershipAdminRow.planCode) !== expectedMembershipPlanCode) {
        failures.push("admin-membership-seeded-plan-mismatch");
      }
    }
  }
  if (!hasPagedRecords(results["gift-registry-admin-page"])) failures.push("admin-gift-registry-page-empty");
  else {
    const giftRegistryAdminRow = findGiftRegistryAdminRow(
      results["gift-registry-admin-page"],
      expectedUserId,
      expectedGiftRegistryPublicCode,
    );
    if ((expectedUserId || expectedGiftRegistryPublicCode) && !giftRegistryAdminRow) {
      failures.push("admin-gift-registry-seeded-record-missing");
    }
    const giftRegistryAdminDetail = results["gift-registry-admin-detail"];
    if (expectedUserId && normalizeComparable(giftRegistryAdminDetail?.userId) && normalizeComparable(giftRegistryAdminDetail.userId) !== expectedUserId) {
      failures.push("admin-gift-registry-detail-seeded-user-mismatch");
    }
    if (
      expectedGiftRegistryPublicCode &&
      normalizeComparable(giftRegistryAdminDetail?.publicCode) &&
      normalizeComparable(giftRegistryAdminDetail.publicCode) !== expectedGiftRegistryPublicCode
    ) {
      failures.push("admin-gift-registry-detail-seeded-record-mismatch");
    }
    if (
      (expectedGiftRegistryItemSpuId || expectedGiftRegistryItemSkuId) &&
      !registryHasSeededItem(giftRegistryAdminDetail, expectedGiftRegistryItemSpuId, expectedGiftRegistryItemSkuId)
    ) {
      failures.push("admin-gift-registry-detail-seeded-item-missing");
    }
  }
  if (!hasPagedRecords(results["trade-application-admin-page"])) failures.push("admin-trade-application-page-empty");
  else {
    const tradeApplicationAdminRow = Array.isArray(results["trade-application-admin-page"]?.list)
      ? results["trade-application-admin-page"].list.find(
          (row) => !expectedTradeId || normalizeComparable(row.tradeId) === expectedTradeId,
        )
      : null;
    if (expectedTradeId && !tradeApplicationAdminRow) {
      failures.push("admin-trade-application-seeded-record-missing");
    } else if (tradeApplicationAdminRow && expectedTradeEmail && !rowMatchesEmail(tradeApplicationAdminRow, expectedTradeEmail)) {
      failures.push("admin-trade-application-seeded-email-missing");
    }
  }
  Object.entries(moduleSnapshot).forEach(([moduleName, status]) => {
    if (status !== "ready") failures.push(`module-${moduleName}-${status}`);
  });

  return {
    ok: failures.length === 0,
    failures,
    cartReadiness,
    moduleSnapshot,
    seededAccount: {
      userId: expectedUserId,
      cartId: expectedCartId,
      skuId: expectedSkuId,
      addressId: expectedAddressId,
      orderId: expectedOrderId,
      giftRegistryPublicCode: expectedGiftRegistryPublicCode,
      tradeId: expectedTradeId,
      tradeEmail: expectedTradeEmail,
      membershipStatus: expectedMembershipStatus,
      membershipPlanCode: expectedMembershipPlanCode,
      giftRegistryItemSpuId: expectedGiftRegistryItemSpuId,
      giftRegistryItemSkuId: expectedGiftRegistryItemSkuId,
    },
  };
};

export const runRealAccountReadinessSmoke = async (options = {}) => {
  const config = buildRealAccountReadinessConfig(options);
  const results = {};

  for (const step of buildRealAccountReadinessPlan(config)) {
    console.log(`\n==> ${step.name}`);
    try {
      results[step.name] = await requestLive(config, step);
    } catch (error) {
      if (step.required) throw error;
      results[step.name] = { ...OPTIONAL_READINESS_STEP_UNAVAILABLE };
      console.warn(`Optional readiness step skipped: ${step.name}`);
    }
    if (step.name === "gift-registry-admin-page") {
      const adminRegistryRow = findGiftRegistryAdminRow(results[step.name], config.userId, config.giftRegistryPublicCode);
      if (adminRegistryRow?.id) {
        const detailStep = {
          name: "gift-registry-admin-detail",
          method: "GET",
          path: `/member/gift-registry/get?id=${encodeURIComponent(adminRegistryRow.id)}`,
          required: true,
          scope: "admin",
        };
        console.log(`\n==> ${detailStep.name}`);
        results[detailStep.name] = await requestLive(config, detailStep);
      }
    }
  }

  const evaluation = evaluateRealAccountReadiness(results, config.token, config);
  if (!evaluation.ok) {
    throw new Error(`Real account readiness failed: ${evaluation.failures.join(", ")}`);
  }
  return evaluation;
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  try {
    const options = parseRealAccountReadinessArgs(process.argv.slice(2));
    const result = await runRealAccountReadinessSmoke(options);
    console.log("\nReal account readiness smoke passed.");
    console.log(JSON.stringify(result.moduleSnapshot, null, 2));
    console.log(JSON.stringify({ seededAccount: result.seededAccount }, null, 2));
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}
