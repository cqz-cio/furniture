<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "../i18n.js";
import {
  canUseGiftRegistryDemoFallback,
  REGISTRY_VISIBILITY,
  createGiftRegistryDraft,
  getGiftRegistrySteps,
  getRegistryShareState,
} from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { addYudaoGiftRegistryItem, getMyYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";

const { t } = useI18n();
const registry = ref(createGiftRegistryDraft());
const registryLoadState = ref("idle");
const registryMessage = ref("");
const itemForm = reactive({
  spuId: "",
  skuId: "",
  productName: "",
  quantityRequested: 1,
  priority: "normal",
  note: "",
});

const steps = computed(() => getGiftRegistrySteps(registry.value));
const shareState = computed(() => getRegistryShareState(registry.value));
const stepTitleMap = {
  event: "giftRegistry.create.steps.event",
  registrants: "giftRegistry.create.steps.registrant",
  addresses: "giftRegistry.create.steps.delivery",
  privacy: "giftRegistry.create.steps.privacy",
  share: "giftRegistry.create.steps.share",
};
const visibilityLabels = {
  [REGISTRY_VISIBILITY.public]: "giftRegistry.create.visibility.public",
  [REGISTRY_VISIBILITY.searchableByEmail]: "giftRegistry.create.visibility.searchableByEmail",
  [REGISTRY_VISIBILITY.inviteOnly]: "giftRegistry.create.visibility.inviteOnly",
};
const ownerActions = computed(() => [
  {
    title: t("giftRegistry.manage.actions.visibility.title"),
    description: t("giftRegistry.manage.actions.visibility.description", {
      value: registry.value.privacy.visibility
        ? t(visibilityLabels[registry.value.privacy.visibility] || "giftRegistry.manage.actions.visibility.notSet")
        : t("giftRegistry.manage.actions.visibility.notSet"),
    }),
    cta: t("giftRegistry.manage.actions.visibility.cta"),
    href: `${membershipRoutes.giftRegistryManage}?section=visibility`,
  },
  {
    title: t("giftRegistry.manage.actions.preferences.title"),
    description: t("giftRegistry.manage.actions.preferences.description"),
    cta: t("giftRegistry.manage.actions.preferences.cta"),
    href: `${membershipRoutes.giftRegistryManage}?section=preferences`,
  },
  {
    title: t("giftRegistry.manage.actions.addresses.title"),
    description: t("giftRegistry.manage.actions.addresses.description"),
    cta: t("giftRegistry.manage.actions.addresses.cta"),
    href: `${membershipRoutes.giftRegistryManage}?section=addresses`,
  },
  {
    title: t("giftRegistry.manage.actions.items.title"),
    description: t("giftRegistry.manage.actions.items.description"),
    cta: t("giftRegistry.manage.actions.items.cta"),
    href: `${membershipRoutes.giftRegistryManage}?section=items`,
  },
]);

const loadRegistry = async () => {
  if (!readYudaoToken()) {
    registryLoadState.value = "auth_required";
    registryMessage.value = t("giftRegistry.manage.messages.signInRequired");
    return;
  }
  registryLoadState.value = "loading";
  registryMessage.value = "";
  try {
    const data = await getMyYudaoGiftRegistry();
    registry.value = data || createGiftRegistryDraft();
    registryLoadState.value = data ? "loaded" : "empty";
    registryMessage.value = data ? "" : t("giftRegistry.manage.messages.empty");
  } catch (error) {
    if (!import.meta.env.PROD && canUseGiftRegistryDemoFallback(import.meta.env)) {
      registry.value = createGiftRegistryDraft();
      registryLoadState.value = "preview";
      registryMessage.value = t("giftRegistry.manage.messages.preview");
      return;
    }
    registryLoadState.value = "error";
    registryMessage.value = error?.message || t("giftRegistry.manage.messages.loadError");
  }
};

const addItem = async () => {
  if (!registry.value.id) {
    registryMessage.value = t("giftRegistry.manage.messages.createBeforeAdd");
    return;
  }
  registryLoadState.value = "saving_item";
  try {
    const item = await addYudaoGiftRegistryItem({
      ...itemForm,
      registryId: registry.value.id,
      spuId: Number(itemForm.spuId),
      skuId: Number(itemForm.skuId),
      quantityRequested: Number(itemForm.quantityRequested || 1),
    });
    registry.value = createGiftRegistryDraft({
      ...registry.value,
      items: [...(registry.value.items || []), item],
    });
    Object.assign(itemForm, {
      spuId: "",
      skuId: "",
      productName: "",
      quantityRequested: 1,
      priority: "normal",
      note: "",
    });
    registryLoadState.value = "loaded";
    registryMessage.value = t("giftRegistry.manage.messages.itemSaved");
  } catch (error) {
    registryLoadState.value = "loaded";
    registryMessage.value = error?.message || t("giftRegistry.manage.messages.itemError");
  }
};

onMounted(loadRegistry);
</script>

<template>
  <section class="account-page account-service-shell">
    <aside class="account-sidebar" aria-label="Registry management navigation">
      <p class="eyebrow">{{ t("giftRegistry.eyebrow") }}</p>
      <a :href="membershipRoutes.giftRegistry">{{ t("giftRegistry.nav.home") }}</a>
      <a :href="membershipRoutes.giftRegistryCreate">{{ t("giftRegistry.nav.create") }}</a>
      <a :href="membershipRoutes.giftRegistryFind">{{ t("giftRegistry.nav.find") }}</a>
      <a :href="membershipRoutes.account">{{ t("giftRegistry.nav.account") }}</a>
    </aside>

    <section class="account-content registry-manage-content">
      <p class="eyebrow">{{ t("giftRegistry.manage.eyebrow") }}</p>
      <h1>{{ t("giftRegistry.manage.title") }}</h1>
      <p>{{ t("giftRegistry.manage.description") }}</p>
      <p v-if="registryMessage">{{ registryMessage }}</p>

      <section v-if="registryLoadState === 'auth_required'" class="membership-status-panel">
        <div>
          <p class="eyebrow">{{ t("giftRegistry.manage.signInEyebrow") }}</p>
          <h2>{{ t("giftRegistry.manage.signInTitle") }}</h2>
        </div>
        <a :href="membershipRoutes.checkoutAuth">{{ t("giftRegistry.manage.signIn") }}</a>
      </section>

      <template v-else>
        <section class="membership-status-panel">
          <div>
            <p class="eyebrow">{{ registry.event.type }}</p>
            <h2>{{ registry.registrants.primaryName || t("giftRegistry.manage.summary.noOwner") }}</h2>
            <p>{{ registry.event.date || t("giftRegistry.manage.summary.noEventDate") }} - {{ shareState.publicUrl || t("giftRegistry.manage.summary.noPublicUrl") }}</p>
          </div>
          <a v-if="shareState.publicUrl" :href="shareState.publicUrl">{{ t("giftRegistry.manage.viewPublic") }}</a>
        </section>

        <ol class="registry-step-list registry-step-list-inline" aria-label="Registry completion">
          <li v-for="step in steps" :key="step.key" :class="{ complete: step.complete }">
            <span>{{ step.complete ? t("giftRegistry.common.complete") : t("giftRegistry.common.open") }}</span>
            <strong>{{ t(stepTitleMap[step.key] || "giftRegistry.create.steps.share") }}</strong>
          </li>
        </ol>

        <section class="registry-form-section">
          <p class="eyebrow">{{ t("giftRegistry.manage.giftsEyebrow") }}</p>
          <h2>{{ t("giftRegistry.manage.addProductTitle") }}</h2>
          <label>
            {{ t("giftRegistry.manage.fields.spuIdLabel") }}
            <input
              v-model="itemForm.spuId"
              :placeholder="t('giftRegistry.manage.fields.spuIdPlaceholder')"
              aria-label="Registry item SPU ID"
              inputmode="numeric"
            />
          </label>
          <label>
            {{ t("giftRegistry.manage.fields.skuIdLabel") }}
            <input
              v-model="itemForm.skuId"
              :placeholder="t('giftRegistry.manage.fields.skuIdPlaceholder')"
              aria-label="Registry item SKU ID"
              inputmode="numeric"
            />
          </label>
          <label>
            {{ t("giftRegistry.manage.fields.productNameLabel") }}
            <input
              v-model="itemForm.productName"
              :placeholder="t('giftRegistry.manage.fields.productNamePlaceholder')"
              aria-label="Registry item product name"
            />
          </label>
          <label>
            {{ t("giftRegistry.manage.fields.quantityLabel") }}
            <input
              v-model="itemForm.quantityRequested"
              aria-label="Registry item requested quantity"
              min="1"
              :placeholder="t('giftRegistry.manage.fields.quantityPlaceholder')"
              type="number"
            />
          </label>
          <button class="membership-primary-link" type="button" @click="addItem">{{ t("giftRegistry.manage.addGift") }}</button>
        </section>

        <section v-if="registry.items.length" class="registry-result-list" aria-label="Registry gifts">
          <article v-for="item in registry.items" :key="item.id || item.skuId">
            <div>
              <p class="eyebrow">
                {{ t("giftRegistry.manage.itemIds", { spuId: item.spuId, skuId: item.skuId }) }}
              </p>
              <h2>{{ item.productName }}</h2>
              <p>
                {{
                  t("giftRegistry.public.requestedPurchased", {
                    requested: item.quantityRequested,
                    purchased: item.quantityPurchased,
                  })
                }}
              </p>
            </div>
            <a :href="`/product?id=${item.spuId}`">{{ t("giftRegistry.manage.viewProduct") }}</a>
          </article>
        </section>

        <section class="registry-owner-action-grid" aria-label="Registry management tools">
          <article v-for="action in ownerActions" :key="action.title">
            <h3>{{ action.title }}</h3>
            <p>{{ action.description }}</p>
            <a :href="action.href">{{ action.cta }}</a>
          </article>
        </section>
      </template>
    </section>
  </section>
</template>
