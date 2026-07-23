<template>
  <div class="suggestion-list">
    <div v-if="!items.length" class="empty-suggestion">当前没有可执行的优化建议</div>
    <div v-for="item in items" :key="item.id || item.ruleCode" class="suggestion-item">
      <div class="suggestion-title">
        <div>
          <el-tag effect="plain" size="small">{{ dimensionLabel(item.dimension) }}</el-tag>
          <span class="rule-code">{{ item.ruleCode }}</span>
        </div>
        <div class="score-recovery" v-if="typeof item.recoverableScore === 'number'">
          完成后预计可回收 {{ formatScore(item.recoverableScore) }} 分
        </div>
      </div>
      <div class="suggestion-block">
        <strong>判断原因：</strong>{{ item.reason || '规则未提供补充说明' }}
      </div>
      <div class="suggestion-block recommendation">
        <strong>修改建议：</strong>{{ item.recommendation || '当前规则无需修改' }}
      </div>
      <div class="evidence-line">
        <span v-if="item.contentLocation">检测位置：{{ item.contentLocation }}</span>
        <span v-if="item.evidence && Object.keys(item.evidence).length">
          证据：{{ formatEvidence(item.evidence) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SeoKeywordRuleRespVO } from '@/api/seo/analysis'

const props = defineProps<{ rules?: SeoKeywordRuleRespVO[] | null }>()

const items = computed(() => [...(props.rules || [])].sort((left, right) => left.sort - right.sort))

const labels: Record<string, string> = {
  POSITION: '关键位置',
  LEXICAL: '词面匹配',
  SEMANTIC: '语义关联',
  DISTRIBUTION: '分布自然度',
  INTENT: '搜索意图',
  SYSTEM: '系统'
}

const dimensionLabel = (dimension: string) => labels[dimension] || dimension
const formatScore = (score: number) => Number(score).toFixed(1).replace(/\.0$/, '')
const formatEvidence = (evidence: Record<string, unknown>) =>
  Object.entries(evidence)
    .map(([key, value]) => `${key}=${Array.isArray(value) ? value.join('、') : String(value)}`)
    .join('；')
</script>

<style scoped>
.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-item {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.suggestion-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.rule-code {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.score-recovery {
  color: var(--el-color-primary);
  font-size: 13px;
  white-space: nowrap;
}

.suggestion-block {
  margin-top: 7px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.65;
}

.recommendation {
  color: var(--el-text-color-primary);
}

.evidence-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 9px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.empty-suggestion {
  padding: 18px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
