<script setup>
import { computed, onMounted, ref, watch } from "vue";
import ProductImage from "../components/ProductImage.vue";
import { useI18n } from "../i18n.js";
import { accountMenuItems, accountMenuLabelKeys, membershipRoutes } from "../services/membershipNavigation.js";
import {
  clearLocalWishlist,
  readLocalWishlist,
  removeLocalWishlistItem,
  updateLocalWishlistItemQuantity,
  writeLocalWishlist,
} from "../services/localWishlist.js";
import {
  deleteFavorite,
  getRemoteWishlistItems,
  syncLocalWishlistToRemote,
  updateFavoriteCount,
} from "../services/yudaoFavoriteApi.js";
import { isYudaoAuthError, readYudaoToken } from "../services/yudaoRequest.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});
const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();
const wishlistItems = ref([]);
const noticeKey = ref("");
const statusKey = ref("");
const statusDetail = ref("");
const loading = ref(false);
const wishlistMode = ref("local");
const memberDiscountRate = 0.7;
let wishlistRequestId = 0;

const getErrorDetail = (error) =>
  [error?.kind ? `kind=${error.kind}` : "", error?.status ? `status=${error.status}` : "", error?.message || ""]
    .filter(Boolean)
    .join(", ");

const useLocalWishlist = (key = "") => {
  wishlistMode.value = "local";
  wishlistItems.value = readLocalWishlist();
  statusKey.value = key;
};

const mergeLocalWishlist = async () => {
  const localItems = readLocalWishlist();
  if (!localItems.length) return;
  const result = await syncLocalWishlistToRemote(localItems);
  if (result.failedItems?.length) {
    writeLocalWishlist(result.failedItems);
    noticeKey.value = "wishlist.partialSync";
    return;
  }
  clearLocalWishlist();
};

const loadWishlist = async () => {
  const token = readYudaoToken();
  statusDetail.value = "";
  noticeKey.value = "";

  if (!token) {
    useLocalWishlist("wishlist.signInRequired");
    return;
  }

  const requestId = ++wishlistRequestId;
  loading.value = true;
  statusKey.value = "";

  try {
    await mergeLocalWishlist();
    const page = await getRemoteWishlistItems();
    if (requestId !== wishlistRequestId) return;
    wishlistMode.value = "yudao";
    wishlistItems.value = page.list;
  } catch (error) {
    if (requestId !== wishlistRequestId) return;
    statusDetail.value = getErrorDetail(error);
    useLocalWishlist(isYudaoAuthError(error) ? "wishlist.signInRequired" : "wishlist.remoteUnavailable");
  } finally {
    if (requestId === wishlistRequestId) loading.value = false;
  }
};

const totalQuantity = computed(() => wishlistItems.value.reduce((sum, item) => sum + item.quantity, 0));
const showRetry = computed(() => statusKey.value === "wishlist.remoteUnavailable");
const money = (value) =>
  `$${Number(value || 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const regularPrice = (item) => Math.round(Number(item.price || 0) / memberDiscountRate);
const detailHref = (item) => (item?.id ? `/sofa-pdp?id=${encodeURIComponent(item.id)}` : "/products");
const itemNumber = (item) => `${item.skuId} NOCM`;
const itemColor = (item) => item.color || "Wheat";
const itemFabric = (item) => item.subtitle || "Perennials Performance Textured Linen Weave";
const itemWidth = (item) => item.dimensions || item.width || `7'`;
const availability = (item) => item.delivery || t("wishlist.defaultAvailability");

const setQuantity = async (item, quantity) => {
  const nextQuantity = Math.max(1, Math.floor(Number(quantity) || 1));
  if (wishlistMode.value === "yudao" || item.source === "yudao") {
    wishlistItems.value = wishlistItems.value.map((row) =>
      row.skuId === item.skuId ? { ...row, quantity: nextQuantity } : row,
    );
    if (readYudaoToken()) {
      try {
        await updateFavoriteCount(item, nextQuantity);
        statusKey.value = "";
        statusDetail.value = "";
      } catch (error) {
        statusDetail.value = getErrorDetail(error);
        statusKey.value = isYudaoAuthError(error) ? "wishlist.signInRequired" : "wishlist.remoteUnavailable";
      }
    }
    return;
  }
  wishlistItems.value = updateLocalWishlistItemQuantity(item.skuId, nextQuantity);
};

const adjustQuantity = (item, delta) => {
  setQuantity(item, Number(item.quantity) + delta);
};

const addToCart = (item) => {
  emit("add-to-cart", item, item.quantity);
  noticeKey.value = "wishlist.addedToCart";
};

const removeItem = async (item) => {
  if ((wishlistMode.value === "yudao" || item.source === "yudao") && readYudaoToken()) {
    try {
      await deleteFavorite(item);
      await loadWishlist();
      noticeKey.value = "wishlist.removed";
      return;
    } catch (error) {
      statusDetail.value = getErrorDetail(error);
      statusKey.value = isYudaoAuthError(error) ? "wishlist.signInRequired" : "wishlist.remoteUnavailable";
    }
  }
  wishlistItems.value = removeLocalWishlistItem(item.skuId);
  noticeKey.value = "wishlist.removed";
};

onMounted(loadWishlist);
watch(() => props.authVersion, loadWishlist);
</script>

<template>
  <section class="account-page account-wishlist-page">
    <aside class="account-sidebar account-wishlist-sidebar" :aria-label="t('membership.account.menuAria')">
      <p class="eyebrow">{{ t("membership.account.myAccount") }}</p>
      <a
        v-for="item in accountMenuItems"
        :key="item.label"
        :aria-current="item.href === membershipRoutes.accountWishlist ? 'page' : undefined"
        :href="item.href"
      >
        {{ t(accountMenuLabelKeys[item.label] || "membership.account.menuProfile") }}
      </a>
    </aside>

    <section class="account-content account-wishlist-content">
      <header class="wishlist-page-head">
        <div>
          <p class="eyebrow">{{ t("wishlist.eyebrow") }}</p>
          <h1>{{ t("wishlist.title") }}</h1>
          <p v-if="wishlistItems.length">{{ t("wishlist.itemCount", { count: totalQuantity }) }}</p>
        </div>
      </header>

      <p v-if="loading" class="wishlist-notice" aria-live="polite">{{ t("wishlist.loading") }}</p>
      <div v-else-if="statusKey" class="wishlist-notice" aria-live="polite">
        <span>{{ t(statusKey) }}</span>
        <small v-if="statusDetail">{{ statusDetail }}</small>
        <button v-if="showRetry" type="button" @click="loadWishlist">{{ t("wishlist.retrySync") }}</button>
      </div>
      <p v-if="noticeKey" class="wishlist-notice" aria-live="polite">{{ t(noticeKey) }}</p>

      <section
        v-if="!loading && wishlistItems.length"
        id="component-wishlist"
        class="wishlist-list"
        :aria-label="t('wishlist.title')"
      >
        <article v-for="item in wishlistItems" :key="item.skuId" class="wishlist-line">
          <a class="wishlist-line-image" :href="detailHref(item)" :aria-label="item.name">
            <ProductImage :src="item.cover" :label="item.name" />
          </a>

          <div class="wishlist-line-main">
            <div class="wishlist-title-panel">
              <a :href="detailHref(item)">{{ item.name }}</a>
              <dl class="wishlist-spec-list">
                <div>
                  <dt>{{ t("wishlist.itemNumber") }}</dt>
                  <dd>{{ itemNumber(item) }}</dd>
                </div>
                <div>
                  <dt>{{ t("wishlist.color") }}</dt>
                  <dd>{{ itemColor(item) }}</dd>
                </div>
                <div>
                  <dt>{{ t("wishlist.fabric") }}</dt>
                  <dd>{{ itemFabric(item) }}</dd>
                </div>
                <div>
                  <dt>{{ t("wishlist.width") }}</dt>
                  <dd>{{ itemWidth(item) }}</dd>
                </div>
              </dl>
            </div>

            <div class="wishlist-line-commerce">
              <div class="wishlist-price-panel">
                <dl>
                  <div>
                    <dt>{{ t("wishlist.member") }}</dt>
                    <dd>{{ money(item.price) }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("wishlist.regular") }}</dt>
                    <dd>{{ money(regularPrice(item)) }}</dd>
                  </div>
                </dl>
              </div>

              <div class="wishlist-quantity-panel">
                <span>{{ t("cart.quantity") }}</span>
                <span class="cart-quantity-stepper wishlist-quantity-stepper">
                  <button
                    type="button"
                    :disabled="item.quantity <= 1"
                    :aria-label="t('wishlist.decreaseQuantity')"
                    @click="adjustQuantity(item, -1)"
                  >
                    -
                  </button>
                  <input
                    :aria-label="t('cart.quantity')"
                    :value="item.quantity"
                    min="1"
                    type="number"
                    @change="setQuantity(item, $event.target.value)"
                  />
                  <button type="button" :aria-label="t('wishlist.increaseQuantity')" @click="adjustQuantity(item, 1)">
                    +
                  </button>
                </span>
              </div>

              <div class="wishlist-action-panel">
                <button class="wishlist-cart-button" type="button" @click="addToCart(item)">
                  {{ t("wishlist.addToCart") }}
                </button>
                <button class="wishlist-remove-button" type="button" @click="removeItem(item)">
                  {{ t("wishlist.remove") }}
                </button>
              </div>
            </div>

            <p class="wishlist-availability">{{ t("wishlist.availability", { value: availability(item) }) }}</p>
          </div>
        </article>
      </section>

      <section v-else-if="!loading" class="wishlist-empty">
        <p class="eyebrow">{{ t("wishlist.emptyEyebrow") }}</p>
        <h2>{{ t("wishlist.emptyTitle") }}</h2>
        <p>{{ t("wishlist.emptyHelp") }}</p>
        <a href="/products">{{ t("wishlist.continueShopping") }}</a>
      </section>
    </section>
  </section>
</template>
