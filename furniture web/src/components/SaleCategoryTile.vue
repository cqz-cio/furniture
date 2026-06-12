<script setup>
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { saleCategoryLinkHref } from "../data/rhLayout.js";

defineProps({
  category: {
    type: Object,
    required: true,
  },
});

const categoryAsset = (category, device) => generatedFurnitureAssets.sale.categories[category.title]?.[device] || "";
</script>

<template>
  <a class="sale-tile" :href="saleCategoryLinkHref(category)" :aria-label="`Shop ${category.title} sale`">
    <picture class="sale-tile-picture">
      <source media="(max-width: 760px)" :srcset="categoryAsset(category, 'mobile')" />
      <img
        class="sale-tile-image"
        :src="categoryAsset(category, 'desktop')"
        :alt="`${category.title} sale furniture collection`"
      />
    </picture>
    <span class="sale-tile-title">{{ category.title }}</span>
  </a>
</template>
