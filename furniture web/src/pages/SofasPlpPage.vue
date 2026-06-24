<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import { PRODUCT_SORT_OPTIONS } from "../services/productListControls.js";
import {
  buildProductListingModel,
  productFacetGroups,
  productListingFilters,
  productListingQueryFilterLabels,
  resolveProductListingQuery,
} from "../services/productListingModel.js";
import { getProductPage } from "../services/yudaoProductApi.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const loading = ref(true);
const source = ref("demo");
const products = ref(demoProducts);
const searchQuery = ref("");
const initialListingQuery = resolveProductListingQuery(typeof window === "undefined" ? "" : window.location.search);
const selectedProductType = ref(initialListingQuery.filter);
const selectedFacets = ref(initialListingQuery.facets);
const selectedSort = ref("featured");
const quickAddMessage = ref("");
const skeletonCards = [0, 1, 2, 3];

const sourceLabel = computed(() => (source.value === "yudao" ? t("connectedCatalog") : t("offlineCatalog")));
const normalizeValue = (value) => String(value ?? "").trim().toLowerCase();
const sortForListingModel = computed(() => {
  if (selectedSort.value === "priceAsc") return "price-asc";
  if (selectedSort.value === "priceDesc") return "price-desc";
  return "featured";
});
const productTypeOptions = computed(() => {
  const baseOptions = productListingFilters.filter((option) => option.value !== "all");
  const hasSelectedOption = baseOptions.some((option) => option.value === selectedProductType.value);
  if (hasSelectedOption || selectedProductType.value === "all") return baseOptions;

  return [
    {
      value: selectedProductType.value,
      label: productListingQueryFilterLabels[selectedProductType.value] || selectedProductType.value,
    },
    ...baseOptions,
  ];
});
const listingModel = computed(() =>
  buildProductListingModel(products.value, {
    filter: selectedProductType.value,
    sort: sortForListingModel.value,
    facets: selectedFacets.value,
  })
);
const productMatchesSearch = (product) => {
  const query = normalizeValue(searchQuery.value);
  if (!query) return true;
  return [product.name, product.subtitle, product.description, product.productType]
    .map(normalizeValue)
    .some((value) => value.includes(query));
};
const visibleProducts = computed(() => listingModel.value.products.filter(productMatchesSearch));
const resultSummary = computed(() =>
  t("productList.resultSummary", { visible: visibleProducts.value.length, total: products.value.length })
);
const activeFilterLabels = computed(() => {
  const labels = [`Showing ${visibleProducts.value.length} of ${products.value.length}`];
  if (searchQuery.value.trim()) labels.push(`Search: ${searchQuery.value.trim()}`);
  if (selectedProductType.value !== "all") {
    labels.push(productTypeOptions.value.find((option) => option.value === selectedProductType.value)?.label || selectedProductType.value);
  }
  Object.entries(selectedFacets.value).forEach(([key, value]) => {
    if (!value || value === "all") return;
    const group = productFacetGroups.find((item) => item.key === key);
    const option = group?.options.find((item) => item.value === value);
    labels.push(option?.label || value);
  });
  return labels;
});
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const isProductAvailable = (product) => Number(product.stock) > 0;
const resetProductListControls = () => {
  searchQuery.value = "";
  selectedProductType.value = "all";
  selectedFacets.value = {};
  selectedSort.value = "featured";
};
const handleQuickAdd = (product, options) => {
  emit("add-to-cart", product, 1, options);
  quickAddMessage.value = `${product.name} added to bag`;
};
const syncListingQueryFromLocation = () => {
  const listingQuery = resolveProductListingQuery(window.location.search);
  selectedProductType.value = listingQuery.filter;
  selectedFacets.value = listingQuery.facets;
};

onMounted(async () => {
  window.addEventListener("popstate", syncListingQueryFromLocation);
  window.addEventListener("oakved:navigation", syncListingQueryFromLocation);
  try {
    const page = await getProductPage({ pageNo: 1, pageSize: 24 });
    if (page.list.length > 0) {
      products.value = page.list;
      source.value = "yudao";
    }
  } catch {
    source.value = "demo";
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("popstate", syncListingQueryFromLocation);
  window.removeEventListener("oakved:navigation", syncListingQueryFromLocation);
});
</script>

<template>
  <section class="product-list-page">
    <header class="product-list-head">
      <p class="eyebrow">{{ sourceLabel }}</p>
      <h1>{{ t("productsTitle") }}</h1>
      <p>{{ t("productsSubtitle") }}</p>
    </header>

    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>
    <section v-if="loading" class="product-grid product-grid-skeleton" aria-hidden="true">
      <article v-for="item in skeletonCards" :key="item" class="product-card product-card-skeleton"></article>
    </section>

    <section class="product-list-toolbar" :aria-label="t('productList.controlsAria')">
      <label>
        <span>{{ t("productList.search") }}</span>
        <input v-model="searchQuery" type="search" :placeholder="t('productList.searchPlaceholder')" />
      </label>
      <label>
        <span>{{ t("productList.type") }}</span>
        <select v-model="selectedProductType">
          <option value="all">{{ t("productList.allTypes") }}</option>
          <option v-for="option in productTypeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ t("productList.sort.label") }}</span>
        <select v-model="selectedSort">
          <option v-for="option in PRODUCT_SORT_OPTIONS" :key="option.value" :value="option.value">
            {{ t(option.labelKey) }}
          </option>
        </select>
      </label>
      <p>{{ resultSummary }}</p>
    </section>
    <section class="product-active-filters" aria-label="Active filters">
      <span v-for="label in activeFilterLabels" :key="label">{{ label }}</span>
      <button type="button" @click="resetProductListControls">Clear all</button>
    </section>
    <p v-if="quickAddMessage" class="product-quick-add-status" role="status">{{ quickAddMessage }}</p>

    <div v-if="!loading && visibleProducts.length === 0" class="product-list-empty">
      <p class="eyebrow">{{ t("productList.empty.eyebrow") }}</p>
      <h2>{{ t("productList.empty.title") }}</h2>
      <p>{{ t("productList.empty.description") }}</p>
      <button type="button" @click="resetProductListControls">{{ t("productList.empty.action") }}</button>
    </div>

    <section v-else-if="!loading" class="product-grid" aria-label="Furniture products">
      <article v-for="product in visibleProducts" :key="product.skuId" class="product-card">
        <a :href="`/sofa-pdp?id=${product.id}`" class="product-card-media">
          <ProductImage :src="product.cover" :label="product.name" />
        </a>
        <div class="product-card-body">
          <div>
            <p class="eyebrow">{{ product.source }}</p>
            <h2>{{ product.name }}</h2>
            <p>{{ product.subtitle }}</p>
          </div>
          <div class="product-card-foot">
            <strong>{{ money(product.price) }}</strong>
            <span>{{ t("stock") }} {{ product.stock }}</span>
          </div>
          <div class="product-card-actions">
            <a :href="`/sofa-pdp?id=${product.id}`">{{ t("viewDetails") }}</a>
            <button
              type="button"
              :disabled="!isProductAvailable(product)"
              :aria-disabled="!isProductAvailable(product)"
              @click="handleQuickAdd(product, { trigger: $event.currentTarget })"
            >
              {{ isProductAvailable(product) ? t("addToCart") : t("product.unavailable") }}
            </button>
          </div>
          <p v-if="!isProductAvailable(product)" class="product-card-unavailable">{{ t("product.unavailableHint") }}</p>
        </div>
      </article>
    </section>
  </section>
</template>
