export const MEMBERSHIP_SERVICE_SKU = "membership-annual";
export const ANNUAL_FIRST_ORDER_DISCOUNT_RATE = 0.05;
export const WHOLE_ROOM_LIFETIME_DISCOUNT_RATE = 0.15;
export const CUSTOM_ITEM_DEPOSIT_RATE = 0.5;

export const ANNUAL_MEMBERSHIP_PRODUCT = {
  id: MEMBERSHIP_SERVICE_SKU,
  skuId: MEMBERSHIP_SERVICE_SKU,
  name: "Annual Membership",
  subtitle: "Members Program annual service",
  price: 200,
  cover: "",
  source: "membership",
};

const moneyRound = (value) => Math.round((Number(value) || 0) * 100) / 100;

const lineTotal = (item) => (Number(item.price) || 0) * (Number(item.quantity) || 1);

const isCustomItem = (item = {}) => Boolean(item.isCustom || item.customization || item.customOrder);

export const isMembershipItem = (item = {}) =>
  item.source === "membership" || item.skuId === MEMBERSHIP_SERVICE_SKU || item.id === MEMBERSHIP_SERVICE_SKU;

export const hasMembershipService = (items = []) => items.some((item) => isMembershipItem(item));

const normalizeMembershipContext = (options = {}) => {
  if (options.membership) return options.membership;
  if (options.activeMember) {
    return { status: "active", planCode: "annual", firstOrderUsed: false };
  }
  return { status: "not_member", planCode: "", firstOrderUsed: false };
};

const getMembershipDiscountRule = (membership = {}, items = []) => {
  if (hasMembershipService(items)) {
    return {
      memberPricingActive: true,
      discountRate: ANNUAL_FIRST_ORDER_DISCOUNT_RATE,
      appliedRule: "annual-membership-in-cart",
    };
  }

  if (membership.status !== "active") {
    return {
      memberPricingActive: false,
      discountRate: 0,
      appliedRule: "not-active-member",
    };
  }

  if (membership.planCode === "whole_room") {
    return {
      memberPricingActive: true,
      discountRate: WHOLE_ROOM_LIFETIME_DISCOUNT_RATE,
      appliedRule: "whole-room-lifetime",
    };
  }

  if (membership.planCode === "annual" && !membership.firstOrderUsed) {
    return {
      memberPricingActive: true,
      discountRate: ANNUAL_FIRST_ORDER_DISCOUNT_RATE,
      appliedRule: "annual-first-order",
    };
  }

  if (membership.planCode === "annual") {
    return {
      memberPricingActive: true,
      discountRate: 0,
      appliedRule: "annual-first-order-used",
    };
  }

  return {
    memberPricingActive: true,
    discountRate: 0,
    appliedRule: "active-no-pricing-rule",
  };
};

export const getMembershipPricing = (items = [], options = {}) => {
  const membershipSubtotal = moneyRound(
    items.filter((item) => isMembershipItem(item)).reduce((sum, item) => sum + lineTotal(item), 0),
  );
  const merchandiseItems = items.filter((item) => !isMembershipItem(item));
  const merchandiseSubtotal = moneyRound(merchandiseItems.reduce((sum, item) => sum + lineTotal(item), 0));
  const customItemDeposit = moneyRound(
    merchandiseItems.filter((item) => isCustomItem(item)).reduce((sum, item) => sum + lineTotal(item), 0) *
      CUSTOM_ITEM_DEPOSIT_RATE,
  );
  const membership = normalizeMembershipContext(options);
  const discountRule = getMembershipDiscountRule(membership, items);
  const memberDiscount = moneyRound(merchandiseSubtotal * discountRule.discountRate);

  return {
    merchandiseSubtotal,
    membershipSubtotal,
    memberDiscount,
    estimatedTotal: moneyRound(merchandiseSubtotal + membershipSubtotal - memberDiscount),
    memberPricingActive: discountRule.memberPricingActive,
    discountRate: discountRule.discountRate,
    appliedRule: discountRule.appliedRule,
    customItemDeposit,
  };
};

export const getMembershipCartNotice = (items = [], options = {}) => {
  if (hasMembershipService(items)) {
    return {
      title: "Membership checkout required",
      message: "Member benefits apply after account sign-in and successful membership checkout.",
    };
  }

  if (getMembershipPricing(items, options).memberPricingActive) {
    return {
      title: "Member savings applied",
      message: "Eligible merchandise reflects Members Program pricing.",
    };
  }

  return {
    title: "Join the Members Program",
    message: "Add an annual membership to unlock savings on eligible merchandise.",
  };
};
