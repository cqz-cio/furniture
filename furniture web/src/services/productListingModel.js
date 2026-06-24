export const productListingFilters = [
  { value: "all", label: "All" },
  { value: "nightstand", label: "Nightstands" },
  { value: "bed-bench", label: "Benches" },
  { value: "dresser", label: "Dressers" },
  { value: "vanity", label: "Vanities" },
  { value: "desk", label: "Desks" },
  { value: "round-table", label: "Round Tables" },
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
      { value: "wood", label: "Wood" },
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
  "drawer-chest": "dresser",
  "side-cabinet": "dresser",
  "tall-cabinet": "dresser",
  "desk-chair": "chair",
  "accent-chair": "chair",
  "vanity-chair": "chair",
  bench: "bed-bench",
};

const productTypeGroupFilters = {
  storage: ["nightstand", "dresser"],
  "desk-table": ["desk", "vanity", "round-table"],
  seating: ["single-sofa", "chair", "bed-bench"],
  "bedroom-set": ["nightstand", "dresser", "vanity", "bed-bench"],
  "storage-set": ["nightstand", "dresser"],
  "study-set": ["desk", "vanity", "round-table", "chair"],
  "bedroom-room": ["nightstand", "dresser", "vanity", "desk", "bed-bench", "single-sofa", "chair", "round-table"],
  "master-bedroom": ["nightstand", "dresser", "vanity", "desk", "bed-bench", "single-sofa", "chair", "round-table"],
  "guest-bedroom": ["nightstand", "dresser", "bed-bench", "single-sofa", "chair"],
  room: ["nightstand", "dresser", "vanity", "desk", "bed-bench", "single-sofa", "chair", "round-table"],
  study: ["desk", "vanity", "round-table", "chair"],
  living: ["single-sofa", "chair", "round-table"],
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
};

const materialFacetAliases = {
  wood: { material: "wood" },
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
  if (room === "study" || room === "living") {
    filter = room;
  } else if (room === "bedroom") {
    filter = "bedroom-room";
  }

  const material = params.get("material");
  if (material) {
    Object.assign(facets, materialFacetAliases[material] || { material: "wood" });
  }

  const tag = params.get("tag");
  if (tag === "in-stock") {
    facets.availability = "in-stock";
  } else if (tag === "new") {
    filter = "desk";
  } else if (tag === "best-seller") {
    filter = "nightstand";
  }

  return { filter, facets };
};

export const inferListingType = (product = {}) => {
  const rawType = String(product.productType || product.type || product.category || "").toLowerCase();
  const knownTypes = new Set(productListingFilters.map((filter) => filter.value).filter((value) => value !== "all"));
  if (knownTypes.has(rawType)) return rawType;
  const text = `${rawType} ${product.name || ""} ${product.subtitle || ""}`.toLowerCase();

  if (text.includes("round") && text.includes("table")) return "round-table";
  if (text.includes("nightstand") || text.includes("bedside")) return "nightstand";
  if (text.includes("bench")) return "bed-bench";
  if (text.includes("dresser") || text.includes("chest") || text.includes("cabinet")) return "dresser";
  if (text.includes("vanity")) return "vanity";
  if (text.includes("desk")) return "desk";
  if (text.includes("single-sofa") || text.includes("single sofa") || text.includes("sofa")) return "single-sofa";
  if (text.includes("chair")) return "chair";
  return product.productType || "uncategorized";
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
  const filterGroup = productTypeGroupFilters[filter] || null;
  const filteredProducts = products.filter((product) => {
    const listingType = inferListingType(product);
    const matchesType = filter === "all" || (filterGroup ? filterGroup.includes(listingType) : listingType === filter);
    const matchesFacets = Object.entries(facets).every(([key, value]) => matchesFacet(product, key, value));
    return matchesType && matchesFacets;
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
