<script setup>
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();
const catalogArtwork = generatedFurnitureAssets.home.modules["005"];
</script>

<template>
  <article class="catalog-page">
    <section class="catalog-hero" aria-labelledby="catalog-title">
      <picture class="catalog-hero-picture">
        <source media="(max-width: 760px)" :srcset="catalogArtwork.mobile" />
        <img :src="catalogArtwork.desktop" :alt="t('catalogPage.imageAlt')" />
      </picture>

      <div class="catalog-hero-scrim" aria-hidden="true"></div>
      <div class="catalog-hero-copy">
        <p class="catalog-hero-eyebrow">{{ t("catalogPage.eyebrow") }}</p>
        <h1 id="catalog-title">{{ t("catalogPage.title") }}</h1>
        <p class="catalog-hero-introduction">{{ t("catalogPage.introduction") }}</p>
      </div>
    </section>
  </article>
</template>
