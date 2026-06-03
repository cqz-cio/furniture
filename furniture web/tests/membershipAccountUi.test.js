import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("account membership UI integration", () => {
  it("renders account membership from the membership account service", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");

    expect(source).toContain("createMembershipProfile");
    expect(source).toContain("getMembershipStatusView");
    expect(source).toContain("getMembershipGrowth");
    expect(source).toContain("getEmailBindingState");
    expect(source).toContain("getMembershipBenefits");
  });

  it("exposes status, renewal, growth and email binding sections", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");

    expect(source).toContain("membership-status-panel");
    expect(source).toContain("membership-growth-panel");
    expect(source).toContain("membership-email-panel");
    expect(source).toContain("membership-benefit-grid");
  });
});
