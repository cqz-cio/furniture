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
      "auth.recovery.useSecureLink",
      "auth.recovery.createAccount",
      "auth.recovery.passwordSignIn",
      "auth.recovery.signIn",
    ];

    for (const locale of availableLocales) {
      for (const key of authKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides homepage image overlay copy across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const moduleKeys = [
      "bedroom",
      "dining",
      "outdoorLiving",
      "sourcebooks",
      "milan",
      "interiors",
      "members",
      "founder",
      "architecture",
      "hospitality",
      "guesthouse",
      "aviation",
      "yachting",
      "services",
    ];

    for (const locale of availableLocales) {
      for (const moduleKey of moduleKeys) {
        expect(getMessage(`home.modules.${moduleKey}.eyebrow`, locale.lang), `${moduleKey} eyebrow missing`).toBeTruthy();
        expect(getMessage(`home.modules.${moduleKey}.title`, locale.lang), `${moduleKey} title missing`).toBeTruthy();
        expect(getMessage(`home.modules.${moduleKey}.subtitle`, locale.lang), `${moduleKey} subtitle missing`).toBeTruthy();
        expect(getMessage(`home.modules.${moduleKey}.cta`, locale.lang), `${moduleKey} cta missing`).toBeTruthy();
      }
    }
  });

  it("provides membership journey translations across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const membershipKeys = [
      "membership.landing.title",
      "membership.landing.benefit1",
      "membership.landing.flowTitle",
      "membership.landing.flow.price.description",
      "membership.enrollment.title",
      "membership.enrollment.annualTitle",
      "membership.terms.title",
      "membership.terms.emailBindingTitle",
      "membership.terms.ruleMatrixTitle",
      "membership.terms.rules.merchandise.description",
      "membership.terms.rules.services.title",
      "membership.terms.rules.wholeRoom.label",
      "membership.faq.title",
      "membership.faq.topicsTitle",
      "membership.faq.topics.pricing.description",
      "membership.faq.bindQuestion",
      "membership.faq.pricingAnswer",
      "membership.faq.renewalBadge",
      "membership.faq.rulesAnswer",
      "membership.account.title",
      "membership.account.currentStatus",
      "membership.account.overview.values.email",
      "membership.account.states.title",
      "membership.account.states.pendingLink.description",
      "membership.account.states.activeWholeRoom.title",
      "membership.account.emptyStates.notMember.title",
      "membership.account.emptyStates.expired.description",
      "membership.account.planWholeRoom",
      "membership.account.billingTitle",
      "membership.account.billingHistory.actions.connectAccount",
      "membership.account.billingHistory.actions.retry",
      "membership.account.billingHistory.actions.createOrder",
      "membership.account.addressBook.actions.connectAccount",
      "membership.account.addressBook.actions.retry",
      "membership.account.addressBook.actions.addFirstAddress",
      "membership.account.profile.actions.connectAccount",
      "membership.account.profile.actions.retry",
      "membership.account.profile.actions.reviewProfile",
      "membership.account.profile.actions.reviewPhone",
      "membership.account.actions.shopEligible.label",
      "membership.account.eligibility.title",
      "membership.account.eligibility.summary.eligible",
      "membership.account.eligibility.summary.ineligible",
      "membership.account.eligibility.line.savings",
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
    const orderKeys = [
      "orders.memberSavings",
      "orders.memberSavingsUnavailable",
      "orders.error",
      "orders.actions.connectAccount",
      "orders.actions.retry",
      "orders.actions.shop",
    ];

    for (const locale of availableLocales) {
      for (const key of orderKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides cart recovery notices across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const cartKeys = [
      "cart.remoteUnavailable",
      "cart.remoteMutationUnavailable",
      "cart.retrySync",
      "cart.itemUnavailable",
    ];

    for (const locale of availableLocales) {
      for (const key of cartKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });

  it("provides checkout confirmation translations across every supported locale", async () => {
    const { availableLocales, getMessage } = await loadI18n();
    const checkoutKeys = [
      "checkout.steps.details",
      "checkout.steps.customCheck",
      "checkout.steps.shippingAddress",
      "checkout.steps.addressVerification",
      "checkout.steps.payment",
      "checkout.steps.review",
      "checkout.steps.placeOrder",
      "checkout.steps.deliveryNotes",
      "checkout.confirm.customNotice",
      "checkout.confirm.useSuggestedAddress",
      "checkout.confirm.paymentReady",
      "checkout.confirm.termsAccepted",
      "checkout.address.verified",
      "checkout.address.suggested",
      "checkout.payment.title",
      "checkout.payment.intro",
      "checkout.payment.method",
      "checkout.payment.cardDetails",
      "checkout.payment.card",
      "checkout.payment.methods.card.label",
      "checkout.payment.methods.card.description",
      "checkout.payment.methods.giftCard.label",
      "checkout.payment.methods.giftCard.description",
      "checkout.payment.methods.memberCredit.label",
      "checkout.payment.methods.memberCredit.description",
      "checkout.payment.ready",
      "checkout.payment.required",
      "checkout.terms.title",
      "checkout.terms.intro",
      "checkout.deliveryNotes.title",
      "checkout.deliveryNotes.intro",
      "checkout.errors.loadUnavailable",
      "checkout.errors.orderUnavailable",
      "checkout.errors.noAddress",
      "checkout.errors.stockUnavailable",
      "checkout.errors.addressUnavailable",
      "checkout.errors.priceChanged",
      "checkout.errors.sessionExpired",
      "checkout.actions.manageAddresses",
      "checkout.actions.reviewBag",
      "checkout.actions.signIn",
      "checkout.actions.refreshSettlement",
    ];

    for (const locale of availableLocales) {
      for (const key of checkoutKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });
});
