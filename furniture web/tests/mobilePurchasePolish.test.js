import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const css = () => readFileSync(new URL("../src/styles.css", import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("mobile purchase polish", () => {
  it("keeps mobile product and checkout surfaces compact and buyer-facing", () => {
    const source = css();

    expect(source).toContain("transition: opacity 260ms ease;");
    expect(source).toContain("transition: transform 260ms cubic-bezier(0.22, 1, 0.36, 1);");
    expect(source).toContain(".product-purchase-row {\n    display: grid;");
    expect(source).toContain(".checkout-flow-rail {\n    grid-template-columns: repeat(2, minmax(0, 1fr));");
    expect(source).toContain(".image-spec .spec-copy {\n    display: none;");
    expect(source).toContain(".cart-item .product-image-fallback,\n  .checkout-item .product-image-fallback");
  });

  it("keeps desktop and mobile presentation free of extraction overlays and aligned", () => {
    const source = css();
    const homePage = readFileSync(new URL("../src/pages/HomePage.vue", import.meta.url), "utf8");
    const firstResponsiveBlock = source.indexOf("@media (max-width: 760px)");
    const globalSpecHide = source.indexOf(".image-spec .spec-copy {\n  display: none;");

    expect(globalSpecHide).toBeGreaterThan(-1);
    expect(globalSpecHide).toBeLessThan(firstResponsiveBlock);
    expect(homePage).toContain('homeModuleCopy(index, "title")');
    expect(homePage).not.toContain("<small>{{ item.sourceLevel }}</small>");
    expect(homePage).not.toContain("sourcebook-overlay-slots");
    expect(source).toContain(".nav-link {\n  display: inline-flex;");
    expect(source).toContain("min-height: 36px;");
    expect(source).toContain(".home-hero-picture,\n  .home-hero-image {\n    height: min(600.08px, 153.87vw);");
    expect(source).toContain("margin: 0 auto;");
  });

  it("keeps sale images buyer-facing after extraction metadata is removed", () => {
    const salePage = readFileSync(new URL("../src/pages/SalePage.vue", import.meta.url), "utf8");
    const saleTile = readFileSync(new URL("../src/components/SaleCategoryTile.vue", import.meta.url), "utf8");
    const source = css();

    expect(salePage).toContain("sale-hero-copy");
    expect(salePage).toContain("sale-hero-picture");
    expect(salePage).not.toContain("ImageSpecPlaceholder");
    expect(saleTile).toContain("sale-tile-title");
    expect(saleTile).toContain("sale-tile-picture");
    expect(saleTile).not.toContain("ImageSpecPlaceholder");
    expect(source).toContain(".sale-hero-copy");
    expect(source).toContain(".sale-tile-title");
    expect(source).toContain(".sale-tile-picture {\n    height: min(228px, 58.47vw);");
  });

  it("makes mobile purchase controls thumb-friendly and keeps add-to-cart visible", () => {
    const pdp = readFileSync(new URL("../src/pages/SofaPdpPage.vue", import.meta.url), "utf8");
    const header = readFileSync(new URL("../src/components/RhHeader.vue", import.meta.url), "utf8");
    const source = css();

    expect(pdp).toContain("product-mobile-purchase-bar");
    expect(header).toContain("const searchOpen = ref(false)");
    expect(header).toContain("const toggleSearch = () => {");
    expect(header).toContain(':aria-expanded="searchOpen"');
    expect(header).toContain('class="mobile-search-panel"');
    expect(source).toContain(".product-mobile-purchase-bar {\n  display: none;");
    expect(source).toContain(".mobile-search-panel {\n  display: none;");
    expect(source).toContain(".mobile-search-panel {\n    display: grid;");
    expect(source).toContain(".mobile-search-panel input {\n    min-height: 44px;");
    expect(source).toContain(".header-topline .icon-button,\n  .header-actions .account-icon,\n  .header-actions .bag-icon {\n    width: 44px;");
    expect(source).toContain(".product-card-actions {\n    grid-template-columns: repeat(2, minmax(0, 1fr));");
    expect(source).toContain(".product-card-actions a,\n  .product-card-actions button {\n    min-height: 48px;");
    expect(source).toContain(".product-option-values button,\n  .product-related-links a,\n  .product-stock-link {\n    min-height: 44px;");
    expect(source).toContain("position: fixed;\n    left: 50%;");
  });

  it("keeps mobile sale/footer links tappable and desktop navigation within frame", () => {
    const source = css();

    expect(source).toContain(".icon-button,\n.header-actions button {\n  min-width: 40px;");
    expect(source).toContain(".menu-icon {\n  width: 40px;\n  height: 40px;");
    expect(source).toContain("gap: clamp(18px, 2.6vw, 38px);");
    expect(source).toContain(".nav-link {\n  display: inline-flex;");
    expect(source).toContain("min-height: 36px;");
    expect(source).toContain("font-size: 14px;\n  text-transform: uppercase;");
    expect(source).toContain(".sale-links {\n    display: grid;");
    expect(source).toContain("grid-template-columns: repeat(2, minmax(0, 1fr));");
    expect(source).toContain(".sale-links a {\n    min-height: 44px;");
    expect(source).toContain(".footer-links a {\n    min-height: 44px;");
    expect(source).toContain(".footer-region {\n    min-height: 44px;");
  });

  it("makes mobile modal, cart, and checkout entry actions easier to tap", () => {
    const source = css();

    expect(source).toContain(".account-modal-close {\n    width: 44px;");
    expect(source).toContain(".account-modal-close span {\n    left: 11px;");
    expect(source).toContain(".forgot-password,\n  .account-modal-links button {\n    min-height: 44px;");
    expect(source).toContain(".account-menu a {\n    min-height: 44px;");
    expect(source).toContain(".auth-check-row {\n    min-height: 44px;");
    expect(source).toContain(".account-sidebar a {\n    min-height: 44px;");
    expect(source).toContain(".account-form-actions button,\n  .billing-row-actions a,\n  .membership-billing-context-actions a {\n    min-height: 44px;");
    expect(source).toContain(".cart-close {\n    width: 44px;");
    expect(source).toContain(".cart-item-controls button {\n    min-height: 44px;");
    expect(source).toContain(".cart-item-controls input {\n    min-height: 44px;");
    expect(source).toContain(".checkout-auth-options a,\n.checkout-auth-options button {\n  width: fit-content;\n  min-height: 42px;");
    expect(source).toContain(".checkout-auth-options a,\n  .checkout-auth-options button {");
    expect(source).toContain("justify-content: center;\n    width: 100%;\n    min-height: 48px;");
  });

  it("keeps mobile service, registry, and trade actions tappable", () => {
    const source = css();

    expect(source).toContain(".membership-actions a,\n  .membership-agreement a,\n  .membership-two-column a,");
    expect(source).toContain(".registry-share-panel a,\n  .registry-result-list a,\n  .registry-owner-action-grid a,");
    expect(source).toContain(".trade-program-nav a,\n  .trade-text-button,\n  .trade-add-button,\n  .trade-sign-in-panel a {\n    min-height: 44px;");
    expect(source).toContain(".trade-consent-section label {\n    min-height: 44px;");
    expect(source).toContain(".trade-consent-section input {\n    width: 16px;");
  });

  it("polishes tablet navigation, body links, and purchase bar spacing", () => {
    const source = css();
    const desktopGlobalMenu = source.indexOf(".global-menu {\n  position: static;");
    const tabletMedia = source.indexOf("@media (min-width: 761px) and (max-width: 900px)");
    const generalMobileMedia = source.indexOf("@media (max-width: 900px)");
    const tabletGlobalMenuOverride = source.indexOf(".global-menu,\n  .category-mega-menu {\n    display: none;", tabletMedia);

    expect(source).toContain("@media (min-width: 761px) and (max-width: 900px) {\n  .primary-nav,\n  .global-menu,\n  .category-mega-menu {\n    display: none;");
    expect(source).toContain(".global-menu,\n  .category-mega-menu {\n    display: none;");
    expect(source).toContain(".country-button,\n  .search-input {\n    display: none;");
    expect(source).toContain(".mobile-search-panel {\n    display: grid;");
    expect(tabletGlobalMenuOverride).toBeGreaterThan(desktopGlobalMenu);
    expect(tabletMedia).toBeLessThan(generalMobileMedia);
    expect(source).toContain(".trade-page-header a,\n  .membership-page p a,\n  .registry-workflow-page p a {\n    min-height: 44px;");
    expect(source).toContain(".product-mobile-purchase-bar {\n    max-width: 640px;");
    expect(source).toContain("left: 50%;\n    right: auto;\n    transform: translateX(-50%);");
    expect(source).toContain("@media (max-width: 540px) {\n  .product-mobile-purchase-bar {\n    max-width: none;");
  });

  it("keeps phone drawer geometry separate from tablet navigation", () => {
    const source = css();
    const phoneMedia = source.indexOf("@media (max-width: 760px)");
    const tabletMedia = source.indexOf("@media (min-width: 761px) and (max-width: 900px)");
    const generalMobileMedia = source.indexOf("@media (max-width: 900px)");
    const phoneBlock = source.slice(phoneMedia, tabletMedia);
    const tabletBlock = source.slice(tabletMedia, generalMobileMedia);
    const generalBlock = source.slice(generalMobileMedia);

    expect(phoneBlock).toContain(".mobile-drawer-layer {\n    position: fixed;\n    left: 0;\n    right: 0;\n    top: 76px;");
    expect(phoneBlock).toContain("min-height: calc(100vh - 76px);");
    expect(tabletBlock).toContain(".mobile-drawer-layer {\n    position: fixed;\n    left: 0;\n    right: 0;\n    top: 82px;");
    expect(tabletBlock).toContain("min-height: calc(100vh - 82px);");
    expect(generalBlock).not.toContain(".mobile-drawer-layer {\n    position: fixed;");
  });

  it("caps measured mobile imagery at the 390px reference while scaling down on narrow phones", () => {
    const source = css();

    expect(source).toContain("height: min(600.08px, 153.87vw);");
    expect(source).toContain("min-height: min(600.08px, 153.87vw);");
    expect(source).toContain("height: min(600.08px, 153.87vw);");
    expect(source).toContain("height: min(228px, 58.47vw);");
    expect(source).toContain("height: min(523.42px, 134.21vw);");
  });
});
