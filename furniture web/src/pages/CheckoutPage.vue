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
import { getMembershipPricing, isMembershipItem } from "../services/membershipCart.js";
import {
  createOrder,
  getAddressList,
  getDefaultAddress,
  readYudaoToken,
  settleOrder,
} from "../services/yudaoClient.js";
import { useI18n } from "../i18n.js";

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
const membershipPricing = computed(() => getMembershipPricing(props.items));
const mode = computed(() => getCheckoutMode(props.items, readYudaoToken()));
const checkoutModeKey = computed(() => `checkout.mode.${mode.value}`);
const displaySubtotal = computed(() => settlement.value?.payPrice ?? membershipPricing.value.estimatedTotal);
const displayDelivery = computed(() => settlement.value?.deliveryPrice ?? 0);
const displayItemTotal = computed(() => settlement.value?.totalPrice ?? membershipPricing.value.merchandiseSubtotal);
const displayEstimatedTotal = computed(() => displaySubtotal.value);
const selectedAddress = computed(() => addresses.value.find((address) => address.id === selectedAddressId.value));
const { t } = useI18n();
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

const handlePrimaryAction = () => {
  if (mode.value === "empty") {
    window.location.href = "/";
    return;
  }
  submitOrder();
};

onMounted(loadCheckoutData);
</script>

<template>
  <section class="checkout-page">
    <header class="checkout-head">
      <p class="eyebrow">{{ t("checkout.eyebrow") }}</p>
      <h1>{{ t(`${checkoutModeKey}.title`) }}</h1>
      <p>{{ t(`${checkoutModeKey}.message`) }}</p>
    </header>

    <p v-if="error" class="checkout-error">{{ error }}</p>

    <section class="checkout-grid">
      <div class="checkout-main">
        <section class="checkout-status-card">
          <span>01</span>
          <div>
            <h2>{{ t("checkout.statusTitle") }}</h2>
            <p>{{ t(`${checkoutModeKey}.status`) }}</p>
          </div>
        </section>

        <section v-if="addresses.length" class="checkout-addresses">
          <div class="checkout-section-title">
            <span>02</span>
            <div>
              <h2>{{ t("checkout.deliveryTitle") }}</h2>
              <p v-if="selectedAddress">{{ selectedAddress.label }}</p>
            </div>
          </div>
          <label>
            {{ t("checkout.shipTo") }}
            <select v-model.number="selectedAddressId" @change="loadCheckoutData">
              <option v-for="address in addresses" :key="address.id" :value="address.id">{{ address.label }}</option>
            </select>
          </label>
        </section>

        <section class="checkout-items">
          <div class="checkout-section-title">
            <span>03</span>
            <div>
              <h2>{{ t("checkout.itemsTitle") }}</h2>
              <p>{{ t("checkout.itemsCount", { count: summary.quantity }) }}</p>
            </div>
          </div>
          <article v-for="item in items" :key="item.skuId" class="checkout-item">
            <ProductImage :src="item.cover" :label="item.name" />
            <div>
              <p class="checkout-item-kicker">
                {{ isMembershipItem(item) ? "Membership" : item.source === "yudao" ? t("checkout.itemKickerYudao") : t("checkout.itemKickerPreview") }}
              </p>
              <h3>{{ item.name }}</h3>
              <p>{{ item.subtitle }}</p>
              <strong>{{ item.quantity }} x {{ money(item.price) }}</strong>
            </div>
          </article>
          <p v-if="items.length === 0" class="checkout-empty-note">{{ t("checkout.emptyNote") }}</p>
        </section>
      </div>
      <aside class="checkout-summary">
        <p class="eyebrow">{{ t("checkout.summaryTitle") }}</p>
        <div class="summary-row">
          <span>{{ t("checkout.pieces") }}</span>
          <strong>{{ summary.quantity }}</strong>
        </div>
        <div class="summary-row">
          <span>{{ t("checkout.merchandise") }}</span>
          <strong>{{ money(displayItemTotal) }}</strong>
        </div>
        <div v-if="membershipPricing.membershipSubtotal" class="summary-row">
          <span>Membership</span>
          <strong>{{ money(membershipPricing.membershipSubtotal) }}</strong>
        </div>
        <div v-if="membershipPricing.memberDiscount" class="summary-row">
          <span>Member Savings</span>
          <strong>-{{ money(membershipPricing.memberDiscount) }}</strong>
        </div>
        <div class="summary-row">
          <span>{{ t("checkout.delivery") }}</span>
          <strong>{{ money(displayDelivery) }}</strong>
        </div>
        <div class="summary-total">
          <span>{{ t("checkout.estimatedTotal") }}</span>
          <strong>{{ money(displayEstimatedTotal) }}</strong>
        </div>
        <small v-if="settlement">{{ t("checkout.settlementIncluded") }}</small>
        <small v-else>{{ t("checkout.settlementPending") }}</small>
        <button type="button" :disabled="busy || (mode !== 'yudao' && mode !== 'empty')" @click="handlePrimaryAction">
          {{ busy ? t("common.working") : t(`${checkoutModeKey}.cta`) }}
        </button>
      </aside>
    </section>
  </section>
</template>
