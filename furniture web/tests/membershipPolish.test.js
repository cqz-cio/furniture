import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("membership and registry polish", () => {
  it("uses the shared service page shell on public service pages", () => {
    [
      "../src/pages/MembershipPage.vue",
      "../src/pages/MembershipEnrollmentPage.vue",
      "../src/pages/MembershipFaqPage.vue",
      "../src/pages/MembershipTermsPage.vue",
      "../src/pages/GiftRegistryPage.vue",
      "../src/pages/GiftRegistryCreatePage.vue",
      "../src/pages/GiftRegistryFindPage.vue",
    ].forEach((path) => {
      expect(readSource(path), path).toContain("service-page-shell");
    });
  });

  it("keeps account destinations in a tighter account service shell", () => {
    const accountMembership = readSource("../src/pages/AccountMembershipPage.vue");
    const registryManage = readSource("../src/pages/GiftRegistryManagePage.vue");

    expect(accountMembership).toContain("account-service-shell");
    expect(registryManage).toContain("account-service-shell");
  });

  it("centralizes gift registry owner actions instead of repeating placeholder links", () => {
    const source = readSource("../src/pages/GiftRegistryManagePage.vue");

    expect(source).toContain("ownerActions");
    expect(source).toContain("registry-owner-action-grid");
    expect(source).not.toContain('href="/gift-registry/manage"');
  });

  it("styles tighter Oakved service surfaces", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".service-page-shell");
    expect(source).toContain(".account-service-shell");
    expect(source).toContain(".membership-flow-panel");
    expect(source).toContain(".membership-rule-matrix");
    expect(source).toContain(".membership-faq-layout");
    expect(source).toContain(".registry-owner-action-grid");
    expect(source).toContain(".service-link-row");
  });
});
