<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  canUseGiftRegistryDemoFallback,
  createGiftRegistryDraft,
  getGiftRegistrySteps,
  getRegistryShareState,
} from "../services/giftRegistry.js";
import { membershipRoutes } from "../services/membershipNavigation.js";
import { addYudaoGiftRegistryItem, getMyYudaoGiftRegistry } from "../services/yudaoGiftRegistryApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";

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
const ownerActions = computed(() => [
  {
    title: "Registry Visibility",
    description: `Current access is ${registry.value.privacy.visibility || "not set"}.`,
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
    title: "Registry Gifts",
    description: "Gift items are persisted with real SPU and SKU identifiers.",
    cta: "View Gifts",
    href: `${membershipRoutes.giftRegistryManage}?section=items`,
  },
]);

const loadRegistry = async () => {
  if (!readYudaoToken()) {
    registryLoadState.value = "auth_required";
    registryMessage.value = "Sign in to manage a persistent gift registry.";
    return;
  }
  registryLoadState.value = "loading";
  registryMessage.value = "";
  try {
    const data = await getMyYudaoGiftRegistry();
    registry.value = data || createGiftRegistryDraft();
    registryLoadState.value = data ? "loaded" : "empty";
    registryMessage.value = data ? "" : "No registry has been created for this account yet.";
  } catch (error) {
    if (!import.meta.env.PROD && canUseGiftRegistryDemoFallback(import.meta.env)) {
      registry.value = createGiftRegistryDraft();
      registryLoadState.value = "preview";
      registryMessage.value = "Local registry preview is shown because the backend is unavailable.";
      return;
    }
    registryLoadState.value = "error";
    registryMessage.value = error?.message || "Gift registry could not be loaded.";
  }
};

const addItem = async () => {
  if (!registry.value.id) {
    registryMessage.value = "Create a registry before adding gifts.";
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
    registryMessage.value = "Gift item saved to the registry.";
  } catch (error) {
    registryLoadState.value = "loaded";
    registryMessage.value = error?.message || "Gift item could not be saved.";
  }
};

onMounted(loadRegistry);
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
        Signed-in registry owners manage visibility, delivery addresses, preferences, sharing and gift item status from
        this account destination.
      </p>
      <p v-if="registryMessage">{{ registryMessage }}</p>

      <section v-if="registryLoadState === 'auth_required'" class="membership-status-panel">
        <div>
          <p class="eyebrow">Sign In Required</p>
          <h2>Registry management is connected to your Oakved account.</h2>
        </div>
        <a :href="membershipRoutes.checkoutAuth">Sign In</a>
      </section>

      <template v-else>
        <section class="membership-status-panel">
          <div>
            <p class="eyebrow">{{ registry.event.type }}</p>
            <h2>{{ registry.registrants.primaryName || "No registry owner yet" }}</h2>
            <p>{{ registry.event.date || "No event date" }} - {{ shareState.publicUrl || "No public URL yet" }}</p>
          </div>
          <a v-if="shareState.publicUrl" :href="shareState.publicUrl">View Public Registry</a>
        </section>

        <ol class="registry-step-list registry-step-list-inline" aria-label="Registry completion">
          <li v-for="step in steps" :key="step.key" :class="{ complete: step.complete }">
            <span>{{ step.complete ? "Complete" : "Open" }}</span>
            <strong>{{ step.title }}</strong>
          </li>
        </ol>

        <section class="registry-form-section">
          <p class="eyebrow">Registry Gifts</p>
          <h2>Add a Yudao product by SPU and SKU</h2>
          <label>
            SPU ID
            <input v-model="itemForm.spuId" aria-label="Registry item SPU ID" inputmode="numeric" />
          </label>
          <label>
            SKU ID
            <input v-model="itemForm.skuId" aria-label="Registry item SKU ID" inputmode="numeric" />
          </label>
          <label>
            Product Name
            <input v-model="itemForm.productName" aria-label="Registry item product name" />
          </label>
          <label>
            Quantity
            <input v-model="itemForm.quantityRequested" aria-label="Registry item requested quantity" min="1" type="number" />
          </label>
          <button class="membership-primary-link" type="button" @click="addItem">Add Gift</button>
        </section>

        <section v-if="registry.items.length" class="registry-result-list" aria-label="Registry gifts">
          <article v-for="item in registry.items" :key="item.id || item.skuId">
            <div>
              <p class="eyebrow">SPU {{ item.spuId }} / SKU {{ item.skuId }}</p>
              <h2>{{ item.productName }}</h2>
              <p>Requested {{ item.quantityRequested }} - Purchased {{ item.quantityPurchased }}</p>
            </div>
            <a :href="`/product?id=${item.spuId}`">View Product</a>
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
