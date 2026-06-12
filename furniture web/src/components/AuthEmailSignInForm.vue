<script setup>
import { computed, ref } from "vue";
import { useI18n } from "../i18n.js";
import { isEmailAddress, isPasswordInRange } from "../services/formValidation.js";
import { loginByEmailPassword, requestEmailSignInLink } from "../services/yudaoAuthApi.js";

const props = defineProps({
  variant: {
    type: String,
    default: "signin",
  },
});
const emit = defineEmits(["secure-link", "create-account", "trade", "sign-in", "authenticated"]);
const { t } = useI18n();

const email = ref("");
const password = ref("");
const busy = ref(false);
const notice = ref("");
const error = ref("");

const isSecureLink = computed(() => props.variant === "secureLink");
const isEmailValid = computed(() => isEmailAddress(email.value));
const isPasswordValid = computed(() => isPasswordInRange(password.value));
const canSubmit = computed(() => isEmailValid.value && (isSecureLink.value || isPasswordValid.value) && !busy.value);

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  notice.value = "";
  error.value = "";
  try {
    if (isSecureLink.value) {
      await requestEmailSignInLink(email.value);
      notice.value = t("auth.secureLink.notice");
      return;
    }

    const session = await loginByEmailPassword({
      email: email.value,
      password: password.value,
    });
    emit("authenticated", session);
  } catch {
    error.value = isSecureLink.value
      ? t("auth.secureLink.error")
      : t("auth.signIn.error");
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form account-email-form" @submit.prevent="submit">
    <p v-if="isSecureLink" class="auth-intro">
      {{ t("auth.secureLink.intro") }}
    </p>
    <p v-else class="auth-intro">
      {{ t("auth.signIn.intro") }}
      <button class="auth-inline-link" type="button" @click="emit('create-account')">
        {{ t("auth.create.title") }}
      </button>
    </p>

    <label class="auth-field">
      <span class="sr-only">{{ t("auth.fields.email") }}</span>
      <input
        v-model.trim="email"
        autocomplete="email"
        inputmode="email"
        maxlength="255"
        :placeholder="t('auth.fields.email')"
        type="email"
      />
    </label>
    <label v-if="!isSecureLink" class="auth-field">
      <span class="sr-only">{{ t("auth.fields.password") }}</span>
      <input
        v-model="password"
        autocomplete="current-password"
        maxlength="16"
        minlength="4"
        :placeholder="t('auth.fields.password')"
        type="password"
      />
    </label>

    <button class="auth-primary-button" type="submit" :disabled="!canSubmit">
      {{ busy ? t("common.working") : isSecureLink ? t("auth.secureLink.submit") : t("auth.signIn.submit") }}
    </button>

    <button v-if="!isSecureLink" class="forgot-password" type="button" @click="emit('secure-link')">
      {{ t("auth.signIn.forgotPassword") }}
    </button>

    <p v-if="notice" class="auth-success">{{ notice }}</p>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <div v-if="error" class="auth-recovery-actions">
      <template v-if="!isSecureLink">
        <button type="button" @click="emit('secure-link')">
          {{ t("auth.recovery.useSecureLink") }}
        </button>
        <button type="button" @click="emit('create-account')">
          {{ t("auth.recovery.createAccount") }}
        </button>
      </template>
      <button v-else type="button" @click="emit('sign-in')">
        {{ t("auth.recovery.passwordSignIn") }}
      </button>
    </div>

    <div class="account-modal-links">
      <button v-if="!isSecureLink" type="button" @click="emit('secure-link')">
        {{ t("auth.secureLink.title") }}
      </button>
      <button v-else type="button" @click="emit('sign-in')">{{ t("auth.returnToSignIn") }}</button>
      <button type="button" @click="emit('trade')">{{ t("auth.trade.title") }}</button>
    </div>
  </form>
</template>
