<script setup>
import { computed, ref } from "vue";
import { isYudaoSessionAuthenticated, readYudaoSession, redactSecret } from "../services/authSession.js";
import { useI18n } from "../i18n.js";
import { readYudaoToken, writeYudaoToken } from "../services/yudaoRequest.js";

const emit = defineEmits(["token-change"]);
const { t } = useI18n();
const token = ref(readYudaoToken());
const savedToken = ref(readYudaoSession()?.accessToken || "");
const isConnected = computed(() => isYudaoSessionAuthenticated() || Boolean(savedToken.value));
const maskedToken = computed(() => redactSecret(savedToken.value || token.value));

const save = () => {
  writeYudaoToken(token.value);
  savedToken.value = token.value.trim();
  emit("token-change", savedToken.value);
};

const clear = () => {
  token.value = "";
  writeYudaoToken("");
  savedToken.value = "";
  emit("token-change", "");
};
</script>

<template>
  <section class="auth-token-panel" :aria-label="t('auth.aria')">
    <div class="auth-token-status" :class="{ connected: isConnected }">
      <span>{{ isConnected ? t("auth.connected") : t("auth.notConnected") }}</span>
      <strong>{{ isConnected ? maskedToken : t("auth.accountLabel") }}</strong>
      <p>{{ t("auth.help") }}</p>
    </div>
    <label>
      <span>{{ t("auth.accessToken") }}</span>
      <input v-model="token" autocomplete="off" type="password" />
    </label>
    <div class="auth-token-actions">
      <button type="button" @click="save">{{ isConnected ? t("auth.updateToken") : t("auth.saveToken") }}</button>
      <button type="button" @click="clear">{{ t("auth.clear") }}</button>
    </div>
  </section>
</template>
