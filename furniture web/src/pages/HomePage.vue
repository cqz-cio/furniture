<script setup>
import ImageSpecPlaceholder from "../components/ImageSpecPlaceholder.vue";
import { homeFullPageModules, homeHeroAssets } from "../data/rhLayout.js";
import { useI18n } from "../i18n.js";

const { t } = useI18n();

const specHeight = (rendered) => {
  const match = rendered.match(/x\s*([0-9.]+)/);
  return match ? `${match[1]}px` : undefined;
};
</script>

<template>
  <section class="home-hero">
    <div class="home-hero-media">
      <ImageSpecPlaceholder
        class="desktop-home-image"
        :label="homeHeroAssets.desktop.label"
        :rendered="homeHeroAssets.desktop.rendered"
        recommended2x="2700 x 1816"
        file-size="WebP 320-620KB"
        :fit="homeHeroAssets.desktop.fit"
        ratio="1.49:1"
        :natural="homeHeroAssets.desktop.natural"
        tone="dark"
      />
      <ImageSpecPlaceholder
        class="mobile-home-image"
        :label="homeHeroAssets.mobile.label"
        :rendered="homeHeroAssets.mobile.rendered"
        recommended2x="780 x 1200"
        file-size="WebP 220-420KB"
        :fit="homeHeroAssets.mobile.fit"
        ratio="0.65:1"
        :natural="homeHeroAssets.mobile.natural"
        tone="dark"
      />
    </div>
    <div class="home-hero-copy">
      <p class="eyebrow">{{ t("home.heroEyebrow") }}</p>
      <h1>RH</h1>
      <p>{{ t("home.heroSubtitle") }}</p>
    </div>
  </section>

  <section class="home-grid" :aria-label="t('home.gridAria')">
    <article
      v-for="item in homeFullPageModules"
      :key="item.title"
      class="home-entry"
      :class="{ 'is-screenshot-inferred': item.sourceLevel.includes('截图推断') }"
    >
      <div class="home-media-frame" :class="{ 'has-overlays': item.overlays }">
        <ImageSpecPlaceholder
          class="desktop-home-slot"
          :style="{ minHeight: specHeight(item.desktopRendered) }"
          :label="item.label"
          :rendered="item.desktopRendered"
          :recommended2x="item.recommended2x"
          :file-size="item.fileSize"
          :fit="item.fit"
          :ratio="item.ratio"
          :natural="item.desktopNatural"
        />
        <ImageSpecPlaceholder
          class="mobile-home-slot"
          :style="{ minHeight: specHeight(item.mobileRendered) }"
          :label="item.label"
          :rendered="item.mobileRendered"
          :recommended2x="item.mobileRecommended2x"
          :file-size="item.fileSize"
          :fit="item.fit"
          :ratio="item.ratio"
          :natural="item.mobileNatural"
        />
        <div v-if="item.overlays" class="sourcebook-overlay-slots" aria-label="Sourcebook layered cover slots">
          <div v-for="overlay in item.overlays" :key="overlay.label" class="sourcebook-cover-slot">
            <strong>{{ overlay.label }}</strong>
            <span>PC: {{ overlay.desktopRendered }}</span>
            <span>Mobile: {{ overlay.mobileRendered }}</span>
            <small>{{ overlay.sourceLevel }}</small>
          </div>
        </div>
      </div>
      <div>
        <h2>{{ item.title }}</h2>
        <small>{{ item.sourceLevel }}</small>
      </div>
    </article>
  </section>
</template>
