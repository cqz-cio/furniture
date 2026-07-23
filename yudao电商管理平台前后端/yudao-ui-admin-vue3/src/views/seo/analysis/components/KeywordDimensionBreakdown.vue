<template>
  <div class="dimension-list">
    <div v-for="dimension in dimensions" :key="dimension.key" class="dimension-row">
      <div class="dimension-heading">
        <span>{{ dimension.label }}</span>
        <span v-if="typeof dimension.value === 'number'" class="dimension-value">
          {{ dimension.value }}%
        </span>
        <span v-else class="dimension-unavailable">未完成</span>
      </div>
      <el-progress
        v-if="typeof dimension.value === 'number'"
        :percentage="dimension.value"
        :stroke-width="8"
        :show-text="false"
        color="var(--el-color-primary)"
      />
      <div v-else class="dimension-empty-bar" aria-label="该维度未完成" />
      <div class="dimension-help">{{ dimension.help }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SeoKeywordAnalysisRespVO } from '@/api/seo/analysis'

const props = defineProps<{ keyword: SeoKeywordAnalysisRespVO }>()

const normalizedPercent = (value?: number | null) =>
  typeof value === 'number' ? Math.max(0, Math.min(100, value)) : undefined

const dimensions = computed(() => [
  {
    key: 'position',
    label: '关键位置覆盖',
    value: normalizedPercent(props.keyword.keyPositionPercent),
    help: '标题、H1、摘要、Meta 描述与 URL 等重要位置是否自然出现关键词'
  },
  {
    key: 'lexical',
    label: '词面匹配',
    value: normalizedPercent(props.keyword.lexicalMatchPercent),
    help: '精确词、规范化词与行业词典变体的实际匹配情况'
  },
  {
    key: 'semantic',
    label: '语义关联',
    value: normalizedPercent(props.keyword.semanticPercent),
    help: typeof props.keyword.semanticPercent !== 'number'
      ? '语义模型尚未接入，本维度不计入当前总分，也不会被当作 0 分'
      : '内容含义与关键词搜索意图的语义接近程度'
  },
  {
    key: 'distribution',
    label: '分布与自然度',
    value: normalizedPercent(props.keyword.distributionPercent),
    help: '关键词在正文中的分布、密度与自然表达情况'
  },
  {
    key: 'intent',
    label: '搜索意图覆盖',
    value: normalizedPercent(props.keyword.intentCoveragePercent),
    help: '商品属性、材质、风格、场景等电商搜索意图是否被内容覆盖'
  }
])
</script>

<style scoped>
.dimension-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 28px;
}

.dimension-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.dimension-value {
  color: var(--el-color-primary);
}

.dimension-unavailable {
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.dimension-empty-bar {
  height: 8px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
}

.dimension-help {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .dimension-list {
    grid-template-columns: 1fr;
  }
}
</style>
