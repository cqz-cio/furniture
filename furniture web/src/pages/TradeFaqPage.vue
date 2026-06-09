<script setup>
import { ref } from "vue";
import TradeProgramNav from "../components/TradeProgramNav.vue";
import { useI18n } from "../i18n.js";
import { faqItems } from "../services/tradeProgram.js";

const { t } = useI18n();
const openItems = ref(new Set());

const toggle = (key) => {
  const next = new Set(openItems.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  openItems.value = next;
};
</script>

<template>
  <section class="trade-page trade-faq-page">
    <TradeProgramNav />
    <header class="trade-page-header">
      <h1>{{ t("tradeProgram.faq.title") }}</h1>
    </header>
    <div class="trade-faq-list">
      <article v-for="key in faqItems" :key="key" class="trade-faq-item">
        <button type="button" :aria-expanded="openItems.has(key)" @click="toggle(key)">
          <span>{{ openItems.has(key) ? "-" : "+" }}</span>
          {{ t(`tradeProgram.faq.items.${key}.question`) }}
        </button>
        <p v-if="openItems.has(key)">
          {{ t(`tradeProgram.faq.items.${key}.answer`) }}
        </p>
      </article>
    </div>
  </section>
</template>
