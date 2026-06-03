export const MEMBERSHIP_SERVICE_SKU = "membership-annual";
export const MEMBER_DISCOUNT_RATE = 0.25;

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

export const isMembershipItem = (item = {}) =>
  item.source === "membership" || item.skuId === MEMBERSHIP_SERVICE_SKU || item.id === MEMBERSHIP_SERVICE_SKU;

export const hasMembershipService = (items = []) => items.some((item) => isMembershipItem(item));

export const getMembershipPricing = (items = [], options = {}) => {
  const membershipSubtotal = moneyRound(
    items.filter((item) => isMembershipItem(item)).reduce((sum, item) => sum + lineTotal(item), 0),
  );
  const merchandiseSubtotal = moneyRound(
    items.filter((item) => !isMembershipItem(item)).reduce((sum, item) => sum + lineTotal(item), 0),
  );
  const memberPricingActive = Boolean(options.activeMember) || hasMembershipService(items);
  const memberDiscount = memberPricingActive ? moneyRound(merchandiseSubtotal * MEMBER_DISCOUNT_RATE) : 0;

  return {
    merchandiseSubtotal,
    membershipSubtotal,
    memberDiscount,
    estimatedTotal: moneyRound(merchandiseSubtotal + membershipSubtotal - memberDiscount),
    memberPricingActive,
  };
};

export const getMembershipCartNotice = (items = [], options = {}) => {
  if (hasMembershipService(items)) {
    return {
      title: "Membership added to bag",
      message: "Member pricing is applied to eligible merchandise in this bag.",
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
