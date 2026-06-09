import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("account membership UI integration", () => {
  it("renders account membership from the membership account service", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");

    expect(source).toContain("MEMBERSHIP_ACCOUNT_SCENARIOS");
    expect(source).toContain("accountMenuLabelKeys");
    expect(source).toContain("getMembershipAccountScenario");
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

  it("renders membership eligibility review details", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("getMembershipEligibilityReview");
    expect(source).toContain("eligibilityReview");
    expect(source).toContain("membership-eligibility-panel");
    expect(source).toContain("membership-eligibility-row");
    expect(source).toContain("membership.account.eligibility");
    expect(source).toContain("membership.account.eligibility.summary.eligible");
    expect(source).toContain("membership.account.eligibility.line.savings");
    expect(styles).toContain(".membership-eligibility-panel");
    expect(styles).toContain(".membership-eligibility-row");
  });
});
