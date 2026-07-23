<template>
  <div v-loading="loading" class="seo-analysis-page">
    <ContentWrap v-if="!analysis">
      <div class="analysis-empty">
        <div class="empty-icon"><Icon icon="ep:data-analysis" /></div>
        <h2>逐关键词关联度分析</h2>
        <p>针对焦点关键词和每个关联关键词，分别给出 0–100% 关联度、评分依据和可执行修改建议。</p>
        <div class="workflow">
          <span>1. 选择商品或录入内容</span>
          <Icon icon="ep:right" />
          <span>2. 逐关键词分析</span>
          <Icon icon="ep:right" />
          <span>3. 按建议修改</span>
          <Icon icon="ep:right" />
          <span>4. 重新分析并对比</span>
        </div>
        <el-button type="primary" v-hasPermi="['seo:analysis:run']" @click="openManualDialog">
          <Icon icon="ep:plus" class="mr-5px" />手工录入内容并分析
        </el-button>
        <p class="empty-tip">也可以在“内容优化”列表中点击“分析”，直接读取最新商品与 SEO 元数据。</p>
      </div>
    </ContentWrap>

    <template v-else>
      <ContentWrap>
        <div class="analysis-header">
          <div>
            <div class="header-title">
              <h2>{{ analysis.focusKeyphrase }}</h2>
              <el-tag effect="plain">焦点关键词</el-tag>
              <el-tag v-if="analysis.analysisStatus === 'PARTIAL'" effect="plain">部分完成</el-tag>
              <el-tag v-else-if="analysis.analysisStatus === 'FAILED'" effect="plain">分析失败</el-tag>
            </div>
            <div class="analysis-context">
              {{ entityTypeLabel(analysis.entityType) }} #{{ analysis.entityId }} · 站点 {{ analysis.siteId }} ·
              {{ analysis.locale }} · 分析 #{{ analysis.id }}
            </div>
          </div>
          <div class="header-actions">
            <el-button v-hasPermi="['seo:analysis:run']" @click="openManualDialog">新建手工分析</el-button>
            <el-button
              v-if="analysis.previousAnalysisId"
              v-hasPermi="['seo:analysis:query']"
              @click="handleCompare"
            >
              历史对比
            </el-button>
            <el-button type="primary" v-hasPermi="['seo:analysis:run']" :loading="rerunning" @click="handleRerun">
              重新分析
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="analysis.failureMessage"
          :title="analysis.failureMessage"
          type="warning"
          show-icon
          :closable="false"
          class="mt-16px"
        />

        <div class="overall-panel">
          <div class="overall-score">
            <span>焦点关键词总关联度</span>
            <strong v-if="typeof analysis.overallRelevancePercent === 'number'">
              {{ analysis.overallRelevancePercent }}%
            </strong>
            <strong v-else>未完成</strong>
          </div>
          <el-progress
            v-if="typeof analysis.overallRelevancePercent === 'number'"
            class="overall-progress"
            :percentage="analysis.overallRelevancePercent"
            :stroke-width="14"
            :show-text="false"
            color="var(--el-color-primary)"
          />
          <div v-else class="overall-unavailable-bar" />
          <div class="overall-meta">
            <span>置信度 {{ typeof analysis.confidencePercent === 'number' ? `${analysis.confidencePercent}%` : '未完成' }}</span>
            <span>规则 {{ analysis.ruleProfileVersion }}</span>
            <span>词典 {{ analysis.dictionaryVersion || '未启用' }}</span>
            <span>语义模型 {{ analysis.semanticModelVersion || '未接入（不计入总分）' }}</span>
          </div>
        </div>
      </ContentWrap>

      <ContentWrap>
        <div class="section-heading">
          <div>
            <h3>逐关键词分析结果</h3>
            <p>每个关键词独立评分；展开后可查看五维依据、命中证据、原因和修改建议。</p>
          </div>
          <span>共 {{ analysis.keywords?.length || 0 }} 个关键词</span>
        </div>

        <KeywordRelevanceCard
          v-for="keyword in analysis.keywords"
          :key="keyword.id"
          :analysis-id="analysis.id"
          :keyword="keyword"
        />
      </ContentWrap>

      <ContentWrap>
        <el-alert
          title="关联度是系统内部内容分析指标，不代表搜索引擎排名保证。"
          type="info"
          show-icon
          :closable="false"
        />
      </ContentWrap>
    </template>
  </div>

  <el-dialog v-model="manualDialogVisible" title="手工录入内容并分析" width="760px" destroy-on-close>
    <el-form ref="manualFormRef" :model="manualForm" :rules="manualRules" label-width="110px">
      <div class="form-grid">
        <el-form-item label="站点 ID" prop="siteId">
          <el-input-number v-model="manualForm.siteId" :min="1" :precision="0" class="!w-100%" />
        </el-form-item>
        <el-form-item label="内容类型" prop="entityType">
          <el-select v-model="manualForm.entityType" class="!w-100%">
            <el-option v-for="item in entityTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实体 ID" prop="entityId">
          <el-input-number v-model="manualForm.entityId" :min="1" :precision="0" class="!w-100%" />
        </el-form-item>
        <el-form-item label="Locale" prop="locale">
          <el-input v-model="manualForm.locale" placeholder="zh-CN" />
        </el-form-item>
      </div>
      <el-form-item label="焦点关键词" prop="focusKeyphrase">
        <el-input v-model="manualForm.focusKeyphrase" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="关联关键词" prop="relatedKeyphrases">
        <el-select
          v-model="manualForm.relatedKeyphrases"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="输入一个关键词后按回车，可添加多个"
          class="!w-100%"
        />
      </el-form-item>
      <el-form-item label="SEO 标题" prop="content.seoTitle">
        <el-input v-model="manualForm.content.seoTitle" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="页面 H1">
        <el-input v-model="manualForm.content.h1" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="Meta 描述">
        <el-input v-model="manualForm.content.metaDescription" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="商品简介">
        <el-input v-model="manualForm.content.introduction" type="textarea" :rows="3" maxlength="4000" show-word-limit />
      </el-form-item>
      <el-form-item label="正文内容">
        <el-input v-model="manualForm.content.body" type="textarea" :rows="7" maxlength="200000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="manualDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" v-hasPermi="['seo:analysis:run']" @click="submitManualAnalysis">
        开始分析
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="compareDialogVisible" title="SEO 分析历史对比" width="920px">
    <AnalysisComparison v-if="comparison" :data="comparison" />
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  compareSeoAnalysis,
  createSeoIdempotencyKey,
  getSeoAnalysis,
  rerunSeoAnalysis,
  runSeoAnalysis,
  type SeoAnalysisCompareRespVO,
  type SeoAnalysisRespVO,
  type SeoAnalysisRunReqVO,
  type SeoContentSnapshotReqVO
} from '@/api/seo/analysis'
import { useMessage } from '@/hooks/web/useMessage'
import AnalysisComparison from './components/AnalysisComparison.vue'
import KeywordRelevanceCard from './components/KeywordRelevanceCard.vue'

defineOptions({ name: 'SeoAnalysis' })

const route = useRoute()
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const rerunning = ref(false)
const analysis = ref<SeoAnalysisRespVO>()
const comparison = ref<SeoAnalysisCompareRespVO>()
const compareDialogVisible = ref(false)
const manualDialogVisible = ref(false)
const manualFormRef = ref<FormInstance>()

const entityTypeOptions = [
  { label: '商品', value: 'PRODUCT' },
  { label: '分类', value: 'CATEGORY' },
  { label: '文章', value: 'ARTICLE' },
  { label: '页面', value: 'PAGE' }
]

type ManualAnalysisForm = Omit<SeoAnalysisRunReqVO, 'content'> & {
  content: SeoContentSnapshotReqVO
}

const createManualForm = (): ManualAnalysisForm => ({
  siteId: 1,
  entityType: 'PRODUCT',
  entityId: 1,
  locale: 'zh-CN',
  focusKeyphrase: '',
  relatedKeyphrases: [],
  sourceType: 'MANUAL',
  idempotencyKey: createSeoIdempotencyKey('seo-manual'),
  content: {
    seoTitle: '',
    h1: '',
    introduction: '',
    metaDescription: '',
    body: ''
  }
})

const manualForm = reactive<ManualAnalysisForm>(createManualForm())
const manualRules: FormRules = {
  siteId: [{ required: true, message: '请输入站点 ID', trigger: 'change' }],
  entityType: [{ required: true, message: '请选择内容类型', trigger: 'change' }],
  entityId: [{ required: true, message: '请输入实体 ID', trigger: 'change' }],
  locale: [{ required: true, message: '请输入 Locale', trigger: 'blur' }],
  focusKeyphrase: [{ required: true, message: '请输入焦点关键词', trigger: 'blur' }],
  'content.seoTitle': [{ required: true, message: '请输入用于分析的 SEO 标题', trigger: 'blur' }]
}

const entityTypeLabel = (value: string) =>
  entityTypeOptions.find((item) => item.value === value)?.label || value

const routeAnalysisId = () => {
  const raw = Array.isArray(route.query.id) ? route.query.id[0] : route.query.id
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : undefined
}

const loadAnalysis = async () => {
  const id = routeAnalysisId()
  if (!id) {
    analysis.value = undefined
    return
  }
  loading.value = true
  try {
    analysis.value = await getSeoAnalysis(id)
  } catch {
    analysis.value = undefined
    message.error('SEO 分析结果加载失败，请确认记录存在且当前账号有权限')
  } finally {
    loading.value = false
  }
}

const openManualDialog = () => {
  Object.assign(manualForm, createManualForm())
  manualDialogVisible.value = true
}

const submitManualAnalysis = async () => {
  await manualFormRef.value?.validate()
  submitting.value = true
  try {
    manualForm.idempotencyKey = createSeoIdempotencyKey('seo-manual')
    const id = await runSeoAnalysis({
      ...manualForm,
      relatedKeyphrases: manualForm.relatedKeyphrases.map((item) => item.trim()).filter(Boolean),
      content: { ...manualForm.content }
    })
    manualDialogVisible.value = false
    message.success('SEO 分析完成')
    await router.replace({ path: '/seo/analysis', query: { id: String(id) } })
  } finally {
    submitting.value = false
  }
}

const handleRerun = async () => {
  if (!analysis.value) return
  await message.confirm('将读取当前来源的最新内容重新分析，并保留本次结果用于对比，是否继续？')
  rerunning.value = true
  try {
    const id = await rerunSeoAnalysis(
      analysis.value.id,
      createSeoIdempotencyKey(`seo-rerun-${analysis.value.id}`)
    )
    message.success('重新分析完成')
    await router.replace({ path: '/seo/analysis', query: { id: String(id) } })
  } finally {
    rerunning.value = false
  }
}

const handleCompare = async () => {
  if (!analysis.value?.previousAnalysisId) return
  comparison.value = await compareSeoAnalysis(
    analysis.value.id,
    analysis.value.previousAnalysisId
  )
  compareDialogVisible.value = true
}

watch(() => route.query.id, loadAnalysis, { immediate: true })
</script>

<style scoped>
.seo-analysis-page {
  min-height: 300px;
}

.analysis-empty {
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  color: var(--el-color-primary);
  font-size: 46px;
}

.analysis-empty h2 {
  margin: 12px 0 8px;
}

.analysis-empty p {
  color: var(--el-text-color-secondary);
}

.workflow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 28px 0;
  color: var(--el-text-color-regular);
}

.empty-tip {
  margin-top: 14px;
  font-size: 12px;
}

.analysis-header,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-title h2,
.section-heading h3 {
  margin: 0;
}

.analysis-context,
.section-heading p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.overall-panel {
  margin-top: 24px;
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.overall-score {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.overall-score strong {
  color: var(--el-color-primary);
  font-size: 32px;
}

.overall-progress,
.overall-unavailable-bar {
  margin-top: 12px;
}

.overall-unavailable-bar {
  height: 14px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
}

.overall-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.section-heading {
  margin-bottom: 18px;
}

.section-heading > span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

@media (max-width: 850px) {
  .workflow,
  .analysis-header,
  .section-heading {
    flex-direction: column;
  }

  .workflow {
    align-items: stretch;
  }

  .workflow :deep(.el-icon) {
    transform: rotate(90deg);
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
