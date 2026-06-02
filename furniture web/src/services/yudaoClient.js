const DEFAULT_APP_API_BASE = "http://127.0.0.1:48080/app-api";

const trimSlash = (value) => value.replace(/\/$/, "");

export const getYudaoAppApiBase = () =>
  trimSlash(import.meta.env.VITE_YUDAO_APP_API_BASE || DEFAULT_APP_API_BASE);

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

export const requestYudao = async (path, options = {}) => {
  const base = options.baseUrl || getYudaoAppApiBase();
  const token = options.token || globalThis.localStorage?.getItem("YUDAO_APP_TOKEN");
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
