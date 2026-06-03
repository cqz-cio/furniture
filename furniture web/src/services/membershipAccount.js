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
