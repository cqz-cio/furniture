<script setup>
import { onMounted, reactive, ref } from "vue";
import { canUseGiftRegistryDemoFallback, createGiftRegistryDraft } from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { searchPublicYudaoGiftRegistries } from "../services/yudaoGiftRegistryApi.js";

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
    searchMessage.value = page.list.length ? "" : "No public registries matched that search.";
  } catch (error) {
    if (!import.meta.env.PROD && canUseGiftRegistryDemoFallback(import.meta.env)) {
      results.value = [
        createGiftRegistryDraft({
          publicCode: "local-preview-registry",
          event: { type: "Preview", date: "2026-10-01", location: "Local preview" },
          registrants: { primaryName: "Local Preview", coRegistrantName: "Registry" },
        }),
      ];
      total.value = 1;
      searchState.value = "preview";
      searchMessage.value = "Local preview is shown because the backend is unavailable.";
      return;
    }
    results.value = [];
    total.value = 0;
    searchState.value = "error";
    searchMessage.value = error?.message || "Public registry search is unavailable.";
  }
};

onMounted(runSearch);
</script>

<template>
  <section class="membership-page service-page-shell registry-workflow-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">Gift Registry</p>
      <h1>Find a Registry</h1>
      <p>
        Guests can search public registries by registrant name, event date or email, then enter the registry shopping
        path.
      </p>
      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="membershipRoutes.giftRegistryCreate">Create a Registry</a>
        <a :href="membershipRoutes.giftRegistryManage">Manage Registry</a>
      </div>
    </header>

    <section class="registry-search-panel" aria-label="Find registry search">
      <label>
        Registrant Name or Email
        <input v-model="query.keyword" aria-label="Registrant name or email" />
      </label>
      <label>
        Event Month
        <input v-model="query.eventMonth" aria-label="Event month" placeholder="YYYY-MM" />
      </label>
      <button type="button" @click="runSearch">Search</button>
    </section>

    <p v-if="searchMessage">{{ searchMessage }}</p>

    <section class="registry-result-list" aria-label="Registry results">
      <article v-for="registry in results" :key="registry.publicCode || registry.id">
        <div>
          <p class="eyebrow">{{ registry.event.type }}</p>
          <h2>{{ registry.registrants.primaryName }} {{ registry.registrants.coRegistrantName }}</h2>
          <p>{{ registry.event.date }} - {{ registry.event.location || "Location private" }}</p>
        </div>
        <a :href="`/gift-registry/${registry.publicCode}`">View Registry</a>
      </article>
    </section>
  </section>
</template>
