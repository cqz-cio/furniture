<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-alert
      v-if="isEdit && formData.publishStatus === 'PUBLISHED'"
      title="保存后将影响线上版本"
      type="warning"
      show-icon
      :closable="false"
      class="mb-18px"
    />
    <el-alert
      v-else
      title="当前维护 VANZ 英文官网。选择商品或分类后即可填写，不需要输入技术编号。"
      type="info"
      show-icon
      :closable="false"
      class="mb-18px"
    />
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="130px"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="内容类型" prop="entityType">
            <el-select v-model="formData.entityType" :disabled="isEdit" class="!w-1/1">
              <el-option
                v-for="item in entityTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="内容对象" prop="entityId">
            <el-select
              v-if="entityOptions.length"
              v-model="formData.entityId"
              :disabled="isEdit"
              class="!w-1/1"
              filterable
              placeholder="请选择要优化的内容"
            >
              <el-option
                v-for="item in entityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input-number
              v-else
              v-model="formData.entityId"
              :disabled="isEdit"
              :min="1"
              :precision="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="网站语言" prop="locale">
        <el-select v-model="formData.locale" :disabled="isEdit" class="!w-240px">
          <el-option label="English" value="en" />
          <el-option label="简体中文" value="zh-CN" />
        </el-select>
      </el-form-item>
      <el-form-item label="SEO 标题" prop="seoTitle">
        <el-input v-model="formData.seoTitle" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="搜索摘要" prop="metaDescription">
        <el-input
          v-model="formData.metaDescription"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="焦点关键词" prop="focusKeyphrase">
        <el-input v-model="formData.focusKeyphrase" maxlength="255" />
      </el-form-item>
      <el-form-item label="关联关键词" prop="relatedKeyphrases">
        <el-select
          v-model="formData.relatedKeyphrases"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="输入关键词后按回车创建标签"
          class="!w-1/1"
        />
      </el-form-item>
      <el-form-item label="规范网址（可选）" prop="canonicalUrl">
        <el-input v-model="formData.canonicalUrl" placeholder="可留空，或输入绝对 HTTP(S) URL" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="允许索引" prop="robotsIndex">
            <el-switch v-model="formData.robotsIndex" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="跟踪链接" prop="robotsFollow">
            <el-switch v-model="formData.robotsFollow" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="分享标题" prop="ogTitle">
        <el-input v-model="formData.ogTitle" maxlength="255" />
      </el-form-item>
      <el-form-item label="分享摘要" prop="ogDescription">
        <el-input v-model="formData.ogDescription" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>
      <el-form-item label="分享图片" prop="ogImage">
        <el-input v-model="formData.ogImage" placeholder="请输入图片 URL" />
      </el-form-item>
      <el-form-item label="结构化类型" prop="schemaType">
        <el-input v-model="formData.schemaType" placeholder="例如：Product" />
      </el-form-item>

      <div class="seo-form-preview" aria-label="搜索结果预览">
        <small>{{ formData.canonicalUrl || 'https://vanz.com/' }}</small>
        <strong>{{ formData.seoTitle || '搜索标题预览' }}</strong>
        <p>{{ formData.metaDescription || '填写搜索摘要后，会在这里预览搜索结果展示效果。' }}</p>
      </div>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="formLoading" @click="submitForm">保存</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import { computed, ref } from 'vue'
import {
  createSeoMetadata,
  getSeoMetadata,
  updateSeoMetadata,
  type SeoEntityType,
  type SeoMetadataCreateReqVO,
  type SeoMetadataRespVO,
  type SeoMetadataUpdateReqVO,
  type SeoPublishStatus
} from '@/api/seo/metadata'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProductSpuApi from '@/api/mall/product/spu'
import * as ProductCategoryApi from '@/api/mall/product/category'

defineOptions({ name: 'SeoMetadataForm' })

interface SeoMetadataFormData extends SeoMetadataCreateReqVO {
  id?: number
  version?: number
  publishStatus?: SeoPublishStatus
}

const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref<FormInstance>()
const isEdit = computed(() => formType.value === 'update')
const productOptions = ref<Array<{ label: string; value: number }>>([])
const categoryOptions = ref<Array<{ label: string; value: number }>>([])
const pageOptions = [
  { label: '首页', value: 1 },
  { label: '产品中心', value: 2 },
  { label: '品牌介绍', value: 3 },
  { label: '工坊', value: 4 },
  { label: '博客', value: 5 },
  { label: '联系我们', value: 6 }
]

const entityTypeOptions: Array<{ label: string; value: SeoEntityType }> = [
  { label: '商品', value: 'PRODUCT' },
  { label: '分类', value: 'CATEGORY' },
  { label: '文章', value: 'ARTICLE' },
  { label: '页面', value: 'PAGE' }
]
const entityOptions = computed(() => {
  if (formData.value.entityType === 'PRODUCT') return productOptions.value
  if (formData.value.entityType === 'CATEGORY') return categoryOptions.value
  if (formData.value.entityType === 'PAGE') return pageOptions
  return []
})

const createDefaultForm = (): SeoMetadataFormData => ({
  siteId: 1,
  entityType: 'PRODUCT',
  entityId: 0,
  locale: 'en',
  seoTitle: '',
  metaDescription: '',
  focusKeyphrase: '',
  relatedKeyphrases: [],
  canonicalUrl: '',
  robotsIndex: true,
  robotsFollow: true,
  ogTitle: '',
  ogDescription: '',
  ogImage: '',
  schemaType: ''
})

const formData = ref<SeoMetadataFormData>(createDefaultForm())

const isAbsoluteHttpUrl = (value: string) => {
  if (value.includes('\\')) return false
  try {
    const url = new URL(value.trim())
    return (
      ['http:', 'https:'].includes(url.protocol) &&
      Boolean(url.hostname) &&
      !url.username &&
      !url.password &&
      !url.hash
    )
  } catch {
    return false
  }
}

const validateCanonicalUrl = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value.trim()) {
    callback()
    return
  }
  if (!isAbsoluteHttpUrl(value)) {
    callback(new Error('Canonical URL 必须是绝对 HTTP(S) URL'))
    return
  }
  callback()
}

const formRules = {
  entityType: [{ required: true, message: '内容类型不能为空', trigger: 'change' }],
  entityId: [
    {
      validator: (_rule: unknown, value: number, callback: (error?: Error) => void) =>
        value > 0 ? callback() : callback(new Error('请选择要优化的内容')),
      trigger: 'change'
    }
  ],
  locale: [{ required: true, message: '请选择网站语言', trigger: 'change' }],
  seoTitle: [{ required: true, message: 'SEO 标题不能为空', trigger: 'blur' }],
  canonicalUrl: [{ validator: validateCanonicalUrl, trigger: 'blur' }]
}

const loadEntityOptions = async () => {
  const [products, categories] = await Promise.allSettled([
    ProductSpuApi.getSpuSimpleList(),
    ProductCategoryApi.getCategoryList({})
  ])
  productOptions.value =
    products.status === 'fulfilled'
      ? (products.value || [])
          .filter((item: ProductSpuApi.Spu) => Boolean(item.id))
          .map((item: ProductSpuApi.Spu) => ({
            label: item.name || `商品 #${item.id}`,
            value: item.id!
          }))
      : []
  categoryOptions.value =
    categories.status === 'fulfilled'
      ? (categories.value || [])
          .filter((item: ProductCategoryApi.CategoryVO) => Boolean(item.id))
          .map((item: ProductCategoryApi.CategoryVO) => ({ label: item.name, value: item.id! }))
      : []
}

const open = async (type: 'create' | 'update', id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增 SEO 元数据' : '编辑 SEO 元数据'
  formType.value = type
  formData.value = createDefaultForm()
  formRef.value?.resetFields()
  await loadEntityOptions()
  if (type === 'create' && entityOptions.value.length) {
    formData.value.entityId = entityOptions.value[0].value
  }
  if (type === 'update' && id) {
    formLoading.value = true
    try {
      const data: SeoMetadataRespVO = await getSeoMetadata(id)
      formData.value = { ...data, relatedKeyphrases: data.relatedKeyphrases || [] }
    } catch {
      message.error('加载 SEO 元数据失败，请重试')
      dialogVisible.value = false
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

watch(
  () => formData.value.entityType,
  (entityType) => {
    if (isEdit.value) return
    formData.value.entityId = entityOptions.value[0]?.value || 0
    if (!formData.value.schemaType) {
      formData.value.schemaType =
        entityType === 'PRODUCT' ? 'Product' : entityType === 'ARTICLE' ? 'Article' : ''
    }
  }
)

const emit = defineEmits<{ success: [] }>()

const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    const baseData: SeoMetadataCreateReqVO = {
      siteId: formData.value.siteId,
      entityType: formData.value.entityType,
      entityId: formData.value.entityId,
      locale: formData.value.locale,
      seoTitle: formData.value.seoTitle,
      metaDescription: formData.value.metaDescription,
      focusKeyphrase: formData.value.focusKeyphrase,
      relatedKeyphrases: formData.value.relatedKeyphrases,
      canonicalUrl: formData.value.canonicalUrl,
      robotsIndex: formData.value.robotsIndex,
      robotsFollow: formData.value.robotsFollow,
      ogTitle: formData.value.ogTitle,
      ogDescription: formData.value.ogDescription,
      ogImage: formData.value.ogImage,
      schemaType: formData.value.schemaType
    }
    if (isEdit.value) {
      const data: SeoMetadataUpdateReqVO = {
        ...baseData,
        id: formData.value.id!,
        version: formData.value.version!
      }
      await updateSeoMetadata(data)
      message.success('SEO 元数据更新成功')
    } else {
      await createSeoMetadata(baseData)
      message.success('SEO 元数据创建成功')
    }
    dialogVisible.value = false
    emit('success')
  } catch {
    if (isEdit.value) {
      message.error('保存失败，可能存在版本冲突，请重新加载后再编辑')
    } else {
      message.error('保存失败，请重试')
    }
  } finally {
    formLoading.value = false
  }
}
</script>

<style scoped>
.seo-form-preview {
  padding: 14px 18px;
  margin: 4px 0 0 130px;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
}

.seo-form-preview small {
  color: #188038;
}

.seo-form-preview strong {
  display: block;
  margin: 5px 0;
  color: #1a0dab;
  font-size: 18px;
  font-weight: 500;
}

.seo-form-preview p {
  margin: 0;
  color: #4d5156;
  line-height: 1.55;
}
</style>
