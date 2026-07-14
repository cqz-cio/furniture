import {
  clearYudaoSession,
  LEGACY_AUTH_TOKEN_STORAGE_KEY,
  readYudaoSession,
  writeYudaoSession,
} from "./authSession.js";

const DEFAULT_APP_API_BASE = "http://127.0.0.1:48080/app-api";
const DEFAULT_APP_TENANT_ID = "121";
export const AUTH_TOKEN_STORAGE_KEY = LEGACY_AUTH_TOKEN_STORAGE_KEY;

const trimSlash = (value) => value.replace(/\/$/, "");

export const getYudaoAppApiBase = () =>
  trimSlash(import.meta.env.VITE_YUDAO_APP_API_BASE || import.meta.env.VITE_YUDAO_API_BASE_URL || DEFAULT_APP_API_BASE);

export const getYudaoAppTenantId = () =>
  String(import.meta.env.VITE_YUDAO_APP_TENANT_ID || import.meta.env.VITE_YUDAO_TENANT_ID || DEFAULT_APP_TENANT_ID).trim();

export const readYudaoToken = (storage) => readYudaoSession(storage)?.accessToken || "";

export const writeYudaoToken = (token, storage) => {
  const nextToken = String(token || "").trim();
  if (nextToken) {
    writeYudaoSession(
      {
        userId: null,
        accessToken: nextToken,
        refreshToken: "",
        expiresTime: "",
      },
      storage
    );
  } else {
    clearYudaoSession(storage);
  }
};

const hasOwn = (object, key) => Object.prototype.hasOwnProperty.call(object, key);

export const authStorage = (options = {}) =>
  hasOwn(options, "storage") ? options.storage : undefined;

export const persistLoginResponse = (data, options = {}) =>
  writeYudaoSession(data, authStorage(options));

const AUTH_FAILURE_CODES = new Set([401]);

const isAuthFailurePayload = (payload) =>
  payload && typeof payload === "object" && AUTH_FAILURE_CODES.has(Number(payload.code));

const withoutAuthorizationHeader = (headers = {}) => {
  const nextHeaders = { ...headers };
  delete nextHeaders.Authorization;
  delete nextHeaders.authorization;
  return nextHeaders;
};

const readYudaoPayload = async (response) => {
  try {
    return await response.json();
  } catch {
    return null;
  }
};

const getYudaoErrorKind = (payload, fallbackKind = "business") => {
  if (isAuthFailurePayload(payload)) return "auth";
  return fallbackKind;
};

const createYudaoError = (payload, options = {}) => {
  const error = new Error(
    options.message || payload?.msg || payload?.message || `Yudao request failed: ${payload?.code}`
  );
  error.kind = options.kind || getYudaoErrorKind(payload);
  error.code = Number(payload?.code);
  error.data = payload?.data;
  if (options.status !== undefined) error.status = options.status;
  if (options.cause) error.cause = options.cause;
  return error;
};

const createYudaoHttpError = (response, payload) => {
  if (payload?.code !== undefined) {
    return createYudaoError(payload, {
      kind: getYudaoErrorKind(payload, "http"),
      status: response.status,
      message: `Yudao HTTP ${response.status}`,
    });
  }
  const error = new Error(`Yudao HTTP ${response.status}`);
  error.kind = response.status === 401 ? "auth" : "http";
  error.code = response.status;
  error.status = response.status;
  return error;
};

const createYudaoNetworkError = (caught) => {
  const error = new Error(caught?.message || "Yudao network request failed");
  error.kind = "network";
  error.cause = caught;
  return error;
};

export const isYudaoAuthError = (error) => error?.kind === "auth" || Number(error?.code) === 401;
export const isYudaoBusinessError = (error) => error?.kind === "business";
export const isYudaoNetworkError = (error) => error?.kind === "network";

export const unwrapYudaoResult = (payload) => {
  if (!payload || typeof payload !== "object") return payload;
  if (payload.code !== undefined && payload.code !== 0) {
    throw createYudaoError(payload);
  }
  return payload.data !== undefined ? payload.data : payload;
};

export const requestYudao = async (path, options = {}) => {
  const {
    baseUrl,
    storage,
    tenantId: optionTenantId,
    token: optionToken,
    skipAuthRetry,
    headers: optionHeaders,
    ...fetchOptions
  } = options;
  const base = baseUrl || getYudaoAppApiBase();
  const session = readYudaoSession(storage);
  const token = hasOwn(options, "token") ? optionToken : session?.accessToken || "";
  const tenantId = String(
    hasOwn(options, "tenantId") ? optionTenantId || "" : getYudaoAppTenantId()
  ).trim();
  const headers = Object.fromEntries(
    Object.entries({
      "Content-Type": "application/json",
      ...(tenantId ? { "tenant-id": tenantId } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(optionHeaders || {}),
    }).filter(([, value]) => value !== undefined)
  );

  let response;
  try {
    response = await fetch(`${base}${path}`, {
      ...fetchOptions,
      headers,
    });
  } catch (caught) {
    throw createYudaoNetworkError(caught);
  }
  const payload = await readYudaoPayload(response);

  if (!response.ok) {
    if (isAuthFailurePayload(payload) && session?.refreshToken && !skipAuthRetry) {
      let refreshed;
      try {
        refreshed = await refreshMemberToken(session.refreshToken, {
          ...options,
          headers: withoutAuthorizationHeader(optionHeaders),
        });
      } catch (error) {
        clearYudaoSession(authStorage(options));
        throw error;
      }
      return requestYudao(path, {
        ...options,
        headers: withoutAuthorizationHeader(optionHeaders),
        token: refreshed?.accessToken || "",
        skipAuthRetry: true,
      });
    }
    throw createYudaoHttpError(response, payload);
  }

  if (isAuthFailurePayload(payload) && session?.refreshToken && !skipAuthRetry) {
    let refreshed;
    try {
      refreshed = await refreshMemberToken(session.refreshToken, {
        ...options,
        headers: withoutAuthorizationHeader(optionHeaders),
      });
    } catch (error) {
      clearYudaoSession(authStorage(options));
      throw error;
    }
    return requestYudao(path, {
      ...options,
      headers: withoutAuthorizationHeader(optionHeaders),
      token: refreshed?.accessToken || "",
      skipAuthRetry: true,
    });
  }

  return unwrapYudaoResult(payload);
};

export const refreshMemberToken = async (refreshToken, options = {}) => {
  const data = await requestYudao(
    `/member/auth/refresh-token?refreshToken=${encodeURIComponent(refreshToken)}`,
    {
      ...options,
      headers: withoutAuthorizationHeader(options.headers),
      method: "POST",
      token: "",
      skipAuthRetry: true,
    }
  );
  return persistLoginResponse(data, options);
};
