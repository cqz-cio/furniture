import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const sliceBetween = (source, start, end) => source.slice(source.indexOf(start), source.indexOf(end));

describe("profile page recovery actions", () => {
  it("offers recovery actions for sign-in, load, profile, and mobile failures", () => {
    const source = readSource("../src/pages/AccountProfilePage.vue");

    expect(source).toContain("isYudaoAuthError");
    expect(source).toContain("errorAction = ref(\"\")");
    expect(source).toContain("canEditProfile");
    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).toContain('errorAction.value = "retryProfile"');
    expect(source).toContain('errorAction.value = "profileForm"');
    expect(source).toContain('errorAction.value = "mobileForm"');
    expect(source).toContain('t("membership.account.profile.actions.connectAccount")');
    expect(source).toContain('t("membership.account.profile.actions.retry")');
    expect(source).toContain('t("membership.account.profile.actions.reviewProfile")');
    expect(source).toContain('t("membership.account.profile.actions.reviewPhone")');
    expect(source).toContain('@click="loadProfile"');
    expect(source).toContain('href="#account-profile-form"');
    expect(source).toContain('href="#account-mobile-form"');
    expect(source).toContain("orders-recovery-actions");
    expect(source).toContain("orders-recovery-action");
  });

  it("treats expired Yudao sessions as sign-in-required across profile mutations", () => {
    const source = readSource("../src/pages/AccountProfilePage.vue");

    expect(source).toContain("const handleProfileAuthError = (error) =>");
    expect(source).toContain("if (!isYudaoAuthError(error)) return false");
    expect(source).toContain("tokenRequired.value = true");
    expect(source).toContain("profile.value = null");
    expect(source).toContain("return true");

    for (const [marker, nextMarker] of [
      ["const loadProfile = async", "const submitProfile = async"],
      ["const submitProfile = async", "const requestEmailVerification = async"],
      ["const requestEmailVerification = async", "const requestMobileCode = async"],
      ["const requestMobileCode = async", "const submitMobile = async"],
      ["const submitMobile = async", "onMounted("],
    ]) {
      const block = sliceBetween(source, marker, nextMarker);
      expect(block).toContain("catch (error)");
      expect(block).toContain("if (handleProfileAuthError(error)) return;");
    }
  });
});
