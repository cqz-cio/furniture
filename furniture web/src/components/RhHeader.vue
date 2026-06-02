<script setup>
import { computed, ref } from "vue";
import {
  babyChildNavigation,
  globalMenuPanels,
  livingMegaMenu,
  mobileDrawerNavigation,
  primaryNavigation,
  saleMegaMenu,
} from "../data/rhLayout.js";
import { useI18n } from "../i18n.js";
import AuthTokenPanel from "./AuthTokenPanel.vue";
import ImageSpecPlaceholder from "./ImageSpecPlaceholder.vue";

defineProps({
  overlay: {
    type: Boolean,
    default: false,
  },
  cartCount: {
    type: Number,
    default: 0,
  },
  cartMode: {
    type: String,
    default: "local",
  },
});

const emit = defineEmits(["open-cart"]);
const page = defineModel("page", { type: String, default: "home" });
const { availableLocales, currentLocale, setLocale, t } = useI18n();
const menuOpen = ref(false);
const activeDropdown = ref("");
const regionOpen = ref(false);
const accountOpen = ref(false);
const navItems = computed(() => (page.value === "baby-child" ? babyChildNavigation : primaryNavigation));
const hoverMenuItems = computed(() => (activeDropdown.value === "Sale" ? saleMegaMenu : livingMegaMenu));
const regionOptions = [
  { country: "BEL" },
  { country: "CAN", languages: ["FR", "EN"] },
  { country: "DEU" },
  { country: "ESP" },
  { country: "FRA", languages: ["FR", "EN"] },
  { country: "GBR" },
  { country: "ITA", languages: ["IT", "EN"] },
];

const pageKey = (label) => {
  if (label === "Living") return "sofas-plp";
  if (label === "Furniture") return "baby-child";
  if (label === "Baby & Child") return "baby-child";
  if (label === "Sale") return "sale";
  if (label === "Teen") return "teen";
  if (label === "Outdoor") return "outdoor";
  if (label === "RH") return "home";
  return "missing";
};

const localeButtonLabel = computed(() => (currentLocale.value === "zh-CN" ? "中文" : "EN"));

const isActive = (label) => {
  if (page.value === "baby-child") return label === "Furniture";
  return pageKey(label) === page.value;
};

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value;
  activeDropdown.value = "";
  regionOpen.value = false;
  accountOpen.value = false;
};

const closeMenu = () => {
  menuOpen.value = false;
};

const toggleRegion = () => {
  regionOpen.value = !regionOpen.value;
  activeDropdown.value = "";
  accountOpen.value = false;
  closeMenu();
};

const openAccount = () => {
  accountOpen.value = true;
  regionOpen.value = false;
  activeDropdown.value = "";
  closeMenu();
};

const closeAccount = () => {
  accountOpen.value = false;
};

const showDropdown = (label) => {
  if (!menuOpen.value && ["Living", "Sale"].includes(label)) activeDropdown.value = label;
};

const hideDropdown = () => {
  activeDropdown.value = "";
};

const activatePage = (label) => {
  page.value = pageKey(label);
  closeMenu();
  hideDropdown();
  regionOpen.value = false;
  accountOpen.value = false;
};
</script>

<template>
  <header
    class="rh-header"
    :class="{ 'is-overlay': overlay, 'menu-is-open': menuOpen, 'is-baby-child': page === 'baby-child' }"
    @mouseleave="hideDropdown"
  >
    <div class="header-topline">
      <div class="header-left" aria-label="Menu and search">
        <button
          class="icon-button menu-icon"
          :class="{ open: menuOpen }"
          type="button"
          :aria-label="menuOpen ? 'Close menu' : 'Open menu'"
          :aria-expanded="menuOpen"
          @click="toggleMenu"
        >
          <span></span>
          <span></span>
          <span></span>
        </button>
        <div class="header-search">
          <button class="icon-button search-icon" type="button" aria-label="Search"></button>
          <input class="search-input" aria-label="Search text" type="search" />
        </div>
      </div>

      <button
        class="brand-button"
        :class="{ 'baby-brand': page === 'baby-child' }"
        type="button"
        :aria-label="page === 'baby-child' ? 'RH Baby and Child' : 'The World of RH'"
        @click="page = page === 'baby-child' ? 'baby-child' : 'home'"
      >
        <template v-if="page === 'baby-child'">
          <span>baby &amp; child</span>
          <strong>RH</strong>
        </template>
        <template v-else>
          <span>The</span>
          <span>WORLD of</span>
          <strong>RH</strong>
        </template>
      </button>

      <div class="header-actions" aria-label="Account and cart links">
        <div class="region-switcher">
          <button
            class="country-button"
            type="button"
            :aria-expanded="regionOpen"
            aria-controls="region-menu"
            @click="toggleRegion"
          >
            {{ localeButtonLabel }} <span aria-hidden="true">⌃</span>
          </button>
          <section v-if="regionOpen" id="region-menu" class="region-menu" aria-label="Region selector">
            <div class="region-language-list" :aria-label="t('language')">
              <button
                v-for="locale in availableLocales"
                :key="locale.lang"
                class="region-option"
                :class="{ active: currentLocale === locale.lang }"
                type="button"
                @click="setLocale(locale.lang)"
              >
                {{ locale.label }}
              </button>
            </div>
            <div class="region-input-row">
              <input value="USA" aria-label="Selected country" />
              <span aria-hidden="true">⌃</span>
            </div>
            <button v-for="item in regionOptions" :key="item.country" class="region-option" type="button">
              <span>{{ item.country }}</span>
              <span v-if="item.languages" class="region-languages">
                <span v-for="language in item.languages" :key="language">{{ language }}</span>
              </span>
            </button>
          </section>
        </div>
        <button class="account-icon" type="button" aria-label="Account" @click="openAccount"></button>
        <button class="bag-icon" type="button" :aria-label="t('bag')" @click="emit('open-cart')">
          <span v-if="cartCount" class="bag-count">{{ cartCount }}</span>
        </button>
      </div>
    </div>

    <nav class="primary-nav" aria-label="Primary navigation">
      <div class="primary-nav-inner">
        <button
          v-for="item in navItems"
          :key="item.label"
          class="nav-link"
          :class="{ active: isActive(item.label) }"
          type="button"
          @mouseenter="showDropdown(item.label)"
          @focus="showDropdown(item.label)"
          @click="activatePage(item.label)"
        >
          {{ item.label }}
        </button>
      </div>
    </nav>

    <section
      v-if="['Living', 'Sale'].includes(activeDropdown)"
      class="category-mega-menu"
      :class="{ 'is-sale-menu': activeDropdown === 'Sale' }"
      :aria-label="`${activeDropdown} category menu`"
    >
      <ul>
        <li v-for="item in hoverMenuItems" :key="item.label">
          <a :class="{ accent: item.accent }" :href="item.href">{{ item.label }}</a>
        </li>
      </ul>
      <div class="category-mega-empty" aria-hidden="true"></div>
    </section>

    <section v-if="menuOpen" class="global-menu" aria-label="RH menu">
      <article v-for="panel in globalMenuPanels" :key="panel.heading" class="global-menu-panel">
        <ImageSpecPlaceholder
          class="global-menu-spec"
          :label="panel.spec.label"
          :rendered="panel.spec.rendered"
          :recommended2x="panel.spec.recommended2x"
          :file-size="panel.spec.fileSize"
          :fit="panel.spec.fit"
          :ratio="panel.spec.ratio"
          :natural="panel.spec.natural"
        />
        <p>Our</p>
        <h2>{{ panel.heading }}</h2>
        <template v-if="panel.groups">
          <div v-for="group in panel.groups" :key="group.heading" class="global-menu-group">
            <h3>{{ group.heading }}</h3>
            <a v-for="link in group.links" :key="link" href="#">{{ link }}</a>
          </div>
        </template>
        <template v-else>
          <a v-for="link in panel.links" :key="link" href="#">{{ link }}</a>
        </template>
      </article>
    </section>

    <div v-if="menuOpen" class="mobile-drawer-layer" aria-label="Mobile RH menu">
      <aside class="mobile-menu-drawer">
        <button
          v-for="item in mobileDrawerNavigation"
          :key="item.label"
          type="button"
          :class="{ accent: item.accent }"
          @click="activatePage(item.label)"
        >
          <span>{{ item.label }}</span>
          <span aria-hidden="true">›</span>
        </button>
        <button class="mobile-region" type="button">
          <span>United States ($) / English</span>
          <span aria-hidden="true">›</span>
        </button>
        <div class="mobile-drawer-brand">
          <span>The</span> WORLD <span>of</span> RH
        </div>
      </aside>
      <button class="mobile-drawer-scrim" type="button" aria-label="Close menu" @click="closeMenu"></button>
    </div>

    <div v-if="accountOpen" class="account-modal-layer" role="presentation">
      <section class="account-modal" role="dialog" aria-modal="true" aria-labelledby="account-modal-title">
        <button class="account-modal-close" type="button" aria-label="Close sign in" @click="closeAccount">
          <span></span>
          <span></span>
        </button>
        <h2 id="account-modal-title">SIGN IN</h2>
        <p>
          Please enter your email address to sign in, or
          <a href="#">Create an Account</a>
        </p>
        <form class="account-signin-form">
          <input type="email" placeholder="Email" aria-label="Email" autocomplete="email" />
          <a class="forgot-password" href="#">Forgot Password?</a>
          <button type="button">SIGN IN</button>
        </form>
        <AuthTokenPanel />
        <div class="account-modal-links">
          <a href="#">Sign In With a Secure Link</a>
          <a href="#">Trade Program Sign In</a>
        </div>
      </section>
    </div>
  </header>
</template>
