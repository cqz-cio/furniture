<script setup>
import ImageSpecPlaceholder from "../components/ImageSpecPlaceholder.vue";
import { babyChildPageSpecs } from "../data/rhLayout.js";

const moduleByKey = Object.fromEntries(babyChildPageSpecs.desktop.modules.map((module) => [module.key, module]));
const groups = babyChildPageSpecs.groups;

const specOf = (key) => moduleByKey[key];
const groupClasses = (group) => [
  "baby-spec-module",
  `baby-spec-${group.key}`,
  ...group.layout.split(" ").map((layout) => `baby-layout-${layout}`),
];
</script>

<template>
  <section class="baby-spec-page">
    <header class="spec-page-head baby-spec-head">
      <p class="eyebrow">Baby &amp; Child 首页</p>
      <h1>图片 / 视频投放区域抽取</h1>
      <p>
        PC：{{ babyChildPageSpecs.desktop.viewport }}，页面总高度：{{ babyChildPageSpecs.desktop.documentHeight }}，
        {{ babyChildPageSpecs.desktop.summary }}。
      </p>
      <p>
        Mobile：{{ babyChildPageSpecs.mobile.viewport }}，页面总高度：{{ babyChildPageSpecs.mobile.documentHeight }}，
        {{ babyChildPageSpecs.mobile.summary }}。
      </p>
    </header>

    <article v-for="group in groups" :key="group.key" :class="groupClasses(group)">
      <ImageSpecPlaceholder
        v-if="group.layout === 'single'"
        v-bind="specOf(group.parts[0])"
        :tone="specOf(group.parts[0]).type === 'background' ? 'light' : 'dark'"
      />

      <div v-else-if="group.layout === 'sourcebook'" class="baby-sourcebook-layout">
        <ImageSpecPlaceholder class="baby-sourcebook-bg" v-bind="specOf(group.parts[0])" />
        <ImageSpecPlaceholder class="baby-sourcebook-cover" v-bind="specOf(group.parts[1])" />
        <ImageSpecPlaceholder class="baby-sourcebook-logo" v-bind="specOf(group.parts[2])" />
      </div>

      <div v-else-if="group.layout.includes('collection')" class="baby-collection-layout" :class="{ 'baby-wide-logo': group.layout.includes('wideLogo') }">
        <ImageSpecPlaceholder class="baby-collection-main" v-bind="specOf(group.parts[0])" tone="dark" />
        <ImageSpecPlaceholder class="baby-collection-logo" v-bind="specOf(group.parts[1])" />
      </div>

      <div v-else-if="group.layout === 'halfGrid'" class="baby-half-grid">
        <ImageSpecPlaceholder
          v-for="part in group.parts"
          :key="part"
          :class="`baby-grid-${part}`"
          v-bind="specOf(part)"
          :tone="specOf(part).type === 'background' ? 'light' : 'dark'"
        />
      </div>
    </article>
  </section>
</template>
