<script setup>
import { computed } from "vue";
import { accountMenuItems, membershipRoutes } from "../services/membershipNavigation.js";
import {
  MEMBERSHIP_STATUSES,
  createMembershipProfile,
  getEmailBindingState,
  getMembershipBenefits,
  getMembershipGrowth,
  getMembershipStatusView,
} from "../services/membershipAccount.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const profile = createMembershipProfile({
  status: MEMBERSHIP_STATUSES.activeAnnual,
  planName: "Annual Membership",
  memberId: "RH-MEMBER-2026",
  memberEmail: "customer@example.com",
  startedAt: "2026-06-03",
  expiresAt: "2027-06-03",
  autoRenew: true,
  growthLevel: "Member",
  growthPoints: 320,
  nextGrowthTarget: 500,
});
const statusView = getMembershipStatusView(profile);
const growth = getMembershipGrowth(profile);
const emailBindingState = getEmailBindingState(profile);
const benefits = getMembershipBenefits(profile);

const accountMenuLabelKeys = {
  Membership: "membership.account.menuMembership",
  "Payment Methods": "membership.account.menuPaymentMethods",
  "Order History": "membership.account.menuOrderHistory",
  "Wish List": "membership.account.menuWishlist",
  "Address Book": "membership.account.menuAddressBook",
  "Account Profile": "membership.account.menuProfile",
  "Gift Registry": "membership.account.menuGiftRegistry",
};
const statusLabelKeys = {
  [MEMBERSHIP_STATUSES.notMember]: "membership.account.statusNotMember",
  [MEMBERSHIP_STATUSES.activeAnnual]: "membership.account.statusActiveAnnual",
  [MEMBERSHIP_STATUSES.activeWholeRoom]: "membership.account.statusActiveWholeRoom",
  [MEMBERSHIP_STATUSES.expired]: "membership.account.statusExpired",
  [MEMBERSHIP_STATUSES.pendingLink]: "membership.account.statusPendingLink",
};
const statusCtaKeys = {
  [MEMBERSHIP_STATUSES.activeAnnual]: "membership.account.viewRules",
  [MEMBERSHIP_STATUSES.activeWholeRoom]: "membership.account.viewRules",
  [MEMBERSHIP_STATUSES.expired]: "membership.account.renewMembership",
  [MEMBERSHIP_STATUSES.pendingLink]: "membership.account.verifyEmail",
};
const benefitKeyByTitle = {
  "Member Savings": "savings",
  "Renewal Management": "renewal",
  "Whole-Room Project Planning": "wholeRoom",
};
const statusLabelKey = computed(() => statusLabelKeys[profile.status] || statusLabelKeys[MEMBERSHIP_STATUSES.notMember]);
const statusCtaKey = computed(() => statusCtaKeys[profile.status] || "membership.account.joinOrRenew");
const statusRows = [
  ["membership.account.rowStatus", statusLabelKey.value],
  ["membership.account.rowPlan", "membership.account.planAnnual"],
  ["membership.account.rowMemberId", profile.memberId],
  ["membership.account.rowRenewal", profile.autoRenew ? "membership.account.autoRenewOn" : "membership.account.manualRenewal"],
  ["membership.account.rowExpires", profile.expiresAt],
];
</script>

<template>
  <section class="account-page account-service-shell">
    <aside class="account-sidebar" :aria-label="t('membership.account.menuAria')">
      <p class="eyebrow">{{ t("membership.account.myAccount") }}</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">
        {{ t(accountMenuLabelKeys[item.label] || "membership.account.menuProfile") }}
      </a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">{{ t("membership.account.eyebrow") }}</p>
      <h1>{{ t("membership.account.title") }}</h1>
      <p>{{ t("membership.account.intro") }}</p>

      <dl class="membership-status-list">
        <div v-for="[label, value] in statusRows" :key="label">
          <dt>{{ t(label) }}</dt>
          <dd>{{ value.startsWith?.("membership.") ? t(value) : value }}</dd>
        </div>
      </dl>

      <section class="membership-status-panel" :class="`is-${statusView.tone}`">
        <div>
          <p class="eyebrow">{{ t("membership.account.currentStatus") }}</p>
          <h2>{{ t(statusLabelKey) }}</h2>
          <p>{{ t("membership.account.statusIntro") }}</p>
        </div>
        <a :href="statusView.ctaHref">{{ t(statusCtaKey) }}</a>
      </section>

      <section class="membership-growth-panel" :aria-label="t('membership.account.growthAria')">
        <div>
          <p class="eyebrow">{{ t("membership.account.growthEyebrow") }}</p>
          <h2>{{ t("membership.account.growthLevelMember") }}</h2>
          <p>
            {{ t("membership.account.growthProgress", { points: growth.points, target: growth.target, remaining: growth.remaining }) }}
          </p>
        </div>
        <div class="membership-progress" aria-hidden="true">
          <span :style="{ width: `${growth.percent}%` }"></span>
        </div>
      </section>

      <section class="membership-email-panel" :aria-label="t('membership.account.emailAria')">
        <p class="eyebrow">{{ t("membership.account.emailEyebrow") }}</p>
        <h2>
          {{
            emailBindingState === "matched"
              ? t("membership.account.emailMatched")
              : t("membership.account.emailVerificationRequired")
          }}
        </h2>
        <p>{{ t("membership.account.accountEmail", { email: profile.accountEmail }) }}</p>
        <p>{{ t("membership.account.memberEmail", { email: profile.memberEmail || t("membership.account.notLinked") }) }}</p>
      </section>

      <section class="membership-benefit-grid" :aria-label="t('membership.account.benefitsAria')">
        <article v-for="benefit in benefits" :key="benefit.title">
          <h3>{{ t(`membership.account.benefits.${benefitKeyByTitle[benefit.title]}.title`) }}</h3>
          <p>{{ t(`membership.account.benefits.${benefitKeyByTitle[benefit.title]}.description`) }}</p>
        </article>
      </section>

      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="membershipRoutes.membershipEnrollment">
          {{ t("membership.account.joinOrRenew") }}
        </a>
        <a :href="membershipRoutes.membershipTerms">{{ t("membership.account.viewRules") }}</a>
      </div>
    </section>
  </section>
</template>
