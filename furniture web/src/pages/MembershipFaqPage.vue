<script setup>
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const topics = ["account", "pricing", "support"];
const questions = ["join", "bind", "pricing", "renewal", "growth", "rules"];
</script>

<template>
  <section class="membership-page membership-narrow service-page-shell">
    <header class="membership-page-head">
      <p class="eyebrow">{{ t("membership.faq.eyebrow") }}</p>
      <h1>{{ t("membership.faq.title") }}</h1>
      <p>{{ t("membership.faq.intro") }}</p>
    </header>

    <section class="membership-faq-layout" :aria-label="t('membership.faq.aria')">
      <aside class="membership-faq-topics" :aria-label="t('membership.faq.topicsAria')">
        <header>
          <p class="eyebrow">{{ t("membership.faq.topicsEyebrow") }}</p>
          <h2>{{ t("membership.faq.topicsTitle") }}</h2>
        </header>
        <article v-for="topic in topics" :key="topic">
          <span>{{ t(`membership.faq.topics.${topic}.label`) }}</span>
          <p>{{ t(`membership.faq.topics.${topic}.description`) }}</p>
        </article>
      </aside>

      <section class="membership-faq-list" :aria-label="t('membership.faq.listAria')">
        <details v-for="question in questions" :key="question" :open="question === 'join'">
          <summary>
            <span>{{ t(`membership.faq.${question}Badge`) }}</span>
            <strong>{{ t(`membership.faq.${question}Question`) }}</strong>
          </summary>
          <p>{{ t(`membership.faq.${question}Answer`) }}</p>
        </details>
      </section>
    </section>
  </section>
</template>
