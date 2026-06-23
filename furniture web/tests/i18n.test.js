import { readFileSync } from "node:fs";
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

  it("provides primary and Baby & Child navigation labels across every supported locale", async () => {
    const { getMessage } = await loadI18n();
    const navKeys = [
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
      "navigation.babyChild.furniture",
      "navigation.babyChild.bedding",
      "navigation.babyChild.nursery",
      "navigation.babyChild.decor",
      "navigation.babyChild.windows",
      "navigation.babyChild.storage",
      "navigation.babyChild.registry",
    ];

    for (const key of navKeys) {
      expect(getMessage(key, "en"), `${key} missing for en`).toBeTruthy();
      expect(getMessage(key, "zh-CN"), `${key} missing for zh-CN`).toBeTruthy();
      expect(getMessage(key, "fr"), `${key} missing for fr`).toBeTruthy();
    }

    expect(getMessage("navigation.primary.decor", "zh-CN")).toBe("装饰");
    expect(getMessage("navigation.babyChild.furniture", "zh-CN")).toBe("家具");
    expect(getMessage("navigation.babyChild.registry", "fr")).toBe("Liste de naissance");
  });

  it("renders header navigation labels through locale messages instead of raw layout labels", () => {
    const source = readFileSync(new URL("../src/components/RhHeader.vue", import.meta.url), "utf8");

    expect(source).toContain("const navigationLabelKey = (label) =>");
    expect(source).toContain("const navItemLabel = (label) => t(navigationLabelKey(label));");
    expect(source).toContain("{{ navItemLabel(item.label) }}");
    expect(source).not.toContain("{{ item.label }}");
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
      "membership.account.addressBook.verification.lastChecked",
      "membership.account.addressBook.verification.missingWarning",
      "membership.account.addressBook.verification.warning",
      "membership.account.addressBook.verification.providerFallbackWarning",
      "membership.account.addressBook.verification.localPostalRegionWarning",
      "membership.account.addressBook.verification.statuses.verified",
      "membership.account.addressBook.verification.statuses.suggested",
      "membership.account.addressBook.verification.statuses.unverified",
      "membership.account.addressBook.verification.statuses.missing",
      "membership.account.addressBook.verification.statuses.unknown",
      "membership.account.addressBook.verification.choices.original",
      "membership.account.addressBook.verification.choices.suggested",
      "membership.account.addressBook.verification.choices.unknown",
      "membership.account.addressBook.verification.reasons.google-unverified",
      "membership.account.addressBook.verification.reasons.remote-standardized",
      "membership.account.addressBook.verification.providerStatuses.fallback",
      "membership.account.addressBook.verification.providerStatuses.unknown",
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
      "orders.paymentStatus",
      "orders.paymentStatusUnavailable",
      "orders.paymentResumeUnavailable",
      "orders.paymentChannelUnavailable",
      "orders.paymentReturn.cancelled.title",
      "orders.paymentReturn.cancelled.message",
      "orders.paymentReturn.failed.title",
      "orders.paymentReturn.failed.message",
      "orders.paymentReturn.paid.title",
      "orders.paymentReturn.paid.message",
      "orders.paymentReturn.waiting.title",
      "orders.paymentReturn.waiting.message",
      "orders.paymentReturn.unknown.title",
      "orders.paymentReturn.unknown.message",
      "orders.paymentStatuses.waiting",
      "orders.paymentStatuses.paid",
      "orders.paymentStatuses.closed",
      "orders.paymentStatuses.refunded",
      "orders.paymentStatuses.unknown",
      "orders.payOrderLabel",
      "orders.addressVerification.title",
      "orders.addressVerification.source",
      "orders.addressVerification.addressSource",
      "orders.addressVerification.status",
      "orders.addressVerification.choice",
      "orders.addressVerification.selected",
      "orders.addressVerification.providerStatus",
      "orders.addressVerification.warning",
      "orders.addressVerification.providerFallbackWarning",
      "orders.addressVerification.localPostalRegionWarning",
      "orders.addressVerification.localOnlyVerificationWarning",
      "orders.addressVerification.verificationSources.google-address-validation",
      "orders.addressVerification.verificationSources.local-postal-region",
      "orders.addressVerification.verificationSources.remote-address-verification",
      "orders.addressVerification.verificationSources.backend-address-verification",
      "orders.addressVerification.verificationSources.unknown",
      "orders.addressVerification.addressSources.saved",
      "orders.addressVerification.addressSources.new",
      "orders.addressVerification.addressSources.unknown",
      "orders.addressVerification.statuses.verified",
      "orders.addressVerification.statuses.suggested",
      "orders.addressVerification.statuses.unverified",
      "orders.addressVerification.statuses.unknown",
      "orders.addressVerification.choices.original",
      "orders.addressVerification.choices.suggested",
      "orders.addressVerification.choices.unknown",
      "orders.addressVerification.reasons.postal-region-mismatch",
      "orders.addressVerification.reasons.missing-required-fields",
      "orders.addressVerification.reasons.unknown-postal-code",
      "orders.addressVerification.reasons.google-address-complete",
      "orders.addressVerification.reasons.google-review-required",
      "orders.addressVerification.reasons.google-unverified",
      "orders.addressVerification.reasons.backend-standardized",
      "orders.addressVerification.reasons.remote-standardized",
      "orders.addressVerification.reasons.cass-standardized",
      "orders.addressVerification.reasons.unknown",
      "orders.addressVerification.providerStatuses.fallback",
      "orders.addressVerification.providerStatuses.unknown",
      "orders.error",
      "orders.actions.connectAccount",
      "orders.actions.retry",
      "orders.actions.refreshPaymentStatus",
      "orders.actions.resumePayment",
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
      "checkout.shipping.title",
      "checkout.shipping.firstName",
      "checkout.shipping.lastName",
      "checkout.shipping.country",
      "checkout.shipping.street",
      "checkout.shipping.apartment",
      "checkout.shipping.city",
      "checkout.shipping.state",
      "checkout.shipping.postalCode",
      "checkout.shipping.phone",
      "checkout.shipping.savedAddresses",
      "checkout.shipping.enterNewAddress",
      "checkout.shipping.addressVerificationFallbackWarning",
      "checkout.confirm.customNotice",
      "checkout.confirm.useSuggestedAddress",
      "checkout.confirm.paymentReady",
      "checkout.confirm.termsAccepted",
      "checkout.address.verified",
      "checkout.address.suggested",
      "checkout.addressReview.titleVerified",
      "checkout.addressReview.titleReview",
      "checkout.addressReview.suggestedMessage",
      "checkout.addressReview.unverifiedMessage",
      "checkout.addressReview.confirmMessage",
      "checkout.addressReview.verifiedLabel",
      "checkout.addressReview.enteredLabel",
      "checkout.addressReview.suggestedLabel",
      "checkout.addressReview.useSuggested",
      "checkout.addressReview.useVerified",
      "checkout.addressReview.useEntered",
      "checkout.addressReview.editOriginal",
      "checkout.addressReview.confirmationNotice",
      "checkout.addressReview.providerFallbackWarning",
      "checkout.addressReview.localPostalRegionWarning",
      "checkout.addressReview.localOnlyVerificationWarning",
      "checkout.addressConfirmation.title",
      "checkout.addressConfirmation.status",
      "checkout.addressConfirmation.choice",
      "checkout.addressConfirmation.addressSource",
      "checkout.addressConfirmation.reason",
      "checkout.addressConfirmation.providerStatus",
      "checkout.addressConfirmation.edit",
      "checkout.addressConfirmation.warning",
      "checkout.addressConfirmation.providerFallbackWarning",
      "checkout.addressConfirmation.localPostalRegionWarning",
      "checkout.addressConfirmation.localOnlyVerificationWarning",
      "checkout.addressConfirmation.addressSources.saved",
      "checkout.addressConfirmation.addressSources.new",
      "checkout.addressConfirmation.addressSources.unknown",
      "checkout.addressConfirmation.statuses.verified",
      "checkout.addressConfirmation.statuses.suggested",
      "checkout.addressConfirmation.statuses.unverified",
      "checkout.addressConfirmation.statuses.unknown",
      "checkout.addressConfirmation.choices.original",
      "checkout.addressConfirmation.choices.suggested",
      "checkout.addressConfirmation.choices.unknown",
      "checkout.addressConfirmation.providerStatuses.fallback",
      "checkout.addressConfirmation.providerStatuses.unknown",
      "checkout.addressConfirmation.reasons.postal-region-mismatch",
      "checkout.addressConfirmation.reasons.missing-required-fields",
      "checkout.addressConfirmation.reasons.unknown-postal-code",
      "checkout.addressConfirmation.reasons.google-address-complete",
      "checkout.addressConfirmation.reasons.google-review-required",
      "checkout.addressConfirmation.reasons.google-unverified",
      "checkout.addressConfirmation.reasons.backend-standardized",
      "checkout.addressConfirmation.reasons.remote-standardized",
      "checkout.addressConfirmation.reasons.cass-standardized",
      "checkout.addressConfirmation.reasons.unknown",
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
      "checkout.payment.channelUnavailable",
      "checkout.payment.ready",
      "checkout.payment.required",
      "checkout.terms.title",
      "checkout.terms.intro",
      "checkout.deliveryNotes.title",
      "checkout.deliveryNotes.intro",
      "checkout.errors.loadUnavailable",
      "checkout.errors.orderUnavailable",
      "checkout.errors.paymentUnavailable",
      "checkout.errors.paymentChannelUnavailable",
      "checkout.errors.paymentRequired",
      "checkout.errors.addressConfirmationRequired",
      "checkout.errors.noAddress",
      "checkout.errors.stockUnavailable",
      "checkout.errors.addressUnavailable",
      "checkout.errors.priceChanged",
      "checkout.errors.sessionExpired",
      "checkout.actions.manageAddresses",
      "checkout.actions.reviewBag",
      "checkout.actions.signIn",
      "checkout.actions.refreshSettlement",
      "checkout.actions.viewOrder",
      "checkout.actions.reviewAddress",
    ];

    for (const locale of availableLocales) {
      for (const key of checkoutKeys) {
        expect(getMessage(key, locale.lang), `${key} missing for ${locale.lang}`).toBeTruthy();
      }
    }
  });
});
