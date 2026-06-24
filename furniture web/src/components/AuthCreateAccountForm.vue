<script setup>
import { computed, onUnmounted, ref } from "vue";
import { useI18n } from "../i18n.js";
import {
  createEmailCaptchaChallenge,
  registerByEmail,
  sendEmailRegistrationCode,
  verifyEmailCaptchaChallenge,
  YUDAO_MEMBER_ERROR_CODES,
} from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated", "sign-in", "trade"]);
const { t } = useI18n();

const firstName = ref("");
const lastName = ref("");
const email = ref("");
const password = ref("");
const verificationCode = ref("");
const emailOptIn = ref(true);
const privacyAccepted = ref(false);
const busy = ref(false);
const codeBusy = ref(false);
const codeCooldown = ref(0);
const error = ref("");
const notice = ref("");
const captchaChallenge = ref(null);
const captchaAnswer = ref("");
const captchaBusy = ref(false);
const captchaError = ref("");
let cooldownTimer = null;

const isEmailValid = computed(() => email.value.length <= 255 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value));
const isCodeValid = computed(() => /^\d{6}$/.test(verificationCode.value));
const canSendCode = computed(
  () => isEmailValid.value && !busy.value && !codeBusy.value && !captchaChallenge.value && codeCooldown.value === 0,
);
const canSubmit = computed(
  () =>
    firstName.value.trim().length > 0 &&
    lastName.value.trim().length > 0 &&
    isEmailValid.value &&
    password.value.length >= 4 &&
    password.value.length <= 16 &&
    isCodeValid.value &&
    privacyAccepted.value &&
    !busy.value,
);

const startCodeCooldown = () => {
  codeCooldown.value = 60;
  window.clearInterval(cooldownTimer);
  cooldownTimer = window.setInterval(() => {
    codeCooldown.value = Math.max(0, codeCooldown.value - 1);
    if (codeCooldown.value === 0) {
      window.clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
  }, 1000);
};

const resetCaptcha = () => {
  captchaChallenge.value = null;
  captchaAnswer.value = "";
  captchaError.value = "";
};

const emailCodeErrorMessage = (caught) => {
  switch (Number(caught?.code)) {
    case YUDAO_MEMBER_ERROR_CODES.USER_EMAIL_USED:
      return t("auth.emailCode.emailExists");
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_NOT_FOUND:
      return t("auth.emailCode.invalidCode");
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_EXPIRED:
      return t("auth.emailCode.expiredCode");
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_USED:
      return t("auth.emailCode.usedCode");
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CAPTCHA_REQUIRED:
      return t("auth.emailCode.captchaRequired");
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CAPTCHA_INVALID:
      return t("auth.emailCode.captchaInvalid");
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CODE_VERIFY_TOO_MANY:
      return t("auth.emailCode.tooManyWrong");
    default:
      return t("auth.emailCode.error");
  }
};

const registerErrorMessage = (caught) => {
  switch (Number(caught?.code)) {
    case YUDAO_MEMBER_ERROR_CODES.USER_EMAIL_USED:
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_NOT_FOUND:
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_EXPIRED:
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CREDENTIAL_USED:
    case YUDAO_MEMBER_ERROR_CODES.EMAIL_CODE_VERIFY_TOO_MANY:
      return emailCodeErrorMessage(caught);
    default:
      return t("auth.create.error");
  }
};

const openCaptchaChallenge = async () => {
  captchaBusy.value = true;
  captchaError.value = "";
  try {
    captchaChallenge.value = await createEmailCaptchaChallenge();
    captchaAnswer.value = "";
  } catch {
    resetCaptcha();
    error.value = t("auth.emailCode.error");
  } finally {
    captchaBusy.value = false;
  }
};

const refreshCaptchaChallenge = () => {
  openCaptchaChallenge();
};

const sendCode = async () => {
  if (!canSendCode.value) return;
  codeBusy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await sendEmailRegistrationCode(email.value);
    notice.value = t("auth.emailCode.notice");
    startCodeCooldown();
  } catch (caught) {
    error.value = emailCodeErrorMessage(caught);
    if (
      Number(caught?.code) === YUDAO_MEMBER_ERROR_CODES.EMAIL_CAPTCHA_REQUIRED ||
      Number(caught?.code) === YUDAO_MEMBER_ERROR_CODES.EMAIL_CAPTCHA_INVALID
    ) {
      await openCaptchaChallenge();
    }
  } finally {
    codeBusy.value = false;
  }
};

const handleCaptchaSubmit = async () => {
  if (!captchaChallenge.value || captchaBusy.value || !captchaAnswer.value.trim()) return;
  captchaBusy.value = true;
  captchaError.value = "";
  error.value = "";
  try {
    const result = await verifyEmailCaptchaChallenge({
      challengeId: captchaChallenge.value.challengeId,
      code: captchaAnswer.value.trim(),
    });
    await sendEmailRegistrationCode(email.value, {
      captchaVerification: result?.captchaVerification,
    });
    notice.value = t("auth.emailCode.notice");
    startCodeCooldown();
    resetCaptcha();
  } catch (caught) {
    captchaError.value = emailCodeErrorMessage(caught);
    error.value = captchaError.value;
    try {
      captchaChallenge.value = await createEmailCaptchaChallenge();
      captchaAnswer.value = "";
    } catch {
      resetCaptcha();
    }
  } finally {
    captchaBusy.value = false;
  }
};

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const session = await registerByEmail({
      firstName: firstName.value,
      lastName: lastName.value,
      email: email.value,
      password: password.value,
      code: verificationCode.value,
      emailOptIn: emailOptIn.value,
      privacyAccepted: privacyAccepted.value,
    });
    emit("authenticated", session);
  } catch (caught) {
    error.value = registerErrorMessage(caught);
    if (Number(caught?.code) === YUDAO_MEMBER_ERROR_CODES.EMAIL_CODE_VERIFY_TOO_MANY) {
      await openCaptchaChallenge();
    }
  } finally {
    busy.value = false;
  }
};

onUnmounted(() => {
  window.clearInterval(cooldownTimer);
});
</script>

<template>
  <form class="auth-form account-create-form" @submit.prevent="submit">
    <p class="auth-intro">
      {{ t("auth.create.intro") }}
    </p>

    <div class="auth-two-column">
      <label class="auth-field">
        <span class="sr-only">{{ t("auth.fields.firstName") }}</span>
        <input
          v-model.trim="firstName"
          autocomplete="given-name"
          :placeholder="t('auth.fields.firstName')"
          type="text"
        />
      </label>
      <label class="auth-field">
        <span class="sr-only">{{ t("auth.fields.lastName") }}</span>
        <input
          v-model.trim="lastName"
          autocomplete="family-name"
          :placeholder="t('auth.fields.lastName')"
          type="text"
        />
      </label>
      <div class="auth-code-row auth-code-row-wide">
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
        <button class="auth-secondary-button" type="button" :disabled="!canSendCode" @click="sendCode">
          {{ codeBusy ? t("auth.emailCode.sending") : codeCooldown > 0 ? `${codeCooldown}s` : t("auth.emailCode.send") }}
        </button>
      </div>

      <section v-if="captchaChallenge" class="auth-captcha-panel auth-code-row-wide" role="dialog" aria-modal="true">
        <div class="auth-captcha-head">
          <div>
            <p>{{ t("auth.emailCode.captchaTitle") }}</p>
            <span>{{ captchaChallenge.instruction || t("auth.emailCode.captchaHelp") }}</span>
          </div>
          <button class="auth-inline-link" type="button" @click="resetCaptcha">
            {{ t("auth.emailCode.captchaClose") }}
          </button>
        </div>
        <div class="auth-captcha-image-row">
          <button class="auth-captcha-image-button" type="button" :disabled="captchaBusy" @click="refreshCaptchaChallenge">
            <img :src="captchaChallenge.imageBase64" :alt="t('auth.emailCode.captchaTitle')" />
          </button>
          <label class="auth-field auth-captcha-input">
            <span class="sr-only">{{ t("auth.emailCode.captchaInput") }}</span>
            <input
              v-model.trim="captchaAnswer"
              autocomplete="off"
              maxlength="12"
              :placeholder="t('auth.emailCode.captchaInput')"
              type="text"
              @keydown.enter.prevent="handleCaptchaSubmit"
            />
          </label>
        </div>
        <div class="auth-captcha-footer">
          <button class="auth-inline-link" type="button" :disabled="captchaBusy" @click="refreshCaptchaChallenge">
            {{ t("auth.emailCode.captchaRefresh") }}
          </button>
          <button class="auth-secondary-button" type="button" :disabled="captchaBusy || !captchaAnswer.trim()" @click="handleCaptchaSubmit">
            {{ t("auth.emailCode.captchaSubmit") }}
          </button>
          <span v-if="captchaBusy">{{ t("auth.emailCode.verifying") }}</span>
        </div>
        <p v-if="captchaError" class="auth-error">{{ captchaError }}</p>
      </section>

      <label class="auth-field">
        <span class="sr-only">{{ t("auth.fields.password") }}</span>
        <input
          v-model="password"
          autocomplete="new-password"
          maxlength="16"
          minlength="4"
          :placeholder="t('auth.fields.password')"
          type="password"
        />
      </label>
      <label class="auth-field">
        <span class="sr-only">{{ t("auth.fields.verificationCode") }}</span>
        <input
          v-model.trim="verificationCode"
          autocomplete="one-time-code"
          inputmode="numeric"
          maxlength="6"
          :placeholder="t('auth.fields.verificationCode')"
          type="text"
        />
      </label>
    </div>

    <label class="auth-check-row">
      <input v-model="emailOptIn" type="checkbox" />
      <span>{{ t("auth.create.emailOptIn") }}</span>
    </label>

    <label class="auth-check-row auth-privacy-row">
      <input v-model="privacyAccepted" type="checkbox" />
      <span>
        {{ t("auth.create.privacyPrefix") }}
        <a href="/membership/terms">{{ t("auth.create.privacyNotice") }}</a>
      </span>
    </label>

    <p v-if="notice" class="auth-success">{{ notice }}</p>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button class="auth-primary-button" type="submit" :disabled="!canSubmit">
      {{ busy ? t("common.working") : t("auth.create.submit") }}
    </button>

    <div class="account-modal-links is-centered">
      <button type="button" @click="emit('sign-in')">{{ t("auth.returnToSignIn") }}</button>
      <button type="button" @click="emit('trade')">
        {{ t("auth.trade.createAccount") }}
      </button>
    </div>
  </form>
</template>
