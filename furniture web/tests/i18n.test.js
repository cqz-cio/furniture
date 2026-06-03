import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const STORAGE_KEY = "furniture-web-locale";

const loadI18n = async () => {
  vi.resetModules();
  return import("../src/i18n.js");
};

describe("i18n locale helper", () => {
  beforeEach(() => {
    const store = new Map();
    vi.stubGlobal("localStorage", {
      getItem: vi.fn((key) => store.get(key) || null),
      setItem: vi.fn((key, value) => store.set(key, value)),
      removeItem: vi.fn((key) => store.delete(key)),
    });
    vi.stubGlobal("document", {
      createElement: vi.fn(() => ({ style: {} })),
      documentElement: { lang: "" },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("exposes English, Chinese, and French locale options", async () => {
    const { availableLocales } = await loadI18n();

    expect(availableLocales).toEqual([
      { lang: "en", label: "English", shortLabel: "EN" },
      { lang: "zh-CN", label: "中文", shortLabel: "中文" },
      { lang: "fr", label: "Français", shortLabel: "FR" },
    ]);
  });

  it("persists selected locale and updates the document language", async () => {
    const { currentLocale, setLocale } = await loadI18n();

    setLocale("fr");

    expect(currentLocale.value).toBe("fr");
    expect(globalThis.localStorage.setItem).toHaveBeenCalledWith(STORAGE_KEY, "fr");
    expect(globalThis.document.documentElement.lang).toBe("fr");
  });

  it("falls back to English when an unsupported locale is requested", async () => {
    const { currentLocale, setLocale } = await loadI18n();

    setLocale("es");

    expect(currentLocale.value).toBe("en");
    expect(globalThis.localStorage.setItem).toHaveBeenCalledWith(STORAGE_KEY, "en");
    expect(globalThis.document.documentElement.lang).toBe("en");
  });

  it("falls back to English when localStorage contains an unsupported locale", async () => {
    globalThis.localStorage.getItem.mockReturnValue("es");

    const { currentLocale } = await loadI18n();

    expect(currentLocale.value).toBe("en");
  });

  it("does not throw when localStorage is unavailable or throws", async () => {
    vi.stubGlobal("localStorage", {
      getItem: vi.fn(() => {
        throw new Error("storage disabled");
      }),
      setItem: vi.fn(() => {
        throw new Error("storage disabled");
      }),
    });

    const { currentLocale, setLocale } = await loadI18n();

    expect(currentLocale.value).toBe("en");
    expect(() => setLocale("zh-CN")).not.toThrow();
    expect(currentLocale.value).toBe("zh-CN");
  });

  it("translates nested keys and interpolates params", async () => {
    const { setLocale, t } = await loadI18n();

    expect(t("cart.itemCount", { count: 2 })).toBe("2 ITEMS");
    setLocale("zh-CN");
    expect(t("cart.itemCount", { count: 2 })).toBe("2 件商品");
    setLocale("fr");
    expect(t("cart.itemCount", { count: 2 })).toBe("2 ARTICLES");
  });

  it("translates French messages and falls back to the key", async () => {
    const { setLocale, t } = await loadI18n();

    setLocale("fr");

    expect(t("home.heroEyebrow")).toBe("Bienvenue dans l'univers RH");
    expect(t("missing.key")).toBe("missing.key");
  });
});
