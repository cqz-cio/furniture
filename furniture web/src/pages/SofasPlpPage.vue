<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { useI18n } from "../i18n.js";
import { PRODUCT_SORT_OPTIONS } from "../services/productListControls.js";
import {
  buildProductListingModel,
  inferListingType,
  productFacetGroups,
  productListingFilters,
  resolveProductListingQuery,
} from "../services/productListingModel.js";
import {
  isWishlistItemSaved,
  loadWishlistIdentityState,
  withWishlistItemSaved,
} from "../services/wishlistState.js";
import { getAllProducts } from "../services/yudaoProductApi.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
  tenantId: {
    type: String,
    default: "",
  },
});
const emit = defineEmits(["add-to-cart", "add-to-wishlist"]);
const { t } = useI18n();

const loading = ref(true);
const source = ref("yudao");
const products = ref([]);
const catalogError = ref(false);
const searchQuery = ref("");
const initialListingQuery = resolveProductListingQuery(typeof window === "undefined" ? "" : window.location.search);
const emptyFacetState = () => Object.fromEntries(productFacetGroups.map((group) => [group.key, "all"]));
const selectedProductType = ref(initialListingQuery.filter);
const selectedFacets = ref({ ...emptyFacetState(), ...initialListingQuery.facets });
const selectedTag = ref(initialListingQuery.tag);
const selectedSort = ref("featured");
const mobileFiltersOpen = ref(false);
const quickAddMessage = ref("");
const wishlistIdentityKeys = ref(new Set());
const wishlistIdentityStatusKey = ref("");
const skeletonCards = [0, 1, 2, 3];
const productTypeLabelKeys = {
  sofa: "productList.typeOptions.sofa",
  "lounge-chair": "productList.typeOptions.loungeChair",
  ottoman: "productList.typeOptions.ottoman",
  "dining-table": "productList.typeOptions.diningTable",
  "dining-chair": "productList.typeOptions.diningChair",
  "coffee-table": "productList.typeOptions.coffeeTable",
  bed: "productList.typeOptions.bed",
  nightstand: "productList.typeOptions.nightstand",
  "bed-bench": "productList.typeOptions.bedBench",
  dresser: "productList.typeOptions.dresser",
  wardrobe: "productList.typeOptions.wardrobe",
  vanity: "productList.typeOptions.vanity",
  desk: "productList.typeOptions.desk",
  "round-table": "productList.typeOptions.roundTable",
  "side-table": "productList.typeOptions.sideTable",
  "media-console": "productList.typeOptions.mediaConsole",
  sideboard: "productList.typeOptions.sideboard",
  "bar-stool": "productList.typeOptions.barStool",
  lighting: "productList.typeOptions.lighting",
  rug: "productList.typeOptions.rug",
  "single-sofa": "productList.typeOptions.singleSofa",
  chair: "productList.typeOptions.chair",
  storage: "productList.typeOptions.storage",
  "desk-table": "productList.typeOptions.deskTable",
  table: "productList.typeOptions.table",
  seating: "productList.typeOptions.seating",
  "bedroom-set": "productList.typeOptions.bedroomSet",
  "storage-set": "productList.typeOptions.storageSet",
  "study-set": "productList.typeOptions.studySet",
  "bedroom-room": "productList.typeOptions.bedroomRoom",
  "master-bedroom": "productList.typeOptions.masterBedroom",
  "guest-bedroom": "productList.typeOptions.guestBedroom",
  room: "productList.typeOptions.room",
  study: "productList.typeOptions.study",
  living: "productList.typeOptions.living",
  dining: "productList.typeOptions.dining",
  decor: "productList.typeOptions.decor",
};
const facetGroupLabelKeys = {
  material: "productList.facetGroups.material",
  color: "productList.facetGroups.color",
  availability: "productList.facetGroups.availability",
  price: "productList.facetGroups.price",
};
const facetOptionLabelKeys = {
  material: {
    all: "productList.facetOptions.material.all",
    fabric: "productList.facetOptions.material.fabric",
    leather: "productList.facetOptions.material.leather",
    wood: "productList.facetOptions.material.wood",
    glass: "productList.facetOptions.material.glass",
    stone: "productList.facetOptions.material.stone",
    metal: "productList.facetOptions.material.metal",
  },
  color: {
    all: "productList.facetOptions.color.all",
    natural: "productList.facetOptions.color.natural",
    brown: "productList.facetOptions.color.brown",
    light: "productList.facetOptions.color.light",
    black: "productList.facetOptions.color.black",
    grey: "productList.facetOptions.color.grey",
  },
  availability: {
    all: "productList.facetOptions.availability.all",
    "in-stock": "productList.facetOptions.availability.inStock",
    "low-stock": "productList.facetOptions.availability.lowStock",
  },
  price: {
    all: "productList.facetOptions.price.all",
    "under-1500": "productList.facetOptions.price.under1500",
    "1500-3500": "productList.facetOptions.price.between1500And3500",
    "over-3500": "productList.facetOptions.price.over3500",
  },
};

const sourceLabel = computed(() => {
  if (source.value === "yudao") return t("connectedCatalog");
  if (source.value === "error") return t("productList.backendUnavailable.eyebrow");
  return t("offlineCatalog");
});
const normalizeValue = (value) => String(value ?? "").trim().toLowerCase();
const sortForListingModel = computed(() => {
  if (selectedSort.value === "priceAsc") return "price-asc";
  if (selectedSort.value === "priceDesc") return "price-desc";
  return "featured";
});
const productTypeOptions = computed(() => {
  const availableTypes = new Set(products.value.map((product) => inferListingType(product)));
  const baseOptions = productListingFilters
    .filter((option) => option.value !== "all" && availableTypes.has(option.value))
    .map((option) => ({ value: option.value }));
  const hasSelectedOption = baseOptions.some((option) => option.value === selectedProductType.value);
  if (hasSelectedOption || selectedProductType.value === "all") return baseOptions;

  return [
    { value: selectedProductType.value },
    ...baseOptions,
  ];
});
const productTypeLabel = (value) => {
  const key = productTypeLabelKeys[value];
  return key ? t(key) : value;
};
const facetGroupLabel = (key) => {
  const labelKey = facetGroupLabelKeys[key];
  return labelKey ? t(labelKey) : key;
};
const facetOptionLabel = (groupKey, optionValue) => {
  const labelKey = facetOptionLabelKeys[groupKey]?.[optionValue];
  return labelKey ? t(labelKey) : optionValue;
};
const selectedFilterLabel = computed(() => {
  if (selectedProductType.value === "all") return t("productList.allFurniture");
  return productTypeLabel(selectedProductType.value);
});
const listingModel = computed(() =>
  buildProductListingModel(products.value, {
    filter: selectedProductType.value,
    sort: sortForListingModel.value,
    facets: selectedFacets.value,
    tag: selectedTag.value,
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
  const labels = [resultSummary.value];
  if (searchQuery.value.trim()) labels.push(t("productList.activeSearch", { query: searchQuery.value.trim() }));
  if (selectedProductType.value !== "all") {
    labels.push(selectedFilterLabel.value);
  }
  Object.entries(selectedFacets.value).forEach(([key, value]) => {
    if (!value || value === "all") return;
    labels.push(facetOptionLabel(key, value));
  });
  return labels;
});
const money = (value) => `$${Number(value || 0).toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const isProductAvailable = (product) => Number(product.stock) > 0;
const productStockLabel = (product) => {
  const stock = Number(product.stock || 0);
  if (stock <= 0) return t("productList.stock.madeToOrder");
  if (stock <= 5) return t("productList.stock.onlyLeft", { count: stock });
  return t("productList.stock.ready", { count: stock });
};
const resetProductListControls = () => {
  searchQuery.value = "";
  selectedProductType.value = "all";
  selectedFacets.value = emptyFacetState();
  selectedTag.value = "";
  selectedSort.value = "featured";
  mobileFiltersOpen.value = false;
};
const handleQuickAdd = (product, options) => {
  emit("add-to-cart", product, 1, options);
  quickAddMessage.value = t("productList.quickAdd", { name: product.name });
};
const loadProductWishlistState = async () => {
  const state = await loadWishlistIdentityState();
  wishlistIdentityKeys.value = state.keys;
  wishlistIdentityStatusKey.value = state.statusKey || "";
};
const isProductSaved = (product) => isWishlistItemSaved(product, wishlistIdentityKeys.value);
const handleWishlistSave = (product) => {
  wishlistIdentityKeys.value = withWishlistItemSaved(wishlistIdentityKeys.value, product);
  emit("add-to-wishlist", product);
  quickAddMessage.value = t("wishlist.saved");
};
const syncListingQueryFromLocation = () => {
  const listingQuery = resolveProductListingQuery(window.location.search);
  selectedProductType.value = listingQuery.filter;
  selectedFacets.value = { ...emptyFacetState(), ...listingQuery.facets };
  selectedTag.value = listingQuery.tag;
  mobileFiltersOpen.value = false;
};

const resolveCategoryIdFromPath = (pathname = window.location.pathname) => {
  const match = pathname.match(/^\/products\/category\/(\d+)\/?$/);
  return match ? Number(match[1]) : undefined;
};

onMounted(async () => {
  window.addEventListener("popstate", syncListingQueryFromLocation);
  window.addEventListener("oakved:navigation", syncListingQueryFromLocation);
  loadProductWishlistState();
  try {
    const categoryId = resolveCategoryIdFromPath();
    const requestOptions = props.tenantId ? { tenantId: props.tenantId } : {};
    const page = await getAllProducts(categoryId ? { categoryId } : {}, requestOptions);
    products.value = page.list;
    source.value = "yudao";
    catalogError.value = false;
  } catch {
    products.value = [];
    source.value = "error";
    catalogError.value = true;
  } finally {
    loading.value = false;
  }
});

watch(() => props.authVersion, loadProductWishlistState);

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
            {{ productTypeLabel(option.value) }}
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
        {{ t("productList.filters.title") }}
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
        {{ t("productList.allTypes") }}
      </button>
      <button
        v-for="option in productTypeOptions"
        :key="option.value"
        type="button"
        :class="{ active: selectedProductType === option.value }"
        @click="selectedProductType = option.value"
      >
        {{ productTypeLabel(option.value) }}
      </button>
    </div>

    <section class="product-facet-shell" :class="{ open: mobileFiltersOpen }" aria-label="Product filters">
      <button class="product-facet-backdrop" type="button" aria-label="Close filters" @click="mobileFiltersOpen = false"></button>
      <div class="product-facet-panel">
        <div class="product-facet-head">
          <div>
            <p class="eyebrow">{{ t("productList.filters.title") }}</p>
            <h2>{{ selectedFilterLabel }}</h2>
          </div>
          <button type="button" @click="mobileFiltersOpen = false">{{ t("productList.filters.close") }}</button>
        </div>
        <label v-for="group in productFacetGroups" :key="group.key" class="product-facet-control">
          <span>{{ facetGroupLabel(group.key) }}</span>
          <select v-model="selectedFacets[group.key]">
            <option v-for="option in group.options" :key="option.value" :value="option.value">
              {{ facetOptionLabel(group.key, option.value) }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="product-active-filters" aria-label="Active filters">
      <span v-for="label in activeFilterLabels" :key="label">{{ label }}</span>
      <button type="button" @click="resetProductListControls">{{ t("productList.filters.clearAll") }}</button>
    </section>

    <section class="product-list-confidence" aria-label="Catalog confidence">
      <span>{{ t("productList.matchedPieces", { count: listingModel.summary.productCount }) }}</span>
      <span>{{ t("productList.productGroups", { count: listingModel.summary.collectionCount }) }}</span>
      <span>{{ t("productList.browseStatus") }}</span>
    </section>

    <p v-if="quickAddMessage" class="product-quick-add-status" role="status">{{ quickAddMessage }}</p>
    <p v-if="wishlistIdentityStatusKey" class="product-quick-add-status" role="status">{{ t(wishlistIdentityStatusKey) }}</p>

    <div v-if="!loading && catalogError" class="product-list-empty">
      <p class="eyebrow">{{ t("productList.backendUnavailable.eyebrow") }}</p>
      <h2>{{ t("productList.backendUnavailable.title") }}</h2>
      <p>{{ t("productList.backendUnavailable.description") }}</p>
      <p>{{ t("catalogUnavailable") }}</p>
    </div>

    <div v-else-if="!loading && visibleProducts.length === 0" class="product-list-empty">
      <p class="eyebrow">{{ t("productList.empty.eyebrow") }}</p>
      <h2>{{ t("productList.empty.title") }}</h2>
      <p>{{ t("productList.empty.description") }}</p>
      <p>{{ t("catalogEmpty") }}</p>
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
              <dt>{{ t("productList.card.member") }}</dt>
              <dd>{{ money(product.price) }}</dd>
            </div>
            <div v-if="product.marketPrice">
              <dt>{{ t("productList.card.regular") }}</dt>
              <dd>{{ money(product.marketPrice) }}</dd>
            </div>
            <div v-if="product.dimensions">
              <dt>{{ t("productList.card.size") }}</dt>
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
              class="product-card-wishlist"
              type="button"
              :aria-pressed="isProductSaved(product)"
              @click="handleWishlistSave(product)"
            >
              {{ t(isProductSaved(product) ? "wishlist.saved" : "wishlist.save") }}
            </button>
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
        <p class="eyebrow">{{ t("productList.edit.eyebrow") }}</p>
        <h2>{{ t("productList.edit.title") }}</h2>
        <p>{{ t("productList.edit.description") }}</p>
        <a href="/products?collection=bedroom-set">{{ t("productList.edit.cta") }}</a>
      </aside>
    </section>
  </section>
</template>
