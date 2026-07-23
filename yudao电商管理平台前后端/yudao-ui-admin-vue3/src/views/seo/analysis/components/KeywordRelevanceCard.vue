<template>
  <el-card class="keyword-card" shadow="never">
    <div class="keyword-header">
      <div class="keyword-identity">
        <div class="keyword-title">
          <el-tag v-if="keyword.keywordType === 'FOCUS'" type="primary" effect="plain">焦点关键词</el-tag>
          <el-tag v-else effect="plain">关联关键词</el-tag>
          <strong>{{ keyword.keyword }}</strong>
        </div>
        <div class="keyword-summary">{{ summaryReason }}</div>
      </div>
      <div class="keyword-score">
        <template v-if="typeof keyword.relevancePercent === 'number'">
          <strong>{{ keyword.relevancePercent }}%</strong>
          <span>{{ gradeLabel(keyword.grade) }}</span>
        </template>
        <template v-else>
          <strong>未完成</strong>
          <span>{{ statusLabel(keyword.analysisStatus) }}</span>
        </template>
      </div>
    </div>

    <el-progress
      v-if="typeof keyword.relevancePercent === 'number'"
      class="keyword-progress"
      :percentage="keyword.relevancePercent"
      :stroke-width="12"
      :show-text="false"
      color="var(--el-color-primary)"
    />
    <div v-else class="keyword-unavailable-bar" />

    <div class="keyword-meta">
      <span>精确匹配 {{ keyword.exactMatchCount }} 次</span>
      <span>变体匹配 {{ keyword.variantMatchCount }} 次</span>
      <span>置信度 {{ keyword.confidencePercent }}%</span>
      <span v-if="keyword.analysisStatus === 'PARTIAL'">部分完成：未接入的维度不计入总分</span>
      <span v-if="keyword.matchedLocations?.length">
        已覆盖 {{ keyword.matchedLocations.join('、') }}
      </span>
    </div>

    <div class="detail-trigger">
      <el-button type="primary" link @click="toggleDetails">
        {{ detailsVisible ? '收起评分依据' : `查看评分依据与建议（${keyword.suggestionCount || 0}）` }}
        <Icon :icon="detailsVisible ? 'ep:arrow-up' : 'ep:arrow-down'" class="ml-4px" />
      </el-button>
    </div>

    <el-collapse-transition>
      <div v-show="detailsVisible" class="keyword-details" v-loading="loading">
        <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
          <template #default>
            <el-button type="primary" link @click="loadDetails">重新加载</el-button>
          </template>
        </el-alert>
        <template v-else>
          <h4>五维评分</h4>
          <KeywordDimensionBreakdown :keyword="resolvedKeyword" />
          <el-divider />
          <h4>逐项判断、证据与修改建议</h4>
          <KeywordSuggestionList :rules="resolvedKeyword.items" />
        </template>
      </div>
    </el-collapse-transition>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  getSeoKeywordAnalysis,
  type SeoKeywordAnalysisRespVO,
  type SeoKeywordGrade
} from '@/api/seo/analysis'
import KeywordDimensionBreakdown from './KeywordDimensionBreakdown.vue'
import KeywordSuggestionList from './KeywordSuggestionList.vue'

const props = defineProps<{
  analysisId: number
  keyword: SeoKeywordAnalysisRespVO
}>()

const detailsVisible = ref(props.keyword.keywordType === 'FOCUS')
const loading = ref(false)
const loadError = ref('')
const detail = ref<SeoKeywordAnalysisRespVO>()
const resolvedKeyword = computed(() => detail.value || props.keyword)

const gradeLabels: Record<SeoKeywordGrade, string> = {
  HIGH: '高度相关',
  MEDIUM: '基本相关',
  WEAK: '关联较弱',
  LOW: '关联度低'
}

const gradeLabel = (grade?: SeoKeywordGrade | null) => (grade ? gradeLabels[grade] : '待判断')
const statusLabel = (status: string) => status === 'FAILED' ? '分析失败' : '分析未完成'

const summaryReason = computed(() => {
  if (props.keyword.analysisStatus === 'FAILED') {
    return '该关键词本次未能完成分析，其他关键词结果不受影响，可展开查看失败原因。'
  }
  const locations = props.keyword.matchedLocations?.length
    ? `已在${props.keyword.matchedLocations.join('、')}发现匹配`
    : '关键位置尚未发现有效匹配'
  return `${locations}；共检测到 ${props.keyword.exactMatchCount} 次精确匹配和 ${props.keyword.variantMatchCount} 次变体匹配。`
})

const loadDetails = async () => {
  if (detail.value || loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await getSeoKeywordAnalysis(props.analysisId, props.keyword.id)
  } catch {
    loadError.value = '关键词评分依据加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const toggleDetails = async () => {
  detailsVisible.value = !detailsVisible.value
  if (detailsVisible.value) await loadDetails()
}

onMounted(() => {
  if (detailsVisible.value) loadDetails()
})
</script>

<style scoped>
.keyword-card + .keyword-card {
  margin-top: 16px;
}

.keyword-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.keyword-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
}

.keyword-summary {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.keyword-score {
  display: flex;
  min-width: 92px;
  flex-direction: column;
  align-items: flex-end;
}

.keyword-score strong {
  color: var(--el-color-primary);
  font-size: 28px;
  line-height: 1;
}

.keyword-score span {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.keyword-progress,
.keyword-unavailable-bar {
  margin-top: 18px;
}

.keyword-unavailable-bar {
  height: 12px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
}

.keyword-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-trigger {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.keyword-details {
  min-height: 70px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.keyword-details h4 {
  margin: 0 0 14px;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

@media (max-width: 700px) {
  .keyword-header {
    flex-direction: column;
  }

  .keyword-score {
    align-items: flex-start;
  }
}
</style>
