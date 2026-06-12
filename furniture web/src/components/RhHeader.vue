<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  babyChildNavigation,
  globalMenuPanels,
  globalMenuLinkHref,
  livingMegaMenu,
  livingMegaSubmenus,
  mobileDrawerNavigation,
  primaryNavigation,
  saleMegaMenu,
} from "../data/rhLayout.js";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";
import AuthModal from "./AuthModal.vue";

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

const emit = defineEmits(["open-cart", "auth-change"]);
const page = defineModel("page", { type: String, default: "home" });
const { availableLocales, currentLocale, setLocale, t } = useI18n();
const headerRef = ref(null);
const regionSwitcherRef = ref(null);
const menuOpen = ref(false);
const activeDropdown = ref("");
const activeMegaItem = ref("");
const regionOpen = ref(false);
const accountOpen = ref(false);
const searchOpen = ref(false);
const navItems = computed(() => (page.value === "baby-child" ? babyChildNavigation : primaryNavigation));
const hoverMenuItems = computed(() => (activeDropdown.value === "Sale" ? saleMegaMenu : livingMegaMenu));
const hoverSecondaryMenuItems = computed(() =>
  activeDropdown.value === "Living" && activeMegaItem.value ? livingMegaSubmenus[activeMegaItem.value] || [] : [],
);
const generatedGlobalMenuImages = [
  generatedFurnitureAssets.products.sofa.cover,
  generatedFurnitureAssets.home.modules["004"].desktop,
  generatedFurnitureAssets.home.modules["002"].desktop,
  generatedFurnitureAssets.products.pendant.gallery,
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

const localeButtonLabel = computed(
  () => availableLocales.find((item) => item.lang === currentLocale.value)?.shortLabel || "EN",
);

const isActive = (label) => {
  if (page.value === "baby-child") return label === "Furniture";
  return pageKey(label) === page.value;
};

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value;
  activeDropdown.value = "";
  activeMegaItem.value = "";
  regionOpen.value = false;
  accountOpen.value = false;
  searchOpen.value = false;
};

const closeMenu = () => {
  menuOpen.value = false;
};

const toggleSearch = () => {
  searchOpen.value = !searchOpen.value;
  menuOpen.value = false;
  activeDropdown.value = "";
  activeMegaItem.value = "";
  regionOpen.value = false;
  accountOpen.value = false;
};

const toggleRegion = () => {
  regionOpen.value = !regionOpen.value;
  activeDropdown.value = "";
  accountOpen.value = false;
  searchOpen.value = false;
  closeMenu();
};

const selectLocale = (lang) => {
  setLocale(lang);
  regionOpen.value = false;
};

const openAccount = () => {
  accountOpen.value = true;
  regionOpen.value = false;
  activeDropdown.value = "";
  searchOpen.value = false;
  closeMenu();
};

const closeAccount = () => {
  accountOpen.value = false;
};

const hideDropdown = () => {
  activeDropdown.value = "";
  activeMegaItem.value = "";
};

const setBodyMenuState = (isOpen) => {
  if (typeof document === "undefined") return;
  document.body.classList.toggle("rh-menu-open", isOpen);
};

const activatePage = (label) => {
  page.value = pageKey(label);
  closeMenu();
  hideDropdown();
  regionOpen.value = false;
  accountOpen.value = false;
  searchOpen.value = false;
};

const handleNavClick = (label) => {
  if (!menuOpen.value && ["Living", "Sale"].includes(label)) {
    activeDropdown.value = activeDropdown.value === label ? "" : label;
    activeMegaItem.value = "";
    regionOpen.value = false;
    accountOpen.value = false;
    return;
  }

  activatePage(label);
};

const activateMegaItem = (label) => {
  activeMegaItem.value = label;
};

const generatedGlobalMenuImage = (index) => generatedGlobalMenuImages[index % generatedGlobalMenuImages.length];

const handleDocumentPointerDown = (event) => {
  if (regionOpen.value && !regionSwitcherRef.value?.contains(event.target)) {
    regionOpen.value = false;
  }

  if (activeDropdown.value && !headerRef.value?.contains(event.target)) {
    hideDropdown();
  }
};

watch(menuOpen, setBodyMenuState);

onMounted(() => {
  document.addEventListener("pointerdown", handleDocumentPointerDown);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handleDocumentPointerDown);
  setBodyMenuState(false);
});
</script>

<template>
  <header
    ref="headerRef"
    class="rh-header"
    :class="{
      'is-overlay': overlay,
      'menu-is-open': menuOpen,
      'region-is-open': regionOpen,
      'is-baby-child': page === 'baby-child',
    }"
  >
    <div class="header-topline">
      <div class="header-left" :aria-label="`${t('header.menuOpen')} / ${t('common.search')}`">
        <button
          class="icon-button menu-icon"
          :class="{ open: menuOpen }"
          type="button"
          :aria-label="menuOpen ? t('header.menuClose') : t('header.menuOpen')"
          :aria-expanded="menuOpen"
          @click="toggleMenu"
        >
          <span></span>
          <span></span>
          <span></span>
        </button>
        <div class="header-search">
          <button
            class="icon-button search-icon"
            type="button"
            :aria-label="t('common.search')"
            :aria-expanded="searchOpen"
            aria-controls="mobile-search-panel"
            @click="toggleSearch"
          ></button>
          <input class="search-input" :aria-label="t('common.search')" type="search" />
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

      <div class="header-actions" :aria-label="`${t('header.account')} / ${t('header.bag')}`">
        <div ref="regionSwitcherRef" class="region-switcher">
          <button
            class="country-button"
            type="button"
            :aria-expanded="regionOpen"
            aria-controls="region-menu"
            @click="toggleRegion"
          >
            {{ localeButtonLabel }} <span aria-hidden="true">⌃</span>
          </button>
          <section v-if="regionOpen" id="region-menu" class="region-menu" :aria-label="t('common.language')">
            <div class="region-language-list" :aria-label="t('common.language')">
              <button
                v-for="locale in availableLocales"
                :key="locale.lang"
                class="region-option"
                :class="{ active: currentLocale === locale.lang }"
                type="button"
                @click="selectLocale(locale.lang)"
              >
                {{ locale.label }}
              </button>
            </div>
          </section>
        </div>
        <button class="account-icon" type="button" :aria-label="t('header.account')" @click="openAccount"></button>
        <button class="bag-icon" type="button" :aria-label="t('header.bag')" @click="emit('open-cart')">
          <span v-if="cartCount" class="bag-count">{{ cartCount }}</span>
        </button>
      </div>
    </div>

    <div v-if="searchOpen" id="mobile-search-panel" class="mobile-search-panel" role="search">
      <input :aria-label="t('common.search')" type="search" />
    </div>

    <nav class="primary-nav" aria-label="Primary navigation">
      <div class="primary-nav-inner">
        <button
          v-for="item in navItems"
          :key="item.label"
          class="nav-link"
          :class="{ active: isActive(item.label) }"
          type="button"
          :aria-expanded="['Living', 'Sale'].includes(item.label) ? activeDropdown === item.label : undefined"
          @click="handleNavClick(item.label)"
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
          <button
            v-if="activeDropdown === 'Living'"
            class="category-mega-link"
            :class="{ accent: item.accent, active: activeMegaItem === item.label }"
            type="button"
            @click="activateMegaItem(item.label)"
          >
            {{ item.label }}
          </button>
          <a v-else :class="{ accent: item.accent }" :href="item.href" @click="hideDropdown">{{ item.label }}</a>
        </li>
      </ul>
      <ul v-if="hoverSecondaryMenuItems.length" class="category-mega-secondary">
        <li v-for="item in hoverSecondaryMenuItems" :key="item.label">
          <a :href="item.href" @click="hideDropdown">{{ item.label }}</a>
        </li>
      </ul>
      <div v-else class="category-mega-empty" aria-hidden="true"></div>
    </section>

    <section v-if="menuOpen" class="global-menu" aria-label="RH menu">
      <article v-for="(panel, index) in globalMenuPanels" :key="panel.heading" class="global-menu-panel">
        <img
          class="global-menu-image"
          :src="generatedGlobalMenuImage(index)"
          :alt="`${panel.heading} menu collection`"
        />
        <p>Our</p>
        <h2>{{ panel.heading }}</h2>
        <template v-if="panel.groups">
          <div v-for="group in panel.groups" :key="group.heading" class="global-menu-group">
            <h3>{{ group.heading }}</h3>
            <a v-for="link in group.links" :key="link" :href="globalMenuLinkHref(link)" @click="closeMenu">{{ link }}</a>
          </div>
        </template>
        <template v-else>
          <a v-for="link in panel.links" :key="link" :href="globalMenuLinkHref(link)" @click="closeMenu">{{ link }}</a>
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
          <span>{{ t("header.mobileRegion") }}</span>
          <span aria-hidden="true">›</span>
        </button>
        <div class="mobile-drawer-brand">
          <span>The</span> WORLD <span>of</span> RH
        </div>
      </aside>
      <button class="mobile-drawer-scrim" type="button" :aria-label="t('header.menuClose')" @click="closeMenu"></button>
    </div>

    <AuthModal :open="accountOpen" @close="closeAccount" @auth-change="emit('auth-change', $event)" />
  </header>
</template>
