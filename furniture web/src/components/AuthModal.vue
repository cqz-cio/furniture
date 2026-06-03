<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { readYudaoSession } from "../services/authSession.js";
import { logoutMember } from "../services/yudaoClient.js";
import AuthCreateAccountForm from "./AuthCreateAccountForm.vue";
import AuthEmailSignInForm from "./AuthEmailSignInForm.vue";
import AuthTradeSignInForm from "./AuthTradeSignInForm.vue";
import AuthTokenPanel from "./AuthTokenPanel.vue";

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
});
const emit = defineEmits(["close", "auth-change"]);

const mode = ref("signin");
const session = ref(readYudaoSession());
const logoutBusy = ref(false);
const error = ref("");
const logoutNotice = ref("");
const isAuthenticated = computed(() => Boolean(session.value?.accessToken));
const showDeveloperToken = computed(() => import.meta.env.VITE_SHOW_AUTH_TOKEN_PANEL === "true");
const modalTitle = computed(() => {
  if (isAuthenticated.value) return "MY ACCOUNT";
  if (mode.value === "secureLink") return "SIGN IN WITH A SECURE LINK";
  if (mode.value === "create") return "CREATE AN ACCOUNT";
  if (mode.value === "trade") return "TRADE PROGRAM SIGN IN";
  return "SIGN IN";
});
const modalClass = computed(() => (mode.value === "create" ? "is-create-account" : ""));

const refreshSession = () => {
  session.value = readYudaoSession();
  logoutNotice.value = "";
  emit("auth-change", session.value);
};

const setBodyModalState = (isOpen) => {
  if (typeof document === "undefined") return;
  document.body.classList.toggle("auth-modal-open", isOpen);
};

const handleAuthenticated = (nextSession) => {
  session.value = nextSession;
  error.value = "";
  logoutNotice.value = "";
  emit("auth-change", nextSession);
};

const showSignIn = () => {
  mode.value = "signin";
};

const showSecureLink = () => {
  mode.value = "secureLink";
};

const showCreate = () => {
  mode.value = "create";
};

const showTrade = () => {
  mode.value = "trade";
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
      mode.value = "signin";
      error.value = "";
      logoutNotice.value = "";
    }
    setBodyModalState(open);
  },
  { immediate: true },
);

onBeforeUnmount(() => setBodyModalState(false));
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="account-modal-layer" role="presentation">
      <section
        class="account-modal"
        :class="modalClass"
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-modal-title"
      >
        <button class="account-modal-close" type="button" aria-label="Close sign in" @click="emit('close')">
          <span></span>
          <span></span>
        </button>

        <template v-if="isAuthenticated">
          <h2 id="account-modal-title">{{ modalTitle }}</h2>
          <p class="account-welcome">WELCOME BACK {{ session.userId ? `MEMBER ${session.userId}` : "FURNITURE" }}</p>
          <p v-if="error" class="auth-error">{{ error }}</p>
          <nav class="account-menu" aria-label="Account links">
            <a href="#">ORDER HISTORY</a>
            <a href="#">WISHLIST</a>
            <a href="#">MEMBERSHIP</a>
            <a href="#">GIFT REGISTRY</a>
            <a href="#">PROFILE</a>
          </nav>
          <button class="auth-primary-button" type="button" :disabled="logoutBusy" @click="logout">
            {{ logoutBusy ? "Working..." : "SIGN OUT" }}
          </button>
        </template>

        <template v-else>
          <h2 id="account-modal-title">{{ modalTitle }}</h2>
          <p v-if="logoutNotice" class="auth-error">{{ logoutNotice }}</p>
          <AuthEmailSignInForm
            v-if="mode === 'signin'"
            @secure-link="showSecureLink"
            @create-account="showCreate"
            @trade="showTrade"
          />
          <AuthEmailSignInForm
            v-else-if="mode === 'secureLink'"
            variant="secureLink"
            @sign-in="showSignIn"
            @create-account="showCreate"
            @trade="showTrade"
          />
          <AuthCreateAccountForm
            v-else-if="mode === 'create'"
            @authenticated="handleAuthenticated"
            @sign-in="showSignIn"
            @trade="showTrade"
          />
          <AuthTradeSignInForm v-else @authenticated="handleAuthenticated" @sign-in="showSignIn" />
          <details v-if="showDeveloperToken" class="auth-developer-token">
            <summary>Developer token</summary>
            <AuthTokenPanel @token-change="refreshSession" />
          </details>
        </template>
      </section>
    </div>
  </Teleport>
</template>
