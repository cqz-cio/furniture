<template>
  <div class="website-blog-page" :class="{ 'is-editor-open': editorOpen }">
    <section class="blog-toolbar" aria-label="企业日志操作栏">
      <div class="blog-toolbar__identity">
        <div class="blog-toolbar__icon">
          <Icon icon="ep:notebook" />
        </div>
        <div>
          <div class="blog-toolbar__title">
            <strong>VANZ 企业日志</strong>
            <el-tag size="small" type="info" effect="plain">English</el-tag>
          </div>
          <p>编辑文章、管理排序与发布状态；官网只展示已发布且开启显示的内容。</p>
        </div>
      </div>

      <div class="blog-toolbar__actions">
        <div class="blog-version-summary">
          <span>草稿 {{ summary.draft }}</span>
          <span class="blog-version-summary__divider"></span>
          <span>已发布 {{ summary.published }}</span>
        </div>
        <el-button :disabled="busy" @click="openHistoryForSelected">
          <Icon icon="ep:clock" class="mr-5px" />发布记录
        </el-button>
        <el-button
          :disabled="busy || !selectedArticle"
          v-hasPermi="['seo:blog:update']"
          @click="editSelected"
        >
          <Icon icon="ep:document-checked" class="mr-5px" />保存草稿
        </el-button>
        <el-button
          type="primary"
          plain
          :loading="previewLoading"
          :disabled="busy || !selectedArticle"
          v-hasPermi="['seo:blog:preview']"
          @click="previewSelected"
        >
          <Icon icon="ep:view" class="mr-5px" />保存并预览
        </el-button>
        <el-button
          type="primary"
          :loading="publishLoading"
          :disabled="busy || !selectedArticle"
          v-hasPermi="['seo:blog:publish']"
          @click="publishSelected"
        >
          <Icon icon="ep:promotion" class="mr-5px" />发布到官网
        </el-button>
      </div>
    </section>

    <section class="blog-list-panel">
      <div class="blog-list-panel__header">
        <div>
          <h2>企业日志列表</h2>
          <p>{{ summary.total }} 篇日志 · {{ summary.published }} 篇官网已同步</p>
        </div>
        <div class="blog-list-filters">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="搜索标题或 Slug"
            class="blog-search"
            @keyup.enter="handleQuery"
            @clear="handleQuery"
          >
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部状态"
            class="blog-status-filter"
            @change="handleQuery"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
          <el-button
            type="primary"
            v-hasPermi="['seo:blog:create']"
            @click="editorRef?.open('create')"
          >
            <Icon icon="ep:plus" class="mr-5px" />新增日志
          </el-button>
        </div>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="articles"
        row-key="id"
        highlight-current-row
        class="blog-table"
        empty-text="暂无企业日志"
        @current-change="handleCurrentChange"
        @row-click="handleRowClick"
      >
        <el-table-column width="52" align="center">
          <template #default="{ row }">
            <el-radio
              :model-value="selectedArticle?.id"
              :value="row.id"
              aria-label="选择企业日志"
              @change="selectRow(row)"
            >
              <span></span>
            </el-radio>
          </template>
        </el-table-column>
        <el-table-column label="封面" :width="editorOpen ? 72 : 90">
          <template #default="{ row }">
            <img :src="resolveMediaUrl(row.coverImageUrl)" :alt="row.coverImageAlt" class="blog-cover" />
          </template>
        </el-table-column>
        <el-table-column label="标题 / Slug" :min-width="editorOpen ? 220 : 320">
          <template #default="{ row }">
            <div class="blog-title-cell">
              <strong>{{ row.title }}</strong>
              <span>/{{ row.slug }}</span>
              <small v-if="row.hasUnpublishedChanges">
                <Icon icon="ep:warning" /> 有未发布修改
              </small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" :min-width="editorOpen ? 120 : 160" />
        <el-table-column label="状态" :width="editorOpen ? 90 : 110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" :width="editorOpen ? 140 : 170">
          <template #default="{ row }">
            <span class="date-cell">{{ formatDate(row.publishedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          :width="editorOpen ? 64 : 82"
          align="center"
        />
        <el-table-column label="操作" :width="editorOpen ? 160 : 265" fixed="right">
          <template #default="{ row }">
            <div class="blog-row-actions">
              <el-button
                type="primary"
                link
                v-hasPermi="['seo:blog:update']"
                @click.stop="editorRef?.open('update', row.id)"
              >
                编辑
              </el-button>
              <el-button
                type="primary"
                link
                v-hasPermi="['seo:blog:create']"
                @click.stop="editorRef?.open('copy', row.id)"
              >
                复制
              </el-button>
              <el-dropdown trigger="click" @command="(command) => handleRowCommand(command, row)">
                <el-button link type="primary" @click.stop>
                  更多<Icon icon="ep:arrow-down" class="ml-3px" />
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="preview">
                      <Icon icon="ep:view" class="mr-6px" />预览
                    </el-dropdown-item>
                    <el-dropdown-item command="history">
                      <Icon icon="ep:clock" class="mr-6px" />发布记录
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="offline" divided>
                      <Icon icon="ep:remove" class="mr-6px" />下线
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span class="is-danger"><Icon icon="ep:delete" class="mr-6px" />删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="blog-list-panel__footer">
        <span>共 {{ total }} 条</span>
        <Pagination
          v-model:page="query.pageNo"
          v-model:limit="query.pageSize"
          :total="total"
          @pagination="loadArticles"
        />
      </div>
    </section>

    <BlogEditorDrawer
      ref="editorRef"
      :site-url="siteUrl"
      @success="refreshAll"
      @state-change="handleEditorStateChange"
    />

    <Dialog v-model="historyVisible" title="企业日志发布记录" width="720px">
      <div v-loading="historyLoading" class="history-dialog">
        <div v-if="historyArticle" class="history-dialog__article">
          <img :src="resolveMediaUrl(historyArticle.coverImageUrl)" :alt="historyArticle.coverImageAlt" />
          <div>
            <strong>{{ historyArticle.title }}</strong>
            <span>/{{ historyArticle.slug }}</span>
          </div>
        </div>
        <el-table :data="history" empty-text="该日志还没有发布记录">
          <el-table-column label="版本" width="90">
            <template #default="{ row }">v{{ row.publishedVersion }}</template>
          </el-table-column>
          <el-table-column prop="title" label="发布标题" min-width="230" />
          <el-table-column label="官网发布时间" width="170">
            <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column prop="publishedBy" label="发布人" width="100" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, nextTick, onActivated, onMounted, reactive, ref } from 'vue'
import {
  createWebsiteBlogPreviewTicket,
  deleteWebsiteBlogArticle,
  getWebsiteBlogPage,
  getWebsiteBlogPublishHistory,
  getWebsiteBlogSummary,
  offlineWebsiteBlogArticle,
  publishWebsiteBlogArticle,
  type WebsiteBlogArticle,
  type WebsiteBlogPublishRecord,
  type WebsiteBlogStatus,
  type WebsiteBlogSummary
} from '@/api/seo/blog'
import { getSeoSiteConfig } from '@/api/seo/siteConfig'
import { useMessage } from '@/hooks/web/useMessage'
import BlogEditorDrawer from './BlogEditorDrawer.vue'

defineOptions({ name: 'SeoBlog' })

const SITE_ID = 1
const LOCALE = 'en'

const message = useMessage()
const editorRef = ref<InstanceType<typeof BlogEditorDrawer>>()
const tableRef = ref()
const articles = ref<WebsiteBlogArticle[]>([])
const selectedArticle = ref<WebsiteBlogArticle>()
const loading = ref(false)
const previewLoading = ref(false)
const publishLoading = ref(false)
const total = ref(0)
const siteUrl = ref('')
const summary = ref<WebsiteBlogSummary>({ total: 0, draft: 0, published: 0, offline: 0 })
const historyVisible = ref(false)
const historyLoading = ref(false)
const history = ref<WebsiteBlogPublishRecord[]>([])
const historyArticle = ref<WebsiteBlogArticle>()
const editorOpen = ref(false)

const query = reactive<{
  pageNo: number
  pageSize: number
  keyword: string
  status?: WebsiteBlogStatus
}>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined
})

const busy = computed(() => loading.value || previewLoading.value || publishLoading.value)

const loadArticles = async () => {
  loading.value = true
  try {
    const result = await getWebsiteBlogPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      siteId: SITE_ID,
      locale: LOCALE,
      keyword: query.keyword || undefined,
      status: query.status
    })
    articles.value = result.list || []
    total.value = result.total || 0
    if (selectedArticle.value) {
      selectedArticle.value = articles.value.find((item) => item.id === selectedArticle.value?.id)
    }
    if (!selectedArticle.value && articles.value.length) {
      await nextTick()
      selectRow(articles.value[0])
    }
  } finally {
    loading.value = false
  }
}

const loadSummary = async () => {
  summary.value = await getWebsiteBlogSummary(SITE_ID, LOCALE)
}

const loadSiteConfig = async () => {
  try {
    const config = await getSeoSiteConfig(SITE_ID)
    siteUrl.value = config?.siteUrl?.trim() || ''
  } catch {
    siteUrl.value = ''
  }
}

const refreshAll = () => Promise.all([loadArticles(), loadSummary(), loadSiteConfig()])

const resolveMediaUrl = (value?: string) => {
  if (!value || /^(?:https?:|data:|blob:)/i.test(value) || !siteUrl.value) return value || ''
  try {
    return new URL(value, siteUrl.value.endsWith('/') ? siteUrl.value : `${siteUrl.value}/`).toString()
  } catch {
    return value
  }
}

const handleQuery = () => {
  query.pageNo = 1
  loadArticles()
}

const selectRow = (row: WebsiteBlogArticle) => {
  selectedArticle.value = row
  tableRef.value?.setCurrentRow(row)
}

const handleCurrentChange = (row?: WebsiteBlogArticle) => {
  if (row) selectedArticle.value = row
}

const handleRowClick = (row: WebsiteBlogArticle) => selectRow(row)

const handleEditorStateChange = (visible: boolean) => {
  editorOpen.value = visible
}

const editSelected = () => {
  if (selectedArticle.value?.id) editorRef.value?.open('update', selectedArticle.value.id)
}

const previewArticle = async (article: WebsiteBlogArticle) => {
  if (!article.id || !article.version) return
  previewLoading.value = true
  try {
    const ticket = await createWebsiteBlogPreviewTicket(article.id, article.version)
    window.open(ticket.previewUrl, '_blank', 'noopener,noreferrer')
  } finally {
    previewLoading.value = false
  }
}

const previewSelected = () => selectedArticle.value && previewArticle(selectedArticle.value)

const publishArticle = async (article: WebsiteBlogArticle) => {
  if (!article.id || !article.version) return
  await message.confirm(`确认将“${article.title}”发布到 2B 官网吗？`)
  publishLoading.value = true
  try {
    await publishWebsiteBlogArticle(article.id, article.version)
    message.success('企业日志已发布到官网')
    await refreshAll()
  } finally {
    publishLoading.value = false
  }
}

const publishSelected = () => selectedArticle.value && publishArticle(selectedArticle.value)

const offlineArticle = async (article: WebsiteBlogArticle) => {
  if (!article.id || !article.version) return
  await message.confirm(`下线后官网将不再展示“${article.title}”，确认继续吗？`)
  await offlineWebsiteBlogArticle(article.id, article.version)
  message.success('企业日志已下线')
  await refreshAll()
}

const deleteArticle = async (article: WebsiteBlogArticle) => {
  if (!article.id) return
  await message.confirm(`删除“${article.title}”后不可恢复，确认继续吗？`)
  await deleteWebsiteBlogArticle(article.id)
  message.success('企业日志已删除')
  if (selectedArticle.value?.id === article.id) selectedArticle.value = undefined
  await refreshAll()
}

const openHistory = async (article: WebsiteBlogArticle) => {
  if (!article.id) return
  historyVisible.value = true
  historyArticle.value = article
  historyLoading.value = true
  try {
    history.value = await getWebsiteBlogPublishHistory(article.id)
  } finally {
    historyLoading.value = false
  }
}

const openHistoryForSelected = () => {
  if (!selectedArticle.value) {
    message.info('请先选择一篇企业日志')
    return
  }
  openHistory(selectedArticle.value)
}

const handleRowCommand = (command: string, article: WebsiteBlogArticle) => {
  selectRow(article)
  if (command === 'preview') previewArticle(article)
  if (command === 'history') openHistory(article)
  if (command === 'offline') offlineArticle(article)
  if (command === 'delete') deleteArticle(article)
}

const formatDate = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—')

const statusLabel = (status: WebsiteBlogStatus) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' })[status]

const statusTagType = (status: WebsiteBlogStatus) =>
  ({ DRAFT: 'info', PUBLISHED: 'success', OFFLINE: 'warning' })[status] as
    | 'info'
    | 'success'
    | 'warning'

onMounted(refreshAll)
onActivated(refreshAll)
</script>

<style scoped lang="scss">
.website-blog-page {
  box-sizing: border-box;
  min-height: calc(100vh - var(--top-tool-height) - var(--tags-view-height) - 44px);
  padding: 0 2px 28px;
  color: #172033;
  transition: padding-right 180ms ease;
}

.blog-toolbar,
.blog-list-panel {
  background: #fff;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(17 32 57 / 3%);
}

.blog-toolbar {
  display: flex;
  gap: 22px;
  align-items: center;
  justify-content: space-between;
  min-height: 78px;
  padding: 15px 18px;
}

.blog-toolbar__identity {
  display: flex;
  gap: 13px;
  align-items: center;
  min-width: 260px;
}

.blog-toolbar__icon {
  display: grid;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  color: #176bdb;
  font-size: 20px;
  background: #edf5ff;
  border-radius: 7px;
  place-items: center;
}

.blog-toolbar__title {
  display: flex;
  gap: 9px;
  align-items: center;
}

.blog-toolbar__identity p {
  margin: 5px 0 0;
  color: #778397;
  font-size: 12px;
}

.blog-toolbar__actions {
  display: flex;
  gap: 7px;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.blog-toolbar__actions :deep(.el-button) {
  padding-right: 12px;
  padding-left: 12px;
}

.blog-version-summary {
  display: flex;
  gap: 7px;
  align-items: center;
  height: 32px;
  padding: 0 9px;
  color: #6f7d91;
  font-size: 12px;
  background: #f7f9fb;
  border: 1px solid #e3e8ee;
  border-radius: 4px;
}

.blog-version-summary__divider {
  width: 1px;
  height: 13px;
  background: #d7dee6;
}

.blog-list-panel {
  margin-top: 16px;
  overflow: hidden;
}

.blog-list-panel__header {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  min-height: 78px;
  padding: 16px 18px;
  border-bottom: 1px solid #e3e8ee;
}

.blog-list-panel__header h2,
.blog-list-panel__header p {
  margin: 0;
}

.blog-list-panel__header h2 {
  font-size: 16px;
}

.blog-list-panel__header p {
  margin-top: 6px;
  color: #8290a3;
  font-size: 12px;
}

.blog-list-filters {
  display: flex;
  gap: 10px;
  align-items: center;
}

.blog-search {
  width: 270px;
}

.blog-status-filter {
  width: 170px;
}

.blog-table {
  width: 100%;
}

.blog-table :deep(.el-table__header th) {
  height: 48px;
  color: #3d4b60;
  font-size: 12px;
  font-weight: 700;
  background: #fafbfd;
}

.blog-table :deep(.el-table__row td) {
  height: 80px;
}

.blog-table :deep(.current-row > td.el-table__cell) {
  background: #f4f8fe;
}

.blog-table :deep(.el-radio__label) {
  display: none;
}

.blog-cover {
  display: block;
  width: 58px;
  height: 42px;
  object-fit: cover;
  border: 1px solid #dfe5eb;
  border-radius: 5px;
}

.blog-title-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.blog-title-cell strong {
  color: #223047;
  font-size: 13px;
  font-weight: 600;
}

.blog-title-cell span {
  color: #8a96a8;
  font-size: 12px;
}

.blog-title-cell small {
  display: flex;
  gap: 4px;
  align-items: center;
  color: #b7791f;
  font-size: 11px;
}

.date-cell {
  color: #56657a;
  font-variant-numeric: tabular-nums;
}

.blog-row-actions {
  display: flex;
  gap: 3px;
  align-items: center;
}

.blog-list-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 8px 18px;
  color: #7d899a;
  font-size: 12px;
  border-top: 1px solid #e6ebf0;
}

.blog-list-panel__footer :deep(.pagination-container) {
  margin: 0;
}

.history-dialog__article {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
  margin-bottom: 14px;
  background: #f7f9fb;
  border: 1px solid #e3e8ee;
  border-radius: 6px;
}

.history-dialog__article img {
  width: 64px;
  height: 44px;
  object-fit: cover;
  border-radius: 4px;
}

.history-dialog__article div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.history-dialog__article span {
  color: #8290a3;
  font-size: 12px;
}

.is-danger {
  display: inline-flex;
  align-items: center;
  color: var(--el-color-danger);
}

@media (max-width: 1440px) {
  .blog-toolbar {
    align-items: flex-start;
  }

  .blog-toolbar__actions {
    max-width: 650px;
  }
}

@media (min-width: 1500px) {
  .website-blog-page.is-editor-open {
    padding-right: 582px;
  }

  .website-blog-page.is-editor-open .blog-toolbar {
    gap: 12px;
    padding-right: 14px;
    padding-left: 14px;
  }

  .website-blog-page.is-editor-open .blog-toolbar__identity {
    flex: 1 1 auto;
    min-width: 220px;
  }

  .website-blog-page.is-editor-open .blog-toolbar__actions {
    flex: 0 0 auto;
    gap: 5px;
    flex-wrap: nowrap;
  }

  .website-blog-page.is-editor-open .blog-toolbar__actions :deep(.el-button) {
    padding-right: 9px;
    padding-left: 9px;
  }

  .website-blog-page.is-editor-open .blog-version-summary {
    padding-right: 7px;
    padding-left: 7px;
  }
}

@media (max-width: 1100px) {
  .blog-toolbar,
  .blog-list-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .blog-toolbar__actions,
  .blog-list-filters {
    justify-content: flex-start;
  }

  .blog-search {
    width: min(100%, 360px);
  }
}
</style>
