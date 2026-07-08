import { describe, expect, it } from "vitest";
import {
  ANNUAL_MEMBERSHIP_PRODUCT,
  ANNUAL_FIRST_ORDER_DISCOUNT_RATE,
  CUSTOM_ITEM_DEPOSIT_RATE,
  MEMBERSHIP_SERVICE_SKU,
  WHOLE_ROOM_LIFETIME_DISCOUNT_RATE,
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

const customTableItem = {
  skuId: "custom-table-1",
  id: "custom-table-1",
  name: "Custom Dining Table",
  price: 1200,
  quantity: 1,
  source: "local",
  customOrder: true,
};

describe("membership cart pricing", () => {
  it("defines an annual membership product compatible with the local cart", () => {
    expect(MEMBERSHIP_SERVICE_SKU).toBe("membership-annual");
    expect(ANNUAL_FIRST_ORDER_DISCOUNT_RATE).toBe(0.05);
    expect(WHOLE_ROOM_LIFETIME_DISCOUNT_RATE).toBe(0.15);
    expect(CUSTOM_ITEM_DEPOSIT_RATE).toBe(0.5);
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
      discountRate: 0,
      appliedRule: "not-active-member",
      customItemDeposit: 0,
    });
  });

  it("applies the annual membership first-order 95% price rule", () => {
    expect(
      getMembershipPricing([sofaItem, lampItem], {
        membership: { status: "active", planCode: "annual", firstOrderUsed: false },
      }),
    ).toEqual({
      merchandiseSubtotal: 2300,
      membershipSubtotal: 0,
      memberDiscount: 115,
      estimatedTotal: 2185,
      memberPricingActive: true,
      discountRate: 0.05,
      appliedRule: "annual-first-order",
      customItemDeposit: 0,
    });
  });

  it("does not reapply the annual first-order discount after it has been used", () => {
    expect(
      getMembershipPricing([sofaItem, lampItem], {
        membership: { status: "active", planCode: "annual", firstOrderUsed: true },
      }),
    ).toEqual({
      merchandiseSubtotal: 2300,
      membershipSubtotal: 0,
      memberDiscount: 0,
      estimatedTotal: 2300,
      memberPricingActive: true,
      discountRate: 0,
      appliedRule: "annual-first-order-used",
      customItemDeposit: 0,
    });
  });

  it("applies the whole-room lifetime 85% price rule", () => {
    expect(
      getMembershipPricing([sofaItem, lampItem], {
        membership: { status: "active", planCode: "whole_room" },
      }),
    ).toEqual({
      merchandiseSubtotal: 2300,
      membershipSubtotal: 0,
      memberDiscount: 345,
      estimatedTotal: 1955,
      memberPricingActive: true,
      discountRate: 0.15,
      appliedRule: "whole-room-lifetime",
      customItemDeposit: 0,
    });
  });

  it("applies the annual first-order discount when buying the annual membership in the same cart", () => {
    expect(getMembershipPricing([sofaItem, ANNUAL_MEMBERSHIP_PRODUCT])).toEqual({
      merchandiseSubtotal: 2000,
      membershipSubtotal: 200,
      memberDiscount: 100,
      estimatedTotal: 2100,
      memberPricingActive: true,
      discountRate: 0.05,
      appliedRule: "annual-membership-in-cart",
      customItemDeposit: 0,
    });
  });

  it("reports custom item non-refundable deposit without changing the discount base", () => {
    expect(
      getMembershipPricing([customTableItem], {
        membership: { status: "active", planCode: "whole_room" },
      }),
    ).toEqual({
      merchandiseSubtotal: 1200,
      membershipSubtotal: 0,
      memberDiscount: 180,
      estimatedTotal: 1020,
      memberPricingActive: true,
      discountRate: 0.15,
      appliedRule: "whole-room-lifetime",
      customItemDeposit: 600,
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
      memberDiscount: 0,
      estimatedTotal: 200.04,
      memberPricingActive: true,
      discountRate: 0.05,
      appliedRule: "annual-membership-in-cart",
      customItemDeposit: 0,
    });
  });

  it("returns the correct cart notice for each membership state", () => {
    expect(getMembershipCartNotice([sofaItem]).title).toBe("Join the Members Program");
    expect(
      getMembershipCartNotice([sofaItem], {
        membership: { status: "active", planCode: "annual", firstOrderUsed: false },
      }).title,
    ).toBe("Member savings applied");
    expect(getMembershipCartNotice([sofaItem, ANNUAL_MEMBERSHIP_PRODUCT])).toEqual({
      title: "Membership checkout required",
      message: "Member benefits apply after account sign-in and successful membership checkout.",
    });
  });
});
