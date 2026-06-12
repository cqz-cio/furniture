import { clearYudaoSession } from "./authSession.js";
import {
  authStorage,
  persistLoginResponse,
  refreshMemberToken,
  requestYudao,
} from "./yudaoRequest.js";

export const YUDAO_MEMBER_ERROR_CODES = {
  USER_EMAIL_NOT_EXISTS: 1004001005,
  USER_EMAIL_USED: 1004001004,
  TRADE_ACCOUNT_NOT_FOUND: 1004003008,
  EMAIL_CODE_SEND_TOO_FAST: 1004003009,
  EMAIL_CREDENTIAL_NOT_FOUND: 1004003010,
  EMAIL_CREDENTIAL_EXPIRED: 1004003011,
  EMAIL_CREDENTIAL_USED: 1004003012,
  EMAIL_CAPTCHA_REQUIRED: 1004003014,
  EMAIL_CAPTCHA_INVALID: 1004003015,
  EMAIL_CODE_VERIFY_TOO_MANY: 1004003016,
};

export const EMAIL_REGISTRATION_CODE_SCENE = 5;
export const TRADE_LOGIN_EMAIL_CODE_SCENE = 6;

export const sendMemberSmsCode = (mobile, options = {}) => {
  const { scene = 2, withAuth = true, ...requestOptions } = options;
  return requestYudao("/member/auth/send-sms-code", {
    ...requestOptions,
    method: "POST",
    ...(withAuth ? {} : { token: "" }),
    body: JSON.stringify({ mobile, scene }),
  });
};

export const requestEmailSignInLink = (email, options = {}) =>
  requestYudao("/member/auth/email-secure-link", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify({ email }),
  });

export const sendEmailRegistrationCode = (email, options = {}) => {
  const { captchaVerification, ...requestOptions } = options;
  return requestYudao("/member/auth/send-email-code", {
    ...requestOptions,
    method: "POST",
    token: "",
    body: JSON.stringify({
      email,
      scene: EMAIL_REGISTRATION_CODE_SCENE,
      ...(captchaVerification ? { captchaVerification } : {}),
    }),
  });
};

export const sendTradeLoginCode = (payload, options = {}) => {
  const { captchaVerification, ...tradePayload } = payload || {};
  return requestYudao("/member/auth/trade-login-code", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify({
      ...tradePayload,
      ...(captchaVerification ? { captchaVerification } : {}),
    }),
  });
};

export const createEmailCaptchaChallenge = (options = {}) =>
  requestYudao("/member/auth/email-captcha/challenge", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify({}),
  });

export const verifyEmailCaptchaChallenge = (payload, options = {}) =>
  requestYudao("/member/auth/email-captcha/verify", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });

export const loginBySms = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/sms-login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByPassword = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByEmailPassword = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/email-login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const registerByEmail = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/email-register", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

export const loginByTradeAccount = async (payload, options = {}) => {
  const data = await requestYudao("/member/auth/trade-login", {
    ...options,
    method: "POST",
    token: "",
    body: JSON.stringify(payload),
  });
  return persistLoginResponse(data, options);
};

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

export { refreshMemberToken };

export const logoutMember = async (options = {}) => {
  try {
    await requestYudao("/member/auth/logout", {
      ...options,
      method: "POST",
      skipAuthRetry: true,
    });
  } finally {
    clearYudaoSession(authStorage(options));
  }
};
