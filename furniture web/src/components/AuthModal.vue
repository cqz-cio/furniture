<script setup>
import { computed, ref, watch } from "vue";
import { readYudaoSession } from "../services/authSession.js";
import { logoutMember } from "../services/yudaoClient.js";
import AuthPasswordForm from "./AuthPasswordForm.vue";
import AuthSmsForm from "./AuthSmsForm.vue";
import AuthTokenPanel from "./AuthTokenPanel.vue";

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
});
const emit = defineEmits(["close", "auth-change"]);

const mode = ref("sms");
const session = ref(readYudaoSession());
const logoutBusy = ref(false);
const error = ref("");
const logoutNotice = ref("");
const isAuthenticated = computed(() => Boolean(session.value?.accessToken));

const refreshSession = () => {
  session.value = readYudaoSession();
  logoutNotice.value = "";
  emit("auth-change", session.value);
};

const handleAuthenticated = (nextSession) => {
  session.value = nextSession;
  error.value = "";
  logoutNotice.value = "";
  emit("auth-change", nextSession);
};

const logout = async () => {
  logoutBusy.value = true;
  error.value = "";
  logoutNotice.value = "";
  try {
    await logoutMember();
  } catch {
    logoutNotice.value = "Signed out locally. Remote logout could not be confirmed.";
  } finally {
    session.value = null;
    logoutBusy.value = false;
    emit("auth-change", null);
  }
};

watch(
  () => props.open,
  (open) => {
    if (open) {
      session.value = readYudaoSession();
      error.value = "";
      logoutNotice.value = "";
    }
  }
);
</script>

<template>
  <div v-if="open" class="account-modal-layer" role="presentation">
    <section class="account-modal" role="dialog" aria-modal="true" aria-labelledby="account-modal-title">
      <button class="account-modal-close" type="button" aria-label="Close sign in" @click="emit('close')">
        <span></span>
        <span></span>
      </button>

      <template v-if="isAuthenticated">
        <h2 id="account-modal-title">ACCOUNT</h2>
        <p>Signed in as member {{ session.userId || "with developer access" }}.</p>
        <p v-if="error" class="auth-error">{{ error }}</p>
        <button class="auth-primary-button" type="button" :disabled="logoutBusy" @click="logout">
          {{ logoutBusy ? "Working..." : "SIGN OUT" }}
        </button>
      </template>

      <template v-else>
        <h2 id="account-modal-title">SIGN IN</h2>
        <p>Use your mobile number to access your RH account.</p>
        <p v-if="logoutNotice" class="auth-error">{{ logoutNotice }}</p>
        <div class="auth-mode-tabs" role="tablist" aria-label="Sign in method">
          <button type="button" :class="{ active: mode === 'sms' }" @click="mode = 'sms'">Code</button>
          <button type="button" :class="{ active: mode === 'password' }" @click="mode = 'password'">Password</button>
        </div>
        <AuthSmsForm v-if="mode === 'sms'" @authenticated="handleAuthenticated" />
        <AuthPasswordForm v-else @authenticated="handleAuthenticated" />
        <details class="auth-developer-token">
          <summary>Developer token</summary>
          <AuthTokenPanel @token-change="refreshSession" />
        </details>
      </template>
    </section>
  </div>
</template>
