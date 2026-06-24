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
  supplementMissingCompanyTypes,
} from "../services/productListingModel.js";
import { getProductPage } from "../services/yudaoProductApi.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const loading = ref(true);
const source = ref("demo");
const products = ref(demoProducts);
const searchQuery = ref("");
const initialListingQuery = resolveProductListingQuery(typeof window === "undefined" ? "" : window.location.search);
const emptyFacetState = () => Object.fromEntries(productFacetGroups.map((group) => [group.key, "all"]));
const selectedProductType = ref(initialListingQuery.filter);
const selectedFacets = ref({ ...emptyFacetState(), ...initialListingQuery.facets });
const selectedSort = ref("featured");
const mobileFiltersOpen = ref(false);
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
const selectedFilterLabel = computed(() => {
  if (selectedProductType.value === "all") return "All furniture";
  return productTypeOptions.value.find((option) => option.value === selectedProductType.value)?.label || productListingQueryFilterLabels[selectedProductType.value] || selectedProductType.value;
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
  return [product.name, product.subtitle, product.description, product.productType, product.material, product.color]
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
    labels.push(selectedFilterLabel.value);
  }
  Object.entries(selectedFacets.value).forEach(([key, value]) => {
    if (!value || value === "all") return;
    const group = productFacetGroups.find((item) => item.key === key);
    const option = group?.options.find((item) => item.value === value);
    labels.push(option?.label || value);
  });
  return labels;
});
const money = (value) => `$${Number(value || 0).toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const isProductAvailable = (product) => Number(product.stock) > 0;
const productStockLabel = (product) => {
  const stock = Number(product.stock || 0);
  if (stock <= 0) return "Made to order";
  if (stock <= 5) return `Only ${stock} left`;
  return `${stock} ready`;
};
const resetProductListControls = () => {
  searchQuery.value = "";
  selectedProductType.value = "all";
  selectedFacets.value = emptyFacetState();
  selectedSort.value = "featured";
  mobileFiltersOpen.value = false;
};
const handleQuickAdd = (product, options) => {
  emit("add-to-cart", product, 1, options);
  quickAddMessage.value = `${product.name} added to bag`;
};
const syncListingQueryFromLocation = () => {
  const listingQuery = resolveProductListingQuery(window.location.search);
  selectedProductType.value = listingQuery.filter;
  selectedFacets.value = { ...emptyFacetState(), ...listingQuery.facets };
  mobileFiltersOpen.value = false;
};

onMounted(async () => {
  window.addEventListener("popstate", syncListingQueryFromLocation);
  window.addEventListener("oakved:navigation", syncListingQueryFromLocation);
  try {
    const page = await getProductPage({ pageNo: 1, pageSize: 24 });
    if (page.list.length > 0) {
      products.value = supplementMissingCompanyTypes(page.list, demoProducts);
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
      <button class="product-mobile-filter-toggle" type="button" @click="mobileFiltersOpen = true">
        Filter
        <span v-if="listingModel.summary.activeFacetCount">{{ listingModel.summary.activeFacetCount }}</span>
      </button>
      <p>{{ resultSummary }}</p>
    </section>

    <div class="product-filter-list" aria-label="Furniture categories">
      <button
        type="button"
        :class="{ active: selectedProductType === 'all' }"
        @click="selectedProductType = 'all'"
      >
        All
      </button>
      <button
        v-for="option in productTypeOptions"
        :key="option.value"
        type="button"
        :class="{ active: selectedProductType === option.value }"
        @click="selectedProductType = option.value"
      >
        {{ option.label }}
      </button>
    </div>

    <section class="product-facet-shell" :class="{ open: mobileFiltersOpen }" aria-label="Product filters">
      <button class="product-facet-backdrop" type="button" aria-label="Close filters" @click="mobileFiltersOpen = false"></button>
      <div class="product-facet-panel">
        <div class="product-facet-head">
          <div>
            <p class="eyebrow">Filter</p>
            <h2>{{ selectedFilterLabel }}</h2>
          </div>
          <button type="button" @click="mobileFiltersOpen = false">Close</button>
        </div>
        <label v-for="group in productFacetGroups" :key="group.key" class="product-facet-control">
          <span>{{ group.label }}</span>
          <select v-model="selectedFacets[group.key]">
            <option v-for="option in group.options" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="product-active-filters" aria-label="Active filters">
      <span v-for="label in activeFilterLabels" :key="label">{{ label }}</span>
      <button type="button" @click="resetProductListControls">Clear all</button>
    </section>

    <section class="product-list-confidence" aria-label="Catalog confidence">
      <span>{{ listingModel.summary.productCount }} matched pieces</span>
      <span>{{ listingModel.summary.collectionCount }} product groups</span>
      <span>Images, sizes and stock stay visible while browsing</span>
    </section>

    <p v-if="quickAddMessage" class="product-quick-add-status" role="status">{{ quickAddMessage }}</p>

    <div v-if="!loading && visibleProducts.length === 0" class="product-list-empty">
      <p class="eyebrow">{{ t("productList.empty.eyebrow") }}</p>
      <h2>{{ t("productList.empty.title") }}</h2>
      <p>{{ t("productList.empty.description") }}</p>
      <button type="button" @click="resetProductListControls">{{ t("productList.empty.action") }}</button>
    </div>

    <section v-else-if="!loading" class="product-grid product-grid-editorial" aria-label="Furniture products">
      <article v-for="product in visibleProducts" :key="product.skuId" class="product-card">
        <a :href="`/sofa-pdp?id=${product.id}`" class="product-card-media">
          <span class="product-card-badge">{{ product.badge || productStockLabel(product) }}</span>
          <ProductImage :src="product.cover" :hover-src="product.gallery?.[0]" :label="product.name" />
        </a>
        <div class="product-card-body">
          <div>
            <p class="eyebrow">{{ product.source }} / {{ product.material || "wood" }}</p>
            <h2>{{ product.name }}</h2>
            <p>{{ product.subtitle }}</p>
          </div>
          <dl class="product-card-meta">
            <div>
              <dt>Member</dt>
              <dd>{{ money(product.price) }}</dd>
            </div>
            <div v-if="product.marketPrice">
              <dt>Regular</dt>
              <dd>{{ money(product.marketPrice) }}</dd>
            </div>
            <div v-if="product.dimensions">
              <dt>Size</dt>
              <dd>{{ product.dimensions }}</dd>
            </div>
          </dl>
          <div class="product-card-foot">
            <strong>{{ money(product.price) }}</strong>
            <span>{{ productStockLabel(product) }}</span>
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

      <aside v-if="visibleProducts.length > 2" class="product-editorial-tile">
        <p class="eyebrow">Oakved Edit</p>
        <h2>Wood furniture for the finished bedroom</h2>
        <p>Build the room around bedside storage, bench seating, dressing tables and compact lounge pieces instead of browsing isolated SKUs.</p>
        <a href="/products?collection=bedroom-set">View bedroom sets</a>
      </aside>
    </section>
  </section>
</template>
