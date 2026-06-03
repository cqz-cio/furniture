<script setup>
import { computed } from "vue";
import ProductImage from "./ProductImage.vue";
import { getCartTotals } from "../services/localCart.js";
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
});

const emit = defineEmits(["checkout", "close", "update-quantity", "remove"]);
const { t } = useI18n();
const totals = computed(() => getCartTotals(props.items));
const membershipPricing = computed(() => getMembershipPricing(props.items));
const membershipNotice = computed(() => getMembershipCartNotice(props.items));
const hasRemoteItems = computed(() => props.items.some((item) => item.source === "yudao"));
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
</script>

<template>
  <div v-if="open" class="cart-layer" role="presentation">
    <button class="cart-scrim" type="button" :aria-label="t('common.close')" @click="emit('close')"></button>
    <aside class="cart-drawer" aria-live="polite">
      <header class="cart-drawer-head">
        <div>
          <p class="eyebrow">{{ t("cart.itemCount", { count: totals.quantity }) }}</p>
          <h2>{{ t("cart.title") }}</h2>
          <small>{{ hasRemoteItems ? t("cart.remoteBag") : t("cart.localBag") }}</small>
        </div>
        <button class="cart-close" type="button" :aria-label="t('common.close')" @click="emit('close')">
          <span></span>
          <span></span>
        </button>
      </header>

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
          <ProductImage :src="item.cover" :label="item.name" />
          <div class="cart-item-main">
            <span class="cart-item-source">
              {{ isMembershipItem(item) ? "Membership" : item.source === "yudao" ? "Yudao" : "Preview" }}
            </span>
            <h3>{{ item.name }}</h3>
            <p>{{ item.subtitle }}</p>
            <strong>{{ money(item.price) }}</strong>
            <div class="cart-item-controls">
              <label>
                {{ t("cart.quantity") }}
                <input
                  :value="item.quantity"
                  min="1"
                  type="number"
                  @change="emit('update-quantity', item, Number($event.target.value))"
                />
              </label>
              <button type="button" @click="emit('remove', item)">{{ t("cart.remove") }}</button>
            </div>
          </div>
        </article>
      </div>

      <footer class="cart-drawer-foot">
        <div class="cart-foot-row">
          <span>Merchandise</span>
          <strong>{{ money(membershipPricing.merchandiseSubtotal) }}</strong>
        </div>
        <div v-if="membershipPricing.membershipSubtotal" class="cart-foot-row">
          <span>Membership</span>
          <strong>{{ money(membershipPricing.membershipSubtotal) }}</strong>
        </div>
        <div v-if="membershipPricing.memberDiscount" class="cart-foot-row">
          <span>Member Savings</span>
          <strong>-{{ money(membershipPricing.memberDiscount) }}</strong>
        </div>
        <div class="cart-foot-row">
          <span>{{ t("cart.subtotal") }}</span>
          <strong>{{ money(membershipPricing.estimatedTotal) }}</strong>
        </div>
        <p>{{ t("cart.deliveryNote") }}</p>
        <button type="button" :disabled="items.length === 0" @click="emit('checkout')">{{ t("common.checkout") }}</button>
      </footer>
    </aside>
  </div>
</template>
