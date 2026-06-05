<script setup>
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const sections = ["eligibility", "renewal", "emailBinding", "checkoutRules"];
</script>

<template>
  <section class="membership-page membership-narrow service-page-shell">
    <header class="membership-page-head">
      <p class="eyebrow">{{ t("membership.terms.eyebrow") }}</p>
      <h1>{{ t("membership.terms.title") }}</h1>
      <p>{{ t("membership.terms.intro") }}</p>
    </header>

    <section class="terms-list" :aria-label="t('membership.terms.aria')">
      <article v-for="section in sections" :key="section">
        <h2>{{ t(`membership.terms.${section}Title`) }}</h2>
        <p>{{ t(`membership.terms.${section}Description`) }}</p>
      </article>
    </section>
  </section>
</template>
