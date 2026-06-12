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

  it("requires explicit checkout confirmations instead of hard-coded completed state", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("customNoticeAccepted = ref(false)");
    expect(source).toContain("cardComplete = ref(false)");
    expect(source).toContain("termsAccepted = ref(false)");
    expect(source).toContain("customNoticeAccepted.value");
    expect(source).toContain("cardComplete.value");
    expect(source).toContain("termsAccepted.value");
    expect(source).not.toContain("customNoticeAccepted: true");
    expect(source).not.toContain("cardComplete: true");
    expect(source).not.toContain("termsAccepted: true");
  });

  it("uses checkout i18n keys for confirmation and status copy", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('t("checkout.confirm.customNotice")');
    expect(source).toContain('t("checkout.confirm.useSuggestedAddress")');
    expect(source).toContain('t("checkout.confirm.paymentReady")');
    expect(source).toContain('t("checkout.confirm.termsAccepted")');
    expect(source).toContain("checkoutStepLabelKeys");
    expect(source).toContain("getCheckoutErrorKey");
    expect(source).toContain("getCheckoutRecoveryAction");
    expect(source).toContain('"checkout.errors.loadUnavailable"');
    expect(source).toContain('"checkout.errors.orderUnavailable"');
    expect(source).toContain('"checkout.errors.noAddress"');
    expect(source).toContain("checkoutRecoveryAction");
    expect(source).not.toContain("error === t('checkout.errors.noAddress')");
    expect(source).not.toContain("Payment details are ready for secure order submission.");
    expect(source).not.toContain("I agree to the checkout terms and order review requirements.");
    expect(source).not.toContain("Checkout service is unavailable. Please try again later.");
  });

  it("gates downstream checkout panels behind a real verified address", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("const hasCheckoutAddress = computed");
    expect(source).toContain("const canReviewPayment = computed");
    expect(source).toContain("return null;");
    expect(source).toContain('v-if="canReviewPayment" class="checkout-payment-panel"');
    expect(source).toContain('v-if="canReviewPayment" class="checkout-terms-panel"');
    expect(source).toContain('v-if="canReviewPayment" class="checkout-delivery-notes"');
    expect(source).not.toContain('line1: "12 Main"');
    expect(source).not.toContain('city: "Boston"');
  });

  it("offers simulated payment method choices before payment confirmation", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('const paymentMethod = ref("card")');
    expect(source).toContain("const paymentMethodOptions =");
    expect(source).toContain('value: "gift-card"');
    expect(source).toContain('value: "member-credit"');
    expect(source).toContain("paymentMethod.value");
    expect(source).toContain('v-for="option in paymentMethodOptions"');
    expect(source).toContain('v-model="paymentMethod"');
    expect(source).toContain("checkout-payment-options");
    expect(source).toContain("checkout-payment-option");
    expect(source).not.toContain('paymentMethod: "card"');
  });

  it("styles the checkout flow planning surfaces", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".checkout-flow-rail");
    expect(source).toContain(".checkout-flow-card");
    expect(source).toContain(".checkout-payment-panel");
    expect(source).toContain(".checkout-payment-options");
    expect(source).toContain(".checkout-delivery-notes");
  });
});
