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

  it("opens focused wood furniture category menus only after a nav click", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");

    expect(source).toContain("const handleNavClick = (label) => {");
    expect(source).toContain("woodFurnitureDropdownLabels.includes(label)");
    expect(source).toContain("activeDropdown.value = activeDropdown.value === label ? \"\" : label");
    expect(source).toContain("activeMegaItem.value = \"\"");
    expect(source).toContain("woodFurnitureMegaMenus[activeDropdown.value]");
    expect(source).toContain("const activateMegaItem = (label) => {");
    expect(source).toContain("category-mega-secondary");
    expect(source).toContain("category-mega-link");
    expect(source).not.toContain("previewMegaItem");
    expect(source).not.toContain("@mouseenter=\"previewMegaItem(item.label)\"");
    expect(css).toContain(".category-mega-link");
    expect(css).toContain(".category-mega-link.active");
    expect(css).not.toContain("font: inherit;\n  padding: 0;\n  text-align: left;");
    expect(source).toContain('ref="headerRef"');
    expect(source).toContain('document.addEventListener("pointerdown", handleDocumentPointerDown)');
    expect(source).toContain('document.removeEventListener("pointerdown", handleDocumentPointerDown)');
    expect(source).toContain('@click="handleNavClick(item.label)"');
    expect(source).not.toContain('@mouseleave="hideDropdown"');
    expect(source).not.toContain("@mouseenter=\"showDropdown");
    expect(source).not.toContain("@focus=\"showDropdown");
    expect(source).not.toContain("const showDropdown");
  });

  it("positions the category menu from the clicked primary nav item", () => {
    const source = readSource("../src/components/RhHeader.vue");
    const css = readSource("../src/styles.css");

    expect(source).toContain("const navButtonRefs = ref({})");
    expect(source).toContain("const dropdownPositionStyle = computed");
    expect(source).toContain("updateDropdownPosition(label)");
    expect(source).toContain(':ref="(element) => setNavButtonRef(item.label, element)"');
    expect(source).toContain(':style="dropdownPositionStyle"');
    expect(css).toContain("left: var(--category-menu-left, 80px);");
    expect(css).not.toContain(".category-mega-menu.is-sale-menu {\n  left: auto;");
  });
});
