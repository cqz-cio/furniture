<template>
  <el-drawer
    v-model="drawerVisible"
    class="blog-editor-drawer"
    size="min(580px, 100vw)"
    :modal="false"
    :with-header="false"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="blog-editor">
      <header class="blog-editor__header">
        <div class="blog-editor__heading">
          <div class="blog-editor__title-row">
            <h2>{{ formMode === 'create' ? '新增企业日志' : '编辑企业日志' }}</h2>
            <el-tag :type="statusTagType(form.status)" effect="plain" size="small">
              {{ statusLabel(form.status) }}
            </el-tag>
            <el-tag v-if="form.hasUnpublishedChanges" type="warning" effect="plain" size="small">
              有未发布修改
            </el-tag>
          </div>
          <p>结构化维护官网 Blog 卡片、正文与 SEO；发布前可在真实 2B 页面预览。</p>
        </div>
        <el-button text circle aria-label="关闭企业日志编辑器" @click="closeDrawer">
          <Icon icon="ep:close" />
        </el-button>
      </header>

      <el-tabs v-model="activeTab" class="blog-editor__tabs">
        <el-tab-pane label="基础信息" name="basic" />
        <el-tab-pane label="正文内容" name="content" />
        <el-tab-pane label="SEO 设置" name="seo" />
      </el-tabs>

      <el-scrollbar class="blog-editor__scrollbar">
        <el-form
          ref="formRef"
          v-loading="loading"
          :model="form"
          :rules="formRules"
          label-position="top"
          class="blog-editor__form"
        >
          <section v-show="activeTab === 'basic'" class="editor-panel">
            <div class="field-kicker">官网卡片预览</div>
            <article class="blog-card-preview" :class="{ 'is-empty': !form.coverImageUrl }">
              <img
                v-if="form.coverImageUrl"
                :src="resolveMediaUrl(form.coverImageUrl)"
                :alt="form.coverImageAlt || form.title"
              />
              <div v-else class="blog-card-preview__empty">
                <Icon icon="ep:picture" />
                <span>上传封面后在此预览官网卡片</span>
              </div>
              <div class="blog-card-preview__shade"></div>
              <div class="blog-card-preview__content">
                <span>{{ form.label || 'FIELD NOTE' }}</span>
                <h3>{{ form.title || 'Your journal title' }}</h3>
                <small>{{ form.category || 'Journal' }}</small>
              </div>
            </article>

            <div class="form-grid form-grid--two">
              <el-form-item label="文章标题" prop="title">
                <el-input
                  v-model="form.title"
                  maxlength="180"
                  show-word-limit
                  placeholder="例如：5 Quick Steps to Double Your Bedroom Space"
                  @blur="fillSeoDefaults"
                />
              </el-form-item>
              <el-form-item label="URL Slug" prop="slug">
                <el-input
                  v-model="form.slug"
                  maxlength="140"
                  placeholder="5-quick-steps-to-double-your-bedroom-space"
                  @input="slugTouched = true"
                >
                  <template #prepend>/blog/</template>
                </el-input>
              </el-form-item>
            </div>

            <div class="form-grid form-grid--two">
              <el-form-item label="分类" prop="category">
                <el-input v-model="form.category" maxlength="80" placeholder="Bedroom planning" />
              </el-form-item>
              <el-form-item label="标签" prop="label">
                <el-input v-model="form.label" maxlength="80" placeholder="Small-space guide" />
              </el-form-item>
            </div>

            <el-form-item label="摘要" prop="summary">
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="3"
                maxlength="600"
                show-word-limit
                placeholder="用于 Blog 卡片、正文导语与默认 SEO 描述。"
                @blur="fillSeoDefaults"
              />
            </el-form-item>

            <div class="title-lines-editor">
              <div class="title-lines-editor__head">
                <div>
                  <strong>大标题换行</strong>
                  <span>最多 3 行；留空时系统使用完整文章标题。</span>
                </div>
                <el-button
                  v-if="form.titleLines.length < 3"
                  size="small"
                  plain
                  @click="form.titleLines.push('')"
                >
                  <Icon icon="ep:plus" class="mr-4px" />新增一行
                </el-button>
              </div>
              <div v-if="form.titleLines.length" class="title-lines-editor__list">
                <div v-for="(_, index) in form.titleLines" :key="index" class="inline-field-row">
                  <span>{{ index + 1 }}</span>
                  <el-input
                    v-model="form.titleLines[index]"
                    maxlength="100"
                    :placeholder="`标题第 ${index + 1} 行`"
                  />
                  <el-button
                    text
                    type="danger"
                    aria-label="删除标题行"
                    @click="form.titleLines.splice(index, 1)"
                  >
                    <Icon icon="ep:delete" />
                  </el-button>
                </div>
              </div>
            </div>

            <div class="media-grid">
              <el-form-item label="封面图片" prop="coverImageUrl">
                <div class="media-upload-row">
                  <UploadImg
                    v-model="form.coverImageUrl"
                    width="156px"
                    height="92px"
                    :limit="1"
                    :is-show-tip="false"
                    directory="website-blog-cover"
                  />
                  <div class="media-upload-row__note">
                    <strong>建议尺寸 1200 × 675</strong>
                    <span>支持 JPG、PNG、WEBP，卡片按 16:9 裁切。</span>
                  </div>
                </div>
              </el-form-item>
              <el-form-item label="封面替代文本" prop="coverImageAlt">
                <el-input
                  v-model="form.coverImageAlt"
                  type="textarea"
                  :rows="3"
                  maxlength="240"
                  placeholder="准确描述图片内容，用于无障碍和搜索。"
                />
              </el-form-item>
            </div>

            <div class="media-grid">
              <el-form-item label="正文头图（可选）">
                <div class="media-upload-row">
                  <UploadImg
                    v-model="form.heroImageUrl"
                    width="156px"
                    height="92px"
                    :limit="1"
                    :is-show-tip="false"
                    directory="website-blog-hero"
                  />
                  <div class="media-upload-row__note">
                    <strong>未上传时使用封面图</strong>
                    <span>用于文章详情页首屏大图。</span>
                  </div>
                </div>
              </el-form-item>
              <div class="publish-fields">
                <el-form-item label="发布时间">
                  <el-date-picker
                    v-model="form.publishedAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ss"
                    format="YYYY-MM-DD HH:mm"
                    placeholder="发布时自动填入当前时间"
                    class="w-100%"
                  />
                </el-form-item>
                <el-form-item label="排序">
                  <el-input-number
                    v-model="form.sortOrder"
                    :min="-100000"
                    :max="100000"
                    controls-position="right"
                    class="w-100%"
                  />
                </el-form-item>
              </div>
            </div>

            <div class="visibility-row">
              <div>
                <strong>官网显示</strong>
                <span>关闭后再次发布，该日志不会出现在官网列表与详情接口中。</span>
              </div>
              <el-switch v-model="form.visible" />
            </div>

            <el-form-item label="旧版兼容地址（可选）">
              <el-input
                v-model="form.legacyPath"
                maxlength="220"
                placeholder="/5-quick-steps-to-double-your-bedroom-space/"
              />
            </el-form-item>

            <div class="sync-note">
              <Icon icon="ep:lock" />
              <span>发布后约数秒同步至 2B 官网 Blog 列表与详情页；保存草稿不会覆盖线上版本。</span>
            </div>
          </section>

          <section v-show="activeTab === 'content'" class="editor-panel">
            <div class="content-intro">
              <div>
                <div class="field-kicker">结构化正文</div>
                <h3>章节与段落</h3>
                <p>章节序号按顺序自动生成；正文只保存纯文本，官网不执行任意 HTML。</p>
              </div>
              <el-button type="primary" plain @click="addSection">
                <Icon icon="ep:plus" class="mr-5px" />新增章节
              </el-button>
            </div>

            <el-empty
              v-if="form.sections.length === 0"
              :image-size="72"
              description="还没有正文；点击“新增章节”开始编写。"
            />

            <draggable
              v-else
              v-model="form.sections"
              item-key="id"
              handle=".section-card__handle"
              ghost-class="section-card--ghost"
              :animation="180"
              class="section-list"
            >
              <template #item="{ element, index }">
                <article class="section-card">
                  <div class="section-card__head">
                    <button
                      type="button"
                      class="section-card__handle"
                      aria-label="拖动调整章节顺序"
                    >
                      <Icon icon="ep:rank" />
                    </button>
                    <span class="section-card__number">{{ sectionNumber(index) }}</span>
                    <el-input
                      v-model="element.title"
                      maxlength="160"
                      placeholder="章节标题"
                      class="section-card__title"
                    />
                    <el-button
                      type="danger"
                      text
                      aria-label="删除章节"
                      @click="removeSection(index)"
                    >
                      <Icon icon="ep:delete" />
                    </el-button>
                  </div>

                  <div class="paragraph-list">
                    <div
                      v-for="(_, paragraphIndex) in element.paragraphs"
                      :key="paragraphIndex"
                      class="paragraph-row"
                    >
                      <span>{{ Number(paragraphIndex) + 1 }}</span>
                      <el-input
                        v-model="element.paragraphs[paragraphIndex]"
                        type="textarea"
                        :rows="4"
                        maxlength="4000"
                        show-word-limit
                        placeholder="输入纯文本段落…"
                      />
                      <el-button
                        type="danger"
                        text
                        aria-label="删除段落"
                        @click="removeParagraph(element, Number(paragraphIndex))"
                      >
                        <Icon icon="ep:close" />
                      </el-button>
                    </div>
                  </div>
                  <el-button plain size="small" @click="element.paragraphs.push('')">
                    <Icon icon="ep:plus" class="mr-4px" />新增段落
                  </el-button>
                </article>
              </template>
            </draggable>

            <div class="content-metrics">
              <div>
                <span>章节</span>
                <strong>{{ form.sections.length }}</strong>
              </div>
              <div>
                <span>段落</span>
                <strong>{{ paragraphCount }}</strong>
              </div>
              <div>
                <span>预计阅读</span>
                <strong>{{ estimatedReadTime }}</strong>
              </div>
            </div>
          </section>

          <section v-show="activeTab === 'seo'" class="editor-panel">
            <div class="seo-preview">
              <span>{{ seoPreviewUrl }}</span>
              <h3>{{ form.seoTitle || form.title || 'Blog title — VANZ Journal' }}</h3>
              <p>{{ form.seoDescription || form.summary || 'Search description preview.' }}</p>
            </div>

            <el-form-item label="SEO 标题">
              <el-input
                v-model="form.seoTitle"
                maxlength="180"
                show-word-limit
                :placeholder="`${form.title || 'Blog title'} — VANZ Journal`"
              />
            </el-form-item>
            <el-form-item label="SEO 描述">
              <el-input
                v-model="form.seoDescription"
                type="textarea"
                :rows="5"
                maxlength="320"
                show-word-limit
                placeholder="未填写时自动使用文章摘要。"
              />
            </el-form-item>

            <div class="seo-guidance">
              <Icon icon="ep:info-filled" />
              <div>
                <strong>发布检查</strong>
                <span>建议 SEO 标题不超过 60 个英文字符，描述保持在 120–160 个字符。</span>
              </div>
            </div>
          </section>
        </el-form>
      </el-scrollbar>

      <footer class="blog-editor__footer">
        <el-button :disabled="busy" @click="closeDrawer">取消</el-button>
        <div class="blog-editor__footer-actions">
          <el-button
            :loading="saving"
            :disabled="busy"
            v-hasPermi="['seo:blog:update', 'seo:blog:create']"
            @click="saveDraft"
          >
            <Icon icon="ep:document-checked" class="mr-5px" />保存草稿
          </el-button>
          <el-button
            type="primary"
            plain
            :loading="previewing"
            :disabled="busy"
            v-hasPermi="['seo:blog:preview']"
            @click="saveAndPreview"
          >
            <Icon icon="ep:view" class="mr-5px" />保存并预览
          </el-button>
          <el-button
            type="primary"
            :loading="publishing"
            :disabled="busy"
            v-hasPermi="['seo:blog:publish']"
            @click="publishArticle"
          >
            <Icon icon="ep:promotion" class="mr-5px" />发布到官网
          </el-button>
        </div>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, ref, watch } from 'vue'
import draggable from 'vuedraggable'
import { UploadImg } from '@/components/UploadFile'
import {
  createWebsiteBlogArticle,
  createWebsiteBlogPreviewTicket,
  getWebsiteBlogArticle,
  publishWebsiteBlogArticle,
  updateWebsiteBlogArticle,
  type WebsiteBlogArticle,
  type WebsiteBlogArticleSaveReqVO,
  type WebsiteBlogSection,
  type WebsiteBlogStatus
} from '@/api/seo/blog'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'SeoBlogEditorDrawer' })

const props = defineProps<{ siteUrl?: string }>()
const emit = defineEmits<{ success: []; stateChange: [visible: boolean, articleId?: number] }>()

const SITE_ID = 1
const LOCALE = 'en'

const message = useMessage()
const drawerVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const publishing = ref(false)
const activeTab = ref('basic')
const formMode = ref<'create' | 'update'>('create')
const slugTouched = ref(false)
const formRef = ref()

const emptyForm = (): WebsiteBlogArticle => ({
  siteId: SITE_ID,
  locale: LOCALE,
  slug: '',
  legacyPath: '',
  title: '',
  titleLines: [],
  category: '',
  label: '',
  summary: '',
  coverImageUrl: '',
  coverImageAlt: '',
  heroImageUrl: '',
  sections: [],
  status: 'DRAFT',
  visible: true,
  publishedAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
  sortOrder: 100,
  seoTitle: '',
  seoDescription: ''
})

const form = ref<WebsiteBlogArticle>(emptyForm())

const formRules = {
  title: [{ required: true, message: '请输入文章标题', trigger: 'blur' }],
  slug: [
    { required: true, message: '请输入 URL Slug', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: 'Slug 仅支持小写字母、数字和连字符',
      trigger: 'blur'
    }
  ],
  category: [{ required: true, message: '请输入分类', trigger: 'blur' }],
  label: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入摘要', trigger: 'blur' }],
  coverImageUrl: [{ required: true, message: '请上传封面图片', trigger: 'change' }],
  coverImageAlt: [{ required: true, message: '请输入封面替代文本', trigger: 'blur' }]
}

const busy = computed(() => loading.value || saving.value || previewing.value || publishing.value)

const paragraphCount = computed(() =>
  form.value.sections.reduce((count, section) => count + section.paragraphs.length, 0)
)

const wordCount = computed(() => {
  const parts = [
    form.value.summary,
    ...form.value.sections.flatMap((section) => [section.title, ...section.paragraphs])
  ]
  return parts.join(' ').match(/[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*/g)?.length || 0
})

const estimatedReadTime = computed(() => `${Math.max(1, Math.ceil(wordCount.value / 180))} min`)
const resolveMediaUrl = (value?: string) => {
  if (!value || /^(?:https?:|data:|blob:)/i.test(value) || !props.siteUrl) return value || ''
  try {
    return new URL(value, props.siteUrl.endsWith('/') ? props.siteUrl : `${props.siteUrl}/`).toString()
  } catch {
    return value
  }
}
const seoPreviewUrl = computed(
  () => `vanz.com${form.value.legacyPath || `/blog/${form.value.slug || 'article-slug'}`}`
)

watch(
  () => form.value.title,
  (title) => {
    if (formMode.value === 'create' && !slugTouched.value) {
      form.value.slug = slugify(title)
    }
  }
)

watch(drawerVisible, (visible) => emit('stateChange', visible, form.value.id))

const open = async (mode: 'create' | 'update' | 'copy', articleId?: number) => {
  drawerVisible.value = true
  activeTab.value = 'basic'
  formMode.value = mode === 'update' ? 'update' : 'create'
  slugTouched.value = mode !== 'create'
  form.value = emptyForm()
  formRef.value?.clearValidate()
  if (!articleId) return

  loading.value = true
  try {
    const article = await getWebsiteBlogArticle(articleId)
    form.value = JSON.parse(JSON.stringify(article))
    if (mode === 'copy') {
      form.value.id = undefined
      form.value.version = undefined
      form.value.status = 'DRAFT'
      form.value.publishedVersion = undefined
      form.value.hasUnpublishedChanges = false
      form.value.lastPublishedTime = undefined
      form.value.publishedBy = undefined
      form.value.title = `${article.title} Copy`
      form.value.slug = `${article.slug}-copy`
      form.value.legacyPath = ''
      form.value.publishedAt = dayjs().format('YYYY-MM-DDTHH:mm:ss')
    }
  } finally {
    loading.value = false
  }
}

defineExpose({ open })

const closeDrawer = () => {
  if (!busy.value) drawerVisible.value = false
}

const addSection = () => {
  form.value.sections.push({
    id: `section-${Date.now()}`,
    title: '',
    paragraphs: ['']
  })
}

const removeSection = async (index: number) => {
  await message.confirm('确认删除这个章节及其全部段落吗？')
  form.value.sections.splice(index, 1)
}

const removeParagraph = (section: WebsiteBlogSection, index: number) => {
  section.paragraphs.splice(index, 1)
  if (section.paragraphs.length === 0) section.paragraphs.push('')
}

const sectionNumber = (index: number) => String(index + 1).padStart(2, '0')

const fillSeoDefaults = () => {
  if (!form.value.seoTitle && form.value.title) {
    form.value.seoTitle = `${form.value.title} — VANZ Journal`
  }
  if (!form.value.seoDescription && form.value.summary) {
    form.value.seoDescription = form.value.summary
  }
}

const normalizePayload = (): WebsiteBlogArticleSaveReqVO => {
  fillSeoDefaults()
  const sections = form.value.sections
    .map((section, index) => ({
      id: section.id || `section-${index + 1}`,
      title: section.title.trim(),
      paragraphs: section.paragraphs.map((paragraph) => paragraph.trim()).filter(Boolean)
    }))
    .filter((section) => section.title || section.paragraphs.length)
  return {
    id: form.value.id,
    version: form.value.version,
    siteId: SITE_ID,
    locale: LOCALE,
    slug: form.value.slug.trim().toLowerCase(),
    legacyPath: form.value.legacyPath?.trim() || '',
    title: form.value.title.trim(),
    titleLines: form.value.titleLines.map((line) => line.trim()).filter(Boolean),
    category: form.value.category.trim(),
    label: form.value.label.trim(),
    summary: form.value.summary.trim(),
    coverImageUrl: form.value.coverImageUrl,
    coverImageAlt: form.value.coverImageAlt.trim(),
    heroImageUrl: form.value.heroImageUrl || '',
    sections,
    visible: form.value.visible,
    publishedAt: form.value.publishedAt,
    sortOrder: form.value.sortOrder || 0,
    seoTitle: form.value.seoTitle.trim(),
    seoDescription: form.value.seoDescription.trim()
  }
}

const validateBeforeSave = async (requireBody: boolean) => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    activeTab.value = 'basic'
    return false
  }
  const normalized = normalizePayload()
  const incompleteSection = normalized.sections.find(
    (section) => !section.title || section.paragraphs.length === 0
  )
  if (incompleteSection) {
    activeTab.value = 'content'
    message.warning('每个已添加的章节都需要标题和至少一个正文段落')
    return false
  }
  if (requireBody && normalized.sections.length === 0) {
    activeTab.value = 'content'
    message.warning('发布前至少需要一个完整章节')
    return false
  }
  return true
}

const persist = async (showSuccess = true, requireBody = false) => {
  if (!(await validateBeforeSave(requireBody))) return undefined
  const payload = normalizePayload()
  saving.value = true
  try {
    let articleId = form.value.id
    if (articleId && form.value.version) {
      await updateWebsiteBlogArticle(payload)
    } else {
      articleId = await createWebsiteBlogArticle(payload)
    }
    if (!articleId) return undefined
    form.value = await getWebsiteBlogArticle(articleId)
    formMode.value = 'update'
    slugTouched.value = true
    if (showSuccess) message.success('企业日志草稿已保存')
    emit('success')
    return form.value
  } finally {
    saving.value = false
  }
}

const saveDraft = () => persist(true, false)

const saveAndPreview = async () => {
  previewing.value = true
  try {
    const saved = await persist(false, false)
    if (!saved?.id || !saved.version) return
    const ticket = await createWebsiteBlogPreviewTicket(saved.id, saved.version)
    window.open(ticket.previewUrl, '_blank', 'noopener,noreferrer')
    message.success(`预览已生成，${Math.floor(ticket.expiresInSeconds / 60)} 分钟内有效`)
  } finally {
    previewing.value = false
  }
}

const publishArticle = async () => {
  publishing.value = true
  try {
    const saved = await persist(false, true)
    if (!saved?.id || !saved.version) return
    await message.confirm('发布后将立即替换 2B 官网 Blog 的线上内容，确认继续吗？')
    await publishWebsiteBlogArticle(saved.id, saved.version)
    form.value = await getWebsiteBlogArticle(saved.id)
    message.success('企业日志已发布到官网')
    emit('success')
  } finally {
    publishing.value = false
  }
}

const slugify = (value: string) =>
  String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 140)

const statusLabel = (status: WebsiteBlogStatus) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' })[status]

const statusTagType = (status: WebsiteBlogStatus) =>
  ({ DRAFT: 'info', PUBLISHED: 'success', OFFLINE: 'warning' })[status] as
    | 'info'
    | 'success'
    | 'warning'
</script>

<style scoped lang="scss">
:global(.blog-editor-drawer) {
  border-left: 1px solid #dfe5eb;
  box-shadow: -16px 0 40px rgb(23 32 51 / 12%);
}

:global(.blog-editor-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

.blog-editor {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  height: 100%;
  color: #172033;
  background: #fff;
}

.blog-editor__header {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px 26px 14px;
  border-bottom: 1px solid #edf0f3;
}

.blog-editor__heading h2,
.blog-editor__heading p {
  margin: 0;
}

.blog-editor__heading p {
  margin-top: 7px;
  color: #778397;
  font-size: 13px;
}

.blog-editor__title-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.blog-editor__title-row h2 {
  font-size: 18px;
  line-height: 1.4;
}

.blog-editor__tabs {
  padding: 0 26px;
  border-bottom: 1px solid #e6ebf0;
}

.blog-editor__tabs :deep(.el-tabs__header) {
  margin: 0;
}

.blog-editor__tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.blog-editor__scrollbar {
  min-height: 0;
}

.blog-editor__form {
  padding: 22px 26px 34px;
}

.editor-panel {
  display: grid;
  gap: 20px;
}

.field-kicker {
  color: #49576a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.blog-card-preview {
  position: relative;
  min-height: 205px;
  overflow: hidden;
  color: #fff;
  background: #253142;
  border-radius: 8px;
}

.blog-card-preview img {
  width: 100%;
  height: 205px;
  object-fit: cover;
}

.blog-card-preview__shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgb(0 0 0 / 72%) 0%, rgb(0 0 0 / 18%) 72%);
}

.blog-card-preview__content {
  position: absolute;
  right: 28px;
  bottom: 24px;
  left: 28px;
  z-index: 1;
}

.blog-card-preview__content span,
.blog-card-preview__content small {
  display: block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.blog-card-preview__content h3 {
  max-width: 440px;
  margin: 8px 0 5px;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 28px;
  font-weight: 500;
  line-height: 1.06;
}

.blog-card-preview__empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  color: #aeb8c5;
}

.blog-card-preview__empty :deep(svg) {
  width: 36px;
  height: 36px;
}

.form-grid {
  display: grid;
  gap: 18px;
}

.form-grid--two,
.media-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.media-grid {
  display: grid;
  gap: 22px;
  align-items: start;
}

.media-upload-row {
  display: flex;
  gap: 14px;
  align-items: center;
}

.media-upload-row__note {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: #8792a4;
  font-size: 12px;
  line-height: 1.5;
}

.media-upload-row__note strong {
  color: #455268;
}

.publish-fields {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(110px, 0.65fr);
  gap: 12px;
}

.title-lines-editor,
.visibility-row,
.sync-note,
.seo-guidance,
.content-metrics {
  border: 1px solid #e2e7ed;
  border-radius: 6px;
}

.title-lines-editor {
  padding: 15px;
  background: #fafbfd;
}

.title-lines-editor__head,
.visibility-row {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
}

.title-lines-editor__head div,
.visibility-row div,
.seo-guidance div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-lines-editor__head span,
.visibility-row span,
.seo-guidance span {
  color: #7b8799;
  font-size: 12px;
  line-height: 1.5;
}

.title-lines-editor__list {
  display: grid;
  gap: 9px;
  margin-top: 12px;
}

.inline-field-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 32px;
  gap: 8px;
  align-items: center;
}

.inline-field-row > span {
  color: #8792a4;
  font-size: 12px;
  text-align: center;
}

.visibility-row {
  padding: 14px 16px;
}

.sync-note,
.seo-guidance {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 13px 15px;
  color: #45627f;
  font-size: 12px;
  line-height: 1.5;
  background: #f2f7fd;
  border-color: #d9e8f9;
}

.content-intro {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
}

.content-intro h3,
.content-intro p {
  margin: 0;
}

.content-intro h3 {
  margin-top: 6px;
  font-size: 19px;
}

.content-intro p {
  margin-top: 6px;
  color: #7c8797;
  font-size: 13px;
}

.section-list {
  display: grid;
  gap: 14px;
}

.section-card {
  padding: 16px;
  background: #fafbfd;
  border: 1px solid #dfe5eb;
  border-radius: 7px;
}

.section-card--ghost {
  opacity: 0.45;
}

.section-card__head {
  display: grid;
  grid-template-columns: 30px 40px minmax(0, 1fr) 32px;
  gap: 8px;
  align-items: center;
}

.section-card__handle {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0;
  color: #8b96a7;
  cursor: grab;
  background: transparent;
  border: 0;
  place-items: center;
}

.section-card__number {
  color: #176bdb;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 18px;
}

.paragraph-list {
  display: grid;
  gap: 12px;
  margin: 14px 0 12px 78px;
}

.paragraph-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 32px;
  gap: 8px;
  align-items: flex-start;
}

.paragraph-row > span {
  padding-top: 9px;
  color: #8792a4;
  font-size: 12px;
  text-align: center;
}

.section-card > .el-button {
  margin-left: 78px;
}

.content-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  overflow: hidden;
}

.content-metrics > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  padding: 14px;
  border-right: 1px solid #e2e7ed;
}

.content-metrics > div:last-child {
  border-right: 0;
}

.content-metrics span {
  color: #8390a3;
  font-size: 12px;
}

.content-metrics strong {
  color: #243248;
  font-size: 18px;
}

.seo-preview {
  padding: 18px;
  background: #fafbfd;
  border: 1px solid #e0e5eb;
  border-radius: 7px;
}

.seo-preview span {
  color: #188038;
  font-size: 12px;
}

.seo-preview h3 {
  margin: 7px 0 5px;
  color: #1a0dab;
  font-size: 19px;
  font-weight: 500;
}

.seo-preview p {
  margin: 0;
  color: #515c6d;
  font-size: 13px;
  line-height: 1.55;
}

.blog-editor__footer {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #fff;
  border-top: 1px solid #dfe5eb;
  box-shadow: 0 -8px 24px rgb(23 32 51 / 5%);
}

.blog-editor__footer-actions {
  display: flex;
  gap: 10px;
}

.blog-editor__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.blog-editor__form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #425067;
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 760px) {
  .form-grid--two,
  .media-grid,
  .publish-fields {
    grid-template-columns: 1fr;
  }

  .blog-editor__footer {
    align-items: stretch;
  }

  .blog-editor__footer-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
