import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("membership UI integration", () => {
  it("requires a real account before membership enrollment instead of adding preview membership to the cart", () => {
    const source = readSource("../src/pages/MembershipEnrollmentPage.vue");

    expect(source).toContain("createYudaoMembershipCheckoutIntent");
    expect(source).not.toContain("activateAnnualMembership");
    expect(source).toContain("readYudaoToken");
    expect(source).toContain('statusMessageKey.value = "membership.enrollment.signInRequired"');
    expect(source).toContain('statusMessageKey.value = "membership.enrollment.checkoutReady"');
    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).not.toContain("ANNUAL_MEMBERSHIP_PRODUCT");
    expect(source).not.toContain('defineEmits(["add-to-cart"])');
    expect(source).not.toContain('emit("add-to-cart"');
    expect(source).not.toContain("membership.enrollment.previewAdded");
    expect(source).not.toContain("membership.enrollment.activated");
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
    expect(source).toContain("membershipPricing.discountRate");
    expect(source).toContain('t("cart.membership.description")');
    expect(source).toContain('t("cart.membership.checkoutNotice")');
    expect(source).not.toContain("memberDiscount || Math.round");
    expect(source).not.toContain("membershipSubtotal || 200");
    expect(source).not.toContain("Save 30%");
    expect(source).not.toContain("/ 0.7");
  });

  it("animates the cart drawer as a full-screen shopping bag overlay", () => {
    const drawerSource = readSource("../src/components/CartDrawer.vue");
    const styles = readSource("../src/styles.css");

    expect(drawerSource).toContain('<Transition name="cart-drawer-slide">');
    expect(drawerSource).toContain("cart-full-header");
    expect(drawerSource).toContain("cart-full-main");
    expect(styles).toContain(".cart-drawer-slide-enter-active");
    expect(styles).toContain("transition: opacity 260ms ease");
    expect(styles).toContain("transform: translateY(12px)");
    expect(styles).toContain("transition: transform 260ms cubic-bezier");
    expect(styles).toContain("grid-template-rows: auto minmax(0, 1fr)");
  });

  it("uses membership pricing in checkout summary totals", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("getMembershipPricing");
    expect(source).toContain("memberDiscount");
    expect(source).toContain("customItemDeposit");
    expect(source).toContain("displayEstimatedTotal");
    expect(source).not.toContain("memberDiscount || Math.round");
    expect(source).not.toContain("membershipSubtotal || 200");
    expect(source).not.toContain("(displayItemTotal || 0) / 2");
  });

  it("keeps guest checkout restriction copy limited to disabled membership checkout", () => {
    const source = readSource("../src/pages/CheckoutAuthPage.vue");

    expect(source).toContain('v-if="option.disabled"');
    expect(source).not.toContain("v-else-if=\"option.disabledForMembership\"");
  });

  it("adds membership eligibility review to account order detail", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("getMembershipEligibilityItemsFromOrderItems");
    expect(source).toContain("getMembershipEligibilityReview");
    expect(source).toContain("orderMembershipEligibilityReview");
    expect(source).toContain("order-membership-eligibility");
    expect(source).toContain("membership-eligibility-row");
  });

  it("surfaces member savings in the order history list", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("getOrderMembershipReview");
    expect(source).toContain("getOrderMembershipSavingsLabel");
    expect(source).toContain("orders.memberSavings");
    expect(source).toContain("order-member-savings");
  });
});
