export const PRODUCT_SORT_OPTIONS = [
  { value: "featured", labelKey: "productList.sort.featured" },
  { value: "priceAsc", labelKey: "productList.sort.priceAsc" },
  { value: "priceDesc", labelKey: "productList.sort.priceDesc" },
];

const normalizeValue = (value) => String(value ?? "").trim().toLowerCase();

export const normalizeProductTypeLabel = (productType) =>
  String(productType ?? "")
    .split("-")
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");

export const buildProductTypeOptions = (products) =>
  [...new Set(products.map((product) => product.productType).filter(Boolean))]
    .sort((a, b) => normalizeProductTypeLabel(a).localeCompare(normalizeProductTypeLabel(b)))
    .map((productType) => ({
      value: productType,
      label: normalizeProductTypeLabel(productType),
    }));

const matchesQuery = (product, query) => {
  const normalizedQuery = normalizeValue(query);
  if (!normalizedQuery) return true;

  return [product.name, product.subtitle, product.description, product.productType]
    .map(normalizeValue)
    .some((value) => value.includes(normalizedQuery));
};

const matchesProductType = (product, productType) => productType === "all" || product.productType === productType;

export const applyProductListControls = (products, controls) => {
  const query = controls?.query ?? "";
  const productType = controls?.productType || "all";
  const sort = controls?.sort || "featured";

  const filteredProducts = products.filter(
    (product) => matchesQuery(product, query) && matchesProductType(product, productType)
  );

  if (sort === "priceAsc") {
    return [...filteredProducts].sort((a, b) => Number(a.price) - Number(b.price));
  }

  if (sort === "priceDesc") {
    return [...filteredProducts].sort((a, b) => Number(b.price) - Number(a.price));
  }

  return filteredProducts;
};
