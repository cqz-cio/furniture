import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("address book recovery actions", () => {
  it("offers recovery actions for sign-in, load errors, and empty addresses", () => {
    const source = readSource("../src/pages/AccountAddressBookPage.vue");

    expect(source).toContain("isYudaoAuthError");
    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).toContain('t("membership.account.addressBook.actions.connectAccount")');
    expect(source).toContain('t("membership.account.addressBook.actions.retry")');
    expect(source).toContain('t("membership.account.addressBook.actions.addFirstAddress")');
    expect(source).toContain('@click="loadAddresses"');
    expect(source).toContain('id="address-book-form"');
    expect(source).toContain('href="#address-book-form"');
  });

  it("treats expired Yudao sessions as sign-in-required instead of a generic address load error", () => {
    const source = readSource("../src/pages/AccountAddressBookPage.vue");

    expect(source).toContain("if (isYudaoAuthError(error))");
    expect(source).toContain("tokenRequired.value = true");
    expect(source).toContain("return");
    expect(source).toContain('error.value = t("membership.account.addressBook.error")');
  });

  it("uses shared recovery action styling", () => {
    const source = readSource("../src/pages/AccountAddressBookPage.vue");

    expect(source).toContain("orders-recovery-actions");
    expect(source).toContain("orders-recovery-action");
  });

  it("shows saved address verification metadata without treating it as a live lookup", () => {
    const source = readSource("../src/pages/AccountAddressBookPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("address.addressVerificationSummary");
    expect(source).toContain('class="address-book-verification"');
    expect(source).toContain('t(address.addressVerificationSummary.statusLabelKey)');
    expect(source).toContain('t("membership.account.addressBook.verification.lastChecked")');
    expect(source).toContain("address.addressVerificationSummary.warningKey");
    expect(source).toContain("address.addressVerificationSummary.providerWarningKey");
    expect(source).toContain("address.addressVerificationSummary.sourceWarningKey");
    expect(i18n).toContain("addressBook");
    expect(i18n).toContain("missingWarning");
    expect(i18n).toContain("providerFallbackWarning");
    expect(i18n).toContain("localPostalRegionWarning");
  });
});
