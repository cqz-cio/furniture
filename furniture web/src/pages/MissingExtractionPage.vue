<script setup>
import { extractionBacklog } from "../data/rhLayout.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
</script>

<template>
  <section class="missing-page">
    <p class="eyebrow">{{ t("placeholder.missing.eyebrow") }}</p>
    <h1>{{ t("placeholder.missing.title") }}</h1>
    <p>
      {{ t("placeholder.missing.description") }}
    </p>
    <div class="backlog-list">
      <article v-for="item in extractionBacklog" :key="item.label">
        <strong>{{ item.label }}</strong>
        <span>{{ item.status }}</span>
        <p>{{ item.needed }}</p>
      </article>
    </div>
  </section>
</template>
