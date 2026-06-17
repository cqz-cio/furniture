<script setup>
import { computed, onBeforeUnmount, watch } from "vue";
import ProductImage from "./ProductImage.vue";
import RhFooter from "./RhFooter.vue";
import { getCartTotals, normalizeCartQuantity } from "../services/localCart.js";
import { getMembershipCartNotice, getMembershipPricing, isMembershipItem } from "../services/membershipCart.js";
import { useI18n } from "../i18n.js";

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  items: {
    type: Array,
    default: () => [],
  },
  noticeKey: {
    type: String,
    default: "",
  },
  noticeDetail: {
    type: String,
    default: "",
  },
  debugInfo: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["checkout", "close", "resync", "update-quantity", "remove"]);
const { t } = useI18n();
const totals = computed(() => getCartTotals(props.items));
const membershipPricing = computed(() => getMembershipPricing(props.items));
const membershipNotice = computed(() => getMembershipCartNotice(props.items));
const hasRemoteItems = computed(() => props.items.some((item) => item.source === "yudao"));
const cartPrimaryNavItems = [
  { key: "navigation.primary.living", href: "/sofas-plp" },
  { key: "navigation.primary.dining", href: "/sofas-plp" },
  { key: "navigation.primary.bed", href: "/sofas-plp" },
  { key: "navigation.primary.bath", href: "/missing" },
  { key: "navigation.primary.outdoor", href: "/outdoor" },
  { key: "navigation.primary.lighting", href: "/sofas-plp" },
  { key: "navigation.primary.textiles", href: "/missing" },
  { key: "navigation.primary.rugs", href: "/missing" },
  { key: "navigation.primary.decor", href: "/missing" },
  { key: "navigation.primary.babyChild", href: "/baby-child" },
  { key: "navigation.primary.teen", href: "/teen" },
  { key: "navigation.primary.sale", href: "/sale", accent: true },
  { key: "navigation.primary.interiorDesign", href: "/missing" },
];
const canResyncCart = computed(() =>
  ["cart.remoteUnavailable", "cart.remoteAuthRequired", "cart.remoteMutationUnavailable"].includes(props.noticeKey),
);
const showNoticeDetail = computed(() => Boolean(props.noticeDetail) && import.meta.env.DEV);
const showDebugInfo = computed(() => Boolean(props.debugInfo) && import.meta.env.DEV);
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const displaySavings = computed(() => membershipPricing.value.memberDiscount || Math.round(membershipPricing.value.merchandiseSubtotal * 0.3));
const displayDelivery = computed(() => (props.items.length ? 299 : 0));
const displaySummaryTotal = computed(() => membershipPricing.value.estimatedTotal + displayDelivery.value);
const handleQuantityChange = (item, value) => {
  emit("update-quantity", item, normalizeCartQuantity(value));
};
const cartItemDetailHref = (item) => {
  if (isMembershipItem(item)) return "";
  const productId = item?.id || item?.spuId;
  return productId ? `/sofa-pdp?id=${encodeURIComponent(productId)}` : "";
};
const setBodyCartState = (isOpen) => {
  if (typeof document === "undefined") return;
  document.body.classList.toggle("cart-layer-open", isOpen);
};

watch(() => props.open, setBodyCartState, { immediate: true });
onBeforeUnmount(() => setBodyCartState(false));
</script>

<template>
  <Transition name="cart-drawer-slide">
    <div v-if="open" class="cart-layer" role="presentation">
      <button class="cart-scrim" type="button" tabindex="-1" :aria-label="t('common.close')" @click="emit('close')"></button>
      <aside class="cart-drawer" role="dialog" aria-modal="true" :aria-label="t('cart.title')" aria-live="polite">
        <header class="cart-full-header">
          <p class="cart-promo-line">
            The summer sale. RH members save up to 70% on hundreds of new items.
            <span>Shop</span>
          </p>

          <div class="cart-topline">
            <div class="cart-header-tools" :aria-label="`${t('header.menuOpen')} / ${t('common.search')}`">
              <span class="menu-icon" aria-hidden="true">
                <span></span>
                <span></span>
                <span></span>
              </span>
              <span class="search-icon" aria-hidden="true"></span>
            </div>

            <img class="cart-brand-logo" src="/assets/brand/oakved-logo-black.png" alt="Oakved" />

            <div class="cart-header-actions" :aria-label="`${t('header.account')} / ${t('header.bag')}`">
              <span>USA</span>
              <span class="account-icon" aria-hidden="true"></span>
              <span class="bag-icon" aria-hidden="true">
                <span v-if="totals.quantity" class="bag-count">{{ totals.quantity }}</span>
              </span>
            </div>
          </div>

          <nav class="cart-primary-nav" aria-label="Primary navigation">
            <a v-for="item in cartPrimaryNavItems" :key="item.key" :class="['cart-nav-link', { accent: item.accent }]" :href="item.href">
              {{ t(item.key) }}
            </a>
          </nav>

          <button class="cart-close" type="button" :aria-label="t('common.close')" @click="emit('close')">
            <span></span>
            <span></span>
          </button>
        </header>

        <main class="cart-full-main">
          <header class="cart-drawer-head">
            <div>
              <p class="eyebrow">{{ t("cart.itemCount", { count: totals.quantity }) }}</p>
              <h2>Cart</h2>
              <small>{{ hasRemoteItems ? t("cart.remoteBag") : t("cart.localBag") }}</small>
            </div>
          </header>

          <div v-if="noticeKey" class="cart-drawer-notice">
            <p>{{ t(noticeKey) }}</p>
            <small v-if="showNoticeDetail" class="cart-notice-detail">{{ noticeDetail }}</small>
            <button v-if="canResyncCart" type="button" @click="emit('resync')">
              {{ t("cart.retrySync") }}
            </button>
          </div>

          <p v-if="showDebugInfo" class="cart-debug-info">{{ debugInfo }}</p>

          <div v-if="items.length === 0" class="cart-empty">
            <p>{{ t("cart.empty") }}</p>
            <span>{{ t("cart.emptyHelp") }}</span>
          </div>

          <div v-else class="cart-items">
            <section class="cart-membership-notice" aria-label="Membership pricing">
              <strong>{{ membershipNotice.title }}</strong>
              <p>{{ membershipNotice.message }}</p>
            </section>
            <article v-for="item in items" :key="item.skuId" class="cart-item">
              <a v-if="cartItemDetailHref(item)" class="cart-item-media-link" :href="cartItemDetailHref(item)">
                <ProductImage :src="item.cover" :label="item.name" />
              </a>
              <ProductImage v-else :src="item.cover" :label="item.name" />
              <div class="cart-item-main">
                <span class="cart-item-source">
                  {{ isMembershipItem(item) ? "Membership" : item.source === "yudao" ? "Yudao" : "Preview" }}
                </span>
                <h3>
                  <a v-if="cartItemDetailHref(item)" class="cart-item-title-link" :href="cartItemDetailHref(item)">
                    {{ item.name }}
                  </a>
                  <span v-else>{{ item.name }}</span>
                </h3>
                <dl class="cart-item-specs">
                  <div>
                    <dt>Fabric</dt>
                    <dd>{{ item.subtitle || "Perennials Performance Textured Linen Weave" }}</dd>
                  </div>
                  <div>
                    <dt>Color</dt>
                    <dd>Wheat</dd>
                  </div>
                  <div>
                    <dt>Width</dt>
                    <dd>7'</dd>
                  </div>
                  <div>
                    <dt>Depth</dt>
                    <dd>Luxe 45"</dd>
                  </div>
                  <div>
                    <dt>Item#</dt>
                    <dd>{{ item.skuId }} BXCM</dd>
                  </div>
                </dl>
                <small v-if="item.cartProblemKey" class="cart-item-problem">{{ t(item.cartProblemKey) }}</small>
                <div class="cart-item-links">
                  <button type="button" @click="emit('remove', item)">{{ t("cart.remove") }}</button>
                  <span>+ Add To Wishlist</span>
                </div>
              </div>
              <div class="cart-item-controls">
                <label>
                  <span class="sr-only">{{ t("cart.quantity") }}</span>
                  <input
                    :value="item.quantity"
                    min="1"
                    type="number"
                    @change="handleQuantityChange(item, $event.target.value)"
                  />
                </label>
              </div>
              <div class="cart-item-price">
                <strong>{{ money(item.price) }}</strong>
                <span v-if="membershipPricing.memberDiscount">Member</span>
                <small>{{ money(Math.round(item.price / 0.7)) }} Regular</small>
              </div>
            </article>
          </div>

          <section class="cart-commerce-panel" aria-label="Cart order summary">
            <div class="cart-commerce-left">
              <form class="cart-promo-form">
                <input aria-label="Promo code" placeholder="Promo code" type="text" />
                <button type="button">Apply</button>
              </form>
              <aside class="cart-rh-members">
                <strong>RH</strong>
                <span>Members<br />Program</span>
                <p>
                  <b>Save 30% on everything RH*</b>
                  For $200 annually, member benefits include 30% savings on all full-priced items and complimentary
                  services with RH Interior Design.
                </p>
              </aside>
              <small class="cart-members-fineprint">
                *Compared to regular price. Limited exclusions apply. See RH Members Program Terms & Conditions for details.
              </small>
            </div>

            <footer class="cart-drawer-foot">
              <header>
                <h3>Order Summary</h3>
                <span>Shipping to <u>94925</u></span>
              </header>
              <div class="cart-foot-row is-muted">
                <span>Member Savings</span>
                <strong>-{{ money(displaySavings) }}</strong>
              </div>
              <div class="cart-foot-row">
                <span>Order Subtotal</span>
                <strong>{{ money(membershipPricing.estimatedTotal) }}</strong>
              </div>
              <div class="cart-foot-row">
                <span>RH Members Program</span>
                <strong>{{ money(membershipPricing.membershipSubtotal || 200) }}</strong>
              </div>
              <div class="cart-foot-row">
                <span><u>Unlimited Furniture Delivery</u></span>
                <strong>{{ money(displayDelivery) }}</strong>
              </div>
              <div class="cart-foot-total">
                <span>Total (excluding sales tax)</span>
                <strong>{{ money(displaySummaryTotal) }}</strong>
              </div>
              <button type="button" :disabled="items.length === 0" @click="emit('checkout')">Checkout</button>
            </footer>
          </section>

          <RhFooter />
        </main>
      </aside>
    </div>
  </Transition>
</template>
