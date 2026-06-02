<script setup>
import ImageSpecPlaceholder from "../components/ImageSpecPlaceholder.vue";
import { teenPageSpecs } from "../data/rhLayout.js";

const groups = [
  { key: "hero", title: "首屏大图", spec: teenPageSpecs.desktop.hero },
  { key: "video", title: "视频封面", spec: teenPageSpecs.desktop.video },
  { key: "sourcebook", title: "Sourcebook 封面", spec: teenPageSpecs.desktop.sourcebook },
  { key: "collection", title: "系列主视觉大图", spec: teenPageSpecs.desktop.collection },
  { key: "collectionLogo", title: "系列名称图片", spec: teenPageSpecs.desktop.collectionLogo },
  { key: "promo", title: "额外横幅图", spec: teenPageSpecs.desktop.promo },
  { key: "rhid", title: "RHID 模块图", spec: teenPageSpecs.desktop.rhid },
  { key: "pinterest", title: "Pinterest 模块图", spec: teenPageSpecs.desktop.pinterest },
];
</script>

<template>
  <section class="teen-spec-page">
    <header class="spec-page-head teen-spec-head">
      <p class="eyebrow">Teen 首页</p>
      <h1>图片 / 视频投放区域抽取</h1>
      <p>电脑端视口：{{ teenPageSpecs.desktop.viewport }}，页面总高度：{{ teenPageSpecs.desktop.documentHeight }}。当前只收到 Teen 电脑端 JSON，手机端待补充。</p>
    </header>

    <article v-for="group in groups" :key="group.key" class="teen-spec-module" :class="`teen-spec-${group.key}`">
      <div class="spec-module-meta">
        <p class="eyebrow">{{ group.title }}</p>
        <h2>电脑端 {{ group.spec.count }} 个区域</h2>
      </div>
      <ImageSpecPlaceholder
        :label="group.spec.label"
        :rendered="group.spec.rendered"
        :recommended2x="group.spec.recommended2x"
        :file-size="group.spec.fileSize"
        :fit="group.spec.fit"
        :natural="group.spec.natural"
      />
    </article>
  </section>
</template>
