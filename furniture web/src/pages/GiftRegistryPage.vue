<script setup>
import { computed, onMounted, ref } from "vue";
import { registryItemToCartProduct } from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { getPublicYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";

const emit = defineEmits(["add-to-cart"]);

const registryEntries = [
  { title: "Find a Registry", href: membershipRoutes.giftRegistryFind },
  { title: "Create a Registry", href: membershipRoutes.giftRegistryCreate },
  { title: "Manage Your Registry", href: membershipRoutes.giftRegistryManage },
];

const publicCode = computed(() => {
  const match = window.location.pathname.match(/^\/gift-registry\/([^/?#]+)/);
  return match?.[1] ? decodeURIComponent(match[1]) : "";
});
const registry = ref(null);
const loadState = ref("idle");
const loadMessage = ref("");

const loadPublicRegistry = async () => {
  if (!publicCode.value) return;
  loadState.value = "loading";
  try {
    registry.value = await getPublicYudaoGiftRegistry(publicCode.value, { token: "" });
    loadState.value = "loaded";
    loadMessage.value = "";
  } catch (error) {
    registry.value = null;
    loadState.value = "error";
    loadMessage.value = error?.message || "This public gift registry is unavailable.";
  }
};

const handleAddRegistryGiftToCart = (item) => {
  const product = registryItemToCartProduct(item);
  emit("add-to-cart", product, 1, { registryContext: product.registryContext });
};

onMounted(loadPublicRegistry);
</script>

<template>
  <section class="membership-page service-page-shell">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">Gift Registry</p>
      <template v-if="publicCode">
        <h1>{{ registry?.registrants?.primaryName || "Public Registry" }}</h1>
        <p>
          {{ registry?.event?.type || "Registry" }} {{ registry?.event?.date || "" }}
          {{ registry?.event?.location ? `- ${registry.event.location}` : "" }}
        </p>
      </template>
      <template v-else>
        <h1>Find, create and manage furniture gift registries.</h1>
        <p>Registry planning includes event details, registrant information, delivery addresses and privacy controls.</p>
      </template>
    </header>

    <p v-if="loadMessage">{{ loadMessage }}</p>

    <section v-if="publicCode && registry" class="registry-result-list" aria-label="Public registry gifts">
      <article v-for="item in registry.items" :key="item.id || item.skuId">
        <div>
          <p class="eyebrow">Requested {{ item.quantityRequested }} - Purchased {{ item.quantityPurchased }}</p>
          <h2>{{ item.productName }}</h2>
          <p>{{ item.note || "Gift item linked to Oakved product inventory." }}</p>
        </div>
        <a :href="`/product?id=${item.spuId}&registryItemId=${item.id}`">View Product</a>
        <button class="registry-cart-button" type="button" @click="handleAddRegistryGiftToCart(item)">Add Gift To Bag</button>
      </article>
      <article v-if="!registry.items.length">
        <div>
          <p class="eyebrow">No Gifts Yet</p>
          <h2>This registry does not have public gift items yet.</h2>
          <p>Check back after the owner adds items.</p>
        </div>
      </article>
    </section>

    <section v-else-if="!publicCode" class="registry-entry-list" aria-label="Gift registry actions">
      <a v-for="entry in registryEntries" :key="entry.title" :href="entry.href">{{ entry.title }}</a>
    </section>
  </section>
</template>
