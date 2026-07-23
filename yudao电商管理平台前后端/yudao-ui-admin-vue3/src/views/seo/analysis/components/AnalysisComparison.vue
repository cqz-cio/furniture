<template>
  <div class="comparison">
    <el-alert
      :title="`分析 #${data.previousAnalysisId} 与 #${data.currentAnalysisId} 的逐关键词变化`"
      type="info"
      :closable="false"
      show-icon
      class="mb-16px"
    />
    <el-table :data="data.keywords" border>
      <el-table-column label="关键词" min-width="160">
        <template #default="scope">
          <el-tag effect="plain" size="small">
            {{ scope.row.keywordType === 'FOCUS' ? '焦点' : '关联' }}
          </el-tag>
          <span class="ml-8px">{{ scope.row.keyword }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上次" width="90" align="center">
        <template #default="scope">{{ percentLabel(scope.row.previousPercent) }}</template>
      </el-table-column>
      <el-table-column label="本次" width="90" align="center">
        <template #default="scope">{{ percentLabel(scope.row.currentPercent) }}</template>
      </el-table-column>
      <el-table-column label="变化" width="110" align="center">
        <template #default="scope">
          <strong class="delta">{{ deltaLabel(scope.row.deltaPercent, scope.row.changeType) }}</strong>
        </template>
      </el-table-column>
      <el-table-column label="规则变化" min-width="260">
        <template #default="scope">
          <div v-if="scope.row.resolvedRuleCodes?.length">
            已解决：{{ scope.row.resolvedRuleCodes.join('、') }}
          </div>
          <div v-if="scope.row.newRuleCodes?.length">
            新增问题：{{ scope.row.newRuleCodes.join('、') }}
          </div>
          <span v-if="!scope.row.resolvedRuleCodes?.length && !scope.row.newRuleCodes?.length">无规则变化</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { SeoAnalysisCompareRespVO } from '@/api/seo/analysis'

defineProps<{ data: SeoAnalysisCompareRespVO }>()

const percentLabel = (value?: number | null) => typeof value === 'number' ? `${value}%` : '未完成'
const deltaLabel = (delta: number | null | undefined, changeType: string) => {
  if (changeType === 'ADDED') return '新增关键词'
  if (changeType === 'REMOVED') return '已移除'
  if (typeof delta !== 'number') return '无法比较'
  return `${delta > 0 ? '+' : ''}${delta}%`
}
</script>

<style scoped>
.delta {
  color: var(--el-color-primary);
}
</style>
