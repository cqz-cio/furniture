<script setup>
import { computed, ref } from "vue";
import { useI18n } from "../i18n.js";
import {
  REGISTRY_VISIBILITY,
  createGiftRegistryDraft,
  getGiftRegistrySteps,
  getRegistryShareState,
} from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { createYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";

const { t } = useI18n();
const draft = ref(
  createGiftRegistryDraft({
    privacy: { visibility: REGISTRY_VISIBILITY.public },
  }),
);
const saveState = ref("idle");
const saveMessage = ref("");

const steps = computed(() => getGiftRegistrySteps(draft.value));
const shareState = computed(() => getRegistryShareState(draft.value));
const addressRows = computed(() => [
  ["giftRegistry.create.addresses.beforeEvent", draft.value.addresses.beforeEvent],
  ["giftRegistry.create.addresses.afterEvent", draft.value.addresses.afterEvent],
]);
const visibilityOptions = computed(() => [
  ["giftRegistry.create.visibility.public", REGISTRY_VISIBILITY.public],
  ["giftRegistry.create.visibility.searchableByEmail", REGISTRY_VISIBILITY.searchableByEmail],
  ["giftRegistry.create.visibility.inviteOnly", REGISTRY_VISIBILITY.inviteOnly],
]);
const stepTitleMap = {
  event: "giftRegistry.create.steps.event",
  registrants: "giftRegistry.create.steps.registrant",
  addresses: "giftRegistry.create.steps.delivery",
  privacy: "giftRegistry.create.steps.privacy",
  share: "giftRegistry.create.steps.share",
};

const createRegistry = async () => {
  if (!readYudaoToken()) {
    saveState.value = "auth_required";
    saveMessage.value = t("giftRegistry.create.messages.signInRequired");
    return;
  }
  saveState.value = "saving";
  saveMessage.value = "";
  try {
    draft.value = await createYudaoGiftRegistry(draft.value);
    saveState.value = "saved";
    saveMessage.value = t("giftRegistry.create.messages.saved");
  } catch (error) {
    saveState.value = "error";
    saveMessage.value = error?.message || t("giftRegistry.create.messages.error");
  }
};
</script>

<template>
  <section class="membership-page service-page-shell registry-workflow-page">
    <header class="membership-hero registry-hero">
      <p class="eyebrow">{{ t("giftRegistry.eyebrow") }}</p>
      <h1>{{ t("giftRegistry.create.title") }}</h1>
      <p>{{ t("giftRegistry.create.description") }}</p>
      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="membershipRoutes.giftRegistryFind">{{ t("giftRegistry.create.find") }}</a>
        <a :href="membershipRoutes.giftRegistryManage">{{ t("giftRegistry.create.manage") }}</a>
      </div>
    </header>

    <section class="registry-workflow-grid">
      <aside class="registry-step-panel" aria-label="Create registry steps">
        <p class="eyebrow">{{ t("giftRegistry.create.flow") }}</p>
        <ol class="registry-step-list">
          <li v-for="step in steps" :key="step.key" :class="{ complete: step.complete }">
            <span>{{ step.complete ? t("giftRegistry.common.complete") : t("giftRegistry.common.open") }}</span>
            <strong>{{ t(stepTitleMap[step.key] || "giftRegistry.create.steps.share") }}</strong>
          </li>
        </ol>
      </aside>

      <div class="registry-form-stack">
        <section class="registry-form-section">
          <p class="eyebrow">{{ t("giftRegistry.create.sections.event") }}</p>
          <label>
            {{ t("giftRegistry.create.fields.eventTypeLabel") }}
            <input
              v-model="draft.event.type"
              :placeholder="t('giftRegistry.create.fields.eventTypePlaceholder')"
              aria-label="Event type"
            />
          </label>
          <label>
            {{ t("giftRegistry.create.fields.eventDateLabel") }}
            <input v-model="draft.event.date" aria-label="Event date" type="date" />
          </label>
          <label>
            {{ t("giftRegistry.create.fields.eventLocationLabel") }}
            <input
              v-model="draft.event.location"
              :placeholder="t('giftRegistry.create.fields.eventLocationPlaceholder')"
              aria-label="Event location"
            />
          </label>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">{{ t("giftRegistry.create.sections.registrant") }}</p>
          <label>
            {{ t("giftRegistry.create.fields.primaryNameLabel") }}
            <input
              v-model="draft.registrants.primaryName"
              :placeholder="t('giftRegistry.create.fields.primaryNamePlaceholder')"
              aria-label="Primary registrant name"
            />
          </label>
          <label>
            {{ t("giftRegistry.create.fields.coRegistrantLabel") }}
            <input v-model="draft.registrants.coRegistrantName" aria-label="Co-registrant name" />
          </label>
          <label>
            {{ t("giftRegistry.create.fields.emailLabel") }}
            <input
              v-model="draft.registrants.email"
              :placeholder="t('giftRegistry.create.fields.emailPlaceholder')"
              aria-label="Registrant email"
              type="email"
            />
          </label>
          <label>
            {{ t("giftRegistry.create.fields.phoneLabel") }}
            <input
              v-model="draft.registrants.phone"
              :placeholder="t('giftRegistry.create.fields.phonePlaceholder')"
              aria-label="Registrant phone"
            />
          </label>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">{{ t("giftRegistry.create.sections.delivery") }}</p>
          <div class="registry-address-grid">
            <article v-for="[labelKey, address] in addressRows" :key="labelKey">
              <h3>{{ t(labelKey) }}</h3>
              <label>
                {{ t("giftRegistry.create.fields.addressLabel") }}
                <input v-model="address.line1" :aria-label="`${labelKey} address`" />
              </label>
              <label>
                {{ t("giftRegistry.create.fields.cityLabel") }}
                <input v-model="address.city" :aria-label="`${labelKey} city`" />
              </label>
              <label>
                {{ t("giftRegistry.create.fields.regionLabel") }}
                <input v-model="address.region" :aria-label="`${labelKey} region`" />
              </label>
              <label>
                {{ t("giftRegistry.create.fields.postalCodeLabel") }}
                <input v-model="address.postalCode" :aria-label="`${labelKey} postal code`" />
              </label>
            </article>
          </div>
        </section>

        <section class="registry-form-section">
          <p class="eyebrow">{{ t("giftRegistry.create.sections.privacy") }}</p>
          <div class="registry-choice-row" aria-label="Registry visibility">
            <button
              v-for="[labelKey, value] in visibilityOptions"
              :key="value"
              type="button"
              :class="{ selected: draft.privacy.visibility === value }"
              @click="draft.privacy.visibility = value"
            >
              {{ t(labelKey) }}
            </button>
          </div>
          <label>
            <input v-model="draft.privacy.giftCardPreference" type="checkbox" />
            {{ t("giftRegistry.create.fields.giftCardPreference") }}
          </label>
          <label>
            <input v-model="draft.privacy.emailSubscription" type="checkbox" />
            {{ t("giftRegistry.create.fields.emailSubscription") }}
          </label>
        </section>

        <section class="registry-share-panel">
          <div>
            <p class="eyebrow">{{ t("giftRegistry.create.sections.share") }}</p>
            <h2>
              {{
                shareState.ready
                  ? t("giftRegistry.create.share.ready")
                  : t("giftRegistry.create.share.completeRequired")
              }}
            </h2>
            <p>
              {{ t("giftRegistry.create.share.publicPage") }}:
              <a :href="shareState.publicUrl || membershipRoutes.giftRegistryCreate">{{
                shareState.publicUrl || t("giftRegistry.create.share.unavailable")
              }}</a>
            </p>
            <p>{{ t("giftRegistry.create.purchaseCallbackNote") }}</p>
            <p v-if="saveMessage">{{ saveMessage }}</p>
          </div>
          <button class="membership-primary-link" type="button" :disabled="saveState === 'saving'" @click="createRegistry">
            {{ saveState === "saving" ? t("giftRegistry.create.actions.saving") : t("giftRegistry.create.actions.create") }}
          </button>
        </section>
      </div>
    </section>
  </section>
</template>
