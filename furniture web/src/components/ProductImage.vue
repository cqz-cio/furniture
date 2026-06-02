<script setup>
defineProps({
  src: {
    type: String,
    default: "",
  },
  label: {
    type: String,
    required: true,
  },
});
</script>

<template>
  <div class="product-image-frame">
    <img v-if="src" :src="src" :alt="label" loading="lazy" />
    <div v-else class="product-image-fallback" aria-hidden="true">
      <span>{{ label }}</span>
    </div>
  </div>
</template>
