<template>
  <ContentWrap>
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
          class="!w-300px"
          @change="loadConfig"
        />
      </el-form-item>
      <el-form-item label="站点名称" prop="siteName">
        <el-input v-model="formData.siteName" placeholder="请输入站点名称" />
      </el-form-item>
      <el-form-item label="站点 URL" prop="siteUrl">
        <el-input v-model="formData.siteUrl" placeholder="https://www.example.com" />
      </el-form-item>
      <el-form-item label="标题后缀" prop="defaultTitleSuffix">
        <el-input v-model="formData.defaultTitleSuffix" placeholder="例如：- 品牌商城" />
      </el-form-item>
      <el-form-item label="默认描述" prop="defaultDescription">
        <el-input
          v-model="formData.defaultDescription"
          type="textarea"
          :rows="3"
          placeholder="请输入页面默认描述"
        />
      </el-form-item>
      <el-form-item label="默认 Robots" prop="defaultRobots">
        <el-select v-model="formData.defaultRobots" class="!w-300px">
          <el-option label="允许索引并跟踪链接 (index,follow)" value="index,follow" />
          <el-option label="允许索引，不跟踪链接 (index,nofollow)" value="index,nofollow" />
          <el-option label="不索引，跟踪链接 (noindex,follow)" value="noindex,follow" />
          <el-option label="不索引且不跟踪链接 (noindex,nofollow)" value="noindex,nofollow" />
        </el-select>
      </el-form-item>
      <el-form-item label="默认 OG 图" prop="defaultOgImage">
        <el-input v-model="formData.defaultOgImage" placeholder="请输入图片 URL" />
      </el-form-item>
      <el-form-item label="默认 Locale" prop="defaultLocale">
        <el-input v-model="formData.defaultLocale" placeholder="例如：zh-CN" class="!w-300px" />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :disabled="saving"
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
import { onMounted, ref } from 'vue'
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

const loadConfig = async () => {
  const siteId = formData.value.siteId
  if (!siteId) return
  loading.value = true
  try {
    const config = await getSeoSiteConfig(siteId)
    formData.value = config ? { ...config } : createDefaultForm(siteId)
    formRef.value?.clearValidate()
  } finally {
    loading.value = false
  }
}

const submitForm = async () => {
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
