<script setup>
import { computed, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { getCheckoutErrorKey } from "../services/checkoutErrors.js";
import {
  buildPaymentReturnUrl,
  buildYudaoPaymentPayload,
  getCreatedOrderId,
  getPayOrderId,
  getPaymentRedirectTarget,
  normalizeYudaoPayChannelCode,
  submitPaymentFormDisplay,
} from "../services/checkoutPayment.js";
import { createRemoteAddressVerificationProvider } from "../services/addressVerificationProvider.js";
import { buildAddressBookVerificationSummary } from "../services/addressBookVerification.js";
import { buildCheckoutAddressConfirmationSummary } from "../services/checkoutAddressConfirmation.js";
import { getCheckoutRecoveryAction } from "../services/checkoutRecovery.js";
import { buildCheckoutFlow } from "../services/checkoutFlow.js";
import {
  buildAddressVerificationAudit,
  buildConfirmedShippingAddressInput,
  buildLocalCheckoutSummary,
  buildYudaoAddressPayload,
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getCheckoutMode,
  getCheckoutPresentation,
  getOrderDetailPath,
  getSelectedAddressId,
  savedAddressToShippingForm,
} from "../services/checkoutSession.js";
import { getMembershipPricing, isMembershipItem } from "../services/membershipCart.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import {
  buildAddressConfirmationRecord,
  getUsStateOptions,
  verifyUsCheckoutAddressWithProvider,
} from "../services/usAddress.js";
import { createMemberAddress, getAddressList, getDefaultAddress, updateMemberAddress } from "../services/yudaoMemberApi.js";
import { createOrder, settleOrder } from "../services/yudaoOrderApi.js";
import { submitPayOrder } from "../services/yudaoPaymentApi.js";
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
const addressVerificationResult = ref(null);
const addressVerificationProviderStatus = ref(null);
const addressConfirmationRecord = ref(null);
const createdOrderPath = ref("");
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
const renewalAccepted = ref(false);
const saveCard = ref(true);
const billingSameAsShipping = ref(true);
const checkoutStage = ref("shipping");
const addressReviewOpen = ref(false);
const addressReviewBusy = ref(false);
const defaultShippingForm = () => ({
  firstName: "",
  lastName: "",
  country: "United States",
  street: "",
  apartment: "",
  city: "",
  state: "",
  postalCode: "",
  phone: "",
  areaId: "",
});
const shippingForm = ref(defaultShippingForm());
const paymentForm = ref({
  cardNumber: "",
  expiry: "",
  cvv: "",
});
const paymentMethodOptions = [
  {
    value: "card",
    labelKey: "checkout.payment.methods.card.label",
    descriptionKey: "checkout.payment.methods.card.description",
    enabled: true,
  },
  {
    value: "gift-card",
    labelKey: "checkout.payment.methods.giftCard.label",
    descriptionKey: "checkout.payment.methods.giftCard.description",
    enabled: false,
  },
  {
    value: "member-credit",
    labelKey: "checkout.payment.methods.memberCredit.label",
    descriptionKey: "checkout.payment.methods.memberCredit.description",
    enabled: false,
  },
];
const summary = computed(() => buildLocalCheckoutSummary(props.items));
const stateOptions = getUsStateOptions();
const paymentChannelCode = normalizeYudaoPayChannelCode(import.meta.env.VITE_YUDAO_PAY_CHANNEL_CODE);
const paymentChannelConfigured = computed(() => Boolean(paymentChannelCode));
const addressVerificationProvider = createRemoteAddressVerificationProvider();
const membershipPricing = computed(() => getMembershipPricing(props.items));
const mode = computed(() => getCheckoutMode(props.items, readYudaoToken()));
const checkoutModeKey = computed(() => `checkout.mode.${mode.value}`);
const checkoutPresentation = computed(() => getCheckoutPresentation(mode.value));
const displaySubtotal = computed(() => settlement.value?.payPrice ?? membershipPricing.value.estimatedTotal);
const displayDelivery = computed(() => settlement.value?.deliveryPrice ?? 0);
const displayItemTotal = computed(() => settlement.value?.totalPrice ?? membershipPricing.value.merchandiseSubtotal);
const displayEstimatedTotal = computed(() => displaySubtotal.value);
const paymentRequired = computed(() => Number(settlement.value?.payPrice ?? displayEstimatedTotal.value) > 0);
const displayZip = computed(() => shippingForm.value.postalCode || checkoutAddress.value?.postalCode || "94925");
const displaySalesTax = computed(() => (checkoutStage.value === "payment" ? Math.round(displayEstimatedTotal.value * 0.07336 * 100) / 100 : 0));
const displayOrderTotal = computed(() =>
  displayEstimatedTotal.value + (displayDelivery.value || 299) + displaySalesTax.value,
);
const formattedAddressLines = computed(() => {
  const form = shippingForm.value;
  const name = [form.firstName, form.lastName].filter(Boolean).join(" ");
  const locality = [form.city, form.state, form.postalCode].filter(Boolean).join(", ");
  return {
    name: name || "Shipping Guest",
    street: form.street || "223 Winter Street",
    locality: locality || "Lucedale, MS, 39452",
    country: form.country || "United States",
    phone: form.phone || "",
  };
});
const paymentStepReady = computed(() => checkoutStage.value === "payment");
const cardLooksComplete = computed(() => paymentForm.value.cardNumber.trim() && paymentForm.value.expiry.trim() && paymentForm.value.cvv.trim());
const selectedPaymentMethodEnabled = computed(() =>
  paymentMethodOptions.some((option) => option.value === paymentMethod.value && option.enabled),
);
const canSubmitPayment = computed(
  () =>
    paymentStepReady.value &&
    (!paymentRequired.value ||
      (paymentChannelConfigured.value &&
        selectedPaymentMethodEnabled.value &&
        cardLooksComplete.value &&
        termsAccepted.value &&
        renewalAccepted.value)) &&
    !busy.value,
);
const error = computed(() => (errorKey.value ? t(errorKey.value) : ""));
const checkoutRecoveryAction = computed(() =>
  getCheckoutRecoveryAction(errorKey.value, {
    addressBook: membershipRoutes.accountAddressBook,
    checkoutAuth: membershipRoutes.checkoutAuth,
    orderDetail: createdOrderPath.value,
  }),
);
const selectedAddress = computed(() => addresses.value.find((address) => String(address.id) === String(selectedAddressId.value)));
const selectedAddressSource = computed(() => (selectedAddress.value ? "saved" : "new"));
const selectedSavedAddressVerificationSummary = computed(() =>
  buildAddressBookVerificationSummary(selectedAddress.value?.addressVerification),
);
const addressVerificationProviderFallbackWarning = computed(() =>
  addressVerificationProviderStatus.value?.fallbackActive ? "checkout.shipping.addressVerificationFallbackWarning" : "",
);
const hasCheckoutAddress = computed(() => Boolean(selectedAddress.value || defaultAddress.value));
const hasShippingFormAddress = computed(() => {
  const form = shippingForm.value;
  return Boolean(
    String(form.firstName || "").trim() &&
      String(form.lastName || "").trim() &&
      String(form.street || "").trim() &&
      String(form.city || "").trim() &&
      String(form.state || "").trim() &&
      String(form.postalCode || "").trim() &&
      String(form.phone || "").trim(),
  );
});
const shippingAddressPayload = computed(() =>
  buildYudaoAddressPayload(buildConfirmedShippingAddressInput(shippingForm.value, addressConfirmationRecord.value?.selectedAddress), {
    addressConfirmation: addressConfirmationRecord.value,
  }),
);
const addressConfirmationSummary = computed(() => buildCheckoutAddressConfirmationSummary(addressConfirmationRecord.value));
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
    addressConfirmed: Boolean(addressConfirmationRecord.value),
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
const knownAddressReviewReasons = [
  "postal-region-mismatch",
  "missing-required-fields",
  "unknown-postal-code",
  "google-address-complete",
  "google-review-required",
  "google-unverified",
  "backend-standardized",
  "remote-standardized",
  "cass-standardized",
];
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
const addressReviewReasonLabelKey = computed(() => {
  const reason = addressVerificationResult.value?.reason;
  if (!reason) return "";
  return `checkout.addressConfirmation.reasons.${knownAddressReviewReasons.includes(reason) ? reason : "unknown"}`;
});
const canReviewPayment = computed(() => mode.value === "yudao" && hasCheckoutAddress.value && checkoutFlow.value.readyForPayment);
const canStartAddressReview = computed(() => mode.value === "yudao" && hasShippingFormAddress.value && !busy.value);
const primaryActionDisabled = computed(
  () =>
    busy.value ||
    mode.value === "empty" ||
    mode.value !== "yudao" ||
    (checkoutStage.value === "shipping" && !canStartAddressReview.value) ||
    (checkoutStage.value === "payment" && !canSubmitPayment.value),
);
const { t } = useI18n();
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
let checkoutRequestId = 0;

const resetShippingForm = () => {
  shippingForm.value = defaultShippingForm();
};

const applySelectedSavedAddress = () => {
  if (selectedAddressSource.value === "new") {
    resetShippingForm();
    addressVerificationResult.value = null;
    addressConfirmationRecord.value = null;
    return;
  }

  shippingForm.value = {
    ...shippingForm.value,
    ...savedAddressToShippingForm(selectedAddress.value),
  };
  addressVerificationResult.value = null;
  addressConfirmationRecord.value = null;
};

const handleSavedAddressSelectionChange = async () => {
  applySelectedSavedAddress();
  if (!selectedAddressId.value) {
    settlement.value = null;
    return;
  }

  busy.value = true;
  errorKey.value = "";
  try {
    await refreshSettlement(selectedAddressId.value);
  } catch (caught) {
    errorKey.value = getCheckoutErrorKey(caught, "checkout.errors.loadUnavailable");
  } finally {
    busy.value = false;
  }
};

const buildSavedAddressSnapshot = (savedAddressId, payload, selectedAddress) => ({
  id: savedAddressId,
  name: payload.name,
  mobile: payload.mobile,
  areaName: [selectedAddress.city, selectedAddress.state].filter(Boolean).join(", "),
  detailAddress: payload.detailAddress,
  label: [payload.name, payload.mobile, payload.detailAddress].filter(Boolean).join(" - "),
  addressVerification: buildAddressVerificationAudit(addressConfirmationRecord.value),
  raw: {
    ...payload,
    addressConfirmation: addressConfirmationRecord.value,
    addressVerification: buildAddressVerificationAudit(addressConfirmationRecord.value),
    areaId: payload.areaId,
    postalCode: selectedAddress.postalCode,
    region: selectedAddress.state,
  },
});

const syncSavedAddressAfterSave = (savedAddress) => {
  const exists = addresses.value.some((address) => String(address.id) === String(savedAddress.id));
  if (!exists) {
    addresses.value = [savedAddress, ...addresses.value];
    return;
  }

  addresses.value = addresses.value.map((address) =>
    String(address.id) === String(savedAddress.id) ? savedAddress : address,
  );
};

const resetCheckoutConfirmations = () => {
  customNoticeAccepted.value = false;
  useSuggestedAddress.value = false;
  addressVerificationResult.value = null;
  addressConfirmationRecord.value = null;
  createdOrderPath.value = "";
  paymentMethod.value = "card";
  cardComplete.value = false;
  termsAccepted.value = false;
  renewalAccepted.value = false;
};

const resetAddressConfirmationAfterShippingEdit = () => {
  if (!addressConfirmationRecord.value && !addressVerificationResult.value) return;
  useSuggestedAddress.value = false;
  addressVerificationResult.value = null;
  addressConfirmationRecord.value = null;
};

const editConfirmedAddress = () => {
  resetAddressConfirmationAfterShippingEdit();
  checkoutStage.value = "shipping";
};

const clearRemoteCheckoutData = ({ preserveAddressSelection = false } = {}) => {
  const previousAddressId = selectedAddressId.value;
  addresses.value = [];
  defaultAddress.value = null;
  selectedAddressId.value = preserveAddressSelection ? previousAddressId : undefined;
  settlement.value = null;
};

const refreshSettlement = async (addressId) => {
  const payload = buildYudaoOrderPayload(props.items, { addressId });
  settlement.value = await settleOrder(payload);
};

const loadAddressVerificationProviderStatus = async () => {
  if (!addressVerificationProvider?.getStatus) return;
  try {
    addressVerificationProviderStatus.value = await addressVerificationProvider.getStatus();
  } catch (caught) {
    addressVerificationProviderStatus.value = null;
  }
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
    applySelectedSavedAddress();
    if (selectedAddressId.value) {
      const nextSettlement = await settleOrder(buildYudaoOrderPayload(props.items, { addressId: selectedAddressId.value }));
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
  if (!canUseYudaoCheckout(props.items)) {
    errorKey.value = "checkout.errors.orderUnavailable";
    checkoutStage.value = "shipping";
    return;
  }
  if (!readYudaoToken()) {
    errorKey.value = "checkout.errors.sessionExpired";
    checkoutStage.value = "shipping";
    return;
  }
  if (!addressConfirmationRecord.value) {
    errorKey.value = "checkout.errors.addressConfirmationRequired";
    checkoutStage.value = "shipping";
    return;
  }
  const addressVerificationAudit = buildAddressVerificationAudit(addressConfirmationRecord.value);
  if (!addressVerificationAudit) {
    errorKey.value = "checkout.errors.addressConfirmationRequired";
    addressConfirmationRecord.value = null;
    checkoutStage.value = "shipping";
    return;
  }
  if (checkoutStage.value === "payment" && paymentRequired.value && !paymentChannelConfigured.value) {
    errorKey.value = "checkout.errors.paymentChannelUnavailable";
    return;
  }
  if (checkoutStage.value === "payment" && paymentRequired.value && !canSubmitPayment.value) {
    errorKey.value = "checkout.errors.paymentRequired";
    return;
  }
  const addressId = getSelectedAddressId(selectedAddressId.value, defaultAddress.value);
  if (!addressId) {
    errorKey.value = "checkout.errors.noAddress";
    return;
  }
  busy.value = true;
  errorKey.value = "";
  try {
    const payload = buildYudaoOrderPayload(props.items, {
      addressId,
      addressConfirmation: addressConfirmationRecord.value,
    });
    const result = await createOrder(payload);
    const createdOrderId = getCreatedOrderId(result);
    if (!createdOrderId) {
      errorKey.value = "checkout.errors.orderUnavailable";
      return;
    }
    createdOrderPath.value = getOrderDetailPath(createdOrderId);
    const payOrderId = getPayOrderId(result);
    if (!payOrderId) {
      emit("order-created", createdOrderId);
      return;
    }
    createdOrderPath.value = getOrderDetailPath(createdOrderId, payOrderId);
    const paymentPayload = buildYudaoPaymentPayload(result, {
      channelCode: paymentChannelCode,
      returnUrl: buildPaymentReturnUrl(window.location.origin, createdOrderId, payOrderId),
    });
    if (payOrderId && !paymentPayload) {
      errorKey.value = "checkout.errors.paymentUnavailable";
      return;
    }
    if (paymentPayload) {
      let paymentResult;
      try {
        paymentResult = await submitPayOrder(paymentPayload);
      } catch (caught) {
        errorKey.value = getCheckoutErrorKey(caught, "checkout.errors.paymentUnavailable");
        return;
      }
      if (submitPaymentFormDisplay(paymentResult, window.document)) return;
      const paymentRedirectTarget = getPaymentRedirectTarget(paymentResult);
      if (!paymentRedirectTarget) {
        errorKey.value = "checkout.errors.paymentUnavailable";
        return;
      }
      window.location.assign(paymentRedirectTarget);
      return;
    }
    emit("order-created", createdOrderId);
  } catch (caught) {
    errorKey.value = getCheckoutErrorKey(caught, "checkout.errors.orderUnavailable");
    if (errorKey.value === "checkout.errors.addressConfirmationRequired") {
      addressConfirmationRecord.value = null;
      checkoutStage.value = "shipping";
    }
  } finally {
    busy.value = false;
  }
};

const handlePrimaryAction = async () => {
  if (mode.value === "empty") {
    window.location.href = "/";
    return;
  }
  if (mode.value === "token-required") {
    errorKey.value = "checkout.errors.sessionExpired";
    return;
  }
  if (mode.value === "local-preview") {
    errorKey.value = "checkout.errors.previewOnlyOrderUnavailable";
    return;
  }
  if (primaryActionDisabled.value && checkoutStage.value !== "payment") {
    errorKey.value = "checkout.errors.addressConfirmationRequired";
    return;
  }
  if (checkoutStage.value === "shipping") {
    addressVerificationResult.value = await verifyUsCheckoutAddressWithProvider(shippingForm.value, addressVerificationProvider);
    addressReviewOpen.value = true;
    return;
  }
  if (mode.value === "yudao") {
    submitOrder();
    return;
  }
  errorKey.value = "";
};

const selectPaymentMethod = (option) => {
  if (!option.enabled) return;
  paymentMethod.value = option.value;
};

const continueWithAddressChoice = async (choice) => {
  addressConfirmationRecord.value = buildAddressConfirmationRecord(addressVerificationResult.value, choice, {
    addressSource: selectedAddressSource.value,
  });
  const chosenAddressAudit = buildAddressVerificationAudit(addressConfirmationRecord.value);
  if (!chosenAddressAudit) {
    errorKey.value = "checkout.errors.addressConfirmationRequired";
    addressReviewBusy.value = false;
    addressConfirmationRecord.value = null;
    checkoutStage.value = "shipping";
    return;
  }
  addressReviewBusy.value = true;
  errorKey.value = "";
  let savedAddressId;
  try {
    savedAddressId = await saveShippingAddress();
  } catch (caught) {
    errorKey.value = getCheckoutErrorKey(caught, "checkout.errors.addressUnavailable");
    addressReviewBusy.value = false;
    return;
  }
  if (!savedAddressId) {
    addressReviewBusy.value = false;
    return;
  }

  useSuggestedAddress.value = choice === "suggested";
  customNoticeAccepted.value = true;
  addressReviewOpen.value = false;
  addressReviewBusy.value = false;
  checkoutStage.value = "payment";
};

const continueWithOriginalAddress = () => continueWithAddressChoice("original");
const continueWithSuggestedAddress = () => continueWithAddressChoice("suggested");

const editOriginalAddress = () => {
  addressReviewOpen.value = false;
  addressReviewBusy.value = false;
  checkoutStage.value = "shipping";
};

const closeAddressReview = () => {
  addressReviewOpen.value = false;
  addressReviewBusy.value = false;
};

const handleCheckoutRecoveryAction = () => {
  if (!checkoutRecoveryAction.value) return;
  if (checkoutRecoveryAction.value.type === "emit") {
    emit(checkoutRecoveryAction.value.event);
    return;
  }
  if (checkoutRecoveryAction.value.type === "retry") {
    loadCheckoutData({ preserveAddressSelection: true });
    return;
  }
  if (checkoutRecoveryAction.value.type === "address-review") {
    errorKey.value = "";
    checkoutStage.value = "shipping";
    addressReviewOpen.value = false;
  }
};

const saveShippingAddress = async () => {
  const payload = shippingAddressPayload.value;
  if (!payload.name || !payload.mobile || !payload.areaId || !payload.detailAddress) {
    errorKey.value = "checkout.errors.noAddress";
    return null;
  }

  let savedAddressId;
  if (selectedAddress.value) {
    await updateMemberAddress({ id: selectedAddressId.value, ...payload });
    savedAddressId = selectedAddressId.value;
  } else {
    const savedAddress = await createMemberAddress(payload);
    savedAddressId = savedAddress?.id ?? savedAddress;
  }
  selectedAddressId.value = savedAddressId;
  const confirmedAddress = addressConfirmationRecord.value?.selectedAddress || shippingForm.value;
  const savedAddress = buildSavedAddressSnapshot(savedAddressId, payload, confirmedAddress);
  defaultAddress.value = savedAddress;
  syncSavedAddressAfterSave(savedAddress);
  await refreshSettlement(savedAddressId);
  return savedAddressId;
};

onMounted(() => {
  loadAddressVerificationProviderStatus();
  loadCheckoutData();
});
watch(() => props.authVersion, loadCheckoutData);
watch(() => props.items, () => loadCheckoutData({ preserveAddressSelection: true }), { deep: true });
watch(shippingForm, () => {
  if (checkoutStage.value === "shipping") {
    resetAddressConfirmationAfterShippingEdit();
  }
}, { deep: true });
watch(paymentMethod, () => {
  cardComplete.value = false;
});
watch(paymentForm, () => {
  cardComplete.value = Boolean(cardLooksComplete.value);
}, { deep: true });
</script>

<template>
  <section class="checkout-page rh-checkout-page">
    <header class="rh-checkout-top">
      <img src="/assets/brand/oakved-logo-black.png" alt="Oakved" />
      <button type="button">Ship to United States <span aria-hidden="true">v</span></button>
    </header>

    <div v-if="error" class="checkout-error rh-checkout-error">
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

    <section class="rh-checkout-layout">
      <main class="rh-checkout-form">
        <header class="rh-checkout-heading">
          <h1>Checkout</h1>
          <nav aria-label="Checkout steps" :class="{ 'is-payment': checkoutStage === 'payment' }">
            <span :class="{ muted: checkoutStage === 'payment' }">Shipping</span>
            <b aria-hidden="true">&gt;</b>
            <span :class="{ muted: checkoutStage !== 'payment' }">Payment</span>
            <b aria-hidden="true">&gt;</b>
            <span class="muted">Confirmation</span>
          </nav>
        </header>

        <section class="checkout-status-card">
          <span>{{ t(`${checkoutModeKey}.status`) }}</span>
          <h2>{{ checkoutPresentation.title }}</h2>
          <p>{{ checkoutPresentation.message }}</p>
        </section>

        <section v-if="checkoutStage === 'shipping'" class="rh-shipping-card">
          <h2>{{ t("checkout.shipping.title") }}</h2>
          <form class="rh-shipping-form" @submit.prevent="handlePrimaryAction">
            <section
              v-if="addressVerificationProviderFallbackWarning"
              class="wide rh-address-provider-status"
            >
              {{ t(addressVerificationProviderFallbackWarning) }}
            </section>
            <label v-if="addresses.length" class="wide saved-address-selector">
              <span>{{ t("checkout.shipping.savedAddresses") }}</span>
              <select v-model="selectedAddressId" @change="handleSavedAddressSelectionChange">
                <option value="">{{ t("checkout.shipping.enterNewAddress") }}</option>
                <option v-for="address in addresses" :key="address.id" :value="address.id">{{ address.label }}</option>
              </select>
            </label>
            <section
              v-if="selectedSavedAddressVerificationSummary"
              class="wide rh-saved-address-verification"
            >
              <strong>{{ t("checkout.shipping.savedAddressVerification") }}</strong>
              <span>{{ t(selectedSavedAddressVerificationSummary.statusLabelKey) }}</span>
              <small v-if="selectedSavedAddressVerificationSummary.confirmedAt">
                {{ selectedSavedAddressVerificationSummary.confirmedAt }}
              </small>
              <p>{{ t("checkout.shipping.savedAddressVerificationRecheck") }}</p>
              <em v-if="selectedSavedAddressVerificationSummary.warningKey">
                {{ t(selectedSavedAddressVerificationSummary.warningKey) }}
              </em>
              <em v-if="selectedSavedAddressVerificationSummary.sourceWarningKey">
                {{ t(selectedSavedAddressVerificationSummary.sourceWarningKey) }}
              </em>
              <em v-if="selectedSavedAddressVerificationSummary.providerWarningKey">
                {{ t(selectedSavedAddressVerificationSummary.providerWarningKey) }}
              </em>
            </section>
            <input v-model="shippingForm.firstName" :aria-label='t("checkout.shipping.firstName")' :placeholder='t("checkout.shipping.firstName")' type="text" />
            <input v-model="shippingForm.lastName" :aria-label='t("checkout.shipping.lastName")' :placeholder='t("checkout.shipping.lastName")' type="text" />
            <label class="wide">
              <span>{{ t("checkout.shipping.country") }}</span>
              <input v-model="shippingForm.country" :aria-label='t("checkout.shipping.country")' type="text" />
            </label>
            <label class="wide address-field">
              <input v-model="shippingForm.street" :aria-label='t("checkout.shipping.street")' :placeholder='t("checkout.shipping.street")' type="text" />
              <span aria-hidden="true">+</span>
            </label>
            <input v-model="shippingForm.apartment" class="wide" :aria-label='t("checkout.shipping.apartment")' :placeholder='t("checkout.shipping.apartment")' type="text" />
            <input v-model="shippingForm.city" class="wide" :aria-label='t("checkout.shipping.city")' :placeholder='t("checkout.shipping.city")' type="text" />
            <label>
              <select v-model="shippingForm.state" :aria-label='t("checkout.shipping.state")'>
                <option value="">{{ t("checkout.shipping.state") }}</option>
                <option v-for="state in stateOptions" :key="state.code" :value="state.code">{{ state.name }}</option>
              </select>
            </label>
            <input v-model="shippingForm.postalCode" :aria-label='t("checkout.shipping.postalCode")' :placeholder='t("checkout.shipping.postalCode")' type="text" />
            <input v-model="shippingForm.phone" class="wide" :aria-label='t("checkout.shipping.phone")' :placeholder='t("checkout.shipping.phone")' type="tel" />
          </form>
        </section>

        <section v-if="checkoutStage === 'payment'" class="rh-payment-panel">
          <section v-if="addressConfirmationSummary" class="rh-address-confirmation-summary">
            <header>
              <div>
                <span>{{ t("checkout.addressConfirmation.title") }}</span>
                <h3>{{ t(addressConfirmationSummary.statusLabelKey) }}</h3>
              </div>
              <button type="button" @click="editConfirmedAddress">{{ t("checkout.addressConfirmation.edit") }}</button>
            </header>
            <dl>
              <div>
                <dt>{{ t("checkout.addressConfirmation.choice") }}</dt>
                <dd>{{ t(addressConfirmationSummary.choiceLabelKey) }}</dd>
              </div>
              <div>
                <dt>{{ t("checkout.addressConfirmation.addressSource") }}</dt>
                <dd>{{ t(addressConfirmationSummary.addressSourceLabelKey) }}</dd>
              </div>
              <div v-if="addressConfirmationSummary.reason">
                <dt>{{ t("checkout.addressConfirmation.reason") }}</dt>
                <dd>{{ t(addressConfirmationSummary.reasonLabelKey) }}</dd>
              </div>
              <div v-if="addressConfirmationSummary.providerStatus">
                <dt>{{ t("checkout.addressConfirmation.providerStatus") }}</dt>
                <dd>{{ t(addressConfirmationSummary.providerStatusLabelKey) }}</dd>
              </div>
            </dl>
            <p>{{ addressConfirmationSummary.selected }}</p>
            <strong v-if="addressConfirmationSummary.warningKey">{{ t(addressConfirmationSummary.warningKey) }}</strong>
            <strong v-if="addressConfirmationSummary.sourceWarningKey">{{ t(addressConfirmationSummary.sourceWarningKey) }}</strong>
            <strong v-if="addressConfirmationSummary.providerWarningKey">{{ t(addressConfirmationSummary.providerWarningKey) }}</strong>
          </section>
          <h2>Payment</h2>
          <p>Select a payment method to use.</p>
          <div class="rh-payment-methods" role="radiogroup" aria-label="Payment method">
            <button
              v-for="option in paymentMethodOptions"
              :key="option.value"
              type="button"
              :class="{ selected: paymentMethod === option.value }"
              :disabled="!option.enabled"
              @click="selectPaymentMethod(option)"
            >
              {{ t(option.labelKey) }}
            </button>
          </div>
          <p v-if="paymentRequired && !paymentChannelConfigured" class="rh-payment-warning">{{ t("checkout.payment.channelUnavailable") }}</p>
          <form class="rh-card-form">
            <label class="wide">
              Card number
              <input v-model="paymentForm.cardNumber" inputmode="numeric" placeholder="1234 5678 9012 3456" type="text" />
            </label>
            <label>
              Expiry date
              <input v-model="paymentForm.expiry" placeholder="MM/YY" type="text" />
            </label>
            <label>
              CVC / CVV
              <input v-model="paymentForm.cvv" inputmode="numeric" placeholder="3 digits" type="text" />
            </label>
          </form>
          <button class="rh-split-payment" type="button">
            <span aria-hidden="true">+</span>
            Split Payment with Additional Method
          </button>
          <label class="rh-checkbox-row">
            <input v-model="saveCard" type="checkbox" />
            <span>Save this credit card to my account</span>
          </label>
          <label class="rh-checkbox-row">
            <input v-model="billingSameAsShipping" type="checkbox" />
            <span>Billing address same as shipping</span>
          </label>
          <section class="rh-billing-address">
            <h2>Billing Address</h2>
            <address>
              {{ formattedAddressLines.name }}<br />
              {{ formattedAddressLines.street }}<br />
              {{ formattedAddressLines.locality }}, US<br />
              <span v-if="formattedAddressLines.phone">{{ formattedAddressLines.phone }}</span>
            </address>
            <button type="button" @click="editConfirmedAddress">Edit</button>
          </section>
        </section>

        <section v-if="checkoutStage === 'shipping'" class="rh-checkout-accordion">
          <button type="button">Gift Message <span aria-hidden="true">+</span></button>
          <button type="button">Order Description <span aria-hidden="true">+</span></button>
        </section>
      </main>

      <aside class="rh-order-card">
        <header class="rh-order-card-head">
          <h2>My Order ({{ summary.quantity }})</h2>
          <button type="button" @click="emit('open-cart')">View Cart <span aria-hidden="true">&gt;</span></button>
        </header>

        <div class="rh-order-items">
          <ProductImage v-if="items[0]" :src="items[0].cover" :label="items[0].name" />
        </div>

        <section class="rh-order-summary">
          <header>
            <h3>Order Summary</h3>
            <span>Shipping to {{ displayZip }}</span>
          </header>
          <div class="summary-row muted">
            <span>Member Savings</span>
            <strong>{{ money(membershipPricing.memberDiscount) }}</strong>
          </div>
          <div class="summary-row">
            <span>Subtotal with member savings</span>
            <strong>{{ money(displayItemTotal || membershipPricing.estimatedTotal) }}</strong>
          </div>
          <div class="summary-row">
            <span>RH Members Program</span>
            <strong>{{ money(membershipPricing.membershipSubtotal) }}</strong>
          </div>
          <div class="summary-row">
            <span><u>Unlimited Furniture Delivery</u></span>
            <strong>{{ money(displayDelivery || 299) }}</strong>
          </div>
          <div v-if="checkoutStage === 'payment'" class="summary-row">
            <span>Estimated Sales Tax for {{ displayZip }}</span>
            <strong>{{ money(displaySalesTax) }}</strong>
          </div>
          <div class="summary-total">
            <span>{{ checkoutStage === "payment" ? "Total" : "Total (excluding sales tax)" }}</span>
            <strong>{{ money(displayOrderTotal) }}</strong>
          </div>
          <p>Custom Item Non-Refundable Amount <strong>{{ money((displayItemTotal || 0) / 2) }}</strong></p>
        </section>

        <button
          class="rh-payment-button"
          type="button"
          :disabled="primaryActionDisabled"
          @click="handlePrimaryAction"
        >
          {{ busy ? t("common.working") : checkoutStage === "payment" ? "Submit Order" : "Continue To Payment" }}
        </button>

        <section v-if="checkoutStage === 'payment'" class="rh-payment-agreements">
          <label>
            <input v-model="termsAccepted" type="checkbox" />
            <span>
              I agree to the <u>RH Members Program Terms and Conditions</u>, which includes the <u>RH Privacy Notice</u>.
            </span>
          </label>
          <label>
            <input v-model="renewalAccepted" type="checkbox" />
            <span>
              I agree to enroll in the RH Membership Program. I authorize RH to automatically renew my membership on an
              annual basis and charge my credit card for my membership fee of $200 plus applicable taxes.
            </span>
          </label>
        </section>
      </aside>
    </section>

    <Transition name="rh-address-review">
      <div v-if="addressReviewOpen" class="rh-address-review-layer" role="presentation">
        <button class="rh-address-review-scrim" type="button" aria-label="Close address review" @click="closeAddressReview"></button>
        <aside class="rh-address-review-panel" role="dialog" aria-modal="true" aria-label="Address verification">
          <button class="rh-address-review-close" type="button" aria-label="Close address review" @click="closeAddressReview">
            <span></span>
            <span></span>
          </button>
          <h2>{{ addressVerificationResult?.status === "verified" ? t("checkout.addressReview.titleVerified") : t("checkout.addressReview.titleReview") }}</h2>
          <p v-if="addressVerificationResult?.status === 'suggested'">{{ t("checkout.addressReview.suggestedMessage") }}</p>
          <p v-else-if="addressVerificationResult?.status === 'unverified'">
            {{ t("checkout.addressReview.unverifiedMessage") }}
          </p>
          <p v-else>{{ t("checkout.addressReview.confirmMessage") }}</p>
          <p v-if='addressVerificationResult?.providerStatus === "fallback"' class="rh-address-review-warning">
            {{ t("checkout.addressReview.providerFallbackWarning") }}
          </p>
          <p v-if='addressVerificationResult?.source === "local-postal-region"' class="rh-address-review-warning">
            {{ t("checkout.addressReview.localPostalRegionWarning") }}
          </p>
          <p v-if='addressVerificationResult?.source === "backend-address-verification"' class="rh-address-review-warning">
            {{ t("checkout.addressReview.localOnlyVerificationWarning") }}
          </p>
          <dl v-if="addressReviewReasonLabelKey" class="rh-address-review-meta">
            <div>
              <dt>{{ t("checkout.addressConfirmation.reason") }}</dt>
              <dd>{{ t(addressReviewReasonLabelKey) }}</dd>
            </div>
          </dl>
          <section class="rh-original-address">
            <header>
              <span>{{ addressVerificationResult?.status === "verified" ? t("checkout.addressReview.verifiedLabel") : t("checkout.addressReview.enteredLabel") }}</span>
              <b v-if="addressVerificationResult?.status === 'verified'" aria-hidden="true">&check;</b>
            </header>
            <p>
              {{ addressVerificationResult?.originalAddress?.street || formattedAddressLines.street }}<br />
              {{ addressVerificationResult?.originalAddress?.city || shippingForm.city || "Lucedale" }},
              {{ addressVerificationResult?.originalAddress?.state || shippingForm.state || "MS" }}
              {{ addressVerificationResult?.originalAddress?.postalCode || displayZip }}<br />
              United States
            </p>
          </section>
          <section
            v-if="addressVerificationResult?.status === 'suggested' && addressVerificationResult?.suggestedAddress"
            class="rh-original-address rh-suggested-address"
          >
            <header>
              <span>{{ t("checkout.addressReview.suggestedLabel") }}</span>
              <b aria-hidden="true">&check;</b>
            </header>
            <p>
              {{ addressVerificationResult.suggestedAddress.street }}<br />
              {{ addressVerificationResult.suggestedAddress.city }},
              {{ addressVerificationResult.suggestedAddress.state }}
              {{ addressVerificationResult.suggestedAddress.postalCode }}<br />
              United States
            </p>
          </section>
          <p class="rh-address-review-notice">{{ t("checkout.addressReview.confirmationNotice") }}</p>
          <button
            v-if="addressVerificationResult?.status === 'suggested' && addressVerificationResult?.suggestedAddress"
            class="rh-address-primary"
            type="button"
            :disabled="addressReviewBusy"
            @click="continueWithSuggestedAddress"
          >
            {{ addressReviewBusy ? t("common.working") : t("checkout.addressReview.useSuggested") }}
          </button>
          <button class="rh-address-primary" type="button" :disabled="addressReviewBusy" @click="continueWithOriginalAddress">
            {{
              addressReviewBusy
                ? t("common.working")
                : addressVerificationResult?.status === "verified"
                  ? t("checkout.addressReview.useVerified")
                  : t("checkout.addressReview.useEntered")
            }}
          </button>
          <button class="rh-address-secondary" type="button" @click="editOriginalAddress">{{ t("checkout.addressReview.editOriginal") }}</button>
        </aside>
      </div>
    </Transition>

    <footer class="rh-checkout-footer" aria-label="Checkout footer">
      <a href="/privacy">Privacy</a>
      <a href="/shipping-delivery">Shipping &amp; Delivery</a>
      <a href="/returns-exchanges">Returns &amp; Exchanges</a>
      <a href="/accessibility">Accessibility Statement</a>
      <a href="/contact">Contact Us</a>
      <span>© 2026 RH</span>
    </footer>
  </section>
</template>
