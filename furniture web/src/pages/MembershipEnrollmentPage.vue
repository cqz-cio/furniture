<script setup>
import { ANNUAL_MEMBERSHIP_PRODUCT } from "../services/membershipCart.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { useI18n } from "../i18n.js";

const emit = defineEmits(["add-to-cart"]);
const { t } = useI18n();

const plans = [
  {
    titleKey: "membership.enrollment.annualTitle",
    priceKey: "membership.enrollment.annualPrice",
    descriptionKey: "membership.enrollment.annualDescription",
  },
  {
    titleKey: "membership.enrollment.wholeRoomTitle",
    priceKey: "membership.enrollment.wholeRoomPrice",
    descriptionKey: "membership.enrollment.wholeRoomDescription",
  },
];

const addAnnualMembership = () => {
  emit("add-to-cart", ANNUAL_MEMBERSHIP_PRODUCT, 1);
};
</script>

<template>
  <section class="membership-page membership-narrow service-page-shell">
    <header class="membership-page-head">
      <p class="eyebrow">{{ t("membership.enrollment.eyebrow") }}</p>
      <h1>{{ t("membership.enrollment.title") }}</h1>
      <p>{{ t("membership.enrollment.intro") }}</p>
    </header>

    <section class="membership-plan-list" :aria-label="t('membership.enrollment.plansAria')">
      <article v-for="plan in plans" :key="plan.titleKey">
        <div>
          <h2>{{ t(plan.titleKey) }}</h2>
          <p>{{ t(plan.descriptionKey) }}</p>
        </div>
        <strong>{{ t(plan.priceKey) }}</strong>
      </article>
    </section>

    <section class="membership-agreement">
      <label>
        <input type="checkbox" />
        <span>{{ t("membership.enrollment.agreement") }}</span>
      </label>
      <a :href="membershipRoutes.membershipTerms">{{ t("membership.enrollment.readTerms") }}</a>
    </section>

    <div class="membership-actions">
      <button class="membership-primary-button" type="button" @click="addAnnualMembership">
        {{ t("membership.enrollment.addToBag") }}
      </button>
      <a :href="membershipRoutes.checkoutAuth">{{ t("membership.enrollment.continueCheckout") }}</a>
    </div>
  </section>
</template>
