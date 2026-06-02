<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import { getProductDetail } from "../services/yudaoClient.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const product = ref(demoProducts[0]);
const loading = ref(true);
const source = ref("demo");
const quantity = ref(1);

const productId = computed(() => new URLSearchParams(window.location.search).get("id"));
const sourceLabel = computed(() => (source.value === "yudao" ? t("connectedCatalog") : t("offlineCatalog")));
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;

onMounted(async () => {
  const id = productId.value;
  if (!id) {
    loading.value = false;
    return;
  }

  try {
    product.value = await getProductDetail(id);
    source.value = "yudao";
  } catch {
    product.value = demoProducts.find((item) => String(item.id) === String(id)) || demoProducts[0];
    source.value = "demo";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="product-detail-page">
    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>

    <div class="product-detail-grid">
      <div class="product-detail-media">
        <ProductImage :src="product.cover" :label="product.name" />
      </div>

      <article class="product-detail-panel">
        <p class="eyebrow">{{ sourceLabel }}</p>
        <h1>{{ product.name }}</h1>
        <p>{{ product.description || product.subtitle }}</p>
        <div class="product-detail-price">
          <strong>{{ money(product.price) }}</strong>
          <span v-if="product.marketPrice">{{ money(product.marketPrice) }}</span>
        </div>
        <p class="product-stock">{{ t("stock") }} {{ product.stock }}</p>
        <div class="product-purchase-row">
          <label>
            {{ t("quantity") }}
            <input v-model.number="quantity" min="1" type="number" />
          </label>
          <button type="button" @click="emit('add-to-cart', product, quantity)">
            {{ t("addToCart") }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>
