<script setup>
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";

const heroImage = generatedFurnitureAssets.babyChild.hero;

const collections = [
  {
    id: "nursery",
    title: "Nursery",
    description: "Soft upholstered beds, quiet storage, and layered lighting for the earliest routines.",
    image: generatedFurnitureAssets.babyChild.collections.nursery,
    href: "/sofas-plp",
  },
  {
    id: "playroom",
    title: "Playroom",
    description: "Durable silhouettes and warm wood tones arranged for reading, play, and reset.",
    image: generatedFurnitureAssets.babyChild.collections.playroom,
    href: "/sofas-plp",
  },
  {
    id: "study",
    title: "Study",
    description: "Scaled desks, shelves, and seating that make homework corners feel considered.",
    image: generatedFurnitureAssets.babyChild.collections.study,
    href: "/sofas-plp",
  },
  {
    id: "lighting",
    title: "Lighting",
    description: "A softer glow for bedtime stories, morning routines, and every little pause between.",
    image: generatedFurnitureAssets.babyChild.collections.lighting,
    href: "/sofas-plp",
  },
];

const services = [
  "Complimentary design consultation",
  "Registry guidance",
  "Member savings",
  "Delivery coordination",
];
</script>

<template>
  <section class="child-landing-page">
    <section class="child-landing-hero" aria-labelledby="child-landing-title">
      <picture>
        <source media="(max-width: 760px)" :srcset="heroImage.mobile" />
        <img :src="heroImage.desktop" alt="Layered child bedroom with upholstered bed and warm neutral furnishings" />
      </picture>
      <div class="landing-hero-copy child-landing-copy">
        <BrandEyebrow suffix="Baby &amp; Child" />
        <h1 id="child-landing-title">Rooms for first chapters.</h1>
        <p>
          Timeless nursery, bedroom, and playroom pieces designed to grow with the routines of family life.
        </p>
        <div class="landing-actions">
          <a href="/sofas-plp">Shop Baby &amp; Child</a>
          <a href="/membership">Join RH Members</a>
        </div>
      </div>
    </section>

    <section class="landing-feature-grid child-feature-grid" aria-label="Baby and child collections">
      <a v-for="collection in collections" :key="collection.id" :href="collection.href" class="landing-feature-card">
        <img :src="collection.image" :alt="`${collection.title} furniture collection`" />
        <span class="eyebrow">Collection</span>
        <h2>{{ collection.title }}</h2>
        <p>{{ collection.description }}</p>
      </a>
    </section>

    <section class="landing-service-band child-service-band" aria-label="Baby and child services">
      <div>
        <BrandEyebrow suffix="Design Services" tone="dark" />
        <h2>From nursery plans to growing bedrooms.</h2>
      </div>
      <ul>
        <li v-for="service in services" :key="service">{{ service }}</li>
      </ul>
      <a href="/membership">Explore services</a>
    </section>
  </section>
</template>
