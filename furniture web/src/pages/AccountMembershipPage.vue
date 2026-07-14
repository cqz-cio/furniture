<script setup>
import { computed, onMounted, ref } from "vue";
import { accountMenuItems, accountMenuLabelKeys, membershipRoutes } from "../services/membershipNavigation.js";
import {
  MEMBERSHIP_ACCOUNT_SCENARIOS,
  MEMBERSHIP_STATUSES,
  createMembershipProfile,
  getEmailBindingState,
  getLiveMembershipAccountScenario,
  getMembershipEligibilityReview,
  getMembershipAccountScenario,
  getMembershipBenefits,
  getMembershipGrowth,
  getMembershipStatusView,
} from "../services/membershipAccount.js";
import { isYudaoAuthError, readYudaoToken } from "../services/yudaoRequest.js";
import { getYudaoMembershipProfile } from "../services/yudaoMembershipApi.js";
import { getOrderPage } from "../services/yudaoOrderApi.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const selectedScenarioKey = ref("activeAnnual");
const statePreviewKeys = Object.keys(MEMBERSHIP_ACCOUNT_SCENARIOS);
const membershipProfile = ref(createMembershipProfile({ status: MEMBERSHIP_STATUSES.loggedOut }));
const liveOrders = ref([]);
const membershipLoadState = ref("idle");
const membershipLoadError = ref("");
const membershipOrdersLoadError = ref("");
const tokenRequired = ref(false);
const showScenarioPreview = import.meta.env.DEV;

const statusLabelKeys = {
  [MEMBERSHIP_STATUSES.notMember]: "membership.account.statusNotMember",
  [MEMBERSHIP_STATUSES.loggedOut]: "membership.account.statusLoggedOut",
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
const planLabelKeys = {
  "Annual Membership": "membership.account.planAnnual",
  "Whole-Room Membership": "membership.account.planWholeRoom",
  None: "membership.account.planNone",
};
const benefitKeyByTitle = {
  "Member Savings": "savings",
  "Renewal Management": "renewal",
  "Whole-Room Project Planning": "wholeRoom",
};

const scenarioPreview = computed(() => getMembershipAccountScenario(selectedScenarioKey.value));
const currentScenario = computed(() => {
  if (showScenarioPreview && selectedScenarioKey.value !== "live") return scenarioPreview.value;
  return getLiveMembershipAccountScenario(membershipProfile.value, liveOrders.value);
});
const profile = computed(() => currentScenario.value.profile);
const membershipValue = computed(() => currentScenario.value.membershipValue);
const statusView = computed(() => getMembershipStatusView(profile.value));
const growth = computed(() => getMembershipGrowth(profile.value));
const emailBindingState = computed(() => getEmailBindingState(profile.value));
const benefits = computed(() => getMembershipBenefits(profile.value));
const eligibilityReview = computed(() => getMembershipEligibilityReview(currentScenario.value.eligibilityItems || []));
const statusLabelKey = computed(() => statusLabelKeys[profile.value.status] || statusLabelKeys[MEMBERSHIP_STATUSES.notMember]);
const statusCtaKey = computed(() => statusCtaKeys[profile.value.status] || "membership.account.joinOrRenew");
const overviewRows = computed(() => [
  ["membership.account.overview.savings", membershipValue.value.annualSavings],
  ["membership.account.overview.eligibleSpend", membershipValue.value.eligibleSpend],
  [
    "membership.account.overview.renewalWindow",
    Number.isFinite(Number(membershipValue.value.renewalWindow))
      ? t("membership.account.overview.days", { count: membershipValue.value.renewalWindow })
      : t(`membership.account.overview.values.${String(membershipValue.value.renewalWindow).toLowerCase()}`),
  ],
]);
const statusRows = computed(() => [
  ["membership.account.rowStatus", statusLabelKey.value],
  ["membership.account.rowPlan", planLabelKeys[profile.value.planName] || profile.value.planName],
  ["membership.account.rowMemberId", profile.value.memberId || "membership.account.notLinked"],
  ["membership.account.rowRenewal", profile.value.autoRenew ? "membership.account.autoRenewOn" : "membership.account.manualRenewal"],
  ["membership.account.rowExpires", profile.value.expiresAt || "membership.account.notLinked"],
]);
const lifecycleSteps = computed(() => [
  ["membership.account.lifecycle.started", profile.value.startedAt || t("membership.account.notLinked")],
  ["membership.account.lifecycle.active", t(statusLabelKey.value)],
  ["membership.account.lifecycle.renews", profile.value.expiresAt || t("membership.account.notLinked")],
]);
const accountChecklistItems = ["renewal", "email", "orders", "rules"];
const renewalActions = ["changePayment", "turnOffAutoRenew", "renewEarly"];
const emailActions = ["verify", "changeEmail"];
const recentOrders = computed(() => currentScenario.value.orders);
const money = (value) => `$${Number(value || 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const loadMembershipOrders = async () => {
  membershipOrdersLoadError.value = "";
  try {
    const orderPage = await getOrderPage({ pageNo: 1, pageSize: 5 });
    liveOrders.value = orderPage.list || [];
  } catch {
    liveOrders.value = [];
    membershipOrdersLoadError.value = t("membership.account.ordersUnavailable");
  }
};

const handleMembershipAuthError = (error) => {
  if (!isYudaoAuthError(error)) return false;
  tokenRequired.value = true;
  membershipLoadError.value = "";
  membershipProfile.value = createMembershipProfile({ status: MEMBERSHIP_STATUSES.loggedOut });
  liveOrders.value = [];
  membershipOrdersLoadError.value = "";
  selectedScenarioKey.value = "live";
  membershipLoadState.value = "logged-out";
  return true;
};

const loadMembershipProfile = async () => {
  if (!readYudaoToken()) {
    tokenRequired.value = true;
    membershipProfile.value = createMembershipProfile({ status: MEMBERSHIP_STATUSES.loggedOut });
    liveOrders.value = [];
    membershipOrdersLoadError.value = "";
    membershipLoadState.value = "logged-out";
    return;
  }
  tokenRequired.value = false;
  membershipLoadState.value = "loading";
  membershipLoadError.value = "";
  try {
    const nextProfile = await getYudaoMembershipProfile();
    membershipProfile.value = nextProfile;
    await loadMembershipOrders();
    selectedScenarioKey.value = "live";
    membershipLoadState.value = "loaded";
  } catch (error) {
    if (handleMembershipAuthError(error)) return;
    membershipLoadError.value = error?.message || "Membership profile unavailable";
    membershipProfile.value = createMembershipProfile({ status: MEMBERSHIP_STATUSES.notMember });
    liveOrders.value = [];
    selectedScenarioKey.value = "live";
    membershipLoadState.value = "error";
  }
};

onMounted(loadMembershipProfile);
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

      <section v-if="membershipLoadState === 'loading'" class="membership-state-empty">
        <div>
          <p class="eyebrow">{{ t("membership.account.loadingEyebrow") }}</p>
          <h2>{{ t("membership.account.loadingTitle") }}</h2>
          <p>{{ t("membership.account.loadingDescription") }}</p>
        </div>
      </section>

      <section v-if="tokenRequired" class="membership-state-empty">
        <div>
          <p class="eyebrow">{{ t("membership.account.errorEyebrow") }}</p>
          <h2>{{ t("membership.account.statusLoggedOut") }}</h2>
          <p>{{ t("membership.account.signInRequired") }}</p>
        </div>
        <a class="orders-recovery-action" :href="membershipRoutes.checkoutAuth">
          {{ t("membership.account.actions.connectAccount") }}
        </a>
      </section>

      <section v-if="membershipLoadError" class="membership-state-empty">
        <div>
          <p class="eyebrow">{{ t("membership.account.errorEyebrow") }}</p>
          <h2>{{ t("membership.account.errorTitle") }}</h2>
          <p>{{ membershipLoadError }}</p>
        </div>
      </section>

      <section v-if="showScenarioPreview" class="membership-account-state-strip" :aria-label="t('membership.account.states.aria')">
        <header>
          <p class="eyebrow">{{ t("membership.account.states.eyebrow") }}</p>
          <h2>{{ t("membership.account.states.title") }}</h2>
        </header>
        <div>
          <button
            type="button"
            :class="{ 'is-selected': selectedScenarioKey === 'live' }"
            @click="selectedScenarioKey = 'live'"
          >
            Live API
          </button>
          <button
            v-for="key in statePreviewKeys"
            :key="key"
            type="button"
            :class="{ 'is-selected': selectedScenarioKey === key }"
            @click="selectedScenarioKey = key"
          >
            {{ t(`membership.account.states.${key}.label`) }}
          </button>
        </div>
      </section>

      <section v-if="!tokenRequired" class="membership-state-card" :class="{ 'is-attention': currentScenario.requiresAttention }">
        <div>
          <p class="eyebrow">{{ t(`membership.account.states.${currentScenario.key}.eyebrow`) }}</p>
          <h2>{{ t(`membership.account.states.${currentScenario.key}.title`) }}</h2>
          <p>{{ t(`membership.account.states.${currentScenario.key}.description`) }}</p>
        </div>
        <a :href="statusView.ctaHref">{{ t(statusCtaKey) }}</a>
      </section>

      <section v-if="!tokenRequired" class="membership-account-overview" :aria-label="t('membership.account.overviewAria')">
        <article v-for="[label, value] in overviewRows" :key="label">
          <strong>{{ value }}</strong>
          <span>{{ t(label) }}</span>
        </article>
      </section>

      <section v-if="!tokenRequired" class="membership-account-command-center">
        <section class="membership-status-panel" :class="`is-${statusView.tone}`">
          <div>
            <p class="eyebrow">{{ t("membership.account.currentStatus") }}</p>
            <span class="membership-status-badge" :class="`is-${statusView.tone}`">{{ t(statusLabelKey) }}</span>
            <h2>{{ t(statusLabelKey) }}</h2>
            <p>{{ t("membership.account.statusIntro") }}</p>
          </div>
          <a :href="statusView.ctaHref">{{ t(statusCtaKey) }}</a>
        </section>

        <dl class="membership-status-list">
          <div v-for="[label, value] in statusRows" :key="label">
            <dt>{{ t(label) }}</dt>
            <dd>{{ value.startsWith?.("membership.") ? t(value) : value }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="!tokenRequired" class="membership-lifecycle-panel" :aria-label="t('membership.account.lifecycleAria')">
        <header>
          <p class="eyebrow">{{ t("membership.account.lifecycleEyebrow") }}</p>
          <h2>{{ t("membership.account.lifecycleTitle") }}</h2>
        </header>
        <ol class="membership-lifecycle-list">
          <li v-for="[label, value] in lifecycleSteps" :key="label">
            <span>{{ t(label) }}</span>
            <strong>{{ value }}</strong>
          </li>
        </ol>
      </section>

      <section v-if="currentScenario.emptyStateKey" class="membership-state-empty">
        <div>
          <p class="eyebrow">{{ t(`membership.account.emptyStates.${currentScenario.emptyStateKey}.eyebrow`) }}</p>
          <h2>{{ t(`membership.account.emptyStates.${currentScenario.emptyStateKey}.title`) }}</h2>
          <p>{{ t(`membership.account.emptyStates.${currentScenario.emptyStateKey}.description`) }}</p>
        </div>
        <a :href="statusView.ctaHref">{{ t(statusCtaKey) }}</a>
      </section>

      <section v-if="currentScenario.hasActiveBenefits" class="membership-account-checklist" :aria-label="t('membership.account.checklistAria')">
        <header>
          <p class="eyebrow">{{ t("membership.account.checklistEyebrow") }}</p>
          <h2>{{ t("membership.account.checklistTitle") }}</h2>
        </header>
        <article v-for="item in accountChecklistItems" :key="item">
          <span>{{ t(`membership.account.checklist.${item}.status`) }}</span>
          <strong>{{ t(`membership.account.checklist.${item}.title`) }}</strong>
          <p>{{ t(`membership.account.checklist.${item}.description`) }}</p>
        </article>
      </section>

      <section v-if="currentScenario.hasActiveBenefits" class="membership-account-operations">
        <section class="membership-renewal-panel" :aria-label="t('membership.account.renewalAria')">
          <div>
            <p class="eyebrow">{{ t("membership.account.renewalEyebrow") }}</p>
            <h2>{{ profile.autoRenew ? t("membership.account.autoRenewOn") : t("membership.account.manualRenewal") }}</h2>
            <p>{{ t("membership.account.renewalIntro", { date: profile.expiresAt }) }}</p>
          </div>
          <div class="membership-management-actions">
            <a v-for="action in renewalActions" :key="action" :href="membershipRoutes.accountMembership">
              {{ t(`membership.account.renewalActions.${action}`) }}
            </a>
          </div>
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
          <div class="membership-management-actions">
            <a v-for="action in emailActions" :key="action" :href="membershipRoutes.accountMembership">
              {{ t(`membership.account.emailActions.${action}`) }}
            </a>
          </div>
        </section>
      </section>

      <section v-if="currentScenario.hasActiveBenefits" class="membership-savings-panel" :aria-label="t('membership.account.savingsAria')">
        <div>
          <p class="eyebrow">{{ t("membership.account.savingsEyebrow") }}</p>
          <h2>{{ t("membership.account.savingsTitle", { amount: membershipValue.annualSavings }) }}</h2>
          <p>{{ t("membership.account.savingsIntro", { spend: membershipValue.eligibleSpend }) }}</p>
          <p v-if="membershipOrdersLoadError" class="orders-payment-warning">{{ membershipOrdersLoadError }}</p>
        </div>
        <section class="membership-order-list" :aria-label="t('membership.account.ordersAria')">
          <article v-for="order in recentOrders" :key="order.id">
            <div>
              <h3>{{ order.label || t(`membership.account.orders.${order.key}`) }}</h3>
              <p>{{ t("membership.account.orderMeta", { id: order.id, date: order.date }) }}</p>
            </div>
            <strong>{{ t("membership.account.orderSavings", { amount: order.savings }) }}</strong>
          </article>
        </section>
      </section>

      <section v-if="!tokenRequired" class="membership-eligibility-panel" :aria-label="t('membership.account.eligibility.aria')">
        <header>
          <div>
            <p class="eyebrow">{{ t("membership.account.eligibility.eyebrow") }}</p>
            <h2>{{ t("membership.account.eligibility.title") }}</h2>
            <p>{{ t("membership.account.eligibility.intro") }}</p>
          </div>
          <dl>
            <div>
              <dt>{{ t("membership.account.eligibility.summary.eligible") }}</dt>
              <dd>{{ eligibilityReview.eligibleCount }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.summary.ineligible") }}</dt>
              <dd>{{ eligibilityReview.ineligibleCount }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.summary.savings") }}</dt>
              <dd>{{ money(eligibilityReview.savingsTotal) }}</dd>
            </div>
          </dl>
        </header>

        <article
          v-for="line in eligibilityReview.lines"
          :key="`${line.name}-${line.category}`"
          class="membership-eligibility-row"
          :class="{ 'is-eligible': line.eligible }"
        >
          <div>
            <span>{{ t(`membership.account.eligibility.reasons.${line.key}.label`) }}</span>
            <h3>{{ line.name }}</h3>
            <p>{{ t(`membership.account.eligibility.reasons.${line.key}.description`) }}</p>
          </div>
          <dl>
            <div>
              <dt>{{ t("membership.account.eligibility.line.regular") }}</dt>
              <dd>{{ money(line.regularPrice) }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.line.member") }}</dt>
              <dd>{{ money(line.memberPrice) }}</dd>
            </div>
            <div>
              <dt>{{ t("membership.account.eligibility.line.savings") }}</dt>
              <dd>{{ money(line.savings) }}</dd>
            </div>
          </dl>
        </article>
      </section>

      <section v-if="currentScenario.hasActiveBenefits" class="membership-benefit-grid" :aria-label="t('membership.account.benefitsAria')">
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
