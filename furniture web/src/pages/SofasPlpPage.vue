<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import { getProductPage } from "../services/yudaoClient.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const loading = ref(true);
const source = ref("demo");
const products = ref(demoProducts);

const sourceLabel = computed(() => (source.value === "yudao" ? t("connectedCatalog") : t("offlineCatalog")));
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;

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

    <section class="product-grid" aria-label="Furniture products">
      <article v-for="product in products" :key="product.skuId" class="product-card">
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
            <button type="button" @click="emit('add-to-cart', product, 1)">{{ t("addToCart") }}</button>
          </div>
        </div>
      </article>
    </section>
  </section>
</template>
