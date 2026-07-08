<script setup>
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const heroImage = generatedFurnitureAssets.babyChild.hero;

const collections = [
  {
    id: "nursery",
    altLabel: "Nursery",
    image: generatedFurnitureAssets.babyChild.collections.nursery,
    href: "/sofas-plp",
  },
  {
    id: "playroom",
    altLabel: "Playroom",
    image: generatedFurnitureAssets.babyChild.collections.playroom,
    href: "/sofas-plp",
  },
  {
    id: "study",
    altLabel: "Study",
    image: generatedFurnitureAssets.babyChild.collections.study,
    href: "/sofas-plp",
  },
  {
    id: "lighting",
    altLabel: "Lighting",
    image: generatedFurnitureAssets.babyChild.collections.lighting,
    href: "/sofas-plp",
  },
];

const services = ["consultation", "registryGuidance", "memberSavings", "deliveryCoordination"];

const collectionCopy = (collection, field) => t(`babyChild.collections.${collection.id}.${field}`);
</script>

<template>
  <section class="child-landing-page">
    <section class="child-landing-hero" aria-labelledby="child-landing-title">
      <picture>
        <source media="(max-width: 760px)" :srcset="heroImage.mobile" />
        <img :src="heroImage.desktop" alt="Layered child bedroom with upholstered bed and warm neutral furnishings" />
      </picture>
      <div class="landing-hero-copy child-landing-copy">
        <BrandEyebrow :suffix="t('babyChild.hero.eyebrow')" />
        <h1 id="child-landing-title">{{ t("babyChild.hero.title") }}</h1>
        <p>{{ t("babyChild.hero.description") }}</p>
        <div class="landing-actions">
          <a href="/sofas-plp">{{ t("babyChild.hero.shopCta") }}</a>
          <a href="/membership">{{ t("landing.common.joinMembers") }}</a>
        </div>
      </div>
    </section>

    <section class="landing-feature-grid child-feature-grid" aria-label="Baby and child collections">
      <a v-for="collection in collections" :key="collection.id" :href="collection.href" class="landing-feature-card">
        <img :src="collection.image" :alt="`${collection.altLabel} furniture collection`" />
        <span class="eyebrow">{{ t("landing.common.collection") }}</span>
        <h2>{{ collectionCopy(collection, "title") }}</h2>
        <p>{{ collectionCopy(collection, "description") }}</p>
      </a>
    </section>

    <section class="landing-service-band child-service-band" aria-label="Baby and child services">
      <div>
        <BrandEyebrow :suffix="t('landing.common.designServices')" tone="dark" />
        <h2>{{ t("babyChild.services.title") }}</h2>
      </div>
      <ul>
        <li v-for="service in services" :key="service">{{ t(`babyChild.services.${service}`) }}</li>
      </ul>
      <a href="/membership">{{ t("babyChild.services.cta") }}</a>
    </section>
  </section>
</template>
