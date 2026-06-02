<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import {
  buildLocalCheckoutSummary,
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getCheckoutMode,
  getSelectedAddressId,
} from "../services/checkoutSession.js";
import {
  createOrder,
  getAddressList,
  getDefaultAddress,
  readYudaoToken,
  settleOrder,
} from "../services/yudaoClient.js";

const emit = defineEmits(["order-created"]);

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
});

const addresses = ref([]);
const defaultAddress = ref(null);
const selectedAddressId = ref(undefined);
const settlement = ref(null);
const error = ref("");
const busy = ref(false);
const summary = computed(() => buildLocalCheckoutSummary(props.items));
const mode = computed(() => getCheckoutMode(props.items, readYudaoToken()));
const displaySubtotal = computed(() => settlement.value?.payPrice ?? summary.value.subtotal);
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const loadCheckoutData = async () => {
  if (!canUseYudaoCheckout(props.items) || !readYudaoToken()) return;
  busy.value = true;
  error.value = "";
  try {
    addresses.value = await getAddressList();
    defaultAddress.value = await getDefaultAddress();
    selectedAddressId.value = getSelectedAddressId(selectedAddressId.value, defaultAddress.value);
    if (selectedAddressId.value) {
      const payload = buildYudaoOrderPayload(props.items, { addressId: selectedAddressId.value });
      settlement.value = await settleOrder(payload);
    }
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
};

const submitOrder = async () => {
  const addressId = getSelectedAddressId(selectedAddressId.value, defaultAddress.value);
  if (!addressId) {
    error.value = "No Yudao address is available for this user.";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const payload = buildYudaoOrderPayload(props.items, { addressId });
    const result = await createOrder(payload);
    emit("order-created", result.id);
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
};

onMounted(loadCheckoutData);
</script>

<template>
  <section class="checkout-page">
    <header class="checkout-head">
      <p class="eyebrow">Checkout</p>
      <h1>Review Your Order</h1>
      <p v-if="mode === 'token-required'">Add a Yudao App token before creating a remote order.</p>
      <p v-else-if="mode === 'local-preview'">Demo cart items can be reviewed locally but cannot create a Yudao order.</p>
      <p v-else-if="mode === 'empty'">Your bag is empty.</p>
    </header>

    <section v-if="addresses.length" class="checkout-addresses">
      <label>
        Ship To
        <select v-model.number="selectedAddressId" @change="loadCheckoutData">
          <option v-for="address in addresses" :key="address.id" :value="address.id">{{ address.label }}</option>
        </select>
      </label>
    </section>

    <p v-if="error" class="checkout-error">{{ error }}</p>

    <section class="checkout-grid">
      <div class="checkout-items">
        <article v-for="item in items" :key="item.skuId" class="checkout-item">
          <ProductImage :src="item.cover" :label="item.name" />
          <div>
            <h2>{{ item.name }}</h2>
            <p>{{ item.quantity }} x {{ money(item.price) }}</p>
          </div>
        </article>
      </div>
      <aside class="checkout-summary">
        <span>Subtotal</span>
        <strong>{{ money(displaySubtotal) }}</strong>
        <small v-if="settlement">Includes Yudao settlement pricing.</small>
        <button type="button" :disabled="busy || mode !== 'yudao'" @click="submitOrder">
          {{ busy ? "Working..." : "Create Yudao Order" }}
        </button>
      </aside>
    </section>
  </section>
</template>
