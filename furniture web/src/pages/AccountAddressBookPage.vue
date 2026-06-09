<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { accountMenuItems, accountMenuLabelKeys } from "../services/membershipNavigation.js";
import {
  createMemberAddress,
  deleteMemberAddress,
  getAddressList,
  getAreaTree,
  readYudaoToken,
  updateMemberAddress,
} from "../services/yudaoClient.js";
import { useI18n } from "../i18n.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});

const { t } = useI18n();
const emptyAddressForm = () => ({
  id: undefined,
  name: "",
  mobile: "",
  areaId: undefined,
  detailAddress: "",
  defaultStatus: false,
});

const loading = ref(true);
const saving = ref(false);
const error = ref("");
const notice = ref("");
const tokenRequired = ref(false);
const addresses = ref([]);
const areaOptions = ref([]);
const areaLoading = ref(false);
const form = reactive(emptyAddressForm());
let addressRequestId = 0;

const isEditing = computed(() => Boolean(form.id));

const flattenAreaOptions = (nodes = [], prefix = "") =>
  nodes.flatMap((node) => {
    const label = [prefix, node.name || node.label].filter(Boolean).join(" / ");
    const option = { id: node.id, label };
    return [option, ...flattenAreaOptions(node.children || [], label)];
  });

const loadAreaOptions = async () => {
  areaLoading.value = true;
  try {
    areaOptions.value = flattenAreaOptions(await getAreaTree());
  } catch {
    areaOptions.value = [];
  } finally {
    areaLoading.value = false;
  }
};

const resetForm = () => {
  Object.assign(form, emptyAddressForm());
};

const editAddress = (address) => {
  Object.assign(form, {
    id: address.id,
    name: address.name,
    mobile: address.mobile,
    areaId: address.raw?.areaId,
    detailAddress: address.detailAddress,
    defaultStatus: Boolean(address.raw?.defaultStatus),
  });
};

const loadAddresses = async () => {
  const requestId = ++addressRequestId;
  loading.value = true;
  error.value = "";
  notice.value = "";
  tokenRequired.value = false;
  addresses.value = [];
  try {
    if (!readYudaoToken()) {
      tokenRequired.value = true;
      return;
    }
    const nextAddresses = await getAddressList();
    if (requestId !== addressRequestId) return;
    addresses.value = nextAddresses;
  } catch {
    if (requestId !== addressRequestId) return;
    error.value = t("membership.account.addressBook.error");
  } finally {
    if (requestId === addressRequestId) loading.value = false;
  }
};

const submitAddress = async () => {
  if (!form.name.trim() || !form.mobile.trim() || !form.areaId || !form.detailAddress.trim() || saving.value) return;
  saving.value = true;
  error.value = "";
  notice.value = "";
  const payload = {
    name: form.name.trim(),
    mobile: form.mobile.trim(),
    areaId: Number(form.areaId),
    detailAddress: form.detailAddress.trim(),
    defaultStatus: Boolean(form.defaultStatus),
  };
  try {
    if (form.id) {
      await updateMemberAddress({ id: form.id, ...payload });
      notice.value = t("membership.account.addressBook.updated");
    } else {
      await createMemberAddress(payload);
      notice.value = t("membership.account.addressBook.added");
    }
    resetForm();
    await loadAddresses();
  } catch {
    error.value = t("membership.account.addressBook.saveError");
  } finally {
    saving.value = false;
  }
};

const removeAddress = async (address) => {
  if (!address?.id || saving.value) return;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    await deleteMemberAddress(address.id);
    notice.value = t("membership.account.addressBook.deleted");
    if (form.id === address.id) resetForm();
    await loadAddresses();
  } catch {
    error.value = t("membership.account.addressBook.deleteError");
  } finally {
    saving.value = false;
  }
};

const setDefaultAddress = async (address) => {
  if (!address?.id || saving.value) return;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    await updateMemberAddress({
      id: address.id,
      name: address.name,
      mobile: address.mobile,
      areaId: address.raw?.areaId,
      detailAddress: address.detailAddress,
      defaultStatus: true,
    });
    notice.value = t("membership.account.addressBook.defaultUpdated");
    await loadAddresses();
  } catch {
    error.value = t("membership.account.addressBook.defaultError");
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadAreaOptions();
  loadAddresses();
});
watch(() => props.authVersion, loadAddresses);
</script>

<template>
  <section class="account-page">
    <aside class="account-sidebar" :aria-label="t('membership.account.menuAria')">
      <p class="eyebrow">{{ t("membership.account.myAccount") }}</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">
        {{ t(accountMenuLabelKeys[item.label] || "membership.account.menuProfile") }}
      </a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">{{ t("membership.account.addressBook.eyebrow") }}</p>
      <h1>{{ t("membership.account.addressBook.title") }}</h1>
      <p v-if="loading" class="product-loading">{{ t("membership.account.addressBook.loading") }}</p>
      <p v-if="tokenRequired" class="checkout-error">{{ t("membership.account.addressBook.signInRequired") }}</p>
      <p v-else-if="error" class="checkout-error">{{ error }}</p>
      <p v-if="notice" class="auth-success">{{ notice }}</p>

      <form v-if="!loading && !tokenRequired" class="address-book-form account-form-panel" @submit.prevent="submitAddress">
        <header class="account-form-toolbar">
          <div>
            <p class="eyebrow">{{ t("membership.account.addressBook.eyebrow") }}</p>
            <h2>{{ isEditing ? t("membership.account.addressBook.updateAddress") : t("membership.account.addressBook.addAddress") }}</h2>
          </div>
          <button class="auth-primary-button" type="submit" :disabled="saving">
            {{ saving ? t("membership.account.addressBook.saving") : isEditing ? t("membership.account.addressBook.updateAddress") : t("membership.account.addressBook.addAddress") }}
          </button>
        </header>
        <label>
          {{ t("membership.account.addressBook.fields.recipient") }}
          <input v-model.trim="form.name" autocomplete="name" required type="text" />
        </label>
        <label>
          {{ t("membership.account.addressBook.fields.phone") }}
          <input v-model.trim="form.mobile" autocomplete="tel" inputmode="tel" required type="tel" />
        </label>
        <label>
          {{ t("membership.account.addressBook.fields.region") }}
          <select v-model.number="form.areaId" class="address-area-select" :disabled="areaLoading" required>
            <option :value="undefined">
              {{ areaLoading ? t("membership.account.addressBook.loadingRegions") : t("membership.account.addressBook.chooseRegion") }}
            </option>
            <option v-for="area in areaOptions" :key="area.id" :value="area.id">{{ area.label }}</option>
          </select>
        </label>
        <label>
          {{ t("membership.account.addressBook.fields.address") }}
          <textarea v-model.trim="form.detailAddress" required rows="3"></textarea>
        </label>
        <label class="auth-check-row">
          <input v-model="form.defaultStatus" type="checkbox" />
          <span>{{ t("membership.account.addressBook.defaultAddress") }}</span>
        </label>
        <button v-if="isEditing" class="auth-secondary-button" type="button" @click="resetForm">
          {{ t("membership.account.addressBook.cancel") }}
        </button>
      </form>

      <section v-if="addresses.length" class="address-book-list" :aria-label="t('membership.account.addressBook.listAria')">
        <article v-for="address in addresses" :key="address.id" class="address-book-card">
          <div>
            <strong>{{ address.name }}</strong>
            <p>{{ address.mobile }}</p>
            <p>{{ address.areaName }} {{ address.detailAddress }}</p>
            <small v-if="address.raw?.defaultStatus">{{ t("membership.account.addressBook.defaultBadge") }}</small>
          </div>
          <div class="account-form-actions">
            <button type="button" @click="editAddress(address)">{{ t("membership.account.addressBook.edit") }}</button>
            <button type="button" :disabled="address.raw?.defaultStatus" @click="setDefaultAddress(address)">
              {{ t("membership.account.addressBook.setDefault") }}
            </button>
            <button type="button" @click="removeAddress(address)">{{ t("membership.account.addressBook.delete") }}</button>
          </div>
        </article>
      </section>
      <p v-else-if="!loading && !tokenRequired && !error" class="orders-empty">
        {{ t("membership.account.addressBook.empty") }}
      </p>
    </section>
  </section>
</template>
