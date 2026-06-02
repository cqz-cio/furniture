<script setup>
import { categoryImageSpec } from "../data/rhLayout.js";
import ImageSpecPlaceholder from "./ImageSpecPlaceholder.vue";

defineProps({
  category: {
    type: Object,
    required: true,
  },
});
</script>

<template>
  <a class="sale-tile" :href="category.href" target="_blank" rel="noreferrer" :aria-label="`${category.title} 图片区域`">
    <ImageSpecPlaceholder
      class="desktop-category-spec"
      :label="`${category.title} 图片区域 | ${category.imageId}`"
      :rendered="categoryImageSpec.rendered"
      :recommended2x="categoryImageSpec.recommended2x"
      :file-size="categoryImageSpec.fileSize"
      :fit="categoryImageSpec.fit"
      :ratio="categoryImageSpec.ratio"
      :natural="categoryImageSpec.natural"
    />
    <ImageSpecPlaceholder
      class="mobile-category-spec"
      :label="`${category.title} 图片区域（手机端） | ${category.imageId}`"
      :rendered="categoryImageSpec.mobileRendered"
      :recommended2x="categoryImageSpec.mobileRecommended2x"
      :file-size="categoryImageSpec.fileSize"
      :fit="categoryImageSpec.fit"
      :ratio="categoryImageSpec.ratio"
      :natural="categoryImageSpec.mobileNatural"
    />
  </a>
</template>
