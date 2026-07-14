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
  hoverSrc: {
    type: String,
    default: "",
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
    <img
      v-if="src && !failed && hoverSrc"
      class="product-image-hover"
      :src="hoverSrc"
      :alt="`${label} detail view`"
      loading="lazy"
      aria-hidden="true"
    />
    <div v-if="!src || failed" class="product-image-fallback" aria-hidden="true">
      <span>{{ label }}</span>
    </div>
  </div>
</template>
