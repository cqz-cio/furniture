<script setup>
import { onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { useI18n } from "../i18n.js";
import { getProductPage } from "../services/yudaoClient.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const loading = ref(true);
const products = ref([]);
const error = ref(false);

const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;

onMounted(async () => {
  try {
    const page = await getProductPage({ pageNo: 1, pageSize: 24 });
    products.value = page.list;
  } catch {
    error.value = true;
    products.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="product-list-page">
    <header class="product-list-head">
      <h1>{{ t("productsTitle") }}</h1>
      <p>{{ t("productsSubtitle") }}</p>
    </header>

    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>
    <p v-else-if="error" class="product-loading">{{ t("catalogUnavailable") }}</p>
    <p v-else-if="products.length === 0" class="product-loading">{{ t("catalogEmpty") }}</p>

    <section v-else class="product-grid" aria-label="Furniture products">
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
