const DEFAULT_APP_API_BASE = "http://127.0.0.1:48080/app-api";
export const AUTH_TOKEN_STORAGE_KEY = "YUDAO_APP_TOKEN";

const trimSlash = (value) => value.replace(/\/$/, "");

export const getYudaoAppApiBase = () =>
  trimSlash(import.meta.env.VITE_YUDAO_APP_API_BASE || DEFAULT_APP_API_BASE);

export const readYudaoToken = (storage = globalThis.localStorage) => storage?.getItem(AUTH_TOKEN_STORAGE_KEY) || "";

export const writeYudaoToken = (token, storage = globalThis.localStorage) => {
  if (!storage) return;
  const nextToken = String(token || "").trim();
  if (nextToken) {
    storage.setItem(AUTH_TOKEN_STORAGE_KEY, nextToken);
  } else {
    storage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  }
};

export const unwrapYudaoResult = (payload) => {
  if (!payload || typeof payload !== "object") return payload;
  if (payload.code !== undefined && payload.code !== 0) {
    throw new Error(payload.msg || payload.message || `Yudao request failed: ${payload.code}`);
  }
  return payload.data !== undefined ? payload.data : payload;
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
  const base = options.baseUrl || getYudaoAppApiBase();
  const token = options.token || readYudaoToken();
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(`${base}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new Error(`Yudao HTTP ${response.status}`);
  }

  return unwrapYudaoResult(await response.json());
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
