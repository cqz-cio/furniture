<script setup>
import { computed } from "vue";
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const props = defineProps({
  pageKey: {
    type: String,
    default: "baby-child-furniture",
  },
});

const categoryPages = {
  "baby-child-furniture": "furniture",
  "baby-child-bedding": "bedding",
  "baby-child-nursery": "nursery",
  "baby-child-decor": "decor",
  "baby-child-lighting": "lighting",
  "baby-child-rugs": "rugs",
  "baby-child-windows": "windows",
  "baby-child-storage": "storage",
  "baby-child-playroom": "playroom",
  "baby-child-gifts": "gifts",
  "baby-child-teen": "teen",
  "baby-child-sale": "sale",
  "baby-child-registry": "registry",
};

const currentCategoryId = computed(() => categoryPages[props.pageKey] || categoryPages["baby-child-furniture"]);

const categoryCopy = (field) => t(`babyChild.category.categories.${currentCategoryId.value}.${field}`);

const placeholderModules = ["hero", "filters", "grid", "banner"];
</script>

<template>
  <section class="baby-category-placeholder">
    <div class="baby-category-intro">
      <BrandEyebrow :suffix="t('babyChild.hero.eyebrow')" tone="dark" />
      <h1>{{ categoryCopy("title") }}</h1>
      <p>{{ categoryCopy("summary") }}</p>
      <span>{{ t("babyChild.category.placeholderEyebrow") }}</span>
    </div>

    <div class="baby-category-grid" aria-label="Baby and child category placeholder modules">
      <article v-for="module in placeholderModules" :key="module">
        <span>{{ t(`babyChild.category.modules.${module}`) }}</span>
        <p>{{ t("babyChild.category.placeholderDescription") }}</p>
      </article>
    </div>
  </section>
</template>
