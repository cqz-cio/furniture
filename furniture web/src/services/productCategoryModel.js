export const PRODUCT_ROOM_CODES = ["dining-room", "living-room", "bedroom"];

const isEnabled = (category = {}) => category.status === undefined || Number(category.status) === 0;
const categoryOrder = (left, right) =>
  Number(left.sort || 0) - Number(right.sort || 0) || Number(left.id || 0) - Number(right.id || 0);

const normalizedCategory = (category = {}) => ({
  id: Number(category.id),
  parentId: Number(category.parentId || 0),
  code: String(category.code || "").trim(),
  name: String(category.name || "").trim(),
  sort: Number(category.sort || 0),
  status: Number(category.status || 0),
  children: [],
});

export const normalizeProductCategoryTree = (categories = []) => {
  const flat = [];
  const visit = (category) => {
    if (!category || typeof category !== "object") return;
    flat.push(normalizedCategory(category));
    if (Array.isArray(category.children)) category.children.forEach(visit);
  };
  (Array.isArray(categories) ? categories : []).forEach(visit);

  const byId = new Map(flat.map((category) => [category.id, category]));
  const roots = [];
  flat.forEach((category) => {
    const parent = byId.get(category.parentId);
    if (category.parentId > 0 && parent) parent.children.push(category);
    else roots.push(category);
  });
  const sortTree = (nodes) => nodes.sort(categoryOrder).map((node) => ({
    ...node,
    children: sortTree(node.children),
  }));
  return sortTree(roots);
};

/** Build the Room/P2 filter model exclusively from the public category API. */
export const buildRoomCatalog = (categories = []) => {
  const roomCodes = new Set(PRODUCT_ROOM_CODES);
  return normalizeProductCategoryTree(categories)
    .filter((room) => room.parentId === 0 && roomCodes.has(room.code) && isEnabled(room))
    .map((room) => ({
      id: room.id,
      parentId: room.parentId,
      code: room.code,
      name: room.name,
      sort: room.sort,
      productTypes: room.children
        .filter((category) => category.code && isEnabled(category))
        .map(({ children: _children, ...category }) => category),
    }));
};
