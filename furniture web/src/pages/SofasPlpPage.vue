<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import {
  buildProductListingModel,
  inferListingType,
  productFacetGroups,
  productListingFilters,
  productListingQueryFilterLabels,
  productListingQueryFilters,
  resolveProductListingQuery,
} from "../services/productListingModel.js";
import { getProductPage } from "../services/yudaoClient.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const loading = ref(true);
const source = ref("demo");
const products = ref(demoProducts);
const selectedFilter = ref("all");
const selectedSort = ref("featured");
const mobileFiltersOpen = ref(false);
const selectedFacets = ref(Object.fromEntries(productFacetGroups.map((group) => [group.key, "all"])));
const quickAddMessage = ref("");
const skeletonCards = Array.from({ length: 6 }, (_, index) => index);

const sourceLabel = computed(() => (source.value === "yudao" ? "Oakved collection" : "Oakved catalog"));
const productEyebrow = (product) => product.badge || (product.material ? `${product.material} furniture` : sourceLabel.value);
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const listingModel = computed(() =>
  buildProductListingModel(products.value, {
    filter: selectedFilter.value,
    sort: selectedSort.value,
    facets: selectedFacets.value,
  }),
);
const displayedProducts = computed(() => listingModel.value.products);
const summary = computed(() => listingModel.value.summary);
const selectedFilterLabel = computed(
  () =>
    productListingFilters.find((filter) => filter.value === selectedFilter.value)?.label ||
    productListingQueryFilterLabels[selectedFilter.value] ||
    "All",
);
const activeFilterLabels = computed(() =>
  [
    selectedFilter.value !== "all" ? selectedFilterLabel.value : null,
    ...productFacetGroups.map((group) => {
      const selectedValue = selectedFacets.value[group.key];
      if (!selectedValue || selectedValue === "all") return null;
      const selectedOption = group.options.find((option) => option.value === selectedValue);
      return `${group.label}: ${selectedOption?.label || selectedValue}`;
    }),
  ].filter(Boolean),
);

const resetFilters = () => {
  selectedFilter.value = "all";
  selectedFacets.value = Object.fromEntries(productFacetGroups.map((group) => [group.key, "all"]));
};

const applyQueryFilters = () => {
  const { filter, facets } = resolveProductListingQuery(window.location.search);
  const validFilters = new Set([...productListingFilters.map((item) => item.value), ...productListingQueryFilters]);
  selectedFilter.value = validFilters.has(filter) ? filter : "all";
  selectedFacets.value = {
    ...Object.fromEntries(productFacetGroups.map((group) => [group.key, "all"])),
    ...facets,
  };
};

const productStockLabel = (product) => {
  const stock = Number(product.stock || 0);
  if (stock <= 0) return "Made to order";
  if (stock <= 8) return `Only ${stock} left`;
  return "In stock";
};

const addProductFromListing = (product) => {
  quickAddMessage.value = `${product.name} added to bag`;
  emit("add-to-cart", product, 1);
};

const supplementMissingCompanyTypes = (liveProducts = []) => {
  const liveTypes = new Set(liveProducts.map((product) => inferListingType(product)));
  const fallbackProducts = demoProducts.filter((product) => !liveTypes.has(product.productType));
  return [...liveProducts, ...fallbackProducts];
};

onMounted(async () => {
  applyQueryFilters();
  window.addEventListener("popstate", applyQueryFilters);
  window.addEventListener("oakved:navigation", applyQueryFilters);
  try {
    const page = await getProductPage({ pageNo: 1, pageSize: 24 });
    if (page.list.length > 0) {
      products.value = supplementMissingCompanyTypes(page.list);
      source.value = "yudao";
    }
  } catch {
    source.value = "demo";
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("popstate", applyQueryFilters);
  window.removeEventListener("oakved:navigation", applyQueryFilters);
});
</script>

<template>
  <section class="product-list-page">
    <header class="product-list-head">
      <p class="eyebrow">{{ sourceLabel }}</p>
      <h1>{{ t("productsTitle") }}</h1>
      <p>{{ t("productsSubtitle") }}</p>
    </header>

    <section class="product-list-toolbar" aria-label="Product listing controls">
      <div>
        <span>{{ summary.productCount }} pieces</span>
        <span>{{ summary.collectionCount }} collections</span>
      </div>
      <div class="product-filter-list" aria-label="Filter by room or type">
        <button
          v-for="filter in productListingFilters"
          :key="filter.value"
          type="button"
          :class="{ active: selectedFilter === filter.value }"
          @click="selectedFilter = filter.value"
        >
          {{ filter.label }}
        </button>
      </div>
      <label class="product-sort-control">
        Sort
        <select v-model="selectedSort">
          <option value="featured">Featured</option>
          <option value="price-asc">Price low to high</option>
          <option value="price-desc">Price high to low</option>
        </select>
      </label>
      <button class="product-mobile-filter-toggle" type="button" @click="mobileFiltersOpen = true">
        Filter
        <span v-if="summary.activeFacetCount">{{ summary.activeFacetCount }}</span>
      </button>
    </section>

    <section class="product-facet-shell" :class="{ open: mobileFiltersOpen }" aria-label="Product filters">
      <button class="product-facet-backdrop" type="button" aria-label="Close filters" @click="mobileFiltersOpen = false"></button>
      <aside class="product-facet-panel">
        <div class="product-facet-head">
          <div>
            <p class="eyebrow">Refine</p>
            <h2>Filter furniture</h2>
          </div>
          <button type="button" @click="mobileFiltersOpen = false">Close</button>
        </div>
        <div class="product-facet-groups">
          <label v-for="group in productFacetGroups" :key="group.key">
            {{ group.label }}
            <select v-model="selectedFacets[group.key]">
              <option v-for="option in group.options" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
        <div class="product-facet-actions">
          <button type="button" @click="resetFilters">Clear filters</button>
          <button type="button" @click="mobileFiltersOpen = false">Show {{ summary.productCount }} pieces</button>
        </div>
      </aside>
    </section>

    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>

    <section v-if="loading" class="product-grid product-grid-skeleton" aria-label="Loading products">
      <article v-for="index in skeletonCards" :key="index" class="product-card product-card-skeleton">
        <span></span>
        <span></span>
        <span></span>
      </article>
    </section>

    <p v-if="quickAddMessage" class="product-quick-add-status" role="status">{{ quickAddMessage }}</p>

    <section v-if="activeFilterLabels.length" class="product-active-filters" aria-label="Active product filters">
      <p>Showing {{ summary.productCount }} pieces for</p>
      <div>
        <span v-for="label in activeFilterLabels" :key="label">{{ label }}</span>
      </div>
      <button type="button" @click="resetFilters">Clear all</button>
    </section>

     <section class="product-list-confidence" aria-label="Shopping confidence">
      <span>Member pricing shown</span>
      <span>Delivery window included</span>
      <span>Material and finish filters</span>
    </section>

    <section v-if="!loading" class="product-grid" aria-label="Furniture products">
      <article v-for="product in displayedProducts" :key="product.skuId" class="product-card">
        <a :href="`/product?id=${product.id}`" class="product-card-media">
          <span v-if="product.badge" class="product-card-badge">{{ product.badge }}</span>
          <ProductImage :src="product.cover" :hover-src="product.gallery?.[0]" :label="product.name" />
        </a>
        <div class="product-card-body">
          <div>
            <p class="eyebrow">{{ productEyebrow(product) }}</p>
            <h2>{{ product.name }}</h2>
            <p>{{ product.subtitle }}</p>
          </div>
          <div class="product-card-foot">
            <strong>{{ money(product.price) }} Member</strong>
            <span v-if="product.marketPrice">{{ money(product.marketPrice) }} Regular</span>
            <span>{{ productStockLabel(product) }}</span>
            <span v-if="product.dimensions">{{ product.dimensions }}</span>
          </div>
          <div class="product-card-actions">
            <a :href="`/product?id=${product.id}`">{{ t("viewDetails") }}</a>
            <button type="button" @click="addProductFromListing(product)">{{ t("addToCart") }}</button>
          </div>
        </div>
      </article>
      <aside v-if="displayedProducts.length > 2" class="product-editorial-tile">
        <img :src="summary.heroImage" alt="Styled furniture room inspiration" />
        <div>
          <p class="eyebrow">Room inspiration</p>
          <h2>Build the room around texture, proportion and quiet materials.</h2>
          <a href="/product?id=1001">Explore the edit</a>
        </div>
      </aside>
      <article v-if="!loading && displayedProducts.length === 0" class="product-empty-state">
        <p class="eyebrow">No results</p>
        <h2>No pieces match those filters.</h2>
        <p>Clear filters or browse the full collection to keep exploring.</p>
        <button type="button" @click="resetFilters">Clear filters</button>
      </article>
    </section>
  </section>
</template>
