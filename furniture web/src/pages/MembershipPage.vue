<script setup>
import { getMembershipJoinTarget, membershipRoutes } from "../services/membershipNavigation.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const joinHref = getMembershipJoinTarget({ signedIn: false, memberStatus: "guest" });

const benefits = ["benefit1", "benefit2", "benefit3", "benefit4"];
const flowSteps = ["join", "bind", "price", "manage"];
</script>

<template>
  <section class="membership-page service-page-shell">
    <header class="membership-hero">
      <p class="eyebrow">{{ t("membership.landing.eyebrow") }}</p>
      <h1>{{ t("membership.landing.title") }}</h1>
      <p>{{ t("membership.landing.intro") }}</p>
      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="joinHref">{{ t("membership.landing.joinNow") }}</a>
        <a :href="membershipRoutes.membershipTerms">{{ t("membership.landing.seeTerms") }}</a>
      </div>
    </header>

    <section class="membership-section membership-benefits" :aria-label="t('membership.landing.benefitsAria')">
      <article v-for="benefit in benefits" :key="benefit">
        <span></span>
        <p>{{ t(`membership.landing.${benefit}`) }}</p>
      </article>
    </section>

    <section class="membership-section membership-flow-panel" :aria-label="t('membership.landing.flowAria')">
      <header>
        <p class="eyebrow">{{ t("membership.landing.flowEyebrow") }}</p>
        <h2>{{ t("membership.landing.flowTitle") }}</h2>
        <p>{{ t("membership.landing.flowIntro") }}</p>
      </header>
      <article v-for="step in flowSteps" :key="step">
        <span>{{ t(`membership.landing.flow.${step}.step`) }}</span>
        <h3>{{ t(`membership.landing.flow.${step}.title`) }}</h3>
        <p>{{ t(`membership.landing.flow.${step}.description`) }}</p>
      </article>
    </section>

    <section class="membership-section membership-two-column">
      <div>
        <p class="eyebrow">{{ t("membership.landing.growthEyebrow") }}</p>
        <h2>{{ t("membership.landing.growthTitle") }}</h2>
      </div>
      <p>{{ t("membership.landing.growthIntro") }}</p>
      <a :href="membershipRoutes.accountMembership">{{ t("membership.landing.viewAccountMembership") }}</a>
    </section>
  </section>
</template>
