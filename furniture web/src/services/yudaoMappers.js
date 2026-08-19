import { buildAddressBookVerificationSummary } from "./addressBookVerification.js";

const fenToYuan = (value) => {
  const amount = Number(value);
  return Number.isFinite(amount) ? amount / 100 : 0;
};

const nullableFenToYuan = (value) => {
  if (value === null || value === undefined || value === "") return null;
  const amount = Number(value);
  return Number.isFinite(amount) ? amount / 100 : null;
};

const nullableNumber = (value) => {
  if (value === null || value === undefined || value === "") return null;
  const amount = Number(value);
  return Number.isFinite(amount) ? amount : null;
};

const firstSku = (spu) => (Array.isArray(spu.skus) && spu.skus.length > 0 ? spu.skus[0] : {});

const uniqueImageUrls = (urls = []) => [
  ...new Set(
    urls
      .map((url) => (typeof url === "string" ? url.trim() : ""))
      .filter(Boolean),
  ),
];

export const mapSpuToProduct = (spu) => {
  const sku = firstSku(spu);
  const sliderImages = uniqueImageUrls(Array.isArray(spu.sliderPicUrls) ? spu.sliderPicUrls : []);
  const cover = uniqueImageUrls([spu.picUrl, sku.picUrl, sliderImages[0]])[0] || "";
  const gallery = sliderImages.filter((url) => url !== cover);
  const categoryName = spu.categoryName || "";
  const categoryCode = String(spu.categoryCode || "").trim().toLowerCase();
  const detailConfig = spu.detailConfig && typeof spu.detailConfig === "object" ? spu.detailConfig : null;

  return {
    id: spu.id,
    spuId: spu.id,
    skuId: sku.id || spu.skuId || spu.id,
    name: spu.name || "Untitled product",
    subtitle: spu.introduction || spu.keyword || "",
    description: spu.description || spu.introduction || "",
    cover,
    gallery,
    price: nullableFenToYuan(sku.price ?? spu.price),
    marketPrice: nullableFenToYuan(sku.marketPrice ?? spu.marketPrice),
    stock: nullableNumber(sku.stock ?? spu.stock),
    salesCount: nullableNumber(spu.salesCount),
    categoryId: spu.categoryId,
    categoryCode,
    categoryParentId: spu.categoryParentId,
    categoryName,
    productType: categoryCode || "uncategorized",
    material: spu.material || detailConfig?.material || "",
    color: spu.color || detailConfig?.color || "",
    isNew: spu.recommendNew === true,
    isBestSeller: spu.recommendBest === true,
    detailConfig,
    displayPolicy: spu.displayPolicy || null,
    source: "yudao",
    raw: spu,
  };
};

export const mapCartResponseToItems = (cartResponse) => {
  const rows = [
    ...(cartResponse?.validList || []).map((row) => ({ ...row, cartProblemKey: "" })),
    ...(cartResponse?.invalidList || []).map((row) => ({ ...row, cartProblemKey: "cart.itemUnavailable" })),
  ];
  return rows.map((row) => {
    const product = mapSpuToProduct({ ...(row.spu || {}), skus: [row.sku || {}] });
    return {
      ...product,
      cartId: row.id,
      cartProblemKey: row.cartProblemKey,
      quantity: Number(row.count) || 1,
      selected: row.selected !== false,
      source: "yudao",
      registryContext:
        row.registryId || row.registryItemId
          ? {
              registryId: row.registryId,
              registryItemId: row.registryItemId,
            }
          : undefined,
    };
  });
};

export const mapFavoritePageToItems = (page = {}) => ({
  list: (page.list || []).map((row) => ({
    favoriteId: row.id,
    id: row.spuId,
    spuId: row.spuId,
    skuId: row.skuId || row.spuId,
    name: row.spuName || row.name || "Product",
    subtitle: row.introduction || row.keyword || "",
    cover: row.picUrl || row.cover || "",
    price: fenToYuan(row.price),
    marketPrice: fenToYuan(row.marketPrice),
    color: row.color || "",
    fabric: row.fabric || "",
    width: row.width || "",
    delivery: row.delivery || "",
    dimensions: row.dimensions || "",
    quantity: Math.max(1, Math.floor(Number(row.count) || Number(row.quantity) || 1)),
    source: "yudao",
    raw: row,
  })),
  total: Number(page.total || 0),
});

export const mapAddressResponse = (address = {}) => {
  const addressVerification = address.addressVerification || address.addressConfirmation || null;

  return {
    id: address.id,
    name: address.name || "",
    mobile: address.mobile || "",
    areaName: address.areaName || "",
    detailAddress: address.detailAddress || "",
    label: [address.name, address.mobile, `${address.areaName || ""} ${address.detailAddress || ""}`.trim()]
      .filter(Boolean)
      .join(" - "),
    addressVerification,
    addressVerificationSummary: buildAddressBookVerificationSummary(addressVerification),
    raw: address,
  };
};

export const mapMemberProfile = (profile = {}) => ({
  id: profile.id,
  nickname: profile.nickname || "",
  name: profile.name || "",
  email: profile.email || "",
  mobile: profile.mobile || "",
  tradeId: String(profile.tradeId || "").trim(),
  areaId: profile.areaId,
  areaName: profile.areaName || "",
  sex: profile.sex,
  emailVerified: Boolean(profile.emailVerified),
  raw: profile,
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
  addressVerification: order.addressVerification || null,
  items: (order.items || []).map((item) => ({
    id: item.id,
    skuId: item.skuId,
    name: item.spuName || item.name || "Product",
    cover: item.picUrl || item.cover || "",
    count: Number(item.count) || 1,
    price: fenToYuan(item.price ?? item.payPrice),
    regularPrice: fenToYuan(item.regularPrice ?? item.originalPrice ?? item.marketPrice ?? item.price ?? item.payPrice),
    memberPrice: fenToYuan(item.memberPrice ?? item.vipPrice ?? item.price ?? item.payPrice),
    category:
      item.categoryCode ||
      item.categoryName ||
      item.productType ||
      item.spuType ||
      item.type ||
      "",
  })),
  raw: order,
});

export const mapOrderPage = (page = {}) => ({
  list: (page.list || []).map(mapOrderDetail),
  total: Number(page.total || 0),
});
