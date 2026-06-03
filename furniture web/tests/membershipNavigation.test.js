import { describe, expect, it } from "vitest";
import {
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
    expect(membershipRoutes.checkoutAuth).toBe("/checkout/auth");
    expect(membershipRoutes.giftRegistry).toBe("/gift-registry");
  });

  it("keeps the My Account menu order aligned with RH", () => {
    expect(accountMenuItems.map((item) => item.label)).toEqual([
      "Membership",
      "Payment Methods",
      "Order History",
      "Wish List",
      "Address Book",
      "Account Profile",
      "Gift Registry",
    ]);
  });

  it("uses three checkout auth choices and blocks guest membership purchase", () => {
    expect(checkoutAuthOptions.map((option) => option.key)).toEqual(["sign-in", "create-account", "guest"]);
    expect(checkoutAuthOptions.find((option) => option.key === "guest").disabledForMembership).toBe(true);
  });

  it("routes membership join actions by login and member state", () => {
    expect(getMembershipJoinTarget({ signedIn: false, memberStatus: "guest" })).toBe("/checkout/auth?intent=membership");
    expect(getMembershipJoinTarget({ signedIn: true, memberStatus: "not_member" })).toBe("/membership/enrollment");
    expect(getMembershipJoinTarget({ signedIn: true, memberStatus: "active" })).toBe("/account/membership");
  });
});
