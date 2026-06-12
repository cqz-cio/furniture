import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("address book recovery actions", () => {
  it("offers recovery actions for sign-in, load errors, and empty addresses", () => {
    const source = readSource("../src/pages/AccountAddressBookPage.vue");

    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).toContain('t("membership.account.addressBook.actions.connectAccount")');
    expect(source).toContain('t("membership.account.addressBook.actions.retry")');
    expect(source).toContain('t("membership.account.addressBook.actions.addFirstAddress")');
    expect(source).toContain('@click="loadAddresses"');
    expect(source).toContain('id="address-book-form"');
    expect(source).toContain('href="#address-book-form"');
  });

  it("uses shared recovery action styling", () => {
    const source = readSource("../src/pages/AccountAddressBookPage.vue");

    expect(source).toContain("orders-recovery-actions");
    expect(source).toContain("orders-recovery-action");
  });
});
