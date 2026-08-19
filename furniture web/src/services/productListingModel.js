export const productListingFilters = [
  { value: "all", label: "All" },
  { value: "sofa", label: "Sofas" },
  { value: "lounge-chair", label: "Lounge Chairs" },
  { value: "ottoman", label: "Ottomans" },
  { value: "dining-table", label: "Dining Tables" },
  { value: "dining-chair", label: "Dining Chairs" },
  { value: "coffee-table", label: "Coffee Tables" },
  { value: "bed", label: "Beds" },
  { value: "nightstand", label: "Nightstands" },
  { value: "bed-bench", label: "Benches" },
  { value: "dresser", label: "Dressers" },
  { value: "wardrobe", label: "Wardrobes" },
  { value: "vanity", label: "Vanities" },
  { value: "desk", label: "Desks" },
  { value: "round-table", label: "Round Tables" },
  { value: "side-table", label: "Side Tables" },
  { value: "media-console", label: "Media Consoles" },
  { value: "sideboard", label: "Sideboards" },
  { value: "bar-stool", label: "Bar Stools" },
  { value: "lighting", label: "Lighting" },
  { value: "rug", label: "Rugs" },
  { value: "single-sofa", label: "Single Sofas" },
  { value: "chair", label: "Chairs" },
];

export const productFacetGroups = [
  {
    key: "material",
    label: "Material",
    options: [
      { value: "all", label: "All materials" },
      { value: "fabric", label: "Fabric" },
      { value: "leather", label: "Leather" },
      { value: "wood", label: "Wood" },
      { value: "glass", label: "Glass" },
      { value: "stone", label: "Stone" },
      { value: "metal", label: "Metal" },
    ],
  },
  {
    key: "color",
    label: "Color",
    options: [
      { value: "all", label: "All colors" },
      { value: "natural", label: "Natural" },
      { value: "brown", label: "Brown" },
      { value: "light", label: "Light" },
      { value: "black", label: "Black" },
      { value: "grey", label: "Grey" },
    ],
  },
  {
    key: "availability",
    label: "Availability",
    options: [
      { value: "all", label: "All availability" },
      { value: "in-stock", label: "In stock" },
      { value: "low-stock", label: "Low stock" },
    ],
  },
  {
    key: "price",
    label: "Price",
    options: [
      { value: "all", label: "All prices" },
      { value: "under-1500", label: "Under $1,500" },
      { value: "1500-3500", label: "$1,500-$3,500" },
      { value: "over-3500", label: "Over $3,500" },
    ],
  },
];

const sortProducts = (products, sort) => {
  const nextProducts = [...products];
  if (sort === "price-asc") {
    return nextProducts.sort((a, b) => Number(a.price || 0) - Number(b.price || 0));
  }
  if (sort === "price-desc") {
    return nextProducts.sort((a, b) => Number(b.price || 0) - Number(a.price || 0));
  }
  return nextProducts;
};

const categoryFilterAliases = {
  sofas: "sofa",
  "single-sofa": "sofa",
  "accent-chair": "lounge-chair",
  "vanity-chair": "lounge-chair",
  "desk-chair": "chair",
  "rectangular-table": "dining-table",
  "round-oval-table": "dining-table",
  "bistro-table": "dining-table",
  "fabric-chair": "dining-chair",
  "wood-woven-chair": "dining-chair",
  "bar-counter-stool": "bar-stool",
  stool: "bar-stool",
  console: "media-console",
  cabinet: "storage",
  headboard: "bed",
  decor: "decor",
  "drawer-chest": "dresser",
  "side-cabinet": "dresser",
  "tall-cabinet": "wardrobe",
  bench: "bed-bench",
};

const productTypeGroupFilters = {
  sofa: ["sofa", "single-sofa"],
  "single-sofa": ["sofa", "single-sofa"],
  chair: ["chair", "lounge-chair", "dining-chair"],
  "round-table": ["round-table", "dining-table"],
  table: ["coffee-table", "side-table"],
  storage: ["nightstand", "dresser", "wardrobe", "media-console", "sideboard"],
  "desk-table": ["desk", "vanity", "round-table", "dining-table", "coffee-table", "side-table"],
  seating: ["sofa", "single-sofa", "lounge-chair", "dining-chair", "chair", "ottoman", "bar-stool", "bed-bench"],
  "bedroom-set": ["bed", "nightstand", "dresser", "wardrobe", "vanity", "side-table", "bed-bench", "lounge-chair", "lighting"],
  "storage-set": ["nightstand", "dresser", "wardrobe"],
  "study-set": ["desk", "vanity", "side-table", "chair", "lounge-chair", "lighting"],
  "bedroom-room": ["bed", "nightstand", "dresser", "wardrobe", "vanity", "desk", "side-table", "bed-bench", "lounge-chair", "single-sofa", "chair", "round-table", "lighting", "rug"],
  "master-bedroom": ["bed", "nightstand", "dresser", "wardrobe", "vanity", "side-table", "bed-bench", "lounge-chair", "single-sofa", "chair", "round-table", "lighting", "rug"],
  "guest-bedroom": ["bed", "nightstand", "dresser", "wardrobe", "side-table", "lounge-chair", "single-sofa", "chair", "lighting", "rug"],
  room: ["bed", "nightstand", "dresser", "wardrobe", "vanity", "desk", "sofa", "lounge-chair", "dining-table", "dining-chair", "coffee-table", "side-table", "lighting", "rug"],
  study: ["desk", "side-table", "chair", "lounge-chair", "lighting", "rug"],
  living: ["sofa", "single-sofa", "lounge-chair", "chair", "ottoman", "coffee-table", "side-table", "round-table", "media-console", "sideboard", "lighting", "rug"],
  dining: ["dining-table", "round-table", "dining-chair", "bar-stool", "sideboard", "lighting", "rug"],
  decor: ["lighting", "rug"],
};

export const productListingQueryFilters = Object.keys(productTypeGroupFilters);

export const productListingQueryFilterLabels = {
  storage: "Storage Cabinets",
  "desk-table": "Desks & Tables",
  seating: "Seating & Benches",
  "bedroom-set": "Bedroom Sets",
  "storage-set": "Bedroom Storage",
  "study-set": "Study Sets",
  "bedroom-room": "Bedroom Furniture",
  "master-bedroom": "Master Bedroom",
  "guest-bedroom": "Guest Bedroom",
  room: "Complete Rooms",
  study: "Study Rooms",
  living: "Living Corners",
  dining: "Dining Furniture",
  decor: "Rugs & Lighting",
};

const materialFacetAliases = {
  fabric: { material: "fabric" },
  leather: { material: "leather" },
  wood: { material: "wood" },
  glass: { material: "glass" },
  metal: { material: "metal" },
  walnut: { material: "wood", color: "brown" },
  oak: { material: "wood", color: "natural" },
  cherry: { material: "wood", color: "brown" },
  ash: { material: "wood", color: "natural" },
};

const collectionFilterAliases = {
  "bedroom-set": "bedroom-set",
  "storage-set": "storage-set",
  "study-set": "study-set",
  "bedroom-room": "bedroom-room",
  "master-bedroom": "master-bedroom",
  "guest-bedroom": "guest-bedroom",
  room: "room",
};

export const resolveProductListingQuery = (search = "") => {
  const params = new URLSearchParams(String(search).replace(/^\?/, ""));
  const facets = {};
  let filter = "all";

  const category = params.get("category");
  if (category) {
    filter = categoryFilterAliases[category] || String(category).trim().toLowerCase().replace(/\s+/g, "-");
  }

  const collection = params.get("collection");
  if (collection) {
    filter = collectionFilterAliases[collection] || "all";
  }

  const room = params.get("room");
  if (room === "study" || room === "living" || room === "dining") {
    filter = room;
  } else if (room === "bedroom") {
    filter = "bedroom-room";
  }

  const material = params.get("material");
  if (material) {
    Object.assign(facets, materialFacetAliases[material] || { material: String(material).trim().toLowerCase() });
  }

  const tag = params.get("tag");
  if (tag === "in-stock") {
    facets.availability = "in-stock";
  }

  return { filter, facets, tag: tag || "" };
};

export const inferListingType = (product = {}) => {
  if (product.source === "yudao") {
    const stableCode = String(product.categoryCode || product.productType || "").trim().toLowerCase();
    return stableCode || "uncategorized";
  }
  const rawType = String(product.productType || product.type || product.categoryName || product.category || "").toLowerCase();
  const rawSlug = rawType.trim().replace(/[\s_]+/g, "-");
  const knownTypes = new Set(productListingFilters.map((filter) => filter.value).filter((value) => value !== "all"));
  if (knownTypes.has(rawSlug)) return rawSlug;
  const categoryTypeAliases = {
    sofas: "sofa",
    "lounge-chairs": "lounge-chair",
    ottomans: "ottoman",
    "dining-tables": "dining-table",
    "dining-chairs": "dining-chair",
    "coffee-tables": "coffee-table",
    beds: "bed",
    desks: "desk",
    rugs: "rug",
    wardrobes: "wardrobe",
    "side-tables": "side-table",
    lighting: "lighting",
    "bar-stools": "bar-stool",
  };
  if (categoryTypeAliases[rawSlug]) return categoryTypeAliases[rawSlug];
  const text = `${rawType} ${product.name || ""} ${product.subtitle || ""}`.toLowerCase();

  if (text.includes("sideboard") || text.includes("餐边柜")) return "sideboard";
  if (text.includes("media console") || text.includes("电视柜") || text.includes("媒体柜")) return "media-console";
  if (text.includes("bar stool") || text.includes("counter stool") || text.includes("吧椅")) return "bar-stool";
  if (text.includes("coffee table") || text.includes("茶几")) return "coffee-table";
  if (text.includes("side table") || text.includes("边桌") || text.includes("角几")) return "side-table";
  if (text.includes("dining table") || text.includes("餐桌")) return "dining-table";
  if ((text.includes("round") && text.includes("table")) || text.includes("圆桌")) return "round-table";
  if (text.includes("dining chair") || text.includes("餐椅")) return "dining-chair";
  if (text.includes("nightstand") || text.includes("bedside") || text.includes("床头柜") || text.includes("床边柜")) {
    return "nightstand";
  }
  if (text.includes("bench") || text.includes("床尾长凳") || text.includes("床凳") || text.includes("长凳")) {
    return "bed-bench";
  }
  if (
    text.includes("dresser") ||
    text.includes("chest") ||
    text.includes("cabinet") ||
    text.includes("斗柜") ||
    text.includes("抽屉柜") ||
    text.includes("收纳柜")
  ) {
    return "dresser";
  }
  if (text.includes("wardrobe") || text.includes("衣柜")) return "wardrobe";
  if (text.includes("vanity") || text.includes("化妆桌") || text.includes("梳妆台")) return "vanity";
  if (text.includes("desk") || text.includes("书桌") || text.includes("写字桌")) return "desk";
  if (text.includes("single-sofa") || text.includes("single sofa") || text.includes("单人座沙发") || text.includes("单人沙发")) return "single-sofa";
  if (text.includes("sofa") || text.includes("沙发")) return "sofa";
  if (text.includes("lounge chair") || text.includes("accent chair") || text.includes("休闲椅")) return "lounge-chair";
  if (text.includes("ottoman") || text.includes("脚凳") || text.includes("脚踏")) return "ottoman";
  if (text.includes("pendant") || text.includes("lamp") || text.includes("lighting") || text.includes("灯")) return "lighting";
  if (text.includes("rug") || text.includes("地毯")) return "rug";
  if (text.includes("bed") || text.includes("床")) return "bed";
  if (text.includes("chair") || text.includes("椅子") || text.includes("座椅") || text.includes("椅")) return "chair";
  return product.productType || "uncategorized";
};

export const canSupplementListingWithFallbackProducts = (env = import.meta.env) => !env?.PROD;

export const supplementMissingCompanyTypes = (liveProducts = [], fallbackProducts = [], options = {}) => {
  const env = options.env ?? import.meta.env;
  if (!canSupplementListingWithFallbackProducts(env)) {
    return liveProducts;
  }

  const liveTypes = new Set(liveProducts.map((product) => inferListingType(product)));
  const missingFallbackProducts = fallbackProducts.filter((product) => !liveTypes.has(product.productType));
  return [...liveProducts, ...missingFallbackProducts];
};

const matchesFacet = (product, key, value) => {
  if (!value || value === "all") return true;

  if (key === "availability") {
    const stock = Number(product.stock || 0);
    if (value === "in-stock") return stock > 0;
    if (value === "low-stock") return stock > 0 && stock <= 8;
  }

  if (key === "price") {
    const price = Number(product.price || 0);
    if (value === "under-1500") return price < 1500;
    if (value === "1500-3500") return price >= 1500 && price <= 3500;
    if (value === "over-3500") return price > 3500;
  }

  return String(product[key] || "").toLowerCase() === String(value).toLowerCase();
};

const activeFacetCount = (facets = {}) => Object.values(facets).filter((value) => value && value !== "all").length;

export const buildProductListingModel = (products = [], options = {}) => {
  const filter = options.filter || "all";
  const sort = options.sort || "featured";
  const facets = options.facets || {};
  const tag = options.tag || "";
  const allowedTypes = new Set(Array.isArray(options.allowedTypes) ? options.allowedTypes : []);
  const filterGroup = productTypeGroupFilters[filter] || null;
  const filteredProducts = products.filter((product) => {
    const listingType = inferListingType(product);
    const matchesRoom = allowedTypes.size === 0 || allowedTypes.has(listingType);
    const matchesType = filter === "all" || (filterGroup ? filterGroup.includes(listingType) : listingType === filter);
    const matchesFacets = Object.entries(facets).every(([key, value]) => matchesFacet(product, key, value));
    const matchesTag = tag === "new" ? product.isNew === true : tag === "best-seller" ? product.isBestSeller === true : true;
    return matchesRoom && matchesType && matchesFacets && matchesTag;
  });
  const sortedProducts = sortProducts(filteredProducts, sort);
  const collectionCount = new Set(sortedProducts.map((product) => inferListingType(product))).size;

  return {
    products: sortedProducts,
    summary: {
      productCount: sortedProducts.length,
      collectionCount,
      heroImage: "/assets/generated-furniture/home-module-002-bedroom-desktop.webp",
      activeFacetCount: activeFacetCount(facets),
    },
  };
};
