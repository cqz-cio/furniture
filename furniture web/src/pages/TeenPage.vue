<script setup>
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const heroImage = generatedFurnitureAssets.teen.hero;

const collections = [
  {
    id: "bedroom",
    altLabel: "Bedroom",
    image: generatedFurnitureAssets.teen.collections.bedroom,
    href: "/sofas-plp",
  },
  {
    id: "lounge",
    altLabel: "Lounge",
    image: generatedFurnitureAssets.teen.collections.lounge,
    href: "/sofas-plp",
  },
  {
    id: "study",
    altLabel: "Study",
    image: generatedFurnitureAssets.teen.collections.study,
    href: "/sofas-plp",
  },
  {
    id: "lighting",
    altLabel: "Lighting",
    image: generatedFurnitureAssets.teen.collections.lighting,
    href: "/sofas-plp",
  },
];

const edits = ["sleep", "study", "lounge", "storage"];

const collectionCopy = (collection, field) => t(`teen.collections.${collection.id}.${field}`);
</script>

<template>
  <section class="teen-landing-page">
    <section class="teen-landing-hero" aria-labelledby="teen-landing-title">
      <picture>
        <source media="(max-width: 760px)" :srcset="heroImage.mobile" />
        <img :src="heroImage.desktop" alt="Modern teen lounge with deep seating and layered neutral furniture" />
      </picture>
      <div class="landing-hero-copy teen-landing-copy">
        <BrandEyebrow :suffix="t('teen.hero.eyebrow')" />
        <h1 id="teen-landing-title">{{ t("teen.hero.title") }}</h1>
        <p>{{ t("teen.hero.description") }}</p>
        <div class="landing-actions">
          <a href="/sofas-plp">{{ t("teen.hero.shopCta") }}</a>
          <a href="/membership">{{ t("landing.common.joinMembers") }}</a>
        </div>
      </div>
    </section>

    <section class="landing-edit-row" aria-label="Teen room edits">
      <a v-for="edit in edits" :key="edit" href="/sofas-plp">{{ t(`teen.edits.${edit}`) }}</a>
    </section>

    <section class="landing-feature-grid teen-feature-grid" aria-label="Teen collections">
      <a v-for="collection in collections" :key="collection.id" :href="collection.href" class="landing-feature-card">
        <img :src="collection.image" :alt="`${collection.altLabel} furniture collection`" />
        <span class="eyebrow">{{ t("landing.common.collection") }}</span>
        <h2>{{ collectionCopy(collection, "title") }}</h2>
        <p>{{ collectionCopy(collection, "description") }}</p>
      </a>
    </section>

    <section class="landing-service-band teen-service-band" aria-label="Teen room design services">
      <div>
        <p class="eyebrow">{{ t("landing.common.designServices") }}</p>
        <h2>{{ t("teen.services.title") }}</h2>
      </div>
      <a href="/membership">{{ t("teen.services.cta") }}</a>
    </section>
  </section>
</template>
