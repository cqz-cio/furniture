import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("account membership UI integration", () => {
  it("renders account membership from the membership account service", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");

    expect(source).toContain("MEMBERSHIP_ACCOUNT_SCENARIOS");
    expect(source).toContain("accountMenuLabelKeys");
    expect(source).toContain("getMembershipAccountScenario");
    expect(source).toContain("getLiveMembershipAccountScenario");
    expect(source).toContain("getYudaoMembershipProfile");
    expect(source).toContain("getOrderPage");
    expect(source).toContain("readYudaoToken");
    expect(source).toContain("showScenarioPreview");
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

  it("keeps live order-summary failures separate from membership profile failures", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");

    expect(source).toContain("membershipOrdersLoadError");
    expect(source).toContain("membership.account.ordersUnavailable");
    expect(source).toContain("loadMembershipOrders");
  });

  it("treats membership auth failures as sign-in required instead of pending-link membership", () => {
    const source = readSource("../src/pages/AccountMembershipPage.vue");
    const loadProfileCatch = source.slice(source.indexOf("} catch (error) {"), source.indexOf("};\n\nonMounted"));

    expect(source).toContain("isYudaoAuthError");
    expect(source).toContain("const tokenRequired = ref(false)");
    expect(source).toContain("handleMembershipAuthError");
    expect(source).toContain("if (!isYudaoAuthError(error)) return false");
    expect(source).toContain("tokenRequired.value = true");
    expect(source).toContain("MEMBERSHIP_STATUSES.loggedOut");
    expect(source).toContain("liveOrders.value = []");
    expect(source).toContain("membership.account.signInRequired");
    expect(source).toContain("membership.account.actions.connectAccount");
    expect(source).toContain('v-if="!tokenRequired" class="membership-state-card"');
    expect(source).toContain('v-if="!tokenRequired" class="membership-account-overview"');
    expect(source).toContain('v-if="!tokenRequired" class="membership-account-command-center"');
    expect(source).toContain('v-if="!tokenRequired" class="membership-lifecycle-panel"');
    expect(source).toContain('v-if="!tokenRequired" class="membership-eligibility-panel"');
    expect(loadProfileCatch).toContain("if (handleMembershipAuthError(error)) return");
    expect(loadProfileCatch).toContain("MEMBERSHIP_STATUSES.notMember");
    expect(loadProfileCatch).not.toContain("MEMBERSHIP_STATUSES.pendingLink");
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
