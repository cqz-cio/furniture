import { describe, expect, it } from "vitest";
import {
  CHECKOUT_STEP_KEYS,
  buildCheckoutFlow,
  canPlaceCheckoutOrder,
  getAddressVerification,
  hasCustomItems,
} from "../src/services/checkoutFlow.js";

const regularItem = { skuId: "sofa-1", quantity: 1, price: 3200, name: "Cloud Sofa", source: "demo" };
const customItem = {
  skuId: "custom-sectional",
  quantity: 1,
  price: 7200,
  name: "Custom Sectional",
  source: "demo",
  customization: { fabric: "Italian Textured Weave", configuration: "Left Chaise" },
};

describe("checkout flow model", () => {
  it("defines the Oakved checkout step order", () => {
    expect(CHECKOUT_STEP_KEYS).toEqual([
      "details",
      "custom-check",
      "shipping-address",
      "address-verification",
      "payment",
      "review",
      "place-order",
      "delivery-notes",
    ]);
  });

  it("detects custom merchandise that requires a custom order notice", () => {
    expect(hasCustomItems([regularItem])).toBe(false);
    expect(hasCustomItems([customItem])).toBe(true);
    expect(hasCustomItems([{ ...regularItem, isCustom: true }])).toBe(true);
  });

  it("builds custom notice and address issue steps before payment", () => {
    const flow = buildCheckoutFlow([customItem], {
      address: { line1: "12 Main", postalCode: "02116" },
      termsAccepted: false,
    });

    expect(flow.steps.map((step) => step.key)).toEqual(CHECKOUT_STEP_KEYS);
    expect(flow.customNotice.required).toBe(true);
    expect(flow.addressVerification.status).toBe("issue");
    expect(flow.addressVerification.suggestedAddress.postalCode).toBe("02116-0000");
    expect(flow.readyForPayment).toBe(false);
  });

  it("keeps payment and place order open while address verification is unresolved", () => {
    const flow = buildCheckoutFlow([regularItem], {
      address: { line1: "12 Main", postalCode: "02116" },
      paymentMethod: "card",
      cardComplete: true,
      termsAccepted: true,
    });

    expect(flow.steps.find((step) => step.key === "payment").status).toBe("open");
    expect(flow.steps.find((step) => step.key === "place-order").status).toBe("open");
    expect(canPlaceCheckoutOrder(flow)).toBe(false);
  });

  it("allows payment and order placement after a verified address and terms agreement", () => {
    const flow = buildCheckoutFlow([regularItem], {
      address: { line1: "12 Main Street", city: "Boston", region: "MA", postalCode: "02116-0000" },
      useSuggestedAddress: true,
      paymentMethod: "card",
      cardComplete: true,
      termsAccepted: true,
    });

    expect(flow.addressVerification.status).toBe("verified");
    expect(flow.readyForPayment).toBe(true);
    expect(canPlaceCheckoutOrder(flow)).toBe(true);
  });

  it("returns normal verification when an address is already specific enough", () => {
    expect(
      getAddressVerification({ line1: "48 Gallery Lane", city: "New York", region: "NY", postalCode: "10013-1100" }),
    ).toMatchObject({
      status: "verified",
      issue: "",
    });
  });
});
