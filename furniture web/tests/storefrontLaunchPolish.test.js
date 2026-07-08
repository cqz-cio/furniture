import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("storefront launch polish", () => {
  it("keeps the PDP gallery stable, discoverable, and keyboard operable", () => {
    const source = readSource("../src/pages/SofaPdpPage.vue");

    expect(source).toContain('class="product-gallery-status"');
    expect(source).toContain("activeGalleryIndex + 1");
    expect(source).toContain("handleGalleryWheel");
    expect(source).toContain("@wheel.prevent");
    expect(source).toContain("@click=\"showNextGalleryItem\"");
    expect(source).toContain("<Transition name=\"product-gallery-fade\"");
    expect(source).toContain("@keydown.left.prevent");
    expect(source).toContain("@keydown.right.prevent");
    expect(source).toContain("&lsaquo;");
    expect(source).toContain("&rsaquo;");
  });

  it("renders PDP commerce modules from the detail model", () => {
    const source = readSource("../src/pages/SofaPdpPage.vue");

    expect(source).toContain("product-membership-callout");
    expect(source).toContain("detail.membershipPrompt");
    expect(source).toContain("product-assurance-grid");
    expect(source).toContain("detail.purchaseAssurance");
    expect(source).toContain("product-inspiration-section");
    expect(source).toContain("detail.roomInspiration");
    expect(source).toContain("shop-room-section");
    expect(source).toContain("detail.companionProducts");
    expect(source).toContain("product-companion-grid");
  });

  it("surfaces active PLP filters before the grid", () => {
    const source = readSource("../src/pages/SofasPlpPage.vue");

    expect(source).toContain("activeFilterLabels");
    expect(source).toContain('class="product-active-filters"');
    expect(source).toContain('t("productList.resultSummary"');
    expect(source).toContain('t("productList.filters.clearAll")');
  });

  it("respects reduced-motion settings for launch animations", () => {
    const styles = readSource("../src/styles.css");

    expect(styles).toContain("@media (prefers-reduced-motion: reduce)");
    expect(styles).toContain(".home-hero-picture");
    expect(styles).toContain("animation: none");
    expect(styles).toContain(".product-gallery-fade-enter-active");
  });

  it("groups mobile navigation into product and service sections", () => {
    const source = readSource("../src/components/RhHeader.vue");

    expect(source).toContain("mobileDrawerSections");
    expect(source).toContain('class="mobile-drawer-section"');
    expect(source).toContain("Shop Furniture");
    expect(source).toContain("Service");
  });

  it("shows loading skeletons and quick-add feedback on the PLP", () => {
    const source = readSource("../src/pages/SofasPlpPage.vue");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("skeletonCards");
    expect(source).toContain("product-grid-skeleton");
    expect(source).toContain("quickAddMessage");
    expect(source).toContain('role="status"');
    expect(styles).toContain(".product-card-skeleton");
    expect(styles).toContain(".product-quick-add-status");
  });

  it("updates product detail SEO from the loaded product", () => {
    const source = readSource("../src/pages/SofaPdpPage.vue");

    expect(source).toContain("applyProductSeo");
    expect(source).toContain("Product Details | Oakved");
    expect(source).toContain("document.title");
    expect(source).toContain('meta[name="description"]');
  });

  it("keeps launch-facing source free of mojibake and empty PDP links", () => {
    const sourceFiles = [
      "../src/components/RhHeader.vue",
      "../src/pages/SofaPdpPage.vue",
      "../src/pages/SofasPlpPage.vue",
      "../src/services/productDetailModel.js",
    ];

    sourceFiles.forEach((path) => {
      const source = readSource(path);
      expect(source, path).not.toMatch(/鈥|鈱|茅|鑼|�/);
    });
    expect(readSource("../src/services/productDetailModel.js")).not.toContain('href: "#"');
  });
});
