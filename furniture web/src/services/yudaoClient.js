import {
  clearYudaoSession,
  LEGACY_AUTH_TOKEN_STORAGE_KEY,
  readYudaoSession,
  writeYudaoSession,
} from "./authSession.js";

const DEFAULT_APP_API_BASE = "http://127.0.0.1:48080/app-api";
const DEFAULT_APP_TENANT_ID = "121";
export const AUTH_TOKEN_STORAGE_KEY = LEGACY_AUTH_TOKEN_STORAGE_KEY;

const trimSlash = (value) => value.replace(/\/$/, "");

export const getYudaoAppApiBase = () =>
  trimSlash(import.meta.env.VITE_YUDAO_APP_API_BASE || DEFAULT_APP_API_BASE);

export const getYudaoAppTenantId = () =>
  String(import.meta.env.VITE_YUDAO_APP_TENANT_ID || DEFAULT_APP_TENANT_ID).trim();

export const readYudaoToken = (storage) =>
  readYudaoSession(storage)?.accessToken || "";

export const writeYudaoToken = (token, storage) => {
  const nextToken = String(token || "").trim();
  if (nextToken) {
    writeYudaoSession(
      {
        userId: null,
        accessToken: nextToken,
        refreshToken: "",
        expiresTime: "",
      },
      storage
    );
  } else {
    clearYudaoSession(storage);
  }
};

const hasOwn = (object, key) => Object.prototype.hasOwnProperty.call(object, key);

const authStorage = (options = {}) => (hasOwn(options, "storage") ? options.storage : undefined);

const persistLoginResponse = (data, options = {}) => writeYudaoSession(data, authStorage(options));

const AUTH_FAILURE_CODES = new Set([401]);

const isAuthFailurePayload = (payload) =>
  payload && typeof payload === "object" && AUTH_FAILURE_CODES.has(Number(payload.code));

const withoutAuthorizationHeader = (headers = {}) => {
  const nextHeaders = { ...headers };
  delete nextHeaders.Authorization;
  delete nextHeaders.authorization;
  return nextHeaders;
};

const readYudaoPayload = async (response) => {
  try {
    return await response.json();
  } catch {
    return null;
  }
};

const createYudaoError = (payload) => {
  const error = new Error(payload?.msg || payload?.message || `Yudao request failed: ${payload?.code}`);
  error.code = Number(payload?.code);
  error.data = payload?.data;
  return error;
};

export const unwrapYudaoResult = (payload) => {
  if (!payload || typeof payload !== "object") return payload;
  if (payload.code !== undefined && payload.code !== 0) {
    throw createYudaoError(payload);
  }
  return payload.data !== undefined ? payload.data : payload;
};

export const YUDAO_MEMBER_ERROR_CODES = {
  USER_EMAIL_USED: 1004001004,
  EMAIL_CREDENTIAL_NOT_FOUND: 1004003010,
  EMAIL_CREDENTIAL_EXPIRED: 1004003011,
  EMAIL_CREDENTIAL_USED: 1004003012,
  EMAIL_CAPTCHA_REQUIRED: 1004003014,
  EMAIL_CAPTCHA_INVALID: 1004003015,
  EMAIL_CODE_VERIFY_TOO_MANY: 1004003016,
};

const fenToYuan = (value) => {
  const amount = Number(value);
  return Number.isFinite(amount) ? amount / 100 : 0;
};

const firstSku = (spu) => (Array.isArray(spu.skus) && spu.skus.length > 0 ? spu.skus[0] : {});

export const mapSpuToProduct = (spu) => {
  const sku = firstSku(spu);
  const gallery = Array.isArray(spu.sliderPicUrls) ? spu.sliderPicUrls.filter(Boolean) : [];
  const cover = spu.picUrl || sku.picUrl || gallery[0] || "";

  return {
    id: spu.id,
    skuId: sku.id || spu.skuId || spu.id,
    name: spu.name || "Untitled product",
    subtitle: spu.introduction || spu.keyword || "",
    description: spu.description || spu.introduction || "",
    cover,
    gallery,
    price: fenToYuan(sku.price ?? spu.price),
    marketPrice: fenToYuan(sku.marketPrice ?? spu.marketPrice),
    stock: Number(sku.stock ?? spu.stock ?? 0),
    salesCount: Number(spu.salesCount ?? 0),
    productType: spu.productType || spu.type || spu.categoryCode || spu.categoryName || "",
    detailConfig: spu.detailConfig || null,
    source: "yudao",
    raw: spu,
  };
};

export const mapCartResponseToItems = (cartResponse) => {
  const rows = [...(cartResponse?.validList || []), ...(cartResponse?.invalidList || [])];
  return rows.map((row) => {
    const product = mapSpuToProduct({ ...(row.spu || {}), skus: [row.sku || {}] });
    return {
      ...product,
      cartId: row.id,
      quantity: Number(row.count) || 1,
      selected: row.selected !== false,
      source: "yudao",
    };
  });
};

export const mapAddressResponse = (address = {}) => ({
  id: address.id,
  name: address.name || "",
  mobile: address.mobile || "",
  areaName: address.areaName || "",
  detailAddress: address.detailAddress || "",
  label: [address.name, address.mobile, `${address.areaName || ""} ${address.detailAddress || ""}`.trim()]
    .filter(Boolean)
    .join(" - "),
  raw: address,
});

export const mapSettlementResponse = (settlement = {}) => ({
  payPrice: fenToYuan(settlement.price?.payPrice ?? settlement.payPrice),
  totalPrice: fenToYuan(settlement.price?.totalPrice ?? settlement.totalPrice),
  deliveryPrice: fenToYuan(settlement.price?.deliveryPrice ?? settlement.deliveryPrice),
  items: (settlement.items || []).map((item) => ({
    skuId: item.skuId,
    count: Number(item.count) || 1,
    name: item.spuName || item.name || "Product",
    cover: item.picUrl || item.cover || "",
    payPrice: fenToYuan(item.payPrice ?? item.price),
  })),
  raw: settlement,
});

export const mapOrderDetail = (order = {}) => ({
  id: order.id,
  no: order.no || String(order.id || ""),
  status: order.status,
  payStatus: Boolean(order.payStatus),
  payPrice: fenToYuan(order.payPrice),
  payOrderId: order.payOrderId,
  createTime: order.createTime,
  items: (order.items || []).map((item) => ({
    id: item.id,
    skuId: item.skuId,
    name: item.spuName || item.name || "Product",
    cover: item.picUrl || item.cover || "",
    count: Number(item.count) || 1,
    price: fenToYuan(item.price ?? item.payPrice),
  })),
  raw: order,
});

export const mapOrderPage = (page = {}) => ({
  list: (page.list || []).map(mapOrderDetail),
  total: Number(page.total || 0),
});

export const requestYudao = async (path, options = {}) => {
  const {
    baseUrl,
    storage,
    tenantId: optionTenantId,
    token: optionToken,
    skipAuthRetry,
    headers: optionHeaders,
    ...fetchOptions
  } = options;
  const base = baseUrl || getYudaoAppApiBase();
  const session = readYudaoSession(storage);
  const token = hasOwn(options, "token") ? optionToken : session?.accessToken || "";
  const tenantId = String(hasOwn(options, "tenantId") ? optionTenantId || "" : getYudaoAppTenantId()).trim();
  const headers = {
    "Content-Type": "application/json",
    ...(tenantId ? { "tenant-id": tenantId } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(optionHeaders || {}),
  };

  const response = await fetch(`${base}${path}`, {
    ...fetchOptions,
    headers,
  });
  const payload = await readYudaoPayload(response);

  if (!response.ok) {
    if (isAuthFailurePayload(payload) && session?.refreshToken && !skipAuthRetry) {
      let refreshed;
      try {
        refreshed = await refreshMemberToken(session.refreshToken, {
          ...options,
          headers: withoutAuthorizationHeader(optionHeaders),
        });
      } catch (error) {
        clearYudaoSession(authStorage(options));
        throw error;
      }
      return requestYudao(path, {
        ...options,
        headers: withoutAuthorizationHeader(optionHeaders),
        token: refreshed?.accessToken || "",
        skipAuthRetry: true,
      });
    }
    throw new Error(`Yudao HTTP ${response.status}`);
  }

  if (isAuthFailurePayload(payload) && session?.refreshToken && !skipAuthRetry) {
    let refreshed;
    try {
      refreshed = await refreshMemberToken(session.refreshToken, {
        ...options,
        headers: withoutAuthorizationHeader(optionHeaders),
      });
    } catch (error) {
      clearYudaoSession(authStorage(options));
      throw error;
    }
    return requestYudao(path, {
      ...options,
      headers: withoutAuthorizationHeader(optionHeaders),
      token: refreshed?.accessToken || "",
      skipAuthRetry: true,
    });
  }

  return unwrapYudaoResult(payload);
};

export const sendMemberSmsCode = (mobile, options = {}) =>
  requestYudao("/member/auth/send-sms-code", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify({ mobile, scene: 1 }),
  });

export const requestEmailSignInLink = (email, options = {}) =>
  requestYudao("/member/auth/email-secure-link", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify({ email }),
  });

export const sendEmailRegistrationCode = (email, options = {}) => {
  const { captchaVerification, ...requestOptions } = options;
  return requestYudao("/member/auth/send-email-code", {
    ...requestOptions,
    method: "POST",
    token: "",
    body: JSON.stringify({
      email,
      scene: 5,
      ...(captchaVerification ? { captchaVerification } : {}),
    }),
  });
};

export const createEmailCaptchaChallenge = (options = {}) =>
  requestYudao("/member/auth/email-captcha/challenge", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify({}),
  });

export const verifyEmailCaptchaChallenge = (payload, options = {}) =>
  requestYudao("/member/auth/email-captcha/verify", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });

export const loginBySms = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/sms-login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByPassword = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByEmailPassword = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/email-login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const registerByEmail = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/email-register", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByTradeAccount = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/trade-login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const refreshMemberToken = async (refreshToken, options = {}) => {
  const data = await requestYudao(
    `/member/auth/refresh-token?refreshToken=${encodeURIComponent(refreshToken)}`,
    {
      ...options,
      headers: withoutAuthorizationHeader(options.headers),
      method: "POST",
      token: "",
      skipAuthRetry: true,
    }
  );
  return persistLoginResponse(data, options);
};

export const logoutMember = async (options = {}) => {
  try {
    await requestYudao("/member/auth/logout", {
      ...options,
      method: "POST",
      skipAuthRetry: true,
    });
  } finally {
    clearYudaoSession(authStorage(options));
  }
};

export const getProductPage = async (params = {}, options = {}) => {
  const search = new URLSearchParams({
    pageNo: "1",
    pageSize: "24",
    ...Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== "")),
  });
  const data = await requestYudao(`/product/spu/page?${search}`, options);
  return {
    list: (data.list || []).map(mapSpuToProduct),
    total: Number(data.total || 0),
  };
};

export const getProductDetail = async (id, options = {}) => {
  const data = await requestYudao(`/product/spu/get-detail?id=${encodeURIComponent(id)}`, options);
  return mapSpuToProduct(data);
};

export const addCartItem = (skuId, count = 1, options = {}) =>
  requestYudao("/trade/cart/add", {
    ...options,
    method: "POST",
    body: JSON.stringify({ skuId, count }),
  });

export const updateCartItemCount = (cartId, count, options = {}) =>
  requestYudao("/trade/cart/update-count", {
    ...options,
    method: "PUT",
    body: JSON.stringify({ id: cartId, count }),
  });

export const deleteCartItems = (cartIds, options = {}) =>
  requestYudao(`/trade/cart/delete?ids=${cartIds.join(",")}`, {
    ...options,
    method: "DELETE",
  });

export const getRemoteCartItems = async (options = {}) => {
  const data = await requestYudao("/trade/cart/list", options);
  return mapCartResponseToItems(data);
};

export const getDefaultAddress = async (options = {}) => {
  const data = await requestYudao("/member/address/get-default", options);
  return data ? mapAddressResponse(data) : null;
};

export const getAddressList = async (options = {}) => {
  const data = await requestYudao("/member/address/list", options);
  return (data || []).map(mapAddressResponse);
};

export const settleOrder = async (payload, options = {}) => {
  const search = new URLSearchParams();
  payload.items.forEach((item, index) => {
    search.append(`items[${index}].skuId`, item.skuId);
    search.append(`items[${index}].count`, item.count);
    search.append(`items[${index}].cartId`, item.cartId);
  });
  search.append("pointStatus", String(payload.pointStatus));
  search.append("deliveryType", String(payload.deliveryType));
  if (payload.addressId) search.append("addressId", String(payload.addressId));
  const data = await requestYudao(`/trade/order/settlement?${search}`, options);
  return mapSettlementResponse(data);
};

export const createOrder = async (payload, options = {}) =>
  requestYudao("/trade/order/create", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });

export const getOrderPage = async (params = {}, options = {}) => {
  const search = new URLSearchParams({ pageNo: "1", pageSize: "10", ...params });
  const data = await requestYudao(`/trade/order/page?${search}`, options);
  return mapOrderPage(data);
};

export const getOrderDetail = async (id, options = {}) => {
  const data = await requestYudao(`/trade/order/get-detail?id=${encodeURIComponent(id)}`, options);
  return data ? mapOrderDetail(data) : null;
};
