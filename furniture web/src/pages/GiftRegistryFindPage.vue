<script setup>
import { membershipRoutes } from "../services/membershipNavigation.js";

const sampleRegistries = [
  {
    name: "Avery Stone & Morgan Vale",
    event: "Wedding",
    date: "2026-10-01",
    location: "Boston, MA",
  },
  {
    name: "Jordan Lee",
    event: "Housewarming",
    date: "2026-09-15",
    location: "Chicago, IL",
  },
];
</script>

<template>
  <section class="membership-page registry-workflow-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">Gift Registry</p>
      <h1>Find a Registry</h1>
      <p>
        Guests can search public registries by registrant name, event date or email, then enter the registry shopping
        path.
      </p>
      <div class="membership-actions">
        <a class="membership-primary-link" :href="membershipRoutes.giftRegistryCreate">Create a Registry</a>
        <a :href="membershipRoutes.giftRegistryManage">Manage Registry</a>
      </div>
    </header>

    <section class="registry-search-panel" aria-label="Find registry search">
      <label>
        Registrant Name
        <input value="Avery Stone" aria-label="Registrant name" />
      </label>
      <label>
        Event Month
        <select aria-label="Event month">
          <option>October 2026</option>
          <option>September 2026</option>
          <option>August 2026</option>
        </select>
      </label>
      <button type="button">Search</button>
    </section>

    <section class="registry-result-list" aria-label="Registry results">
      <article v-for="registry in sampleRegistries" :key="registry.name">
        <div>
          <p class="eyebrow">{{ registry.event }}</p>
          <h2>{{ registry.name }}</h2>
          <p>{{ registry.date }} · {{ registry.location }}</p>
        </div>
        <a :href="membershipRoutes.giftRegistry">View Registry</a>
      </article>
    </section>
  </section>
</template>
