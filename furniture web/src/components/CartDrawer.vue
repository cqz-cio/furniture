<script setup>
import { computed, onBeforeUnmount, watch } from "vue";
import ProductImage from "./ProductImage.vue";
import RhFooter from "./RhFooter.vue";
import { canUseYudaoCheckout, getCheckoutPresentation } from "../services/checkoutSession.js";
import { getCartTotals, normalizeCartQuantity } from "../services/localCart.js";
import {
  getMembershipCartNotice,
  getMembershipPricing,
  hasMembershipService,
  isMembershipItem,
} from "../services/membershipCart.js";
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
  wishlistNoticeKey: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["checkout", "close", "resync", "update-quantity", "remove", "wishlist", "add-membership"]);
const { t } = useI18n();
const totals = computed(() => getCartTotals(props.items));
const membershipPricing = computed(() => getMembershipPricing(props.items));
const membershipNotice = computed(() => getMembershipCartNotice(props.items));
const hasMembershipServiceItem = computed(() => hasMembershipService(props.items));
const hasRemoteItems = computed(() => props.items.some((item) => item.source === "yudao"));
const checkoutPresentation = computed(() =>
  getCheckoutPresentation(props.items.length && canUseYudaoCheckout(props.items) ? "yudao" : "local-preview"),
);
const cartCheckoutPreviewMessage = computed(() =>
  props.items.length && !canUseYudaoCheckout(props.items) ? checkoutPresentation.value.message : "",
);
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
const displaySavings = computed(() => membershipPricing.value.memberDiscount);
const displayDelivery = computed(() => (props.items.length ? 299 : 0));
const displaySummaryTotal = computed(() => membershipPricing.value.estimatedTotal + displayDelivery.value);
const regularItemPrice = (item) => {
  const discountRate = membershipPricing.value.discountRate;
  return discountRate ? Math.round((Number(item.price) || 0) / (1 - discountRate)) : Number(item.price) || 0;
};
const handleQuantityChange = (item, value) => {
  emit("update-quantity", item, normalizeCartQuantity(value));
};
const adjustQuantity = (item, delta) => {
  emit("update-quantity", item, normalizeCartQuantity(Number(item.quantity) + delta));
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
            {{ t("cart.promoLine") }}
            <span>{{ t("cart.shop") }}</span>
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

            <div class="cart-topline-spacer" aria-hidden="true"></div>
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
              <h2>{{ t("cart.drawerTitle") }}</h2>
              <small>{{ hasRemoteItems ? t("cart.remoteBag") : t("cart.localBag") }}</small>
            </div>
          </header>

          <div v-if="noticeKey" class="cart-drawer-notice">
            <p>{{ t(noticeKey) }}</p>
            <small v-if="showNoticeDetail" class="cart-notice-detail">{{ noticeDetail }}</small>
            <button
              v-if="canResyncCart"
              class="cart-risk-action cart-risk-action-warning"
              type="button"
              @click="emit('resync')"
            >
              {{ t("cart.retrySync") }}
            </button>
          </div>

          <p v-if="showDebugInfo" class="cart-debug-info">{{ debugInfo }}</p>
          <p v-if="wishlistNoticeKey" class="cart-wishlist-notice" aria-live="polite">{{ t(wishlistNoticeKey) }}</p>

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
                  {{
                    isMembershipItem(item)
                      ? t("cart.itemSource.membership")
                      : item.source === "yudao"
                        ? t("cart.itemSource.yudao")
                        : t("cart.itemSource.preview")
                  }}
                </span>
                <h3>
                  <a v-if="cartItemDetailHref(item)" class="cart-item-title-link" :href="cartItemDetailHref(item)">
                    {{ item.name }}
                  </a>
                  <span v-else>{{ item.name }}</span>
                </h3>
                <dl class="cart-item-specs">
                  <div>
                    <dt>{{ t("cart.fabric") }}</dt>
                    <dd>{{ item.subtitle || t("cart.defaultFabric") }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("cart.color") }}</dt>
                    <dd>{{ t("cart.defaultColor") }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("cart.width") }}</dt>
                    <dd>7'</dd>
                  </div>
                  <div>
                    <dt>{{ t("cart.depth") }}</dt>
                    <dd>Luxe 45"</dd>
                  </div>
                  <div>
                    <dt>{{ t("cart.itemNumber") }}</dt>
                    <dd>{{ item.skuId }} BXCM</dd>
                  </div>
                </dl>
                <small v-if="item.cartProblemKey" class="cart-item-problem">{{ t(item.cartProblemKey) }}</small>
                <div class="cart-item-links">
                  <button
                    class="cart-risk-action cart-risk-action-danger"
                    type="button"
                    @click="emit('remove', item)"
                  >
                    {{ t("cart.remove") }}
                  </button>
                  <button class="cart-risk-action cart-risk-action-neutral" type="button" @click="emit('wishlist', item)">
                    + {{ t("cart.addToWishlist") }}
                  </button>
                </div>
              </div>
              <div class="cart-item-controls">
                <label>
                  <span class="sr-only">{{ t("cart.quantity") }}</span>
                  <span class="cart-quantity-stepper">
                    <button
                      type="button"
                      :disabled="item.quantity <= 1"
                      aria-label="Decrease quantity"
                      @click="adjustQuantity(item, -1)"
                    >
                      -
                    </button>
                    <input
                      :value="item.quantity"
                      min="1"
                      type="number"
                      @change="handleQuantityChange(item, $event.target.value)"
                    />
                    <button type="button" aria-label="Increase quantity" @click="adjustQuantity(item, 1)">
                      +
                    </button>
                  </span>
                </label>
              </div>
              <div class="cart-item-price">
                <strong>{{ money(item.price) }}</strong>
                <span v-if="membershipPricing.memberDiscount">{{ t("cart.member") }}</span>
                <small v-if="membershipPricing.discountRate">{{ money(regularItemPrice(item)) }} {{ t("cart.regular") }}</small>
              </div>
            </article>
          </div>

          <section class="cart-commerce-panel" aria-label="Cart order summary">
            <div class="cart-commerce-left">
              <form class="cart-promo-form">
                <input :aria-label="t('cart.promoCode')" :placeholder="t('cart.promoCode')" type="text" />
                <button class="cart-risk-action cart-risk-action-warning" type="button">{{ t("cart.apply") }}</button>
              </form>
              <aside class="cart-rh-members">
                <strong>RH</strong>
                <span>{{ t("cart.membership.programLine1") }}<br />{{ t("cart.membership.programLine2") }}</span>
                <p>
                  <b>{{ t("cart.membership.description") }}</b>
                  {{ t("cart.membership.checkoutNotice") }}
                </p>
                <button
                  class="cart-risk-action cart-risk-action-warning cart-membership-action"
                  type="button"
                  :disabled="hasMembershipServiceItem"
                  @click="emit('add-membership')"
                >
                  {{ hasMembershipServiceItem ? t("cart.membership.added") : t("cart.membership.add") }}
                </button>
              </aside>
              <small class="cart-members-fineprint">
                {{ t("cart.membership.fineprint") }}
              </small>
            </div>

            <footer class="cart-drawer-foot">
              <header>
                <h3>{{ t("cart.summary.title") }}</h3>
                <span>{{ t("cart.summary.shippingTo") }} <u>94925</u></span>
              </header>
              <div class="cart-foot-row is-muted">
                <span>{{ t("cart.summary.memberSavings") }}</span>
                <strong>-{{ money(displaySavings) }}</strong>
              </div>
              <div class="cart-foot-row">
                <span>{{ t("cart.summary.orderSubtotal") }}</span>
                <strong>{{ money(membershipPricing.estimatedTotal) }}</strong>
              </div>
              <div class="cart-foot-row">
                <span>{{ t("cart.summary.membersProgram") }}</span>
                <strong>{{ money(membershipPricing.membershipSubtotal) }}</strong>
              </div>
              <div class="cart-foot-row">
                <span><u>{{ t("cart.summary.unlimitedDelivery") }}</u></span>
                <strong>{{ money(displayDelivery) }}</strong>
              </div>
              <div class="cart-foot-total">
                <span>{{ t("cart.summary.totalExcludingTax") }}</span>
                <strong>{{ money(displaySummaryTotal) }}</strong>
              </div>
              <small v-if="cartCheckoutPreviewMessage" class="cart-checkout-preview-note">
                {{ cartCheckoutPreviewMessage }}
              </small>
              <button type="button" :disabled="items.length === 0" @click="emit('checkout')">
                {{ cartCheckoutPreviewMessage ? checkoutPresentation.cta : t("common.checkout") }}
              </button>
            </footer>
          </section>

          <RhFooter />
        </main>
      </aside>
    </div>
  </Transition>
</template>
