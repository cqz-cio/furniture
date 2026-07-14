import { mapAddressResponse, mapMemberProfile } from "./yudaoMappers.js";
import { requestYudao } from "./yudaoRequest.js";

export const getDefaultAddress = async (options = {}) => {
  const data = await requestYudao("/member/address/get-default", options);
  return data ? mapAddressResponse(data) : null;
};

export const getAddressList = async (options = {}) => {
  const data = await requestYudao("/member/address/list", options);
  return (data || []).map(mapAddressResponse);
};

export const createMemberAddress = (payload, options = {}) =>
  requestYudao("/member/address/create", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });

export const updateMemberAddress = (payload, options = {}) =>
  requestYudao("/member/address/update", {
    ...options,
    method: "PUT",
    body: JSON.stringify(payload),
  });

export const deleteMemberAddress = (id, options = {}) =>
  requestYudao(`/member/address/delete?id=${encodeURIComponent(id)}`, {
    ...options,
    method: "DELETE",
  });

export const getAreaTree = (options = {}) => requestYudao("/system/area/tree", options);

export const getMemberProfile = async (options = {}) => {
  const data = await requestYudao("/member/user/get", options);
  return mapMemberProfile(data);
};

export const updateMemberProfile = (payload, options = {}) =>
  requestYudao("/member/user/update", {
    ...options,
    method: "PUT",
    body: JSON.stringify(payload),
  });

export const updateMemberMobile = (payload, options = {}) =>
  requestYudao("/member/user/update-mobile", {
    ...options,
    method: "PUT",
    body: JSON.stringify(payload),
  });

export const requestEmailVerificationLink = (email, options = {}) =>
  requestYudao("/member/auth/email-verify-link", {
    ...options,
    method: "POST",
    body: JSON.stringify({ email }),
  });
