import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("auth UI structure", () => {
  it("keeps RhHeader responsible for opening AuthModal and forwarding auth changes", () => {
    const source = readSource("../src/components/RhHeader.vue");

    expect(source).toContain('import AuthModal from "./AuthModal.vue";');
    expect(source).toContain('const emit = defineEmits(["open-cart", "auth-change"])');
    expect(source).toContain('<AuthModal');
    expect(source).toContain(':open="accountOpen"');
    expect(source).toContain('@close="closeAccount"');
    expect(source).toContain('@auth-change="emit(\'auth-change\', $event)"');
    expect(source).toContain('@click="emit(\'open-cart\')"');
  });

  it("defines the auth modal shell and keeps the developer token fallback behind auth-change", () => {
    const source = readSource("../src/components/AuthModal.vue");

    expect(source).toContain('defineEmits(["close", "auth-change"])');
    expect(source).toContain('role="dialog"');
    expect(source).toContain('aria-labelledby="account-modal-title"');
    expect(source).toContain('<AuthEmailSignInForm');
    expect(source).toContain('@authenticated="handleAuthenticated"');
    expect(source).toContain('<AuthCreateAccountForm');
    expect(source).toContain('<AuthTradeSignInForm');
    expect(source).toContain('mode.value = "secureLink"');
    expect(source).toContain('mode.value = "create"');
    expect(source).toContain('mode.value = "trade"');
    expect(source).not.toContain('<AuthSmsForm');
    expect(source).not.toContain('<AuthPasswordForm');
    expect(source).toContain('<AuthTokenPanel');
    expect(source).toContain("logoutMember");
    expect(source).toContain("logoutNotice");
    expect(source).toContain("membershipRoutes.accountOrders");
    expect(source).toContain("membershipRoutes.accountMembership");
    expect(source).toContain("membershipRoutes.accountGiftRegistry");
    expect(source).toContain('@click="emit(\'close\')"');
    expect(source).not.toContain('<a href="#">ORDER HISTORY</a>');
  });

  it("localizes the auth modal and account forms through i18n keys", () => {
    const modalSource = readSource("../src/components/AuthModal.vue");
    const emailSource = readSource("../src/components/AuthEmailSignInForm.vue");
    const createSource = readSource("../src/components/AuthCreateAccountForm.vue");
    const tradeSource = readSource("../src/components/AuthTradeSignInForm.vue");
    const i18nSource = readSource("../src/i18n.js");

    for (const source of [modalSource, emailSource, createSource, tradeSource]) {
      expect(source).toContain('import { useI18n } from "../i18n.js";');
      expect(source).toContain("const { t } = useI18n()");
    }

    expect(modalSource).toContain('t("auth.account.title")');
    expect(modalSource).toContain('t("auth.account.welcomeMember"');
    expect(emailSource).toContain('t("auth.signIn.submit")');
    expect(createSource).toContain('t("auth.create.submit")');
    expect(tradeSource).toContain('t("auth.trade.submit")');
    expect(i18nSource).toContain("account: {");
    expect(i18nSource).toContain("welcomeMember:");
    expect(i18nSource).toContain("secureLink:");
    expect(i18nSource).toContain("emailCode:");
  });

  it("defines RH-style email, account creation, and trade sign-in forms", () => {
    const emailSource = readSource("../src/components/AuthEmailSignInForm.vue");
    const createSource = readSource("../src/components/AuthCreateAccountForm.vue");
    const tradeSource = readSource("../src/components/AuthTradeSignInForm.vue");

    expect(emailSource).toContain("loginByEmailPassword");
    expect(emailSource).toContain("requestEmailSignInLink");
    expect(emailSource).toContain('defineEmits(["secure-link", "create-account", "trade", "sign-in", "authenticated"])');
    expect(emailSource).toContain('autocomplete="current-password"');
    expect(emailSource).toContain('maxlength="16"');
    expect(emailSource).toContain('minlength="4"');
    expect(emailSource).toContain('emit("authenticated", session)');
    expect(emailSource).toContain('autocomplete="email"');
    expect(emailSource).toContain('maxlength="255"');
    expect(emailSource).toContain('t("auth.signIn.submit")');
    expect(emailSource).toContain('t("auth.signIn.forgotPassword")');
    expect(emailSource).not.toContain("mobile");
    expect(emailSource).not.toContain("sms");
    expect(emailSource).not.toContain('inputmode="tel"');
    expect(emailSource).not.toContain("one-time-code");

    expect(createSource).toContain("registerByEmail");
    expect(createSource).toContain("sendEmailRegistrationCode");
    expect(createSource).toContain("createEmailCaptchaChallenge");
    expect(createSource).toContain("verifyEmailCaptchaChallenge");
    expect(createSource).toContain("YUDAO_MEMBER_ERROR_CODES");
    expect(createSource).toContain("auth-captcha-panel");
    expect(createSource).toContain("captchaAnswer");
    expect(createSource).toContain("captchaChallenge.imageBase64");
    expect(createSource).toContain("refreshCaptchaChallenge");
    expect(createSource).toContain("verificationCode");
    expect(createSource).toContain('t("auth.emailCode.send")');
    expect(createSource).toContain('t("auth.emailCode.emailExists")');
    expect(createSource).toContain('t("auth.emailCode.invalidCode")');
    expect(createSource).toContain('t("auth.emailCode.captchaTitle")');
    expect(createSource).toContain('autocomplete="one-time-code"');
    expect(createSource).toContain('t("auth.fields.firstName")');
    expect(createSource).toContain('t("auth.fields.lastName")');
    expect(createSource).toContain('t("auth.create.privacyNotice")');
    expect(createSource).toContain("emailOptIn");
    expect(createSource).toContain('maxlength="255"');
    expect(createSource).toContain("password.value.length >= 4");
    expect(createSource).toContain("password.value.length <= 16");
    expect(createSource).toContain('maxlength="16"');
    expect(createSource).toContain('minlength="4"');

    expect(tradeSource).toContain("loginByTradeAccount");
    expect(tradeSource).toContain('t("auth.fields.tradeId")');
    expect(tradeSource).toContain('t("auth.fields.emailAddress")');
    expect(tradeSource).toContain('t("auth.trade.apply")');
  });

  it("opens the image captcha recovery flow when registration code attempts are exhausted", () => {
    const createSource = readSource("../src/components/AuthCreateAccountForm.vue");

    expect(createSource).toContain(`error.value = registerErrorMessage(caught);
    if (Number(caught?.code) === YUDAO_MEMBER_ERROR_CODES.EMAIL_CODE_VERIFY_TOO_MANY) {
      await openCaptchaChallenge();
    }`);
  });

  it("shows specific email code errors instead of the generic account creation error", () => {
    const createSource = readSource("../src/components/AuthCreateAccountForm.vue");
    const clientSource = readSource("../src/services/yudaoClient.js");
    const i18nSource = readSource("../src/i18n.js");

    expect(clientSource).toContain("EMAIL_CREDENTIAL_NOT_FOUND: 1004003010");
    expect(clientSource).toContain("EMAIL_CREDENTIAL_EXPIRED: 1004003011");
    expect(clientSource).toContain("EMAIL_CREDENTIAL_USED: 1004003012");
    expect(createSource).toContain("case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_NOT_FOUND:");
    expect(createSource).toContain("case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_EXPIRED:");
    expect(createSource).toContain("case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_USED:");
    expect(createSource).toContain("return emailCodeErrorMessage(caught);");
    expect(i18nSource).toContain("invalidCode:");
    expect(i18nSource).toContain("expiredCode:");
    expect(i18nSource).toContain("usedCode:");
  });

  it("keeps auth modal usable on small screens", () => {
    const css = readSource("../src/styles.css");

    expect(css).toContain("overflow-y: auto;");
    expect(css).toContain(".account-modal-layer");
    expect(css).toContain("max-height: calc(100dvh - 40px);");
  });
});
