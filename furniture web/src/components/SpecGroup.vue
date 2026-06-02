<script setup>
import ImageSpecPlaceholder from "./ImageSpecPlaceholder.vue";

defineProps({
  title: {
    type: String,
    required: true,
  },
  desktop: {
    type: Object,
    required: true,
  },
  mobile: {
    type: Object,
    required: true,
  },
  groups: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <section class="spec-page">
    <header class="spec-page-head">
      <p class="eyebrow">{{ title }}</p>
      <h1>图片 / 视频投放区域抽取</h1>
      <p>电脑端视口：{{ desktop.viewport }}，页面总高度：{{ desktop.documentHeight }}。手机端视口：{{ mobile.viewport }}，页面总高度：{{ mobile.documentHeight }}。</p>
    </header>

    <article v-for="group in groups" :key="group.key" class="spec-module" :class="`spec-module-${group.key}`">
      <div class="spec-module-meta">
        <p class="eyebrow">{{ group.title }}</p>
        <h2>电脑端 {{ group.desktop.count }} 个区域 / 手机端 {{ group.mobile.count }} 个区域</h2>
      </div>
      <ImageSpecPlaceholder
        class="desktop-spec-slot"
        :label="group.desktop.label"
        :rendered="group.desktop.rendered"
        :recommended2x="group.desktop.recommended2x"
        :file-size="group.desktop.fileSize"
        :fit="group.desktop.fit"
        :natural="group.desktop.natural"
      />
      <ImageSpecPlaceholder
        class="mobile-spec-slot"
        :label="group.mobile.label"
        :rendered="group.mobile.rendered"
        :recommended2x="group.mobile.recommended2x"
        :file-size="group.mobile.fileSize"
        :fit="group.mobile.fit"
        :natural="group.mobile.natural"
      />
    </article>
  </section>
</template>
