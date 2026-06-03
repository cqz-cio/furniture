import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const pagePath = (fileName) => new URL(`../src/pages/${fileName}`, import.meta.url);

describe("membership phase 1 pages", () => {
  it("adds the RH-aligned membership, account, checkout auth and registry pages", () => {
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
});
