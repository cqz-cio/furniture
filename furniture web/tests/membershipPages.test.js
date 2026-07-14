import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const pagePath = (fileName) => new URL(`../src/pages/${fileName}`, import.meta.url);

describe("membership phase 1 pages", () => {
  it("adds the Oakved membership, account, checkout auth and registry pages", () => {
    [
      "MembershipPage.vue",
      "MembershipEnrollmentPage.vue",
      "MembershipTermsPage.vue",
      "MembershipFaqPage.vue",
      "AccountPage.vue",
      "AccountMembershipPage.vue",
      "CheckoutAuthPage.vue",
      "GiftRegistryPage.vue",
    ].forEach((fileName) => {
      expect(existsSync(pagePath(fileName)), `${fileName} should exist`).toBe(true);
    });
  });

  it("registers phase 1 routes in the current App.vue route map", () => {
    const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");

    expect(app).toContain('membership: "/membership"');
    expect(app).toContain('"membership-enrollment": "/membership/enrollment"');
    expect(app).toContain('"membership-terms": "/membership/terms"');
    expect(app).toContain('"account-membership": "/account/membership"');
    expect(app).toContain('"checkout-auth": "/checkout/auth"');
    expect(app).toContain('"gift-registry": "/gift-registry"');
  });

  it("renders membership journey pages through i18n keys instead of fixed English copy", () => {
    [
      "MembershipPage.vue",
      "MembershipEnrollmentPage.vue",
      "MembershipTermsPage.vue",
      "MembershipFaqPage.vue",
      "AccountMembershipPage.vue",
      "CheckoutAuthPage.vue",
    ].forEach((fileName) => {
      const source = readFileSync(pagePath(fileName), "utf8");
      expect(source, `${fileName} should use the i18n composable`).toContain("useI18n");
      expect(source, `${fileName} should render membership translation keys`).toContain('t("membership.');
    });

    const termsSource = readFileSync(pagePath("MembershipTermsPage.vue"), "utf8");
    expect(termsSource).not.toContain("Rules, renewal and benefit eligibility.");
    expect(termsSource).toContain("ruleRows");
    expect(termsSource).toContain("membership-rule-matrix");
    expect(termsSource).toContain("membership.terms.rules");

    const landingSource = readFileSync(pagePath("MembershipPage.vue"), "utf8");
    expect(landingSource).toContain("flowSteps");
    expect(landingSource).toContain("membership-flow-panel");
    expect(landingSource).toContain("membership.landing.flow");

    const faqSource = readFileSync(pagePath("MembershipFaqPage.vue"), "utf8");
    expect(faqSource).toContain("topics");
    expect(faqSource).toContain("membership-faq-layout");
    expect(faqSource).toContain("<details");
    expect(faqSource).toContain("membership.faq.topics");
  });
});
