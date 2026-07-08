<script setup>
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const heroSlides = [
  {
    desktop: generatedFurnitureAssets.home.hero.desktop,
    mobile: generatedFurnitureAssets.home.hero.mobile,
    alt: "Oakved living room with layered neutral furniture and architectural lighting",
  },
  {
    desktop: generatedFurnitureAssets.home.modules["003"].desktop,
    mobile: generatedFurnitureAssets.home.modules["003"].mobile,
    alt: "Oakved dining room with stone, wood and warm lighting",
  },
  {
    desktop: generatedFurnitureAssets.home.modules["004"].desktop,
    mobile: generatedFurnitureAssets.home.modules["004"].mobile,
    alt: "Oakved outdoor room with teak and performance cushions",
  },
  {
    desktop: generatedFurnitureAssets.home.modules["002"].desktop,
    mobile: generatedFurnitureAssets.home.modules["002"].mobile,
    alt: "Oakved bedroom with calm upholstery and wood finishes",
  },
];

const editorialModules = [
  {
    id: "bedroomFurniture",
    href: "/products?room=bedroom",
    desktop: generatedFurnitureAssets.home.modules["002"].desktop,
    mobile: generatedFurnitureAssets.home.modules["002"].mobile,
    alt: "Oakved wood bedroom with bed bench, nightstand and soft neutral textiles",
  },
  {
    id: "storageCabinets",
    href: "/products?category=storage",
    desktop: generatedFurnitureAssets.home.modules["003"].desktop,
    mobile: generatedFurnitureAssets.home.modules["003"].mobile,
    alt: "Oakved storage and cabinet edit in a calm wood interior",
  },
  {
    id: "desksTables",
    href: "/products?category=desk-table",
    desktop: generatedFurnitureAssets.home.modules["004"].desktop,
    mobile: generatedFurnitureAssets.home.modules["004"].mobile,
    alt: "Oakved desk and table arrangement with warm lighting",
  },
  {
    id: "seatingBenches",
    href: "/products?category=seating",
    desktop: generatedFurnitureAssets.home.modules["005"].desktop,
    mobile: generatedFurnitureAssets.home.modules["005"].mobile,
    alt: "Oakved seating and bench arrangement for a complete bedroom",
  },
];

const homeModuleCopy = (item, field) => t(`home.editorial.${item.id}.${field}`);

const homeModuleEyebrowSuffix = (item) => homeModuleCopy(item, "eyebrow");
const generatedHomeModuleAsset = (index) => editorialModules[index] || editorialModules[0];

const categoryEdits = [
  {
    id: "bedroom",
    altLabel: "Bedroom",
    href: "/products",
    image: generatedFurnitureAssets.home.modules["002"].desktop,
    ambientImage: generatedFurnitureAssets.home.modules["003"].desktop,
  },
  {
    id: "storage",
    altLabel: "Storage",
    href: "/products?category=storage",
    image: generatedFurnitureAssets.home.modules["003"].desktop,
    ambientImage: generatedFurnitureAssets.home.modules["005"].desktop,
  },
  {
    id: "study",
    altLabel: "Study",
    href: "/products?category=desk-table",
    image: generatedFurnitureAssets.home.modules["004"].desktop,
    ambientImage: generatedFurnitureAssets.home.hero.desktop,
  },
];

const categoryEditCopy = (item, field) => t(`home.categoryEdits.${item.id}.${field}`);

const trustSignals = [
  { id: "memberPricing" },
  { id: "deliveryClarity" },
  { id: "materialLedChoices" },
];

const trustSignalCopy = (item, field) => t(`home.trust.${item.id}.${field}`);
</script>

<template>
  <section class="home-hero">
    <picture
      v-for="(slide, index) in heroSlides"
      :key="slide.desktop"
      class="home-hero-picture"
      :class="`home-hero-slide-${index + 1}`"
    >
      <source media="(max-width: 760px)" :srcset="slide.mobile" />
      <img class="home-hero-image" :src="slide.desktop" :alt="slide.alt" :loading="index === 0 ? 'eager' : 'lazy'" />
    </picture>
    <div class="home-hero-copy">
      <p class="eyebrow">{{ t("home.heroEyebrow") }}</p>
      <h1 class="sr-only">Oakved</h1>
      <img class="home-hero-logo" src="/assets/brand/oakved-logo-white.png" alt="Oakved" />
      <p>{{ t("home.heroSubtitle") }}</p>
    </div>
  </section>

  <section class="home-grid" :aria-label="t('home.gridAria')">
    <a v-for="(item, index) in editorialModules" :key="item.id" :href="item.href" class="home-entry">
      <picture class="home-entry-picture">
         <source media="(max-width: 760px)" :srcset="generatedHomeModuleAsset(index).mobile" />
         <img class="home-entry-image" :src="generatedHomeModuleAsset(index).desktop" :alt="item.alt" />
         <span class="home-entry-shade" aria-hidden="true"></span>
       </picture>
       <div class="home-entry-copy">
         <BrandEyebrow :suffix="homeModuleEyebrowSuffix(item)" />
         <h2>{{ homeModuleCopy(item, "title") }}</h2>
         <p>{{ homeModuleCopy(item, "subtitle") }}</p>
         <span>{{ homeModuleCopy(item, "cta") }}</span>
       </div>
     </a>
  </section>

  <section class="home-commerce-section" aria-label="Shop by room">
    <header class="home-commerce-head">
      <p class="eyebrow">{{ t("home.commerce.eyebrow") }}</p>
      <h2>{{ t("home.commerce.title") }}</h2>
      <p>{{ t("home.commerce.description") }}</p>
    </header>
    <div class="home-category-edit">
      <a v-for="item in categoryEdits" :key="item.id" :href="item.href">
        <figure class="home-category-image-stack">
          <img :src="item.image" :alt="`${item.altLabel} furniture edit`" loading="lazy" />
          <img :src="item.ambientImage" :alt="`${item.altLabel} room inspiration`" loading="lazy" />
        </figure>
        <span>
          <strong>{{ categoryEditCopy(item, "title") }}</strong>
          <small>{{ categoryEditCopy(item, "copy") }}</small>
        </span>
      </a>
    </div>
  </section>

  <section class="home-featured-collection" aria-label="Featured collection">
    <img
      :src="generatedFurnitureAssets.home.modules['005'].desktop"
      alt="Layered Oakved interior with upholstery, wood and lighting"
      loading="lazy"
    />
    <div>
      <p class="eyebrow">{{ t("home.featured.eyebrow") }}</p>
      <h2>{{ t("home.featured.title") }}</h2>
      <p>{{ t("home.featured.description") }}</p>
      <a href="/products?room=bedroom">{{ t("home.featured.cta") }}</a>
    </div>
  </section>

  <section class="home-trust-strip" aria-label="Shopping confidence">
    <article v-for="item in trustSignals" :key="item.id">
      <h2>{{ trustSignalCopy(item, "title") }}</h2>
      <p>{{ trustSignalCopy(item, "copy") }}</p>
    </article>
  </section>
</template>
