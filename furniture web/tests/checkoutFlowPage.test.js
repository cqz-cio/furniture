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

  it("renders the RH-aligned checkout planning sections", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("checkout-flow-rail");
    expect(source).toContain("checkout-custom-notice");
    expect(source).toContain("checkout-address-verification");
    expect(source).toContain("checkout-payment-panel");
    expect(source).toContain("checkout-terms-panel");
    expect(source).toContain("checkout-delivery-notes");
  });

  it("styles the checkout flow planning surfaces", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".checkout-flow-rail");
    expect(source).toContain(".checkout-flow-card");
    expect(source).toContain(".checkout-payment-panel");
    expect(source).toContain(".checkout-delivery-notes");
  });
});
