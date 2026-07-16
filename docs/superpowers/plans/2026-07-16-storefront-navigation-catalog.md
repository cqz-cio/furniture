# Storefront Navigation and Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current storefront navigation with the approved English furniture hierarchy and add one shared `/catalog` brand lookbook page that every `OAKVED catalog` entry opens.

**Architecture:** Define the navigation once with stable keys and translation keys in `rhLayout.js`, derive every dropdown's first catalog item through a shared helper, and let desktop and mobile header views consume that same model. Add `/catalog` to the existing lightweight route map and render an image-led `CatalogPage.vue` using the existing sourcebook artwork; new Chinese and French keys intentionally fall back to English until the later translation pass.

**Tech Stack:** Vue 3 Composition API, Vite 8, Vitest 4, existing project i18n helper, existing generated WebP assets.

## Global Constraints

- Primary order is exactly `NEW`, `SHOP BY COLLECTIONS`, `BEDROOM`, `LIVING`, `DINING`, `BESPOKE`, `DECOR`, `SALE`.
- Only `SHOP BY COLLECTIONS`, `BEDROOM`, `LIVING`, and `DINING` have dropdowns in this delivery.
- Every configured dropdown begins with `OAKVED catalog` and that item always uses `/catalog`.
- `DINING` must include the previously omitted `OAKVED catalog` entry.
- Create one catalog page only; do not create room-specific catalog routes or query variants.
- English is the only new locale copy in this delivery; Chinese and French use the existing English fallback until their reviewed translation pass.
- Preserve account, language, search, cart, global hamburger menu, Baby & Child navigation, and unrelated user changes.
- Desktop and mobile navigation must consume the same data model.

---

## File Structure

- `furniture web/src/data/rhLayout.js`: owns primary navigation, dropdown membership, routes, stable keys, and the catalog-first invariant.
- `furniture web/src/components/RhHeader.vue`: renders the shared model for desktop dropdowns and the mobile drawer.
- `furniture web/src/i18n.js`: contains English labels for the new primary and submenu keys, with existing fallback behavior supplying English to untranslated locales.
- `furniture web/src/pages/CatalogPage.vue`: owns the shared brand catalog page presentation.
- `furniture web/src/App.vue`: registers `/catalog`, lazy-loads the page, chooses the component, and supplies catalog SEO.
- `furniture web/src/styles.css`: styles the wider English navigation, dropdown, mobile nesting, and catalog page responsively.
- `furniture web/tests/rhLayout.test.js`: verifies structure, order, dropdown contents, and shared catalog route.
- `furniture web/tests/headerLanguageMenu.test.js`: verifies header consumption and desktop/mobile behavior contracts.
- `furniture web/tests/i18n.test.js`: verifies all new English keys resolve and untranslated locales fall back to English.
- `furniture web/tests/catalogPage.test.js`: verifies the shared route, page wiring, copy keys, and existing sourcebook asset use.

---

### Task 1: Centralize the approved navigation model

**Files:**
- Modify: `furniture web/src/data/rhLayout.js`
- Modify: `furniture web/tests/rhLayout.test.js`

**Interfaces:**
- Produces: `CATALOG_HREF: string`, `primaryNavigation: NavigationItem[]`, `storefrontDropdownMenus: Record<string, NavigationItem[]>`, `storefrontDropdownKeys: string[]`, and `mobileDrawerNavigation: NavigationItem[]`.
- `NavigationItem` shape: `{ key: string, labelKey: string, href: string, accent?: boolean, items?: NavigationItem[] }`.
- Consumers: `RhHeader.vue` in Task 2 and the `/catalog` route in Task 3.

- [ ] **Step 1: Replace the existing navigation assertions with failing contract tests**

In `tests/rhLayout.test.js`, import `CATALOG_HREF`, `storefrontDropdownKeys`, and `storefrontDropdownMenus`, then replace the focused wood-navigation tests with:

```js
it("defines the approved storefront navigation order", () => {
  expect(primaryNavigation.map((item) => item.key)).toEqual([
    "new",
    "collections",
    "bedroom",
    "living",
    "dining",
    "bespoke",
    "decor",
    "sale",
  ]);
  expect(primaryNavigation.map((item) => item.labelKey)).toEqual([
    "navigation.storefront.primary.new",
    "navigation.storefront.primary.collections",
    "navigation.storefront.primary.bedroom",
    "navigation.storefront.primary.living",
    "navigation.storefront.primary.dining",
    "navigation.storefront.primary.bespoke",
    "navigation.storefront.primary.decor",
    "navigation.storefront.primary.sale",
  ]);
});

it("prepends the same catalog destination to every configured dropdown", () => {
  expect(storefrontDropdownKeys).toEqual(["collections", "bedroom", "living", "dining"]);

  storefrontDropdownKeys.forEach((key) => {
    expect(storefrontDropdownMenus[key][0]).toEqual({
      key: "catalog",
      labelKey: "navigation.storefront.submenu.catalog",
      href: CATALOG_HREF,
    });
  });

  expect(CATALOG_HREF).toBe("/catalog");
  expect(storefrontDropdownMenus.dining.map((item) => item.key)).toEqual([
    "catalog",
    "rectangularTables",
    "roundOvalTables",
    "bistroTables",
    "fabricChairs",
    "woodWovenChairs",
    "barCounterStools",
    "upholsterySwatches",
    "sales",
  ]);
});

it("derives mobile navigation from the same dropdown model", () => {
  expect(mobileDrawerNavigation.map((item) => item.key)).toEqual(primaryNavigation.map((item) => item.key));
  expect(mobileDrawerNavigation.find((item) => item.key === "dining").items)
    .toBe(storefrontDropdownMenus.dining);
  expect(mobileDrawerNavigation.find((item) => item.key === "sale").accent).toBe(true);
});
```

- [ ] **Step 2: Run the focused test and verify the new exports are missing**

Run:

```powershell
npm.cmd test -- tests/rhLayout.test.js
```

Expected: FAIL because `CATALOG_HREF`, `storefrontDropdownKeys`, and `storefrontDropdownMenus` are not exported and the current primary order differs.

- [ ] **Step 3: Implement the central navigation model**

Replace the current storefront-only `primaryNavigation`, `woodFurnitureMegaMenus`, `woodFurnitureDropdownLabels`, `livingMegaMenu`, `livingSeatingMegaMenu`, `livingMegaSubmenus`, `saleMegaMenu`, and `mobileDrawerNavigation` definitions in `src/data/rhLayout.js` with this model while leaving Baby & Child and unrelated exports intact:

```js
export const CATALOG_HREF = "/catalog";

const item = (key, href) => ({
  key,
  labelKey: `navigation.storefront.submenu.${key}`,
  href,
});

const catalogItem = () => item("catalog", CATALOG_HREF);
const withCatalog = (items) => [catalogItem(), ...items];

export const primaryNavigation = [
  { key: "new", labelKey: "navigation.storefront.primary.new", href: "/products?tag=new" },
  { key: "collections", labelKey: "navigation.storefront.primary.collections", href: "/products?collection=all" },
  { key: "bedroom", labelKey: "navigation.storefront.primary.bedroom", href: "/products?room=bedroom" },
  { key: "living", labelKey: "navigation.storefront.primary.living", href: "/products?room=living" },
  { key: "dining", labelKey: "navigation.storefront.primary.dining", href: "/products?room=dining" },
  { key: "bespoke", labelKey: "navigation.storefront.primary.bespoke", href: "/products?collection=bespoke" },
  { key: "decor", labelKey: "navigation.storefront.primary.decor", href: "/products?category=decor" },
  { key: "sale", labelKey: "navigation.storefront.primary.sale", href: "/sale", accent: true },
];

export const storefrontDropdownMenus = {
  collections: withCatalog([
    item("solstice", "/products?collection=solstice"),
    item("halcyon", "/products?collection=halcyon"),
    item("kindred", "/products?collection=kindred"),
  ]),
  bedroom: withCatalog([
    item("beds", "/products?category=bed"),
    item("headboard", "/products?category=headboard"),
    item("nightstands", "/products?category=nightstand"),
    item("benches", "/products?category=bench"),
    item("dressers", "/products?category=dresser"),
    item("chairs", "/products?category=chair"),
    item("sideTables", "/products?category=side-table"),
    item("fabricCare", "/products?group=fabric-care"),
    item("materialsCraftsmanship", "/products?group=materials-craftsmanship"),
    item("sales", "/sale"),
  ]),
  living: withCatalog([
    item("sofas", "/products?category=sofa"),
    item("tables", "/products?category=table"),
    item("consoles", "/products?category=console"),
    item("sideboards", "/products?category=sideboard"),
    item("cabinets", "/products?category=cabinet"),
    item("benches", "/products?category=bench"),
    item("chairs", "/products?category=chair"),
    item("stools", "/products?category=stool"),
    item("fabricCare", "/products?group=fabric-care"),
    item("materialsCraftsmanship", "/products?group=materials-craftsmanship"),
    item("sales", "/sale"),
  ]),
  dining: withCatalog([
    item("rectangularTables", "/products?category=rectangular-table"),
    item("roundOvalTables", "/products?category=round-oval-table"),
    item("bistroTables", "/products?category=bistro-table"),
    item("fabricChairs", "/products?category=fabric-chair"),
    item("woodWovenChairs", "/products?category=wood-woven-chair"),
    item("barCounterStools", "/products?category=bar-counter-stool"),
    item("upholsterySwatches", "/products?group=upholstery-swatches"),
    item("sales", "/sale"),
  ]),
};

export const storefrontDropdownKeys = Object.keys(storefrontDropdownMenus);
export const mobileDrawerNavigation = primaryNavigation.map((navigationItem) => ({
  ...navigationItem,
  items: storefrontDropdownMenus[navigationItem.key] || [],
}));
```

- [ ] **Step 4: Run the focused data tests**

Run:

```powershell
npm.cmd test -- tests/rhLayout.test.js
```

Expected: PASS with the new navigation tests and all unrelated layout tests still green.

- [ ] **Step 5: Commit the navigation model**

```powershell
git add -- "furniture web/src/data/rhLayout.js" "furniture web/tests/rhLayout.test.js"
git commit -m "feat: define storefront navigation model"
```

---

### Task 2: Render the English navigation on desktop and mobile

**Files:**
- Modify: `furniture web/src/components/RhHeader.vue`
- Modify: `furniture web/src/i18n.js`
- Modify: `furniture web/src/styles.css`
- Modify: `furniture web/tests/headerLanguageMenu.test.js`
- Modify: `furniture web/tests/i18n.test.js`

**Interfaces:**
- Consumes: `primaryNavigation`, `storefrontDropdownKeys`, `storefrontDropdownMenus`, and `mobileDrawerNavigation` from Task 1.
- Produces: translated desktop labels, key-based dropdown toggling, and a nested mobile drawer using the identical submenu arrays.

- [ ] **Step 1: Add failing English-label and header-consumption tests**

Add this test to `tests/i18n.test.js` inside the existing i18n suite:

```js
it("resolves every English storefront navigation label and falls back for untranslated locales", async () => {
  const { getMessage, setLocale, t } = await import("../src/i18n.js");
  const keys = [
    "navigation.storefront.primary.new",
    "navigation.storefront.primary.collections",
    "navigation.storefront.primary.bedroom",
    "navigation.storefront.primary.living",
    "navigation.storefront.primary.dining",
    "navigation.storefront.primary.bespoke",
    "navigation.storefront.primary.decor",
    "navigation.storefront.primary.sale",
    "navigation.storefront.submenu.catalog",
    "navigation.storefront.submenu.rectangularTables",
    "navigation.storefront.submenu.upholsterySwatches",
  ];

  keys.forEach((key) => expect(getMessage(key, "en")).toBeTruthy());
  setLocale("zh-CN");
  expect(t("navigation.storefront.primary.collections")).toBe("SHOP BY COLLECTIONS");
  setLocale("fr");
  expect(t("navigation.storefront.submenu.catalog")).toBe("OAKVED catalog");
  setLocale("en");
});
```

Replace the focused dropdown source assertions in `tests/headerLanguageMenu.test.js` with:

```js
it("renders the shared storefront model for desktop and mobile", () => {
  const source = readSource("../src/components/RhHeader.vue");

  expect(source).toContain("storefrontDropdownKeys");
  expect(source).toContain("storefrontDropdownMenus[activeDropdown.value]");
  expect(source).toContain('const navItemLabel = (item) => t(item.labelKey)');
  expect(source).toContain('const menuItemLabel = (item) => t(item.labelKey)');
  expect(source).toContain('@click="handleNavClick(item)"');
  expect(source).toContain('v-for="child in item.items"');
  expect(source).toContain(':href="child.href"');
});
```

- [ ] **Step 2: Run the two focused test files and verify failure**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js tests/headerLanguageMenu.test.js
```

Expected: FAIL because the new translation keys and new shared-model imports/rendering do not exist.

- [ ] **Step 3: Add the English translation tree**

Inside `navigationMessages.en.navigation`, add:

```js
storefront: {
  primary: {
    new: "NEW",
    collections: "SHOP BY COLLECTIONS",
    bedroom: "BEDROOM",
    living: "LIVING",
    dining: "DINING",
    bespoke: "BESPOKE",
    decor: "DECOR",
    sale: "SALE",
  },
  submenu: {
    catalog: "OAKVED catalog",
    solstice: "The Solstice",
    halcyon: "Halcyon",
    kindred: "Kindred",
    beds: "Beds",
    headboard: "Headboard",
    nightstands: "Nightstands",
    benches: "Benches",
    dressers: "Dressers",
    chairs: "Chairs",
    sideTables: "Side Tables",
    fabricCare: "Fabric Care",
    materialsCraftsmanship: "Materials & Craftsmanship",
    sales: "Sales",
    sofas: "Sofas",
    tables: "Tables",
    consoles: "Consoles",
    sideboards: "Sideboards",
    cabinets: "Cabinets",
    stools: "Stools",
    rectangularTables: "Rectangular Tables",
    roundOvalTables: "Round & Oval Tables",
    bistroTables: "Bistro Tables",
    fabricChairs: "Fabric Chairs",
    woodWovenChairs: "Wood & Woven Chairs",
    barCounterStools: "Bar & Counter Stools",
    upholsterySwatches: "Upholstery Swatches",
  },
},
```

Do not add this tree to `zh-CN` or `fr` in the English pass; `t()` already falls back to `DEFAULT_LOCALE` when `getMessage()` returns `undefined`.

- [ ] **Step 4: Refactor `RhHeader.vue` to stable keys**

Change the imports to consume the new exports:

```js
import {
  babyChildNavigation,
  globalMenuPanels,
  globalMenuLinkHref,
  mobileDrawerNavigation,
  primaryNavigation,
  storefrontDropdownKeys,
  storefrontDropdownMenus,
} from "../data/rhLayout.js";
```

For the main storefront path, replace label-based lookup code with:

```js
const navItems = computed(() => (isBabyChildSitePage.value ? babyChildNavigation : primaryNavigation));
const hasStorefrontDropdown = (item) => !isBabyChildSitePage.value && storefrontDropdownKeys.includes(item.key);
const navItemLabel = (item) => t(item.labelKey);
const menuItemLabel = (item) => t(item.labelKey);
const babyChildItemLabel = (item) => {
  const labelKey = babyChildNavigationLabelKeys[item.label];
  return labelKey ? t(labelKey) : item.label;
};
const hoverMenuItems = computed(() => storefrontDropdownMenus[activeDropdown.value] || []);

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

const handleNavClick = (item) => {
  if (isBabyChildSitePage.value) {
    activatePage(item.label);
    return;
  }

  if (hasStorefrontDropdown(item)) {
    updateDropdownPosition(item.key);
    activeDropdown.value = activeDropdown.value === item.key ? "" : item.key;
    activeMegaItem.value = "";
    regionOpen.value = false;
    accountOpen.value = false;
    return;
  }

  window.location.assign(item.href);
};
```

Keep the separate Baby & Child label and page handling branch currently used when `isBabyChildSitePage` is true.

Update the desktop loop and dropdown to use keys and items:

```vue
<button
  v-for="item in navItems"
  :key="item.key || item.label"
  :ref="(element) => setNavButtonRef(item.key || item.label, element)"
  class="nav-link"
  :class="{ active: activeDropdown === item.key, accent: item.accent }"
  type="button"
  :aria-expanded="hasStorefrontDropdown(item) ? activeDropdown === item.key : undefined"
  @click="handleNavClick(item)"
>
  {{ isBabyChildSitePage ? babyChildItemLabel(item) : navItemLabel(item) }}
</button>

<section
  v-if="storefrontDropdownKeys.includes(activeDropdown)"
  class="category-mega-menu"
  :style="dropdownPositionStyle"
  :aria-label="`${navItemLabel(primaryNavigation.find((item) => item.key === activeDropdown))} category menu`"
>
  <ul>
    <li v-for="item in hoverMenuItems" :key="item.key">
      <a class="category-mega-link" :href="item.href" @click="hideDropdown">
        {{ menuItemLabel(item) }}
      </a>
    </li>
  </ul>
</section>
```

For Baby & Child and service items that still have `{ label, href }`, keep the `babyChildItemLabel(item)` fallback shown above so existing translated Baby & Child labels resolve and service labels remain unchanged.

Render nested mobile links from the derived `items` arrays:

```vue
<div v-for="item in section.items" :key="item.key || item.label" class="mobile-nav-group">
  <a :class="{ accent: item.accent }" :href="item.href" @click="closeMenu">
    <span>{{ item.labelKey ? navItemLabel(item) : babyChildItemLabel(item) }}</span>
    <span aria-hidden="true">›</span>
  </a>
  <div v-if="item.items?.length" class="mobile-nav-children">
    <a v-for="child in item.items" :key="child.key" :href="child.href" @click="closeMenu">
      {{ menuItemLabel(child) }}
    </a>
  </div>
</div>
```

- [ ] **Step 5: Adjust navigation styles for eight English items and mobile children**

In `src/styles.css`, keep the current header height and overlay behavior, then add or update these focused rules:

```css
.primary-nav-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(18px, 2.1vw, 34px);
  min-width: max-content;
  padding: 0 32px;
}

.nav-link {
  white-space: nowrap;
  letter-spacing: 0.08em;
}

.mobile-nav-group > a {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mobile-nav-children {
  display: grid;
  gap: 10px;
  padding: 4px 0 14px 18px;
}

.mobile-nav-children a {
  min-height: 36px;
  font-size: 12px;
  letter-spacing: 0.06em;
}
```

- [ ] **Step 6: Run focused tests and the header-related regression tests**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js tests/headerLanguageMenu.test.js tests/mobilePurchasePolish.test.js tests/storefrontLaunchPolish.test.js
```

Expected: PASS. If an old source-string assertion names the removed wood-furniture exports, update only that assertion to the new stable-key contract and rerun the same command.

- [ ] **Step 7: Commit the English header implementation**

```powershell
git add -- "furniture web/src/components/RhHeader.vue" "furniture web/src/i18n.js" "furniture web/src/styles.css" "furniture web/tests/headerLanguageMenu.test.js" "furniture web/tests/i18n.test.js"
git commit -m "feat: render English storefront navigation"
```

---

### Task 3: Add the one shared brand catalog page and verify the English delivery

**Files:**
- Create: `furniture web/src/pages/CatalogPage.vue`
- Create: `furniture web/tests/catalogPage.test.js`
- Modify: `furniture web/src/App.vue`
- Modify: `furniture web/src/i18n.js`
- Modify: `furniture web/src/styles.css`

**Interfaces:**
- Consumes: `CATALOG_HREF === "/catalog"` from Task 1 and `generatedFurnitureAssets.home.modules["005"]`.
- Produces: the `catalog` page key, `/catalog` route, catalog SEO, and responsive image-led page.

- [ ] **Step 1: Write the failing catalog route and page test**

Create `tests/catalogPage.test.js`:

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("shared Oakved catalog page", () => {
  it("registers one shared catalog route and component", () => {
    const app = readSource("../src/App.vue");

    expect(app).toContain('const CatalogPage = defineAsyncComponent(() => import("./pages/CatalogPage.vue"))');
    expect(app).toContain('catalog: "/catalog"');
    expect(app).toContain('if (currentPage.value === "catalog") return CatalogPage');
    expect(app).toContain('title: "Oakved Catalog | Oakved"');
  });

  it("uses the existing sourcebook artwork and translated English copy", () => {
    const page = readSource("../src/pages/CatalogPage.vue");

    expect(page).toContain('generatedFurnitureAssets.home.modules["005"]');
    expect(page).toContain('t("catalogPage.eyebrow")');
    expect(page).toContain('t("catalogPage.title")');
    expect(page).toContain('t("catalogPage.introduction")');
    expect(page).toContain('<picture class="catalog-hero-picture">');
  });
});
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```powershell
npm.cmd test -- tests/catalogPage.test.js
```

Expected: FAIL because `CatalogPage.vue` and the `catalog` route do not exist.

- [ ] **Step 3: Add English catalog copy**

Add this English-only tree to the English locale data in `src/i18n.js`:

```js
catalogPage: {
  eyebrow: "THE OAKVED CATALOG",
  title: "A study in enduring rooms.",
  introduction:
    "Explore Oakved collections through considered interiors, natural materials and the details that define carved living.",
  imageAlt: "Oakved catalog featuring a refined interior collection",
},
```

- [ ] **Step 4: Create the catalog page**

Create `src/pages/CatalogPage.vue`:

```vue
<script setup>
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const catalogArtwork = generatedFurnitureAssets.home.modules["005"];
</script>

<template>
  <article class="catalog-page">
    <section class="catalog-hero">
      <picture class="catalog-hero-picture">
        <source media="(max-width: 760px)" :srcset="catalogArtwork.mobile" />
        <img :src="catalogArtwork.desktop" :alt="t('catalogPage.imageAlt')" />
      </picture>
      <div class="catalog-hero-copy">
        <p>{{ t("catalogPage.eyebrow") }}</p>
        <h1>{{ t("catalogPage.title") }}</h1>
        <div class="catalog-rule" aria-hidden="true"></div>
        <p class="catalog-introduction">{{ t("catalogPage.introduction") }}</p>
      </div>
    </section>
  </article>
</template>
```

- [ ] **Step 5: Register `/catalog` in `App.vue`**

Add the lazy import with the other page imports:

```js
const CatalogPage = defineAsyncComponent(() => import("./pages/CatalogPage.vue"));
```

Insert `catalog: "/catalog",` immediately after `home: "/",` in `pageRoutes`:

```js
home: "/",
catalog: "/catalog",
sale: "/sale",
```

Insert the catalog selection immediately after the current home selection in `pageComponent`:

```js
if (currentPage.value === "home") return HomePage;
if (currentPage.value === "catalog") return CatalogPage;
if (currentPage.value === "sale") return SalePage;
```

Add catalog SEO inside `pageSeo`:

```js
catalog: {
  title: "Oakved Catalog | Oakved",
  description: "Explore the Oakved brand catalog through considered interiors, natural materials and carved details.",
},
```

- [ ] **Step 6: Add responsive catalog styles**

Append a focused catalog section to `src/styles.css`:

```css
.catalog-page {
  background: #fff;
  color: #171717;
}

.catalog-hero {
  position: relative;
  min-height: calc(100vh - 136px);
  overflow: hidden;
}

.catalog-hero-picture,
.catalog-hero-picture img {
  display: block;
  width: 100%;
  height: 100%;
}

.catalog-hero-picture {
  position: absolute;
  inset: 0;
}

.catalog-hero-picture img {
  object-fit: cover;
}

.catalog-hero::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.52), rgba(0, 0, 0, 0.08));
}

.catalog-hero-copy {
  position: relative;
  z-index: 1;
  width: min(520px, calc(100% - 64px));
  padding: clamp(140px, 22vh, 240px) 0 96px clamp(32px, 7vw, 112px);
  color: #fff;
}

.catalog-hero-copy > p:first-child {
  margin: 0 0 24px;
  font-size: 12px;
  letter-spacing: 0.22em;
}

.catalog-hero-copy h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-size: clamp(48px, 6vw, 88px);
  font-weight: 400;
  line-height: 0.98;
}

.catalog-rule {
  width: 72px;
  height: 1px;
  margin: 32px 0;
  background: currentColor;
}

.catalog-introduction {
  max-width: 440px;
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
}

@media (max-width: 760px) {
  .catalog-hero {
    min-height: calc(100dvh - 88px);
  }

  .catalog-hero::after {
    background: linear-gradient(180deg, rgba(0, 0, 0, 0.08) 35%, rgba(0, 0, 0, 0.62));
  }

  .catalog-hero-copy {
    display: flex;
    min-height: calc(100dvh - 88px);
    flex-direction: column;
    justify-content: flex-end;
    width: auto;
    padding: 96px 24px 48px;
  }
}
```

- [ ] **Step 7: Run catalog tests, navigation regressions, the full suite, and production build**

Run in order:

```powershell
npm.cmd test -- tests/catalogPage.test.js tests/rhLayout.test.js tests/headerLanguageMenu.test.js tests/i18n.test.js
npm.cmd test
npm.cmd run build
```

Expected: every test command exits `0`; the build exits `0` and writes the Vite production bundle to `furniture web/dist`.

- [ ] **Step 8: Run the English site and capture visual evidence**

Start the Vite site on `http://127.0.0.1:5173/`, select English, and capture these desktop states:

1. Default header with all eight English primary items visible without overlap.
2. `SHOP BY COLLECTIONS` dropdown with `OAKVED catalog` first.
3. `DINING` dropdown with `OAKVED catalog` first, followed by `Rectangular Tables`.
4. `/catalog` showing the image-led Oakved catalog page.

Also inspect a viewport at or below `760px` and confirm the mobile drawer exposes the same ordered primary items and nested catalog links.

Expected: no clipped primary text, every visible dropdown starts with `OAKVED catalog`, the `DINING` correction is visible, and clicking any catalog entry reaches the same `/catalog` page.

- [ ] **Step 9: Commit the shared catalog page and verified English delivery**

```powershell
git add -- "furniture web/src/pages/CatalogPage.vue" "furniture web/src/App.vue" "furniture web/src/i18n.js" "furniture web/src/styles.css" "furniture web/tests/catalogPage.test.js"
git commit -m "feat: add shared Oakved catalog page"
```

After the commit, present the four English screenshots to the user and wait for visual approval before adding Chinese and French translations.
