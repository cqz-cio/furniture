<template>
  <el-row :gutter="14" class="seo-site-config">
    <el-col :lg="16" :md="24">
      <ContentWrap
        title="站点默认配置"
        message="这里的设置会作为未单独配置页面的 SEO 默认值"
        surface="form"
        :auto-title="false"
      >
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

        <el-alert
          v-else-if="!loading && !hasSavedConfig"
          title="首次使用只需确认一次官网地址"
          type="warning"
          show-icon
          :closable="false"
          class="mb-18px"
        >
          <template #default>
            系统已带出本地官网地址；确认无误后保存，即可返回导航管理生成真实预览。
          </template>
        </el-alert>

        <el-form
          ref="formRef"
          v-loading="loading"
          :model="formData"
          :rules="formRules"
          label-position="top"
          class="seo-site-config__form"
        >
          <section class="seo-config-section" aria-labelledby="seo-site-identity">
            <div class="seo-config-section__header">
              <div>
                <h2 id="seo-site-identity">站点身份</h2>
                <p>用于识别站点，并生成搜索结果中的品牌信息。</p>
              </div>
              <el-tag type="info" effect="plain">基础信息</el-tag>
            </div>
            <div class="seo-config-grid">
              <el-form-item label="官网语言" prop="defaultLocale">
                <el-select
                  v-model="formData.defaultLocale"
                  :disabled="editorDisabled"
                  class="!w-full"
                >
                  <el-option label="English" value="en" />
                  <el-option label="简体中文" value="zh-CN" />
                </el-select>
              </el-form-item>
              <el-form-item label="导航模板" prop="navigationTemplate">
                <el-select
                  v-model="formData.navigationTemplate"
                  :disabled="editorDisabled"
                  class="!w-full"
                >
                  <el-option label="VANZ · 2B 官网导航" value="VANZ_B2B" />
                  <el-option label="Oakved · 2C 家具导航" value="OAKVED_B2C" />
                </el-select>
              </el-form-item>
              <el-form-item label="站点名称" prop="siteName" class="seo-config-grid__wide">
                <el-input
                  v-model="formData.siteName"
                  :disabled="editorDisabled"
                  placeholder="请输入站点名称"
                />
              </el-form-item>
              <el-form-item label="站点 URL" prop="siteUrl" class="seo-config-grid__wide">
                <el-input
                  v-model="formData.siteUrl"
                  :disabled="editorDisabled"
                  placeholder="https://www.example.com"
                />
              </el-form-item>
            </div>
          </section>

          <el-divider />

          <section class="seo-config-section" aria-labelledby="seo-search-defaults">
            <div class="seo-config-section__header">
              <div>
                <h2 id="seo-search-defaults">搜索默认值</h2>
                <p>页面缺少独立配置时，将使用以下标题、描述和抓取规则。</p>
              </div>
              <el-tag type="info" effect="plain">搜索展示</el-tag>
            </div>
            <div class="seo-config-grid">
              <el-form-item label="标题后缀" prop="defaultTitleSuffix">
                <el-input
                  v-model="formData.defaultTitleSuffix"
                  :disabled="editorDisabled"
                  placeholder="例如：- 品牌商城"
                />
              </el-form-item>
              <el-form-item label="默认 Robots" prop="defaultRobots">
                <el-select
                  v-model="formData.defaultRobots"
                  :disabled="editorDisabled"
                  class="!w-full"
                >
                  <el-option label="允许索引并跟踪链接 (index,follow)" value="index,follow" />
                  <el-option label="允许索引，不跟踪链接 (index,nofollow)" value="index,nofollow" />
                  <el-option label="不索引，跟踪链接 (noindex,follow)" value="noindex,follow" />
                  <el-option
                    label="不索引且不跟踪链接 (noindex,nofollow)"
                    value="noindex,nofollow"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                label="默认描述"
                prop="defaultDescription"
                class="seo-config-grid__wide"
              >
                <el-input
                  v-model="formData.defaultDescription"
                  :disabled="editorDisabled"
                  type="textarea"
                  :rows="4"
                  maxlength="320"
                  show-word-limit
                  placeholder="请输入页面默认描述"
                />
              </el-form-item>
            </div>
          </section>

          <el-divider />

          <section class="seo-config-section" aria-labelledby="seo-social-sharing">
            <div class="seo-config-section__header">
              <div>
                <h2 id="seo-social-sharing">社交分享</h2>
                <p>设置页面未指定图片时使用的默认 Open Graph 图片。</p>
              </div>
              <el-tag type="info" effect="plain">分享预览</el-tag>
            </div>
            <el-form-item label="默认 OG 图" prop="defaultOgImage" class="!mb-0">
              <el-input
                v-model="formData.defaultOgImage"
                :disabled="editorDisabled"
                placeholder="请输入绝对图片 URL"
              />
            </el-form-item>
          </section>

          <div class="seo-site-config__actions">
            <span>
              <Icon :icon="configStatus.icon" :class="configStatus.className" />
              {{ configStatus.label }}
            </span>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="saving || editorDisabled"
              v-hasPermi="['seo:site-config:update']"
              @click="submitForm"
            >
              <Icon icon="ep:check" class="mr-5px" />
              {{ returnToNavigation ? '保存并返回导航' : '保存配置' }}
            </el-button>
          </div>
        </el-form>
      </ContentWrap>
    </el-col>

    <el-col :lg="8" :md="24">
      <aside class="seo-site-config__aside" aria-label="SEO 配置预览">
        <ContentWrap title="搜索结果预览" surface="panel" :auto-title="false">
          <div class="seo-search-preview">
            <span>{{ searchPreview.url }}</span>
            <strong>{{ searchPreview.title }}</strong>
            <p>{{ searchPreview.description }}</p>
          </div>
          <p class="seo-search-preview__hint">
            预览会随左侧配置实时更新，实际页面仍可覆盖这些默认值。
          </p>
        </ContentWrap>

        <ContentWrap title="配置完整度" surface="panel" :auto-title="false">
          <div class="seo-config-progress">
            <div>
              <strong>{{ configCompletion }}%</strong>
              <span>{{ completedChecks }}/{{ configChecks.length }} 项已就绪</span>
            </div>
            <el-progress :percentage="configCompletion" :show-text="false" :stroke-width="6" />
          </div>
          <div class="seo-config-checks">
            <div v-for="item in configChecks" :key="item.label">
              <Icon
                :icon="item.done ? 'ep:circle-check-filled' : 'ep:warning-filled'"
                :class="item.done ? 'is-complete' : 'is-pending'"
              />
              <span>{{ item.label }}</span>
              <small>{{ item.done ? '已配置' : '待补充' }}</small>
            </div>
          </div>
        </ContentWrap>
      </aside>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getSeoSiteConfig,
  saveSeoSiteConfig,
  type SeoSiteConfigSaveReqVO
} from '@/api/seo/siteConfig'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'SeoSiteConfig' })

const SITE_ID = 1
const storefrontUrl = String(import.meta.env.VITE_FURNITURE_WEB_URL || '')
  .trim()
  .replace(/\/+$/, '')
const message = useMessage()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const loadRequestId = ref(0)
const formRef = ref<FormInstance>()
const hasSavedConfig = ref(false)
const returnToNavigation = computed(() => route.query.returnTo === '/seo/navigation')

const createDefaultForm = (siteId = SITE_ID): SeoSiteConfigSaveReqVO => ({
  siteId,
  siteName: 'VANZ 官网',
  siteUrl: storefrontUrl,
  defaultTitleSuffix: '',
  defaultDescription: '',
  defaultRobots: 'index,follow',
  defaultOgImage: '',
  defaultLocale: 'en',
  navigationTemplate: 'VANZ_B2B'
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

const searchPreview = computed(() => {
  const siteName = formData.value.siteName.trim()
  const suffix = formData.value.defaultTitleSuffix.trim()
  return {
    title: [siteName, suffix].filter(Boolean).join(' ') || '站点标题预览',
    url: formData.value.siteUrl.trim() || 'https://www.example.com',
    description:
      formData.value.defaultDescription.trim() ||
      '配置默认描述后，搜索引擎可以更准确地理解并展示站点内容。'
  }
})

const configChecks = computed(() => [
  { label: '站点名称', done: Boolean(formData.value.siteName.trim()) },
  { label: '有效站点 URL', done: isAbsoluteHttpUrl(formData.value.siteUrl) },
  { label: '默认页面描述', done: Boolean(formData.value.defaultDescription.trim()) },
  { label: '抓取规则', done: Boolean(formData.value.defaultRobots) },
  { label: '语言区域', done: Boolean(formData.value.defaultLocale.trim()) },
  { label: '导航模板', done: Boolean(formData.value.navigationTemplate) },
  { label: '社交分享图', done: isAbsoluteHttpUrl(formData.value.defaultOgImage) }
])
const completedChecks = computed(() => configChecks.value.filter((item) => item.done).length)
const configCompletion = computed(() =>
  Math.round((completedChecks.value / configChecks.value.length) * 100)
)
const configStatus = computed(() => {
  if (loadError.value) {
    return { icon: 'ep:circle-close-filled', label: '配置加载失败', className: 'is-error' }
  }
  if (saving.value) {
    return { icon: 'ep:loading', label: '正在保存配置', className: 'is-loading' }
  }
  if (loading.value) {
    return { icon: 'ep:loading', label: '正在读取配置', className: 'is-loading' }
  }
  return { icon: 'ep:circle-check-filled', label: '配置已载入，可以编辑', className: 'is-ready' }
})

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
  siteUrl: [{ required: true, validator: validateSiteUrl, trigger: 'blur' }],
  navigationTemplate: [{ required: true, message: '请选择导航模板', trigger: 'change' }]
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
    hasSavedConfig.value = Boolean(config)
    formData.value = config
      ? { ...createDefaultForm(siteId), ...config }
      : createDefaultForm(siteId)
    formRef.value?.clearValidate()
  } catch {
    if (requestId !== loadRequestId.value || formData.value.siteId !== siteId) return
    formData.value = createDefaultForm(siteId)
    hasSavedConfig.value = false
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
    if (returnToNavigation.value) await router.push('/seo/navigation')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
.seo-site-config {
  align-items: flex-start;
}

.seo-site-config__form {
  width: 100%;
}

.seo-config-section {
  padding: 2px 0;
}

.seo-config-section__header {
  display: flex;
  margin-bottom: 18px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.seo-config-section__header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--furniture-admin-ink);
}

.seo-config-section__header p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--furniture-admin-muted);
}

.seo-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.seo-config-grid__wide {
  grid-column: 1 / -1;
}

.seo-site-config__actions {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  padding: 14px 22px;
  margin: 22px -22px -22px;
  background: rgb(255 255 255 / 96%);
  border-top: 1px solid var(--furniture-admin-border);
  box-shadow: 0 -10px 24px rgb(15 36 58 / 4%);
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  backdrop-filter: blur(8px);
}

.seo-site-config__actions > span {
  display: inline-flex;
  font-size: 12px;
  color: var(--furniture-admin-muted);
  align-items: center;
  gap: 7px;
}

.seo-site-config__actions .is-ready {
  color: var(--furniture-admin-success);
}

.seo-site-config__actions .is-error {
  color: var(--furniture-admin-error);
}

.seo-site-config__actions .is-loading {
  color: var(--furniture-admin-primary);
  animation: seo-config-spin 0.9s linear infinite;
}

.seo-site-config__aside {
  position: sticky;
  top: 14px;
}

.seo-search-preview {
  padding: 16px;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 7px;
}

.seo-search-preview span,
.seo-search-preview strong,
.seo-search-preview p {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.seo-search-preview span {
  font-size: 12px;
  color: #257342;
  white-space: nowrap;
}

.seo-search-preview strong {
  margin-top: 7px;
  font-size: 17px;
  font-weight: 500;
  line-height: 1.35;
  color: #1a0dab;
}

.seo-search-preview p {
  display: -webkit-box;
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--furniture-admin-body);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.seo-search-preview__hint {
  margin: 10px 2px 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--furniture-admin-muted);
}

.seo-config-progress > div {
  display: flex;
  margin-bottom: 10px;
  align-items: baseline;
  justify-content: space-between;
}

.seo-config-progress strong {
  font-size: 24px;
  font-weight: 650;
  color: var(--furniture-admin-ink);
}

.seo-config-progress span {
  font-size: 12px;
  color: var(--furniture-admin-muted);
}

.seo-config-checks {
  display: grid;
  margin-top: 16px;
  gap: 4px;
}

.seo-config-checks > div {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  min-height: 34px;
  padding: 7px 8px;
  color: var(--furniture-admin-body);
  border-radius: 5px;
  align-items: center;
  gap: 7px;
}

.seo-config-checks > div:hover {
  background: var(--furniture-admin-panel-soft);
}

.seo-config-checks .is-complete {
  color: var(--furniture-admin-success);
}

.seo-config-checks .is-pending {
  color: var(--furniture-admin-warning);
}

.seo-config-checks span {
  font-size: 12px;
}

.seo-config-checks small {
  font-size: 11px;
  color: var(--furniture-admin-muted);
}

@keyframes seo-config-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (width <= 1200px) {
  .seo-site-config__aside {
    position: static;
  }
}

@media (width <= 760px) {
  .seo-config-grid {
    grid-template-columns: 1fr;
  }

  .seo-config-grid__wide {
    grid-column: auto;
  }

  .seo-site-config__actions {
    padding-right: 14px;
    padding-left: 14px;
    margin-right: -14px;
    margin-left: -14px;
  }
}
</style>
