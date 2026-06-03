<script setup>
import { createGiftRegistryDraft, getGiftRegistrySteps, getRegistryShareState } from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";

const registry = createGiftRegistryDraft({
  id: "registry-stone-2026",
  event: {
    type: "Wedding",
    date: "2026-10-01",
  },
  registrants: {
    primaryName: "Avery Stone",
    coRegistrantName: "Morgan Vale",
    email: "avery@example.com",
  },
  addresses: {
    beforeEvent: { line1: "123 Oak Road", city: "Boston", region: "MA", postalCode: "02116" },
    afterEvent: { line1: "48 Gallery Lane", city: "New York", region: "NY", postalCode: "10013" },
  },
});

const steps = getGiftRegistrySteps(registry);
const shareState = getRegistryShareState(registry);
const ownerActions = [
  {
    title: "Registry Visibility",
    description: "Review public, email-searchable or invite-only access.",
    cta: "Edit Visibility",
    href: `${membershipRoutes.giftRegistryManage}?section=visibility`,
  },
  {
    title: "Gift Card & Email Preferences",
    description: "Control gift card acceptance and registry communication preferences.",
    cta: "Edit Preferences",
    href: `${membershipRoutes.giftRegistryManage}?section=preferences`,
  },
  {
    title: "Delivery Addresses",
    description: "Maintain before-event and after-event delivery destinations.",
    cta: "Edit Addresses",
    href: `${membershipRoutes.giftRegistryManage}?section=addresses`,
  },
  {
    title: "Purchased Gifts",
    description: "Items purchased from the registry are automatically marked as purchased.",
    cta: "View Gifts",
    href: `${membershipRoutes.giftRegistryManage}?section=purchased`,
  },
];
</script>

<template>
  <section class="account-page account-service-shell">
    <aside class="account-sidebar" aria-label="Registry management navigation">
      <p class="eyebrow">Gift Registry</p>
      <a :href="membershipRoutes.giftRegistry">Registry Home</a>
      <a :href="membershipRoutes.giftRegistryCreate">Create Registry</a>
      <a :href="membershipRoutes.giftRegistryFind">Find Registry</a>
      <a :href="membershipRoutes.account">My Account</a>
    </aside>

    <section class="account-content registry-manage-content">
      <p class="eyebrow">Manage Registry</p>
      <h1>Manage Your Registry</h1>
      <p>
        Signed-in registry owners manage visibility, delivery addresses, preferences, sharing and purchased gift status
        from this account destination.
      </p>

      <section class="membership-status-panel">
        <div>
          <p class="eyebrow">{{ registry.event.type }}</p>
          <h2>{{ registry.registrants.primaryName }} & {{ registry.registrants.coRegistrantName }}</h2>
          <p>{{ registry.event.date }} - {{ shareState.publicUrl }}</p>
        </div>
        <a :href="shareState.publicUrl">View Public Registry</a>
      </section>

      <ol class="registry-step-list registry-step-list-inline" aria-label="Registry completion">
        <li v-for="step in steps" :key="step.key" :class="{ complete: step.complete }">
          <span>{{ step.complete ? "Complete" : "Open" }}</span>
          <strong>{{ step.title }}</strong>
        </li>
      </ol>

      <section class="registry-owner-action-grid" aria-label="Registry management tools">
        <article v-for="action in ownerActions" :key="action.title">
          <h3>{{ action.title }}</h3>
          <p>{{ action.description }}</p>
          <a :href="action.href">{{ action.cta }}</a>
        </article>
      </section>
    </section>
  </section>
</template>
