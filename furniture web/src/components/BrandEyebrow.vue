<script setup>
import { OAKVED_LOGO_SRC } from "../config/brand.js";

defineProps({
  suffix: {
    type: String,
    default: "",
  },
  tone: {
    type: String,
    default: "light",
    validator: (value) => ["light", "dark"].includes(value),
  },
});
</script>

<template>
  <p class="eyebrow brand-eyebrow" :class="`brand-eyebrow-${tone}`">
    <img class="brand-eyebrow-logo" :src="OAKVED_LOGO_SRC" alt="Oakved" />
    <span v-if="suffix" class="brand-eyebrow-suffix">{{ suffix }}</span>
  </p>
</template>
