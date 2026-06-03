import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("membership UI integration", () => {
  it("lets the enrollment page emit the annual membership product into the cart", () => {
    const source = readSource("../src/pages/MembershipEnrollmentPage.vue");

    expect(source).toContain("ANNUAL_MEMBERSHIP_PRODUCT");
    expect(source).toContain('defineEmits(["add-to-cart"])');
    expect(source).toContain("emit(\"add-to-cart\", ANNUAL_MEMBERSHIP_PRODUCT");
  });

  it("routes membership enrollment add-to-cart through App.vue", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("@add-to-cart=\"addToCart\"");
    expect(source).toContain("MembershipEnrollmentPage");
  });

  it("shows membership pricing and notices in the cart drawer", () => {
    const source = readSource("../src/components/CartDrawer.vue");

    expect(source).toContain("getMembershipPricing");
    expect(source).toContain("getMembershipCartNotice");
    expect(source).toContain("membershipSubtotal");
    expect(source).toContain("memberDiscount");
  });

  it("uses membership pricing in checkout summary totals", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("getMembershipPricing");
    expect(source).toContain("memberDiscount");
    expect(source).toContain("displayEstimatedTotal");
  });
});
