<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      label-width="80px"
      class="-mb-15px"
    >
      <el-form-item label="站点 ID" prop="siteId">
        <el-input-number v-model="queryParams.siteId" :min="1" :precision="0" class="!w-180px" />
      </el-form-item>
      <el-form-item label="内容类型" prop="entityType">
        <el-select v-model="queryParams.entityType" clearable placeholder="全部" class="!w-180px">
          <el-option v-for="item in entityTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="实体 ID" prop="entityId">
        <el-input-number v-model="queryParams.entityId" :min="1" :precision="0" class="!w-180px" />
      </el-form-item>
      <el-form-item label="Locale" prop="locale">
        <el-input v-model="queryParams.locale" clearable placeholder="例如：zh-CN" class="!w-180px" />
      </el-form-item>
      <el-form-item label="发布状态" prop="publishStatus">
        <el-select v-model="queryParams.publishStatus" clearable placeholder="全部" class="!w-180px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['seo:metadata:query']" @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />查询
        </el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['seo:metadata:create']" @click="openForm('create')">
          <Icon icon="ep:plus" class="mr-5px" />新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe show-overflow-tooltip>
      <el-table-column label="SEO 标题" prop="seoTitle" min-width="220" />
      <el-table-column label="内容身份" min-width="180">
        <template #default="scope">{{ entityTypeLabel(scope.row.entityType) }} #{{ scope.row.entityId }}</template>
      </el-table-column>
      <el-table-column label="站点 ID" prop="siteId" width="100" />
      <el-table-column label="Locale" prop="locale" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.publishStatus === 'PUBLISHED' ? 'success' : 'info'">
            {{ scope.row.publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" prop="version" width="80" />
      <el-table-column label="更新时间" prop="updateTime" :formatter="dateFormatter" width="180" />
      <el-table-column label="操作" fixed="right" width="390">
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
            查看最新分析
          </el-button>
          <el-button link type="primary" v-hasPermi="['seo:metadata:update']" @click="openForm('update', scope.row.id)">编辑</el-button>
          <el-button link type="danger" v-hasPermi="['seo:metadata:delete']" @click="handleDelete(scope.row.id)">删除</el-button>
          <el-button link type="success" v-hasPermi="['seo:metadata:publish']" @click="handlePublish(scope.row)">发布</el-button>
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

defineOptions({ name: 'SeoMetadata' })

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const analysingId = ref<number>()
const list = ref<SeoMetadataRespVO[]>([])
const total = ref(0)
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
  pageSize: 10
})

const entityTypeLabel = (value: SeoEntityType) =>
  entityTypeOptions.find((item) => item.value === value)?.label || value

const getList = async () => {
  loading.value = true
  try {
    const data = await getSeoMetadataPage(queryParams)
    list.value = data.list
    total.value = data.total
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

onMounted(getList)
</script>
