import { mapFavoritePageToItems } from "./yudaoMappers.js";
import { isYudaoBusinessError, requestYudao } from "./yudaoRequest.js";

const favoriteSpuId = (item) => item?.spuId || item?.id;
const fenAmount = (value) => {
  const amount = Number(value);
  return Number.isFinite(amount) ? Math.round(amount * 100) : undefined;
};
const compactPayload = (payload) =>
  Object.fromEntries(Object.entries(payload).filter(([, value]) => value !== undefined && value !== ""));

const mapFavoriteIdentityPayload = (itemOrSpuId) => {
  if (typeof itemOrSpuId === "number" || typeof itemOrSpuId === "string") {
    return { spuId: Number(itemOrSpuId) };
  }

  const item = itemOrSpuId || {};
  return compactPayload({
    spuId: favoriteSpuId(item),
    skuId: item.skuId,
  });
};

export const mapWishlistItemToFavoritePayload = (itemOrSpuId) => {
  if (typeof itemOrSpuId === "number" || typeof itemOrSpuId === "string") {
    return { spuId: Number(itemOrSpuId) };
  }

  const item = itemOrSpuId || {};
  return compactPayload({
    spuId: favoriteSpuId(item),
    skuId: item.skuId,
    count: Math.max(1, Math.floor(Number(item.quantity ?? item.count) || 1)),
    spuName: item.name || item.spuName,
    picUrl: item.cover || item.picUrl,
    price: fenAmount(item.price),
    marketPrice: fenAmount(item.marketPrice),
    color: item.color,
    fabric: item.fabric || item.subtitle,
    width: item.width,
    dimensions: item.dimensions,
    delivery: item.delivery,
  });
};

const buildFavoriteSearch = (params = {}) => {
  const search = new URLSearchParams({
    pageNo: "1",
    pageSize: "50",
    ...Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== "")),
  });
  return search.toString();
};

export const createFavorite = (itemOrSpuId, options = {}) =>
  requestYudao("/product/favorite/create", {
    ...options,
    method: "POST",
    body: JSON.stringify(mapWishlistItemToFavoritePayload(itemOrSpuId)),
  });

export const deleteFavorite = (itemOrSpuId, options = {}) =>
  requestYudao("/product/favorite/delete", {
    ...options,
    method: "DELETE",
    body: JSON.stringify(mapFavoriteIdentityPayload(itemOrSpuId)),
  });

export const updateFavoriteCount = (item, count, options = {}) =>
  requestYudao("/product/favorite/update-count", {
    ...options,
    method: "PUT",
    body: JSON.stringify({
      spuId: favoriteSpuId(item),
      skuId: item?.skuId,
      count: Math.max(1, Math.floor(Number(count) || 1)),
    }),
  });

export const getFavoriteExists = (spuId, options = {}) =>
  requestYudao(`/product/favorite/exits?spuId=${encodeURIComponent(spuId)}`, options);

export const getFavoriteCount = (options = {}) => requestYudao("/product/favorite/get-count", options);

export const getRemoteWishlistItems = async (params = {}, options = {}) => {
  const data = await requestYudao(`/product/favorite/page?${buildFavoriteSearch(params)}`, options);
  return mapFavoritePageToItems(data);
};

export const syncLocalWishlistToRemote = async (items = [], options = {}) => {
  const uniqueItems = [...new Map(items.filter(favoriteSpuId).map((item) => [favoriteSpuId(item), item])).values()];
  let succeeded = 0;
  const failedItems = [];

  for (const item of uniqueItems) {
    try {
      await createFavorite(item, options);
      succeeded += 1;
    } catch (error) {
      if (!isYudaoBusinessError(error)) throw error;
      failedItems.push(item);
    }
  }

  return { attempted: uniqueItems.length, succeeded, failedItems };
};
