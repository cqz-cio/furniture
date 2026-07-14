import { createMembershipProfile } from "./membershipAccount.js";
import { requestYudao } from "./yudaoRequest.js";

const normalizeMembershipProfile = (data = {}) =>
  createMembershipProfile({
    status: data.status,
    planCode: data.planCode || "",
    planName: data.planName,
    memberId: data.memberId,
    accountEmail: data.accountEmail,
    memberEmail: data.memberEmail || data.accountEmail || "",
    startedAt: data.startedAt,
    expiresAt: data.expiresAt,
    autoRenew: data.autoRenew === true,
    userId: data.userId,
  });

export const getYudaoMembershipProfile = async (options = {}) => {
  const data = await requestYudao("/member/membership/get", options);
  return normalizeMembershipProfile(data);
};

export const createYudaoMembershipCheckoutIntent = (payload = {}, options = {}) =>
  requestYudao("/member/membership/checkout-intent", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });
