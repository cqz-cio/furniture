<script setup>
import { useI18n } from "../i18n.js";
import { tradeRoutes } from "../services/tradeProgram.js";

const { t } = useI18n();
</script>

<template>
  <nav class="trade-program-nav" :aria-label="t('tradeProgram.nav.aria')">
    <a :href="tradeRoutes.signIn">{{ t("tradeProgram.nav.signIn") }}</a>
    <a :href="tradeRoutes.apply">{{ t("tradeProgram.nav.apply") }}</a>
    <a :href="tradeRoutes.faq">{{ t("tradeProgram.nav.faq") }}</a>
  </nav>
</template>
