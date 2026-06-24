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
const sourceLabel = computed(() => (source.value === "yudao" ? "Oakved collection" : "Oakved catalog"));
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const activeGalleryIndex = ref(0);
const detail = computed(() => buildProductDetailModel(product.value));
const activeGalleryItem = computed(() => detail.value.gallery[activeGalleryIndex.value] || detail.value.gallery[0]);
const addCurrentProductToCart = () => emit("add-to-cart", product.value, quantity.value);
let lastGalleryWheelAt = 0;

const setGalleryIndex = (index) => {
  const total = detail.value.gallery.length;
  if (!total) return;
  activeGalleryIndex.value = (index + total) % total;
};

const showPreviousGalleryItem = () => setGalleryIndex(activeGalleryIndex.value - 1);
const showNextGalleryItem = () => setGalleryIndex(activeGalleryIndex.value + 1);

const handleGalleryWheel = (event) => {
  const now = Date.now();
  if (now - lastGalleryWheelAt < 760) return;

  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY;
  if (Math.abs(delta) < 8) return;

  lastGalleryWheelAt = now;
  if (delta > 0) {
    showNextGalleryItem();
  } else {
    showPreviousGalleryItem();
  }
};

const applyProductSeo = () => {
  if (typeof document === "undefined") return;
  const title = detail.value.name ? `${detail.value.name} | Oakved` : "Product Details | Oakved";
  document.title = title;
  let description = document.querySelector('meta[name="description"]');
  if (!description) {
    description = document.createElement("meta");
    description.setAttribute("name", "description");
    document.head.appendChild(description);
  }
  description.setAttribute(
    "content",
    `${detail.value.name} from ${detail.value.collection}. ${detail.value.description}`,
  );
};

onMounted(async () => {
  const id = productId.value;
  if (!id) {
    loading.value = false;
    applyProductSeo();
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
    applyProductSeo();
  }
});
</script>

<template>
  <section class="product-detail-page">
    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>

    <div class="product-detail-grid">
      <div class="product-detail-media">
        <figure
          class="product-gallery-main"
          :class="`tone-${activeGalleryItem.tone}`"
          tabindex="0"
          :aria-label="`${detail.name} gallery. Click, scroll or use arrow keys to switch views.`"
          @click="showNextGalleryItem"
          @wheel.prevent="handleGalleryWheel"
          @keydown.left.prevent="showPreviousGalleryItem"
          @keydown.right.prevent="showNextGalleryItem"
        >
          <Transition name="product-gallery-fade">
            <img
              v-if="activeGalleryItem.src"
              :key="activeGalleryItem.src"
              :src="activeGalleryItem.src"
              :alt="`${detail.name} ${activeGalleryItem.label}`"
            />
            <figcaption v-else :key="activeGalleryItem.label" class="product-gallery-placeholder">
              <span>{{ activeGalleryItem.label }}</span>
              <strong>{{ activeGalleryItem.kind }}</strong>
            </figcaption>
          </Transition>
          <button
            class="product-gallery-nav product-gallery-nav-prev"
            type="button"
            aria-label="Previous product image"
            @click.stop="showPreviousGalleryItem"
          >
            ‹
          </button>
          <button
            class="product-gallery-nav product-gallery-nav-next"
            type="button"
            aria-label="Next product image"
            @click.stop="showNextGalleryItem"
          >
            ›
          </button>
        </figure>
        <p class="product-gallery-status" aria-live="polite">
          <span>{{ activeGalleryItem.label }} view</span>
          <span>{{ activeGalleryIndex + 1 }} / {{ detail.gallery.length }}</span>
          <small>Click, scroll or use arrow keys to switch views</small>
        </p>
        <div class="product-gallery-thumbs" aria-label="Product images">
          <button
            v-for="(item, index) in detail.gallery"
            :key="`${item.label}-${index}`"
            type="button"
            :class="{ active: activeGalleryIndex === index }"
            @click="setGalleryIndex(index)"
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
        <section class="product-membership-callout" aria-label="Membership pricing details">
          <div>
            <strong>{{ detail.membershipPrompt.title }}</strong>
            <p>{{ detail.membershipPrompt.copy }}</p>
          </div>
          <a :href="detail.membershipPrompt.href">{{ detail.membershipPrompt.linkLabel }}</a>
        </section>
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

        <section class="product-assurance-grid" aria-label="Delivery and returns">
          <article v-for="item in detail.purchaseAssurance" :key="item.title">
            <h2>{{ item.title }}</h2>
            <p>{{ item.copy }}</p>
          </article>
        </section>

        <p class="product-stock">{{ detail.stock.label }} {{ detail.stock.value }} / {{ detail.stock.status }}</p>
        <div class="product-purchase-row">
          <label>
            {{ t("quantity") }}
            <input v-model.number="quantity" min="1" type="number" />
          </label>
          <button type="button" @click="addCurrentProductToCart">
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

    <section class="product-inspiration-section" aria-label="Room inspiration">
      <header>
        <p class="eyebrow">Room inspiration</p>
        <h2>Complete the room with proportion, texture and quiet materials.</h2>
      </header>
      <div class="product-inspiration-grid">
        <article v-for="item in detail.roomInspiration" :key="item.title">
          <img :src="item.image" :alt="item.title" />
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.copy }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="shop-room-section" aria-label="Shop the room">
      <figure class="shop-room-figure">
        <img :src="detail.roomInspiration[0]?.image" alt="Styled room with shoppable Oakved furniture" loading="lazy" />
        <a
          v-for="(item, index) in detail.companionProducts"
          :key="item.title"
          :href="item.href"
          class="shop-room-hotspot"
          :class="`hotspot-${index + 1}`"
        >
          <span>{{ index + 1 }}</span>
          <strong>{{ item.title }}</strong>
        </a>
        <a class="shop-room-hotspot hotspot-3" :href="`/product?id=${detail.id || 1001}`">
          <span>3</span>
          <strong>{{ detail.name }}</strong>
        </a>
      </figure>
      <div class="shop-room-copy">
        <p class="eyebrow">Shop the room</p>
        <h2>Tap the room to move from inspiration to product detail.</h2>
        <p>Hotspots keep the room photograph central while making the key pieces immediately shoppable.</p>
      </div>
    </section>

    <section class="product-companion-section" aria-label="Complete the room">
      <header>
        <p class="eyebrow">Complete the room</p>
        <h2>Recommended pieces that share the same quiet material language.</h2>
      </header>
      <div class="product-companion-grid">
        <a v-for="item in detail.companionProducts" :key="item.title" :href="item.href">
          <img :src="item.image" :alt="item.title" loading="lazy" />
          <span>{{ item.title }}</span>
        </a>
      </div>
    </section>

    <aside class="product-sticky-purchase" aria-label="Quick purchase">
      <div>
        <strong>{{ detail.name }}</strong>
        <span>{{ money(detail.price.member) }} Member</span>
      </div>
      <button type="button" @click="addCurrentProductToCart">{{ t("addToCart") }}</button>
    </aside>
  </section>
</template>
