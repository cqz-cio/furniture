<script setup>
import { generatedFurnitureAssets } from "../data/generatedFurnitureAssets.js";
import { homeFullPageModules } from "../data/rhLayout.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const homeModuleHref = (title) => {
  if (title.includes("Outdoor")) return "/outdoor";
  if (title.includes("RH Members Program")) return "/membership";
  return "";
};

const homeModuleKeys = [
  "bedroom",
  "dining",
  "outdoorLiving",
  "sourcebooks",
  "milan",
  "sourcebooks",
  "interiors",
  "outdoorLiving",
  "interiors",
  "sourcebooks",
  "members",
  "founder",
  "architecture",
  "hospitality",
  "guesthouse",
  "aviation",
  "yachting",
  "services",
];

const homeModuleKey = (index) => homeModuleKeys[index] || "interiors";
const homeModuleCopy = (index, field) => t(`home.modules.${homeModuleKey(index)}.${field}`);

const generatedHomeModuleAssets = [
  generatedFurnitureAssets.home.modules["002"],
  generatedFurnitureAssets.home.modules["003"],
  generatedFurnitureAssets.home.modules["004"],
  generatedFurnitureAssets.home.modules["005"],
];

const generatedHomeModuleAsset = (index) => generatedHomeModuleAssets[index % generatedHomeModuleAssets.length];
</script>

<template>
  <section class="home-hero">
    <picture class="home-hero-picture">
      <source media="(max-width: 760px)" :srcset="generatedFurnitureAssets.home.hero.mobile" />
      <img
        class="home-hero-image"
        :src="generatedFurnitureAssets.home.hero.desktop"
        alt="RH interiors with layered neutral furniture and architectural lighting"
      />
    </picture>
    <div class="home-hero-copy">
      <p class="eyebrow">{{ t("home.heroEyebrow") }}</p>
      <h1>RH</h1>
      <p>{{ t("home.heroSubtitle") }}</p>
    </div>
  </section>

  <section class="home-grid" :aria-label="t('home.gridAria')">
    <component
      :is="homeModuleHref(item.title) ? 'a' : 'article'"
      v-for="(item, index) in homeFullPageModules"
      :key="item.title"
      :href="homeModuleHref(item.title) || undefined"
      class="home-entry"
    >
      <picture class="home-entry-picture">
        <source media="(max-width: 760px)" :srcset="generatedHomeModuleAsset(index).mobile" />
        <img
          class="home-entry-image"
          :src="generatedHomeModuleAsset(index).desktop"
          :alt="homeModuleCopy(index, 'title')"
        />
        <span class="home-entry-shade" aria-hidden="true"></span>
      </picture>
      <div class="home-entry-copy">
        <p class="eyebrow">{{ homeModuleCopy(index, "eyebrow") }}</p>
        <h2>{{ homeModuleCopy(index, "title") }}</h2>
        <p>{{ homeModuleCopy(index, "subtitle") }}</p>
        <span>{{ homeModuleCopy(index, "cta") }}</span>
      </div>
    </component>
  </section>
</template>
