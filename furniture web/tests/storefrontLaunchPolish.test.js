import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { getMessage } from "../src/i18n.js";

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

  it("localizes product model UI helper labels at the page layer", () => {
    const plpSource = readSource("../src/pages/SofasPlpPage.vue");
    const pdpSource = readSource("../src/pages/SofaPdpPage.vue");

    expect(plpSource).toContain("productTypeLabel(option.value)");
    expect(plpSource).toContain("facetGroupLabel(group.key)");
    expect(plpSource).toContain("facetOptionLabel(group.key, option.value)");
    expect(plpSource).not.toContain("{{ option.label }}");
    expect(plpSource).not.toContain("{{ group.label }}");

    expect(pdpSource).toContain("priceLabel(");
    expect(pdpSource).toContain("membershipPromptText(");
    expect(pdpSource).toContain("fabricSelectorLabel(detail.fabricSelector)");
    expect(pdpSource).toContain("optionGroupLabel(group.key)");
    expect(pdpSource).toContain("accordionTitle(item.title)");
    expect(pdpSource).not.toContain("{{ detail.price.memberLabel }}");
    expect(pdpSource).not.toContain("{{ detail.fabricSelector.label }}");
    expect(pdpSource).not.toContain("{{ group.label }}");
    expect(pdpSource).not.toContain("<summary>{{ item.title }}</summary>");
  });

  it("surfaces active PLP filters before the grid", () => {
    const source = readSource("../src/pages/SofasPlpPage.vue");

    expect(source).toContain("activeFilterLabels");
    expect(source).toContain('class="product-active-filters"');
    expect(source).toContain('t("productList.resultSummary"');
    expect(source).toContain('t("productList.filters.clearAll")');
  });

  it("keeps Task 4 product UI translations complete for zh-CN and fr", () => {
    const noPlaceholderPaths = [
      "productList.typeOptions.nightstand",
      "productList.typeOptions.bedroomRoom",
      "productList.facetGroups.material",
      "productList.facetOptions.material.all",
      "productList.facetOptions.availability.lowStock",
      "productDetail.price.prefix",
      "productDetail.price.context",
      "productDetail.membershipPrompt.title",
      "productDetail.membershipPrompt.copy",
      "productDetail.fabricSelector.stockedFabrics",
      "productDetail.availability.title",
      "productDetail.optionGroups.labels.fill",
      "productDetail.purchaseAssurance.delivery.copy",
      "productDetail.relatedLinks.availableLeather",
      "productDetail.relatedLinks.viewBedroomSets",
      "productDetail.accordions.titles.details",
      "productDetail.accordions.rows.design",
      "productDetail.accordions.rows.delivery",
      "productDetail.accordions.rows.installation",
    ];
    const englishGuardrails = [
      [
        "productDetail.membershipPrompt.copy",
        "Sign in or join the Members Program to review eligible savings before checkout.",
      ],
      ["productDetail.optionGroups.helpers.bedSize", "Choose the bed frame size."],
      ["productDetail.optionGroups.helpers.top", "Stone and wood top options for Oakved dining filters."],
      ["productDetail.relatedLinks.availableLeather", "ALSO AVAILABLE IN LEATHER"],
      ["productDetail.relatedLinks.viewBedroomSets", "VIEW BEDROOM SETS"],
      ["productDetail.accordions.rows.design", "Design"],
      ["productDetail.accordions.rows.delivery", "Delivery"],
      ["productDetail.accordions.rows.installation", "Installation"],
    ];

    ["zh-CN", "fr"].forEach((lang) => {
      noPlaceholderPaths.forEach((path) => {
        const value = getMessage(path, lang);

        expect(value, `${lang} missing ${path}`).toBeTruthy();
        expect(value, `${lang} placeholder left in ${path}`).not.toMatch(/\?{2,}/);
      });

      englishGuardrails.forEach(([path, englishValue]) => {
        expect(getMessage(path, lang), `${lang} still matches English for ${path}`).not.toBe(englishValue);
      });

      expect(
        getMessage("productDetail.membershipPrompt.copy", lang),
        `${lang} still contains English residual for productDetail.membershipPrompt.copy`,
      ).not.toContain("Members");
    });
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
    expect(source).toContain('t("navigation.storefront.mobile.shopFurniture")');
    expect(source).toContain('t("navigation.storefront.mobile.service")');
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
