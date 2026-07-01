import { readLocalWishlist } from "./localWishlist.js";
import { getRemoteWishlistItems } from "./yudaoFavoriteApi.js";
import { readYudaoToken } from "./yudaoRequest.js";

const identity = (item = {}) => ({
  spuId: item.spuId || item.id,
  skuId: item.skuId || item.id,
});

const identityKeys = (item = {}) => {
  const { spuId, skuId } = identity(item);
  return [
    spuId && skuId ? `pair:${spuId}:${skuId}` : "",
    skuId ? `sku:${skuId}` : "",
    spuId ? `spu:${spuId}` : "",
  ].filter(Boolean);
};

export const createWishlistIdentitySet = (items = []) =>
  new Set(items.flatMap((item) => identityKeys(item)));

export const isWishlistItemSaved = (item, keys = new Set()) =>
  identityKeys(item).some((key) => keys.has(key));

export const withWishlistItemSaved = (keys = new Set(), item) => {
  const nextKeys = new Set(keys);
  identityKeys(item).forEach((key) => nextKeys.add(key));
  return nextKeys;
};

export const loadWishlistIdentityState = async (options = {}) => {
  if (readYudaoToken(options.storage)) {
    try {
      const page = await getRemoteWishlistItems({ pageNo: 1, pageSize: 200 }, options);
      return { source: "yudao", keys: createWishlistIdentitySet(page.list) };
    } catch {
      return { source: "local", keys: createWishlistIdentitySet(readLocalWishlist(options.storage)) };
    }
  }

  return { source: "local", keys: createWishlistIdentitySet(readLocalWishlist(options.storage)) };
};
