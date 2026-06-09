import { describe, expect, it } from "vitest";
import {
  MEMBERSHIP_STATUSES,
  createMembershipProfile,
  getEmailBindingState,
  getMembershipEligibilityReview,
  getMembershipEligibilityItemsFromOrderItems,
  getMembershipBenefits,
  getMembershipGrowth,
  getMembershipStatusView,
} from "../src/services/membershipAccount.js";

describe("membership account model", () => {
  it("creates a default not-member profile", () => {
    expect(createMembershipProfile()).toMatchObject({
      status: "not_member",
      planName: "None",
      accountEmail: "customer@example.com",
      memberEmail: "",
      growthLevel: "Member",
      growthPoints: 0,
      nextGrowthTarget: 500,
      benefits: [],
    });
  });

  it("returns status view models for account membership states", () => {
    expect(getMembershipStatusView(createMembershipProfile()).ctaHref).toBe("/membership/enrollment");
    expect(
      getMembershipStatusView(createMembershipProfile({ status: MEMBERSHIP_STATUSES.activeAnnual })).ctaHref,
    ).toBe("/membership/terms");
    expect(getMembershipStatusView(createMembershipProfile({ status: MEMBERSHIP_STATUSES.pendingLink }))).toMatchObject({
      label: "Pending Link",
      ctaHref: "/account/membership",
    });
  });

  it("calculates clamped growth progress", () => {
    expect(getMembershipGrowth(createMembershipProfile({ growthPoints: 250, nextGrowthTarget: 500 }))).toMatchObject({
      points: 250,
      target: 500,
      percent: 50,
      remaining: 250,
    });
    expect(getMembershipGrowth(createMembershipProfile({ growthPoints: 900, nextGrowthTarget: 500 })).percent).toBe(100);
  });

  it("detects email binding state", () => {
    expect(getEmailBindingState(createMembershipProfile())).toBe("missing_member_email");
    expect(getEmailBindingState(createMembershipProfile({ memberEmail: "other@example.com" }))).toBe("needs_verification");
    expect(getEmailBindingState(createMembershipProfile({ memberEmail: "customer@example.com" }))).toBe("matched");
  });

  it("returns benefits for active annual and whole-room members", () => {
    expect(getMembershipBenefits(createMembershipProfile())).toEqual([]);
    expect(getMembershipBenefits(createMembershipProfile({ status: MEMBERSHIP_STATUSES.activeAnnual }))).toHaveLength(2);
    expect(getMembershipBenefits(createMembershipProfile({ status: MEMBERSHIP_STATUSES.activeWholeRoom }))).toHaveLength(3);
  });

  it("summarizes member price eligibility review lines", () => {
    const review = getMembershipEligibilityReview([
      { name: "Cloud Sofa", category: "merchandise", regularPrice: 4000, memberPrice: 3000 },
      { name: "Delivery Service", category: "service", regularPrice: 299, memberPrice: 299 },
    ]);

    expect(review).toMatchObject({
      eligibleCount: 1,
      ineligibleCount: 1,
      savingsTotal: 1000,
    });
    expect(review.lines[0]).toMatchObject({
      key: "eligible",
      eligible: true,
      savings: 1000,
    });
    expect(review.lines[1]).toMatchObject({
      key: "serviceExcluded",
      eligible: false,
      savings: 0,
    });
  });

  it("normalizes order line items for eligibility review", () => {
    expect(
      getMembershipEligibilityItemsFromOrderItems([
        { name: "Cloud Sofa", price: 3000, originalPrice: 4000, count: 2 },
        { name: "White Glove Delivery Service", price: 299 },
        { name: "Gift Card", price: 100 },
      ]),
    ).toEqual([
      { name: "Cloud Sofa", category: "merchandise", regularPrice: 8000, memberPrice: 6000 },
      { name: "White Glove Delivery Service", category: "service", regularPrice: 299, memberPrice: 299 },
      { name: "Gift Card", category: "gift_card", regularPrice: 100, memberPrice: 100 },
    ]);
  });
});
