<script setup>
import { computed } from "vue";

const props = defineProps({
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

const logoSrc = computed(() =>
  props.tone === "dark" ? "/assets/brand/oakved-logo-black.png" : "/assets/brand/oakved-logo-white.png",
);
</script>

<template>
  <p class="eyebrow brand-eyebrow" :class="`brand-eyebrow-${tone}`">
    <img class="brand-eyebrow-logo" :src="logoSrc" alt="Oakved" />
    <span v-if="suffix" class="brand-eyebrow-suffix">{{ suffix }}</span>
  </p>
</template>
