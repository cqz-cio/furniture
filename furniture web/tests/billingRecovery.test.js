import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("billing history recovery actions", () => {
  it("offers recovery actions for sign-in, load errors, and empty billing history", () => {
    const source = readSource("../src/pages/AccountBillingPage.vue");

    expect(source).toContain("isYudaoAuthError");
    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).toContain('t("membership.account.billingHistory.actions.connectAccount")');
    expect(source).toContain('t("membership.account.billingHistory.actions.retry")');
    expect(source).toContain('t("membership.account.billingHistory.actions.createOrder")');
    expect(source).toContain('@click="loadBilling"');
    expect(source).toContain(':href="membershipRoutes.checkoutAuth"');
    expect(source).toContain(':href="membershipRoutes.checkoutAuth"');
    expect(source).toContain("orders-recovery-actions");
    expect(source).toContain("orders-recovery-action");
  });

  it("treats expired Yudao sessions as sign-in-required instead of a generic billing load error", () => {
    const source = readSource("../src/pages/AccountBillingPage.vue");

    expect(source).toContain("if (isYudaoAuthError(error))");
    expect(source).toContain("tokenRequired.value = true");
    expect(source).toContain("return");
    expect(source).toContain('error.value = t("membership.account.billingHistory.error")');
  });
});
