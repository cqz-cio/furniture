<script setup>
import SaleCategoryTile from "../components/SaleCategoryTile.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { saleCategories, saleCategoryLinkHref, saleQuickLinks } from "../data/rhLayout.js";
</script>

<template>
  <section class="sale-hero">
    <picture class="sale-hero-picture">
      <source media="(max-width: 760px)" :srcset="generatedFurnitureAssets.sale.hero.mobile" />
      <img
        class="sale-hero-image"
        :src="generatedFurnitureAssets.sale.hero.desktop"
        alt="RH Sale living room with upholstered seating and warm neutral furnishings"
      />
    </picture>
    <div class="sale-hero-copy">
      <p class="eyebrow">RH Sale</p>
      <h1>Sale</h1>
    </div>
  </section>

  <section class="sale-links" aria-label="Sale quick links">
    <a v-for="category in saleQuickLinks" :key="category.title" :href="saleCategoryLinkHref(category)">
      {{ category.title }}
    </a>
  </section>

  <section class="sale-grid" aria-label="Sale category modules">
    <SaleCategoryTile v-for="category in saleCategories" :key="category.title" :category="category" />
  </section>

  <section class="sale-membership-slot" aria-label="RH Members Program sale benefits">
    <picture class="sale-membership-picture">
      <source media="(max-width: 760px)" :srcset="generatedFurnitureAssets.sale.membership.mobile" />
      <img
        class="sale-membership-image"
        :src="generatedFurnitureAssets.sale.membership.desktop"
        alt="RH Members Program savings invitation"
      />
    </picture>
  </section>
</template>
