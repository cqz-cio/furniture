<script setup>
import { useI18n } from "../i18n.js";
import { accountMenuItems, accountMenuLabelKeys } from "../services/membershipNavigation.js";

const { t } = useI18n();
</script>

<template>
  <section class="account-page">
    <aside class="account-sidebar" :aria-label="t('membership.account.menuAria')">
      <p class="eyebrow">{{ t("membership.account.myAccount") }}</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">
        {{ t(accountMenuLabelKeys[item.label]) }}
      </a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">{{ t("membership.account.dashboardEyebrow") }}</p>
      <h1>{{ t("membership.account.dashboardTitle") }}</h1>
      <p>{{ t("membership.account.dashboardDescription") }}</p>
    </section>
  </section>
</template>
