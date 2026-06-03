<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import { loginBySms, sendMemberSmsCode } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated"]);

const mobile = ref("");
const code = ref("");
const error = ref("");
const busy = ref(false);
const sending = ref(false);
const cooldown = ref(0);
let cooldownTimer = null;

const mobilePattern = /^(?:\+?86|0086)?1\d{10}$/;
const canSend = computed(() => mobilePattern.test(mobile.value) && !sending.value && cooldown.value === 0);
const canSubmit = computed(() => mobilePattern.test(mobile.value) && /^\d{4,6}$/.test(code.value) && !busy.value);

const startCooldown = () => {
  cooldown.value = 60;
  cooldownTimer = window.setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0 && cooldownTimer) {
      window.clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
  }, 1000);
};

const sendCode = async () => {
  if (!canSend.value) return;
  sending.value = true;
  error.value = "";
  try {
    await sendMemberSmsCode(mobile.value);
    startCooldown();
  } catch {
    error.value = "Authentication service is unavailable. Please try again later.";
  } finally {
    sending.value = false;
  }
};

const submit = async () => {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = "";
  try {
    const session = await loginBySms({ mobile: mobile.value, code: code.value });
    emit("authenticated", session);
  } catch {
    error.value = "Verification failed. Please check the code and try again.";
  } finally {
    busy.value = false;
  }
};

onBeforeUnmount(() => {
  if (cooldownTimer) window.clearInterval(cooldownTimer);
});
</script>

<template>
  <form class="auth-form" @submit.prevent="submit">
    <label>
      <span>Mobile</span>
      <input v-model.trim="mobile" autocomplete="tel" inputmode="tel" type="tel" />
    </label>
    <label>
      <span>Verification Code</span>
      <div class="auth-code-row">
        <input v-model.trim="code" autocomplete="one-time-code" inputmode="numeric" type="password" />
        <button type="button" :disabled="!canSend" @click="sendCode">
          {{ cooldown ? `${cooldown}s` : sending ? "Sending..." : "Send" }}
        </button>
      </div>
    </label>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button type="submit" :disabled="!canSubmit">{{ busy ? "Working..." : "SIGN IN / REGISTER" }}</button>
  </form>
</template>
