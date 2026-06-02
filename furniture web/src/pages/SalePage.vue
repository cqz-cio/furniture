<script setup>
import ImageSpecPlaceholder from "../components/ImageSpecPlaceholder.vue";
import SaleCategoryTile from "../components/SaleCategoryTile.vue";
import { saleCategories, saleHeroSpecs, saleMembershipSpec, saleQuickLinks } from "../data/rhLayout.js";
</script>

<template>
  <section class="sale-hero">
    <ImageSpecPlaceholder
      class="sale-hero-image"
      :label="saleHeroSpecs.desktop.label"
      :rendered="saleHeroSpecs.desktop.rendered"
      :recommended2x="saleHeroSpecs.desktop.recommended2x"
      :file-size="saleHeroSpecs.desktop.fileSize"
      :fit="saleHeroSpecs.desktop.fit"
      :ratio="saleHeroSpecs.desktop.ratio"
      :natural="saleHeroSpecs.desktop.natural"
      tone="dark"
    />
    <ImageSpecPlaceholder
      class="sale-hero-image-mobile"
      :label="saleHeroSpecs.mobile.label"
      :rendered="saleHeroSpecs.mobile.rendered"
      :recommended2x="saleHeroSpecs.mobile.recommended2x"
      :file-size="saleHeroSpecs.mobile.fileSize"
      :fit="saleHeroSpecs.mobile.fit"
      :ratio="saleHeroSpecs.mobile.ratio"
      :natural="saleHeroSpecs.mobile.natural"
      tone="dark"
    />
  </section>

  <section class="sale-links" aria-label="Sale quick links">
    <a v-for="category in saleQuickLinks" :key="category.title" :href="category.href" target="_blank" rel="noreferrer">
      {{ category.title }}
    </a>
  </section>

  <section class="sale-grid" aria-label="Sale category modules">
    <SaleCategoryTile v-for="category in saleCategories" :key="category.title" :category="category" />
  </section>

  <section class="sale-membership-slot" aria-label="Sale 会员横幅图片区域">
    <ImageSpecPlaceholder
      class="desktop-sale-membership"
      :label="saleMembershipSpec.desktop.label"
      :rendered="saleMembershipSpec.desktop.rendered"
      :recommended2x="saleMembershipSpec.desktop.recommended2x"
      :file-size="saleMembershipSpec.desktop.fileSize"
      :fit="saleMembershipSpec.desktop.fit"
      :ratio="saleMembershipSpec.desktop.ratio"
      :natural="saleMembershipSpec.desktop.natural"
      tone="dark"
    />
    <ImageSpecPlaceholder
      class="mobile-sale-membership"
      :label="saleMembershipSpec.mobile.label"
      :rendered="saleMembershipSpec.mobile.rendered"
      :recommended2x="saleMembershipSpec.mobile.recommended2x"
      :file-size="saleMembershipSpec.mobile.fileSize"
      :fit="saleMembershipSpec.mobile.fit"
      :ratio="saleMembershipSpec.mobile.ratio"
      :natural="saleMembershipSpec.mobile.natural"
      tone="dark"
    />
  </section>
</template>
