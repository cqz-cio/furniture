<script setup>
import { computed, ref } from "vue";
import {
  REGISTRY_VISIBILITY,
  createGiftRegistryDraft,
  getGiftRegistrySteps,
  getRegistryShareState,
} from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { createYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";

const draft = ref(
  createGiftRegistryDraft({
    event: { type: "Wedding" },
    privacy: { visibility: REGISTRY_VISIBILITY.public },
  }),
);
const saveState = ref("idle");
const saveMessage = ref("");

const steps = computed(() => getGiftRegistrySteps(draft.value));
const shareState = computed(() => getRegistryShareState(draft.value));
const addressRows = computed(() => [
  ["Before Event", draft.value.addresses.beforeEvent],
  ["After Event", draft.value.addresses.afterEvent],
]);
const visibilityOptions = [
  ["Public", REGISTRY_VISIBILITY.public],
  ["Searchable by Email", REGISTRY_VISIBILITY.searchableByEmail],
  ["Invite Only", REGISTRY_VISIBILITY.inviteOnly],
];

const createRegistry = async () => {
  if (!readYudaoToken()) {
    saveState.value = "auth_required";
    saveMessage.value = "Sign in before creating a persistent gift registry.";
    return;
  }
  saveState.value = "saving";
  saveMessage.value = "";
  try {
    draft.value = await createYudaoGiftRegistry(draft.value);
    saveState.value = "saved";
    saveMessage.value = "Registry saved to your Oakved account.";
  } catch (error) {
    saveState.value = "error";
    saveMessage.value = error?.message || "Gift registry could not be saved.";
  }
};
</script>

<template>
  <section class="membership-page service-page-shell registry-workflow-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">Gift Registry</p>
      <h1>Create a registry with event, delivery and privacy planning.</h1>
      <p>
        The create flow captures the event, registrants, delivery addresses, subscription preferences and sharing state
        before the registry becomes visible.
      </p>
      <div class="membership-actions service-link-row">
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
          <label>
            Event Type
            <input v-model="draft.event.type" aria-label="Event type" />
          </label>
          <label>
            Event Date
            <input v-model="draft.event.date" aria-label="Event date" type="date" />
          </label>
          <label>
            Event Location
            <input v-model="draft.event.location" aria-label="Event location" />
          </label>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">2. Registrant Information</p>
          <label>
            Primary Name
            <input v-model="draft.registrants.primaryName" aria-label="Primary registrant name" />
          </label>
          <label>
            Co-Registrant
            <input v-model="draft.registrants.coRegistrantName" aria-label="Co-registrant name" />
          </label>
          <label>
            Email
            <input v-model="draft.registrants.email" aria-label="Registrant email" type="email" />
          </label>
          <label>
            Phone
            <input v-model="draft.registrants.phone" aria-label="Registrant phone" />
          </label>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">3. Gift Delivery Addresses</p>
          <div class="registry-address-grid">
            <article v-for="[label, address] in addressRows" :key="label">
              <h3>{{ label }}</h3>
              <label>
                Address
                <input v-model="address.line1" :aria-label="`${label} address`" />
              </label>
              <label>
                City
                <input v-model="address.city" :aria-label="`${label} city`" />
              </label>
              <label>
                Region
                <input v-model="address.region" :aria-label="`${label} region`" />
              </label>
              <label>
                Postal Code
                <input v-model="address.postalCode" :aria-label="`${label} postal code`" />
              </label>
            </article>
          </div>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">4. Privacy & Subscription</p>
          <div class="registry-choice-row" aria-label="Registry visibility">
            <button
              v-for="[label, value] in visibilityOptions"
              :key="value"
              type="button"
              :class="{ selected: draft.privacy.visibility === value }"
              @click="draft.privacy.visibility = value"
            >
              {{ label }}
            </button>
          </div>
          <label>
            <input v-model="draft.privacy.giftCardPreference" type="checkbox" />
            Accept gift card preference
          </label>
          <label>
            <input v-model="draft.privacy.emailSubscription" type="checkbox" />
            Registry email messages
          </label>
        </section>

        <section class="registry-share-panel">
          <div>
            <p class="eyebrow">5. Share Registry</p>
            <h2>{{ shareState.ready ? "Ready to share" : "Complete required sections" }}</h2>
            <p>
              Public page: <a :href="shareState.publicUrl || membershipRoutes.giftRegistryCreate">{{
                shareState.publicUrl || "Unavailable until saved"
              }}</a>
            </p>
            <p>Purchased item updates are reserved for the later order callback phase.</p>
            <p v-if="saveMessage">{{ saveMessage }}</p>
          </div>
          <button class="membership-primary-link" type="button" :disabled="saveState === 'saving'" @click="createRegistry">
            {{ saveState === "saving" ? "Saving" : "Create Registry" }}
          </button>
        </section>
      </div>
    </section>
  </section>
</template>
