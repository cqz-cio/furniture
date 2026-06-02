<script setup>
import { extractionBacklog } from "../data/rhLayout.js";
</script>

<template>
  <section class="missing-page">
    <p class="eyebrow">素材与页面方案待定</p>
    <h1>这些页面先保留开发占位</h1>
    <p>
      当前产品图、首页图和导航功能还没有最终确定，所以这里先不强制填充图片素材。
      页面可以继续作为结构占位使用，后续拿到素材或确认导航方案后再补真实内容。
    </p>
    <div class="backlog-list">
      <article v-for="item in extractionBacklog" :key="item.label">
        <strong>{{ item.label }}</strong>
        <span>{{ item.status }}</span>
        <p>{{ item.needed }}</p>
      </article>
    </div>
  </section>
</template>
