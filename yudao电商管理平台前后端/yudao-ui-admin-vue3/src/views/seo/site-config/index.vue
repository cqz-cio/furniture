<template>
  <ContentWrap>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="mb-18px"
    >
      <template #default>
        <el-button type="primary" link @click="loadConfig()">重试加载</el-button>
      </template>
    </el-alert>
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="formData"
      :rules="formRules"
      label-width="130px"
      class="max-w-800px"
    >
      <el-form-item label="站点 ID" prop="siteId">
        <el-input-number
          v-model="formData.siteId"
          :min="1"
          :precision="0"
          :disabled="saving"
          class="!w-300px"
          @change="loadConfig"
        />
      </el-form-item>
      <el-form-item label="站点名称" prop="siteName">
        <el-input v-model="formData.siteName" :disabled="editorDisabled" placeholder="请输入站点名称" />
      </el-form-item>
      <el-form-item label="站点 URL" prop="siteUrl">
        <el-input v-model="formData.siteUrl" :disabled="editorDisabled" placeholder="https://www.example.com" />
      </el-form-item>
      <el-form-item label="标题后缀" prop="defaultTitleSuffix">
        <el-input v-model="formData.defaultTitleSuffix" :disabled="editorDisabled" placeholder="例如：- 品牌商城" />
      </el-form-item>
      <el-form-item label="默认描述" prop="defaultDescription">
        <el-input
          v-model="formData.defaultDescription"
          :disabled="editorDisabled"
          type="textarea"
          :rows="3"
          placeholder="请输入页面默认描述"
        />
      </el-form-item>
      <el-form-item label="默认 Robots" prop="defaultRobots">
        <el-select v-model="formData.defaultRobots" :disabled="editorDisabled" class="!w-300px">
          <el-option label="允许索引并跟踪链接 (index,follow)" value="index,follow" />
          <el-option label="允许索引，不跟踪链接 (index,nofollow)" value="index,nofollow" />
          <el-option label="不索引，跟踪链接 (noindex,follow)" value="noindex,follow" />
          <el-option label="不索引且不跟踪链接 (noindex,nofollow)" value="noindex,nofollow" />
        </el-select>
      </el-form-item>
      <el-form-item label="默认 OG 图" prop="defaultOgImage">
        <el-input v-model="formData.defaultOgImage" :disabled="editorDisabled" placeholder="请输入图片 URL" />
      </el-form-item>
      <el-form-item label="默认 Locale" prop="defaultLocale">
        <el-input v-model="formData.defaultLocale" :disabled="editorDisabled" placeholder="例如：zh-CN" class="!w-300px" />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :disabled="saving || editorDisabled"
          v-hasPermi="['seo:site-config:update']"
          @click="submitForm"
        >
          保存
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  getSeoSiteConfig,
  saveSeoSiteConfig,
  type SeoSiteConfigSaveReqVO
} from '@/api/seo/siteConfig'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'SeoSiteConfig' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const loadRequestId = ref(0)
const formRef = ref<FormInstance>()

const createDefaultForm = (siteId = 1): SeoSiteConfigSaveReqVO => ({
  siteId,
  siteName: '',
  siteUrl: '',
  defaultTitleSuffix: '',
  defaultDescription: '',
  defaultRobots: 'index,follow',
  defaultOgImage: '',
  defaultLocale: 'zh-CN'
})

const formData = ref<SeoSiteConfigSaveReqVO>(createDefaultForm())
const editorDisabled = computed(() => loading.value || Boolean(loadError.value))

const isAbsoluteHttpUrl = (value: string) => {
  if (value.includes('\\')) return false
  try {
    const url = new URL(value.trim())
    return (
      ['http:', 'https:'].includes(url.protocol) &&
      Boolean(url.hostname) &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash
    )
  } catch {
    return false
  }
}

const validateSiteUrl = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!isAbsoluteHttpUrl(value)) {
    callback(new Error('请输入绝对 HTTP(S) URL'))
    return
  }
  callback()
}

const formRules = {
  siteId: [{ required: true, message: '站点 ID 不能为空', trigger: 'change' }],
  siteName: [{ required: true, message: '站点名称不能为空', trigger: 'blur' }],
  siteUrl: [{ required: true, validator: validateSiteUrl, trigger: 'blur' }]
}

const loadConfig = async (requestedSiteId?: number) => {
  const siteId = requestedSiteId || formData.value.siteId
  if (!siteId) return
  const requestId = ++loadRequestId.value
  formData.value = createDefaultForm(siteId)
  loadError.value = ''
  loading.value = true
  formRef.value?.clearValidate()
  try {
    const config = await getSeoSiteConfig(siteId)
    if (requestId !== loadRequestId.value || formData.value.siteId !== siteId) return
    formData.value = config ? { ...config } : createDefaultForm(siteId)
    formRef.value?.clearValidate()
  } catch {
    if (requestId !== loadRequestId.value || formData.value.siteId !== siteId) return
    formData.value = createDefaultForm(siteId)
    loadError.value = '站点 SEO 配置加载失败，请重试'
  } finally {
    if (requestId === loadRequestId.value && formData.value.siteId === siteId) {
      loading.value = false
    }
  }
}

const submitForm = async () => {
  if (editorDisabled.value || saving.value) return
  await formRef.value?.validate()
  saving.value = true
  try {
    await saveSeoSiteConfig(formData.value)
    message.success('站点 SEO 配置保存成功')
    await loadConfig()
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>
