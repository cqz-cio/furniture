import { beforeEach, describe, expect, it } from "vitest";
import {
  AUTH_SESSION_STORAGE_KEY,
  LEGACY_AUTH_TOKEN_STORAGE_KEY,
  clearYudaoSession,
  isYudaoSessionAuthenticated,
  readYudaoSession,
  redactSecret,
  writeYudaoSession,
} from "../src/services/authSession.js";
import { readYudaoToken, writeYudaoToken } from "../src/services/yudaoClient.js";

const createStorage = () => {
  const store = new Map();
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
};

const createThrowingStorage = () => ({
  getItem: () => {
    throw new Error("get failed");
  },
  setItem: () => {
    throw new Error("set failed");
  },
  removeItem: () => {
    throw new Error("remove failed");
  },
});

const withThrowingGlobalStorage = (callback) => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    get: () => {
      throw new Error("blocked storage");
    },
  });

  try {
    callback();
  } finally {
    if (descriptor) {
      Object.defineProperty(globalThis, "localStorage", descriptor);
    } else {
      delete globalThis.localStorage;
    }
  }
};

describe("authSession", () => {
  let storage;

  beforeEach(() => {
    storage = createStorage();
  });

  it("writes and reads the Yudao auth session", () => {
    writeYudaoSession(
      {
        userId: 1024,
        accessToken: "access-token-value",
        refreshToken: "refresh-token-value",
        expiresTime: "2026-06-03T18:00:00",
      },
      storage
    );

    expect(readYudaoSession(storage)).toEqual({
      userId: 1024,
      accessToken: "access-token-value",
      refreshToken: "refresh-token-value",
      expiresTime: "2026-06-03T18:00:00",
    });
    expect(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY)).toBe("access-token-value");
  });

  it("reads the legacy manual token as an authenticated session", () => {
    storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, "manual-token");

    expect(readYudaoSession(storage)).toEqual({
      userId: null,
      accessToken: "manual-token",
      refreshToken: "",
      expiresTime: "",
    });
    expect(isYudaoSessionAuthenticated(storage)).toBe(true);
  });

  it("clears both session and legacy token", () => {
    storage.setItem(AUTH_SESSION_STORAGE_KEY, "{\"accessToken\":\"token\"}");
    storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, "token");

    clearYudaoSession(storage);

    expect(readYudaoSession(storage)).toBe(null);
    expect(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY)).toBe(null);
  });

  it("preserves empty strings in test storage", () => {
    storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, "");

    expect(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY)).toBe("");
    expect(readYudaoSession(storage)).toBe(null);
  });

  it("does not throw when storage operations fail", () => {
    const throwingStorage = createThrowingStorage();

    expect(() => readYudaoSession(throwingStorage)).not.toThrow();
    expect(readYudaoSession(throwingStorage)).toBe(null);
    expect(() =>
      writeYudaoSession(
        {
          userId: 1024,
          accessToken: "access-token-value",
          refreshToken: "refresh-token-value",
          expiresTime: "2026-06-03T18:00:00",
        },
        throwingStorage
      )
    ).not.toThrow();
    expect(() => clearYudaoSession(throwingStorage)).not.toThrow();
  });

  it("does not throw when the global localStorage getter fails", () => {
    withThrowingGlobalStorage(() => {
      const session = {
        userId: 1,
        accessToken: "access",
        refreshToken: "refresh",
        expiresTime: "2026-06-03T18:00:00",
      };

      expect(readYudaoSession()).toBe(null);
      expect(writeYudaoSession(session)).toBe(null);
      expect(() => clearYudaoSession()).not.toThrow();
      expect(isYudaoSessionAuthenticated()).toBe(false);
      expect(readYudaoToken()).toBe("");
      expect(() => writeYudaoToken("manual-token")).not.toThrow();
    });
  });

  it("redacts secrets for logs and test output", () => {
    expect(redactSecret("abcdef123456")).toBe("abcd...3456");
    expect(redactSecret("abc")).toBe("***");
    expect(redactSecret("")).toBe("");
  });
});

describe("yudaoClient token compatibility", () => {
  it("routes manual token save and clear through the unified session", () => {
    const storage = createStorage();
    storage.setItem(
      AUTH_SESSION_STORAGE_KEY,
      JSON.stringify({
        userId: 1024,
        accessToken: "stale-session-token",
        refreshToken: "stale-refresh-token",
        expiresTime: "2026-06-03T18:00:00",
      })
    );
    storage.setItem(LEGACY_AUTH_TOKEN_STORAGE_KEY, "stale-legacy-token");

    writeYudaoToken("manual-token", storage);

    expect(readYudaoSession(storage)).toEqual({
      userId: null,
      accessToken: "manual-token",
      refreshToken: "",
      expiresTime: "",
    });
    expect(readYudaoToken(storage)).toBe("manual-token");

    writeYudaoToken("", storage);

    expect(readYudaoSession(storage)).toBe(null);
    expect(storage.getItem(AUTH_SESSION_STORAGE_KEY)).toBe(null);
    expect(storage.getItem(LEGACY_AUTH_TOKEN_STORAGE_KEY)).toBe(null);
  });
});
