import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

const logoConsumers = [
  "../src/components/RhHeader.vue",
  "../src/pages/HomePage.vue",
  "../src/components/BrandEyebrow.vue",
  "../src/components/CartDrawer.vue",
  "../src/pages/CheckoutPage.vue",
];

describe("Oakved brand Logo", () => {
  it("centralizes every storefront Logo on the 2026 white transparent asset", async () => {
    const { OAKVED_LOGO_SRC } = await import("../src/config/brand.js");
    expect(OAKVED_LOGO_SRC).toBe("/assets/brand/oakved-logo-2026-white.png");

    for (const path of logoConsumers) {
      const source = readSource(path);
      expect(source, path).toContain("OAKVED_LOGO_SRC");
      expect(source, path).not.toMatch(/oakved-logo-(?:black|white)\.png/);
    }
  });

  it("keeps white-on-image and black-on-light presentation rules", () => {
    const css = readSource("../src/styles.css");
    expect(css).toContain(".brand-logo {\n  display: block;");
    expect(css).toContain("filter: invert(1);");
    expect(css).toContain(
      ".rh-header.is-overlay:not(:hover):not(:focus-within):not(.menu-is-open) .brand-logo {\n  filter: none;",
    );
    expect(css).toContain(".brand-eyebrow-dark .brand-eyebrow-logo {\n  filter: invert(1);");
    expect(css).toContain(".mobile-drawer-brand-logo {\n    display: block;");
    expect(css).toContain(".cart-brand-logo {\n  width:");
    expect(css).toContain(".rh-checkout-top img {\n  width:");
  });
});
