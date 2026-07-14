<script setup>
import { computed, onMounted, ref } from "vue";
import { useI18n } from "../i18n.js";
import { registryItemToCartProduct } from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { getPublicYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const registryEntries = [
  { labelKey: "giftRegistry.nav.find", href: membershipRoutes.giftRegistryFind },
  { labelKey: "giftRegistry.nav.create", href: membershipRoutes.giftRegistryCreate },
  { labelKey: "giftRegistry.nav.manage", href: membershipRoutes.giftRegistryManage },
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
    loadMessage.value = error?.message || t("giftRegistry.public.unavailable");
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
      <p class="eyebrow">{{ t("giftRegistry.eyebrow") }}</p>
      <template v-if="publicCode">
        <h1>{{ registry?.registrants?.primaryName || t("giftRegistry.public.titleFallback") }}</h1>
        <p>
          {{ registry?.event?.type || t("giftRegistry.public.eventFallback") }} {{ registry?.event?.date || "" }}
          {{ registry?.event?.location ? `- ${registry.event.location}` : "" }}
        </p>
      </template>
      <template v-else>
        <h1>{{ t("giftRegistry.home.title") }}</h1>
        <p>{{ t("giftRegistry.home.description") }}</p>
      </template>
    </header>

    <p v-if="loadMessage">{{ loadMessage }}</p>

    <section v-if="publicCode && registry" class="registry-result-list" aria-label="Public registry gifts">
      <article v-for="item in registry.items" :key="item.id || item.skuId">
        <div>
          <p class="eyebrow">
            {{
              t("giftRegistry.public.requestedPurchased", {
                requested: item.quantityRequested,
                purchased: item.quantityPurchased,
              })
            }}
          </p>
          <h2>{{ item.productName }}</h2>
          <p>{{ item.note || t("giftRegistry.public.itemFallbackNote") }}</p>
        </div>
        <a :href="`/product?id=${item.spuId}&registryItemId=${item.id}`">{{ t("giftRegistry.public.viewProduct") }}</a>
        <button class="registry-cart-button" type="button" @click="handleAddRegistryGiftToCart(item)">
          {{ t("giftRegistry.public.addGiftToBag") }}
        </button>
      </article>
      <article v-if="!registry.items.length">
        <div>
          <p class="eyebrow">{{ t("giftRegistry.public.noGiftsEyebrow") }}</p>
          <h2>{{ t("giftRegistry.public.noGiftsTitle") }}</h2>
          <p>{{ t("giftRegistry.public.noGiftsDescription") }}</p>
        </div>
      </article>
    </section>

    <section v-else-if="!publicCode" class="registry-entry-list" aria-label="Gift registry actions">
      <a v-for="entry in registryEntries" :key="entry.labelKey" :href="entry.href">{{ t(entry.labelKey) }}</a>
    </section>
  </section>
</template>
