<script setup>
import { computed } from "vue";
import ProductImage from "./ProductImage.vue";
import { getCartTotals } from "../services/localCart.js";
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

const emit = defineEmits(["close", "update-quantity", "remove"]);
const { t } = useI18n();
const totals = computed(() => getCartTotals(props.items));
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
</script>

<template>
  <div v-if="open" class="cart-layer" role="presentation">
    <button class="cart-scrim" type="button" :aria-label="t('close')" @click="emit('close')"></button>
    <aside class="cart-drawer" aria-live="polite">
      <header class="cart-drawer-head">
        <div>
          <p class="eyebrow">{{ totals.quantity }} ITEMS</p>
          <h2>{{ t("cartTitle") }}</h2>
        </div>
        <button class="cart-close" type="button" :aria-label="t('close')" @click="emit('close')">
          <span></span>
          <span></span>
        </button>
      </header>

      <p v-if="items.length === 0" class="cart-empty">{{ t("emptyCart") }}</p>

      <div v-else class="cart-items">
        <article v-for="item in items" :key="item.skuId" class="cart-item">
          <ProductImage :src="item.cover" :label="item.name" />
          <div class="cart-item-main">
            <h3>{{ item.name }}</h3>
            <p>{{ item.subtitle }}</p>
            <strong>{{ money(item.price) }}</strong>
            <div class="cart-item-controls">
              <label>
                {{ t("quantity") }}
                <input
                  :value="item.quantity"
                  min="1"
                  type="number"
                  @change="emit('update-quantity', item, Number($event.target.value))"
                />
              </label>
              <button type="button" @click="emit('remove', item)">{{ t("remove") }}</button>
            </div>
          </div>
        </article>
      </div>

      <footer class="cart-drawer-foot">
        <div>
          <span>{{ t("subtotal") }}</span>
          <strong>{{ money(totals.subtotal) }}</strong>
        </div>
        <button type="button">{{ t("checkout") }}</button>
      </footer>
    </aside>
  </div>
</template>
