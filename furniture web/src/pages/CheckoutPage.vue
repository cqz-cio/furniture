<script setup>
import { computed, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { getCheckoutErrorKey } from "../services/checkoutErrors.js";
import { getCheckoutRecoveryAction } from "../services/checkoutRecovery.js";
import { buildCheckoutFlow, canPlaceCheckoutOrder } from "../services/checkoutFlow.js";
import {
  buildLocalCheckoutSummary,
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getCheckoutMode,
  getSelectedAddressId,
} from "../services/checkoutSession.js";
import { getMembershipPricing, isMembershipItem } from "../services/membershipCart.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { getAddressList, getDefaultAddress } from "../services/yudaoMemberApi.js";
import { createOrder, settleOrder } from "../services/yudaoOrderApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";
import { useI18n } from "../i18n.js";

const emit = defineEmits(["order-created", "open-cart"]);

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
const errorKey = ref("");
const busy = ref(false);
const customNoticeAccepted = ref(false);
const useSuggestedAddress = ref(false);
const paymentMethod = ref("card");
const cardComplete = ref(false);
const termsAccepted = ref(false);
const paymentMethodOptions = [
  {
    value: "card",
    labelKey: "checkout.payment.methods.card.label",
    descriptionKey: "checkout.payment.methods.card.description",
  },
  {
    value: "gift-card",
    labelKey: "checkout.payment.methods.giftCard.label",
    descriptionKey: "checkout.payment.methods.giftCard.description",
  },
  {
    value: "member-credit",
    labelKey: "checkout.payment.methods.memberCredit.label",
    descriptionKey: "checkout.payment.methods.memberCredit.description",
  },
];
const summary = computed(() => buildLocalCheckoutSummary(props.items));
const membershipPricing = computed(() => getMembershipPricing(props.items));
const mode = computed(() => getCheckoutMode(props.items, readYudaoToken()));
const checkoutModeKey = computed(() => `checkout.mode.${mode.value}`);
const displaySubtotal = computed(() => settlement.value?.payPrice ?? membershipPricing.value.estimatedTotal);
const displayDelivery = computed(() => settlement.value?.deliveryPrice ?? 0);
const displayItemTotal = computed(() => settlement.value?.totalPrice ?? membershipPricing.value.merchandiseSubtotal);
const displayEstimatedTotal = computed(() => displaySubtotal.value);
const error = computed(() => (errorKey.value ? t(errorKey.value) : ""));
const checkoutRecoveryAction = computed(() =>
  getCheckoutRecoveryAction(errorKey.value, {
    addressBook: membershipRoutes.accountAddressBook,
    checkoutAuth: membershipRoutes.checkoutAuth,
  }),
);
const selectedAddress = computed(() => addresses.value.find((address) => address.id === selectedAddressId.value));
const hasCheckoutAddress = computed(() => Boolean(selectedAddress.value || defaultAddress.value));
const checkoutAddress = computed(() => {
  const address = selectedAddress.value || defaultAddress.value;

  if (!address) {
    return null;
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
    customNoticeAccepted: customNoticeAccepted.value,
    useSuggestedAddress: useSuggestedAddress.value,
    paymentMethod: paymentMethod.value,
    cardComplete: cardComplete.value,
    termsAccepted: termsAccepted.value,
  }),
);
const selectedPaymentOption = computed(
  () => paymentMethodOptions.find((option) => option.value === paymentMethod.value) || paymentMethodOptions[0],
);
const checkoutStepLabelKeys = {
  details: "checkout.steps.details",
  "custom-check": "checkout.steps.customCheck",
  "shipping-address": "checkout.steps.shippingAddress",
  "address-verification": "checkout.steps.addressVerification",
  payment: "checkout.steps.payment",
  review: "checkout.steps.review",
  "place-order": "checkout.steps.placeOrder",
  "delivery-notes": "checkout.steps.deliveryNotes",
};
const customNoticeTitleKey = computed(() =>
  checkoutFlow.value.customNotice.required ? "checkout.customNotice.requiredTitle" : "checkout.customNotice.clearTitle",
);
const customNoticeMessageKey = computed(() =>
  checkoutFlow.value.customNotice.required ? "checkout.customNotice.requiredMessage" : "checkout.customNotice.clearMessage",
);
const addressVerificationMessage = computed(() => {
  if (checkoutFlow.value.addressVerification.status === "missing") return t("checkout.address.required");
  if (checkoutFlow.value.addressVerification.status === "issue") return t("checkout.address.needsVerification");
  return t("checkout.address.verified");
});
const canReviewPayment = computed(() => mode.value === "yudao" && hasCheckoutAddress.value && checkoutFlow.value.readyForPayment);
const primaryActionDisabled = computed(
  () => busy.value || (mode.value !== "yudao" && mode.value !== "empty") || (mode.value === "yudao" && !canPlaceCheckoutOrder(checkoutFlow.value)),
);
const { t } = useI18n();
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
let checkoutRequestId = 0;

const resetCheckoutConfirmations = () => {
  customNoticeAccepted.value = false;
  useSuggestedAddress.value = false;
  paymentMethod.value = "card";
  cardComplete.value = false;
  termsAccepted.value = false;
};

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
  resetCheckoutConfirmations();
  errorKey.value = "";
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
    if (!selectedAddressId.value) {
      errorKey.value = "checkout.errors.noAddress";
      return;
    }
    if (selectedAddressId.value) {
      const payload = buildYudaoOrderPayload(props.items, { addressId: selectedAddressId.value });
      const nextSettlement = await settleOrder(payload);
      if (requestId !== checkoutRequestId) return;
      settlement.value = nextSettlement;
    }
  } catch (caught) {
    if (requestId !== checkoutRequestId) return;
    errorKey.value = getCheckoutErrorKey(caught, "checkout.errors.loadUnavailable");
  } finally {
    if (requestId === checkoutRequestId) busy.value = false;
  }
};

const submitOrder = async () => {
  const addressId = getSelectedAddressId(selectedAddressId.value, defaultAddress.value);
  if (!addressId) {
    errorKey.value = "checkout.errors.noAddress";
    return;
  }
  busy.value = true;
  errorKey.value = "";
  try {
    const payload = buildYudaoOrderPayload(props.items, { addressId });
    const result = await createOrder(payload);
    emit("order-created", result.id);
  } catch (caught) {
    errorKey.value = getCheckoutErrorKey(caught, "checkout.errors.orderUnavailable");
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

const handleCheckoutRecoveryAction = () => {
  if (!checkoutRecoveryAction.value) return;
  if (checkoutRecoveryAction.value.type === "emit") {
    emit(checkoutRecoveryAction.value.event);
    return;
  }
  if (checkoutRecoveryAction.value.type === "retry") {
    loadCheckoutData({ preserveAddressSelection: true });
  }
};

onMounted(loadCheckoutData);
watch(() => props.authVersion, loadCheckoutData);
watch(() => props.items, () => loadCheckoutData({ preserveAddressSelection: true }), { deep: true });
watch(paymentMethod, () => {
  cardComplete.value = false;
});
</script>

<template>
  <section class="checkout-page">
    <header class="checkout-head">
      <p class="eyebrow">{{ t("checkout.eyebrow") }}</p>
      <h1>{{ t(`${checkoutModeKey}.title`) }}</h1>
      <p>{{ t(`${checkoutModeKey}.message`) }}</p>
    </header>

    <div v-if="error" class="checkout-error">
      <p>{{ error }}</p>
      <div v-if="checkoutRecoveryAction" class="checkout-error-actions">
        <a v-if="checkoutRecoveryAction.type === 'link'" class="checkout-recovery-action" :href="checkoutRecoveryAction.href">
          {{ t(checkoutRecoveryAction.labelKey) }}
        </a>
        <button v-else type="button" class="checkout-recovery-action" @click="handleCheckoutRecoveryAction">
          {{ t(checkoutRecoveryAction.labelKey) }}
        </button>
      </div>
    </div>

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
            <strong>{{ t(checkoutStepLabelKeys[step.key]) }}</strong>
            <small>{{ step.status }}</small>
          </article>
        </section>

        <section class="checkout-custom-notice">
          <div class="checkout-section-title">
            <span>02</span>
            <div>
              <h2>{{ t(customNoticeTitleKey) }}</h2>
              <p>{{ t(customNoticeMessageKey) }}</p>
            </div>
          </div>
          <label v-if="checkoutFlow.customNotice.required" class="checkout-confirm-row">
            <input v-model="customNoticeAccepted" type="checkbox" />
            <span>{{ t("checkout.confirm.customNotice") }}</span>
          </label>
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
              <h2>{{ t("checkout.steps.addressVerification") }}</h2>
              <p>{{ addressVerificationMessage }}</p>
            </div>
          </div>
          <article v-if="checkoutFlow.addressVerification.suggestedAddress">
            <p class="eyebrow">{{ t("checkout.address.suggested") }}</p>
            <strong>{{ checkoutFlow.addressVerification.suggestedAddress.line1 }}</strong>
            <span>{{ checkoutFlow.addressVerification.suggestedAddress.postalCode }}</span>
            <label class="checkout-confirm-row">
              <input v-model="useSuggestedAddress" type="checkbox" />
              <span>{{ t("checkout.confirm.useSuggestedAddress") }}</span>
            </label>
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
                {{ isMembershipItem(item) ? t("checkout.itemKickerMembership") : item.source === "yudao" ? t("checkout.itemKickerYudao") : t("checkout.itemKickerPreview") }}
              </p>
              <h3>{{ item.name }}</h3>
              <p>{{ item.subtitle }}</p>
              <strong>{{ item.quantity }} x {{ money(item.price) }}</strong>
            </div>
          </article>
          <p v-if="items.length === 0" class="checkout-empty-note">{{ t("checkout.emptyNote") }}</p>
        </section>

        <section v-if="canReviewPayment" class="checkout-payment-panel">
          <div class="checkout-section-title">
            <span>06</span>
            <div>
              <h2>{{ t("checkout.payment.title") }}</h2>
              <p>{{ t("checkout.payment.intro") }}</p>
            </div>
          </div>
          <div class="checkout-payment-options" :aria-label="t('checkout.payment.method')">
            <label
              v-for="option in paymentMethodOptions"
              :key="option.value"
              class="checkout-payment-option"
              :class="{ 'is-selected': paymentMethod === option.value }"
            >
              <input v-model="paymentMethod" name="checkout-payment-method" :value="option.value" type="radio" />
              <span>
                <strong>{{ t(option.labelKey) }}</strong>
                <small>{{ t(option.descriptionKey) }}</small>
              </span>
            </label>
          </div>
          <dl>
            <div>
              <dt>{{ t("checkout.payment.method") }}</dt>
              <dd>{{ t(selectedPaymentOption.labelKey) }}</dd>
            </div>
            <div>
              <dt>{{ t("checkout.payment.cardDetails") }}</dt>
              <dd>{{ checkoutFlow.payment.cardComplete ? t("checkout.payment.ready") : t("checkout.payment.required") }}</dd>
            </div>
          </dl>
          <label class="checkout-confirm-row">
            <input v-model="cardComplete" :disabled="mode !== 'yudao' || !checkoutFlow.readyForPayment" type="checkbox" />
            <span>{{ t("checkout.confirm.paymentReady") }}</span>
          </label>
        </section>

        <section v-if="canReviewPayment" class="checkout-terms-panel">
          <div class="checkout-section-title">
            <span>07</span>
            <div>
              <h2>{{ t("checkout.terms.title") }}</h2>
              <p>{{ t("checkout.terms.intro") }}</p>
            </div>
          </div>
          <label class="checkout-confirm-row">
            <input v-model="termsAccepted" :disabled="mode !== 'yudao' || !checkoutFlow.readyForPayment" type="checkbox" />
            <span>{{ t("checkout.confirm.termsAccepted") }}</span>
          </label>
        </section>

        <section v-if="canReviewPayment" class="checkout-delivery-notes">
          <div class="checkout-section-title">
            <span>08</span>
            <div>
              <h2>{{ t("checkout.deliveryNotes.title") }}</h2>
              <p>{{ t("checkout.deliveryNotes.intro") }}</p>
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
          <span>{{ t("checkout.membership") }}</span>
          <strong>{{ money(membershipPricing.membershipSubtotal) }}</strong>
        </div>
        <div v-if="membershipPricing.memberDiscount" class="summary-row">
          <span>{{ t("checkout.memberSavings") }}</span>
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
