import { membershipRoutes } from "./membershipNavigation.js";

export const MEMBERSHIP_STATUSES = {
  notMember: "not_member",
  activeAnnual: "active_annual",
  activeWholeRoom: "active_whole_room",
  expired: "expired",
  pendingLink: "pending_link",
};

const baseBenefits = [
  {
    title: "Member Savings",
    description: "Eligible merchandise receives Members Program pricing.",
  },
  {
    title: "Renewal Management",
    description: "Renewal status, reminders and rules are managed from My Account.",
  },
];

const wholeRoomBenefit = {
  title: "Whole-Room Project Planning",
  description: "Room-level projects can surface whole-room service benefits and planning visibility.",
};

export const createMembershipProfile = (overrides = {}) => ({
  status: MEMBERSHIP_STATUSES.notMember,
  planName: "None",
  memberId: "",
  accountEmail: "customer@example.com",
  memberEmail: "",
  startedAt: "",
  expiresAt: "",
  autoRenew: false,
  growthLevel: "Member",
  growthPoints: 0,
  nextGrowthTarget: 500,
  benefits: [],
  ...overrides,
});

export const MEMBERSHIP_ACCOUNT_SCENARIOS = {
  notMember: {
    profile: createMembershipProfile({
      status: MEMBERSHIP_STATUSES.notMember,
      planName: "None",
    }),
    membershipValue: { annualSavings: "$0", eligibleSpend: "$0", renewalWindow: "0" },
    orders: [],
    eligibilityItems: [
      { name: "Membership enrollment", category: "service", regularPrice: 200, memberPrice: 200 },
      { name: "Cloud Sofa", category: "merchandise", regularPrice: 4000, memberPrice: 3000 },
    ],
  },
  pendingLink: {
    profile: createMembershipProfile({
      status: MEMBERSHIP_STATUSES.pendingLink,
      planName: "Annual Membership",
      memberId: "Pending",
      memberEmail: "member@example.com",
      startedAt: "Pending",
      expiresAt: "Pending",
    }),
    membershipValue: { annualSavings: "$0", eligibleSpend: "$0", renewalWindow: "Email" },
    orders: [],
    eligibilityItems: [
      { name: "Member-priced dining chair", category: "merchandise", regularPrice: 760, memberPrice: 570 },
      { name: "Gift Card", category: "gift_card", regularPrice: 250, memberPrice: 250 },
    ],
  },
  expired: {
    profile: createMembershipProfile({
      status: MEMBERSHIP_STATUSES.expired,
      planName: "Annual Membership",
      memberId: "OAKVED-MEMBER-2025",
      memberEmail: "customer@example.com",
      startedAt: "2025-06-03",
      expiresAt: "2026-06-03",
      growthPoints: 210,
    }),
    membershipValue: { annualSavings: "$420", eligibleSpend: "$1,680", renewalWindow: "Expired" },
    orders: [{ key: "livingRoom", id: "OAKVED-0924", date: "2026-05-08", savings: "$220" }],
    eligibilityItems: [
      { name: "Outdoor Dining Table", category: "merchandise", regularPrice: 3200, memberPrice: 3200 },
      { name: "Delivery Service", category: "service", regularPrice: 299, memberPrice: 299 },
    ],
  },
  activeAnnual: {
    profile: createMembershipProfile({
      status: MEMBERSHIP_STATUSES.activeAnnual,
      planName: "Annual Membership",
      memberId: "OAKVED-MEMBER-2026",
      memberEmail: "customer@example.com",
      startedAt: "2026-06-03",
      expiresAt: "2027-06-03",
      autoRenew: true,
      growthPoints: 320,
      nextGrowthTarget: 500,
    }),
    membershipValue: { annualSavings: "$640", eligibleSpend: "$2,560", renewalWindow: "30" },
    orders: [
      { key: "livingRoom", id: "OAKVED-1024", date: "2026-05-28", savings: "$320" },
      { key: "dining", id: "OAKVED-1018", date: "2026-04-12", savings: "$180" },
      { key: "outdoor", id: "OAKVED-1007", date: "2026-03-09", savings: "$140" },
    ],
    eligibilityItems: [
      { name: "Cloud Sofa", category: "merchandise", regularPrice: 4295, memberPrice: 3221 },
      { name: "Fabric Protection Plan", category: "service", regularPrice: 179, memberPrice: 179 },
      { name: "Dining Chair", category: "merchandise", regularPrice: 760, memberPrice: 570 },
    ],
  },
  activeWholeRoom: {
    profile: createMembershipProfile({
      status: MEMBERSHIP_STATUSES.activeWholeRoom,
      planName: "Whole-Room Membership",
      memberId: "OAKVED-WHOLE-2026",
      memberEmail: "customer@example.com",
      startedAt: "2026-04-16",
      expiresAt: "2027-04-16",
      autoRenew: true,
      growthLevel: "Whole-Room",
      growthPoints: 460,
      nextGrowthTarget: 700,
    }),
    membershipValue: { annualSavings: "$1,120", eligibleSpend: "$4,480", renewalWindow: "45" },
    orders: [
      { key: "livingRoom", id: "OAKVED-1102", date: "2026-05-31", savings: "$520" },
      { key: "dining", id: "OAKVED-1091", date: "2026-05-05", savings: "$360" },
    ],
    eligibilityItems: [
      { name: "Room Plan Sofa", category: "merchandise", regularPrice: 5200, memberPrice: 3900 },
      { name: "Project Planning Session", category: "project_service", regularPrice: 350, memberPrice: 350 },
      { name: "Lighting Pair", category: "merchandise", regularPrice: 1800, memberPrice: 1350 },
    ],
  },
};

export const getMembershipAccountScenario = (key = "activeAnnual") => {
  const scenarioKey = Object.prototype.hasOwnProperty.call(MEMBERSHIP_ACCOUNT_SCENARIOS, key) ? key : "activeAnnual";
  const scenario = MEMBERSHIP_ACCOUNT_SCENARIOS[scenarioKey];
  const status = scenario.profile.status;
  const hasActiveBenefits =
    status === MEMBERSHIP_STATUSES.activeAnnual || status === MEMBERSHIP_STATUSES.activeWholeRoom;
  const requiresAttention = status === MEMBERSHIP_STATUSES.expired || status === MEMBERSHIP_STATUSES.pendingLink;
  const emptyStateKey = hasActiveBenefits
    ? ""
    : status === MEMBERSHIP_STATUSES.expired
      ? "expired"
      : status === MEMBERSHIP_STATUSES.pendingLink
        ? "pendingLink"
        : "notMember";

  return {
    key: scenarioKey,
    ...scenario,
    hasActiveBenefits,
    requiresAttention,
    emptyStateKey,
  };
};

export const getMembershipStatusView = (profile = createMembershipProfile()) => {
  const status = profile.status;

  if (status === MEMBERSHIP_STATUSES.activeAnnual) {
    return {
      label: "Active Annual Member",
      tone: "active",
      ctaLabel: "View Rules",
      ctaHref: membershipRoutes.membershipTerms,
    };
  }

  if (status === MEMBERSHIP_STATUSES.activeWholeRoom) {
    return {
      label: "Active Whole-Room Member",
      tone: "active",
      ctaLabel: "View Rules",
      ctaHref: membershipRoutes.membershipTerms,
    };
  }

  if (status === MEMBERSHIP_STATUSES.expired) {
    return {
      label: "Expired",
      tone: "attention",
      ctaLabel: "Renew Membership",
      ctaHref: membershipRoutes.membershipEnrollment,
    };
  }

  if (status === MEMBERSHIP_STATUSES.pendingLink) {
    return {
      label: "Pending Link",
      tone: "attention",
      ctaLabel: "Verify Email",
      ctaHref: membershipRoutes.accountMembership,
    };
  }

  return {
    label: "Not a Member",
    tone: "neutral",
    ctaLabel: "Join or Renew",
    ctaHref: membershipRoutes.membershipEnrollment,
  };
};

export const getMembershipGrowth = (profile = createMembershipProfile()) => {
  const points = Math.max(0, Number(profile.growthPoints) || 0);
  const target = Math.max(1, Number(profile.nextGrowthTarget) || 1);
  const percent = Math.min(100, Math.max(0, Math.round((points / target) * 100)));

  return {
    level: profile.growthLevel || "Member",
    points,
    target,
    percent,
    remaining: Math.max(0, target - points),
  };
};

export const getEmailBindingState = (profile = createMembershipProfile()) => {
  const accountEmail = String(profile.accountEmail || "").trim().toLowerCase();
  const memberEmail = String(profile.memberEmail || "").trim().toLowerCase();

  if (!memberEmail) return "missing_member_email";
  return accountEmail === memberEmail ? "matched" : "needs_verification";
};

export const getMembershipBenefits = (profile = createMembershipProfile()) => {
  if (Array.isArray(profile.benefits) && profile.benefits.length) return profile.benefits;

  if (profile.status === MEMBERSHIP_STATUSES.activeWholeRoom) {
    return [...baseBenefits, wholeRoomBenefit];
  }

  if (profile.status === MEMBERSHIP_STATUSES.activeAnnual) {
    return baseBenefits;
  }

  return [];
};

const firstNumericValue = (...values) => {
  const value = values.find((item) => item !== undefined && item !== null && item !== "");
  return Math.max(0, Number(value) || 0);
};

const inferEligibilityCategory = (item = {}) => {
  const explicitCategory = item.category || item.categoryKey || item.type || item.itemType || item.productType;
  const searchable = `${explicitCategory || ""} ${item.name || ""} ${item.spuName || ""} ${item.productName || ""}`
    .trim()
    .toLowerCase();

  if (searchable.includes("gift card") || searchable.includes("gift_card")) return "gift_card";
  if (
    searchable.includes("delivery") ||
    searchable.includes("service") ||
    searchable.includes("protection") ||
    searchable.includes("project")
  ) {
    return "service";
  }

  return explicitCategory ? String(explicitCategory) : "merchandise";
};

export const getMembershipEligibilityItemsFromOrderItems = (orderItems = []) =>
  orderItems.map((item = {}) => {
    const quantity = Math.max(1, Number(item.count ?? item.quantity ?? item.num ?? 1) || 1);
    const memberUnitPrice = firstNumericValue(item.memberPrice, item.price, item.payPrice, item.salePrice);
    const regularUnitPrice = firstNumericValue(
      item.regularPrice,
      item.originalPrice,
      item.marketPrice,
      item.originalUnitPrice,
      memberUnitPrice,
    );

    return {
      name: item.name || item.spuName || item.productName || "Item",
      category: inferEligibilityCategory(item),
      regularPrice: regularUnitPrice * quantity,
      memberPrice: memberUnitPrice * quantity,
    };
  });

export const getMembershipEligibilityReview = (items = []) => {
  const lines = items.map((item) => {
    const regularPrice = Math.max(0, Number(item.regularPrice) || 0);
    const memberPrice = Math.max(0, Number(item.memberPrice) || regularPrice);
    const category = String(item.category || "merchandise");
    const excludedCategory = ["service", "gift_card", "project_service"].includes(category);
    const eligible = !excludedCategory && memberPrice < regularPrice;
    const key = eligible ? "eligible" : category === "gift_card" ? "giftCardExcluded" : excludedCategory ? "serviceExcluded" : "notReduced";

    return {
      name: item.name || "Item",
      category,
      regularPrice,
      memberPrice,
      savings: eligible ? regularPrice - memberPrice : 0,
      eligible,
      key,
    };
  });

  return {
    lines,
    eligibleCount: lines.filter((line) => line.eligible).length,
    ineligibleCount: lines.filter((line) => !line.eligible).length,
    savingsTotal: lines.reduce((total, line) => total + line.savings, 0),
  };
};
