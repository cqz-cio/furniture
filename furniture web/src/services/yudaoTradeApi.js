import { getMemberProfile } from "./yudaoMemberApi.js";
import { requestYudao } from "./yudaoRequest.js";

export const YUDAO_TRADE_PROFILE_STATUS = {
  active: "active",
  inactive: "inactive",
};

export const normalizeYudaoTradeProfile = (profile = {}) => {
  const tradeId = String(profile.tradeId || "").trim();
  return {
    userId: profile.id,
    email: profile.email || "",
    tradeId,
    status: tradeId ? YUDAO_TRADE_PROFILE_STATUS.active : YUDAO_TRADE_PROFILE_STATUS.inactive,
    active: Boolean(tradeId),
    raw: profile.raw || profile,
  };
};

export const getYudaoTradeProfile = async (options = {}) =>
  normalizeYudaoTradeProfile(await getMemberProfile(options));

export const submitTradeApplication = (payload, options = {}) =>
  requestYudao("/member/auth/trade-application", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });

export const uploadTradeApplicationAttachment = async (file, options = {}) => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("directory", options.directory || "trade/application");

  const data = await requestYudao("/infra/file/upload", {
    ...options,
    method: "POST",
    token: "",
    headers: {
      ...(options.headers || {}),
      "Content-Type": undefined,
    },
    body: formData,
  });
  return {
    name: file.name,
    url: data,
  };
};
