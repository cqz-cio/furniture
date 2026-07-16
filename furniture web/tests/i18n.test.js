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

  it("translates focused storefront navigation keys instead of showing raw i18n paths", async () => {
    const { availableLocales, setLocale, t } = await loadI18n();
    const navigationKeys = [
      "navigation.primary.bedroomFurniture",
      "navigation.primary.storageCabinets",
      "navigation.primary.desksTables",
      "navigation.primary.seatingBenches",
      "navigation.primary.roomSets",
      "navigation.primary.woodcraft",
      "navigation.primary.newSale",
    ];

    for (const locale of availableLocales) {
      setLocale(locale.lang);
      for (const key of navigationKeys) {
        expect(t(key), `${key} missing for ${locale.lang}`).not.toBe(key);
        expect(t(key), `${key} corrupted for ${locale.lang}`).not.toMatch(/\?{2,}/);
      }
    }

    setLocale("zh-CN");
    expect(t("navigation.primary.bedroomFurniture")).toBe("卧室家具");
    expect(t("navigation.primary.storageCabinets")).toBe("柜类收纳");
    expect(t("navigation.primary.newSale")).toBe("新品特惠");
  });

  it("resolves every English storefront navigation label and falls back for untranslated locales", async () => {
    const { getMessage, setLocale, t } = await loadI18n();
    const keys = [
      "navigation.storefront.primary.new",
      "navigation.storefront.primary.collections",
      "navigation.storefront.primary.bedroom",
      "navigation.storefront.primary.living",
      "navigation.storefront.primary.dining",
      "navigation.storefront.primary.bespoke",
      "navigation.storefront.primary.decor",
      "navigation.storefront.primary.sale",
      "navigation.storefront.submenu.catalog",
      "navigation.storefront.submenu.rectangularTables",
      "navigation.storefront.submenu.upholsterySwatches",
    ];

    keys.forEach((key) => expect(getMessage(key, "en")).toBeTruthy());
    setLocale("zh-CN");
    expect(t("navigation.storefront.primary.collections")).toBe("SHOP BY COLLECTIONS");
    setLocale("fr");
    expect(t("navigation.storefront.submenu.catalog")).toBe("OAKVED catalog");
    setLocale("en");
  });

  it("translates French messages and falls back to the key", async () => {
    const { setLocale, t } = await loadI18n();

    setLocale("fr");

    expect(t("home.heroEyebrow")).toBe("Bienvenue chez Oakved");
    expect(t("missing.key")).toBe("missing.key");
  });

  it("provides auth modal translations across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const authKeys = [
      "auth.account.title",
      "auth.account.welcomeMember",
      "auth.account.orderHistory",
      "auth.signIn.submit",
      "auth.secureLink.title",
      "auth.create.submit",
      "auth.emailCode.send",
      "auth.trade.submit",
      "auth.fields.email",
    ];

    for (const locale of availableLocales) {
      for (const key of authKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides membership journey translations across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const membershipKeys = [
      "membership.landing.title",
      "membership.landing.benefit1",
      "membership.enrollment.title",
      "membership.enrollment.annualTitle",
      "membership.terms.title",
      "membership.terms.emailBindingTitle",
      "membership.faq.title",
      "membership.faq.rulesAnswer",
      "membership.account.title",
      "membership.account.currentStatus",
      "membership.account.overview.values.email",
      "membership.account.states.title",
      "membership.account.states.pendingLink.description",
      "membership.account.states.activeWholeRoom.title",
      "membership.account.signInRequired",
      "membership.account.actions.connectAccount",
      "membership.account.emptyStates.notMember.title",
      "membership.account.emptyStates.expired.description",
      "membership.account.planWholeRoom",
      "membership.account.billingTitle",
      "membership.account.actions.shopEligible.label",
      "membership.account.eligibility.title",
      "membership.account.eligibility.reasons.eligible.description",
      "membership.account.eligibility.reasons.serviceExcluded.label",
      "membership.checkoutAuth.title",
      "membership.checkoutAuth.guestMembershipNote",
    ];

    for (const locale of availableLocales) {
      for (const key of membershipKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides order history membership value labels across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const orderKeys = ["orders.memberSavings", "orders.memberSavingsUnavailable"];

    for (const locale of availableLocales) {
      for (const key of orderKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides storefront navigation and home module labels across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const storefrontKeys = [
      "navigation.primary.living",
      "navigation.primary.dining",
      "navigation.primary.bed",
      "navigation.primary.bath",
      "navigation.primary.outdoor",
      "navigation.primary.lighting",
      "navigation.primary.textiles",
      "navigation.primary.rugs",
      "navigation.primary.decor",
      "navigation.primary.babyChild",
      "navigation.primary.teen",
      "navigation.primary.sale",
      "navigation.primary.interiorDesign",
      "home.modules.bedroom.title",
      "home.modules.dining.title",
      "home.modules.outdoorLiving.title",
      "home.modules.sourcebooks.title",
      "home.modules.services.title",
    ];

    for (const locale of availableLocales) {
      for (const key of storefrontKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides full visible-copy namespaces for every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const keys = [
      "home.commerce.eyebrow",
      "home.commerce.title",
      "home.featured.title",
      "home.trust.memberPricing.title",
      "landing.common.collection",
      "landing.common.designServices",
      "landing.common.joinMembers",
      "landing.common.exploreServices",
      "outdoor.hero.title",
      "outdoor.services.title",
      "teen.hero.title",
      "teen.services.cta",
      "babyChild.hero.title",
      "babyChild.category.placeholderTitle",
      "sale.hero.title",
      "productList.filters.title",
      "productList.filters.clearAll",
      "productList.edit.title",
      "productDetail.gallery.previous",
      "productDetail.gallery.next",
      "productDetail.gallery.instructions",
      "productDetail.registry.add",
      "productDetail.inspiration.eyebrow",
      "productDetail.inspiration.title",
      "productDetail.inspiration.description",
      "productDetail.shopRoom.eyebrow",
      "productDetail.shopRoom.title",
      "productDetail.shopRoom.description",
      "productDetail.completeRoom.eyebrow",
      "productDetail.completeRoom.title",
      "productDetail.completeRoom.description",
      "cart.drawerTitle",
      "cart.summary.title",
      "cart.membership.description",
      "checkout.header.title",
      "checkout.header.shipping",
      "checkout.header.payment",
      "checkout.header.confirmation",
      "checkout.payment.saveCard",
      "checkout.payment.billingSameAsShipping",
      "checkout.summary.memberSavings",
      "checkout.footer.privacy",
      "giftRegistry.eyebrow",
      "giftRegistry.nav.home",
      "giftRegistry.nav.create",
      "giftRegistry.nav.find",
      "giftRegistry.nav.manage",
      "giftRegistry.nav.account",
      "giftRegistry.home.title",
      "giftRegistry.home.description",
      "giftRegistry.find.title",
      "giftRegistry.find.search",
      "giftRegistry.find.create",
      "giftRegistry.find.manage",
      "giftRegistry.find.view",
      "giftRegistry.create.title",
      "giftRegistry.create.find",
      "giftRegistry.create.manage",
      "giftRegistry.create.flow",
      "giftRegistry.create.steps.event",
      "giftRegistry.create.steps.registrant",
      "giftRegistry.create.steps.delivery",
      "giftRegistry.create.steps.privacy",
      "giftRegistry.create.steps.share",
      "giftRegistry.create.purchaseCallbackNote",
      "giftRegistry.manage.eyebrow",
      "giftRegistry.manage.title",
      "giftRegistry.manage.signInEyebrow",
      "giftRegistry.manage.signInTitle",
      "giftRegistry.manage.signIn",
      "giftRegistry.manage.viewPublic",
      "giftRegistry.manage.giftsEyebrow",
      "giftRegistry.manage.addProductTitle",
      "giftRegistry.manage.addGift",
      "giftRegistry.manage.viewProduct",
      "giftRegistry.public.titleFallback",
      "giftRegistry.public.eventFallback",
      "giftRegistry.public.unavailable",
      "giftRegistry.public.requestedPurchased",
      "giftRegistry.public.itemFallbackNote",
      "giftRegistry.public.viewProduct",
      "giftRegistry.public.addGiftToBag",
      "giftRegistry.public.noGiftsEyebrow",
      "giftRegistry.public.noGiftsTitle",
      "giftRegistry.public.noGiftsDescription",
      "account.dashboard.title",
      "placeholder.missing.eyebrow",
      "placeholder.missing.title",
    ];

    for (const locale of availableLocales) {
      for (const key of keys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });
});
