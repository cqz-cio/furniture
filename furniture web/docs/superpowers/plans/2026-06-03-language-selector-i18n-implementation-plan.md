# Language Selector I18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add English, Simplified Chinese, and French display modes to the furniture storefront language selector.

**Architecture:** Keep locale state and translation messages centralized in `src/i18n.js`. Vue components call `t()` for display copy and keep business logic out of translation data. Commerce services should remain unchanged except where presentation helpers already exist and can be replaced by component-level translation keys.

**Tech Stack:** Vue 3 Composition API, Vite, Vitest, Playwright for local visual verification.

---

## File Structure And Ownership

- `src/i18n.js`: Locale metadata, translation table, safe storage access, `t(key, params)`.
- `tests/i18n.test.js`: Unit tests for locale list, fallback, persistence, and interpolation.
- `src/components/RhHeader.vue`: Header language selector, account modal copy, navigation aria labels.
- `src/pages/HomePage.vue`: Home hero copy and translated metadata labels.
- `src/components/CartDrawer.vue`: Cart drawer copy, empty state, item count, checkout action.
- `src/components/AuthTokenPanel.vue`: Account token panel copy and actions.
- `src/pages/CheckoutPage.vue`: Checkout copy, mode messages, summary labels, empty state, busy action.
- `src/pages/OrdersPage.vue`: Orders page copy, loading, empty state, labels.
- `tests/checkoutSession.test.js`: Adjust only if presentation helper is no longer responsible for final user-facing text.

## Subagent Assignment

- Worker 1 owns `src/i18n.js` and `tests/i18n.test.js`.
- Worker 2 owns `src/components/RhHeader.vue` and `src/pages/HomePage.vue`.
- Worker 3 owns `src/components/CartDrawer.vue` and `src/components/AuthTokenPanel.vue`.
- Worker 4 owns `src/pages/CheckoutPage.vue`, `src/pages/OrdersPage.vue`, and any small test adjustment required by checkout copy ownership.
- Coordinator owns final integration, full tests, build, and browser verification.

Workers are not alone in the codebase. Do not revert edits made by others. Keep changes inside assigned files unless the coordinator explicitly expands scope.

---

### Task 1: I18n Core

**Files:**
- Modify: `src/i18n.js`
- Create: `tests/i18n.test.js`

- [ ] **Step 1: Write the failing test**

Create `tests/i18n.test.js`:

```js
import { beforeEach, describe, expect, it, vi } from "vitest";

const loadI18n = async () => {
  vi.resetModules();
  return import("../src/i18n.js");
};

describe("i18n locale helper", () => {
  beforeEach(() => {
    const store = new Map();
    vi.stubGlobal("localStorage", {
      getItem: vi.fn((key) => store.get(key) || null),
      setItem: vi.fn((key, value) => store.set(key, value)),
      removeItem: vi.fn((key) => store.delete(key)),
    });
    vi.stubGlobal("document", { documentElement: { lang: "" } });
  });

  it("exposes English, Chinese, and French locale options", async () => {
    const { availableLocales } = await loadI18n();

    expect(availableLocales).toEqual([
      { lang: "en", label: "English", shortLabel: "EN" },
      { lang: "zh-CN", label: "中文", shortLabel: "中文" },
      { lang: "fr", label: "Français", shortLabel: "FR" },
    ]);
  });

  it("persists selected locale and updates the document language", async () => {
    const { currentLocale, setLocale } = await loadI18n();

    setLocale("fr");

    expect(currentLocale.value).toBe("fr");
    expect(globalThis.localStorage.setItem).toHaveBeenCalledWith("furniture-web-locale", "fr");
    expect(globalThis.document.documentElement.lang).toBe("fr");
  });

  it("falls back to English when an unsupported locale is requested", async () => {
    const { currentLocale, setLocale } = await loadI18n();

    setLocale("es");

    expect(currentLocale.value).toBe("en");
  });

  it("translates nested keys and interpolates params", async () => {
    const { setLocale, t } = await loadI18n();

    expect(t("cart.itemCount", { count: 2 })).toBe("2 ITEMS");
    setLocale("zh-CN");
    expect(t("cart.itemCount", { count: 2 })).toBe("2 件商品");
    setLocale("fr");
    expect(t("cart.itemCount", { count: 2 })).toBe("2 ARTICLES");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js
```

Expected: FAIL because `shortLabel`, `fr`, nested translation keys, interpolation, and unsupported-locale fallback are not implemented yet.

- [ ] **Step 3: Implement i18n core**

Replace `src/i18n.js` with a focused helper that:

- Defines `availableLocales` with `en`, `zh-CN`, `fr`.
- Replaces mojibake with valid UTF-8 text.
- Keeps `STORAGE_KEY = "furniture-web-locale"`.
- Adds `safeStorage()`.
- Adds `getMessage(key, lang)`.
- Adds `interpolate(template, params)`.
- Makes `setLocale(unsupported)` fall back to English.
- Exports `t(key, params = {})`.

Required translation keys must include at least these groups:

```js
common: { close, search, language, checkout, loading, working }
header: { menuOpen, menuClose, account, bag, regionSelector, selectedCountry, mobileRegion, signInTitle, signInIntro, createAccount, forgotPassword, secureLink, tradeSignIn }
home: { heroEyebrow, heroSubtitle, gridAria }
cart: { title, empty, emptyHelp, subtotal, quantity, remove, itemCount, remoteBag, localBag, deliveryNote }
auth: { aria, connected, notConnected, accountLabel, help, accessToken, updateToken, saveToken, clear }
checkout: { eyebrow, statusTitle, deliveryTitle, shipTo, itemsTitle, itemsCount, itemKickerYudao, itemKickerPreview, emptyNote, summaryTitle, pieces, merchandise, delivery, estimatedTotal, settlementIncluded, settlementPending, noAddress, mode.* }
orders: { eyebrow, title, connectedCount, intro, loading, tokenRequired, selectedOrder, orderLabel, view, empty, status }
```

- [ ] **Step 4: Run i18n test**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js
```

Expected: PASS.

---

### Task 2: Header And Home

**Files:**
- Modify: `src/components/RhHeader.vue`
- Modify: `src/pages/HomePage.vue`

- [ ] **Step 1: Update Header locale display**

In `RhHeader.vue`, derive the compact button label from locale metadata:

```js
const localeButtonLabel = computed(() => availableLocales.find((item) => item.lang === currentLocale.value)?.shortLabel || "EN");
```

Replace hard-coded accessibility labels and account modal copy with `t()` calls using keys from Task 1.

- [ ] **Step 2: Keep selector behavior unchanged**

Keep the existing `regionOpen` state and `setLocale(locale.lang)` click handler. Do not add routes or reloads.

- [ ] **Step 3: Update HomePage display copy**

Import `useI18n` in `HomePage.vue`:

```js
import { useI18n } from "../i18n.js";
const { t } = useI18n();
```

Replace:

- `Welcome to the World of RH` with `t("home.heroEyebrow")`
- hero subtitle with `t("home.heroSubtitle")`
- home grid aria label with `t("home.gridAria")`

Do not translate RH brand text or extraction data stored in `rhLayout.js`.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js
```

Expected: PASS.

---

### Task 3: Cart And Account Panel

**Files:**
- Modify: `src/components/CartDrawer.vue`
- Modify: `src/components/AuthTokenPanel.vue`

- [ ] **Step 1: Localize cart copy**

Use `t()` for:

- item count eyebrow: `t("cart.itemCount", { count: totals.quantity })`
- title: `t("cart.title")`
- remote/local bag helper
- empty state text and help
- quantity, remove, subtotal, delivery note, checkout

Keep product names, subtitles, prices, and `Yudao` source label unchanged.

- [ ] **Step 2: Localize account token panel**

Use `t()` for:

- aria label
- connected/not connected
- account label
- help paragraph
- access token label
- update/save/clear buttons

Keep the redacted token value unchanged.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
npm.cmd test -- tests/i18n.test.js tests/authSession.test.js
```

Expected: PASS.

---

### Task 4: Checkout And Orders

**Files:**
- Modify: `src/pages/CheckoutPage.vue`
- Modify: `src/pages/OrdersPage.vue`
- Modify: `tests/checkoutSession.test.js` only if needed

- [ ] **Step 1: Localize checkout copy**

Import `useI18n` in `CheckoutPage.vue` and use translation keys for page text, status messages, section headings, summary labels, and busy state.

The checkout mode copy should come from keys:

```js
const modeKey = computed(() => `checkout.mode.${mode.value}`);
```

Then render:

```vue
<h1>{{ t(`${modeKey}.title`) }}</h1>
<p>{{ t(`${modeKey}.message`) }}</p>
```

If dynamic key lookup is awkward, use a local mapping from mode to key strings, not localized message text.

- [ ] **Step 2: Keep checkout behavior intact**

Do not change:

- `loadCheckoutData`
- `submitOrder`
- `handlePrimaryAction`
- Yudao payload construction
- settlement math
- button disabled logic

- [ ] **Step 3: Localize orders copy**

Import `useI18n` in `OrdersPage.vue` and translate headings, loading copy, token-required helper, selected order labels, row labels, view action, empty state, and status prefix.

Keep order numbers, prices, item names, and API data unchanged.

- [ ] **Step 4: Run checkout and full tests**

Run:

```powershell
npm.cmd test -- tests/checkoutSession.test.js tests/i18n.test.js
npm.cmd test
```

Expected: PASS.

---

### Task 5: Integration Verification

**Files:**
- No production source ownership unless a defect is found.
- Optional output: `captures/local/language-selector-*.png`

- [ ] **Step 1: Build**

Run:

```powershell
npm.cmd run build
```

Expected: PASS.

- [ ] **Step 2: Browser verify language selector**

Start the dev server if needed:

```powershell
npm.cmd run dev -- --port 5173
```

Use Playwright or the in-app browser to verify:

- Header selector opens.
- English, 中文, and Français are visible.
- Click 中文 and confirm visible Chinese copy.
- Click Français and confirm visible French copy.
- Check `/checkout` and `/orders`.
- Check desktop and mobile widths for no horizontal overflow.

- [ ] **Step 3: Final report**

Report:

- Files changed.
- Test commands and build result.
- Browser verification result.
- Any screenshots captured.
