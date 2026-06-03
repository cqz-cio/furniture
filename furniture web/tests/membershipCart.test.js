import { describe, expect, it } from "vitest";
import {
  ANNUAL_MEMBERSHIP_PRODUCT,
  MEMBERSHIP_SERVICE_SKU,
  getMembershipCartNotice,
  getMembershipPricing,
  hasMembershipService,
  isMembershipItem,
} from "../src/services/membershipCart.js";

const sofaItem = {
  skuId: "sofa-1",
  id: "sofa-1",
  name: "Shelter Sofa",
  price: 1000,
  quantity: 2,
  source: "local",
};

const lampItem = {
  skuId: "lamp-1",
  id: "lamp-1",
  name: "Gallery Lamp",
  price: 300,
  quantity: 1,
  source: "local",
};

describe("membership cart pricing", () => {
  it("defines an annual membership product compatible with the local cart", () => {
    expect(MEMBERSHIP_SERVICE_SKU).toBe("membership-annual");
    expect(ANNUAL_MEMBERSHIP_PRODUCT).toMatchObject({
      id: "membership-annual",
      skuId: "membership-annual",
      name: "Annual Membership",
      price: 200,
      source: "membership",
    });
    expect(ANNUAL_MEMBERSHIP_PRODUCT.subtitle).toContain("Members Program");
  });

  it("detects membership service items by source or identifier", () => {
    expect(isMembershipItem({ source: "membership" })).toBe(true);
    expect(isMembershipItem({ skuId: MEMBERSHIP_SERVICE_SKU })).toBe(true);
    expect(isMembershipItem({ id: MEMBERSHIP_SERVICE_SKU })).toBe(true);
    expect(isMembershipItem(sofaItem)).toBe(false);
    expect(hasMembershipService([sofaItem, ANNUAL_MEMBERSHIP_PRODUCT])).toBe(true);
  });

  it("does not discount merchandise when the cart has no active membership context", () => {
    expect(getMembershipPricing([sofaItem, lampItem])).toEqual({
      merchandiseSubtotal: 2300,
      membershipSubtotal: 0,
      memberDiscount: 0,
      estimatedTotal: 2300,
      memberPricingActive: false,
    });
  });

  it("discounts merchandise when the account is already an active member", () => {
    expect(getMembershipPricing([sofaItem, lampItem], { activeMember: true })).toEqual({
      merchandiseSubtotal: 2300,
      membershipSubtotal: 0,
      memberDiscount: 575,
      estimatedTotal: 1725,
      memberPricingActive: true,
    });
  });

  it("discounts merchandise and keeps membership fee separate when membership is in the cart", () => {
    expect(getMembershipPricing([sofaItem, ANNUAL_MEMBERSHIP_PRODUCT])).toEqual({
      merchandiseSubtotal: 2000,
      membershipSubtotal: 200,
      memberDiscount: 500,
      estimatedTotal: 1700,
      memberPricingActive: true,
    });
  });

  it("rounds aggregate subtotals, discounts, and totals to cents", () => {
    const accentItem = {
      skuId: "accent-1",
      id: "accent-1",
      name: "Accent",
      price: 0.015,
      quantity: 1,
      source: "local",
    };
    const fabricItem = {
      skuId: "fabric-1",
      id: "fabric-1",
      name: "Fabric",
      price: 0.015,
      quantity: 1,
      source: "local",
    };
    const fractionalMembership = {
      ...ANNUAL_MEMBERSHIP_PRODUCT,
      price: 200.005,
      quantity: 1,
    };

    expect(getMembershipPricing([accentItem, fabricItem, fractionalMembership])).toEqual({
      merchandiseSubtotal: 0.03,
      membershipSubtotal: 200.01,
      memberDiscount: 0.01,
      estimatedTotal: 200.03,
      memberPricingActive: true,
    });
  });

  it("returns the correct cart notice for each membership state", () => {
    expect(getMembershipCartNotice([sofaItem]).title).toBe("Join the Members Program");
    expect(getMembershipCartNotice([sofaItem], { activeMember: true }).title).toBe("Member savings applied");
    expect(getMembershipCartNotice([sofaItem, ANNUAL_MEMBERSHIP_PRODUCT]).title).toBe("Membership added to bag");
  });
});
