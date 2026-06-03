<script setup>
import { membershipRoutes } from "../services/membershipNavigation.js";

const registryEntries = [
  { title: "Find a Registry", href: membershipRoutes.giftRegistryFind },
  { title: "Create a Registry", href: membershipRoutes.giftRegistryCreate },
  { title: "Manage Your Registry", href: membershipRoutes.giftRegistryManage },
];
</script>

<template>
  <section class="membership-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">Gift Registry</p>
      <h1>Find, create and manage furniture gift registries.</h1>
      <p>Registry planning includes event details, registrant information, delivery addresses and privacy controls.</p>
    </header>

    <section class="registry-entry-list" aria-label="Gift registry actions">
      <a v-for="entry in registryEntries" :key="entry.title" :href="entry.href">{{ entry.title }}</a>
    </section>
  </section>
</template>
