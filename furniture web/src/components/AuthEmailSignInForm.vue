<script setup>
import { computed, ref } from "vue";
import { requestEmailSignInLink } from "../services/yudaoClient.js";

const props = defineProps({
  variant: {
    type: String,
    default: "signin",
  },
});
const emit = defineEmits(["secure-link", "create-account", "trade", "sign-in"]);

const email = ref("");
const busy = ref(false);
const notice = ref("");
const error = ref("");

const isSecureLink = computed(() => props.variant === "secureLink");
const isEmailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value));
const canSubmit = computed(() => isEmailValid.value && !busy.value);

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  notice.value = "";
  error.value = "";
  try {
    await requestEmailSignInLink(email.value);
    notice.value = "If an RH account exists for this email, a secure sign-in link will be sent.";
  } catch {
    error.value = "Authentication service is unavailable. Please try again later.";
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form account-email-form" @submit.prevent="submit">
    <p v-if="isSecureLink" class="auth-intro">
      Enter your email address and you'll receive a link to sign in.
    </p>
    <p v-else class="auth-intro">
      Please enter your email address to sign in, or
      <button class="auth-inline-link" type="button" @click="emit('create-account')">Create an Account</button>
    </p>

    <label class="auth-field">
      <span class="sr-only">Email</span>
      <input v-model.trim="email" autocomplete="email" inputmode="email" placeholder="Email" type="email" />
    </label>

    <button class="auth-primary-button" type="submit" :disabled="!canSubmit">
      {{ busy ? "WORKING..." : isSecureLink ? "CONTINUE" : "SIGN IN" }}
    </button>

    <button v-if="!isSecureLink" class="forgot-password" type="button" @click="emit('secure-link')">
      Forgot Password?
    </button>

    <p v-if="notice" class="auth-success">{{ notice }}</p>
    <p v-if="error" class="auth-error">{{ error }}</p>

    <div class="account-modal-links">
      <button v-if="!isSecureLink" type="button" @click="emit('secure-link')">Sign In With a Secure Link</button>
      <button v-else type="button" @click="emit('sign-in')">Return to Sign In</button>
      <button type="button" @click="emit('trade')">Trade Program Sign In</button>
    </div>
  </form>
</template>
