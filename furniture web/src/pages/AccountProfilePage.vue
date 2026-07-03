<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { accountMenuItems, accountMenuLabelKeys, membershipRoutes } from "../services/membershipNavigation.js";
import {
  getAreaTree,
  getMemberProfile,
  requestEmailVerificationLink,
  updateMemberMobile,
  updateMemberProfile,
} from "../services/yudaoMemberApi.js";
import { sendMemberSmsCode } from "../services/yudaoAuthApi.js";
import { readYudaoToken } from "../services/yudaoRequest.js";
import { useI18n } from "../i18n.js";

const props = defineProps({
  authVersion: {
    type: Number,
    default: 0,
  },
});

const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const errorAction = ref("");
const notice = ref("");
const tokenRequired = ref(false);
const profile = ref(null);
const areaOptions = ref([]);
const areaLoading = ref(false);
const mobileCodeCountdown = ref(0);
const form = reactive({
  nickname: "",
  name: "",
  email: "",
  areaId: undefined,
  sex: undefined,
});
const mobileForm = reactive({
  mobile: "",
  code: "",
});
let profileRequestId = 0;
let mobileCodeTimer = null;

const contactSummary = computed(() =>
  [profile.value?.email, profile.value?.mobile, profile.value?.areaName].filter(Boolean).join(" / ")
);

const accountEmailLabel = computed(() => form.email.trim() || profile.value?.email || t("membership.account.profile.notSet"));

const emailStatusLabel = computed(() => {
  if (!form.email.trim()) return t("membership.account.profile.emailNotSet");
  return profile.value?.emailVerified ? t("membership.account.profile.emailVerified") : t("membership.account.profile.emailVerificationNeeded");
});
const canEditProfile = computed(() => !loading.value && !tokenRequired.value && errorAction.value !== "retryProfile");

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

const clearMobileCodeCountdown = () => {
  if (mobileCodeTimer) window.clearInterval(mobileCodeTimer);
  mobileCodeTimer = null;
};

const startMobileCodeCountdown = (seconds = 60) => {
  clearMobileCodeCountdown();
  mobileCodeCountdown.value = seconds;
  mobileCodeTimer = window.setInterval(() => {
    mobileCodeCountdown.value = Math.max(0, mobileCodeCountdown.value - 1);
    if (mobileCodeCountdown.value === 0) clearMobileCodeCountdown();
  }, 1000);
};

const syncForm = (nextProfile) => {
  form.nickname = nextProfile?.nickname || "";
  form.name = nextProfile?.name || "";
  form.email = nextProfile?.email || "";
  form.areaId = nextProfile?.areaId;
  form.sex = nextProfile?.sex;
  mobileForm.mobile = nextProfile?.mobile || "";
  mobileForm.code = "";
};

const loadProfile = async () => {
  const requestId = ++profileRequestId;
  loading.value = true;
  error.value = "";
  errorAction.value = "";
  notice.value = "";
  tokenRequired.value = false;
  profile.value = null;
  try {
    if (!readYudaoToken()) {
      tokenRequired.value = true;
      return;
    }
    const nextProfile = await getMemberProfile();
    if (requestId !== profileRequestId) return;
    profile.value = nextProfile;
    syncForm(nextProfile);
  } catch {
    if (requestId !== profileRequestId) return;
    error.value = t("membership.account.profile.error");
    errorAction.value = "retryProfile";
  } finally {
    if (requestId === profileRequestId) loading.value = false;
  }
};

const submitProfile = async () => {
  if (!form.nickname.trim() || saving.value) return;
  saving.value = true;
  error.value = "";
  errorAction.value = "";
  notice.value = "";
  try {
    await updateMemberProfile({
      nickname: form.nickname.trim(),
      name: form.name.trim(),
      email: form.email.trim() || undefined,
      areaId: form.areaId || undefined,
      sex: form.sex || undefined,
    });
    notice.value = t("membership.account.profile.profileUpdated");
    await loadProfile();
  } catch {
    error.value = t("membership.account.profile.saveError");
    errorAction.value = "profileForm";
  } finally {
    saving.value = false;
  }
};

const requestEmailVerification = async () => {
  if (!form.email.trim() || saving.value) return;
  saving.value = true;
  error.value = "";
  errorAction.value = "";
  notice.value = "";
  try {
    await requestEmailVerificationLink(form.email.trim());
    notice.value = t("membership.account.profile.verificationSent");
  } catch {
    error.value = t("membership.account.profile.verificationError");
    errorAction.value = "profileForm";
  } finally {
    saving.value = false;
  }
};

const requestMobileCode = async () => {
  if (!mobileForm.mobile.trim() || saving.value || mobileCodeCountdown.value > 0) return;
  saving.value = true;
  error.value = "";
  errorAction.value = "";
  notice.value = "";
  try {
    await sendMemberSmsCode(mobileForm.mobile.trim());
    notice.value = t("membership.account.profile.codeSent");
    startMobileCodeCountdown();
  } catch {
    error.value = t("membership.account.profile.codeError");
    errorAction.value = "mobileForm";
  } finally {
    saving.value = false;
  }
};

const submitMobile = async () => {
  if (!mobileForm.mobile.trim() || !mobileForm.code.trim() || saving.value) return;
  saving.value = true;
  error.value = "";
  errorAction.value = "";
  notice.value = "";
  try {
    await updateMemberMobile({
      mobile: mobileForm.mobile.trim(),
      code: mobileForm.code.trim(),
    });
    notice.value = t("membership.account.profile.phoneUpdated");
    await loadProfile();
  } catch {
    error.value = t("membership.account.profile.phoneError");
    errorAction.value = "mobileForm";
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadAreaOptions();
  loadProfile();
});
onBeforeUnmount(clearMobileCodeCountdown);
watch(() => props.authVersion, loadProfile);
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
      <p class="eyebrow">{{ t("membership.account.profile.eyebrow") }}</p>
      <h1>{{ t("membership.account.profile.title") }}</h1>
      <p v-if="contactSummary">{{ contactSummary }}</p>
      <p v-if="loading" class="product-loading">{{ t("membership.account.profile.loading") }}</p>
      <div v-if="tokenRequired" class="checkout-error">
        <p>{{ t("membership.account.profile.signInRequired") }}</p>
        <div class="orders-recovery-actions">
          <a class="orders-recovery-action" :href="membershipRoutes.checkoutAuth">
            {{ t("membership.account.profile.actions.connectAccount") }}
          </a>
        </div>
      </div>
      <div v-else-if="error" class="checkout-error">
        <p>{{ error }}</p>
        <div class="orders-recovery-actions">
          <button v-if="errorAction === 'retryProfile'" class="orders-recovery-action" type="button" @click="loadProfile">
            {{ t("membership.account.profile.actions.retry") }}
          </button>
          <a v-else-if="errorAction === 'profileForm'" class="orders-recovery-action" href="#account-profile-form">
            {{ t("membership.account.profile.actions.reviewProfile") }}
          </a>
          <a v-else-if="errorAction === 'mobileForm'" class="orders-recovery-action" href="#account-mobile-form">
            {{ t("membership.account.profile.actions.reviewPhone") }}
          </a>
        </div>
      </div>
      <p v-if="notice" class="auth-success">{{ notice }}</p>

      <section v-if="canEditProfile" class="profile-member-identity-panel" aria-labelledby="profile-member-identity-heading">
        <header>
          <p class="eyebrow">{{ t("membership.account.profile.identityEyebrow") }}</p>
          <h2 id="profile-member-identity-heading">{{ t("membership.account.profile.identityTitle") }}</h2>
        </header>
        <dl>
          <div>
            <dt>{{ t("membership.account.profile.accountEmail") }}</dt>
            <dd>{{ accountEmailLabel }}</dd>
          </div>
          <div>
            <dt>{{ t("membership.account.profile.verification") }}</dt>
            <dd>
              <span class="profile-member-identity-status" :class="{ 'is-ready': profile?.emailVerified }">
                {{ emailStatusLabel }}
              </span>
            </dd>
          </div>
          <div v-if="profile?.tradeId">
            <dt>{{ t("auth.fields.tradeId") }}</dt>
            <dd>{{ profile.tradeId }}</dd>
          </div>
        </dl>
        <p>{{ t("membership.account.profile.identityIntro") }}</p>
        <a :href="membershipRoutes.accountMembership">{{ t("membership.account.profile.reviewMembership") }}</a>
      </section>

      <form
        v-if="canEditProfile"
        id="account-profile-form"
        class="profile-form account-form-panel"
        @submit.prevent="submitProfile"
      >
        <header class="account-form-toolbar">
          <div>
            <p class="eyebrow">{{ t("membership.account.profile.profileDetailsEyebrow") }}</p>
            <h2>{{ t("membership.account.profile.profileDetailsTitle") }}</h2>
          </div>
          <button class="auth-primary-button" type="submit" :disabled="saving || !form.nickname.trim()">
            {{ saving ? t("membership.account.profile.saving") : t("membership.account.profile.saveProfile") }}
          </button>
        </header>
        <label>
          {{ t("membership.account.profile.fields.accountName") }}
          <input v-model.trim="form.nickname" autocomplete="name" required type="text" />
        </label>
        <label>
          {{ t("membership.account.profile.fields.deliveryName") }}
          <input v-model.trim="form.name" autocomplete="name" type="text" />
        </label>
        <label>
          {{ t("membership.account.profile.fields.email") }}
          <input v-model.trim="form.email" autocomplete="email" inputmode="email" type="email" />
          <span class="email-verification-status">{{ emailStatusLabel }}</span>
        </label>
        <label>
          {{ t("membership.account.profile.fields.region") }}
          <select v-model.number="form.areaId" class="area-select" :disabled="areaLoading">
            <option :value="undefined">
              {{ areaLoading ? t("membership.account.profile.loadingRegions") : t("membership.account.profile.chooseRegion") }}
            </option>
            <option v-for="area in areaOptions" :key="area.id" :value="area.id">{{ area.label }}</option>
          </select>
        </label>
        <label>
          {{ t("membership.account.profile.fields.gender") }}
          <select v-model.number="form.sex">
            <option :value="undefined">{{ t("membership.account.profile.gender.unspecified") }}</option>
            <option :value="1">{{ t("membership.account.profile.gender.male") }}</option>
            <option :value="2">{{ t("membership.account.profile.gender.female") }}</option>
          </select>
        </label>
        <button class="auth-secondary-button" type="button" :disabled="saving || !form.email.trim()" @click="requestEmailVerification">
          {{ t("membership.account.profile.sendVerificationEmail") }}
        </button>
      </form>

      <form
        v-if="canEditProfile"
        id="account-mobile-form"
        class="mobile-form account-form-panel"
        @submit.prevent="submitMobile"
      >
        <header class="account-form-toolbar">
          <div>
            <p class="eyebrow">{{ t("membership.account.profile.phoneAccessEyebrow") }}</p>
            <h2>{{ t("membership.account.profile.phoneAccessTitle") }}</h2>
          </div>
        </header>
        <label>
          {{ t("membership.account.profile.fields.phone") }}
          <input v-model.trim="mobileForm.mobile" autocomplete="tel" inputmode="tel" type="tel" />
        </label>
        <label>
          {{ t("membership.account.profile.fields.verificationCode") }}
          <input v-model.trim="mobileForm.code" autocomplete="one-time-code" inputmode="numeric" maxlength="6" type="text" />
        </label>
        <div class="account-form-actions">
          <button
            class="auth-secondary-button"
            type="button"
            :disabled="saving || !mobileForm.mobile.trim() || mobileCodeCountdown > 0"
            @click="requestMobileCode"
          >
            {{ mobileCodeCountdown > 0 ? `${mobileCodeCountdown}s` : t("membership.account.profile.sendCode") }}
          </button>
          <button class="auth-primary-button" type="submit" :disabled="saving || !mobileForm.mobile.trim() || !mobileForm.code.trim()">
            {{ saving ? t("membership.account.profile.saving") : t("membership.account.profile.updatePhone") }}
          </button>
        </div>
      </form>
    </section>
  </section>
</template>
