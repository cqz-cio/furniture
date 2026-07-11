<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  src: {
    type: String,
    default: "",
  },
  label: {
    type: String,
    required: true,
  },
});

const failed = ref(false);

watch(() => props.src, () => {
  failed.value = false;
});
</script>

<template>
  <div class="product-image-frame">
    <img v-if="src && !failed" :src="src" :alt="label" loading="lazy" @error="failed = true" />
    <div v-else class="product-image-fallback" aria-hidden="true">
      <span>{{ label }}</span>
    </div>
  </div>
</template>
