export const AUTH_SESSION_STORAGE_KEY = "YUDAO_APP_SESSION";
export const LEGACY_AUTH_TOKEN_STORAGE_KEY = "YUDAO_APP_TOKEN";

const safeStorage = (storage) => {
  if (storage !== undefined) return storage;
  try {
    return globalThis.localStorage;
  } catch {
    return null;
  }
};

const safeGetItem = (storage, key) => {
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
};

const safeSetItem = (storage, key, value) => {
  try {
    storage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
};

const safeRemoveItem = (storage, key) => {
  try {
    storage.removeItem(key);
  } catch {
    // Ignore storage failures so auth state helpers stay safe to call.
  }
};

const normalizeSession = (session) => {
  if (!session || typeof session !== "object") return null;

  const accessToken = String(session.accessToken || "").trim();
  if (!accessToken) return null;

  return {
    userId: session.userId ?? null,
    accessToken,
    refreshToken: String(session.refreshToken || "").trim(),
    expiresTime: String(session.expiresTime || "").trim(),
  };
};

export const readYudaoSession = (storage) => {
  storage = safeStorage(storage);
  if (!storage) return null;

  const rawSession = safeGetItem(storage, AUTH_SESSION_STORAGE_KEY);
  if (rawSession) {
    try {
      const parsed = normalizeSession(JSON.parse(rawSession));
      if (parsed) return parsed;
    } catch {
      safeRemoveItem(storage, AUTH_SESSION_STORAGE_KEY);
    }
  }

  const legacyToken = String(safeGetItem(storage, LEGACY_AUTH_TOKEN_STORAGE_KEY) || "").trim();
  return legacyToken
    ? { userId: null, accessToken: legacyToken, refreshToken: "", expiresTime: "" }
    : null;
};

export const writeYudaoSession = (session, storage) => {
  storage = safeStorage(storage);
  if (!storage) return null;

  const normalized = normalizeSession(session);
  if (!normalized) {
    clearYudaoSession(storage);
    return null;
  }

  const wroteSession = safeSetItem(storage, AUTH_SESSION_STORAGE_KEY, JSON.stringify(normalized));
  const wroteLegacyToken = safeSetItem(storage, LEGACY_AUTH_TOKEN_STORAGE_KEY, normalized.accessToken);
  return wroteSession || wroteLegacyToken ? normalized : null;
};

export const clearYudaoSession = (storage) => {
  storage = safeStorage(storage);
  if (!storage) return;

  safeRemoveItem(storage, AUTH_SESSION_STORAGE_KEY);
  safeRemoveItem(storage, LEGACY_AUTH_TOKEN_STORAGE_KEY);
};

export const isYudaoSessionAuthenticated = (storage) =>
  Boolean(readYudaoSession(storage)?.accessToken);

export const redactSecret = (value) => {
  const text = String(value || "");
  if (!text) return "";
  if (text.length <= 8) return "***";
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
};
