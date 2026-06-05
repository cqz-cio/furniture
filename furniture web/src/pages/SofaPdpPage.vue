<script setup>
import { computed, onMounted, ref } from "vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import { buildProductDetailModel } from "../services/productDetailModel.js";
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
const activeGalleryIndex = ref(0);
const detail = computed(() => buildProductDetailModel(product.value));
const activeGalleryItem = computed(() => detail.value.gallery[activeGalleryIndex.value] || detail.value.gallery[0]);

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
    activeGalleryIndex.value = 0;
    loading.value = false;
  }
});
</script>

<template>
  <section class="product-detail-page">
    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>

    <div class="product-detail-grid">
      <div class="product-detail-media">
        <figure class="product-gallery-main" :class="`tone-${activeGalleryItem.tone}`">
          <img v-if="activeGalleryItem.src" :src="activeGalleryItem.src" :alt="`${detail.name} ${activeGalleryItem.label}`" />
          <figcaption v-else class="product-gallery-placeholder">
            <span>{{ activeGalleryItem.label }}</span>
            <strong>{{ activeGalleryItem.kind }}</strong>
          </figcaption>
        </figure>
        <div class="product-gallery-thumbs" aria-label="Product images">
          <button
            v-for="(item, index) in detail.gallery"
            :key="`${item.label}-${index}`"
            type="button"
            :class="{ active: activeGalleryIndex === index }"
            @click="activeGalleryIndex = index"
          >
            <img v-if="item.src" :src="item.src" :alt="item.label" />
            <span v-else>{{ item.label }}</span>
          </button>
        </div>
        <p class="product-hero-note">{{ detail.heroNote }}</p>
      </div>

      <article class="product-detail-panel">
        <p class="eyebrow">{{ sourceLabel }} / {{ detail.collection }}</p>
        <h1>{{ detail.name }}</h1>
        <p class="product-detail-copy">{{ detail.description }}</p>
        <div class="product-detail-price">
          <small>{{ detail.price.prefix }}</small>
          <strong>{{ money(detail.price.member) }}</strong>
          <em>{{ detail.price.memberLabel }}</em>
          <b v-if="detail.price.sale">{{ money(detail.price.sale) }} {{ detail.price.saleLabel }}</b>
          <span>{{ money(detail.price.regular) }} {{ detail.price.regularLabel }}</span>
        </div>
        <p class="product-savings-label">{{ detail.price.savingsLabel }}</p>
        <p class="product-price-context">{{ detail.price.context }}</p>
        <nav class="product-related-links" aria-label="Related product options">
          <a v-for="link in detail.relatedLinks" :key="link.label" :href="link.href">{{ link.label }}</a>
        </nav>

        <section class="product-highlights" aria-label="Product highlights">
          <ul>
            <li v-for="item in detail.highlights" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="product-fabric-selector" aria-label="Fabric selector">
          <div class="product-fabric-head">
            <h2>{{ detail.fabricSelector.label }}</h2>
            <span>{{ detail.fabricSelector.stockedCount }} stocked / {{ detail.fabricSelector.specialOrderCount }} special order</span>
          </div>
          <div class="product-fabric-rail">
            <button v-for="item in detail.fabricSelector.swatches" :key="item.label" type="button" :title="item.label">
              <span class="product-fabric-swatch" :style="{ backgroundColor: item.swatch }"></span>
            </button>
          </div>
        </section>

        <section class="product-option-stack" aria-label="Product options">
          <div v-for="group in detail.optionGroups" :key="group.key" class="product-option-group">
            <div class="product-option-head">
              <h2>{{ group.label }}</h2>
              <p>{{ group.helper }}</p>
            </div>
            <div class="product-option-values">
              <button v-for="value in group.values" :key="value.label || value" type="button">
                <span v-if="value.swatch" class="product-swatch" :style="{ backgroundColor: value.swatch }"></span>
                {{ value.label || value }}
              </button>
            </div>
          </div>
        </section>

        <section class="product-availability-card" aria-label="Availability">
          <button type="button" class="product-stock-link">{{ detail.availability.title }}</button>
          <p>{{ detail.availability.readyToShip }}</p>
          <small>{{ detail.availability.specialOrder }}</small>
        </section>

        <p class="product-stock">{{ detail.stock.label }} {{ detail.stock.value }} / {{ detail.stock.status }}</p>
        <div class="product-purchase-row">
          <label>
            {{ t("quantity") }}
            <input v-model.number="quantity" min="1" type="number" />
          </label>
          <button type="button" @click="emit('add-to-cart', product, quantity)">
            {{ t("addToCart") }}
          </button>
        </div>

        <section class="product-accordion-list" aria-label="Product details">
          <details v-for="(item, index) in detail.accordions" :key="item.title" :open="index === 0">
            <summary>{{ item.title }}</summary>
            <dl>
              <template v-for="row in item.rows" :key="`${item.title}-${row[0]}`">
                <dt>{{ row[0] }}</dt>
                <dd>{{ row[1] }}</dd>
              </template>
            </dl>
          </details>
        </section>
      </article>
    </div>
  </section>
</template>
