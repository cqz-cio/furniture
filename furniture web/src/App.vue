<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from "vue";
import CartDrawer from "./components/CartDrawer.vue";
import RhFooter from "./components/RhFooter.vue";
import RhHeader from "./components/RhHeader.vue";
import {
  addLocalCartItem,
  normalizeCartQuantity,
  readLocalCart,
  removeLocalCartItem,
  updateLocalCartItemQuantity,
  writeLocalCart,
} from "./services/localCart.js";
import { clearYudaoSession, readYudaoSession, redactSecret } from "./services/authSession.js";
import { playAddToCartFlyAnimation } from "./services/cartFlyAnimation.js";
import { addLocalWishlistItem } from "./services/localWishlist.js";
import { getCheckoutEntryRoute } from "./services/membershipNavigation.js";
import { addCartItem, deleteCartItems, getRemoteCartItems, updateCartItemCount } from "./services/yudaoCartApi.js";
import { createFavorite } from "./services/yudaoFavoriteApi.js";
import { getYudaoAppTenantId, isYudaoAuthError, isYudaoBusinessError, readYudaoToken } from "./services/yudaoRequest.js";

const AccountAddressBookPage = defineAsyncComponent(() => import("./pages/AccountAddressBookPage.vue"));
const AccountBillingPage = defineAsyncComponent(() => import("./pages/AccountBillingPage.vue"));
const AccountMembershipPage = defineAsyncComponent(() => import("./pages/AccountMembershipPage.vue"));
const AccountPage = defineAsyncComponent(() => import("./pages/AccountPage.vue"));
const AccountProfilePage = defineAsyncComponent(() => import("./pages/AccountProfilePage.vue"));
const AccountWishlistPage = defineAsyncComponent(() => import("./pages/AccountWishlistPage.vue"));
const BabyChildCategoryPage = defineAsyncComponent(() => import("./pages/BabyChildCategoryPage.vue"));
const BabyChildPage = defineAsyncComponent(() => import("./pages/BabyChildPage.vue"));
const CheckoutAuthPage = defineAsyncComponent(() => import("./pages/CheckoutAuthPage.vue"));
const CheckoutPage = defineAsyncComponent(() => import("./pages/CheckoutPage.vue"));
const GiftRegistryCreatePage = defineAsyncComponent(() => import("./pages/GiftRegistryCreatePage.vue"));
const GiftRegistryFindPage = defineAsyncComponent(() => import("./pages/GiftRegistryFindPage.vue"));
const GiftRegistryManagePage = defineAsyncComponent(() => import("./pages/GiftRegistryManagePage.vue"));
const GiftRegistryPage = defineAsyncComponent(() => import("./pages/GiftRegistryPage.vue"));
const HomePage = defineAsyncComponent(() => import("./pages/HomePage.vue"));
const MembershipEnrollmentPage = defineAsyncComponent(() => import("./pages/MembershipEnrollmentPage.vue"));
const MembershipFaqPage = defineAsyncComponent(() => import("./pages/MembershipFaqPage.vue"));
const MembershipPage = defineAsyncComponent(() => import("./pages/MembershipPage.vue"));
const MembershipTermsPage = defineAsyncComponent(() => import("./pages/MembershipTermsPage.vue"));
const MissingExtractionPage = defineAsyncComponent(() => import("./pages/MissingExtractionPage.vue"));
const OrdersPage = defineAsyncComponent(() => import("./pages/OrdersPage.vue"));
const OutdoorPage = defineAsyncComponent(() => import("./pages/OutdoorPage.vue"));
const SalePage = defineAsyncComponent(() => import("./pages/SalePage.vue"));
const SofaPdpPage = defineAsyncComponent(() => import("./pages/SofaPdpPage.vue"));
const SofasPlpPage = defineAsyncComponent(() => import("./pages/SofasPlpPage.vue"));
const TeenPage = defineAsyncComponent(() => import("./pages/TeenPage.vue"));
const TradeApplicationPage = defineAsyncComponent(() => import("./pages/TradeApplicationPage.vue"));
const TradeFaqPage = defineAsyncComponent(() => import("./pages/TradeFaqPage.vue"));
const TradeSignInPage = defineAsyncComponent(() => import("./pages/TradeSignInPage.vue"));

const pageRoutes = {
  home: "/",
  sale: "/sale",
  outdoor: "/outdoor",
  "sofas-plp": "/products",
  "sofa-pdp": "/product",
  teen: "/teen",
  "baby-child": "/baby-child",
  "baby-child-furniture": "/baby-child/furniture",
  "baby-child-bedding": "/baby-child/bedding",
  "baby-child-nursery": "/baby-child/nursery",
  "baby-child-decor": "/baby-child/decor",
  "baby-child-lighting": "/baby-child/lighting",
  "baby-child-rugs": "/baby-child/rugs",
  "baby-child-windows": "/baby-child/windows",
  "baby-child-storage": "/baby-child/storage",
  "baby-child-playroom": "/baby-child/playroom",
  "baby-child-gifts": "/baby-child/gifts",
  "baby-child-teen": "/baby-child/teen",
  "baby-child-sale": "/baby-child/sale",
  "baby-child-registry": "/baby-child/registry",
  membership: "/membership",
  "membership-enrollment": "/membership/enrollment",
  "membership-terms": "/membership/terms",
  "membership-faqs": "/membership/faqs",
  account: "/account",
  "account-membership": "/account/membership",
  "account-profile": "/account/profile",
  "account-address-book": "/account/address-book",
  "account-orders": "/account/orders",
  "account-billing": "/account/billing",
  "account-wishlist": "/account/wishlist",
  "checkout-auth": "/checkout/auth",
  "gift-registry": "/gift-registry",
  "gift-registry-create": "/gift-registry/create",
  "gift-registry-find": "/gift-registry/find",
  "gift-registry-manage": "/gift-registry/manage",
  checkout: "/checkout",
  orders: "/orders",
  "trade-sign-in": "/trade/sign-in",
  "trade-application": "/trade/apply",
  "trade-faq": "/trade/faq",
  missing: "/missing",
};

const routePages = Object.fromEntries(Object.entries(pageRoutes).map(([page, path]) => [path, page]));
const routeAliases = {
  "/sofas-plp": "sofas-plp",
  "/sofa-pdp": "sofa-pdp",
  "/orders": "account-orders",
  "/account/payment-methods": "account",
  "/account/gift-registry": "gift-registry",
  "/account/sign-in": "account",
  "/account/register": "account",
  "/us/en/trade/membership-application": "trade-application",
  "/us/en/trade/faq": "trade-faq",
  "/us/en/trade/login": "trade-sign-in",
  "/checkout/shipping": "checkout",
};

const pageFromPath = (path) => {
  const normalizedPath = path.replace(/\/$/, "") || "/";
  return (
    routePages[normalizedPath] ||
    routeAliases[normalizedPath] ||
    (normalizedPath.startsWith("/gift-registry/") ? "gift-registry" : "missing")
  );
};

const currentPage = ref(pageFromPath(window.location.pathname));
const routeSignature = ref(`${window.location.pathname}${window.location.search}${window.location.hash}`);
const cartOpen = ref(false);
const cartItems = ref(readLocalCart());
const cartMode = ref("local");
const cartNoticeKey = ref("");
const cartNoticeDetail = ref("");
const cartDebugInfo = ref("");
const cartWishlistNoticeKey = ref("");
const authVersion = ref(0);
let remoteCartRequestId = 0;

const pageComponent = computed(() => {
  if (currentPage.value === "home") return HomePage;
  if (currentPage.value === "sale") return SalePage;
  if (currentPage.value === "outdoor") return OutdoorPage;
  if (currentPage.value === "sofas-plp") return SofasPlpPage;
  if (currentPage.value === "sofa-pdp") return SofaPdpPage;
  if (currentPage.value === "teen") return TeenPage;
  if (currentPage.value === "baby-child") return BabyChildPage;
  if (currentPage.value.startsWith("baby-child-")) return BabyChildCategoryPage;
  if (currentPage.value === "membership") return MembershipPage;
  if (currentPage.value === "membership-enrollment") return MembershipEnrollmentPage;
  if (currentPage.value === "membership-terms") return MembershipTermsPage;
  if (currentPage.value === "membership-faqs") return MembershipFaqPage;
  if (currentPage.value === "account") return AccountPage;
  if (currentPage.value === "account-membership") return AccountMembershipPage;
  if (currentPage.value === "account-profile") return AccountProfilePage;
  if (currentPage.value === "account-address-book") return AccountAddressBookPage;
  if (currentPage.value === "account-billing") return AccountBillingPage;
  if (currentPage.value === "account-wishlist") return AccountWishlistPage;
  if (currentPage.value === "account-orders") return OrdersPage;
  if (currentPage.value === "checkout-auth") return CheckoutAuthPage;
  if (currentPage.value === "gift-registry") return GiftRegistryPage;
  if (currentPage.value === "gift-registry-create") return GiftRegistryCreatePage;
  if (currentPage.value === "gift-registry-find") return GiftRegistryFindPage;
  if (currentPage.value === "gift-registry-manage") return GiftRegistryManagePage;
  if (currentPage.value === "checkout") return CheckoutPage;
  if (currentPage.value === "orders") return OrdersPage;
  if (currentPage.value === "trade-sign-in") return TradeSignInPage;
  if (currentPage.value === "trade-application") return TradeApplicationPage;
  if (currentPage.value === "trade-faq") return TradeFaqPage;
  return MissingExtractionPage;
});

const pageSeo = {
  home: {
    title: "Oakved | Luxury Furniture, Lighting & Home Decor",
    description: "Explore Oakved furniture, lighting, textiles and room inspiration for refined living.",
  },
  "sofas-plp": {
    title: "Furniture Collection | Oakved",
    description: "Shop bedroom furniture, storage, desks, tables, seating and wood finishes with member pricing.",
  },
  "sofa-pdp": {
    title: "Product Details | Oakved",
    description: "Review furniture details, finishes, delivery windows, member pricing and room inspiration.",
  },
  sale: {
    title: "Sale | Oakved",
    description: "Explore selected furniture, lighting and decor offers with refined room inspiration.",
  },
  outdoor: {
    title: "Outdoor Furniture | Oakved",
    description: "Shop outdoor furniture and garden room inspiration in weather-ready materials.",
  },
  teen: {
    title: "Teen Furniture | Oakved",
    description: "Explore teen bedroom, study and lounge furniture with elevated materials.",
  },
  "baby-child": {
    title: "Baby & Child Furniture | Oakved",
    description: "Explore nursery, playroom and child bedroom furniture with calm material palettes.",
  },
  "account-wishlist": {
    title: "Wishlist | Oakved",
    description: "Review saved Oakved furniture, add pieces to the bag, or remove items from your wishlist.",
  },
};

const applySeo = (page) => {
  const seo = pageSeo[page] || pageSeo.home;
  document.title = seo.title;
  let description = document.querySelector('meta[name="description"]');
  if (!description) {
    description = document.createElement("meta");
    description.setAttribute("name", "description");
    document.head.appendChild(description);
  }
  description.setAttribute("content", seo.description);
};

const syncPageFromLocation = () => {
  currentPage.value = pageFromPath(window.location.pathname);
  routeSignature.value = `${window.location.pathname}${window.location.search}${window.location.hash}`;
};

const navigateToPath = (path) => {
  const nextPath = path || "/";
  const nextPage = pageFromPath(nextPath.split("?")[0].split("#")[0]);
  currentPage.value = nextPage;
  if (`${window.location.pathname}${window.location.search}${window.location.hash}` !== nextPath) {
    window.history.pushState({ page: nextPage }, "", nextPath);
  }
  routeSignature.value = nextPath;
  window.dispatchEvent(new CustomEvent("oakved:navigation", { detail: { path: nextPath } }));
};

const handleInternalLinkClick = (event) => {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return;
  }

  const anchor = event.target.closest?.("a[href]");
  if (!anchor || anchor.target || anchor.hasAttribute("download")) return;

  const href = anchor.getAttribute("href") || "";
  if (!href || href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) return;

  const url = new URL(href, window.location.href);
  if (url.origin !== window.location.origin) return;

  const path = `${url.pathname}${url.search}${url.hash}`;
  if (pageFromPath(url.pathname) === "missing" && url.pathname !== pageRoutes.missing) return;

  event.preventDefault();
  navigateToPath(path);
};

const cartQuantity = computed(() => cartItems.value.reduce((sum, item) => sum + item.quantity, 0));
const usesOverlayHeader = computed(() => ["home", "sale"].includes(currentPage.value));
const usesCheckoutShell = computed(() => currentPage.value === "checkout");

const getYudaoCartErrorDetail = (error) => {
  const parts = [
    error?.kind ? `kind=${error.kind}` : "",
    Number.isFinite(Number(error?.code)) ? `code=${Number(error.code)}` : "",
    error?.status ? `status=${error.status}` : "",
    error?.message ? `msg=${error.message}` : "",
  ].filter(Boolean);
  return parts.length ? `Yudao cart sync failed: ${parts.join(", ")}` : "";
};

const getYudaoCartDebugInfo = (session, result) => {
  const token = session?.accessToken || "";
  const tokenInfo = token ? `yes (${redactSecret(token)})` : "no";
  return `Yudao debug: path=/trade/cart/list, tenant=${getYudaoAppTenantId()}, token=${tokenInfo}, ${result}`;
};

const switchToLocalCart = ({ noticeKey = "", noticeDetail = "" } = {}) => {
  cartItems.value = readLocalCart();
  cartMode.value = "local";
  cartNoticeKey.value = noticeKey;
  cartNoticeDetail.value = noticeDetail;
};

const switchToAuthRequiredCart = () => {
  clearYudaoSession();
  authVersion.value += 1;
  switchToLocalCart({ noticeKey: "cart.remoteAuthRequired" });
};

const loadRemoteCart = async () => {
  const requestId = ++remoteCartRequestId;
  const requestSession = readYudaoSession();
  cartDebugInfo.value = getYudaoCartDebugInfo(requestSession, "status=requesting");
  try {
    const remoteItems = await getRemoteCartItems();
    if (requestId !== remoteCartRequestId) return false;
    cartMode.value = "yudao";
    cartItems.value = remoteItems;
    cartNoticeKey.value = "";
    cartNoticeDetail.value = "";
    cartDebugInfo.value = getYudaoCartDebugInfo(requestSession, `status=success, items=${remoteItems.length}`);
    return true;
  } catch (caught) {
    if (requestId !== remoteCartRequestId) return false;
    cartDebugInfo.value = getYudaoCartDebugInfo(
      requestSession,
      `status=failed${getYudaoCartErrorDetail(caught) ? `, ${getYudaoCartErrorDetail(caught)}` : ""}`,
    );
    if (isYudaoAuthError(caught)) {
      switchToAuthRequiredCart();
      return true;
    }
    switchToLocalCart({
      noticeKey: "cart.remoteUnavailable",
      noticeDetail: getYudaoCartErrorDetail(caught),
    });
    return true;
  }
};

const handleAuthChange = async () => {
  await loadRemoteCart();
  authVersion.value += 1;
};

const playCartFlyAnimation = (options = {}) => {
  playAddToCartFlyAnimation({ trigger: options.trigger });
};

const addToCart = async (product, quantity = 1, options = {}) => {
  if (product.source === "yudao") {
    try {
      await addCartItem(product.skuId, quantity, { registryContext: options.registryContext || product.registryContext });
      await loadRemoteCart();
      playCartFlyAnimation(options);
      return;
    } catch (caught) {
      if (isYudaoAuthError(caught)) {
        switchToAuthRequiredCart();
        return;
      }
      switchToLocalCart({
        noticeKey: "cart.remoteMutationUnavailable",
        noticeDetail: getYudaoCartErrorDetail(caught),
      });
    }
  }

  const localPreviewProduct =
    product.source === "yudao" && !product.cartId ? { ...product, source: "local-preview", cartId: undefined } : product;
  cartItems.value = addLocalCartItem(cartItems.value, localPreviewProduct, quantity);
  playCartFlyAnimation(options);
};

const updateCartQuantity = async (item, quantity) => {
  const nextQuantity = normalizeCartQuantity(quantity);
  cartItems.value = updateLocalCartItemQuantity(cartItems.value, item.skuId, nextQuantity);
  if (item.source === "yudao" && item.cartId) {
    try {
      await updateCartItemCount(item.cartId, nextQuantity);
      await loadRemoteCart();
      return;
    } catch (caught) {
      if (isYudaoAuthError(caught)) {
        switchToAuthRequiredCart();
        return;
      }
      switchToLocalCart({
        noticeKey: "cart.remoteMutationUnavailable",
        noticeDetail: getYudaoCartErrorDetail(caught),
      });
    }
  }
};

const removeFromCart = async (item) => {
  if (item.source === "yudao" && item.cartId) {
    try {
      await deleteCartItems([item.cartId]);
      await loadRemoteCart();
      return;
    } catch (caught) {
      if (isYudaoAuthError(caught)) {
        switchToAuthRequiredCart();
        return;
      }
      switchToLocalCart({
        noticeKey: "cart.remoteMutationUnavailable",
        noticeDetail: getYudaoCartErrorDetail(caught),
      });
      return;
    }
  }
  cartItems.value = removeLocalCartItem(cartItems.value, item.skuId);
};

const addToWishlist = async (item) => {
  const favoriteId = item?.spuId || item?.id;
  if (readYudaoToken() && favoriteId) {
    try {
      await createFavorite(item);
      cartWishlistNoticeKey.value = "cart.wishlistAdded";
      authVersion.value += 1;
      return;
    } catch (caught) {
      if (isYudaoAuthError(caught)) {
        switchToAuthRequiredCart();
        return;
      }
      if (isYudaoBusinessError(caught)) {
        cartWishlistNoticeKey.value = "cart.wishlistAlreadyAdded";
        return;
      }
    }
  }

  const result = addLocalWishlistItem(item);
  cartWishlistNoticeKey.value = result.added ? "cart.wishlistAdded" : "cart.wishlistAlreadyAdded";
  authVersion.value += 1;
};

const openOrderDetail = (orderId) => {
  currentPage.value = "account-orders";
  window.history.pushState({ page: "account-orders" }, "", `/account/orders?id=${orderId}`);
};

const handleOrderCreated = async (orderId) => {
  await loadRemoteCart();
  openOrderDetail(orderId);
};

const startCheckout = () => {
  const nextRoute = getCheckoutEntryRoute(cartItems.value);
  currentPage.value = pageFromPath(nextRoute.split("?")[0]);
  window.history.pushState({ page: currentPage.value }, "", nextRoute);
  cartOpen.value = false;
};

const continueCheckout = () => {
  currentPage.value = "checkout";
};

watch(currentPage, (page) => {
  applySeo(page);
  const nextPath = pageRoutes[page] || pageRoutes.missing;
  if (pageFromPath(window.location.pathname) === page) return;
  if (window.location.pathname !== nextPath) {
    window.history.pushState({ page }, "", `${nextPath}${window.location.search}${window.location.hash}`);
  }
});

watch(
  cartItems,
  (items) => {
    if (cartMode.value !== "yudao") writeLocalCart(items);
  },
  { deep: true }
);

onMounted(() => {
  applySeo(currentPage.value);
  window.addEventListener("popstate", syncPageFromLocation);
  document.addEventListener("click", handleInternalLinkClick);
  loadRemoteCart();
});

onBeforeUnmount(() => {
  window.removeEventListener("popstate", syncPageFromLocation);
  document.removeEventListener("click", handleInternalLinkClick);
});
</script>

<template>
  <RhHeader
    v-if="!usesCheckoutShell"
    v-model:page="currentPage"
    :cart-count="cartQuantity"
    :cart-mode="cartMode"
    :overlay="usesOverlayHeader"
    @auth-change="handleAuthChange"
    @open-cart="cartOpen = true"
  />
  <main class="app-main" :class="{ 'is-checkout-shell': usesCheckoutShell }">
    <component
      :key="`${currentPage}:${routeSignature}`"
      :is="pageComponent"
      :page-key="currentPage"
      :route-signature="routeSignature"
      :auth-version="authVersion"
      :items="cartItems"
      @add-to-cart="addToCart"
      @add-to-wishlist="addToWishlist"
      @continue-checkout="continueCheckout"
      @open-cart="cartOpen = true"
      @order-created="handleOrderCreated"
    />
  </main>
  <RhFooter v-if="!usesCheckoutShell" />
  <CartDrawer
    :items="cartItems"
    :debug-info="cartDebugInfo"
    :notice-detail="cartNoticeDetail"
    :notice-key="cartNoticeKey"
    :open="cartOpen"
    :wishlist-notice-key="cartWishlistNoticeKey"
    @checkout="startCheckout"
    @close="cartOpen = false"
    @remove="removeFromCart"
    @resync="loadRemoteCart"
    @update-quantity="updateCartQuantity"
    @wishlist="addToWishlist"
  />
</template>
