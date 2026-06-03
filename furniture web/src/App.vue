<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import CartDrawer from "./components/CartDrawer.vue";
import RhFooter from "./components/RhFooter.vue";
import RhHeader from "./components/RhHeader.vue";
import AccountMembershipPage from "./pages/AccountMembershipPage.vue";
import AccountPage from "./pages/AccountPage.vue";
import BabyChildPage from "./pages/BabyChildPage.vue";
import CheckoutAuthPage from "./pages/CheckoutAuthPage.vue";
import CheckoutPage from "./pages/CheckoutPage.vue";
import GiftRegistryCreatePage from "./pages/GiftRegistryCreatePage.vue";
import GiftRegistryFindPage from "./pages/GiftRegistryFindPage.vue";
import GiftRegistryManagePage from "./pages/GiftRegistryManagePage.vue";
import GiftRegistryPage from "./pages/GiftRegistryPage.vue";
import HomePage from "./pages/HomePage.vue";
import MembershipEnrollmentPage from "./pages/MembershipEnrollmentPage.vue";
import MembershipFaqPage from "./pages/MembershipFaqPage.vue";
import MembershipPage from "./pages/MembershipPage.vue";
import MembershipTermsPage from "./pages/MembershipTermsPage.vue";
import MissingExtractionPage from "./pages/MissingExtractionPage.vue";
import OrdersPage from "./pages/OrdersPage.vue";
import OutdoorPage from "./pages/OutdoorPage.vue";
import SalePage from "./pages/SalePage.vue";
import SofaPdpPage from "./pages/SofaPdpPage.vue";
import SofasPlpPage from "./pages/SofasPlpPage.vue";
import TeenPage from "./pages/TeenPage.vue";
import {
  addLocalCartItem,
  readLocalCart,
  removeLocalCartItem,
  updateLocalCartItemQuantity,
  writeLocalCart,
} from "./services/localCart.js";
import { getCheckoutEntryRoute } from "./services/membershipNavigation.js";
import { addCartItem, deleteCartItems, getRemoteCartItems, updateCartItemCount } from "./services/yudaoClient.js";

const pageRoutes = {
  home: "/",
  sale: "/sale",
  outdoor: "/outdoor",
  "sofas-plp": "/sofas-plp",
  "sofa-pdp": "/sofa-pdp",
  teen: "/teen",
  "baby-child": "/baby-child",
  membership: "/membership",
  "membership-enrollment": "/membership/enrollment",
  "membership-terms": "/membership/terms",
  "membership-faqs": "/membership/faqs",
  account: "/account",
  "account-membership": "/account/membership",
  "checkout-auth": "/checkout/auth",
  "gift-registry": "/gift-registry",
  "gift-registry-create": "/gift-registry/create",
  "gift-registry-find": "/gift-registry/find",
  "gift-registry-manage": "/gift-registry/manage",
  checkout: "/checkout",
  orders: "/orders",
  missing: "/missing",
};

const routePages = Object.fromEntries(Object.entries(pageRoutes).map(([page, path]) => [path, page]));

const pageFromPath = (path) => {
  const normalizedPath = path.replace(/\/$/, "") || "/";
  return routePages[normalizedPath] || (normalizedPath.startsWith("/gift-registry/") ? "gift-registry" : "missing");
};

const currentPage = ref(pageFromPath(window.location.pathname));
const cartOpen = ref(false);
const cartItems = ref(readLocalCart());
const cartMode = ref("local");

const pageComponent = computed(() => {
  if (currentPage.value === "home") return HomePage;
  if (currentPage.value === "sale") return SalePage;
  if (currentPage.value === "outdoor") return OutdoorPage;
  if (currentPage.value === "sofas-plp") return SofasPlpPage;
  if (currentPage.value === "sofa-pdp") return SofaPdpPage;
  if (currentPage.value === "teen") return TeenPage;
  if (currentPage.value === "baby-child") return BabyChildPage;
  if (currentPage.value === "membership") return MembershipPage;
  if (currentPage.value === "membership-enrollment") return MembershipEnrollmentPage;
  if (currentPage.value === "membership-terms") return MembershipTermsPage;
  if (currentPage.value === "membership-faqs") return MembershipFaqPage;
  if (currentPage.value === "account") return AccountPage;
  if (currentPage.value === "account-membership") return AccountMembershipPage;
  if (currentPage.value === "checkout-auth") return CheckoutAuthPage;
  if (currentPage.value === "gift-registry") return GiftRegistryPage;
  if (currentPage.value === "gift-registry-create") return GiftRegistryCreatePage;
  if (currentPage.value === "gift-registry-find") return GiftRegistryFindPage;
  if (currentPage.value === "gift-registry-manage") return GiftRegistryManagePage;
  if (currentPage.value === "checkout") return CheckoutPage;
  if (currentPage.value === "orders") return OrdersPage;
  return MissingExtractionPage;
});

const syncPageFromLocation = () => {
  currentPage.value = pageFromPath(window.location.pathname);
};

const cartQuantity = computed(() => cartItems.value.reduce((sum, item) => sum + item.quantity, 0));

const loadRemoteCart = async () => {
  try {
    cartItems.value = await getRemoteCartItems();
    cartMode.value = "yudao";
  } catch {
    cartMode.value = "local";
  }
};

const addToCart = async (product, quantity = 1) => {
  if (product.source === "yudao") {
    try {
      await addCartItem(product.skuId, quantity);
      await loadRemoteCart();
      cartOpen.value = true;
      return;
    } catch {
      cartMode.value = "local";
    }
  }

  cartItems.value = addLocalCartItem(cartItems.value, product, quantity);
  cartOpen.value = true;
};

const updateCartQuantity = async (item, quantity) => {
  if (item.source === "yudao" && item.cartId) {
    try {
      await updateCartItemCount(item.cartId, quantity);
      await loadRemoteCart();
      return;
    } catch {
      cartMode.value = "local";
    }
  }
  cartItems.value = updateLocalCartItemQuantity(cartItems.value, item.skuId, quantity);
};

const removeFromCart = async (item) => {
  if (item.source === "yudao" && item.cartId) {
    try {
      await deleteCartItems([item.cartId]);
      await loadRemoteCart();
      return;
    } catch {
      cartMode.value = "local";
    }
  }
  cartItems.value = removeLocalCartItem(cartItems.value, item.skuId);
};

const openOrderDetail = (orderId) => {
  currentPage.value = "orders";
  window.history.pushState({ page: "orders" }, "", `/orders?id=${orderId}`);
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
  const nextPath = pageRoutes[page] || pageRoutes.missing;
  if (window.location.pathname !== nextPath) {
    window.history.pushState({ page }, "", nextPath);
  }
});

watch(cartItems, (items) => writeLocalCart(items), { deep: true });

onMounted(() => {
  window.addEventListener("popstate", syncPageFromLocation);
  loadRemoteCart();
});

onBeforeUnmount(() => {
  window.removeEventListener("popstate", syncPageFromLocation);
});
</script>

<template>
  <RhHeader v-model:page="currentPage" :cart-count="cartQuantity" :cart-mode="cartMode" @open-cart="cartOpen = true" />
  <main class="app-main">
    <component
      :is="pageComponent"
      :items="cartItems"
      @add-to-cart="addToCart"
      @continue-checkout="continueCheckout"
      @order-created="openOrderDetail"
    />
  </main>
  <RhFooter />
  <CartDrawer
    :items="cartItems"
    :open="cartOpen"
    @checkout="startCheckout"
    @close="cartOpen = false"
    @remove="removeFromCart"
    @update-quantity="updateCartQuantity"
  />
</template>
