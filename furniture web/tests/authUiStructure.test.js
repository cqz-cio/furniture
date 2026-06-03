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
    expect(source).toContain('<AuthSmsForm');
    expect(source).toContain('<AuthPasswordForm');
    expect(source).toContain('<AuthTokenPanel');
    expect(source).toContain("logoutMember");
  });

  it("defines SMS and password forms for Yudao member auth", () => {
    const smsSource = readSource("../src/components/AuthSmsForm.vue");
    const passwordSource = readSource("../src/components/AuthPasswordForm.vue");

    expect(smsSource).toContain("sendMemberSmsCode");
    expect(smsSource).toContain("loginBySms");
    expect(smsSource).toContain('autocomplete="one-time-code" inputmode="numeric" type="password"');
    expect(passwordSource).toContain("loginByPassword");
    expect(passwordSource).toContain('type="password"');
  });
});
