<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import { registryProductToItemPayload } from "../services/giftRegistry.js";
import { buildProductDetailModel } from "../services/productDetailModel.js";
import { resolveProductBackendFailure } from "../services/productBackendFallback.js";
import {
  isWishlistItemSaved,
  loadWishlistIdentityState,
  withWishlistItemSaved,
} from "../services/wishlistState.js";
import { addYudaoGiftRegistryItem, getMyYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";
import { getProductDetail } from "../services/yudaoProductApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});
const emit = defineEmits(["add-to-cart", "add-to-wishlist"]);
const { t } = useI18n();

const product = ref(demoProducts[0]);
const loading = ref(true);
const source = ref("demo");
const catalogError = ref(false);
const quantity = ref(1);
const wishlistIdentityKeys = ref(new Set());
const wishlistStatusMessage = ref("");
const registryStatusMessage = ref("");
const registryBusy = ref(false);

const productId = computed(() => new URLSearchParams(window.location.search).get("id"));
const sourceLabel = computed(() => {
  if (source.value === "yudao") return t("connectedCatalog");
  if (source.value === "error") return t("productList.backendUnavailable.eyebrow");
  return t("offlineCatalog");
});
const money = (value) => `$${value.toLocaleString("en-US", { maximumFractionDigits: 0 })}`;
const activeGalleryIndex = ref(0);
const detail = computed(() => buildProductDetailModel(product.value));
const activeGalleryItem = computed(() => detail.value.gallery[activeGalleryIndex.value] || detail.value.gallery[0]);
const maxPurchaseQuantity = computed(() => Math.max(0, Number(product.value.stock) || 0));
const canPurchase = computed(() => maxPurchaseQuantity.value > 0);
let lastGalleryWheelAt = 0;

const normalizedPurchaseQuantity = computed(() => {
  if (!canPurchase.value) return 0;
  return Math.max(1, Math.min(Math.floor(Number(quantity.value) || 1), maxPurchaseQuantity.value));
});
const isCurrentProductSaved = computed(() => isWishlistItemSaved(product.value, wishlistIdentityKeys.value));

const setGalleryIndex = (index) => {
  const total = detail.value.gallery.length;
  if (!total) return;
  activeGalleryIndex.value = (index + total) % total;
};
const moveGallery = (direction) => setGalleryIndex(activeGalleryIndex.value + direction);
const showPreviousGalleryItem = () => moveGallery(-1);
const showNextGalleryItem = () => moveGallery(1);
const handleGalleryWheel = (event) => {
  const now = Date.now();
  if (now - lastGalleryWheelAt < 820) return;
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
  const title = detail.value?.name || "Product Details";
  document.title = `${title} | Oakved`;
  let meta = document.querySelector('meta[name="description"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.setAttribute("name", "description");
    document.head.appendChild(meta);
  }
  meta.setAttribute("content", detail.value?.description || "Product Details | Oakved");
};

const handleAddToCart = (event) => {
  quantity.value = normalizedPurchaseQuantity.value;
  if (!canPurchase.value) return;
  emit("add-to-cart", product.value, normalizedPurchaseQuantity.value, { trigger: event?.currentTarget });
};
const loadProductWishlistState = async () => {
  const state = await loadWishlistIdentityState();
  wishlistIdentityKeys.value = state.keys;
  wishlistStatusMessage.value = state.statusKey ? t(state.statusKey) : "";
};
const handleAddToWishlist = () => {
  wishlistIdentityKeys.value = withWishlistItemSaved(wishlistIdentityKeys.value, product.value);
  emit("add-to-wishlist", product.value);
  wishlistStatusMessage.value = t("wishlist.saved");
};
const handleAddToRegistry = async () => {
  registryStatusMessage.value = "";
  if (source.value !== "yudao") {
    registryStatusMessage.value = "Only connected Yudao products can be added to a gift registry.";
    return;
  }
  if (!readYudaoToken()) {
    registryStatusMessage.value = "Sign in before adding this item to your gift registry.";
    return;
  }
  registryBusy.value = true;
  try {
    const registry = await getMyYudaoGiftRegistry();
    if (!registry?.id) {
      registryStatusMessage.value = "Create a gift registry before adding products.";
      return;
    }
    await addYudaoGiftRegistryItem(
      registryProductToItemPayload(product.value, {
        registryId: registry.id,
        quantityRequested: normalizedPurchaseQuantity.value || 1,
      }),
    );
    registryStatusMessage.value = "Added to your gift registry.";
  } catch (error) {
    registryStatusMessage.value = error?.message || "This item could not be added to your gift registry.";
  } finally {
    registryBusy.value = false;
  }
};

watch(detail, applyProductSeo);
watch(() => props.authVersion, loadProductWishlistState);

onMounted(async () => {
  loadProductWishlistState();
  const id = productId.value;
  if (!id) {
    loading.value = false;
    return;
  }

  try {
    product.value = await getProductDetail(id);
    source.value = "yudao";
    catalogError.value = false;
  } catch {
    const failure = resolveProductBackendFailure({ demoProducts });
    product.value = failure.products.find((item) => String(item.id) === String(id)) || demoProducts[0];
    source.value = failure.source;
    catalogError.value = failure.error;
  } finally {
    activeGalleryIndex.value = 0;
    loading.value = false;
  }
});
</script>

<template>
  <section class="product-detail-page">
    <p v-if="loading" class="product-loading">{{ t("loadingProducts") }}</p>

    <div v-if="!loading && catalogError" class="product-list-empty">
      <p class="eyebrow">{{ t("productList.backendUnavailable.eyebrow") }}</p>
      <h2>{{ t("productList.backendUnavailable.title") }}</h2>
      <p>{{ t("productList.backendUnavailable.description") }}</p>
    </div>

    <div v-else class="product-detail-grid">
      <div class="product-detail-media">
        <figure
          class="product-gallery-main"
          :class="`tone-${activeGalleryItem.tone}`"
          :aria-label="`${detail.name} gallery. Click, scroll or use arrow keys to switch views.`"
          tabindex="0"
          @click="showNextGalleryItem"
          @wheel.prevent="handleGalleryWheel"
          @keydown.left.prevent="showPreviousGalleryItem"
          @keydown.right.prevent="showNextGalleryItem"
        >
          <button class="product-gallery-arrow product-gallery-arrow-prev" type="button" aria-label="Previous image" @click.stop="showPreviousGalleryItem">&lsaquo;</button>
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
          <button class="product-gallery-arrow product-gallery-arrow-next" type="button" aria-label="Next image" @click.stop="showNextGalleryItem">&rsaquo;</button>
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

        <div class="product-mobile-purchase-bar" aria-label="Mobile purchase actions">
          <div>
            <small>{{ detail.price.memberLabel }}</small>
            <strong>{{ money(detail.price.member) }}</strong>
          </div>
          <label>
            {{ t("quantity") }}
            <input
              v-model.number="quantity"
              min="1"
              :max="maxPurchaseQuantity"
              :disabled="!canPurchase"
              type="number"
              @change="quantity = normalizedPurchaseQuantity"
            />
          </label>
          <button type="button" :disabled="!canPurchase" @click="handleAddToCart($event)">
            {{ canPurchase ? t("addToCart") : t("product.unavailable") }}
          </button>
          <button type="button" :aria-pressed="isCurrentProductSaved" @click="handleAddToWishlist">
            {{ t(isCurrentProductSaved ? "wishlist.saved" : "wishlist.save") }}
          </button>
          <button class="product-registry-button" type="button" :disabled="registryBusy" @click="handleAddToRegistry">
            {{ registryBusy ? t("common.working") : "Add to Gift Registry" }}
          </button>
        </div>
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
            <input
              v-model.number="quantity"
              min="1"
              :max="maxPurchaseQuantity"
              :disabled="!canPurchase"
              type="number"
              @change="quantity = normalizedPurchaseQuantity"
            />
          </label>
          <button type="button" :disabled="!canPurchase" @click="handleAddToCart($event)">
            {{ canPurchase ? t("addToCart") : t("product.unavailable") }}
          </button>
          <button class="product-wishlist-button" type="button" :aria-pressed="isCurrentProductSaved" @click="handleAddToWishlist">
            {{ t(isCurrentProductSaved ? "wishlist.saved" : "wishlist.save") }}
          </button>
          <button class="product-registry-button" type="button" :disabled="registryBusy" @click="handleAddToRegistry">
            {{ registryBusy ? t("common.working") : "Add to Gift Registry" }}
          </button>
        </div>
        <p v-if="wishlistStatusMessage" class="product-registry-status" role="status">{{ wishlistStatusMessage }}</p>
        <p v-if="registryStatusMessage" class="product-registry-status" role="status">{{ registryStatusMessage }}</p>

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
        <p class="eyebrow">Room Inspiration</p>
        <h2>Style the full Oakved room</h2>
        <p>Use material, scale and surrounding pieces to help customers picture the product before purchase.</p>
      </header>
      <div class="product-inspiration-grid">
        <article v-for="item in detail.roomInspiration" :key="item.title">
          <img :src="item.image" :alt="item.title" loading="lazy" />
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.copy }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="shop-room-section" aria-label="Shop the room">
      <figure class="shop-room-figure">
        <img :src="detail.roomInspiration?.[0]?.image || activeGalleryItem.src" alt="Styled room with shoppable Oakved furniture" loading="lazy" />
        <a
          v-for="(item, index) in detail.companionProducts"
          :key="item.title"
          class="shop-room-hotspot"
          :href="item.href"
          :style="{ left: index === 0 ? '34%' : '66%', top: index === 0 ? '58%' : '42%' }"
          :aria-label="`Shop ${item.title}`"
        >
          {{ index + 1 }}
        </a>
      </figure>
      <div class="shop-room-copy">
        <p class="eyebrow">Shop The Room</p>
        <h2>Build a coordinated wood furniture setting</h2>
        <p>Each hotspot links the primary item with complementary tables, storage and seating so the page feels closer to a finished showroom.</p>
      </div>
    </section>

    <section class="product-companion-section" aria-label="Complete the room">
      <header>
        <p class="eyebrow">Complete The Room</p>
        <h2>Pieces that sit well together</h2>
        <p>Keep the next step visual and product-led instead of sending customers back to a blank catalog search.</p>
      </header>
      <div class="product-companion-grid">
        <a v-for="item in detail.companionProducts" :key="item.title" :href="item.href">
          <img :src="item.image" :alt="item.title" loading="lazy" />
          <span>{{ item.title }}</span>
        </a>
      </div>
    </section>
  </section>
</template>
