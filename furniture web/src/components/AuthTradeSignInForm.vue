<script setup>
import { computed, ref } from "vue";
import { useI18n } from "../i18n.js";
import { loginByTradeAccount } from "../services/yudaoClient.js";

const emit = defineEmits(["authenticated", "sign-in"]);
const { t } = useI18n();

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
    error.value = t("auth.trade.error");
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <form class="auth-form account-trade-form" @submit.prevent="submit">
    <p class="auth-intro">
      {{ t("auth.trade.intro") }}
    </p>
    <label class="auth-field">
      <span class="sr-only">{{ t("auth.fields.tradeId") }}</span>
      <input v-model.trim="tradeId" autocomplete="off" :placeholder="t('auth.fields.tradeId')" type="text" />
    </label>
    <label class="auth-field">
      <span class="sr-only">{{ t("auth.fields.emailAddress") }}</span>
      <input
        v-model.trim="email"
        autocomplete="email"
        inputmode="email"
        :placeholder="t('auth.fields.emailAddress')"
        type="email"
      />
    </label>
    <p v-if="error" class="auth-error">{{ error }}</p>
    <button class="auth-primary-button" type="submit" :disabled="!canSubmit">
      {{ busy ? t("common.working") : t("auth.trade.submit") }}
    </button>
    <div class="account-modal-links is-stacked">
      <a href="#">{{ t("auth.trade.apply") }}</a>
      <a href="#">{{ t("auth.trade.faq") }}</a>
      <button type="button" @click="emit('sign-in')">{{ t("auth.returnToSignIn") }}</button>
    </div>
  </form>
</template>
