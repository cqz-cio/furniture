<script setup>
import {
  REGISTRY_VISIBILITY,
  createGiftRegistryDraft,
  getGiftRegistrySteps,
  getRegistryShareState,
} from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";

const draft = createGiftRegistryDraft({
  id: "registry-stone-2026",
  event: {
    type: "Wedding",
    date: "2026-10-01",
  },
  registrants: {
    primaryName: "Avery Stone",
    coRegistrantName: "Morgan Vale",
    email: "avery@example.com",
    phone: "(415) 555-0198",
  },
  addresses: {
    beforeEvent: {
      line1: "123 Oak Road",
      city: "Boston",
      region: "MA",
      postalCode: "02116",
    },
    afterEvent: {
      kind: "custom",
      line1: "48 Gallery Lane",
      city: "New York",
      region: "NY",
      postalCode: "10013",
    },
  },
  privacy: {
    visibility: REGISTRY_VISIBILITY.public,
    emailSubscription: true,
    giftCardPreference: true,
  },
});

const steps = getGiftRegistrySteps(draft);
const shareState = getRegistryShareState(draft);
const addressRows = [
  ["Before Event", draft.addresses.beforeEvent],
  ["After Event", draft.addresses.afterEvent],
];
const visibilityOptions = [
  ["Public", REGISTRY_VISIBILITY.public],
  ["Searchable by Email", REGISTRY_VISIBILITY.searchableByEmail],
  ["Invite Only", REGISTRY_VISIBILITY.inviteOnly],
];
</script>

<template>
  <section class="membership-page registry-workflow-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">Gift Registry</p>
      <h1>Create a registry with event, delivery and privacy planning.</h1>
      <p>
        The create flow captures the event, registrants, delivery addresses, subscription preferences and sharing state
        before the registry becomes visible.
      </p>
      <div class="membership-actions">
        <a class="membership-primary-link" :href="membershipRoutes.giftRegistryFind">Find a Registry</a>
        <a :href="membershipRoutes.giftRegistryManage">Manage Registry</a>
      </div>
    </header>

    <section class="registry-workflow-grid">
      <aside class="registry-step-panel" aria-label="Create registry steps">
        <p class="eyebrow">Create Flow</p>
        <ol class="registry-step-list">
          <li v-for="step in steps" :key="step.key" :class="{ complete: step.complete }">
            <span>{{ step.complete ? "Complete" : "Open" }}</span>
            <strong>{{ step.title }}</strong>
          </li>
        </ol>
      </aside>

      <div class="registry-form-stack">
        <section class="registry-form-section">
          <p class="eyebrow">1. Event Details</p>
          <h2>{{ draft.event.type }}</h2>
          <dl>
            <div>
              <dt>Event Date</dt>
              <dd>{{ draft.event.date }}</dd>
            </div>
            <div>
              <dt>Registry Rule</dt>
              <dd>Event type and event date are required before sharing is enabled.</dd>
            </div>
          </dl>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">2. Registrant Information</p>
          <h2>{{ draft.registrants.primaryName }} & {{ draft.registrants.coRegistrantName }}</h2>
          <dl>
            <div>
              <dt>Email</dt>
              <dd>{{ draft.registrants.email }}</dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>{{ draft.registrants.phone }}</dd>
            </div>
          </dl>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">3. Gift Delivery Addresses</p>
          <h2>Before and after event delivery</h2>
          <div class="registry-address-grid">
            <article v-for="[label, address] in addressRows" :key="label">
              <h3>{{ label }}</h3>
              <p>{{ address.line1 }}</p>
              <p>{{ address.city }}, {{ address.region }} {{ address.postalCode }}</p>
              <small>{{ address.kind === "custom" ? "Custom delivery address" : "Local delivery address" }}</small>
            </article>
          </div>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">4. Privacy & Subscription</p>
          <h2>Visibility and registry communications</h2>
          <div class="registry-choice-row" aria-label="Registry visibility">
            <span
              v-for="[label, value] in visibilityOptions"
              :key="value"
              :class="{ selected: draft.privacy.visibility === value }"
            >
              {{ label }}
            </span>
          </div>
          <dl>
            <div>
              <dt>Gift Card Preference</dt>
              <dd>{{ draft.privacy.giftCardPreference ? "Accepted" : "Hidden" }}</dd>
            </div>
            <div>
              <dt>Email Subscription</dt>
              <dd>{{ draft.privacy.emailSubscription ? "Enabled" : "Disabled" }}</dd>
            </div>
          </dl>
        </section>

        <section class="registry-share-panel">
          <div>
            <p class="eyebrow">5. Share Registry</p>
            <h2>{{ shareState.ready ? "Ready to share" : "Complete required sections" }}</h2>
            <p>
              Public page: <a :href="shareState.publicUrl || membershipRoutes.giftRegistryCreate">{{
                shareState.publicUrl || "Unavailable"
              }}</a>
            </p>
            <p>Purchased items are {{ shareState.purchasedAutoMarking ? "automatically marked" : "not yet tracked" }}.</p>
          </div>
          <a class="membership-primary-link" :href="membershipRoutes.giftRegistryManage">Review Registry</a>
        </section>
      </div>
    </section>
  </section>
</template>
