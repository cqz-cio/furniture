<script setup>
import AuthTradeSignInForm from "../components/AuthTradeSignInForm.vue";
import TradeProgramNav from "../components/TradeProgramNav.vue";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
</script>

<template>
  <section class="trade-page trade-sign-in-page">
    <TradeProgramNav />
    <header class="trade-page-header">
      <h1>{{ t("tradeProgram.signIn.title") }}</h1>
      <p>{{ t("tradeProgram.signIn.intro") }}</p>
    </header>
    <div class="trade-sign-in-panel">
      <AuthTradeSignInForm :show-return-link="false" />
    </div>
  </section>
</template>
