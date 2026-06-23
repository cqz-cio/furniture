<script setup>
import BrandEyebrow from "../components/BrandEyebrow.vue";
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";

const heroImage = generatedFurnitureAssets.outdoor.hero;

const edits = ["Lounge", "Dining", "Poolside", "Lighting"];

const collections = [
  {
    id: "lounge",
    title: "Outdoor Lounge",
    description: "Deep seating, low tables, and weather-ready silhouettes for open-air rooms.",
    image: generatedFurnitureAssets.sale.categories.Outdoor.desktop,
    href: "/sofas-plp",
  },
  {
    id: "dining",
    title: "Dining",
    description: "Tables and chairs scaled for long meals, easy hosting, and everyday use outside.",
    image: generatedFurnitureAssets.sale.categories.Dining.desktop,
    href: "/missing",
  },
  {
    id: "shade",
    title: "Shade & Texture",
    description: "Layered materials, woven accents, and soft neutrals that bring depth to the patio.",
    image: generatedFurnitureAssets.sale.categories.Rugs.desktop,
    href: "/missing",
  },
  {
    id: "lighting",
    title: "Lighting",
    description: "Pendants and ambient pieces that keep outdoor spaces warm after sunset.",
    image: generatedFurnitureAssets.sale.categories.Lighting.desktop,
    href: "/missing",
  },
];
</script>

<template>
  <section class="outdoor-landing-page">
    <section class="outdoor-landing-hero" aria-labelledby="outdoor-landing-title">
      <picture>
        <source media="(max-width: 760px)" :srcset="heroImage.mobile" />
        <img :src="heroImage.desktop" alt="Outdoor living terrace with layered neutral furniture" />
      </picture>
      <div class="landing-hero-copy outdoor-landing-copy">
        <BrandEyebrow suffix="Outdoor" />
        <h1 id="outdoor-landing-title">Open-air rooms, fully composed.</h1>
        <p>
          Furniture, lighting, and materials for terraces, gardens, pool decks, and dining under the sky.
        </p>
        <div class="landing-actions">
          <a href="/sofas-plp">Shop Outdoor</a>
          <a href="/membership">Join RH Members</a>
        </div>
      </div>
    </section>

    <section class="landing-edit-row outdoor-edit-row" aria-label="Outdoor room edits">
      <a v-for="edit in edits" :key="edit" href="/sofas-plp">{{ edit }}</a>
    </section>

    <section class="landing-feature-grid outdoor-feature-grid" aria-label="Outdoor collections">
      <a v-for="collection in collections" :key="collection.id" :href="collection.href" class="landing-feature-card">
        <img :src="collection.image" :alt="`${collection.title} outdoor collection`" />
        <span class="eyebrow">Collection</span>
        <h2>{{ collection.title }}</h2>
        <p>{{ collection.description }}</p>
      </a>
    </section>

    <section class="landing-service-band outdoor-service-band" aria-label="Outdoor design services">
      <div>
        <p class="eyebrow">Design Services</p>
        <h2>Plan an exterior room with the same care as the interior.</h2>
      </div>
      <ul>
        <li>Outdoor furniture planning</li>
        <li>Material and finish guidance</li>
        <li>Member savings</li>
      </ul>
      <a href="/membership">Explore services</a>
    </section>
  </section>
</template>
