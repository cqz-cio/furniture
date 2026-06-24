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
    eyebrow: "Bedroom furniture",
    title: "Build the bedroom around warm wood and quiet proportion.",
    copy: "Start with a nightstand, dresser and bench, then layer upholstery and lighting around the same material language.",
    cta: "Shop bedroom furniture",
    href: "/products?room=bedroom",
    desktop: generatedFurnitureAssets.home.modules["002"].desktop,
    mobile: generatedFurnitureAssets.home.modules["002"].mobile,
    alt: "Oakved wood bedroom with bed bench, nightstand and soft neutral textiles",
  },
  {
    eyebrow: "Storage cabinets",
    title: "Storage that reads as furniture, not utility.",
    copy: "Carved dressers, bedside drawers and cabinets keep the room composed while giving daily pieces a clear place.",
    cta: "Explore storage",
    href: "/products?category=storage",
    desktop: generatedFurnitureAssets.home.modules["003"].desktop,
    mobile: generatedFurnitureAssets.home.modules["003"].mobile,
    alt: "Oakved storage and cabinet edit in a calm wood interior",
  },
  {
    eyebrow: "Desks & tables",
    title: "Small work zones with the same finished-room feeling.",
    copy: "Pair writing desks, vanity tables and round tables with chairs that feel residential rather than office-like.",
    cta: "Shop desks and tables",
    href: "/products?category=desk-table",
    desktop: generatedFurnitureAssets.home.modules["004"].desktop,
    mobile: generatedFurnitureAssets.home.modules["004"].mobile,
    alt: "Oakved desk and table arrangement with warm lighting",
  },
  {
    eyebrow: "Seating & benches",
    title: "Complete the room with lounge seating and end-of-bed pieces.",
    copy: "Single sofas, bedroom side chairs and bed benches add the final layer of comfort without crowding the room.",
    cta: "Shop seating",
    href: "/products?category=seating",
    desktop: generatedFurnitureAssets.home.modules["005"].desktop,
    mobile: generatedFurnitureAssets.home.modules["005"].mobile,
    alt: "Oakved seating and bench arrangement for a complete bedroom",
  },
];

const homeModuleCopy = (index, field) => {
  const item = editorialModules[index] || editorialModules[0];
  const map = {
    eyebrow: item.eyebrow,
    title: item.title,
    subtitle: item.copy,
    cta: item.cta,
  };
  return map[field] || "";
};

const homeModuleEyebrowSuffix = (index) => homeModuleCopy(index, "eyebrow");
const generatedHomeModuleAsset = (index) => editorialModules[index] || editorialModules[0];

const categoryEdits = [
  {
    title: "Bedroom",
    copy: "Nightstands, dressers and benches for a complete wood bedroom.",
    href: "/products",
    image: generatedFurnitureAssets.home.modules["002"].desktop,
    ambientImage: generatedFurnitureAssets.home.modules["003"].desktop,
  },
  {
    title: "Storage",
    copy: "Carved dressers, cabinets and bedside storage in warm finishes.",
    href: "/products?category=storage",
    image: generatedFurnitureAssets.home.modules["003"].desktop,
    ambientImage: generatedFurnitureAssets.home.modules["005"].desktop,
  },
  {
    title: "Study",
    copy: "Desks, vanity tables and chairs for quiet bedroom work zones.",
    href: "/products?category=desk-table",
    image: generatedFurnitureAssets.home.modules["004"].desktop,
    ambientImage: generatedFurnitureAssets.home.hero.desktop,
  },
];

const trustSignals = [
  { title: "Member pricing", copy: "Clear member and regular pricing before checkout." },
  { title: "Delivery clarity", copy: "Ready-to-ship and special-order windows shown early." },
  { title: "Material-led choices", copy: "Fabric, stone, wood and metal options stay visual." },
];
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
    <a v-for="(item, index) in editorialModules" :key="item.title" :href="item.href" class="home-entry">
      <picture class="home-entry-picture">
         <source media="(max-width: 760px)" :srcset="generatedHomeModuleAsset(index).mobile" />
         <img class="home-entry-image" :src="generatedHomeModuleAsset(index).desktop" :alt="item.alt" />
         <span class="home-entry-shade" aria-hidden="true"></span>
       </picture>
       <div class="home-entry-copy">
         <BrandEyebrow :suffix="homeModuleEyebrowSuffix(index)" />
         <h2>{{ homeModuleCopy(index, "title") }}</h2>
         <p>{{ homeModuleCopy(index, "subtitle") }}</p>
         <span>{{ homeModuleCopy(index, "cta") }}</span>
       </div>
     </a>
  </section>

  <section class="home-commerce-section" aria-label="Shop by room">
    <header class="home-commerce-head">
      <p class="eyebrow">Shop the edit</p>
      <h2>Rooms built around proportion, material and calm.</h2>
      <p>Start with the room, then refine by fabric, finish, delivery window and member pricing.</p>
    </header>
    <div class="home-category-edit">
      <a v-for="item in categoryEdits" :key="item.title" :href="item.href">
        <figure class="home-category-image-stack">
          <img :src="item.image" :alt="`${item.title} furniture edit`" loading="lazy" />
          <img :src="item.ambientImage" :alt="`${item.title} room inspiration`" loading="lazy" />
        </figure>
        <span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.copy }}</small>
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
      <p class="eyebrow">Featured collection</p>
      <h2>A bedroom collection built from warm wood, storage and quiet proportion.</h2>
      <p>
        Explore nightstands, dressers, benches, desks and lounge chairs before moving into checkout.
      </p>
      <a href="/products?room=bedroom">Shop bedroom furniture</a>
    </div>
  </section>

  <section class="home-trust-strip" aria-label="Shopping confidence">
    <article v-for="item in trustSignals" :key="item.title">
      <h2>{{ item.title }}</h2>
      <p>{{ item.copy }}</p>
    </article>
  </section>
</template>
