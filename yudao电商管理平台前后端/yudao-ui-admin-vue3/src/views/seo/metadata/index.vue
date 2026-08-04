<template>
  <header class="seo-todo-header">
    <div>
      <p>VANZ 官网运营</p>
      <h1>SEO 待办中心</h1>
      <span>按商品、分类和页面维护搜索标题与摘要，不需要记站点 ID 或实体 ID。</span>
    </div>
    <el-button type="primary" v-hasPermi="['seo:metadata:create']" @click="openForm('create')">
      <Icon icon="ep:plus" class="mr-5px" />新增 SEO 内容
    </el-button>
  </header>

  <section class="seo-todo-metrics" aria-label="SEO 内容状态">
    <button type="button" @click="filterByStatus(undefined)">
      <span>全部内容</span><strong>{{ metrics.total }}</strong
      ><small>当前官网记录</small>
    </button>
    <button type="button" @click="filterByStatus('DRAFT')">
      <span>待检查草稿</span><strong>{{ metrics.draft }}</strong
      ><small>检查后再发布</small>
    </button>
    <button type="button" @click="filterByStatus('PUBLISHED')">
      <span>已发布</span><strong>{{ metrics.published }}</strong
      ><small>线上搜索信息</small>
    </button>
  </section>

  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      label-width="76px"
      class="-mb-15px"
    >
      <el-form-item label="搜索内容" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="搜索标题、描述或关键词"
          class="!w-260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="内容类型" prop="entityType">
        <el-select v-model="queryParams.entityType" clearable placeholder="全部" class="!w-180px">
          <el-option
            v-for="item in entityTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="网站语言" prop="locale">
        <el-select v-model="queryParams.locale" clearable placeholder="全部语言" class="!w-160px">
          <el-option label="English" value="en" />
          <el-option label="简体中文" value="zh-CN" />
        </el-select>
      </el-form-item>
      <el-form-item label="发布状态" prop="publishStatus">
        <el-select
          v-model="queryParams.publishStatus"
          clearable
          placeholder="全部"
          class="!w-180px"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['seo:metadata:query']" @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />查询
        </el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <ErpPageState
      v-if="!loading && list.length === 0"
      compact
      :description="
        hasActiveFilters
          ? '没有内容符合当前筛选条件，可以重置筛选后再查看。'
          : '还没有 SEO 内容。建议先从官网正在展示的核心商品开始，填写搜索标题、搜索摘要和焦点关键词。'
      "
      eyebrow="官网获客"
      :primary-text="hasActiveFilters ? '重置筛选' : '为商品创建 SEO'"
      secondary-text="查看商品中心"
      title="暂无 SEO 内容"
      type="empty"
      @primary="hasActiveFilters ? resetQuery() : openForm('create')"
      @secondary="router.push('/mall/product/spu')"
    />
    <el-table v-else v-loading="loading" :data="list" stripe show-overflow-tooltip>
      <el-table-column type="expand" width="48">
        <template #default="{ row }">
          <div class="search-result-preview">
            <small>{{ row.canonicalUrl || 'https://vanz.com/' }}</small>
            <strong>{{ row.seoTitle || entityDisplayName(row) }}</strong>
            <p>{{ row.metaDescription || '尚未填写搜索摘要。' }}</p>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="内容对象" min-width="210">
        <template #default="{ row }">
          <strong>{{ entityDisplayName(row) }}</strong>
          <small class="entity-type-hint">{{ entityTypeLabel(row.entityType) }}</small>
        </template>
      </el-table-column>
      <el-table-column label="SEO 标题" prop="seoTitle" min-width="250" />
      <el-table-column label="内容完整度" width="150">
        <template #default="{ row }">
          <el-tooltip :content="metadataCompleteness(row).hint" placement="top">
            <div class="metadata-completeness">
              <el-progress
                :percentage="metadataCompleteness(row).percentage"
                :show-text="false"
                :stroke-width="6"
                :status="metadataCompleteness(row).percentage === 100 ? 'success' : undefined"
              />
              <span>{{ metadataCompleteness(row).percentage }}%</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="语言" width="110">
        <template #default="{ row }">{{ localeLabel(row.locale) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.publishStatus === 'PUBLISHED' ? 'success' : 'info'">
            {{ scope.row.publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" :formatter="dateFormatter" width="180" />
      <el-table-column label="操作" fixed="right" width="270">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :loading="analysingId === scope.row.id"
            v-hasPermi="['seo:analysis:run']"
            @click="handleAnalyze(scope.row)"
          >
            分析
          </el-button>
          <el-button
            v-if="scope.row.latestAnalysisId"
            link
            type="primary"
            v-hasPermi="['seo:analysis:query']"
            @click="openAnalysis(scope.row.latestAnalysisId)"
          >
            分析结果
          </el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['seo:metadata:update']"
            @click="openForm('update', scope.row.id)"
          >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-hasPermi="['seo:metadata:publish']"
            @click="handlePublish(scope.row)"
          >发布</el-button
          >
          <el-dropdown v-hasPermi="['seo:metadata:delete']" class="ml-10px">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleDelete(scope.row.id)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <MetadataForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dateFormatter } from '@/utils/formatTime'
import { createSeoIdempotencyKey, runSeoAnalysis } from '@/api/seo/analysis'
import {
  deleteSeoMetadata,
  getSeoMetadataPage,
  publishSeoMetadata,
  type SeoEntityType,
  type SeoMetadataPageReqVO,
  type SeoMetadataRespVO
} from '@/api/seo/metadata'
import { useMessage } from '@/hooks/web/useMessage'
import MetadataForm from './MetadataForm.vue'
import * as ProductSpuApi from '@/api/mall/product/spu'
import * as ProductCategoryApi from '@/api/mall/product/category'
import { ErpPageState } from '@/components/ErpPageState'

defineOptions({ name: 'SeoMetadata' })

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const analysingId = ref<number>()
const list = ref<SeoMetadataRespVO[]>([])
const total = ref(0)
const metrics = reactive({ total: 0, draft: 0, published: 0 })
const productNameMap = ref<Record<number, string>>({})
const categoryNameMap = ref<Record<number, string>>({})
const queryFormRef = ref()
const formRef = ref<InstanceType<typeof MetadataForm>>()

const entityTypeOptions: Array<{ label: string; value: SeoEntityType }> = [
  { label: '商品', value: 'PRODUCT' },
  { label: '分类', value: 'CATEGORY' },
  { label: '文章', value: 'ARTICLE' },
  { label: '页面', value: 'PAGE' }
]

const queryParams = reactive<SeoMetadataPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  siteId: 1,
  locale: 'en'
})
const hasActiveFilters = computed(() =>
  Boolean(queryParams.keyword || queryParams.entityType || queryParams.publishStatus)
)

const entityTypeLabel = (value: SeoEntityType) =>
  entityTypeOptions.find((item) => item.value === value)?.label || value
const localeLabel = (value: string) =>
  value === 'en' ? 'English' : value === 'zh-CN' ? '简体中文' : value
const entityDisplayName = (row: SeoMetadataRespVO) => {
  if (row.entityType === 'PRODUCT')
    return productNameMap.value[row.entityId] || `商品 #${row.entityId}`
  if (row.entityType === 'CATEGORY')
    return categoryNameMap.value[row.entityId] || `分类 #${row.entityId}`
  if (row.entityType === 'PAGE') return `官网页面 #${row.entityId}`
  if (row.entityType === 'ARTICLE') return `博客文章 #${row.entityId}`
  return `内容 #${row.entityId}`
}
const metadataCompleteness = (row: SeoMetadataRespVO) => {
  const checks = [
    ['SEO 标题', Boolean(row.seoTitle?.trim())],
    ['搜索摘要', Boolean(row.metaDescription?.trim())],
    ['焦点关键词', Boolean(row.focusKeyphrase?.trim())],
    ['分享标题', Boolean(row.ogTitle?.trim())],
    ['分享图片', Boolean(row.ogImage?.trim())]
  ] as const
  const completed = checks.filter(([, done]) => done).length
  const missing = checks.filter(([, done]) => !done).map(([label]) => label)
  return {
    percentage: Math.round((completed / checks.length) * 100),
    hint: missing.length ? `待补充：${missing.join('、')}` : 'SEO 核心内容已完整'
  }
}

const loadEntityNames = async () => {
  const [products, categories] = await Promise.allSettled([
    ProductSpuApi.getSpuSimpleList(),
    ProductCategoryApi.getCategoryList({})
  ])
  if (products.status === 'fulfilled') {
    productNameMap.value = Object.fromEntries(
      (products.value || []).map((item: ProductSpuApi.Spu) => [
        item.id,
        item.name || `商品 #${item.id}`
      ])
    )
  }
  if (categories.status === 'fulfilled') {
    categoryNameMap.value = Object.fromEntries(
      (categories.value || []).map((item: ProductCategoryApi.CategoryVO) => [
        item.id,
        item.name || `分类 #${item.id}`
      ])
    )
  }
}

const getList = async () => {
  loading.value = true
  try {
    const baseQuery = { pageNo: 1, pageSize: 1, siteId: 1, locale: 'en' }
    const [data, all, draft, published] = await Promise.all([
      getSeoMetadataPage(queryParams),
      getSeoMetadataPage(baseQuery),
      getSeoMetadataPage({ ...baseQuery, publishStatus: 'DRAFT' }),
      getSeoMetadataPage({ ...baseQuery, publishStatus: 'PUBLISHED' })
    ])
    list.value = data.list
    total.value = data.total
    metrics.total = all.total || 0
    metrics.draft = draft.total || 0
    metrics.published = published.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.siteId = 1
  queryParams.locale = 'en'
  queryParams.entityType = undefined
  queryParams.publishStatus = undefined
  queryParams.keyword = undefined
  handleQuery()
}

const filterByStatus = (status?: 'DRAFT' | 'PUBLISHED') => {
  queryParams.publishStatus = status
  handleQuery()
}

const openForm = (type: 'create' | 'update', id?: number) => {
  formRef.value?.open(type, id)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await deleteSeoMetadata(id)
    message.success('删除成功')
    await getList()
  } catch {}
}

const handlePublish = async (row: SeoMetadataRespVO) => {
  try {
    await ElMessageBox.confirm('确认发布这条 SEO 元数据吗？', '发布确认', {
      confirmButtonText: '发布',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await publishSeoMetadata(row.id, row.version)
    message.success('发布成功')
    await getList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    message.error('发布失败，可能存在版本冲突，请重新加载后重试')
  }
}

const openAnalysis = async (id: number) => {
  await router.push({ path: '/seo/analysis', query: { id: String(id) } })
}

const handleAnalyze = async (row: SeoMetadataRespVO) => {
  if (!row.focusKeyphrase?.trim()) {
    message.warning('请先编辑这条内容并设置焦点关键词')
    return
  }
  analysingId.value = row.id
  try {
    const id = await runSeoAnalysis({
      siteId: row.siteId,
      entityType: row.entityType,
      entityId: row.entityId,
      locale: row.locale,
      focusKeyphrase: row.focusKeyphrase,
      relatedKeyphrases: row.relatedKeyphrases || [],
      sourceType: 'ENTITY',
      sourceId: row.id,
      idempotencyKey: createSeoIdempotencyKey(`seo-entity-${row.id}`)
    })
    message.success('SEO 分析完成')
    await openAnalysis(id)
  } finally {
    analysingId.value = undefined
  }
}

onMounted(async () => {
  await loadEntityNames()
  await getList()
})
</script>

<style scoped>
.seo-todo-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
}

.seo-todo-header p,
.seo-todo-header h1,
.seo-todo-header span {
  margin: 0;
}

.seo-todo-header p {
  color: var(--furniture-admin-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.seo-todo-header h1 {
  margin-top: 4px;
  color: var(--furniture-admin-ink);
  font-size: 24px;
}

.seo-todo-header span {
  display: block;
  margin-top: 6px;
  color: var(--furniture-admin-body);
  font-size: 13px;
}

.seo-todo-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.seo-todo-metrics button {
  display: grid;
  padding: 14px 16px;
  color: var(--furniture-admin-ink);
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 7px;
  gap: 5px;
}

.seo-todo-metrics button:hover {
  border-color: var(--furniture-admin-border-strong);
}

.seo-todo-metrics span,
.seo-todo-metrics small,
.entity-type-hint {
  color: var(--furniture-admin-muted);
  font-size: 12px;
}

.seo-todo-metrics strong {
  font-size: 26px;
}

.entity-type-hint {
  display: block;
  margin-top: 4px;
}

.metadata-completeness {
  display: grid;
  grid-template-columns: minmax(74px, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.metadata-completeness span {
  font-size: 12px;
}

.search-result-preview {
  max-width: 720px;
  padding: 14px 24px;
}

.search-result-preview small {
  color: #188038;
}

.search-result-preview strong {
  display: block;
  margin: 5px 0;
  color: #1a0dab;
  font-size: 18px;
  font-weight: 500;
}

.search-result-preview p {
  margin: 0;
  color: #4d5156;
  line-height: 1.55;
}
</style>
