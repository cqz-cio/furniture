# Furniture Frontend I18n Full Coverage Design

**Date:** 2026-07-08

## Goal

Bring the furniture frontend to full visible-copy localization coverage for three languages:

- English: `en`
- Simplified Chinese: `zh-CN`
- French: `fr`

The implementation must scan the full frontend codebase for user-visible copy, move hard-coded text into the existing i18n system, replace corrupted Chinese strings, and ensure the language switcher updates all visible UI copy immediately. The brand name `Oakved` must always remain in English.

## Approved Scope

This design reflects the approved scope from the user:

- Include all user-visible page copy.
- Include obvious visible copy such as navigation labels, page headings, buttons, helper text, empty states, card copy, form labels, placeholders, checkout copy, cart copy, membership copy, account copy, and gift registry copy.
- Include visible text that appears over or alongside images when that text is rendered by frontend code.
- Exclude non-visible accessibility-only copy such as `aria-label`.
- Exclude SEO-only copy such as `document.title` and `meta description`.
- Exclude docs, tests, fixtures, mock files, and other non-runtime content from the translation target set.
- Keep `Oakved` in English in every language.
- Translate image-associated visible sentences into all three languages when those sentences are rendered by code.

## Key Findings From Project Exploration

### Existing i18n foundation already exists

The project already has a central localization module in:

- `src/i18n.js`

It currently provides:

- locale metadata
- locale persistence
- `t(key, params)` lookup and interpolation
- supported locales for `en`, `zh-CN`, and `fr`

### Current implementation is only partially localized

Some areas already call `t(...)`, especially:

- account flows
- membership flows
- parts of auth
- parts of checkout
- parts of orders

However, the codebase still contains many hard-coded visible strings in:

- landing pages
- gift registry pages
- product list and product detail pages
- cart drawer
- checkout page sections
- promo and header/footer-adjacent UI
- placeholder and development pages

### Chinese translation data contains mojibake

`src/i18n.js` contains many corrupted Chinese strings. These cannot be treated as valid translations and must be rewritten with valid UTF-8 Chinese copy.

### Most "image text" is not baked into assets

Exploration shows most visible hero or editorial sentences are rendered by Vue templates or JS-backed page data rather than embedded directly into image files. This keeps the implementation primarily in code and translation tables, not asset production.

## Non-Goals

This project does not include:

- backend translation services
- automatic translation generation from external APIs
- browser-language auto-detection
- SEO locale routing
- currency localization
- translating every business payload field returned from Yudao
- translating tests, docs, fixtures, or extraction data
- changing brand naming from `Oakved`

## Translation Rules

### Always translate

Translate user-visible interface copy such as:

- nav labels
- menu labels
- page hero headings
- page subheadings
- buttons and CTAs
- visible section labels
- explanatory copy
- empty states
- error helper copy shown to the user
- form labels
- placeholders
- step labels
- checkout/payment helper copy
- registry workflow labels
- visible product-supporting UI copy

### Never translate

Do not translate:

- `Oakved`
- system identifiers such as SPU/SKU values
- Yudao source names when they function as system/source labels
- backend field names not exposed as user-facing copy

### Translate carefully

These may appear on screen but should be handled case by case:

- product collection or merchandising names
- material names
- room edit labels
- imported content-like strings

Rule:

- translate surrounding interface copy
- keep content-like proper names stable when translation would harm recognition

## Architectural Decision

Use the existing centralized i18n system and expand it rather than introducing a new framework.

### Chosen approach

Centralize all visible UI text into `src/i18n.js`, then update pages and components to consume translation keys through `t(...)`.

### Why this approach

- It fits the current codebase.
- It preserves existing locale persistence and switching behavior.
- It avoids fragmenting translations across page files.
- It provides one source of truth for three-language coverage.
- It supports later maintenance better than page-local dictionaries.

### Rejected alternatives

#### Page-local translation objects

Rejected because translation ownership would become fragmented and difficult to audit.

#### Introducing a new i18n framework

Rejected because the current helper already covers the necessary behavior and the user requested completion rather than framework replacement.

## Target File Map

### Core translation ownership

- `src/i18n.js`

This file will remain the source of truth for:

- locale metadata
- translation tables
- fallback behavior
- interpolation

### Files likely requiring direct visible-copy localization changes

#### App-level and shared components

- `src/App.vue`
- `src/components/CartDrawer.vue`
- `src/components/RhHeader.vue`
- `src/components/RhPromoBanner.vue`
- `src/components/RhFooter.vue` if visible hard-coded copy is present

#### Landing and merchandising pages

- `src/pages/HomePage.vue`
- `src/pages/SalePage.vue`
- `src/pages/OutdoorPage.vue`
- `src/pages/TeenPage.vue`
- `src/pages/BabyChildPage.vue`
- `src/pages/BabyChildCategoryPage.vue`
- `src/pages/MissingExtractionPage.vue`

#### Product browsing and detail pages

- `src/pages/SofasPlpPage.vue`
- `src/pages/SofaPdpPage.vue`

#### Cart and checkout flows

- `src/pages/CheckoutPage.vue`
- `src/components/CartDrawer.vue`

#### Account, membership, and registry flows

- `src/pages/AccountPage.vue`
- `src/pages/GiftRegistryPage.vue`
- `src/pages/GiftRegistryFindPage.vue`
- `src/pages/GiftRegistryCreatePage.vue`
- `src/pages/GiftRegistryManagePage.vue`

### Files that may need only translation-key expansion

These already use i18n significantly, but their message coverage may need expansion:

- auth components
- membership pages
- orders page
- account profile/address/billing/wishlist pages

## Content Inventory Strategy

Before implementation, perform a full frontend search across:

- `src/pages`
- `src/components`
- `src/App.vue`

Focus on visible strings from:

- template text nodes
- button labels
- headings
- paragraph copy
- arrays or objects storing display strings
- computed UI text that currently uses raw literals

Each found string will be classified into one of these buckets:

- already localized and valid
- already localized but broken in Chinese
- hard-coded and needs a new key
- content-like label requiring preserve-vs-translate judgment

## Translation Table Design

Extend `src/i18n.js` with more structured namespaces instead of scattering unrelated keys.

Recommended grouping:

- `common`
- `header`
- `navigation`
- `home`
- `sale`
- `outdoor`
- `teen`
- `babyChild`
- `productList`
- `productDetail`
- `cart`
- `checkout`
- `membership`
- `orders`
- `giftRegistry`
- `account`
- `placeholder`

### Data-backed page copy

For page arrays or card collections, do not keep final display copy inline in page files. Use one of these patterns:

- store translation keys in the array
- derive the copy from a keyed i18n structure by item id

This is especially important for:

- `HomePage.vue` editorial modules
- `HomePage.vue` category edits
- `HomePage.vue` trust signals
- outdoor, teen, and baby-child collection cards
- product-detail support sections

## Chinese Text Repair Strategy

Mojibake values in `src/i18n.js` will be replaced with proper Simplified Chinese text instead of patched incrementally.

Rules:

- do not preserve corrupted text for compatibility
- replace with readable, product-appropriate Simplified Chinese
- keep terminology consistent across related flows

Examples of consistency targets:

- cart vs bag wording
- membership terminology
- checkout step names
- registry terminology
- account section naming

## Implementation Strategy

### Phase 1: Inventory and gap mapping

- search all visible frontend copy
- identify every hard-coded visible string
- identify every broken Chinese translation key
- identify pages already using `t(...)` but missing keys

### Phase 2: Expand and clean translation tables

- add missing key groups to `src/i18n.js`
- normalize wording across `en`, `zh-CN`, and `fr`
- replace mojibake Chinese values with valid text

### Phase 3: Connect pages and components to i18n

- replace hard-coded visible copy with `t(...)`
- update data arrays to reference keys rather than fixed English strings
- preserve `Oakved` in all languages

### Phase 4: Verify visible coverage

- run tests
- run production build
- inspect for remaining obvious hard-coded text by search

## Validation Plan

### Required command validation

- `npm.cmd test`
- `npm.cmd run build`

### Required codebase verification

Search again after implementation for remaining visible hard-coded strings in:

- `src/pages`
- `src/components`
- `src/App.vue`

### Functional verification goals

Confirm that:

- English renders cleanly
- Chinese renders without mojibake
- French renders without key fallbacks for the covered pages
- visible landing-page hero copy changes with locale
- cart and checkout visible copy changes with locale
- gift registry visible copy changes with locale
- account and membership visible copy remains readable
- `Oakved` stays in English everywhere

## Risks and Mitigations

### Risk: i18n file grows too large

Mitigation:

- keep strict namespace grouping
- place related keys together by page or domain
- use predictable key names

### Risk: content-like labels are over-translated

Mitigation:

- preserve proper nouns and content identifiers when needed
- translate only interface framing around them

### Risk: Chinese repair is inconsistent

Mitigation:

- rewrite by domain rather than piecemeal replacement
- keep shared terminology aligned across cart, checkout, membership, and registry flows

### Risk: hidden hard-coded copy remains in arrays

Mitigation:

- inspect JS arrays/objects, not only template literals
- explicitly convert content collections to keyed translation-backed structures

### Risk: tests fail because old text assumptions were encoded

Mitigation:

- update affected tests alongside implementation
- keep runtime behavior unchanged while changing display copy ownership

## Acceptance Criteria

The implementation is complete when all of the following are true:

- all visible targeted frontend copy is covered by `en`, `zh-CN`, and `fr`
- `src/i18n.js` contains no remaining corrupted Chinese values in covered areas
- supported locale switching still works immediately
- persisted locale behavior remains unchanged
- `Oakved` remains English in every locale
- visible image-adjacent copy switches language where rendered by code
- `npm.cmd test` passes
- `npm.cmd run build` passes

## Out of Scope Follow-Ups

Possible later improvements, but not part of this implementation:

- localizing accessibility-only strings
- localizing SEO metadata
- splitting `src/i18n.js` into modular locale files
- screenshot-based per-locale visual QA
- translating baked text inside external image assets if any are later discovered
