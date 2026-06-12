<script setup>
import { computed, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import {
  getMembershipEligibilityItemsFromOrderItems,
  getMembershipEligibilityReview,
} from "../services/membershipAccount.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { getOrderDetail, getOrderPage } from "../services/yudaoOrderApi.js";
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
const orderId = computed(() => new URLSearchParams(window.location.search).get("id"));
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
let ordersRequestId = 0;

const clearOrderData = () => {
  orders.value = [];
  total.value = 0;
  detail.value = null;
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
      <a v-for="order in orders" :key="order.id" class="order-row" :href="`/orders?id=${order.id}`">
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
