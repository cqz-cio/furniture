import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("header language menu", () => {
  it("only renders the supported locale options", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");

    expect(source).toContain('v-for="locale in availableLocales"');
    expect(source).toContain(':aria-label="t(\'common.language\')"');
    expect(source).not.toContain("regionOptions");
    expect(source).not.toContain("selectedCountry");
    expect(source).not.toContain("region-input-row");
    expect(css).not.toContain(".region-input-row");
    expect(css).not.toContain(".region-languages");
  });

  it("keeps the language menu above the navigation and dismisses it from outside clicks", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");
    const headerRegionOpenStyles = css.match(/\.rh-header\.region-is-open \.header-topline \{[\s\S]*?\n\}/)?.[0] || "";

    expect(source).toContain("const regionSwitcherRef = ref(null)");
    expect(source).toContain('ref="regionSwitcherRef"');
    expect(source).toContain("'region-is-open': regionOpen");
    expect(source).toContain("if (regionOpen.value && !regionSwitcherRef.value?.contains(event.target)) {");
    expect(source).toContain("regionOpen.value = false;");
    expect(headerRegionOpenStyles).toContain("z-index: 3;");
  });

  it("opens the desktop global menu as a full-screen fading overlay", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");
    const headerOpenStyles = css.match(/\.rh-header\.menu-is-open \{[\s\S]*?\n\}/)?.[0] || "";
    const menuStyles = css.match(/\.global-menu \{[\s\S]*?\n\}/)?.[0] || "";

    expect(source).toContain("watch(menuOpen, setBodyMenuState)");
    expect(source).toContain('document.body.classList.toggle("rh-menu-open", isOpen)');
    expect(css).toContain("--rh-header-reveal-duration: 360ms;");
    expect(css).toContain("body.rh-menu-open");
    expect(headerOpenStyles).toContain("position: fixed;");
    expect(headerOpenStyles).toContain("inset: 0;");
    expect(headerOpenStyles).toContain("overflow-y: auto;");
    expect(css).toContain(".rh-header.is-overlay.menu-is-open");
    expect(menuStyles).toContain("position: static;");
    expect(menuStyles).toContain("min-height: calc(100dvh - 136px);");
    expect(menuStyles).toContain("animation: rhGlobalMenuReveal var(--rh-header-reveal-duration)");
    expect(css).toContain("@keyframes rhGlobalMenuReveal");
  });

  it("fades the overlay header background in when the top navigation is touched", () => {
    const appSource = readSource("../src/App.vue");
    const css = readSource("../src/styles.css");
    const overlayStyles = css.match(/\.rh-header\.is-overlay \{[\s\S]*?\n\}/)?.[0] || "";

    expect(appSource).toContain('const usesOverlayHeader = computed(() => ["home", "sale"].includes(currentPage.value))');
    expect(appSource).toContain(':overlay="usesOverlayHeader"');
    expect(overlayStyles).toContain("border-bottom: 0;");
    expect(overlayStyles).toContain("background: transparent;");
    expect(overlayStyles).not.toContain("linear-gradient");
    expect(css).toContain(".rh-header::before");
    expect(css).toContain("transition: opacity var(--rh-header-reveal-duration) ease;");
    expect(css).toContain(".rh-header.is-overlay::before");
    expect(css).toContain(".rh-header.is-overlay:hover::before");
    expect(css).toContain(".rh-header.is-overlay:focus-within::before");
  });

  it("opens configured storefront category menus only after a nav click", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");

    expect(source).toContain("const handleNavClick = (item) => {");
    expect(source).toContain("hasStorefrontDropdown(item)");
    expect(source).toContain("activeDropdown.value = activeDropdown.value === item.key ? \"\" : item.key");
    expect(source).toContain("activeMegaItem.value = \"\"");
    expect(source).toContain("storefrontDropdownMenus[activeDropdown.value]");
    expect(source).toContain("category-mega-link");
    expect(css).toContain(".category-mega-link");
    expect(source).toContain('ref="headerRef"');
    expect(source).toContain('document.addEventListener("pointerdown", handleDocumentPointerDown)');
    expect(source).toContain('document.removeEventListener("pointerdown", handleDocumentPointerDown)');
    expect(source).toContain('@click="handleNavClick(item)"');
    expect(source).not.toContain('@mouseleave="hideDropdown"');
  });

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

  it("positions the category menu from the clicked primary nav item", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");

    expect(source).toContain("const navButtonRefs = ref({})");
    expect(source).toContain("const dropdownPositionStyle = computed");
    expect(source).toContain("updateDropdownPosition(item.key)");
    expect(source).toContain(':ref="(element) => setNavButtonRef(item.key || item.label, element)"');
    expect(source).toContain(':style="dropdownPositionStyle"');
    expect(css).toContain("left: var(--category-menu-left, 80px);");
    expect(css).not.toContain(".category-mega-menu.is-sale-menu {\n  left: auto;");
  });

  it("remounts same-page product listings when the navigation query changes", () => {
    const appSource = readSource("../src/App.vue");
    const plpSource = readSource("../src/pages/SofasPlpPage.vue");

    expect(appSource).toContain("const routeSignature = ref");
    expect(appSource).toContain("routeSignature.value = nextPath;");
    expect(appSource).toContain(':key="`${currentPage}:${routeSignature}`"');
    expect(plpSource).toContain("resolveProductListingQuery");
    expect(plpSource).toContain("buildProductListingModel");
    expect(plpSource).toContain('window.addEventListener("oakved:navigation", syncListingQueryFromLocation)');
  });
});
