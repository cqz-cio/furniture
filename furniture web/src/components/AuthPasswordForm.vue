<script setup>
import { computed, ref } from "vue";
import { loginByPassword } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated"]);

const mobile = ref("");
const password = ref("");
const error = ref("");
const busy = ref(false);

const mobilePattern = /^(?:\+?86|0086)?1\d{10}$/;
const canSubmit = computed(
  () => mobilePattern.test(mobile.value) && password.value.length >= 4 && password.value.length <= 16 && !busy.value
);

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  try {
    const session = await loginByPassword({ mobile: mobile.value, password: password.value });
    emit("authenticated", session);
  } catch {
    error.value = "Mobile or password is incorrect.";
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form" @submit.prevent="submit">
    <label>
      <span>Mobile</span>
      <input v-model.trim="mobile" autocomplete="tel" inputmode="tel" type="tel" />
    </label>
    <label>
      <span>Password</span>
      <input v-model="password" autocomplete="current-password" type="password" />
    </label>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button type="submit" :disabled="!canSubmit">{{ busy ? "Working..." : "SIGN IN" }}</button>
  </form>
</template>
