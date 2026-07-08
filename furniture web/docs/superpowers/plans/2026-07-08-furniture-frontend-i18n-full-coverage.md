# Furniture Frontend I18n Full Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete visible-copy localization for the furniture frontend across English, Simplified Chinese, and French while keeping `Oakved` in English.

**Architecture:** Keep the existing `src/i18n.js` helper as the central translation source. Add coverage tests that fail on known hard-coded visible strings and corrupted Chinese text, then move page/component copy into structured translation namespaces consumed through `useI18n().t(key)`.

**Tech Stack:** Vue 3 single-file components, Vite, Vitest, existing `src/i18n.js` translation helper, PowerShell on Windows.

## Global Constraints

- Include all user-visible page copy.
- Include navigation labels, page headings, buttons, helper text, empty states, card copy, form labels, placeholders, checkout copy, cart copy, membership copy, account copy, and gift registry copy.
- Include visible text that appears over or alongside images when that text is rendered by frontend code.
- Exclude non-visible accessibility-only copy such as `aria-label`.
- Exclude SEO-only copy such as `document.title` and `meta description`.
- Exclude docs, tests, fixtures, mock files, and other non-runtime content from the translation target set.
- Keep `Oakved` in English in every language.
- Translate image-associated visible sentences into all three languages when those sentences are rendered by code.
- Do not add a new i18n framework.
- Do not call a remote translation service.
- Preserve existing commerce, cart, checkout, account, and Yudao service behavior.

---

## File Structure

**Core translation file**

- Modify: `src/i18n.js`

**Primary page and component integration files**

- Modify: `src/App.vue`
- Modify: `src/components/CartDrawer.vue`
- Modify: `src/components/RhHeader.vue`
- Modify: `src/components/RhPromoBanner.vue`
- Modify: `src/components/RhFooter.vue` only if visible hard-coded copy remains after inventory
- Modify: `src/pages/HomePage.vue`
- Modify: `src/pages/SalePage.vue`
- Modify: `src/pages/OutdoorPage.vue`
- Modify: `src/pages/TeenPage.vue`
- Modify: `src/pages/BabyChildPage.vue`
- Modify: `src/pages/BabyChildCategoryPage.vue`
- Modify: `src/pages/MissingExtractionPage.vue`
- Modify: `src/pages/SofasPlpPage.vue`
- Modify: `src/pages/SofaPdpPage.vue`
- Modify: `src/pages/CheckoutPage.vue`
- Modify: `src/pages/AccountPage.vue`
- Modify: `src/pages/GiftRegistryPage.vue`
- Modify: `src/pages/GiftRegistryFindPage.vue`
- Modify: `src/pages/GiftRegistryCreatePage.vue`
- Modify: `src/pages/GiftRegistryManagePage.vue`

**Tests**

- Modify: `tests/i18n.test.js`
- Create: `tests/i18nVisibleCoverage.test.js`
- Modify targeted source-string tests when their old assertions intentionally referenced visible English strings:
  - `tests/homeLandingPage.test.js`
  - `tests/storefrontLaunchPolish.test.js`
  - `tests/checkoutFlowPage.test.js`
  - `tests/giftRegistryPages.test.js`

---

### Task 1: Add Localization Coverage Tests

**Files:**

- Create: `tests/i18nVisibleCoverage.test.js`
- Modify: `tests/i18n.test.js`

**Interfaces:**

- Consumes: `availableLocales`, `getMessage`, `setLocale`, and `t` from `src/i18n.js`.
- Produces: failing tests that define visible-copy coverage, valid locale labels, non-mojibake Chinese, and key availability for pages that will be localized in later tasks.

- [ ] **Step 1: Add the visible-source coverage test file**

Create `tests/i18nVisibleCoverage.test.js` with this content:

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

const visibleSourceFiles = [
  "../src/App.vue",
  "../src/components/CartDrawer.vue",
  "../src/components/RhHeader.vue",
  "../src/components/RhPromoBanner.vue",
  "../src/pages/AccountPage.vue",
  "../src/pages/BabyChildCategoryPage.vue",
  "../src/pages/BabyChildPage.vue",
  "../src/pages/CheckoutPage.vue",
  "../src/pages/GiftRegistryCreatePage.vue",
  "../src/pages/GiftRegistryFindPage.vue",
  "../src/pages/GiftRegistryManagePage.vue",
  "../src/pages/GiftRegistryPage.vue",
  "../src/pages/HomePage.vue",
  "../src/pages/MissingExtractionPage.vue",
  "../src/pages/OutdoorPage.vue",
  "../src/pages/SalePage.vue",
  "../src/pages/SofaPdpPage.vue",
  "../src/pages/SofasPlpPage.vue",
  "../src/pages/TeenPage.vue",
];

const forbiddenVisibleSnippets = [
  ">Account Dashboard<",
  ">Add Gift<",
  ">Add Gift To Bag<",
  "Add to Gift Registry",
  ">Billing address same as shipping<",
  ">Build a coordinated wood furniture setting<",
  "Build the room around bedside storage",
  ">Cart<",
  ">Check back after the owner adds items.<",
  ">Clear all<",
  ">Click, scroll or use arrow keys to switch views<",
  ">Collection<",
  ">Complete The Room<",
  ">Create Flow<",
  ">Create a Registry<",
  ">Design Services<",
  ">Featured collection<",
  ">Filter<",
  ">Find a Registry<",
  ">Gift Message <",
  ">Gift Registry<",
  ">Images, sizes and stock stay visible while browsing<",
  ">Join RH Members<",
  ">Manage Registry<",
  ">Manage Your Registry<",
  ">Material and finish guidance<",
  ">Member Savings<",
  ">No Gifts Yet<",
  ">Oakved Edit<",
  ">Open-air rooms, fully composed.<",
  ">Order Description <",
  ">Order Summary<",
  ">Outdoor furniture planning<",
  ">Payment<",
  ">Personal rooms, fully considered.<",
  ">Product page placeholder<",
  ">Registry Gifts<",
  ">Room Inspiration<",
  ">Rooms built around proportion, material and calm.<",
  ">Rooms for first chapters.<",
  ">Save this credit card to my account<",
  ">Search<",
  ">Ship to United States ",
  ">Shop Outdoor<",
  ">Shop Teen<",
  ">Shop the edit<",
  ">Sign In Required<",
  ">Start with RH Members<",
  ">Style the full Oakved room<",
  ">This registry does not have public gift items yet.<",
  ">View Cart ",
  ">View Product<",
  ">View Registry<",
  ">占位中",
  ">这些页面先保留开发占位<",
  ">素材与页面方案待定<",
];

const mojibakePattern = /[锟閳閼涓濮鍥绾佃瘽]/;

describe("visible copy localization coverage", () => {
  it("keeps targeted runtime source files free of known hard-coded visible copy", () => {
    for (const path of visibleSourceFiles) {
      const source = readSource(path);

      for (const snippet of forbiddenVisibleSnippets) {
        expect(source, `${path} still contains visible snippet: ${snippet}`).not.toContain(snippet);
      }
    }
  });

  it("keeps covered runtime localization text free of common mojibake markers", () => {
    const source = readSource("../src/i18n.js");

    expect(source).not.toMatch(mojibakePattern);
    expect(source).not.toContain("Fran莽ais");
    expect(source).not.toContain("涓枃");
  });
});
```

- [ ] **Step 2: Update the locale metadata expectations in `tests/i18n.test.js`**

Replace the current locale metadata expectation with valid labels:

```js
expect(availableLocales).toEqual([
  { lang: "en", label: "English", shortLabel: "EN" },
  { lang: "zh-CN", label: "中文", shortLabel: "中文" },
  { lang: "fr", label: "Français", shortLabel: "FR" },
]);
```

- [ ] **Step 3: Add i18n key availability assertions for newly covered domains**

Append this test to `tests/i18n.test.js`:

```js
it("provides full visible-copy namespaces for every supported locale", async () => {
  const { availableLocales, getMessage } = await loadI18n();
  const keys = [
    "home.commerce.eyebrow",
    "home.commerce.title",
    "home.featured.title",
    "home.trust.memberPricing.title",
    "landing.common.collection",
    "outdoor.hero.title",
    "outdoor.services.title",
    "teen.hero.title",
    "teen.services.cta",
    "babyChild.hero.title",
    "babyChild.category.placeholderTitle",
    "sale.hero.title",
    "productList.filters.title",
    "productList.filters.clearAll",
    "productList.edit.title",
    "productDetail.gallery.instructions",
    "productDetail.registry.add",
    "productDetail.inspiration.title",
    "productDetail.shopRoom.title",
    "productDetail.completeRoom.title",
    "cart.drawerTitle",
    "cart.summary.title",
    "cart.membership.description",
    "checkout.header.title",
    "checkout.header.shipping",
    "checkout.header.payment",
    "checkout.header.confirmation",
    "checkout.payment.saveCard",
    "checkout.payment.billingSameAsShipping",
    "checkout.summary.memberSavings",
    "checkout.footer.privacy",
    "giftRegistry.eyebrow",
    "giftRegistry.home.title",
    "giftRegistry.find.title",
    "giftRegistry.create.title",
    "giftRegistry.manage.title",
    "giftRegistry.public.noGiftsTitle",
    "account.dashboard.title",
    "placeholder.missing.eyebrow",
  ];

  for (const locale of availableLocales) {
    for (const key of keys) {
      expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
    }
  }
});
```

- [ ] **Step 4: Run the focused tests and verify they fail**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js tests/i18nVisibleCoverage.test.js
```

Expected: FAIL because `src/i18n.js` still contains corrupted labels and many new keys do not exist yet.

- [ ] **Step 5: Commit the failing coverage tests**

Run:

```powershell
git add -- tests/i18n.test.js tests/i18nVisibleCoverage.test.js
git commit -m "test: define i18n visible copy coverage"
```

Expected: commit succeeds with only the two i18n test files staged.

---

### Task 2: Repair Locale Metadata And Core Translation Tables

**Files:**

- Modify: `src/i18n.js`
- Test: `tests/i18n.test.js`
- Test: `tests/i18nVisibleCoverage.test.js`

**Interfaces:**

- Consumes: existing `availableLocales`, `messages`, `getMessage`, `setLocale`, and `t`.
- Produces: valid locale labels, readable Simplified Chinese, readable French, and complete message namespaces for shared domains used by later page tasks.

- [ ] **Step 1: Update locale labels**

In `src/i18n.js`, set locale metadata to:

```js
export const availableLocales = [
  { lang: "en", label: "English", shortLabel: "EN" },
  { lang: "zh-CN", label: "中文", shortLabel: "中文" },
  { lang: "fr", label: "Français", shortLabel: "FR" },
];
```

- [ ] **Step 2: Add shared namespace structure**

Inside each of `messages.en`, `messages["zh-CN"]`, and `messages.fr`, add or normalize these concrete key groups:

```js
[
  "landing.common.collection",
  "landing.common.designServices",
  "landing.common.joinMembers",
  "landing.common.exploreServices",
  "productDetail.gallery.previous",
  "productDetail.gallery.next",
  "productDetail.gallery.instructions",
  "productDetail.registry.add",
  "productDetail.inspiration.eyebrow",
  "productDetail.inspiration.title",
  "productDetail.inspiration.description",
  "productDetail.shopRoom.eyebrow",
  "productDetail.shopRoom.title",
  "productDetail.shopRoom.description",
  "productDetail.completeRoom.eyebrow",
  "productDetail.completeRoom.title",
  "productDetail.completeRoom.description",
  "giftRegistry.eyebrow",
  "giftRegistry.nav.home",
  "giftRegistry.nav.create",
  "giftRegistry.nav.find",
  "giftRegistry.nav.manage",
  "giftRegistry.nav.account",
  "giftRegistry.home.title",
  "giftRegistry.home.description",
  "giftRegistry.find.title",
  "giftRegistry.find.search",
  "giftRegistry.find.create",
  "giftRegistry.find.manage",
  "giftRegistry.find.view",
  "giftRegistry.create.title",
  "giftRegistry.create.find",
  "giftRegistry.create.manage",
  "giftRegistry.create.flow",
  "giftRegistry.create.steps.event",
  "giftRegistry.create.steps.registrant",
  "giftRegistry.create.steps.delivery",
  "giftRegistry.create.steps.privacy",
  "giftRegistry.create.steps.share",
  "giftRegistry.create.purchaseCallbackNote",
  "giftRegistry.manage.eyebrow",
  "giftRegistry.manage.title",
  "giftRegistry.manage.signInEyebrow",
  "giftRegistry.manage.signInTitle",
  "giftRegistry.manage.signIn",
  "giftRegistry.manage.viewPublic",
  "giftRegistry.manage.giftsEyebrow",
  "giftRegistry.manage.addProductTitle",
  "giftRegistry.manage.addGift",
  "giftRegistry.manage.viewProduct",
  "giftRegistry.public.titleFallback",
  "giftRegistry.public.eventFallback",
  "giftRegistry.public.unavailable",
  "giftRegistry.public.requestedPurchased",
  "giftRegistry.public.itemFallbackNote",
  "giftRegistry.public.viewProduct",
  "giftRegistry.public.addGiftToBag",
  "giftRegistry.public.noGiftsEyebrow",
  "giftRegistry.public.noGiftsTitle",
  "giftRegistry.public.noGiftsDescription",
  "placeholder.missing.eyebrow",
  "placeholder.missing.title",
]
```

Use concrete values in all three languages. Required English values include:

```js
landing.common.collection = "Collection";
productDetail.gallery.instructions = "Click, scroll or use arrow keys to switch views";
giftRegistry.public.noGiftsTitle = "This registry does not have public gift items yet.";
placeholder.missing.title = "These pages remain in development preview.";
```

Simplified Chinese examples:

```js
landing.common.collection = "系列";
productDetail.gallery.instructions = "点击、滚动或使用方向键切换视图";
giftRegistry.public.noGiftsTitle = "这个礼品登记暂时没有公开礼品。";
placeholder.missing.title = "这些页面暂时保留为开发预览。";
```

Required French values include:

```js
landing.common.collection = "Collection";
productDetail.gallery.instructions = "Cliquez, faites défiler ou utilisez les flèches pour changer de vue";
giftRegistry.public.noGiftsTitle = "Cette liste ne contient pas encore de cadeaux publics.";
placeholder.missing.title = "Ces pages restent en aperçu de développement.";
```

- [ ] **Step 3: Replace corrupted covered Chinese values**

In `src/i18n.js`, replace corrupted Chinese values in covered namespaces with readable Simplified Chinese. At minimum, repair:

```js
messages["zh-CN"].common
messages["zh-CN"].header
messages["zh-CN"].navigation
messages["zh-CN"].home
messages["zh-CN"].cart
messages["zh-CN"].wishlist
messages["zh-CN"].auth
messages["zh-CN"].checkout
messages["zh-CN"].membership
messages["zh-CN"].orders
messages["zh-CN"].tradeProgram
messages["zh-CN"].giftRegistry
messages["zh-CN"].productList
messages["zh-CN"].productDetail
```

Keep `Oakved`, `Yudao`, `SPU`, and `SKU` unchanged inside translated strings.

- [ ] **Step 4: Update tests that asserted corrupted strings**

In `tests/i18n.test.js`, replace assertions such as:

```js
expect(t("cart.itemCount", { count: 2 })).toBe("2 浠跺晢鍝?);
expect(t("navigation.primary.bedroomFurniture")).toBe("鍗у瀹跺叿");
```

with readable text:

```js
expect(t("cart.itemCount", { count: 2 })).toBe("2 件商品");
expect(t("navigation.primary.bedroomFurniture")).toBe("卧室家具");
```

- [ ] **Step 5: Run focused i18n tests**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js tests/i18nVisibleCoverage.test.js
```

Expected: `tests/i18n.test.js` passes for locale labels and new keys. `tests/i18nVisibleCoverage.test.js` may still fail because page files still contain hard-coded visible literals.

- [ ] **Step 6: Commit core i18n repairs**

Run:

```powershell
git add -- src/i18n.js tests/i18n.test.js
git commit -m "feat: repair core three-language i18n messages"
```

Expected: commit includes `src/i18n.js` and `tests/i18n.test.js`.

---

### Task 3: Localize Landing And Merchandising Pages

**Files:**

- Modify: `src/i18n.js`
- Modify: `src/pages/HomePage.vue`
- Modify: `src/pages/SalePage.vue`
- Modify: `src/pages/OutdoorPage.vue`
- Modify: `src/pages/TeenPage.vue`
- Modify: `src/pages/BabyChildPage.vue`
- Modify: `src/pages/BabyChildCategoryPage.vue`
- Modify: `src/pages/MissingExtractionPage.vue`
- Modify: `tests/homeLandingPage.test.js`
- Test: `tests/i18nVisibleCoverage.test.js`

**Interfaces:**

- Consumes: `useI18n().t`.
- Produces: translated landing-page hero copy, image-adjacent code-rendered copy, card labels, service bands, placeholder copy, and key-backed arrays.

- [ ] **Step 1: Add landing page translation namespaces**

In `src/i18n.js`, add concrete `en`, `zh-CN`, and `fr` values for:

```js
home.commerce
home.featured
home.trust
home.editorial
home.categoryEdits
sale.hero
outdoor.hero
outdoor.edits
outdoor.collections
outdoor.services
teen.hero
teen.collections
teen.services
babyChild.hero
babyChild.collections
babyChild.services
babyChild.category
placeholder.missing
```

Keep `Oakved` in English when it appears in hero or editorial copy.

- [ ] **Step 2: Convert `HomePage.vue` data arrays to key-backed records**

In `src/pages/HomePage.vue`, replace display string fields in `editorialModules`, `categoryEdits`, and `trustSignals` with ids or key fragments.

Use this pattern:

```js
const editorialModules = [
  {
    id: "bedroomFurniture",
    href: "/products?room=bedroom",
    desktop: generatedFurnitureAssets.home.modules["002"].desktop,
    mobile: generatedFurnitureAssets.home.modules["002"].mobile,
  },
];

const homeModuleCopy = (item, field) => t(`home.editorial.${item.id}.${field}`);
```

Update template calls from:

```vue
<h2>{{ homeModuleCopy(index, "title") }}</h2>
```

to:

```vue
<h2>{{ homeModuleCopy(item, "title") }}</h2>
```

Keep `alt` attributes unchanged unless they were already visible in the template as text.

- [ ] **Step 3: Localize visible static Home page template copy**

In `src/pages/HomePage.vue`, replace:

```vue
<p class="eyebrow">Shop the edit</p>
<h2>Rooms built around proportion, material and calm.</h2>
<p>Start with the room, then refine by fabric, finish, delivery window and member pricing.</p>
<p class="eyebrow">Featured collection</p>
<h2>A bedroom collection built from warm wood, storage and quiet proportion.</h2>
<a href="/products?room=bedroom">Shop bedroom furniture</a>
```

with:

```vue
<p class="eyebrow">{{ t("home.commerce.eyebrow") }}</p>
<h2>{{ t("home.commerce.title") }}</h2>
<p>{{ t("home.commerce.description") }}</p>
<p class="eyebrow">{{ t("home.featured.eyebrow") }}</p>
<h2>{{ t("home.featured.title") }}</h2>
<a href="/products?room=bedroom">{{ t("home.featured.cta") }}</a>
```

- [ ] **Step 4: Localize `OutdoorPage.vue`, `TeenPage.vue`, and `BabyChildPage.vue`**

In each file, import `useI18n` if missing:

```js
import { useI18n } from "../i18n.js";

const { t } = useI18n();
```

Replace visible strings such as hero titles, CTA labels, `Collection`, `Design Services`, service bullet labels, and service CTAs with `t(key)`.

Use these key patterns:

```vue
<BrandEyebrow :suffix="t('outdoor.hero.eyebrow')" />
<h1 id="outdoor-landing-title">{{ t("outdoor.hero.title") }}</h1>
<a href="/sofas-plp">{{ t("outdoor.hero.shopCta") }}</a>
<span class="eyebrow">{{ t("landing.common.collection") }}</span>
<p class="eyebrow">{{ t("landing.common.designServices") }}</p>
```

Use `teen.*` and `babyChild.*` for their matching pages.

- [ ] **Step 5: Localize `BabyChildCategoryPage.vue` and `MissingExtractionPage.vue`**

In `src/pages/BabyChildCategoryPage.vue`, replace visible placeholder copy with:

```vue
<span>{{ t("babyChild.category.placeholderEyebrow") }}</span>
<p>{{ t("babyChild.category.placeholderDescription") }}</p>
```

In `src/pages/MissingExtractionPage.vue`, replace visible placeholder copy with:

```vue
<p class="eyebrow">{{ t("placeholder.missing.eyebrow") }}</p>
<h1>{{ t("placeholder.missing.title") }}</h1>
```

- [ ] **Step 6: Update landing page tests**

In `tests/homeLandingPage.test.js`, replace checks that expect old helper signatures:

```js
expect(homePage).toContain('homeModuleCopy(index, "title")');
expect(homePage).toContain("homeModuleEyebrowSuffix(index)");
```

with checks for key-backed copy:

```js
expect(homePage).toContain('homeModuleCopy(item, "title")');
expect(homePage).toContain("homeModuleEyebrowSuffix(item)");
expect(homePage).toContain('t("home.commerce.eyebrow")');
expect(homePage).toContain('t("home.featured.title")');
```

- [ ] **Step 7: Run landing-focused tests**

Run:

```powershell
npm.cmd test -- tests/homeLandingPage.test.js tests/i18n.test.js tests/i18nVisibleCoverage.test.js
```

Expected: landing-related tests pass or fail only on later task domains such as cart, checkout, gift registry, or product pages.

- [ ] **Step 8: Commit landing localization**

Run:

```powershell
git add -- src/i18n.js src/pages/HomePage.vue src/pages/SalePage.vue src/pages/OutdoorPage.vue src/pages/TeenPage.vue src/pages/BabyChildPage.vue src/pages/BabyChildCategoryPage.vue src/pages/MissingExtractionPage.vue tests/homeLandingPage.test.js
git commit -m "feat: localize landing page visible copy"
```

Expected: commit contains landing page files, `src/i18n.js`, and updated landing tests.

---

### Task 4: Localize Product List And Product Detail Pages

**Files:**

- Modify: `src/i18n.js`
- Modify: `src/pages/SofasPlpPage.vue`
- Modify: `src/pages/SofaPdpPage.vue`
- Modify: `tests/storefrontLaunchPolish.test.js`
- Test: `tests/i18nVisibleCoverage.test.js`

**Interfaces:**

- Consumes: existing product models and `useI18n().t`.
- Produces: translated product browsing controls, product support labels, gallery instructions, registry CTA, and visible editorial modules around product content.

- [ ] **Step 1: Add product translations**

In `src/i18n.js`, add concrete `en`, `zh-CN`, and `fr` values for:

```js
productList.filters.title
productList.filters.close
productList.filters.clearAll
productList.browseStatus
productList.card.member
productList.card.regular
productList.card.size
productList.edit.eyebrow
productList.edit.title
productList.edit.description
productList.edit.cta
productDetail.gallery.instructions
productDetail.registry.add
productDetail.inspiration.eyebrow
productDetail.inspiration.title
productDetail.inspiration.description
productDetail.shopRoom.eyebrow
productDetail.shopRoom.title
productDetail.shopRoom.description
productDetail.completeRoom.eyebrow
productDetail.completeRoom.title
productDetail.completeRoom.description
```

- [ ] **Step 2: Localize `SofasPlpPage.vue` visible browse controls**

Replace visible strings:

```vue
<p class="eyebrow">Filter</p>
<button type="button" @click="mobileFiltersOpen = false">Close</button>
<button type="button" @click="resetProductListControls">Clear all</button>
<span>Images, sizes and stock stay visible while browsing</span>
<dt>Member</dt>
<dt>Regular</dt>
<dt>Size</dt>
<p class="eyebrow">Oakved Edit</p>
<h2>Wood furniture for the finished bedroom</h2>
<a href="/products?collection=bedroom-set">View bedroom sets</a>
```

with:

```vue
<p class="eyebrow">{{ t("productList.filters.title") }}</p>
<button type="button" @click="mobileFiltersOpen = false">{{ t("productList.filters.close") }}</button>
<button type="button" @click="resetProductListControls">{{ t("productList.filters.clearAll") }}</button>
<span>{{ t("productList.browseStatus") }}</span>
<dt>{{ t("productList.card.member") }}</dt>
<dt>{{ t("productList.card.regular") }}</dt>
<dt>{{ t("productList.card.size") }}</dt>
<p class="eyebrow">{{ t("productList.edit.eyebrow") }}</p>
<h2>{{ t("productList.edit.title") }}</h2>
<a href="/products?collection=bedroom-set">{{ t("productList.edit.cta") }}</a>
```

- [ ] **Step 3: Localize `SofaPdpPage.vue` visible product support copy**

Replace visible strings:

```vue
<small>Click, scroll or use arrow keys to switch views</small>
{{ registryBusy ? t("common.working") : "Add to Gift Registry" }}
<p class="eyebrow">Room Inspiration</p>
<h2>Style the full Oakved room</h2>
<p class="eyebrow">Shop The Room</p>
<h2>Build a coordinated wood furniture setting</h2>
<p class="eyebrow">Complete The Room</p>
<h2>Pieces that sit well together</h2>
```

with:

```vue
<small>{{ t("productDetail.gallery.instructions") }}</small>
{{ registryBusy ? t("common.working") : t("productDetail.registry.add") }}
<p class="eyebrow">{{ t("productDetail.inspiration.eyebrow") }}</p>
<h2>{{ t("productDetail.inspiration.title") }}</h2>
<p class="eyebrow">{{ t("productDetail.shopRoom.eyebrow") }}</p>
<h2>{{ t("productDetail.shopRoom.title") }}</h2>
<p class="eyebrow">{{ t("productDetail.completeRoom.eyebrow") }}</p>
<h2>{{ t("productDetail.completeRoom.title") }}</h2>
```

Keep product names, material names, collection values, and `Oakved` unchanged where they come from product data.

- [ ] **Step 4: Update source-string tests**

In `tests/storefrontLaunchPolish.test.js`, replace assertions:

```js
expect(source).toContain("Showing");
expect(source).toContain("Clear all");
```

with:

```js
expect(source).toContain('t("productList.resultSummary"');
expect(source).toContain('t("productList.filters.clearAll")');
```

- [ ] **Step 5: Run product-focused tests**

Run:

```powershell
npm.cmd test -- tests/storefrontLaunchPolish.test.js tests/sofasPlpControls.test.js tests/productDetailModel.test.js tests/i18nVisibleCoverage.test.js
```

Expected: product-related tests pass or fail only on later cart, checkout, gift registry, or account literals.

- [ ] **Step 6: Commit product localization**

Run:

```powershell
git add -- src/i18n.js src/pages/SofasPlpPage.vue src/pages/SofaPdpPage.vue tests/storefrontLaunchPolish.test.js
git commit -m "feat: localize product browsing visible copy"
```

Expected: commit contains product page localization and updated source-string test.

---

### Task 5: Localize Gift Registry Pages

**Files:**

- Modify: `src/i18n.js`
- Modify: `src/pages/GiftRegistryPage.vue`
- Modify: `src/pages/GiftRegistryFindPage.vue`
- Modify: `src/pages/GiftRegistryCreatePage.vue`
- Modify: `src/pages/GiftRegistryManagePage.vue`
- Modify: `tests/giftRegistryPages.test.js`
- Test: `tests/i18nVisibleCoverage.test.js`

**Interfaces:**

- Consumes: existing gift registry services and `useI18n().t`.
- Produces: translated gift registry landing, find, create, manage, public registry, empty state, and action copy.

- [ ] **Step 1: Add `useI18n` imports to registry pages**

In each gift registry page that does not already import i18n, add:

```js
import { useI18n } from "../i18n.js";

const { t } = useI18n();
```

- [ ] **Step 2: Replace registry navigation entries with key-backed entries**

In `GiftRegistryPage.vue`, replace:

```js
const registryEntries = [
  { title: "Find a Registry", href: membershipRoutes.giftRegistryFind },
  { title: "Create a Registry", href: membershipRoutes.giftRegistryCreate },
  { title: "Manage Your Registry", href: membershipRoutes.giftRegistryManage },
];
```

with:

```js
const registryEntries = [
  { labelKey: "giftRegistry.nav.find", href: membershipRoutes.giftRegistryFind },
  { labelKey: "giftRegistry.nav.create", href: membershipRoutes.giftRegistryCreate },
  { labelKey: "giftRegistry.nav.manage", href: membershipRoutes.giftRegistryManage },
];
```

Update the template:

```vue
<a v-for="entry in registryEntries" :key="entry.labelKey" :href="entry.href">{{ t(entry.labelKey) }}</a>
```

- [ ] **Step 3: Localize public registry visible copy**

In `GiftRegistryPage.vue`, replace visible literals with these keys:

```vue
<p class="eyebrow">{{ t("giftRegistry.eyebrow") }}</p>
<h1>{{ registry?.registrants?.primaryName || t("giftRegistry.public.titleFallback") }}</h1>
<h1>{{ t("giftRegistry.home.title") }}</h1>
<p>{{ t("giftRegistry.home.description") }}</p>
<p class="eyebrow">
  {{ t("giftRegistry.public.requestedPurchased", { requested: item.quantityRequested, purchased: item.quantityPurchased }) }}
</p>
<p>{{ item.note || t("giftRegistry.public.itemFallbackNote") }}</p>
<a :href="`/product?id=${item.spuId}&registryItemId=${item.id}`">{{ t("giftRegistry.public.viewProduct") }}</a>
<button class="registry-cart-button" type="button" @click="handleAddRegistryGiftToCart(item)">
  {{ t("giftRegistry.public.addGiftToBag") }}
</button>
<p class="eyebrow">{{ t("giftRegistry.public.noGiftsEyebrow") }}</p>
<h2>{{ t("giftRegistry.public.noGiftsTitle") }}</h2>
<p>{{ t("giftRegistry.public.noGiftsDescription") }}</p>
```

When setting `loadMessage`, use:

```js
loadMessage.value = t("giftRegistry.public.unavailable");
```

- [ ] **Step 4: Localize find, create, and manage pages**

Use these replacements:

```vue
<p class="eyebrow">{{ t("giftRegistry.eyebrow") }}</p>
<h1>{{ t("giftRegistry.find.title") }}</h1>
<button type="button" @click="runSearch">{{ t("giftRegistry.find.search") }}</button>
<a :href="`/gift-registry/${registry.publicCode}`">{{ t("giftRegistry.find.view") }}</a>
```

```vue
<h1>{{ t("giftRegistry.create.title") }}</h1>
<a class="membership-primary-link" :href="membershipRoutes.giftRegistryFind">{{ t("giftRegistry.create.find") }}</a>
<a :href="membershipRoutes.giftRegistryManage">{{ t("giftRegistry.create.manage") }}</a>
<p class="eyebrow">{{ t("giftRegistry.create.flow") }}</p>
<p class="eyebrow">{{ t("giftRegistry.create.steps.event") }}</p>
<p class="eyebrow">{{ t("giftRegistry.create.steps.registrant") }}</p>
<p class="eyebrow">{{ t("giftRegistry.create.steps.delivery") }}</p>
<p class="eyebrow">{{ t("giftRegistry.create.steps.privacy") }}</p>
<p class="eyebrow">{{ t("giftRegistry.create.steps.share") }}</p>
<p>{{ t("giftRegistry.create.purchaseCallbackNote") }}</p>
```

```vue
<a :href="membershipRoutes.giftRegistry">{{ t("giftRegistry.nav.home") }}</a>
<a :href="membershipRoutes.giftRegistryCreate">{{ t("giftRegistry.nav.create") }}</a>
<a :href="membershipRoutes.giftRegistryFind">{{ t("giftRegistry.nav.find") }}</a>
<a :href="membershipRoutes.account">{{ t("giftRegistry.nav.account") }}</a>
<p class="eyebrow">{{ t("giftRegistry.manage.eyebrow") }}</p>
<h1>{{ t("giftRegistry.manage.title") }}</h1>
<p class="eyebrow">{{ t("giftRegistry.manage.signInEyebrow") }}</p>
<h2>{{ t("giftRegistry.manage.signInTitle") }}</h2>
<a :href="membershipRoutes.checkoutAuth">{{ t("giftRegistry.manage.signIn") }}</a>
<a v-if="shareState.publicUrl" :href="shareState.publicUrl">{{ t("giftRegistry.manage.viewPublic") }}</a>
<p class="eyebrow">{{ t("giftRegistry.manage.giftsEyebrow") }}</p>
<h2>{{ t("giftRegistry.manage.addProductTitle") }}</h2>
<button class="membership-primary-link" type="button" @click="addItem">{{ t("giftRegistry.manage.addGift") }}</button>
<a :href="`/product?id=${item.spuId}`">{{ t("giftRegistry.manage.viewProduct") }}</a>
```

- [ ] **Step 5: Update gift registry tests**

In `tests/giftRegistryPages.test.js`, add assertions that each registry page uses i18n:

```js
expect(publicPage).toContain('t("giftRegistry.eyebrow")');
expect(find).toContain('t("giftRegistry.find.title")');
expect(manage).toContain('t("giftRegistry.manage.title")');
```

Add this assertion for create:

```js
const create = readFileSync(pagePath("GiftRegistryCreatePage.vue"), "utf8");
expect(create).toContain('t("giftRegistry.create.title")');
```

- [ ] **Step 6: Run registry-focused tests**

Run:

```powershell
npm.cmd test -- tests/giftRegistryPages.test.js tests/i18n.test.js tests/i18nVisibleCoverage.test.js
```

Expected: registry tests pass or fail only on later cart, checkout, or account literals.

- [ ] **Step 7: Commit gift registry localization**

Run:

```powershell
git add -- src/i18n.js src/pages/GiftRegistryPage.vue src/pages/GiftRegistryFindPage.vue src/pages/GiftRegistryCreatePage.vue src/pages/GiftRegistryManagePage.vue tests/giftRegistryPages.test.js
git commit -m "feat: localize gift registry visible copy"
```

Expected: commit contains registry page localization and tests.

---

### Task 6: Localize Cart Drawer And Checkout Visible Copy

**Files:**

- Modify: `src/i18n.js`
- Modify: `src/components/CartDrawer.vue`
- Modify: `src/pages/CheckoutPage.vue`
- Modify: `tests/checkoutFlowPage.test.js`
- Test: `tests/cartRecoveryNotice.test.js`
- Test: `tests/cartNavigation.test.js`
- Test: `tests/i18nVisibleCoverage.test.js`

**Interfaces:**

- Consumes: existing cart and checkout props, computed totals, checkout flow services, and `useI18n().t`.
- Produces: localized visible cart drawer copy, checkout header, payment panel, agreements, summary labels, and footer links.

- [ ] **Step 1: Add cart and checkout translations**

In `src/i18n.js`, add concrete `en`, `zh-CN`, and `fr` values for:

```js
cart.drawerTitle
cart.shop
cart.fabric
cart.color
cart.defaultColor
cart.width
cart.depth
cart.itemNumber
cart.member
cart.apply
cart.membership.programLine1
cart.membership.programLine2
cart.membership.description
cart.summary.title
cart.summary.shippingTo
cart.summary.memberSavings
cart.summary.orderSubtotal
cart.summary.membersProgram
cart.summary.unlimitedDelivery
cart.summary.totalExcludingTax
checkout.header.title
checkout.header.shipping
checkout.header.payment
checkout.header.confirmation
checkout.header.shipToUnitedStates
checkout.payment.title
checkout.payment.intro
checkout.payment.saveCard
checkout.payment.billingSameAsShipping
checkout.payment.billingAddress
checkout.payment.edit
checkout.payment.giftMessage
checkout.payment.orderDescription
checkout.payment.viewCart
checkout.summary.memberSavings
checkout.summary.subtotalWithMemberSavings
checkout.summary.membersProgram
checkout.summary.unlimitedDelivery
checkout.agreements.membersTerms
checkout.footer.privacy
checkout.footer.shippingDelivery
checkout.footer.returnsExchanges
checkout.footer.accessibility
checkout.footer.contact
checkout.footer.copyright
```

- [ ] **Step 2: Localize `CartDrawer.vue` visible strings**

Replace visible strings such as:

```vue
<span>Shop</span>
<h2>Cart</h2>
<dt>Fabric</dt>
<dd>Wheat</dd>
<button class="cart-risk-action cart-risk-action-warning" type="button">Apply</button>
<b>Annual members save 5% on their first eligible order.</b>
<h3>Order Summary</h3>
<span>Shipping to <u>94925</u></span>
```

with `t(key)`, including interpolation for ZIP:

```vue
<span>{{ t("cart.shop") }}</span>
<h2>{{ t("cart.drawerTitle") }}</h2>
<dt>{{ t("cart.fabric") }}</dt>
<dd>{{ t("cart.defaultColor") }}</dd>
<button class="cart-risk-action cart-risk-action-warning" type="button">{{ t("cart.apply") }}</button>
<b>{{ t("cart.membership.description") }}</b>
<h3>{{ t("cart.summary.title") }}</h3>
<span v-html="t('cart.summary.shippingTo', { postalCode: '94925' })"></span>
```

If `v-html` is not already used nearby, avoid it and split the postal code:

```vue
<span>{{ t("cart.summary.shippingToPrefix") }} <u>94925</u></span>
```

- [ ] **Step 3: Localize checkout header and payment sections**

In `CheckoutPage.vue`, replace visible strings such as:

```vue
<button type="button">Ship to United States <span aria-hidden="true">v</span></button>
<h1>Checkout</h1>
<span :class="{ muted: checkoutStage === 'payment' }">Shipping</span>
<span :class="{ muted: checkoutStage !== 'payment' }">Payment</span>
<span class="muted">Confirmation</span>
<h2>Payment</h2>
<p>Select a payment method to use.</p>
<span>Save this credit card to my account</span>
<span>Billing address same as shipping</span>
<h2>Billing Address</h2>
<button type="button" @click="editConfirmedAddress">Edit</button>
```

with:

```vue
<button type="button">{{ t("checkout.header.shipToUnitedStates") }} <span aria-hidden="true">v</span></button>
<h1>{{ t("checkout.header.title") }}</h1>
<span :class="{ muted: checkoutStage === 'payment' }">{{ t("checkout.header.shipping") }}</span>
<span :class="{ muted: checkoutStage !== 'payment' }">{{ t("checkout.header.payment") }}</span>
<span class="muted">{{ t("checkout.header.confirmation") }}</span>
<h2>{{ t("checkout.payment.title") }}</h2>
<p>{{ t("checkout.payment.intro") }}</p>
<span>{{ t("checkout.payment.saveCard") }}</span>
<span>{{ t("checkout.payment.billingSameAsShipping") }}</span>
<h2>{{ t("checkout.payment.billingAddress") }}</h2>
<button type="button" @click="editConfirmedAddress">{{ t("checkout.payment.edit") }}</button>
```

- [ ] **Step 4: Localize checkout summary and footer links**

Replace summary and footer literals with:

```vue
<button type="button">{{ t("checkout.payment.giftMessage") }} <span aria-hidden="true">+</span></button>
<button type="button">{{ t("checkout.payment.orderDescription") }} <span aria-hidden="true">+</span></button>
<button type="button" @click="emit('open-cart')">{{ t("checkout.payment.viewCart") }} <span aria-hidden="true">&gt;</span></button>
<h3>{{ t("checkout.summaryTitle") }}</h3>
<span>{{ t("checkout.summary.memberSavings") }}</span>
<span>{{ t("checkout.summary.subtotalWithMemberSavings") }}</span>
<span>{{ t("checkout.summary.membersProgram") }}</span>
<span><u>{{ t("checkout.summary.unlimitedDelivery") }}</u></span>
<span>{{ t("checkout.agreements.membersTerms") }}</span>
<a href="/privacy">{{ t("checkout.footer.privacy") }}</a>
<a href="/shipping-delivery">{{ t("checkout.footer.shippingDelivery") }}</a>
<a href="/returns-exchanges">{{ t("checkout.footer.returnsExchanges") }}</a>
<a href="/accessibility">{{ t("checkout.footer.accessibility") }}</a>
<a href="/contact">{{ t("checkout.footer.contact") }}</a>
<span>{{ t("checkout.footer.copyright") }}</span>
```

For agreement text that currently contains underlined fragments, split the line into translated fragments:

```vue
{{ t("checkout.agreements.membersTermsPrefix") }}
<u>{{ t("checkout.agreements.membersTermsLink") }}</u>
{{ t("checkout.agreements.membersTermsMiddle") }}
<u>{{ t("checkout.agreements.privacyNotice") }}</u>
```

- [ ] **Step 5: Update checkout source-string tests**

In `tests/checkoutFlowPage.test.js`, keep behavior assertions and replace old visible literal expectations with i18n key checks:

```js
expect(source).toContain('t("checkout.header.title")');
expect(source).toContain('t("checkout.header.shipping")');
expect(source).toContain('t("checkout.payment.saveCard")');
expect(source).toContain('t("checkout.footer.privacy")');
expect(source).not.toContain("<h1>Checkout</h1>");
expect(source).not.toContain("Save this credit card to my account");
```

- [ ] **Step 6: Run cart and checkout tests**

Run:

```powershell
npm.cmd test -- tests/cartRecoveryNotice.test.js tests/cartNavigation.test.js tests/checkoutFlowPage.test.js tests/i18n.test.js tests/i18nVisibleCoverage.test.js
```

Expected: cart and checkout tests pass or fail only on later shared/header/account literals.

- [ ] **Step 7: Commit cart and checkout localization**

Run:

```powershell
git add -- src/i18n.js src/components/CartDrawer.vue src/pages/CheckoutPage.vue tests/checkoutFlowPage.test.js
git commit -m "feat: localize cart and checkout visible copy"
```

Expected: commit contains cart, checkout, translations, and updated checkout tests.

---

### Task 7: Localize Shared Header, Promo, Account Dashboard, And Remaining Visible Copy

**Files:**

- Modify: `src/i18n.js`
- Modify: `src/App.vue`
- Modify: `src/components/RhHeader.vue`
- Modify: `src/components/RhPromoBanner.vue`
- Modify: `src/components/RhFooter.vue` if visible literal coverage identifies remaining text
- Modify: `src/pages/AccountPage.vue`
- Modify: `tests/storefrontLaunchPolish.test.js`
- Test: `tests/authUiStructure.test.js`
- Test: `tests/headerLanguageMenu.test.js`
- Test: `tests/i18nVisibleCoverage.test.js`

**Interfaces:**

- Consumes: existing header navigation structures, account modal state, and `useI18n().t`.
- Produces: localized visible header, promo banner, mobile drawer section labels, account dashboard copy, and app-level visible labels that remain in scope.

- [ ] **Step 1: Add shared translations**

In `src/i18n.js`, add concrete `en`, `zh-CN`, and `fr` values for:

```js
header.mobile.shopFurniture
header.mobile.service
header.brandBabyChildSuffix
promo.newItems
promo.shop
account.dashboard.eyebrow
account.dashboard.sectionEyebrow
account.dashboard.title
```

Keep `Oakved` in English.

- [ ] **Step 2: Localize `RhPromoBanner.vue`**

Replace:

```vue
<span>HUNDREDS OF NEW ITEMS ADDED.</span>
<button type="button">SHOP</button>
```

with:

```vue
<span>{{ t("promo.newItems") }}</span>
<button type="button">{{ t("promo.shop") }}</button>
```

Add:

```js
import { useI18n } from "../i18n.js";

const { t } = useI18n();
```

- [ ] **Step 3: Localize `RhHeader.vue` visible drawer labels**

Replace visible hard-coded drawer labels such as:

```js
"Shop Furniture"
"Service"
```

with translation keys in the section data:

```js
const mobileDrawerSections = [
  { id: "shop", labelKey: "header.mobile.shopFurniture", items: mobileProductItems },
  { id: "service", labelKey: "header.mobile.service", items: mobileServiceItems },
];
```

Render:

```vue
<h2>{{ t(section.labelKey) }}</h2>
```

For the visible `baby & child` suffix, use:

```vue
<span class="brand-button-suffix">{{ t("header.brandBabyChildSuffix") }}</span>
```

- [ ] **Step 4: Localize `AccountPage.vue`**

Add:

```js
import { useI18n } from "../i18n.js";

const { t } = useI18n();
```

Replace:

```vue
<p class="eyebrow">My Account</p>
<p class="eyebrow">Account Dashboard</p>
<h1>Manage orders, addresses, payment methods and membership.</h1>
```

with:

```vue
<p class="eyebrow">{{ t("membership.account.myAccount") }}</p>
<p class="eyebrow">{{ t("account.dashboard.sectionEyebrow") }}</p>
<h1>{{ t("account.dashboard.title") }}</h1>
```

- [ ] **Step 5: Check `App.vue` for visible in-scope literals**

Search:

```powershell
rg -n '>[^<>{}]*[A-Za-z\u4e00-\u9fff][^<>{}]*<' src/App.vue
```

If visible page shell strings remain, add `useI18n` and replace them with keys under `common`, `header`, or the matching domain. Leave SEO-only `document.title` and `meta description` unchanged because the spec excludes them.

- [ ] **Step 6: Update tests that expect old visible header literals**

In `tests/storefrontLaunchPolish.test.js`, replace:

```js
expect(source).toContain("Shop Furniture");
expect(source).toContain("Service");
```

with:

```js
expect(source).toContain('"header.mobile.shopFurniture"');
expect(source).toContain('"header.mobile.service"');
```

- [ ] **Step 7: Run shared UI tests**

Run:

```powershell
npm.cmd test -- tests/authUiStructure.test.js tests/headerLanguageMenu.test.js tests/storefrontLaunchPolish.test.js tests/i18nVisibleCoverage.test.js
```

Expected: shared UI tests pass and visible coverage failures shrink to any missed literals found during this task.

- [ ] **Step 8: Commit shared localization**

Run:

```powershell
git add -- src/i18n.js src/App.vue src/components/RhHeader.vue src/components/RhPromoBanner.vue src/components/RhFooter.vue src/pages/AccountPage.vue tests/storefrontLaunchPolish.test.js
git commit -m "feat: localize shared storefront visible copy"
```

Expected: commit contains only files changed during this task. If `src/components/RhFooter.vue` did not change, omit it from `git add`.

---

### Task 8: Final Full Audit, Build, And Cleanup

**Files:**

- Modify only files needed to address final test or build failures from previous tasks.
- Test: all relevant test files.

**Interfaces:**

- Consumes: all localized source files and tests from Tasks 1-7.
- Produces: passing test suite, passing production build, and final source audit showing no known hard-coded visible copy in covered target files.

- [ ] **Step 1: Run the remaining visible-copy searches**

Run:

```powershell
rg -n '>[^<>{}]*[A-Za-z\u4e00-\u9fff][^<>{}]*<' src/pages src/components src/App.vue
```

Review each hit manually. Keep hits that are:

- dynamic expressions already using `t(key)`
- brand-only `Oakved`
- product data bindings
- non-visible accessibility or SEO strings outside approved scope

Patch every remaining in-scope visible literal into `src/i18n.js` and `t(key)`.

- [ ] **Step 2: Run focused i18n coverage**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js tests/i18nVisibleCoverage.test.js
```

Expected: PASS.

- [ ] **Step 3: Run full test suite**

Run:

```powershell
npm.cmd test
```

Expected: PASS.

- [ ] **Step 4: Run production build**

Run:

```powershell
npm.cmd run build
```

Expected: PASS and Vite emits the production build under `dist`.

- [ ] **Step 5: Review git diff**

Run:

```powershell
git diff -- src/i18n.js src/App.vue src/components src/pages tests
```

Confirm:

- no unrelated service behavior changed
- no backend files changed
- `Oakved` is still English
- Chinese visible strings are readable Simplified Chinese
- French visible strings are readable French

- [ ] **Step 6: Commit final audit fixes**

Run:

```powershell
git add -- src/i18n.js src/App.vue src/components src/pages tests
git commit -m "chore: verify full visible i18n coverage"
```

Expected: commit contains final cleanup changes only. If no files changed after Task 7, skip this commit and record that full tests and build passed with no cleanup diff.

---

## Plan Self-Review

**Spec coverage:** This plan covers the approved visible-copy scope, three locales, `Oakved` preservation, code-rendered image-adjacent text, Chinese mojibake repair, no remote translation service, and final `npm.cmd test` plus `npm.cmd run build` verification.

**Placeholder scan:** The plan contains no empty implementation sections and no deferred file ownership. Every task includes exact target files, test commands, and concrete key patterns or code replacements.

**Type consistency:** The plan uses the existing `useI18n().t`, `getMessage`, `availableLocales`, and `setLocale` interfaces. New key names are referenced consistently between the i18n tests and page/component integration steps.
