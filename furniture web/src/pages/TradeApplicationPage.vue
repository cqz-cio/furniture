<script setup>
import { computed, reactive, ref } from "vue";
import TradeProgramNav from "../components/TradeProgramNav.vue";
import { useI18n } from "../i18n.js";
import { isEmailAddress } from "../services/formValidation.js";
import {
  businessDescriptionOptions,
  businessInfoFields,
  countryOptions,
  socialFields,
  stateOptions,
  tradeRoutes,
} from "../services/tradeProgram.js";
import { submitTradeApplication, uploadTradeApplicationAttachment } from "../services/yudaoAuthApi.js";

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
const businessDocumentInput = ref(null);
const taxDocumentInput = ref(null);
const busy = ref(false);
const uploadBusy = ref(false);
const successNotice = ref("");
const error = ref("");
const errorAction = ref("");

const optionMap = {
  country: countryOptions,
  state: stateOptions,
  businessDescription: businessDescriptionOptions,
};

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
      isEmailAddress(user.email) &&
      user.email === user.confirmEmail,
  );
  return requiredBusinessFields && hasAuthorizedUser && businessDocuments.value.length > 0 && form.privacyAccepted && !busy.value && !uploadBusy.value;
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

const uploadDocumentFiles = (target, files) => {
  const selectedFiles = Array.from(files || []);
  if (selectedFiles.length === 0) return;
  error.value = "";
  errorAction.value = "";
  target.value = selectedFiles.map((file) => ({
    name: file.name,
    file,
    url: "",
  }));
};

const openDocumentPicker = (inputRef) => {
  inputRef.value?.click();
};

const handleDocumentFileChange = (target, event) => {
  uploadDocumentFiles(target, event.target.files);
  event.target.value = "";
};

const openBusinessDocumentPicker = () => {
  openDocumentPicker(businessDocumentInput);
};

const openTaxDocumentPicker = () => {
  openDocumentPicker(taxDocumentInput);
};

const handleBusinessDocumentFileChange = (event) => {
  handleDocumentFileChange(businessDocuments, event);
};

const handleTaxDocumentFileChange = (event) => {
  handleDocumentFileChange(taxDocuments, event);
};

const uploadSelectedDocuments = async (target) => {
  const uploadedDocuments = [];
  for (const document of target.value) {
    if (document.url) {
      uploadedDocuments.push({ name: document.name, url: document.url });
      continue;
    }
    uploadedDocuments.push(await uploadTradeApplicationAttachment(document.file));
  }
  target.value = uploadedDocuments;
  return uploadedDocuments;
};

const uploadAllDocuments = async () => {
  uploadBusy.value = true;
  try {
    await Promise.all([uploadSelectedDocuments(businessDocuments), uploadSelectedDocuments(taxDocuments)]);
  } catch (caught) {
    const uploadError = caught instanceof Error ? caught : new Error("Trade document upload failed");
    uploadError.stage = "upload";
    throw uploadError;
  } finally {
    uploadBusy.value = false;
  }
};

const buildPayload = () => ({
  ...form,
  primaryEmail: authorizedUsers.value[0]?.email || "",
  authorizedUsers: authorizedUsers.value.map((user) => ({ ...user })),
  businessDocuments: businessDocuments.value.map(({ name, url }) => ({ name, url })),
  taxDocuments: taxDocuments.value.map(({ name, url }) => ({ name, url })),
});

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  successNotice.value = "";
  error.value = "";
  errorAction.value = "";
  try {
    await uploadAllDocuments();
    const result = await submitTradeApplication(buildPayload());
    successNotice.value = t("tradeProgram.application.successNotice", { id: result?.id ?? "-" });
  } catch (caught) {
    error.value =
      caught?.stage === "upload" ? t("tradeProgram.application.uploadError") : t("tradeProgram.application.submitError");
    if (caught?.stage === "upload") {
      errorAction.value = "attachments";
    } else {
      errorAction.value = "retry";
    }
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

      <section id="trade-application-documents" class="trade-form-section">
        <h2>{{ t("tradeProgram.application.businessDocuments") }}</h2>
        <p>{{ t("tradeProgram.application.businessDocumentsHelp") }}</p>
        <div class="trade-file-field">
          <span>{{ businessDocuments.length ? businessDocuments.map((file) => file.name).join(", ") : t("tradeProgram.application.addAttachment") }}</span>
          <input ref="businessDocumentInput" class="trade-file-input" type="file" multiple @change="handleBusinessDocumentFileChange" />
          <button class="trade-file-button" type="button" :disabled="busy || uploadBusy" @click="openBusinessDocumentPicker">
            {{ uploadBusy ? t("tradeProgram.application.uploading") : t("tradeProgram.application.chooseFile") }}
          </button>
        </div>
      </section>

      <section class="trade-form-section">
        <h2>{{ t("tradeProgram.application.taxDocuments") }}</h2>
        <p>{{ t("tradeProgram.application.taxDocumentsHelp") }}</p>
        <div class="trade-file-field">
          <span>{{ taxDocuments.length ? taxDocuments.map((file) => file.name).join(", ") : t("tradeProgram.application.addAttachment") }}</span>
          <input ref="taxDocumentInput" class="trade-file-input" type="file" multiple @change="handleTaxDocumentFileChange" />
          <button class="trade-file-button" type="button" :disabled="busy || uploadBusy" @click="openTaxDocumentPicker">
            {{ uploadBusy ? t("tradeProgram.application.uploading") : t("tradeProgram.application.chooseFile") }}
          </button>
        </div>
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
      <div v-if="errorAction" class="trade-application-recovery">
        <a v-if="errorAction === 'attachments'" href="#trade-application-documents">
          {{ t("tradeProgram.application.fixAttachments") }}
        </a>
        <button v-else type="button" :disabled="!canSubmit" @click="submit">
          {{ t("tradeProgram.application.retrySubmit") }}
        </button>
      </div>
      <button class="trade-submit-button" type="submit" :disabled="!canSubmit">
        {{ busy ? t("common.working") : t("tradeProgram.application.submit") }}
      </button>
    </form>
  </section>
</template>
