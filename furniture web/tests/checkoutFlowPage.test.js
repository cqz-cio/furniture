import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("checkout flow page planning", () => {
  it("uses the checkout flow model from CheckoutPage", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("buildCheckoutFlow");
    expect(source).toContain("canPlaceCheckoutOrder");
    expect(source).toContain("checkoutFlow");
  });

  it("renders the Oakved checkout planning sections", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("checkoutServicePromises");
    expect(source).toContain("checkout-service-strip");
    expect(source).toContain("checkout-flow-rail");
    expect(source).toContain("checkout-custom-notice");
    expect(source).toContain("checkout-address-verification");
    expect(source).toContain("checkout-payment-panel");
    expect(source).toContain("checkout-terms-panel");
    expect(source).toContain("checkout-delivery-notes");
  });

  it("styles the checkout flow planning surfaces", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".checkout-service-strip");
    expect(source).toContain(".checkout-summary-note");
    expect(source).toContain(".checkout-flow-rail");
    expect(source).toContain(".checkout-flow-card");
    expect(source).toContain(".checkout-payment-panel");
    expect(source).toContain(".checkout-delivery-notes");
  });

  it("keeps launch-critical cart service cues visible before checkout", () => {
    const source = readSource("../src/components/CartDrawer.vue");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("cartServicePromises");
    expect(source).toContain("cart-assurance-strip");
    expect(source).toContain("cart-item-specs");
    expect(styles).toContain(".cart-assurance-strip");
    expect(styles).toContain(".cart-item-specs");
  });
});
