<script setup>
import { accountMenuItems, membershipRoutes } from "../services/membershipNavigation.js";
import {
  MEMBERSHIP_STATUSES,
  createMembershipProfile,
  getEmailBindingState,
  getMembershipBenefits,
  getMembershipGrowth,
  getMembershipStatusView,
} from "../services/membershipAccount.js";

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
const statusRows = [
  ["Status", statusView.label],
  ["Plan", profile.planName],
  ["Member ID", profile.memberId],
  ["Renewal", profile.autoRenew ? "Auto-renewal on" : "Manual renewal"],
  ["Expires", profile.expiresAt],
];
</script>

<template>
  <section class="account-page account-service-shell">
    <aside class="account-sidebar" aria-label="My Account navigation">
      <p class="eyebrow">My Account</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">{{ item.label }}</a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">Membership</p>
      <h1>Membership status and rules live here.</h1>
      <p>
        This area manages member status, email matching, renewal controls, benefit snapshots and the member growth
        system after sign in.
      </p>

      <dl class="membership-status-list">
        <div v-for="[label, value] in statusRows" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>

      <section class="membership-status-panel" :class="`is-${statusView.tone}`">
        <div>
          <p class="eyebrow">Current Status</p>
          <h2>{{ statusView.label }}</h2>
          <p>Plan details, renewal controls and terms remain visible from this account destination.</p>
        </div>
        <a :href="statusView.ctaHref">{{ statusView.ctaLabel }}</a>
      </section>

      <section class="membership-growth-panel" aria-label="Membership growth">
        <div>
          <p class="eyebrow">Member Growth</p>
          <h2>{{ growth.level }}</h2>
          <p>{{ growth.points }} / {{ growth.target }} points. {{ growth.remaining }} points to the next milestone.</p>
        </div>
        <div class="membership-progress" aria-hidden="true">
          <span :style="{ width: `${growth.percent}%` }"></span>
        </div>
      </section>

      <section class="membership-email-panel" aria-label="Membership email binding">
        <p class="eyebrow">Email Binding</p>
        <h2>{{ emailBindingState === "matched" ? "Membership email matched" : "Verification required" }}</h2>
        <p>Account email: {{ profile.accountEmail }}</p>
        <p>Member email: {{ profile.memberEmail || "Not linked" }}</p>
      </section>

      <section class="membership-benefit-grid" aria-label="Active benefits">
        <article v-for="benefit in benefits" :key="benefit.title">
          <h3>{{ benefit.title }}</h3>
          <p>{{ benefit.description }}</p>
        </article>
      </section>

      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="membershipRoutes.membershipEnrollment">Join or Renew</a>
        <a :href="membershipRoutes.membershipTerms">View Rules</a>
      </div>
    </section>
  </section>
</template>
