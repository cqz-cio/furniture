export const LOCAL_WISHLIST_STORAGE_KEY = "furniture-web-wishlist";

const normalizeWishlistItem = (item = {}) => ({
  id: item.id,
  skuId: item.skuId || item.id,
  name: item.name,
  subtitle: item.subtitle || item.introduction || "",
  price: Number(item.price) || 0,
  marketPrice: Number(item.marketPrice) || 0,
  cover: item.cover || item.picUrl || "",
  delivery: item.delivery || item.detailConfig?.delivery || "",
  dimensions: item.dimensions || item.width || item.detailConfig?.dimensions || "",
  color: item.color || "",
  fabric: item.fabric || "",
  source: item.source || "local",
  quantity: Math.max(1, Math.floor(Number(item.quantity) || 1)),
});

export const readLocalWishlist = (storage = globalThis.localStorage) => {
  if (!storage) return [];
  try {
    const parsed = JSON.parse(storage.getItem(LOCAL_WISHLIST_STORAGE_KEY) || "[]");
    return Array.isArray(parsed) ? parsed.map(normalizeWishlistItem).filter((item) => item.skuId && item.name) : [];
  } catch {
    return [];
  }
};

export const writeLocalWishlist = (items, storage = globalThis.localStorage) => {
  if (!storage) return;
  storage.setItem(LOCAL_WISHLIST_STORAGE_KEY, JSON.stringify(items));
};

export const clearLocalWishlist = (storage = globalThis.localStorage) => {
  if (!storage) return;
  storage.removeItem(LOCAL_WISHLIST_STORAGE_KEY);
};

export const addLocalWishlistItem = (item, storage = globalThis.localStorage) => {
  const wishlistItem = normalizeWishlistItem(item);
  const items = readLocalWishlist(storage);

  if (items.some((existing) => existing.skuId === wishlistItem.skuId)) {
    return { items, added: false };
  }

  const nextItems = [...items, wishlistItem];
  writeLocalWishlist(nextItems, storage);
  return { items: nextItems, added: true };
};

export const updateLocalWishlistItemQuantity = (skuId, quantity, storage = globalThis.localStorage) => {
  const nextQuantity = Math.max(1, Math.floor(Number(quantity) || 1));
  const nextItems = readLocalWishlist(storage).map((item) =>
    item.skuId === skuId ? { ...item, quantity: nextQuantity } : item,
  );
  writeLocalWishlist(nextItems, storage);
  return nextItems;
};

export const removeLocalWishlistItem = (skuId, storage = globalThis.localStorage) => {
  const nextItems = readLocalWishlist(storage).filter((item) => item.skuId !== skuId);
  writeLocalWishlist(nextItems, storage);
  return nextItems;
};
