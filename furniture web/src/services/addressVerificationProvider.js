import { requestYudao } from "./yudaoRequest.js";

export const DEFAULT_ADDRESS_VERIFICATION_PATH = "/member/address/verify";
export const DEFAULT_ADDRESS_VERIFICATION_STATUS_PATH = "/member/address/verification-status";

const clean = (value) => String(value || "").trim();

export const getAddressVerificationPath = () =>
  clean(import.meta.env.VITE_ADDRESS_VERIFICATION_PATH || DEFAULT_ADDRESS_VERIFICATION_PATH);

export const getAddressVerificationStatusPath = () =>
  clean(import.meta.env.VITE_ADDRESS_VERIFICATION_STATUS_PATH || DEFAULT_ADDRESS_VERIFICATION_STATUS_PATH);

export const normalizeRemoteAddressVerification = (payload = {}, localVerification = {}, source = "remote-address-verification") => {
  const suggestedAddress = payload.suggestedAddress || payload.standardizedAddress || payload.address || null;
  const hasSuggestion = Boolean(suggestedAddress);
  const status = clean(payload.status) || (hasSuggestion ? "suggested" : localVerification.status || "unverified");

  return {
    source: payload.source || source,
    status,
    reason: payload.reason || (hasSuggestion ? "remote-standardized" : localVerification.reason || ""),
    requiresConfirmation:
      payload.requiresConfirmation === undefined
        ? hasSuggestion || localVerification.requiresConfirmation || status !== "verified"
        : Boolean(payload.requiresConfirmation),
    originalAddress: payload.originalAddress || localVerification.originalAddress,
    suggestedAddress: hasSuggestion ? suggestedAddress : localVerification.suggestedAddress || null,
    deliverable: payload.deliverable,
    providerResponseId: payload.providerResponseId || payload.metadata?.responseId || "",
    providerStatus: payload.providerStatus || "",
    metadata: payload.metadata,
  };
};

export const createRemoteAddressVerificationProvider = (options = {}) => {
  const path = clean(options.path === undefined ? getAddressVerificationPath() : options.path);
  if (!path) return null;
  const statusPath = clean(
    options.statusPath === undefined ? getAddressVerificationStatusPath() : options.statusPath,
  );

  const name = options.name || "remote-address-verification";

  return {
    name,
    getStatus: async () => {
      if (!statusPath) return null;
      return requestYudao(statusPath, {
        ...options,
        method: "GET",
      });
    },
    verifyAddress: async (address, localVerification) => {
      const payload = await requestYudao(path, {
        ...options,
        method: "POST",
        body: JSON.stringify({
          address,
          localVerification,
        }),
      });

      return normalizeRemoteAddressVerification(payload, localVerification, name);
    },
  };
};
