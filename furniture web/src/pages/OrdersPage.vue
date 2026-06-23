<script setup>
import { computed, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import {
  getMembershipEligibilityItemsFromOrderItems,
  getMembershipEligibilityReview,
} from "../services/membershipAccount.js";
import {
  buildPaymentReturnUrl,
  buildYudaoPaymentPayload,
  getPaymentRedirectTarget,
  getPaymentReturnParams,
  getPaymentReturnSummary,
  normalizeYudaoPayChannelCode,
  submitPaymentFormDisplay,
} from "../services/checkoutPayment.js";
import { getOrderDetailPath } from "../services/checkoutSession.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { buildOrderAddressVerificationSummary } from "../services/orderAddressVerification.js";
import { getOrderDetail, getOrderPage } from "../services/yudaoOrderApi.js";
import { getPayOrder, submitPayOrder } from "../services/yudaoPaymentApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";
import { useI18n } from "../i18n.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});

const loading = ref(true);
const error = ref("");
const tokenRequired = ref(false);
const orders = ref([]);
const total = ref(0);
const detail = ref(null);
const payOrder = ref(null);
const payOrderError = ref("");
const paymentResumeBusy = ref(false);
const paymentResumeError = ref("");
const paymentReturn = computed(() => getPaymentReturnParams(window.location.search));
const paymentReturnSummary = computed(() => getPaymentReturnSummary(paymentReturn.value));
const orderId = computed(() => paymentReturn.value.orderId || new URLSearchParams(window.location.search).get("id"));
const activePayOrderId = computed(() => paymentReturn.value.payOrderId || detail.value?.payOrderId || "");
const resolvedPayOrderId = computed(() => payOrder.value?.id || activePayOrderId.value);
const hasPaidPaymentReturn = computed(() => paymentReturnSummary.value?.status === "paid");
const paymentChannelCode = normalizeYudaoPayChannelCode(import.meta.env.VITE_YUDAO_PAY_CHANNEL_CODE);
const paymentChannelConfigured = computed(() => Boolean(paymentChannelCode));
const { t } = useI18n();
const money = (value) =>
  `$${Number(value || 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const statusLabel = (status) => t("orders.status", { status: status ?? "Pending" });
const orderMembershipEligibilityItems = computed(() =>
  getMembershipEligibilityItemsFromOrderItems(detail.value?.items || []),
);
const orderMembershipEligibilityReview = computed(() =>
  getMembershipEligibilityReview(orderMembershipEligibilityItems.value),
);
const getOrderMembershipReview = (order) =>
  getMembershipEligibilityReview(getMembershipEligibilityItemsFromOrderItems(order.items || []));
const getOrderMembershipSavingsLabel = (order) =>
  order.items?.length ? money(getOrderMembershipReview(order).savingsTotal) : t("orders.memberSavingsUnavailable");
const payOrderStatusMap = new Map([
  ["0", "waiting"],
  ["10", "paid"],
  ["20", "refunded"],
  ["30", "closed"],
  ["waiting", "waiting"],
  ["wait", "waiting"],
  ["unpaid", "waiting"],
  ["success", "paid"],
  ["paid", "paid"],
  ["closed", "closed"],
  ["close", "closed"],
  ["refunded", "refunded"],
  ["refund", "refunded"],
]);
const normalizePayOrderStatus = (status) => payOrderStatusMap.get(String(status ?? "").trim().toLowerCase()) || "unknown";
const payOrderStatus = computed(() => payOrder.value?.status ?? (hasPaidPaymentReturn.value ? "paid" : detail.value ? (detail.value.payStatus ? "paid" : "unpaid") : ""));
const payOrderStatusLabelKey = computed(() => `orders.paymentStatuses.${normalizePayOrderStatus(payOrderStatus.value)}`);
const canResumePayment = computed(
  () =>
    Boolean(activePayOrderId.value) &&
    !hasPaidPaymentReturn.value &&
    paymentChannelConfigured.value &&
    normalizePayOrderStatus(payOrderStatus.value) === "waiting" &&
    !paymentResumeBusy.value,
);
const canShowPaymentChannelNotice = computed(
  () =>
    Boolean(activePayOrderId.value) &&
    !hasPaidPaymentReturn.value &&
    !paymentChannelConfigured.value &&
    normalizePayOrderStatus(payOrderStatus.value) === "waiting",
);
const addressVerificationSummary = computed(() => buildOrderAddressVerificationSummary(detail.value?.addressVerification));
let ordersRequestId = 0;

const clearOrderData = () => {
  orders.value = [];
  total.value = 0;
  detail.value = null;
  payOrder.value = null;
  payOrderError.value = "";
  paymentResumeError.value = "";
};

const refreshPayOrderStatus = async (requestId) => {
  if (!activePayOrderId.value) return;
  try {
    const nextPayOrder = await getPayOrder(activePayOrderId.value, { sync: true });
    if (requestId !== ordersRequestId) return;
    payOrder.value = nextPayOrder;
    payOrderError.value = "";
  } catch {
    if (requestId !== ordersRequestId) return;
    payOrderError.value = t("orders.paymentStatusUnavailable");
  }
};

const resumePayment = async () => {
  if (!canResumePayment.value) {
    paymentResumeError.value = t("orders.paymentResumeUnavailable");
    return;
  }
  paymentResumeBusy.value = true;
  paymentResumeError.value = "";
  try {
    const paymentPayload = buildYudaoPaymentPayload(
      { payOrderId: resolvedPayOrderId.value },
      {
        channelCode: paymentChannelCode,
        returnUrl: buildPaymentReturnUrl(window.location.origin, orderId.value, resolvedPayOrderId.value),
      },
    );
    if (!paymentPayload) {
      paymentResumeError.value = t("orders.paymentResumeUnavailable");
      return;
    }
    const paymentResult = await submitPayOrder(paymentPayload);
    if (submitPaymentFormDisplay(paymentResult, window.document)) return;
    const paymentRedirectTarget = getPaymentRedirectTarget(paymentResult);
    if (!paymentRedirectTarget) {
      paymentResumeError.value = t("orders.paymentResumeUnavailable");
      return;
    }
    window.location.assign(paymentRedirectTarget);
  } catch {
    paymentResumeError.value = t("orders.paymentResumeUnavailable");
  } finally {
    paymentResumeBusy.value = false;
  }
};

const loadOrders = async () => {
  const requestId = ++ordersRequestId;
  loading.value = true;
  error.value = "";
  tokenRequired.value = false;
  clearOrderData();
  try {
    if (!readYudaoToken()) {
      tokenRequired.value = true;
      return;
    }
    if (orderId.value) {
      const nextDetail = await getOrderDetail(orderId.value);
      if (requestId !== ordersRequestId) return;
      detail.value = nextDetail;
    }
    await refreshPayOrderStatus(requestId);
    const page = await getOrderPage({ pageNo: 1, pageSize: 10 });
    if (requestId !== ordersRequestId) return;
    orders.value = page.list;
    total.value = page.total;
  } catch {
    if (requestId !== ordersRequestId) return;
    error.value = t("orders.error");
  } finally {
    if (requestId === ordersRequestId) loading.value = false;
  }
};

onMounted(loadOrders);
watch(() => props.authVersion, loadOrders);
</script>

<template>
  <section class="orders-page">
    <header class="orders-head">
      <p class="eyebrow">{{ t("orders.eyebrow") }}</p>
      <h1>{{ t("orders.title") }}</h1>
      <p v-if="total">{{ t("orders.connectedCount", { count: total }) }}</p>
      <p v-else>{{ t("orders.intro") }}</p>
    </header>

    <p v-if="loading" class="product-loading">{{ t("orders.loading") }}</p>
    <div v-if="tokenRequired" class="checkout-error">
      <p>{{ t("orders.tokenRequired") }}</p>
      <div class="orders-recovery-actions">
        <a class="orders-recovery-action" :href="membershipRoutes.checkoutAuth">{{ t("orders.actions.connectAccount") }}</a>
      </div>
    </div>
    <div v-else-if="error" class="checkout-error">
      <p>{{ error }}</p>
      <div class="orders-recovery-actions">
        <button class="orders-recovery-action" type="button" @click="loadOrders">{{ t("orders.actions.retry") }}</button>
      </div>
    </div>

    <article v-if="detail" class="order-detail-card">
      <div class="order-detail-head">
        <div>
          <p class="eyebrow">{{ t("orders.selectedOrder") }}</p>
          <h2>{{ detail.no }}</h2>
          <p>{{ statusLabel(detail.status) }}</p>
          <p v-if="payOrderStatus">
            {{ t("orders.paymentStatus", { status: t(payOrderStatusLabelKey) }) }}
          </p>
          <p v-if="activePayOrderId">
            {{ t("orders.payOrderLabel", { id: activePayOrderId }) }}
          </p>
          <section v-if="paymentReturnSummary" class="orders-payment-return">
            <h3>{{ t(paymentReturnSummary.titleKey) }}</h3>
            <p>{{ t(paymentReturnSummary.messageKey) }}</p>
            <p v-if="paymentReturnSummary.detail">{{ paymentReturnSummary.detail }}</p>
            <div v-if="paymentReturnSummary.canRetry" class="orders-payment-return-actions">
              <button v-if="activePayOrderId" class="orders-payment-resume" type="button" :disabled="!canResumePayment" @click="resumePayment">
                {{ paymentResumeBusy ? t("common.working") : t("orders.actions.resumePayment") }}
              </button>
              <button class="orders-payment-retry" type="button" @click="loadOrders">
                {{ t("orders.actions.refreshPaymentStatus") }}
              </button>
            </div>
          </section>
          <button v-if="canResumePayment" class="orders-payment-resume" type="button" :disabled="paymentResumeBusy" @click="resumePayment">
            {{ paymentResumeBusy ? t("common.working") : t("orders.actions.resumePayment") }}
          </button>
          <p v-if="canShowPaymentChannelNotice" class="orders-payment-warning">
            {{ t("orders.paymentChannelUnavailable") }}
          </p>
          <p v-if="paymentResumeError" class="orders-payment-warning">
            {{ paymentResumeError }}
          </p>
          <div v-if="payOrderError" class="orders-payment-warning">
            <span>{{ payOrderError }}</span>
            <button class="orders-payment-retry" type="button" @click="loadOrders">
              {{ t("orders.actions.refreshPaymentStatus") }}
            </button>
          </div>
        </div>
        <strong>{{ money(detail.payPrice) }}</strong>
      </div>
      <div v-for="item in detail.items" :key="item.id || item.skuId" class="checkout-item">
        <ProductImage :src="item.cover" :label="item.name" />
        <div>
          <h3>{{ item.name }}</h3>
          <p>{{ item.count }} x {{ money(item.price) }}</p>
        </div>
      </div>
      <section v-if="addressVerificationSummary" class="order-address-verification">
        <h3>{{ t("orders.addressVerification.title") }}</h3>
        <dl>
          <div>
            <dt>{{ t("orders.addressVerification.source") }}</dt>
            <dd>{{ t(addressVerificationSummary.sourceLabelKey) }}</dd>
          </div>
          <div>
            <dt>{{ t("orders.addressVerification.addressSource") }}</dt>
            <dd>{{ t(addressVerificationSummary.addressSourceLabelKey) }}</dd>
          </div>
          <div>
            <dt>{{ t("orders.addressVerification.status") }}</dt>
            <dd>{{ t(addressVerificationSummary.statusLabelKey) }}</dd>
          </div>
          <div>
            <dt>{{ t("orders.addressVerification.choice") }}</dt>
            <dd>{{ t(addressVerificationSummary.choiceLabelKey) }}</dd>
          </div>
          <div v-if="addressVerificationSummary.reason">
            <dt>{{ t("orders.addressVerification.reason") }}</dt>
            <dd>{{ t(addressVerificationSummary.reasonLabelKey) }}</dd>
          </div>
          <div v-if="addressVerificationSummary.confirmedAt">
            <dt>{{ t("orders.addressVerification.confirmedAt") }}</dt>
            <dd>{{ addressVerificationSummary.confirmedAt }}</dd>
          </div>
          <div v-if="addressVerificationSummary.original">
            <dt>{{ t("orders.addressVerification.original") }}</dt>
            <dd>{{ addressVerificationSummary.original }}</dd>
          </div>
          <div v-if="addressVerificationSummary.suggested">
            <dt>{{ t("orders.addressVerification.suggested") }}</dt>
            <dd>{{ addressVerificationSummary.suggested }}</dd>
          </div>
          <div v-if="addressVerificationSummary.selected">
            <dt>{{ t("orders.addressVerification.selected") }}</dt>
            <dd>{{ addressVerificationSummary.selected }}</dd>
          </div>
          <div v-if="addressVerificationSummary.providerResponseId">
            <dt>{{ t("orders.addressVerification.providerResponseId") }}</dt>
            <dd>{{ addressVerificationSummary.providerResponseId }}</dd>
          </div>
          <div v-if="addressVerificationSummary.providerStatus">
            <dt>{{ t("orders.addressVerification.providerStatus") }}</dt>
            <dd>{{ t(addressVerificationSummary.providerStatusLabelKey) }}</dd>
          </div>
        </dl>
        <p v-if="addressVerificationSummary.warningKey" class="order-address-verification-warning">
          {{ t(addressVerificationSummary.warningKey) }}
        </p>
        <p v-if="addressVerificationSummary.sourceWarningKey" class="order-address-verification-warning">
          {{ t(addressVerificationSummary.sourceWarningKey) }}
        </p>
        <p v-if="addressVerificationSummary.providerWarningKey" class="order-address-verification-warning">
          {{ t(addressVerificationSummary.providerWarningKey) }}
        </p>
      </section>
      <section
        v-if="orderMembershipEligibilityReview.lines.length"
        class="membership-eligibility-panel order-membership-eligibility"
        :aria-label="t('membership.account.eligibility.aria')"
      >
        <header>
          <div>
            <p class="eyebrow">{{ t("membership.account.eligibility.eyebrow") }}</p>
            <h2>{{ t("membership.account.eligibility.title") }}</h2>
            <p>{{ t("membership.account.eligibility.intro") }}</p>
          </div>
          <dl>
            <div>
              <dt>{{ t("membership.account.eligibility.summary.eligible") }}</dt>
              <dd>{{ orderMembershipEligibilityReview.eligibleCount }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.summary.ineligible") }}</dt>
              <dd>{{ orderMembershipEligibilityReview.ineligibleCount }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.summary.savings") }}</dt>
              <dd>{{ money(orderMembershipEligibilityReview.savingsTotal) }}</dd>
            </div>
          </dl>
        </header>
        <article
          v-for="line in orderMembershipEligibilityReview.lines"
          :key="`${line.name}-${line.key}`"
          class="membership-eligibility-row"
          :class="{ 'is-eligible': line.eligible }"
        >
          <div>
            <span>{{ t(`membership.account.eligibility.reasons.${line.key}.label`) }}</span>
            <h3>{{ line.name }}</h3>
            <p>{{ t(`membership.account.eligibility.reasons.${line.key}.description`) }}</p>
          </div>
          <dl>
            <div>
              <dt>{{ t("membership.account.eligibility.line.regular") }}</dt>
              <dd>{{ money(line.regularPrice) }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.line.member") }}</dt>
              <dd>{{ money(line.memberPrice) }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.line.savings") }}</dt>
              <dd>{{ money(line.savings) }}</dd>
            </div>
          </dl>
        </article>
      </section>
    </article>

    <section class="order-list">
      <a v-for="order in orders" :key="order.id" class="order-row" :href="getOrderDetailPath(order.id, order.payOrderId)">
        <span>
          <small>{{ t("orders.orderLabel") }}</small>
          {{ order.no }}
        </span>
        <span>{{ statusLabel(order.status) }}</span>
        <span class="order-member-savings">
          <small>{{ t("orders.memberSavings") }}</small>
          {{ getOrderMembershipSavingsLabel(order) }}
        </span>
        <strong>{{ money(order.payPrice) }}</strong>
        <em>{{ t("orders.view") }}</em>
      </a>
      <div v-if="!loading && !tokenRequired && !error && orders.length === 0" class="orders-empty">
        <p>{{ t("orders.empty") }}</p>
        <a class="orders-recovery-action" href="/">{{ t("orders.actions.shop") }}</a>
      </div>
    </section>
  </section>
</template>
