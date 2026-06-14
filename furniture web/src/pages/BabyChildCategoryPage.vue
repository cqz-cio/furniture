<script setup>
import { computed } from "vue";

const props = defineProps({
  pageKey: {
    type: String,
    default: "baby-child-furniture",
  },
});

const categoryPages = {
  "baby-child-furniture": {
    title: "Furniture",
    summary: "Cribs, beds, dressers, changing tables and storage for growing rooms.",
  },
  "baby-child-bedding": {
    title: "Bedding",
    summary: "Sheets, quilts, blankets and layered nursery textiles.",
  },
  "baby-child-nursery": {
    title: "Nursery",
    summary: "Foundational pieces for first rooms, quiet routines and early storage.",
  },
  "baby-child-decor": {
    title: "Décor",
    summary: "Wall art, mirrors, accents and finishing pieces for child spaces.",
  },
  "baby-child-lighting": {
    title: "Lighting",
    summary: "Table lamps, floor lamps, sconces and soft overhead lighting.",
  },
  "baby-child-rugs": {
    title: "Rugs",
    summary: "Soft, durable foundations for nursery, bedroom and playroom layouts.",
  },
  "baby-child-windows": {
    title: "Windows",
    summary: "Drapery, shades and window layers for calmer child rooms.",
  },
  "baby-child-storage": {
    title: "Storage",
    summary: "Dressers, bookcases, bins and cabinets for everyday organization.",
  },
  "baby-child-playroom": {
    title: "Playroom",
    summary: "Tables, seating and storage scaled for reading, play and reset.",
  },
  "baby-child-gifts": {
    title: "Gifts",
    summary: "Considered keepsakes, nursery essentials and family-ready pieces.",
  },
  "baby-child-teen": {
    title: "Teen",
    summary: "A bridge to RH Teen with more mature room and study pieces.",
  },
  "baby-child-sale": {
    title: "Sale",
    summary: "Promotional Baby & Child categories and seasonal savings.",
  },
  "baby-child-registry": {
    title: "Registry",
    summary: "Registry services, gift guidance and nursery planning entry points.",
  },
};

const currentCategory = computed(() => categoryPages[props.pageKey] || categoryPages["baby-child-furniture"]);

const placeholderModules = [
  "Hero / category campaign image",
  "Category filters and sort controls",
  "Product grid cards",
  "Featured collection banner",
];
</script>

<template>
  <section class="baby-category-placeholder">
    <div class="baby-category-intro">
      <p class="eyebrow">RH Baby &amp; Child</p>
      <h1>{{ currentCategory.title }}</h1>
      <p>{{ currentCategory.summary }}</p>
      <span>Product page placeholder</span>
    </div>

    <div class="baby-category-grid" aria-label="Baby and child category placeholder modules">
      <article v-for="module in placeholderModules" :key="module">
        <span>{{ module }}</span>
        <p>占位中，后续接入该分类真实商品、筛选和图片素材。</p>
      </article>
    </div>
  </section>
</template>
