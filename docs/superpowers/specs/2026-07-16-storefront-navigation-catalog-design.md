# Storefront Navigation and Catalog Design

## Goal

Replace the current Oakved storefront navigation with the approved furniture navigation structure, create one shared brand catalog page, and prepare the navigation for English, Chinese, and French without duplicating its behavior or route definitions.

The first delivery is the English version. Chinese and French copy will be added only after the English layout has been reviewed from a running screenshot.

## Primary Navigation

The desktop primary navigation uses this exact order:

1. NEW
2. SHOP BY COLLECTIONS
3. BEDROOM
4. LIVING
5. DINING
6. BESPOKE
7. DECOR
8. SALE

`SHOP BY COLLECTIONS`, `BEDROOM`, `LIVING`, and `DINING` open dropdown menus. `NEW`, `BESPOKE`, `DECOR`, and `SALE` remain direct navigation entries until submenu content is explicitly supplied.

## Dropdown Contents

Every configured dropdown begins with the same catalog entry:

- Label: `OAKVED catalog`
- Route: `/catalog`

The remaining English entries are:

### SHOP BY COLLECTIONS

- The Solstice
- Halcyon
- Kindred

### BEDROOM

- Beds
- Headboard
- Nightstands
- Benches
- Dressers
- Chairs
- Side Tables
- Fabric Care
- Materials & Craftsmanship
- Sales

### LIVING

- Sofas
- Tables
- Consoles
- Sideboards
- Cabinets
- Benches
- Chairs
- Stools
- Fabric Care
- Materials & Craftsmanship
- Sales

### DINING

- Rectangular Tables
- Round & Oval Tables
- Bistro Tables
- Fabric Chairs
- Wood & Woven Chairs
- Bar & Counter Stools
- Upholstery Swatches
- Sales

The `DINING` dropdown therefore receives the previously omitted `OAKVED catalog` entry in its first position.

## Navigation Data Model

The navigation structure is defined once using stable item keys rather than translated labels. A shared catalog-entry constant or helper prepends `OAKVED catalog` to every configured dropdown. This enforces the rule that the first submenu entry links to the shared catalog page and prevents route drift between sections.

Desktop navigation and the mobile drawer consume the same primary-navigation data. Translation changes must not create separate navigation structures or separate catalog routes.

## Brand Catalog Page

The application adds one route and one page:

- Route: `/catalog`
- Page purpose: Oakved brand catalog / lookbook

All `OAKVED catalog` submenu entries navigate to this same page. No room-specific catalog pages or query variants are created.

The first English page uses the existing Oakved logo and sourcebook artwork already available in the project. It should look intentional and image-led rather than like an empty route or technical placeholder. The first version contains a strong catalog hero, a short English brand introduction, and responsive spacing consistent with the existing storefront.

## Localization

Navigation and catalog-page text use the existing localization system with stable keys.

Delivery order:

1. Add and visually verify English text.
2. After English approval, add Chinese text.
3. Add French text and verify all three language states.

Before Chinese and French copy is added, their missing new keys fall back to English so the new navigation remains complete and usable. Routes, dropdown membership, and menu order never vary by locale.

## Interaction and Responsive Behavior

- Clicking a primary item with a configured dropdown toggles that dropdown.
- Clicking `OAKVED catalog` closes the dropdown and opens `/catalog` through the existing local navigation behavior.
- Clicking outside the header closes an open dropdown, preserving current behavior.
- The desktop dropdown stays aligned beneath the selected primary item.
- The mobile drawer exposes the same primary items and nested submenu content in a touch-friendly form.
- Existing account, language, search, cart, and global-menu controls remain intact.

## Verification

Automated coverage will verify:

- the exact primary-navigation order;
- the exact dropdown contents;
- `OAKVED catalog` is first in every configured dropdown;
- every catalog entry uses `/catalog`;
- `DINING` includes the catalog entry;
- the `/catalog` route renders the catalog page;
- desktop and mobile navigation use the shared model;
- English localization keys resolve without raw-key output.

The English delivery will also be run locally and captured in screenshots showing the default header, at least one open dropdown, the `DINING` dropdown, and the shared catalog page.

## Scope

This change does not invent submenu content for `NEW`, `BESPOKE`, `DECOR`, or `SALE`, redesign unrelated storefront modules, or create multiple catalog pages. Chinese and French wording is deferred until the English visual review, but the shared localization structure is included in the English implementation.
