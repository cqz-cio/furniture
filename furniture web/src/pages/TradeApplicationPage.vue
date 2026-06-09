<script setup>
import { computed, reactive, ref } from "vue";
import TradeProgramNav from "../components/TradeProgramNav.vue";
import { useI18n } from "../i18n.js";
import {
  businessDescriptionOptions,
  businessInfoFields,
  countryOptions,
  socialFields,
  stateOptions,
  tradeRoutes,
} from "../services/tradeProgram.js";
import { submitTradeApplication } from "../services/yudaoClient.js";

const { t } = useI18n();

const form = reactive({
  businessName: "",
  country: "",
  street: "",
  address2: "",
  city: "",
  state: "",
  postalCode: "",
  businessDescription: "",
  website: "",
  portfolio: "",
  instagram: "",
  pinterest: "",
  houzz: "",
  linkedin: "",
  emailOptIn: true,
  privacyAccepted: false,
});

const authorizedUsers = ref([
  {
    firstName: "",
    lastName: "",
    title: "",
    phone: "",
    email: "",
    confirmEmail: "",
  },
]);
const businessDocuments = ref([]);
const taxDocuments = ref([]);
const busy = ref(false);
const successNotice = ref("");
const error = ref("");

const optionMap = {
  country: countryOptions,
  state: stateOptions,
  businessDescription: businessDescriptionOptions,
};

const isEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

const canSubmit = computed(() => {
  const requiredBusinessFields = businessInfoFields
    .filter(([, , required]) => required)
    .every(([name]) => String(form[name] || "").trim());
  const hasAuthorizedUser = authorizedUsers.value.every(
    (user) =>
      user.firstName.trim() &&
      user.lastName.trim() &&
      user.title.trim() &&
      user.phone.trim() &&
      isEmail(user.email) &&
      user.email === user.confirmEmail,
  );
  return requiredBusinessFields && hasAuthorizedUser && businessDocuments.value.length > 0 && form.privacyAccepted && !busy.value;
});

const addAuthorizedUser = () => {
  authorizedUsers.value.push({
    firstName: "",
    lastName: "",
    title: "",
    phone: "",
    email: "",
    confirmEmail: "",
  });
};

const removeAuthorizedUser = (index) => {
  if (authorizedUsers.value.length === 1) return;
  authorizedUsers.value.splice(index, 1);
};

const setDocuments = (target, files) => {
  const nextFiles = Array.from(files || []).map((file) => ({
    name: file.name,
    url: `local:${file.name}`,
  }));
  target.value = nextFiles;
};

const buildPayload = () => ({
  ...form,
  primaryEmail: authorizedUsers.value[0]?.email || "",
  authorizedUsers: authorizedUsers.value.map((user) => ({ ...user })),
  businessDocuments: businessDocuments.value,
  taxDocuments: taxDocuments.value,
});

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  successNotice.value = "";
  error.value = "";
  try {
    await submitTradeApplication(buildPayload());
    successNotice.value = t("tradeProgram.application.successNotice");
  } catch {
    error.value = t("tradeProgram.application.submitError");
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <section class="trade-page trade-application-page">
    <TradeProgramNav />
    <header class="trade-page-header">
      <h1>{{ t("tradeProgram.application.title") }}</h1>
      <p>{{ t("tradeProgram.application.intro") }}</p>
      <p>
        {{ t("tradeProgram.application.faqPrefix") }}
        <a :href="tradeRoutes.faq">{{ t("tradeProgram.application.faqLink") }}</a>
      </p>
    </header>

    <form class="trade-application-form" @submit.prevent="submit">
      <section class="trade-form-section">
        <h2>{{ t("tradeProgram.application.businessInfo") }}</h2>
        <div class="trade-form-grid">
          <label v-for="[name, type, required] in businessInfoFields" :key="name" class="trade-field">
            <span class="sr-only">{{ t(`tradeProgram.application.fields.${name}`) }}</span>
            <select v-if="type === 'select'" v-model="form[name]" :required="required">
              <option value="">{{ t(`tradeProgram.application.fields.${name}`) }}</option>
              <option v-for="option in optionMap[name]" :key="option" :value="option">
                {{ t(`tradeProgram.application.options.${option}`) }}
              </option>
            </select>
            <input
              v-else
              v-model.trim="form[name]"
              :placeholder="t(`tradeProgram.application.fields.${name}`)"
              :required="required"
              :type="type"
            />
          </label>
        </div>
      </section>

      <section class="trade-form-section">
        <h2>{{ t("tradeProgram.application.socialMedia") }}</h2>
        <div class="trade-form-grid">
          <label v-for="name in socialFields" :key="name" class="trade-field">
            <span class="sr-only">{{ t(`tradeProgram.application.fields.${name}`) }}</span>
            <input v-model.trim="form[name]" :placeholder="t(`tradeProgram.application.fields.${name}`)" type="text" />
          </label>
        </div>
      </section>

      <section class="trade-form-section">
        <h2>{{ t("tradeProgram.application.authorizedUsers") }}</h2>
        <p>{{ t("tradeProgram.application.authorizedUsersHelp") }}</p>
        <div v-for="(user, index) in authorizedUsers" :key="index" class="trade-user-block">
          <div class="trade-form-grid">
            <label v-for="field in ['firstName', 'lastName', 'title', 'phone', 'email', 'confirmEmail']" :key="field" class="trade-field">
              <span class="sr-only">{{ t(`tradeProgram.application.fields.${field}`) }}</span>
              <input
                v-model.trim="user[field]"
                :placeholder="t(`tradeProgram.application.fields.${field}`)"
                :type="field.includes('email') || field.includes('Email') ? 'email' : 'text'"
                required
              />
            </label>
          </div>
          <button class="trade-text-button" type="button" :disabled="authorizedUsers.length === 1" @click="removeAuthorizedUser(index)">
            {{ t("tradeProgram.application.removeUser") }}
          </button>
        </div>
        <button class="trade-add-button" type="button" @click="addAuthorizedUser">
          {{ t("tradeProgram.application.addUser") }}
        </button>
      </section>

      <section class="trade-form-section">
        <h2>{{ t("tradeProgram.application.businessDocuments") }}</h2>
        <p>{{ t("tradeProgram.application.businessDocumentsHelp") }}</p>
        <label class="trade-file-field">
          <span>{{ businessDocuments.length ? businessDocuments.map((file) => file.name).join(", ") : t("tradeProgram.application.addAttachment") }}</span>
          <input type="file" multiple @change="setDocuments(businessDocuments, $event.target.files)" />
          <strong>{{ t("tradeProgram.application.chooseFile") }}</strong>
        </label>
      </section>

      <section class="trade-form-section">
        <h2>{{ t("tradeProgram.application.taxDocuments") }}</h2>
        <p>{{ t("tradeProgram.application.taxDocumentsHelp") }}</p>
        <label class="trade-file-field">
          <span>{{ taxDocuments.length ? taxDocuments.map((file) => file.name).join(", ") : t("tradeProgram.application.addAttachment") }}</span>
          <input type="file" multiple @change="setDocuments(taxDocuments, $event.target.files)" />
          <strong>{{ t("tradeProgram.application.chooseFile") }}</strong>
        </label>
      </section>

      <section class="trade-form-section trade-consent-section">
        <p>{{ t("tradeProgram.application.reviewTiming") }}</p>
        <label>
          <input v-model="form.emailOptIn" type="checkbox" />
          <span>{{ t("tradeProgram.application.emailOptIn") }}</span>
        </label>
        <label>
          <input v-model="form.privacyAccepted" type="checkbox" />
          <span>{{ t("tradeProgram.application.privacy") }}</span>
        </label>
      </section>

      <p v-if="successNotice" class="auth-success">{{ successNotice }}</p>
      <p v-if="error" class="auth-error">{{ error }}</p>
      <button class="trade-submit-button" type="submit" :disabled="!canSubmit">
        {{ busy ? t("common.working") : t("tradeProgram.application.submit") }}
      </button>
    </form>
  </section>
</template>
