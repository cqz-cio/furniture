<script setup>
import { computed, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { buildCheckoutFlow, canPlaceCheckoutOrder } from "../services/checkoutFlow.js";
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
  authVersion: {
    type: Number,
    default: 0,
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
const checkoutAddress = computed(() => {
  const address = selectedAddress.value || defaultAddress.value;

  if (!address) {
    return {
      line1: "12 Main",
      city: "Boston",
      region: "MA",
      postalCode: "02116",
    };
  }

  return {
    line1: address.detailAddress || address.raw?.detailAddress || address.label,
    city: address.areaName || address.raw?.areaName || "",
    region: address.raw?.region || "",
    postalCode: address.raw?.postalCode || "02116-0000",
  };
});
const checkoutFlow = computed(() =>
  buildCheckoutFlow(props.items, {
    address: checkoutAddress.value,
    customNoticeAccepted: true,
    paymentMethod: "card",
    cardComplete: true,
    termsAccepted: true,
  }),
);
const checkoutStepLabels = {
  details: "Checkout Details",
  "custom-check": "Custom Item Check",
  "shipping-address": "Shipping Address",
  "address-verification": "Address Verification",
  payment: "Payment",
  review: "Terms & Agreements",
  "place-order": "Place Order",
  "delivery-notes": "Delivery & Assembly Notes",
};
const primaryActionDisabled = computed(
  () => busy.value || (mode.value !== "yudao" && mode.value !== "empty") || (mode.value === "yudao" && !canPlaceCheckoutOrder(checkoutFlow.value)),
);
const { t } = useI18n();
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
let checkoutRequestId = 0;

const clearRemoteCheckoutData = ({ preserveAddressSelection = false } = {}) => {
  const previousAddressId = selectedAddressId.value;
  addresses.value = [];
  defaultAddress.value = null;
  selectedAddressId.value = preserveAddressSelection ? previousAddressId : undefined;
  settlement.value = null;
};

const loadCheckoutData = async (options = {}) => {
  const requestId = ++checkoutRequestId;
  clearRemoteCheckoutData(options);
  error.value = "";
  if (!canUseYudaoCheckout(props.items) || !readYudaoToken()) return;
  busy.value = true;
  try {
    const nextAddresses = await getAddressList();
    if (requestId !== checkoutRequestId) return;
    const nextDefaultAddress = await getDefaultAddress();
    if (requestId !== checkoutRequestId) return;
    addresses.value = nextAddresses;
    defaultAddress.value = nextDefaultAddress;
    selectedAddressId.value = getSelectedAddressId(selectedAddressId.value, nextDefaultAddress);
    if (selectedAddressId.value) {
      const payload = buildYudaoOrderPayload(props.items, { addressId: selectedAddressId.value });
      const nextSettlement = await settleOrder(payload);
      if (requestId !== checkoutRequestId) return;
      settlement.value = nextSettlement;
    }
  } catch {
    if (requestId !== checkoutRequestId) return;
    error.value = "Checkout service is unavailable. Please try again later.";
  } finally {
    if (requestId === checkoutRequestId) busy.value = false;
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
  } catch {
    error.value = "Order service is unavailable. Please try again later.";
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
watch(() => props.authVersion, loadCheckoutData);
watch(() => props.items, () => loadCheckoutData({ preserveAddressSelection: true }), { deep: true });
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

        <section class="checkout-flow-rail" aria-label="Checkout flow">
          <article v-for="(step, index) in checkoutFlow.steps" :key="step.key" class="checkout-flow-card" :class="`is-${step.status}`">
            <span>{{ String(index + 1).padStart(2, "0") }}</span>
            <strong>{{ checkoutStepLabels[step.key] }}</strong>
            <small>{{ step.status }}</small>
          </article>
        </section>

        <section class="checkout-custom-notice">
          <div class="checkout-section-title">
            <span>02</span>
            <div>
              <h2>{{ checkoutFlow.customNotice.title }}</h2>
              <p>{{ checkoutFlow.customNotice.message }}</p>
            </div>
          </div>
        </section>

        <section v-if="addresses.length" class="checkout-addresses">
          <div class="checkout-section-title">
            <span>03</span>
            <div>
              <h2>{{ t("checkout.deliveryTitle") }}</h2>
              <p v-if="selectedAddress">{{ selectedAddress.label }}</p>
            </div>
          </div>
          <label>
            {{ t("checkout.shipTo") }}
            <select v-model.number="selectedAddressId" @change="loadCheckoutData({ preserveAddressSelection: true })">
              <option v-for="address in addresses" :key="address.id" :value="address.id">{{ address.label }}</option>
            </select>
          </label>
        </section>

        <section class="checkout-address-verification">
          <div class="checkout-section-title">
            <span>04</span>
            <div>
              <h2>Address Verification</h2>
              <p>{{ checkoutFlow.addressVerification.issue || "Shipping address is verified for payment." }}</p>
            </div>
          </div>
          <article v-if="checkoutFlow.addressVerification.suggestedAddress">
            <p class="eyebrow">Suggested Address</p>
            <strong>{{ checkoutFlow.addressVerification.suggestedAddress.line1 }}</strong>
            <span>{{ checkoutFlow.addressVerification.suggestedAddress.postalCode }}</span>
          </article>
        </section>

        <section class="checkout-items">
          <div class="checkout-section-title">
            <span>05</span>
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

        <section class="checkout-payment-panel">
          <div class="checkout-section-title">
            <span>06</span>
            <div>
              <h2>Payment Method</h2>
              <p>Card payment, member credit and gift card rules are reviewed before order placement.</p>
            </div>
          </div>
          <dl>
            <div>
              <dt>Method</dt>
              <dd>{{ checkoutFlow.payment.method === "card" ? "Credit Card" : checkoutFlow.payment.method }}</dd>
            </div>
            <div>
              <dt>Card Details</dt>
              <dd>{{ checkoutFlow.payment.cardComplete ? "Ready for secure entry" : "Required before placing order" }}</dd>
            </div>
          </dl>
        </section>

        <section class="checkout-terms-panel">
          <div class="checkout-section-title">
            <span>07</span>
            <div>
              <h2>Terms & Agreements</h2>
              <p>Custom order notices, membership renewal language and checkout terms stay visible before submission.</p>
            </div>
          </div>
        </section>

        <section class="checkout-delivery-notes">
          <div class="checkout-section-title">
            <span>08</span>
            <div>
              <h2>Delivery & Assembly Notes</h2>
              <p>Large furniture, lighting installation and final delivery notes are collected after order placement.</p>
            </div>
          </div>
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
        <button type="button" :disabled="primaryActionDisabled" @click="handlePrimaryAction">
          {{ busy ? t("common.working") : t(`${checkoutModeKey}.cta`) }}
        </button>
      </aside>
    </section>
  </section>
</template>
