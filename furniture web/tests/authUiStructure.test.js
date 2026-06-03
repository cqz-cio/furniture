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
  });

  it("defines RH-style email, account creation, and trade sign-in forms", () => {
    const emailSource = readSource("../src/components/AuthEmailSignInForm.vue");
    const createSource = readSource("../src/components/AuthCreateAccountForm.vue");
    const tradeSource = readSource("../src/components/AuthTradeSignInForm.vue");

    expect(emailSource).toContain("requestEmailSignInLink");
    expect(emailSource).toContain('autocomplete="email"');
    expect(emailSource).toContain("SIGN IN");
    expect(emailSource).toContain("Forgot Password?");
    expect(emailSource).not.toContain("mobile");
    expect(emailSource).not.toContain("sms");
    expect(emailSource).not.toContain('inputmode="tel"');
    expect(emailSource).not.toContain("one-time-code");

    expect(createSource).toContain("registerByEmail");
    expect(createSource).toContain("First Name");
    expect(createSource).toContain("Last Name");
    expect(createSource).toContain("RH Privacy Notice");
    expect(createSource).toContain("emailOptIn");

    expect(tradeSource).toContain("loginByTradeAccount");
    expect(tradeSource).toContain("Trade ID");
    expect(tradeSource).toContain("Email Address");
    expect(tradeSource).toContain("Apply for a Trade Account");
  });

  it("keeps auth modal usable on small screens", () => {
    const css = readSource("../src/styles.css");

    expect(css).toContain("overflow-y: auto;");
    expect(css).toContain(".account-modal-layer");
    expect(css).toContain("max-height: calc(100dvh - 40px);");
  });
});
