<script setup>
import { computed, onMounted, ref } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { getOrderDetail, getOrderPage, readYudaoToken } from "../services/yudaoClient.js";

const loading = ref(true);
const error = ref("");
const orders = ref([]);
const total = ref(0);
const detail = ref(null);
const orderId = computed(() => new URLSearchParams(window.location.search).get("id"));
const money = (value) => `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const loadOrders = async () => {
  loading.value = true;
  error.value = "";
  try {
    if (!readYudaoToken()) {
      error.value = "Add a Yudao App token to view orders.";
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
      <p class="eyebrow">Orders</p>
      <h1>Order History</h1>
      <p v-if="total">{{ total }} orders</p>
    </header>

    <p v-if="loading" class="product-loading">Loading orders...</p>
    <p v-if="error" class="checkout-error">{{ error }}</p>

    <article v-if="detail" class="order-detail-card">
      <h2>{{ detail.no }}</h2>
      <p>Status: {{ detail.status }}</p>
      <strong>{{ money(detail.payPrice) }}</strong>
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
        <span>{{ order.no }}</span>
        <span>Status {{ order.status }}</span>
        <strong>{{ money(order.payPrice) }}</strong>
      </a>
    </section>
  </section>
</template>
