<script setup>
import { onMounted, ref, watch } from "vue";
import { accountMenuItems, accountMenuLabelKeys, membershipRoutes } from "../services/membershipNavigation.js";
import { getOrderPage } from "../services/yudaoOrderApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";
import { useI18n } from "../i18n.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});

const { currentLocale, formatCurrency, t } = useI18n();
const loading = ref(true);
const error = ref("");
const tokenRequired = ref(false);
const orders = ref([]);
let billingRequestId = 0;

const money = (value) => formatCurrency(value);
const dateLabel = (value) =>
  value
    ? new Date(value).toLocaleString(currentLocale.value, { dateStyle: "medium", timeStyle: "short" })
    : t("membership.account.billingHistory.pending");

const loadBilling = async () => {
  const requestId = ++billingRequestId;
  loading.value = true;
  error.value = "";
  tokenRequired.value = false;
  orders.value = [];
  try {
    if (!readYudaoToken()) {
      tokenRequired.value = true;
      return;
    }
    const page = await getOrderPage({ pageNo: 1, pageSize: 20 });
    if (requestId !== billingRequestId) return;
    orders.value = page.list;
  } catch {
    if (requestId !== billingRequestId) return;
    error.value = t("membership.account.billingHistory.error");
  } finally {
    if (requestId === billingRequestId) loading.value = false;
  }
};

onMounted(loadBilling);
watch(() => props.authVersion, loadBilling);
</script>

<template>
  <section class="account-page">
    <aside class="account-sidebar" :aria-label="t('membership.account.menuAria')">
      <p class="eyebrow">{{ t("membership.account.myAccount") }}</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">
        {{ t(accountMenuLabelKeys[item.label] || "membership.account.menuProfile") }}
      </a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">{{ t("membership.account.billingHistory.eyebrow") }}</p>
      <h1>{{ t("membership.account.billingHistory.title") }}</h1>
      <p v-if="loading" class="product-loading">{{ t("membership.account.billingHistory.loading") }}</p>
      <div v-if="tokenRequired" class="checkout-error">
        <p>{{ t("membership.account.billingHistory.signInRequired") }}</p>
        <div class="orders-recovery-actions">
          <a class="orders-recovery-action" :href="membershipRoutes.checkoutAuth">
            {{ t("membership.account.billingHistory.actions.connectAccount") }}
          </a>
        </div>
      </div>
      <div v-else-if="error" class="checkout-error">
        <p>{{ error }}</p>
        <div class="orders-recovery-actions">
          <button class="orders-recovery-action" type="button" @click="loadBilling">
            {{ t("membership.account.billingHistory.actions.retry") }}
          </button>
        </div>
      </div>

      <aside class="membership-billing-context" :aria-label="t('membership.account.billingHistory.contextAria')">
        <header>
          <p class="eyebrow">{{ t("membership.account.billingHistory.contextEyebrow") }}</p>
          <h2>{{ t("membership.account.billingHistory.contextTitle") }}</h2>
        </header>
        <div class="membership-billing-context-grid">
          <article v-for="item in ['annualFee', 'renewalBilling', 'manageBilling']" :key="item">
            <span>{{ t(`membership.account.billingHistory.context.${item}.label`) }}</span>
            <p>{{ t(`membership.account.billingHistory.context.${item}.description`) }}</p>
          </article>
        </div>
        <div class="membership-billing-context-actions">
          <a :href="membershipRoutes.accountMembership">{{ t("membership.account.billingHistory.manageMembership") }}</a>
          <a :href="membershipRoutes.membershipTerms">{{ t("membership.account.billingHistory.viewTerms") }}</a>
        </div>
      </aside>

      <section v-if="orders.length" class="billing-list" :aria-label="t('membership.account.billingHistory.listAria')">
        <article v-for="order in orders" :key="order.id" class="billing-row">
          <div class="billing-meta-grid">
            <span>
              <small>{{ t("membership.account.billingHistory.fields.order") }}</small>
              {{ order.no }}
            </span>
            <span>
              <small>{{ t("membership.account.billingHistory.fields.payment") }}</small>
              {{ order.payOrderId || t("membership.account.billingHistory.pending") }}
            </span>
            <span>
              <small>{{ t("membership.account.billingHistory.fields.status") }}</small>
              {{ order.payStatus ? t("membership.account.billingHistory.paid") : t("membership.account.billingHistory.unpaid") }}
            </span>
            <span>
              <small>{{ t("membership.account.billingHistory.fields.paidAt") }}</small>
              {{ dateLabel(order.raw?.payTime) }}
            </span>
          </div>
          <div class="billing-row-actions">
            <strong>{{ money(order.payPrice) }}</strong>
            <a :href="`/account/orders?id=${order.id}`">{{ t("membership.account.billingHistory.viewOrder") }}</a>
          </div>
        </article>
      </section>
      <div v-else-if="!loading && !tokenRequired && !error" class="orders-empty">
        <p>{{ t("membership.account.billingHistory.empty") }}</p>
        <a class="orders-recovery-action" :href="membershipRoutes.checkoutAuth">
          {{ t("membership.account.billingHistory.actions.createOrder") }}
        </a>
      </div>
    </section>
  </section>
</template>
