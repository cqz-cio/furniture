<script setup>
import { computed, ref } from "vue";
import { loginByTradeAccount } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated", "sign-in"]);

const tradeId = ref("");
const email = ref("");
const busy = ref(false);
const error = ref("");

const isEmailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value));
const canSubmit = computed(() => tradeId.value.trim().length > 0 && isEmailValid.value && !busy.value);

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  try {
    const session = await loginByTradeAccount({ tradeId: tradeId.value, email: email.value });
    emit("authenticated", session);
  } catch {
    error.value = "Trade sign in is unavailable. Please contact your trade leader.";
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form account-trade-form" @submit.prevent="submit">
    <p class="auth-intro">
      Contact your trade leader to schedule a viewing of our latest collections.
    </p>
    <label class="auth-field">
      <span class="sr-only">Trade ID</span>
      <input v-model.trim="tradeId" autocomplete="off" placeholder="Trade ID" type="text" />
    </label>
    <label class="auth-field">
      <span class="sr-only">Email Address</span>
      <input v-model.trim="email" autocomplete="email" inputmode="email" placeholder="Email Address" type="email" />
    </label>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button class="auth-primary-button" type="submit" :disabled="!canSubmit">
      {{ busy ? "WORKING..." : "SIGN IN" }}
    </button>
    <div class="account-modal-links is-stacked">
      <a href="#">Apply for a Trade Account</a>
      <a href="#">Trade FAQ</a>
      <button type="button" @click="emit('sign-in')">Return to Sign In</button>
    </div>
  </form>
</template>
