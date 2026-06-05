<script setup>
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const questions = ["join", "growth", "rules"];
</script>

<template>
  <section class="membership-page membership-narrow service-page-shell">
    <header class="membership-page-head">
      <p class="eyebrow">{{ t("membership.faq.eyebrow") }}</p>
      <h1>{{ t("membership.faq.title") }}</h1>
      <p>{{ t("membership.faq.intro") }}</p>
    </header>

    <section class="terms-list" :aria-label="t('membership.faq.aria')">
      <article v-for="question in questions" :key="question">
        <h2>{{ t(`membership.faq.${question}Question`) }}</h2>
        <p>{{ t(`membership.faq.${question}Answer`) }}</p>
      </article>
    </section>
  </section>
</template>
