<script setup>
import { onMounted, reactive, ref } from "vue";
import { useI18n } from "../i18n.js";
import { canUseGiftRegistryDemoFallback, createGiftRegistryDraft } from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { searchPublicYudaoGiftRegistries } from "../services/yudaoGiftRegistryApi.js";

const { t } = useI18n();
const query = reactive({
  keyword: "",
  eventMonth: "",
});
const results = ref([]);
const total = ref(0);
const searchState = ref("idle");
const searchMessage = ref("");

const runSearch = async () => {
  searchState.value = "loading";
  searchMessage.value = "";
  try {
    const page = await searchPublicYudaoGiftRegistries({
      keyword: query.keyword,
      eventMonth: query.eventMonth,
      pageNo: 1,
      pageSize: 10,
    });
    results.value = page.list;
    total.value = page.total;
    searchState.value = page.list.length ? "loaded" : "empty";
    searchMessage.value = page.list.length ? "" : t("giftRegistry.find.empty");
  } catch (error) {
    if (!import.meta.env.PROD && canUseGiftRegistryDemoFallback(import.meta.env)) {
      results.value = [
        createGiftRegistryDraft({
          publicCode: "local-preview-registry",
          event: {
            type: t("giftRegistry.find.previewCard.eventType"),
            date: "2026-10-01",
            location: t("giftRegistry.find.previewCard.location"),
          },
          registrants: {
            primaryName: t("giftRegistry.find.previewCard.primaryName"),
            coRegistrantName: t("giftRegistry.find.previewCard.coRegistrantName"),
          },
        }),
      ];
      total.value = 1;
      searchState.value = "preview";
      searchMessage.value = t("giftRegistry.find.preview");
      return;
    }
    results.value = [];
    total.value = 0;
    searchState.value = "error";
    searchMessage.value = error?.message || t("giftRegistry.find.unavailable");
  }
};

onMounted(runSearch);
</script>

<template>
  <section class="membership-page service-page-shell registry-workflow-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">{{ t("giftRegistry.eyebrow") }}</p>
      <h1>{{ t("giftRegistry.find.title") }}</h1>
      <p>{{ t("giftRegistry.find.description") }}</p>
      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="membershipRoutes.giftRegistryCreate">{{ t("giftRegistry.find.create") }}</a>
        <a :href="membershipRoutes.giftRegistryManage">{{ t("giftRegistry.find.manage") }}</a>
      </div>
    </header>

    <section class="registry-search-panel" aria-label="Find registry search">
      <label>
        {{ t("giftRegistry.find.fields.keywordLabel") }}
        <input
          v-model="query.keyword"
          :placeholder="t('giftRegistry.find.fields.keywordPlaceholder')"
          aria-label="Registrant name or email"
        />
      </label>
      <label>
        {{ t("giftRegistry.find.fields.eventMonthLabel") }}
        <input
          v-model="query.eventMonth"
          aria-label="Event month"
          :placeholder="t('giftRegistry.find.fields.eventMonthPlaceholder')"
        />
      </label>
      <button type="button" @click="runSearch">{{ t("giftRegistry.find.search") }}</button>
    </section>

    <p v-if="searchMessage">{{ searchMessage }}</p>

    <section class="registry-result-list" aria-label="Registry results">
      <article v-for="registry in results" :key="registry.publicCode || registry.id">
        <div>
          <p class="eyebrow">{{ registry.event.type }}</p>
          <h2>{{ registry.registrants.primaryName }} {{ registry.registrants.coRegistrantName }}</h2>
          <p>{{ registry.event.date }} - {{ registry.event.location || t("giftRegistry.find.locationPrivate") }}</p>
        </div>
        <a :href="`/gift-registry/${registry.publicCode}`">{{ t("giftRegistry.find.view") }}</a>
      </article>
    </section>
  </section>
</template>
