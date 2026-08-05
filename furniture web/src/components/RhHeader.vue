<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  babyChildNavigation,
  globalMenuPanels,
  globalMenuLinkHref,
  mobileDrawerNavigation,
  primaryNavigation,
  storefrontDropdownKeys,
  storefrontDropdownMenus,
} from "../data/rhLayout.js";
import { OAKVED_LOGO_SRC } from "../config/brand.js";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";
import AuthModal from "./AuthModal.vue";

const props = defineProps({
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
  navigationItems: {
    type: Array,
    default: () => [],
  },
  navigationPreview: {
    type: Boolean,
    default: false,
  },
  managedNavigation: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["open-cart", "auth-change"]);
const page = defineModel("page", { type: String, default: "home" });
const { availableLocales, currentLocale, setLocale, t } = useI18n();
const headerRef = ref(null);
const regionSwitcherRef = ref(null);
const navButtonRefs = ref({});
const menuOpen = ref(false);
const activeDropdown = ref("");
const activeMegaItem = ref("");
const categoryMenuLeft = ref("80px");
const regionOpen = ref(false);
const accountOpen = ref(false);
const searchOpen = ref(false);
const isBabyChildSitePage = computed(
  () => page.value === "baby-child" || page.value.startsWith("baby-child-"),
);
const babyChildPageMap = {
  Furniture: "baby-child-furniture",
  Bedding: "baby-child-bedding",
  Nursery: "baby-child-nursery",
  Decor: "baby-child-decor",
  Lighting: "baby-child-lighting",
  Rugs: "baby-child-rugs",
  Windows: "baby-child-windows",
  Storage: "baby-child-storage",
  Playroom: "baby-child-playroom",
  Gifts: "baby-child-gifts",
  Teen: "baby-child-teen",
  Sale: "baby-child-sale",
  Registry: "baby-child-registry",
};
const babyChildNavigationLabelKeys = {
  Furniture: "navigation.babyChild.furniture",
  Bedding: "navigation.babyChild.bedding",
  Nursery: "navigation.babyChild.nursery",
  Decor: "navigation.babyChild.decor",
  Lighting: "navigation.babyChild.lighting",
  Rugs: "navigation.babyChild.rugs",
  Windows: "navigation.babyChild.windows",
  Storage: "navigation.babyChild.storage",
  Playroom: "navigation.babyChild.playroom",
  Gifts: "navigation.babyChild.gifts",
  Teen: "navigation.babyChild.teen",
  Sale: "navigation.babyChild.sale",
  Registry: "navigation.babyChild.registry",
};
const navItems = computed(() => {
  if (isBabyChildSitePage.value) return babyChildNavigation;
  if (props.managedNavigation) return props.navigationItems;
  return primaryNavigation;
});
const hasStorefrontDropdown = (item) => {
  if (props.managedNavigation) return Boolean(item?.children?.length);
  return !isBabyChildSitePage.value && storefrontDropdownKeys.includes(item?.key);
};
const navItemLabel = (item) => item?.label || (item?.labelKey ? t(item.labelKey) : "");
const menuItemLabel = (item) => item?.label || (item?.labelKey ? t(item.labelKey) : "");
const babyChildItemLabel = (item) => {
  const labelKey = babyChildNavigationLabelKeys[item.label];
  return labelKey ? t(labelKey) : item.label;
};
const hoverMenuItems = computed(() => {
  if (props.managedNavigation) {
    return navItems.value.find((item) => item.key === activeDropdown.value)?.children || [];
  }
  return storefrontDropdownMenus[activeDropdown.value] || [];
});
const activeMegaChildren = computed(
  () => hoverMenuItems.value.find((item) => item.key === activeMegaItem.value)?.children || [],
);
const dropdownPositionStyle = computed(() => ({
  "--category-menu-left": categoryMenuLeft.value,
}));
const mobileDrawerSections = computed(() => {
  const navigationSection = {
    heading: props.managedNavigation
      ? "Navigation"
      : t("navigation.storefront.mobile.shopFurniture"),
    items: props.managedNavigation
      ? navItems.value.map((item) => ({ ...item, items: item.children || [] }))
      : mobileDrawerNavigation,
  };
  if (props.managedNavigation) return [navigationSection];
  return [
    navigationSection,
    {
      heading: t("navigation.storefront.mobile.service"),
      items: [
        {
          key: "membership-faq",
          labelKey: "navigation.storefront.mobile.membershipFaq",
          href: "/membership/faqs",
        },
        {
          key: "gift-registry",
          labelKey: "navigation.storefront.mobile.giftRegistry",
          href: "/gift-registry",
        },
        {
          key: "trade-program",
          labelKey: "navigation.storefront.mobile.tradeProgram",
          href: "/trade/sign-in",
        },
      ],
    },
  ];
});
const generatedGlobalMenuImages = [
  generatedFurnitureAssets.products.sofa.cover,
  generatedFurnitureAssets.home.modules["004"].desktop,
  generatedFurnitureAssets.home.modules["002"].desktop,
  generatedFurnitureAssets.products.pendant.gallery,
];

const pageKey = (label) => {
  if (isBabyChildSitePage.value && babyChildPageMap[label]) {
    return babyChildPageMap[label];
  }
  if (primaryNavigation.some((item) => item.label === label)) {
    return label === "New & Sale" ? "sale" : "sofas-plp";
  }
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
  if (isBabyChildSitePage.value && page.value === "baby-child") return label === "Furniture";
  if (isBabyChildSitePage.value && babyChildPageMap[label]) {
    return babyChildPageMap[label] === page.value;
  }
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

const setNavButtonRef = (key, element) => {
  if (element) {
    navButtonRefs.value[key] = element;
    return;
  }
  delete navButtonRefs.value[key];
};

const updateDropdownPosition = (key) => {
  const button = navButtonRefs.value[key];
  if (!button || typeof window === "undefined") return;

  const buttonRect = button.getBoundingClientRect();
  const headerRect = headerRef.value?.getBoundingClientRect() || { left: 0, width: window.innerWidth };
  const menuWidth = 516;
  const gutter = 24;
  const headerWidth = headerRect.width || window.innerWidth;
  const navCenter = buttonRect.left - headerRect.left + buttonRect.width / 2;
  const maxLeft = Math.max(gutter, headerWidth - menuWidth - gutter);
  const nextLeft = Math.min(Math.max(navCenter - menuWidth / 2, gutter), maxLeft);

  categoryMenuLeft.value = `${Math.round(nextLeft)}px`;
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

const handleNavClick = (item) => {
  if (isBabyChildSitePage.value) {
    activatePage(item.label);
    return;
  }

  if (!menuOpen.value && hasStorefrontDropdown(item)) {
    updateDropdownPosition(item.key);
    activeDropdown.value = activeDropdown.value === item.key ? "" : item.key;
    activeMegaItem.value = "";
    regionOpen.value = false;
    accountOpen.value = false;
    return;
  }

  openNavigationItem(item);
};

const openNavigationItem = (item) => {
  if (!item?.href) return;
  if (item.openMode === "_blank") {
    window.open(item.href, "_blank", "noopener,noreferrer");
    return;
  }
  window.location.assign(item.href);
};

const activateMegaItem = (item) => {
  activeMegaItem.value = item?.children?.length ? item.key : "";
};

const handleMegaItemClick = (event, item) => {
  if (!item?.href) {
    event.preventDefault();
    activateMegaItem(item);
    return;
  }
  hideDropdown();
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

const handleWindowResize = () => {
  if (activeDropdown.value) updateDropdownPosition(activeDropdown.value);
};

watch(menuOpen, setBodyMenuState);
watch(activeDropdown, () => {
  const firstWithChildren = hoverMenuItems.value.find((item) => item.children?.length);
  activeMegaItem.value = firstWithChildren?.key || "";
});

onMounted(() => {
  document.addEventListener("pointerdown", handleDocumentPointerDown);
  window.addEventListener("resize", handleWindowResize);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handleDocumentPointerDown);
  window.removeEventListener("resize", handleWindowResize);
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
      'is-baby-child': isBabyChildSitePage,
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
        :class="{ 'baby-brand': isBabyChildSitePage }"
        type="button"
        :aria-label="isBabyChildSitePage ? 'Oakved Baby and Child' : 'Oakved'"
        @click="page = isBabyChildSitePage ? 'baby-child' : 'home'"
      >
        <template v-if="isBabyChildSitePage">
          <img class="brand-logo" :src="OAKVED_LOGO_SRC" alt="Oakved" />
          <span class="brand-button-suffix">baby &amp; child</span>
        </template>
        <template v-else>
          <img class="brand-logo" :src="OAKVED_LOGO_SRC" alt="Oakved" />
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
        <button
          class="bag-icon"
          type="button"
          :aria-label="t('header.bag')"
          data-cart-animation-target
          @click="emit('open-cart')"
        >
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
          :key="item.key || item.label"
          :ref="(element) => setNavButtonRef(item.key || item.label, element)"
          class="nav-link"
          :class="{
            active: isBabyChildSitePage ? isActive(item.label) : activeDropdown === item.key,
            accent: item.accent || item.styleVariant === 'SALE',
          }"
          type="button"
          :aria-expanded="hasStorefrontDropdown(item) ? activeDropdown === item.key : undefined"
          @click="handleNavClick(item)"
        >
          {{ isBabyChildSitePage ? babyChildItemLabel(item) : navItemLabel(item) }}
        </button>
      </div>
    </nav>

    <section
      v-if="activeDropdown && hoverMenuItems.length"
      class="category-mega-menu"
      :style="dropdownPositionStyle"
      :aria-label="`${navItemLabel(navItems.find((item) => item.key === activeDropdown))} category menu`"
    >
      <ul>
        <li v-for="item in hoverMenuItems" :key="item.key">
          <a
            class="category-mega-link"
            :class="{ active: activeMegaItem === item.key }"
            :href="item.href || undefined"
            :target="item.openMode === '_blank' ? '_blank' : undefined"
            :rel="item.openMode === '_blank' ? 'noopener noreferrer' : undefined"
            @mouseenter="activateMegaItem(item)"
            @focus="activateMegaItem(item)"
            @click="handleMegaItemClick($event, item)"
          >
            {{ menuItemLabel(item) }}
          </a>
        </li>
      </ul>
      <ul v-if="activeMegaChildren.length" class="category-mega-secondary">
        <li v-for="child in activeMegaChildren" :key="child.key">
          <a
            class="category-mega-link"
            :href="child.href || undefined"
            :target="child.openMode === '_blank' ? '_blank' : undefined"
            :rel="child.openMode === '_blank' ? 'noopener noreferrer' : undefined"
            @click="handleMegaItemClick($event, child)"
          >
            {{ menuItemLabel(child) }}
          </a>
        </li>
      </ul>
      <div v-else class="category-mega-empty" aria-hidden="true"></div>
    </section>

    <section v-if="menuOpen" class="global-menu" aria-label="Oakved menu">
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

    <div v-if="menuOpen" class="mobile-drawer-layer" aria-label="Mobile Oakved menu">
      <aside class="mobile-menu-drawer">
        <section v-for="section in mobileDrawerSections" :key="section.heading" class="mobile-drawer-section">
          <h2>{{ section.heading }}</h2>
          <div v-for="item in section.items" :key="item.key || item.label" class="mobile-nav-group">
            <a
              :class="{ accent: item.accent || item.styleVariant === 'SALE' }"
              :href="item.href || undefined"
              :target="item.openMode === '_blank' ? '_blank' : undefined"
              :rel="item.openMode === '_blank' ? 'noopener noreferrer' : undefined"
              @click="closeMenu"
            >
              <span>{{ item.labelKey ? navItemLabel(item) : babyChildItemLabel(item) }}</span>
              <span aria-hidden="true">›</span>
            </a>
            <div v-if="item.items?.length" class="mobile-nav-children">
              <div v-for="child in item.items" :key="child.key" class="mobile-nav-child-group">
                <a
                  :href="child.href || undefined"
                  :target="child.openMode === '_blank' ? '_blank' : undefined"
                  :rel="child.openMode === '_blank' ? 'noopener noreferrer' : undefined"
                  @click="child.href ? closeMenu() : undefined"
                >
                  {{ menuItemLabel(child) }}
                </a>
                <div v-if="child.children?.length" class="mobile-nav-grandchildren">
                  <a
                    v-for="grandchild in child.children"
                    :key="grandchild.key"
                    :href="grandchild.href || undefined"
                    :target="grandchild.openMode === '_blank' ? '_blank' : undefined"
                    :rel="grandchild.openMode === '_blank' ? 'noopener noreferrer' : undefined"
                    @click="closeMenu"
                  >
                    {{ menuItemLabel(grandchild) }}
                  </a>
                </div>
              </div>
            </div>
          </div>
        </section>
        <button class="mobile-region" type="button">
          <span>{{ t("header.mobileRegion") }}</span>
          <span aria-hidden="true">›</span>
        </button>
        <div class="mobile-drawer-brand">
          <img class="mobile-drawer-brand-logo" :src="OAKVED_LOGO_SRC" alt="Oakved" />
        </div>
      </aside>
      <button class="mobile-drawer-scrim" type="button" :aria-label="t('header.menuClose')" @click="closeMenu"></button>
    </div>

    <AuthModal :open="accountOpen" @close="closeAccount" @auth-change="emit('auth-change', $event)" />
  </header>
</template>
