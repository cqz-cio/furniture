<script setup>
import AuthTokenPanel from "./AuthTokenPanel.vue";

defineProps({
  open: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["close", "auth-change"]);

const handleTokenChange = (token) => {
  emit("auth-change", token ? { accessToken: token } : null);
};
</script>

<template>
  <div v-if="open" class="account-modal-layer" role="presentation">
    <section class="account-modal" role="dialog" aria-modal="true" aria-labelledby="account-modal-title">
      <button class="account-modal-close" type="button" aria-label="Close sign in" @click="emit('close')">
        <span></span>
        <span></span>
      </button>
      <h2 id="account-modal-title">SIGN IN</h2>
      <p>
        Please enter your email address to sign in, or
        <a href="/account/register">Create an Account</a>
      </p>
      <form class="account-signin-form">
        <input type="email" placeholder="Email" aria-label="Email" autocomplete="email" />
        <a class="forgot-password" href="/account/forgot-password">Forgot Password?</a>
        <button type="button">SIGN IN</button>
      </form>
      <AuthTokenPanel @token-change="handleTokenChange" />
      <div class="account-modal-links">
        <a href="/account/sign-in">Sign In With a Secure Link</a>
        <a href="/membership">Members Program</a>
      </div>
    </section>
  </div>
</template>
