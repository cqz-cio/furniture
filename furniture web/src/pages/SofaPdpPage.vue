<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";
import { registryProductToItemPayload } from "../services/giftRegistry.js";
import { buildProductDetailModel } from "../services/productDetailModel.js";
import { trackProductDetailView } from "../services/analytics.js";
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
const priceLabelKeys = {
  "Starting at": "productDetail.price.prefix",
  Member: "productDetail.price.member",
  Sale: "productDetail.price.sale",
  Regular: "productDetail.price.regular",
  "ANNUAL 5% FIRST ORDER / WHOLE-ROOM 15%": "productDetail.price.savings",
  "Starting at price reflects the displayed size and stocked finish.": "productDetail.price.context",
};
const stockLabelKeys = {
  Inventory: "productDetail.stock.label",
};
const stockStatusKeys = {
  "In stock": "productDetail.stock.inStock",
  "Made to order": "productDetail.stock.madeToOrder",
};
const membershipPromptTextKeys = {
  "Member pricing available": "productDetail.membershipPrompt.title",
  "Sign in or join the Members Program to review eligible savings before checkout.": "productDetail.membershipPrompt.copy",
  "Learn More": "productDetail.membershipPrompt.linkLabel",
};
const fabricSelectorLabelKeys = {
  "SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS": "productDetail.fabricSelector.stockedFabrics",
  "SELECT FROM 8 STONE TOPS AND 6 WOOD FINISHES": "productDetail.fabricSelector.stoneWood",
  "SELECT FROM 12 STOCKED AND 48 SPECIAL ORDER OUTDOOR FABRICS": "productDetail.fabricSelector.outdoorFabrics",
  "SELECT FROM 6 METAL FINISHES AND 4 SHADE OPTIONS": "productDetail.fabricSelector.metalShade",
};
const availabilityTextKeys = {
  "VIEW IN STOCK ITEMS": "productDetail.availability.title",
  "Ready to ship in 3-7 days": "productDetail.availability.readyToShip",
  "Special order options ship by confirmed production window": "productDetail.availability.specialOrder",
};
const optionGroupLabelKeys = {
  size: "productDetail.optionGroups.labels.size",
  fabric: "productDetail.optionGroups.labels.fabric",
  finish: "productDetail.optionGroups.labels.finish",
  configuration: "productDetail.optionGroups.labels.configuration",
  depth: "productDetail.optionGroups.labels.depth",
  fill: "productDetail.optionGroups.labels.fill",
  shape: "productDetail.optionGroups.labels.shape",
  top: "productDetail.optionGroups.labels.top",
  base: "productDetail.optionGroups.labels.base",
  frame: "productDetail.optionGroups.labels.frame",
  cushion: "productDetail.optionGroups.labels.cushion",
  orientation: "productDetail.optionGroups.labels.orientation",
  shade: "productDetail.optionGroups.labels.shade",
  bulb: "productDetail.optionGroups.labels.bulb",
};
const optionGroupHelperKeys = {
  "Choose the bed frame size.": "productDetail.optionGroups.helpers.bedSize",
  "Stocked and special order upholstery options.": "productDetail.optionGroups.helpers.upholstery",
  "Visible frame or leg finish.": "productDetail.optionGroups.helpers.visibleFinish",
  "Controls base and headboard setup.": "productDetail.optionGroups.helpers.bedConfiguration",
  "Choose the seating profile before fabric and depth.": "productDetail.optionGroups.helpers.seatingConfiguration",
  "Controls seat depth and room footprint.": "productDetail.optionGroups.helpers.depth",
  "Defines the sit and maintenance level.": "productDetail.optionGroups.helpers.fill",
  "Select the dining room footprint.": "productDetail.optionGroups.helpers.shape",
  "Controls seating capacity and room clearance.": "productDetail.optionGroups.helpers.diningSize",
  "Stone and wood top options for Oakved dining filters.": "productDetail.optionGroups.helpers.top",
  "Base finish can map to SKU attributes later.": "productDetail.optionGroups.helpers.base",
  "Choose weathered wood or metal frame finish.": "productDetail.optionGroups.helpers.frame",
  "Outdoor stocked and special order cushion fabrics.": "productDetail.optionGroups.helpers.outdoorFabric",
  "Controls cushion profile and comfort.": "productDetail.optionGroups.helpers.cushion",
  "Useful for lounge chair or modular outdoor layouts.": "productDetail.optionGroups.helpers.orientation",
  "Choose fixture diameter or drop length.": "productDetail.optionGroups.helpers.fixtureSize",
  "Metal finish for fixture body and canopy.": "productDetail.optionGroups.helpers.metalFinish",
  "Shade options for light diffusion.": "productDetail.optionGroups.helpers.shade",
  "Defines light source and compatibility.": "productDetail.optionGroups.helpers.bulb",
};
const purchaseAssuranceTitleKeys = {
  Delivery: "productDetail.purchaseAssurance.delivery.title",
  Installation: "productDetail.purchaseAssurance.installation.title",
  Returns: "productDetail.purchaseAssurance.returns.title",
};
const purchaseAssuranceCopyKeys = {
  "Delivery windows are shown before checkout and confirmed after order review.": "productDetail.purchaseAssurance.delivery.copy",
  "Large furniture can be scheduled with room-of-choice placement.": "productDetail.purchaseAssurance.installation.copy",
  "Review eligible returns, exchanges and custom-order terms before purchase.": "productDetail.purchaseAssurance.returns.copy",
};
const relatedLinkLabelKeys = {
  "ALSO AVAILABLE IN LEATHER": "productDetail.relatedLinks.availableLeather",
  "ALSO AVAILABLE FOR CUSTOM CONFIGURATION": "productDetail.relatedLinks.customConfiguration",
  "EXPLORE THE LUXE BED COLLECTION": "productDetail.relatedLinks.luxeBed",
  "ALSO AVAILABLE WITH CUSTOM FABRIC": "productDetail.relatedLinks.customFabric",
  "EXPLORE THE BEDROOM LOUNGE COLLECTION": "productDetail.relatedLinks.bedroomLounge",
  "ALSO AVAILABLE WITH WOOD TOP": "productDetail.relatedLinks.woodTop",
  "ALSO AVAILABLE FOR CUSTOM LENGTH": "productDetail.relatedLinks.customLength",
  "EXPLORE THE MARBLE DINING COLLECTION": "productDetail.relatedLinks.marbleDining",
  "ALSO AVAILABLE AS A DINING CHAIR": "productDetail.relatedLinks.diningChair",
  "ALSO AVAILABLE WITH CUSTOM CUSHIONS": "productDetail.relatedLinks.customCushions",
  "EXPLORE THE OUTDOOR LOUNGE COLLECTION": "productDetail.relatedLinks.outdoorLounge",
  "ALSO AVAILABLE AS A SCONCE": "productDetail.relatedLinks.sconce",
  "ALSO AVAILABLE IN CUSTOM FINISHES": "productDetail.relatedLinks.customFinishes",
  "EXPLORE THE ARCHITECTURAL LIGHTING COLLECTION": "productDetail.relatedLinks.architecturalLighting",
  "PAIR WITH DRESSERS": "productDetail.relatedLinks.pairDressers",
  "VIEW BEDROOM SETS": "productDetail.relatedLinks.viewBedroomSets",
  "EXPLORE WOOD FINISHES": "productDetail.relatedLinks.woodFinishes",
  "PAIR WITH NIGHTSTANDS": "productDetail.relatedLinks.pairNightstands",
  "VIEW STORAGE CABINETS": "productDetail.relatedLinks.viewStorageCabinets",
  "EXPLORE CARVED WOOD DETAILS": "productDetail.relatedLinks.carvedWood",
  "PAIR WITH VANITY CHAIRS": "productDetail.relatedLinks.pairVanityChairs",
  "PAIR WITH DESK CHAIRS": "productDetail.relatedLinks.pairDeskChairs",
  "VIEW STUDY SETS": "productDetail.relatedLinks.viewStudySets",
  "EXPLORE BENCHES": "productDetail.relatedLinks.exploreBenches",
  "PAIR WITH SIDE CHAIRS": "productDetail.relatedLinks.pairSideChairs",
  "VIEW DESKS & TABLES": "productDetail.relatedLinks.viewDesksTables",
  "PAIR WITH ROUND TABLES": "productDetail.relatedLinks.pairRoundTables",
  "VIEW CHAIRS & BENCHES": "productDetail.relatedLinks.viewChairsBenches",
  "EXPLORE BEDROOM LOUNGE PIECES": "productDetail.relatedLinks.bedroomLoungePieces",
};
const accordionTitleKeys = {
  DETAILS: "productDetail.accordions.titles.details",
  DIMENSIONS: "productDetail.accordions.titles.dimensions",
  MATERIALS: "productDetail.accordions.titles.materials",
  CARE: "productDetail.accordions.titles.care",
  DELIVERY: "productDetail.accordions.titles.delivery",
};
const accordionRowLabelKeys = {
  Design: "productDetail.accordions.rows.design",
  Structure: "productDetail.accordions.rows.structure",
  Comfort: "productDetail.accordions.rows.comfort",
  Compatibility: "productDetail.accordions.rows.compatibility",
  "Queen 1.5m": "productDetail.accordions.rows.queen",
  "King 1.8m": "productDetail.accordions.rows.king",
  "California King 2.0m": "productDetail.accordions.rows.californiaKing",
  "Floor to platform": "productDetail.accordions.rows.floorToPlatform",
  "Headboard depth": "productDetail.accordions.rows.headboardDepth",
  Frame: "productDetail.accordions.rows.frame",
  Upholstery: "productDetail.accordions.rows.upholstery",
  Fill: "productDetail.accordions.rows.fill",
  Feet: "productDetail.accordions.rows.feet",
  "Fabric care": "productDetail.accordions.rows.fabricCare",
  Spills: "productDetail.accordions.rows.spills",
  Sunlight: "productDetail.accordions.rows.sunlight",
  Delivery: "productDetail.accordions.rows.delivery",
  Assembly: "productDetail.accordions.rows.assembly",
  "Lead time": "productDetail.accordions.rows.leadTime",
  Configuration: "productDetail.accordions.rows.configuration",
  "Overall width": "productDetail.accordions.rows.overallWidth",
  "Overall depth": "productDetail.accordions.rows.overallDepth",
  "Overall height": "productDetail.accordions.rows.overallHeight",
  "Seat height": "productDetail.accordions.rows.seatHeight",
  "Arm height": "productDetail.accordions.rows.armHeight",
  Cushions: "productDetail.accordions.rows.cushions",
  "Stocked fabric": "productDetail.accordions.rows.stockedFabric",
  "Custom order": "productDetail.accordions.rows.customOrder",
  Top: "productDetail.accordions.rows.top",
  Base: "productDetail.accordions.rows.base",
  Use: "productDetail.accordions.rows.use",
  "Top thickness": "productDetail.accordions.rows.topThickness",
  "Seating capacity": "productDetail.accordions.rows.seatingCapacity",
  Stone: "productDetail.accordions.rows.stone",
  Wood: "productDetail.accordions.rows.wood",
  "Stone care": "productDetail.accordions.rows.stoneCare",
  "Wood care": "productDetail.accordions.rows.woodCare",
  Heat: "productDetail.accordions.rows.heat",
  Cushion: "productDetail.accordions.rows.cushion",
  "Outdoor care": "productDetail.accordions.rows.outdoorCare",
  "Frame care": "productDetail.accordions.rows.frameCare",
  "Stocked fabrics": "productDetail.accordions.rows.stockedFabrics",
  "Custom cushions": "productDetail.accordions.rows.customCushions",
  Mounting: "productDetail.accordions.rows.mounting",
  Dimming: "productDetail.accordions.rows.dimming",
  "Canopy": "productDetail.accordions.rows.canopy",
  "Cord length": "productDetail.accordions.rows.cordLength",
  Weight: "productDetail.accordions.rows.weight",
  Body: "productDetail.accordions.rows.body",
  Shade: "productDetail.accordions.rows.shade",
  Cleaning: "productDetail.accordions.rows.cleaning",
  "Shade care": "productDetail.accordions.rows.shadeCare",
  Electrical: "productDetail.accordions.rows.electrical",
  Installation: "productDetail.accordions.rows.installation",
};
const mappedText = (keys, value, params) => {
  const key = keys[value];
  return key ? t(key, params) : value;
};
const priceLabel = (value) => mappedText(priceLabelKeys, value);
const stockLabel = (value) => mappedText(stockLabelKeys, value);
const stockStatus = (value) => mappedText(stockStatusKeys, value);
const membershipPromptText = (value) => mappedText(membershipPromptTextKeys, value);
const fabricSelectorLabel = (selector = {}) => {
  const params = { stocked: selector.stockedCount, special: selector.specialOrderCount };
  const key = fabricSelectorLabelKeys[selector.label];
  return key ? t(key, params) : t("productDetail.fabricSelector.generic", params);
};
const availabilityText = (value) => mappedText(availabilityTextKeys, value);
const optionGroupLabel = (key) => mappedText(optionGroupLabelKeys, key);
const optionGroupHelper = (value) => mappedText(optionGroupHelperKeys, value);
const purchaseAssuranceTitle = (value) => mappedText(purchaseAssuranceTitleKeys, value);
const purchaseAssuranceCopy = (value) => mappedText(purchaseAssuranceCopyKeys, value);
const relatedLinkLabel = (value) => mappedText(relatedLinkLabelKeys, value);
const accordionTitle = (value) => mappedText(accordionTitleKeys, value);
const accordionRowLabel = (value) => mappedText(accordionRowLabelKeys, value);

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
    registryStatusMessage.value = t("productDetail.registry.connectedOnly");
    return;
  }
  if (!readYudaoToken()) {
    registryStatusMessage.value = t("productDetail.registry.signInRequired");
    return;
  }
  registryBusy.value = true;
  try {
    const registry = await getMyYudaoGiftRegistry();
    if (!registry?.id) {
      registryStatusMessage.value = t("productDetail.registry.createFirst");
      return;
    }
    await addYudaoGiftRegistryItem(
      registryProductToItemPayload(product.value, {
        registryId: registry.id,
        quantityRequested: normalizedPurchaseQuantity.value || 1,
      }),
    );
    registryStatusMessage.value = t("productDetail.registry.added");
  } catch (error) {
    registryStatusMessage.value = error?.message || t("productDetail.registry.addFailed");
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
    void trackProductDetailView(product.value.id);
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
          <span>{{ t("productDetail.gallery.view", { label: activeGalleryItem.label }) }}</span>
          <span>{{ activeGalleryIndex + 1 }} / {{ detail.gallery.length }}</span>
          <small>{{ t("productDetail.gallery.instructions") }}</small>
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
          <small>{{ priceLabel(detail.price.prefix) }}</small>
          <strong>{{ money(detail.price.member) }}</strong>
          <em>{{ priceLabel(detail.price.memberLabel) }}</em>
          <b v-if="detail.price.sale">{{ money(detail.price.sale) }} {{ priceLabel(detail.price.saleLabel) }}</b>
          <span>{{ money(detail.price.regular) }} {{ priceLabel(detail.price.regularLabel) }}</span>
        </div>
        <p class="product-savings-label">{{ priceLabel(detail.price.savingsLabel) }}</p>
        <p class="product-price-context">{{ priceLabel(detail.price.context) }}</p>

        <section class="product-membership-callout" aria-label="Membership pricing details">
          <div>
            <strong>{{ membershipPromptText(detail.membershipPrompt.title) }}</strong>
            <p>{{ membershipPromptText(detail.membershipPrompt.copy) }}</p>
          </div>
          <a :href="detail.membershipPrompt.href">{{ membershipPromptText(detail.membershipPrompt.linkLabel) }}</a>
        </section>

        <div class="product-mobile-purchase-bar" aria-label="Mobile purchase actions">
          <div>
            <small>{{ priceLabel(detail.price.memberLabel) }}</small>
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
            {{ registryBusy ? t("common.working") : t("productDetail.registry.add") }}
          </button>
        </div>
        <nav class="product-related-links" aria-label="Related product options">
          <a v-for="link in detail.relatedLinks" :key="link.label" :href="link.href">{{ relatedLinkLabel(link.label) }}</a>
        </nav>

        <section class="product-highlights" aria-label="Product highlights">
          <ul>
            <li v-for="item in detail.highlights" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="product-fabric-selector" aria-label="Fabric selector">
          <div class="product-fabric-head">
            <h2>{{ fabricSelectorLabel(detail.fabricSelector) }}</h2>
            <span>{{ t("productDetail.fabricSelector.count", { stocked: detail.fabricSelector.stockedCount, special: detail.fabricSelector.specialOrderCount }) }}</span>
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
              <h2>{{ optionGroupLabel(group.key) }}</h2>
              <p>{{ optionGroupHelper(group.helper) }}</p>
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
          <button type="button" class="product-stock-link">{{ availabilityText(detail.availability.title) }}</button>
          <p>{{ availabilityText(detail.availability.readyToShip) }}</p>
          <small>{{ availabilityText(detail.availability.specialOrder) }}</small>
        </section>

        <section class="product-assurance-grid" aria-label="Delivery and returns">
          <article v-for="item in detail.purchaseAssurance" :key="item.title">
            <h2>{{ purchaseAssuranceTitle(item.title) }}</h2>
            <p>{{ purchaseAssuranceCopy(item.copy) }}</p>
          </article>
        </section>

        <p class="product-stock">{{ stockLabel(detail.stock.label) }} {{ detail.stock.value }} / {{ stockStatus(detail.stock.status) }}</p>
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
            {{ registryBusy ? t("common.working") : t("productDetail.registry.add") }}
          </button>
        </div>
        <p v-if="wishlistStatusMessage" class="product-registry-status" role="status">{{ wishlistStatusMessage }}</p>
        <p v-if="registryStatusMessage" class="product-registry-status" role="status">{{ registryStatusMessage }}</p>

        <section class="product-accordion-list" aria-label="Product details">
          <details v-for="(item, index) in detail.accordions" :key="item.title" :open="index === 0">
            <summary>{{ accordionTitle(item.title) }}</summary>
            <dl>
              <template v-for="row in item.rows" :key="`${item.title}-${row[0]}`">
                <dt>{{ accordionRowLabel(row[0]) }}</dt>
                <dd>{{ row[1] }}</dd>
              </template>
            </dl>
          </details>
        </section>
      </article>
    </div>

    <section class="product-inspiration-section" aria-label="Room inspiration">
      <header>
        <p class="eyebrow">{{ t("productDetail.inspiration.eyebrow") }}</p>
        <h2>{{ t("productDetail.inspiration.title") }}</h2>
        <p>{{ t("productDetail.inspiration.description") }}</p>
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
        <p class="eyebrow">{{ t("productDetail.shopRoom.eyebrow") }}</p>
        <h2>{{ t("productDetail.shopRoom.title") }}</h2>
        <p>{{ t("productDetail.shopRoom.description") }}</p>
      </div>
    </section>

    <section class="product-companion-section" aria-label="Complete the room">
      <header>
        <p class="eyebrow">{{ t("productDetail.completeRoom.eyebrow") }}</p>
        <h2>{{ t("productDetail.completeRoom.title") }}</h2>
        <p>{{ t("productDetail.completeRoom.description") }}</p>
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
