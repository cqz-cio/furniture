<script setup>
import SpecGroup from "../components/SpecGroup.vue";
import { outdoorCollectionSpecs } from "../data/rhLayout.js";

const groups = [
  { key: "banner", title: "系列横幅大图区域", desktop: outdoorCollectionSpecs.desktop.banner, mobile: outdoorCollectionSpecs.mobile.banner },
  { key: "product", title: "子分类商品图区域", desktop: outdoorCollectionSpecs.desktop.product, mobile: outdoorCollectionSpecs.mobile.product },
  { key: "swatch", title: "材质 / 颜色小样区域", desktop: outdoorCollectionSpecs.desktop.swatch, mobile: outdoorCollectionSpecs.mobile.swatch },
];
</script>

<template>
  <SpecGroup title="Outdoor 分类页面" :desktop="outdoorCollectionSpecs.desktop" :mobile="outdoorCollectionSpecs.mobile" :groups="groups" />
</template>
