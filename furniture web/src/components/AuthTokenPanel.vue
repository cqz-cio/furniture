<script setup>
import { ref } from "vue";
import { readYudaoToken, writeYudaoToken } from "../services/yudaoClient.js";

const emit = defineEmits(["token-change"]);
const token = ref(readYudaoToken());

const save = () => {
  writeYudaoToken(token.value);
  emit("token-change", token.value.trim());
};

const clear = () => {
  token.value = "";
  writeYudaoToken("");
  emit("token-change", "");
};
</script>

<template>
  <section class="auth-token-panel" aria-label="Yudao token">
    <label>
      <span>Yudao App Token</span>
      <input v-model="token" autocomplete="off" type="password" />
    </label>
    <div class="auth-token-actions">
      <button type="button" @click="save">Save Token</button>
      <button type="button" @click="clear">Clear</button>
    </div>
  </section>
</template>
