# Language Selector I18n Spec

## Goal

Build a storefront language selector with English, Simplified Chinese, and French display modes. The feature should let the current RH-style furniture pages present localized navigation, core page copy, account/cart copy, checkout copy, and order copy without changing commerce service behavior.

## Current State

- `src/i18n.js` already owns locale state, locale persistence, and the `t(key)` helper.
- `src/components/RhHeader.vue` already renders a language button and reads `availableLocales`.
- Only a small subset of cart/product copy currently uses `t()`.
- Several strings in `src/i18n.js` are mojibake and must be replaced with valid UTF-8 text.
- Header, home, cart, account, checkout, and orders still contain hard-coded display copy.

## Requirements

1. The language selector must expose exactly these options:
   - English, locale code `en`, compact label `EN`
   - Chinese, locale code `zh-CN`, compact label `中文`
   - French, locale code `fr`, compact label `FR`
2. Selecting a language must update visible page copy immediately.
3. The selected language must persist in `localStorage` under the existing key `furniture-web-locale`.
4. `document.documentElement.lang` must be updated after locale changes.
5. Unsupported saved locale values must fall back to English.
6. Brand and collection identity should stay RH-like:
   - Keep `RH`, product names, collection names, and source labels such as `Yudao` as-is unless they are pure UI copy.
   - Translate UI labels, instructions, empty states, buttons, and page explanations.
7. The first implementation should cover:
   - Header language selector, menu aria labels, account modal, mobile region label
   - Home hero and module metadata labels
   - Cart drawer empty state, totals, checkout button, item count copy
   - Auth token panel labels and actions
   - Checkout page headings, mode messages, summary rows, empty note, busy state
   - Orders page headings, loading/empty/error helper copy, row labels
8. The implementation must not call a remote translation service.
9. The implementation must not add a large i18n framework unless the existing helper becomes insufficient.

## Non-Goals

- No backend translation management.
- No translated product catalog content from Yudao.
- No currency, tax, or region-specific price localization.
- No SEO alternate-language routing.
- No browser language auto-detection for this phase.

## Code Boundaries

### `src/i18n.js`

Owns:
- Locale metadata.
- Translation messages.
- Locale persistence.
- Safe fallback behavior.
- `t(key, params)` interpolation for simple values such as `{count}`.

Does not own:
- Commerce mode logic.
- Checkout pricing logic.
- Order API behavior.
- Component layout.

### Vue Components

Own:
- Calling `t()` for display copy.
- Choosing which existing UI state determines which translation key is shown.
- Keeping local layout and visual classes.

Do not own:
- Translation table structure beyond using keys.
- Locale persistence.
- Business service changes.

### Service Modules

Do not translate user-facing copy except existing pure presentation helpers that already return UI strings. For this module, prefer moving display copy into components or mapping helper keys instead of adding more localized text to commerce services.

## Proposed Translation API

Keep the existing composable:

```js
const { availableLocales, currentLocale, setLocale, t } = useI18n();
```

Extend `t()` to accept optional interpolation:

```js
t("cart.itemCount", { count: totals.quantity });
```

The helper should return the English message or key if a locale misses a translation.

## Testing Requirements

1. Unit test locale metadata includes English, Chinese, and French.
2. Unit test locale fallback rejects unsupported locale values.
3. Unit test locale persistence writes the selected language.
4. Unit test interpolation works.
5. Component or browser-level verification must confirm:
   - Header selector shows all three language options.
   - Selecting Chinese updates visible copy.
   - Selecting French updates visible copy.
   - Checkout and cart remain responsive without horizontal overflow.

## Acceptance Criteria

- `npm.cmd test` passes.
- `npm.cmd run build` passes.
- Local page verification shows English, Chinese, and French versions for core storefront pages.
- The language selector remains visually aligned with the RH-style header shown in the reference image.
- No service API or backend behavior changes are introduced.
