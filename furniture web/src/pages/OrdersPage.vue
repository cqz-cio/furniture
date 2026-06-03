<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { getOrderDetail, getOrderPage, readYudaoToken } from "../services/yudaoClient.js";
import { useI18n } from "../i18n.js";

const loading = ref(true);
const error = ref("");
const tokenRequired = ref(false);
const orders = ref([]);
const total = ref(0);
const detail = ref(null);
const orderId = computed(() => new URLSearchParams(window.location.search).get("id"));
const { t } = useI18n();
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const statusLabel = (status) => t("orders.status", { status: status ?? "Pending" });

const loadOrders = async () => {
  loading.value = true;
  error.value = "";
  tokenRequired.value = false;
  try {
    if (!readYudaoToken()) {
      tokenRequired.value = true;
      return;
    }
    if (orderId.value) {
      detail.value = await getOrderDetail(orderId.value);
    }
    const page = await getOrderPage({ pageNo: 1, pageSize: 10 });
    orders.value = page.list;
    total.value = page.total;
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
};

onMounted(loadOrders);
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
    <p v-if="tokenRequired" class="checkout-error">{{ t("orders.tokenRequired") }}</p>
    <p v-else-if="error" class="checkout-error">{{ error }}</p>

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
    </article>

    <section class="order-list">
      <a v-for="order in orders" :key="order.id" class="order-row" :href="`/orders?id=${order.id}`">
        <span>
          <small>{{ t("orders.orderLabel") }}</small>
          {{ order.no }}
        </span>
        <span>{{ statusLabel(order.status) }}</span>
        <strong>{{ money(order.payPrice) }}</strong>
        <em>{{ t("orders.view") }}</em>
      </a>
      <p v-if="!loading && !tokenRequired && !error && orders.length === 0" class="orders-empty">
        {{ t("orders.empty") }}
      </p>
    </section>
  </section>
</template>
