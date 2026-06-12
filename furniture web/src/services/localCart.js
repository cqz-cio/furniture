export const CART_STORAGE_KEY = "furniture-web-cart";

export const normalizeCartQuantity = (quantity) => {
  const nextQuantity = Math.floor(Number(quantity));
  return Number.isFinite(nextQuantity) && nextQuantity >= 1 ? nextQuantity : 1;
};

export const addLocalCartItem = (items, product, quantity = 1) => {
  const skuId = product.skuId || product.id;
  const nextQuantity = normalizeCartQuantity(quantity);
  const existing = items.find((item) => item.skuId === skuId);

  if (existing) {
    return items.map((item) =>
      item.skuId === skuId ? { ...item, quantity: item.quantity + nextQuantity } : item,
    );
  }

  return [
    ...items,
    {
      id: product.id,
      skuId,
      cartId: product.cartId,
      name: product.name,
      subtitle: product.subtitle || product.introduction || "",
      price: Number(product.price) || 0,
      cover: product.cover || product.picUrl || "",
      quantity: nextQuantity,
      source: product.source || "local",
    },
  ];
};

export const updateLocalCartItemQuantity = (items, skuId, quantity) => {
  const nextQuantity = normalizeCartQuantity(quantity);
  return items.map((item) => (item.skuId === skuId ? { ...item, quantity: nextQuantity } : item));
};

export const removeLocalCartItem = (items, skuId) => items.filter((item) => item.skuId !== skuId);

export const getCartTotals = (items) => {
  const quantity = items.reduce((sum, item) => sum + item.quantity, 0);
  const subtotal = items.reduce((sum, item) => sum + item.quantity * item.price, 0);
  return { quantity, subtotal };
};

export const readLocalCart = (storage = globalThis.localStorage) => {
  if (!storage) return [];
  try {
    const parsed = JSON.parse(storage.getItem(CART_STORAGE_KEY) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

export const writeLocalCart = (items, storage = globalThis.localStorage) => {
  if (!storage) return;
  storage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
};
