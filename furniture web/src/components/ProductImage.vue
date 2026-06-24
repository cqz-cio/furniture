<script setup>
import { computed, ref, watch } from "vue";

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

const hasError = ref(false);
const shouldShowImage = computed(() => props.src && !hasError.value);

watch(
  () => props.src,
  () => {
    hasError.value = false;
  },
);
</script>

<template>
  <div class="product-image-frame">
    <img v-if="shouldShowImage" :src="src" :alt="label" loading="lazy" @error="hasError = true" />
    <img
      v-if="shouldShowImage && hoverSrc"
      class="product-image-hover"
      :src="hoverSrc"
      :alt="`${label} detail view`"
      loading="lazy"
      aria-hidden="true"
    />
    <div v-if="!shouldShowImage" class="product-image-fallback" aria-hidden="true">
      <span>{{ label }}</span>
    </div>
  </div>
</template>
