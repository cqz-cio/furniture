<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import {
  applyProductListControls,
  buildProductTypeOptions,
  PRODUCT_SORT_OPTIONS,
} from "../services/productListControls.js";
import { getProductPage } from "../services/yudaoProductApi.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const loading = ref(true);
const source = ref("demo");
const products = ref(demoProducts);
const searchQuery = ref("");
const selectedProductType = ref("all");
const selectedSort = ref("featured");

const sourceLabel = computed(() => (source.value === "yudao" ? t("connectedCatalog") : t("offlineCatalog")));
const productTypeOptions = computed(() => buildProductTypeOptions(products.value));
const visibleProducts = computed(() =>
  applyProductListControls(products.value, {
    query: searchQuery.value,
    productType: selectedProductType.value,
    sort: selectedSort.value,
  })
);
const resultSummary = computed(() =>
  t("productList.resultSummary", { visible: visibleProducts.value.length, total: products.value.length })
);
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const isProductAvailable = (product) => Number(product.stock) > 0;
const resetProductListControls = () => {
  searchQuery.value = "";
  selectedProductType.value = "all";
  selectedSort.value = "featured";
};

onMounted(async () => {
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
</script>

<template>
  <section class="product-list-page">
    <header class="product-list-head">
      <p class="eyebrow">{{ sourceLabel }}</p>
      <h1>{{ t("productsTitle") }}</h1>
      <p>{{ t("productsSubtitle") }}</p>
    </header>

    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>

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

    <div v-if="!loading && visibleProducts.length === 0" class="product-list-empty">
      <p class="eyebrow">{{ t("productList.empty.eyebrow") }}</p>
      <h2>{{ t("productList.empty.title") }}</h2>
      <p>{{ t("productList.empty.description") }}</p>
      <button type="button" @click="resetProductListControls">{{ t("productList.empty.action") }}</button>
    </div>

    <section v-else class="product-grid" aria-label="Furniture products">
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
              @click="emit('add-to-cart', product, 1)"
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
