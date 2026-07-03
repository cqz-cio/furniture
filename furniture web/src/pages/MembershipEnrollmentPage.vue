<script setup>
import { ref } from "vue";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { readYudaoToken } from "../services/yudaoRequest.js";
import { createYudaoMembershipCheckoutIntent } from "../services/yudaoMembershipApi.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const statusMessageKey = ref("");
const busy = ref(false);
const checkoutHref = ref(`${membershipRoutes.checkoutAuth}?intent=membership`);

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

const startMembershipCheckout = async () => {
  statusMessageKey.value = "";
  if (!readYudaoToken()) {
    statusMessageKey.value = "membership.enrollment.signInRequired";
    return;
  }
  busy.value = true;
  try {
    const intent = await createYudaoMembershipCheckoutIntent({ planCode: "annual_membership" });
    checkoutHref.value = intent?.checkoutPath || checkoutHref.value;
    statusMessageKey.value = "membership.enrollment.checkoutReady";
  } catch {
    statusMessageKey.value = "membership.enrollment.checkoutFailed";
  } finally {
    busy.value = false;
  }
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
      <button class="membership-primary-button" type="button" :disabled="busy" @click="startMembershipCheckout">
        {{ busy ? t("common.working") : t("membership.enrollment.addToBag") }}
      </button>
      <a :href="checkoutHref">{{ t("membership.enrollment.continueCheckout") }}</a>
    </div>
    <p v-if="statusMessageKey" class="membership-enrollment-status" role="status">{{ t(statusMessageKey) }}</p>
  </section>
</template>
