<script setup>
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";

const heroImage = generatedFurnitureAssets.products.sofa.gallery;

const collections = [
  {
    id: "bedroom",
    title: "Bedroom",
    description: "Tailored beds, layered textiles, and storage that keeps a personal room composed.",
    image: generatedFurnitureAssets.products.bed.cover,
    href: "/sofas-plp",
  },
  {
    id: "lounge",
    title: "Lounge",
    description: "Deep seating and relaxed tables for movie nights, gaming, and time with friends.",
    image: generatedFurnitureAssets.products.sofa.cover,
    href: "/sofas-plp",
  },
  {
    id: "study",
    title: "Study",
    description: "Desks, chairs, and lighting that make focus feel natural from afternoon to late night.",
    image: generatedFurnitureAssets.products.table.gallery,
    href: "/sofas-plp",
  },
  {
    id: "lighting",
    title: "Lighting",
    description: "Sculptural pendants and task lamps that give every zone a finished point of view.",
    image: generatedFurnitureAssets.products.pendant.gallery,
    href: "/sofas-plp",
  },
];

const edits = ["Sleep", "Study", "Lounge", "Storage"];
</script>

<template>
  <section class="teen-landing-page">
    <section class="teen-landing-hero" aria-labelledby="teen-landing-title">
      <img :src="heroImage" alt="Modern teen lounge with deep seating and layered neutral furniture" />
      <div class="landing-hero-copy teen-landing-copy">
        <p class="eyebrow">RH Teen</p>
        <h1 id="teen-landing-title">Personal rooms, fully considered.</h1>
        <p>
          Furniture, lighting, and storage for bedrooms, lounges, and study spaces with an elevated point of view.
        </p>
        <div class="landing-actions">
          <a href="/sofas-plp">Shop Teen</a>
          <a href="/membership">Join RH Members</a>
        </div>
      </div>
    </section>

    <section class="landing-edit-row" aria-label="Teen room edits">
      <a v-for="edit in edits" :key="edit" href="/sofas-plp">{{ edit }}</a>
    </section>

    <section class="landing-feature-grid teen-feature-grid" aria-label="Teen collections">
      <a v-for="collection in collections" :key="collection.id" :href="collection.href" class="landing-feature-card">
        <img :src="collection.image" :alt="`${collection.title} furniture collection`" />
        <span class="eyebrow">Collection</span>
        <h2>{{ collection.title }}</h2>
        <p>{{ collection.description }}</p>
      </a>
    </section>

    <section class="landing-service-band teen-service-band" aria-label="Teen room design services">
      <div>
        <p class="eyebrow">Design Services</p>
        <h2>Build a room around sleep, study, and everything after.</h2>
      </div>
      <a href="/membership">Start with RH Members</a>
    </section>
  </section>
</template>
