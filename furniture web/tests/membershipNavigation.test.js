import { describe, expect, it } from "vitest";
import {
  getCheckoutAuthOptions,
  getCheckoutEntryRoute,
  accountMenuItems,
  checkoutAuthOptions,
  getMembershipJoinTarget,
  membershipRoutes,
} from "../src/services/membershipNavigation.js";

describe("membership navigation model", () => {
  it("defines RH-aligned membership and account routes", () => {
    expect(membershipRoutes.membership).toBe("/membership");
    expect(membershipRoutes.membershipEnrollment).toBe("/membership/enrollment");
    expect(membershipRoutes.membershipTerms).toBe("/membership/terms");
    expect(membershipRoutes.membershipFaqs).toBe("/membership/faqs");
    expect(membershipRoutes.accountMembership).toBe("/account/membership");
    expect(membershipRoutes.accountOrders).toBe("/account/orders");
    expect(membershipRoutes.accountGiftRegistry).toBe("/gift-registry");
    expect(membershipRoutes.checkoutAuth).toBe("/checkout/auth");
    expect(membershipRoutes.giftRegistry).toBe("/gift-registry");
  });

  it("keeps the My Account menu order aligned with RH", () => {
    expect(accountMenuItems.map((item) => item.label)).toEqual([
      "Account Profile",
      "Address Book",
      "Membership",
      "Order History",
      "Billing History",
      "Payment Methods",
      "Wish List",
      "Gift Registry",
    ]);
  });

  it("uses three checkout auth choices and blocks guest membership purchase", () => {
    expect(checkoutAuthOptions.map((option) => option.key)).toEqual(["sign-in", "create-account", "guest"]);
    expect(checkoutAuthOptions.find((option) => option.key === "sign-in").href).toBe("/account?return=/checkout");
    expect(checkoutAuthOptions.find((option) => option.key === "create-account").href).toBe(
      "/account?mode=create&return=/checkout",
    );
    expect(checkoutAuthOptions.find((option) => option.key === "guest").href).toBe("/checkout?guest=true");
    expect(checkoutAuthOptions.find((option) => option.key === "guest").disabledForMembership).toBe(true);
  });

  it("routes membership join actions by login and member state", () => {
    expect(getMembershipJoinTarget({ signedIn: false, memberStatus: "guest" })).toBe("/checkout/auth?intent=membership");
    expect(getMembershipJoinTarget({ signedIn: true, memberStatus: "not_member" })).toBe("/membership/enrollment");
    expect(getMembershipJoinTarget({ signedIn: true, memberStatus: "active" })).toBe("/account/membership");
  });

  it("starts checkout through the auth split and flags membership intent", () => {
    expect(getCheckoutEntryRoute([])).toBe("/checkout/auth");
    expect(getCheckoutEntryRoute([{ skuId: "membership-annual", quantity: 1 }])).toBe("/checkout/auth?intent=membership");
  });

  it("disables guest checkout only when a membership service is in the bag", () => {
    const regularOptions = getCheckoutAuthOptions([{ skuId: "sofa-1", quantity: 1 }]);
    const membershipOptions = getCheckoutAuthOptions([{ skuId: "membership-annual", quantity: 1 }]);

    expect(regularOptions.find((option) => option.key === "guest").disabled).toBe(false);
    expect(membershipOptions.find((option) => option.key === "guest").disabled).toBe(true);
    expect(membershipOptions.find((option) => option.key === "guest").reason).toContain("membership");
  });
});
