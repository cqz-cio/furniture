<script setup>
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const heroImage = generatedFurnitureAssets.outdoor.hero;

const edits = ["lounge", "dining", "poolside", "lighting"];

const collections = [
  {
    id: "lounge",
    altLabel: "Outdoor Lounge",
    image: generatedFurnitureAssets.sale.categories.Outdoor.desktop,
    href: "/sofas-plp",
  },
  {
    id: "dining",
    altLabel: "Dining",
    image: generatedFurnitureAssets.sale.categories.Dining.desktop,
    href: "/missing",
  },
  {
    id: "shade",
    altLabel: "Shade and texture",
    image: generatedFurnitureAssets.sale.categories.Rugs.desktop,
    href: "/missing",
  },
  {
    id: "lighting",
    altLabel: "Lighting",
    image: generatedFurnitureAssets.sale.categories.Lighting.desktop,
    href: "/missing",
  },
];

const collectionCopy = (collection, field) => t(`outdoor.collections.${collection.id}.${field}`);
</script>

<template>
  <section class="outdoor-landing-page">
    <section class="outdoor-landing-hero" aria-labelledby="outdoor-landing-title">
      <picture>
        <source media="(max-width: 760px)" :srcset="heroImage.mobile" />
        <img :src="heroImage.desktop" alt="Outdoor living terrace with layered neutral furniture" />
      </picture>
      <div class="landing-hero-copy outdoor-landing-copy">
        <BrandEyebrow :suffix="t('outdoor.hero.eyebrow')" />
        <h1 id="outdoor-landing-title">{{ t("outdoor.hero.title") }}</h1>
        <p>{{ t("outdoor.hero.description") }}</p>
        <div class="landing-actions">
          <a href="/sofas-plp">{{ t("outdoor.hero.shopCta") }}</a>
          <a href="/membership">{{ t("landing.common.joinMembers") }}</a>
        </div>
      </div>
    </section>

    <section class="landing-edit-row outdoor-edit-row" aria-label="Outdoor room edits">
      <a v-for="edit in edits" :key="edit" href="/sofas-plp">{{ t(`outdoor.edits.${edit}`) }}</a>
    </section>

    <section class="landing-feature-grid outdoor-feature-grid" aria-label="Outdoor collections">
      <a v-for="collection in collections" :key="collection.id" :href="collection.href" class="landing-feature-card">
        <img :src="collection.image" :alt="`${collection.altLabel} outdoor collection`" />
        <span class="eyebrow">{{ t("landing.common.collection") }}</span>
        <h2>{{ collectionCopy(collection, "title") }}</h2>
        <p>{{ collectionCopy(collection, "description") }}</p>
      </a>
    </section>

    <section class="landing-service-band outdoor-service-band" aria-label="Outdoor design services">
      <div>
        <p class="eyebrow">{{ t("landing.common.designServices") }}</p>
        <h2>{{ t("outdoor.services.heading") }}</h2>
      </div>
      <ul>
        <li>{{ t("outdoor.services.title") }}</li>
        <li>{{ t("outdoor.services.materialGuidance") }}</li>
        <li>{{ t("outdoor.services.memberSavings") }}</li>
      </ul>
      <a href="/membership">{{ t("landing.common.exploreServices") }}</a>
    </section>
  </section>
</template>
