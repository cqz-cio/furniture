import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("auth recovery actions", () => {
  it("offers sign-in recovery actions after password or secure-link errors", () => {
    const source = readSource("../src/components/AuthEmailSignInForm.vue");

    expect(source).toContain("auth-recovery-actions");
    expect(source).toContain('t("auth.recovery.useSecureLink")');
    expect(source).toContain('t("auth.recovery.createAccount")');
    expect(source).toContain('t("auth.recovery.passwordSignIn")');
    expect(source).toContain("v-if=\"error\"");
  });

  it("routes existing registration emails back to sign-in", () => {
    const source = readSource("../src/components/AuthCreateAccountForm.vue");

    expect(source).toContain("recoveryAction = ref(\"\")");
    expect(source).toContain('recoveryAction.value = "signIn"');
    expect(source).toContain('t("auth.recovery.signIn")');
    expect(source).toContain("auth-recovery-actions");
  });

  it("styles auth recovery actions", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".auth-recovery-actions");
    expect(source).toContain(".auth-recovery-actions button");
  });
});
