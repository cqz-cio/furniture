<script setup>
const props = defineProps({
  label: {
    type: String,
    required: true,
  },
  rendered: {
    type: String,
    required: true,
  },
  recommended2x: {
    type: String,
    required: true,
  },
  fileSize: {
    type: String,
    required: true,
  },
  fit: {
    type: String,
    default: "cover",
  },
  tone: {
    type: String,
    default: "light",
  },
  ratio: {
    type: String,
    default: "",
  },
  natural: {
    type: String,
    default: "",
  },
  mobileRendered: {
    type: String,
    default: "",
  },
  mobileNatural: {
    type: String,
    default: "",
  },
  mobileRecommended2x: {
    type: String,
    default: "",
  },
  src: {
    type: String,
    default: "",
  },
});

const fitLabelMap = {
  cover: "cover，铺满容器，允许裁切",
  contain: "contain，完整显示，可能留白",
  fill: "fill，按源站拉伸填满",
};
</script>

<template>
  <div class="image-spec" :class="`tone-${tone}`">
    <img v-if="src" :src="src" :alt="label" :style="{ objectFit: fit }" loading="lazy" decoding="async" />
    <div class="spec-copy">
      <span>{{ label }}</span>
      <strong>PC: {{ rendered }}</strong>
      <strong v-if="mobileRendered" class="mobile-spec-line">Mobile: {{ mobileRendered }}</strong>
      <small v-if="ratio">比例：{{ ratio }}</small>
      <small v-if="natural">PC 来源：{{ natural }}</small>
      <small v-if="mobileNatural">Mobile 来源：{{ mobileNatural }}</small>
      <small>PC 建议 2x：{{ recommended2x }}</small>
      <small v-if="mobileRecommended2x">Mobile 建议 2x：{{ mobileRecommended2x }}</small>
      <small>建议体积：{{ fileSize }}</small>
      <small>填充方式：{{ fitLabelMap[props.fit] || props.fit }}</small>
    </div>
  </div>
</template>
