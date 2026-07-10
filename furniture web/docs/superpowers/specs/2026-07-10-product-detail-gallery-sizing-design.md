# Product Detail Gallery Sizing Design

## Goal

Reduce the oversized product gallery on desktop product-detail pages, align it with the adjacent product information, and apply the same layout to every product detail page that uses the shared component.

## Scope

- Update the shared product-detail gallery layout only.
- Preserve the current gallery controls, image switching, thumbnails, copy, pricing, and purchase behavior.
- Apply the result to every product rendered by `SofaPdpPage.vue`.

## Layout Design

- Keep the gallery and information panel in the existing two-column desktop grid.
- Align the top edge of the gallery with the top edge of the product information panel.
- Replace the current desktop height range of 520-680 px with a responsive 420-520 px range.
- Keep the gallery width constrained by its existing grid column.
- Continue using `object-fit: contain` so complete product images remain visible without cropping.
- Keep the current 360 px mobile gallery height at widths up to 900 px.

## Responsive Behavior

- Desktop and large tablet widths above 900 px use the 420-520 px responsive gallery height.
- Widths up to 900 px retain the single-column layout and 360 px gallery height.
- Existing thumbnails, status text, and supporting content remain directly below the resized gallery.

## Implementation Boundary

The change should be made in the shared `.product-gallery-main` rule in `src/styles.css`. No product-specific dimensions or duplicated detail-page variants will be introduced.

## Verification

- Run the relevant automated tests and production build.
- Open at least two different product detail pages at a desktop viewport and confirm the same gallery size and top alignment.
- Check a mobile viewport to confirm the existing single-column gallery remains usable.
- Compare the desktop result with the supplied screenshot and confirm the gallery no longer dominates the information panel.
