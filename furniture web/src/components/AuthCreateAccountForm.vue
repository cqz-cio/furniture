<script setup>
import { computed, ref } from "vue";
import { registerByEmail } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated", "sign-in", "trade"]);

const firstName = ref("");
const lastName = ref("");
const email = ref("");
const password = ref("");
const emailOptIn = ref(true);
const privacyAccepted = ref(false);
const busy = ref(false);
const error = ref("");

const isEmailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value));
const canSubmit = computed(
  () =>
    firstName.value.trim().length > 0 &&
    lastName.value.trim().length > 0 &&
    isEmailValid.value &&
    password.value.length >= 8 &&
    privacyAccepted.value &&
    !busy.value,
);

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  try {
    const session = await registerByEmail({
      firstName: firstName.value,
      lastName: lastName.value,
      email: email.value,
      password: password.value,
      emailOptIn: emailOptIn.value,
      privacyAccepted: privacyAccepted.value,
    });
    emit("authenticated", session);
  } catch {
    error.value = "Account creation is unavailable. Please try again later.";
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form account-create-form" @submit.prevent="submit">
    <p class="auth-intro">
      Discover the benefits of an RH account. Start a wishlist, save shipping and payment details and seamlessly manage
      your RH membership.
    </p>

    <div class="auth-two-column">
      <label class="auth-field">
        <span class="sr-only">First Name</span>
        <input v-model.trim="firstName" autocomplete="given-name" placeholder="First Name" type="text" />
      </label>
      <label class="auth-field">
        <span class="sr-only">Last Name</span>
        <input v-model.trim="lastName" autocomplete="family-name" placeholder="Last Name" type="text" />
      </label>
      <label class="auth-field">
        <span class="sr-only">Email</span>
        <input v-model.trim="email" autocomplete="email" inputmode="email" placeholder="Email" type="email" />
      </label>
      <label class="auth-field">
        <span class="sr-only">Password</span>
        <input v-model="password" autocomplete="new-password" placeholder="Password" type="password" />
      </label>
    </div>

    <label class="auth-check-row">
      <input v-model="emailOptIn" type="checkbox" />
      <span>Join our email list and be the first to hear about new collections, gallery openings and special events.</span>
    </label>

    <label class="auth-check-row auth-privacy-row">
      <input v-model="privacyAccepted" type="checkbox" />
      <span>I have read and acknowledge the <a href="#">RH Privacy Notice</a></span>
    </label>

    <p v-if="error" class="auth-error">{{ error }}</p>
    <button class="auth-primary-button" type="submit" :disabled="!canSubmit">
      {{ busy ? "WORKING..." : "CREATE ACCOUNT" }}
    </button>

    <div class="account-modal-links is-centered">
      <button type="button" @click="emit('sign-in')">Return to Sign In</button>
      <button type="button" @click="emit('trade')">Create a Trade Program Account</button>
    </div>
  </form>
</template>
