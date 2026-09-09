<template>
  <div class="website-code-page">
    <div class="code-breadcrumb">网站管理（CMS） / 统计与广告代码</div>
    <header class="code-heading">
      <div>
        <h1>统计与广告代码</h1>
        <p>为当前网站配置广告追踪、访问统计和站点验证代码</p>
      </div>
      <el-button :disabled="busy || !draft" @click="openHistory">
        <Icon icon="ep:clock" class="mr-6px" />修改记录
      </el-button>
    </header>

    <div class="code-site-context">
      <Icon icon="ep:monitor" />
      <span>当前网站：<strong>{{ draft?.siteName || '加载中' }}</strong></span>
      <span class="context-dot">·</span><span>全站生效</span>
      <span v-if="draft?.siteUrl" class="site-domain">{{ draft.siteUrl }}</span>
    </div>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button link type="primary" @click="loadDraft">重新加载</el-button>
    </el-alert>
    <div v-loading="loading" class="code-workspace">
      <section class="code-panel" aria-label="网站代码编辑">
        <el-tabs v-model="position" class="code-tabs">
          <el-tab-pane v-for="item in positions" :key="item.key" :name="item.key" :label="item.label" />
        </el-tabs>
        <div class="code-panel-inner">
          <div class="code-placement-row">
            <p class="code-placement">{{ selected.description }}</p>
            <div class="code-toggle-row">
              <span>{{ selected.toggle }}</span>
              <el-switch v-model="content[position].enabled" :disabled="busy || !draft || !canEdit" :aria-label="selected.toggle" />
            </div>
          </div>
          <div class="snippet-editor" :class="{ 'is-readonly': !canEdit }">
            <div class="line-numbers" aria-hidden="true">
              <div :style="{ transform: 'translateY(-' + scrollTop + 'px)' }">
                <span v-for="line in lineCount" :key="line">{{ line }}</span>
              </div>
            </div>
            <div class="snippet-input-wrap">
              <pre aria-hidden="true" :style="{ transform: 'translate(-' + scrollLeft + 'px, -' + scrollTop + 'px)' }"><code v-html="highlighted"></code></pre>
              <textarea
                :key="position"
                v-model="content[position].code"
                :aria-label="selected.label + '代码'"
                :readonly="busy || !draft || !canEdit"
                maxlength="100000"
                spellcheck="false"
                autocapitalize="off"
                autocomplete="off"
                wrap="off"
                placeholder="在这里粘贴平台提供的完整代码"
                @scroll="syncScroll"
              ></textarea>
            </div>
          </div>
          <div class="editor-caption">
            <span>支持 HTML / JavaScript / CSS，保存时保留原始代码</span>
            <span>{{ content[position].code.length.toLocaleString() }} / 100,000</span>
          </div>
          <div class="code-actions">
            <span class="draft-status">
              <Icon :icon="dirty ? 'ep:edit-pen' : 'ep:circle-check'" />
              {{ draftStatus }}
            </span>
            <div>
              <el-button v-if="canEdit" :disabled="busy || !draft || !dirty" :loading="saving" @click="save">
                保存草稿
              </el-button>
              <el-button v-hasPermi="['seo:website-code:publish']" type="primary"
                :disabled="busy || !draft || dirty || !hasUnpublished" :loading="publishing" @click="publishDraft">
                <Icon icon="ep:promotion" class="mr-6px" />发布到网站
              </el-button>
            </div>
          </div>
        </div>
      </section>
      <aside class="code-help">
        <section class="code-help-card">
          <h2><Icon icon="ep:info-filled" />生效说明</h2>
          <div class="help-entry"><h3>保存草稿</h3><p>仅保存到后台，网站继续使用上一次发布的代码。</p></div>
          <div class="help-entry"><h3>发布到网站</h3><p>将已保存的草稿发布到当前网站，访客下次打开或刷新页面时加载。</p></div>
          <div class="help-entry"><h3>按网站独立</h3><p>每个网站的代码单独保存和发布，修改本站不会影响其他网站。</p></div>
        </section>
        <section class="code-help-card code-position-card">
          <h2><Icon icon="ep:connection" />插入位置</h2>
          <dl class="code-positions">
            <div><dt>Header</dt><dd><code>&lt;head&gt;</code> 内部</dd></div>
            <div><dt>Body</dt><dd><code>&lt;body&gt;</code> 之后</dd></div>
            <div><dt>Footer</dt><dd><code>&lt;/body&gt;</code> 之前</dd></div>
          </dl>
        </section>
        <p class="code-help-note">统计和广告脚本遵循网站的访客同意设置。修改前的版本可在“修改记录”中恢复为草稿。</p>
        <p class="code-help-note">代码在页面加载后插入。若验证平台要求读取网页源代码，请使用 DNS 或验证文件。</p>
        <p v-if="draft?.publishedVersion" class="published-note">已发布 v{{ draft.publishedVersion }}<br />{{ formatTime(draft.publishedTime) }}</p>
      </aside>
    </div>

    <el-dialog v-model="historyOpen" title="修改记录" width="min(860px, calc(100vw - 32px))" class="code-history-dialog" destroy-on-close>
      <p class="history-description">{{ draft?.siteName }} · 最近 50 次保存、发布和恢复。恢复只更新草稿。</p>
      <el-alert v-if="historyError" type="error" :title="historyError" :closable="false" />
      <el-table v-loading="historyLoading" :data="history" empty-text="暂时没有修改记录">
        <el-table-column label="版本" width="82"><template #default="{ row }">v{{ row.version }}</template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{ row }">{{ actionLabels[row.action] }}</template></el-table-column>
        <el-table-column label="时间" min-width="174"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
        <el-table-column prop="creator" label="操作人" width="96" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="selectedHistory = row">查看代码</el-button>
            <el-button v-if="canEdit" link type="primary" :disabled="busy" @click="restoreDraft(row)">恢复为草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
      <section v-if="selectedHistory" class="history-code">
        <h3>v{{ selectedHistory.version }} · {{ actionLabels[selectedHistory.action] }}</h3>
        <div v-for="item in positions" :key="item.key">
          <h4>{{ item.label }} · {{ selectedHistory.content[item.key].enabled ? '已启用' : '已停用' }}</h4>
          <pre>{{ selectedHistory.content[item.key].code || '（空）' }}</pre>
        </div>
      </section>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, toRaw, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import javascript from 'highlight.js/lib/languages/javascript'
import { hasPermission } from '@/directives/permission/hasPermi'
import { registerTenantChangeGuard } from '@/utils/tenantChangeGuard'
import * as WebsiteCodeApi from '@/api/seo/website-code'
import type { CodePosition, WebsiteCodeContent, WebsiteCodeDraft, WebsiteCodeHistory } from '@/api/seo/website-code'

defineOptions({ name: 'SeoWebsiteCode' })
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('javascript', javascript)
const SITE_ID = 1 // Tenant-local site, consistent with the existing navigation and SEO modules.
const positions: { key: CodePosition; label: string; toggle: string; description: string }[] = [
  { key: 'header', label: 'Header 头部', toggle: '启用头部代码', description: '插入到 <head> 内，适用于 Google tag、GA4 和网站验证。' },
  { key: 'body', label: 'Body 正文开始', toggle: '启用正文开始代码', description: '插入到 <body> 之后，适用于平台要求放在正文开始位置的代码。' },
  { key: 'footer', label: 'Footer 页脚', toggle: '启用页脚代码', description: '插入到 </body> 之前，适用于客服工具和页脚脚本。' }
]
const empty = (): WebsiteCodeContent => ({
  header: { enabled: false, code: '' }, body: { enabled: false, code: '' }, footer: { enabled: false, code: '' }
})
const content = reactive(empty())
const draft = ref<WebsiteCodeDraft>()
const savedContent = ref(JSON.stringify(content))
const dirty = computed(() => JSON.stringify(content) !== savedContent.value)
const canEdit = computed(() => hasPermission(['seo:website-code:update']))
const position = ref<CodePosition>('header')
const selected = computed(() => positions.find((item) => item.key === position.value)!)
const loading = ref(false), saving = ref(false), publishing = ref(false), restoring = ref(false)
const switchingTenant = ref(false)
const busy = computed(() => loading.value || saving.value || publishing.value || restoring.value || switchingTenant.value)
const loadError = ref('')
const hasUnpublished = computed(() => Boolean(draft.value?.version && draft.value.version !== draft.value.publishedVersion))
const draftStatus = computed(() => dirty.value ? '有未保存的修改' : !draft.value?.version ? '尚未保存草稿'
  : hasUnpublished.value ? '草稿已保存，待发布' : '当前内容已发布')
const scrollTop = ref(0), scrollLeft = ref(0)
const lineCount = computed(() => Math.max(16, content[position.value].code.split('\n').length))
const highlighted = computed(() => hljs.highlight(content[position.value].code, { language: 'xml' }).value + '\n')
const syncScroll = (event: Event) => {
  const input = event.target as HTMLTextAreaElement
  scrollTop.value = input.scrollTop; scrollLeft.value = input.scrollLeft
}
watch(position, () => { scrollTop.value = 0; scrollLeft.value = 0 })
const acceptDraft = (value: WebsiteCodeDraft) => {
  draft.value = value
  Object.assign(content, structuredClone(value.content))
  savedContent.value = JSON.stringify(content)
}
const loadDraft = async () => {
  if (busy.value) return
  loading.value = true; loadError.value = ''
  try { acceptDraft(await WebsiteCodeApi.getDraft(SITE_ID)) }
  catch { loadError.value = '无法加载本站代码配置，请检查站点设置或重试。' }
  finally { loading.value = false }
}
const save = async () => {
  if (busy.value || !draft.value || !dirty.value || !canEdit.value) return
  saving.value = true
  try {
    acceptDraft(await WebsiteCodeApi.saveDraft({ siteId: SITE_ID, version: draft.value.version, content: structuredClone(toRaw(content)) }))
    ElMessage.success('草稿已保存，网站内容尚未改变')
  } catch { /* The request layer displays the error; keep the edited draft. */ }
  finally { saving.value = false }
}
const publishDraft = async () => {
  if (busy.value || !draft.value || dirty.value) return
  try {
    await ElMessageBox.confirm('将已保存的代码发布到“' + draft.value.siteName + '”？', '发布到网站', {
      confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  publishing.value = true
  try {
    acceptDraft(await WebsiteCodeApi.publish({ siteId: SITE_ID, version: draft.value.version }))
    ElMessage.success('已发布到当前网站')
  } catch { /* Keep the current version so a failed publish never looks successful. */ }
  finally { publishing.value = false }
}
const historyOpen = ref(false), historyLoading = ref(false), historyError = ref('')
const history = ref<WebsiteCodeHistory[]>([]), selectedHistory = ref<WebsiteCodeHistory>()
const actionLabels: Record<string, string> = { SAVE: '保存草稿', PUBLISH: '发布', RESTORE: '恢复草稿' }
const openHistory = async () => {
  historyOpen.value = true; historyLoading.value = true; historyError.value = ''; selectedHistory.value = undefined
  try { history.value = await WebsiteCodeApi.getHistory(SITE_ID) }
  catch { historyError.value = '修改记录加载失败，请关闭后重试。' }
  finally { historyLoading.value = false }
}
const restoreDraft = async (row: WebsiteCodeHistory) => {
  if (busy.value || !draft.value) return
  try {
    await ElMessageBox.confirm('恢复 v' + row.version + ' 为当前草稿？' + (dirty.value ? '未保存的修改会被替换。' : '') + '网站仍使用已发布版本。',
      '恢复为草稿', { confirmButtonText: '恢复草稿', cancelButtonText: '取消' })
  } catch { return }
  restoring.value = true
  try {
    acceptDraft(await WebsiteCodeApi.restore({ siteId: SITE_ID, version: draft.value.version, historyId: row.id }))
    historyOpen.value = false
    ElMessage.success('已恢复为草稿，发布后才会在网站生效')
  } catch { /* Keep the current draft on a failed restore. */ }
  finally { restoring.value = false }
}
const confirmLeave = async () => {
  if (busy.value) { ElMessage.warning('正在保存或加载，请稍候'); return false }
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('当前代码有未保存的修改，确定离开吗？', '未保存的修改',
      { confirmButtonText: '离开', cancelButtonText: '继续编辑' })
    return true
  } catch { return false }
}
const preventUnload = (event: BeforeUnloadEvent) => {
  if (dirty.value || saving.value || publishing.value) { event.preventDefault(); event.returnValue = '' }
}
const unregisterTenantGuard = registerTenantChangeGuard(async () => {
  const allowed = await confirmLeave()
  if (allowed) switchingTenant.value = true
  return allowed
})
onBeforeRouteLeave(() => switchingTenant.value || confirmLeave())
onMounted(() => { loadDraft(); window.addEventListener('beforeunload', preventUnload) })
onBeforeUnmount(() => { unregisterTenantGuard(); window.removeEventListener('beforeunload', preventUnload) })
const formatTime = (value?: string | number) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped lang="scss">
.website-code-page { color: var(--furniture-ink, #172033); padding: 6px 4px 24px; }
.code-heading { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.code-heading h1 { margin: 0 0 10px; font-size: 24px; font-weight: 650; line-height: 1.4; }
.code-heading p { margin: 0; color: #7b8798; font-size: 14px; }
.code-site-context { display: flex; align-items: center; gap: 9px; padding: 16px 0 23px; font-size: 14px; color: #697586; }
.code-site-context strong { color: #263349; font-weight: 600; }
.code-site-context > .iconify { font-size: 17px; color: #5c6a7e; }
.context-dot { color: #aab2bf; margin: 0 3px; }
.site-domain { margin-left: auto; color: #8490a1; font-size: 13px; }
.code-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 286px; align-items: start; gap: 24px; }
.code-panel, .code-help-card { background: #fff; border: 1px solid #e2e7ee; border-radius: 6px; }
.code-tabs { padding: 0 24px; }
.code-tabs :deep(.el-tabs__header) { margin: 0; }
.code-tabs :deep(.el-tabs__item) { height: 58px; font-size: 15px; padding: 0 24px; }
.code-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: #e8edf2; }
.code-panel-inner { padding: 22px 24px 0; }
.code-placement { font-size: 14px; line-height: 1.6; color: #68788e; margin: 0 0 24px; }
.code-toggle-row { display: flex; justify-content: space-between; align-items: center; font-size: 14px; margin-bottom: 18px; }
.snippet-editor { height: 400px; border: 1px solid #dfe5eb; border-radius: 5px; display: flex; overflow: hidden; background: #fcfdff; }
.line-numbers { flex: 0 0 44px; overflow: hidden; padding-top: 16px; background: #f6f8fb; border-right: 1px solid #edf0f4; text-align: right; color: #a0a9b5; }
.line-numbers span { display: block; padding-right: 12px; height: 24px; font: 13px/24px Consolas, monospace; }
.snippet-input-wrap { flex: 1; position: relative; overflow: hidden; min-width: 0; }
.snippet-input-wrap pre, .snippet-input-wrap textarea { margin: 0; padding: 16px; font: 13px/24px Consolas, 'Courier New', monospace; tab-size: 2; white-space: pre; border: 0; box-sizing: border-box; }
.snippet-input-wrap pre { position: absolute; inset: 0 auto auto 0; pointer-events: none; color: #334155; min-width: 100%; min-height: 100%; }
.snippet-input-wrap textarea { position: relative; width: 100%; height: 100%; resize: none; outline: none; background: transparent; color: transparent; caret-color: #172033; overflow: auto; }
.snippet-input-wrap textarea::placeholder { color: #9ba5b4; }
.snippet-editor:focus-within { border-color: #176bdb; box-shadow: 0 0 0 2px #176bdb14; }
.snippet-editor :deep(.hljs-tag), .snippet-editor :deep(.hljs-name) { color: #217995; }
.snippet-editor :deep(.hljs-attr) { color: #7958a8; }
.snippet-editor :deep(.hljs-string) { color: #3d8467; }
.snippet-editor :deep(.hljs-comment) { color: #959eab; }
.editor-caption { display: flex; justify-content: space-between; gap: 10px; margin-top: 12px; font-size: 12px; color: #919ba9; line-height: 1.6; }
.code-actions { border-top: 1px solid #edf0f4; margin: 22px -24px 0; padding: 20px 24px; display: flex; align-items: center; justify-content: space-between; gap: 15px; }
.draft-status { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #8490a1; }
.code-actions .el-button { height: 36px; }
.code-help-card { padding: 24px; }
.code-help-card h2 { display: flex; align-items: center; gap: 8px; font-size: 16px; margin: 0 0 26px; font-weight: 600; }
.code-help-card h2 .iconify { color: #176bdb; }
.code-help-card h3 { font-size: 14px; margin: 0 0 9px; font-weight: 600; }
.help-entry { margin-bottom: 25px; }
.help-entry p, .code-help-note { color: #8290a2; font-size: 13px; line-height: 1.9; margin: 0; }
.help-divider { border-top: 1px solid #edf0f4; margin: 5px 0 24px; }
.code-positions { margin: 18px 0 0; font-size: 13px; }
.code-positions > div { display: flex; margin-bottom: 16px; }
.code-positions > div:last-child { margin-bottom: 0; }
.code-positions dt { width: 65px; color: #536277; }
.code-positions dd { margin: 0; color: #8490a1; }
.code-positions code { color: #637084; background: #f3f5f8; padding: 2px 4px; border-radius: 3px; }
.code-help-note { margin-top: 18px; padding: 0 3px; }
.published-note { color: #8b96a6; font-size: 12px; line-height: 1.8; padding: 0 3px; }
.history-description { margin: 0 0 18px; color: #7b8798; }
.history-code pre { padding: 12px; background: #f5f7fa; max-height: 230px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.7 Consolas, monospace; }
.history-code h3 { margin-top: 28px; }
.history-code h4 { font-weight: 500; margin-bottom: 8px; }
@media (max-width: 1150px) { .code-workspace { grid-template-columns: minmax(0, 1fr) 240px; gap: 16px; } .code-help-card { padding: 20px; } .site-domain { display: none; } }
@media (max-width: 900px) { .code-workspace { grid-template-columns: minmax(0, 1fr); } .code-help { max-width: none; } .code-heading { align-items: flex-start; } .code-heading h1 { font-size: 22px; } .code-actions { flex-wrap: wrap; } .editor-caption { flex-wrap: wrap; } }
/* Match the approved desktop editor proportions while keeping the existing admin shell. */
.code-breadcrumb { font-size: 13px; line-height: 18px; color: #8490a1; margin: -10px 0 4px; }
.code-heading { margin-bottom: 0; }
.code-heading h1 { font-size: 28px; line-height: 36px; margin-bottom: 8px; }
.code-heading p { font-size: 14px; line-height: 22px; }
.code-site-context { padding: 10px 0 17px; line-height: 22px; }
.code-workspace { grid-template-columns: minmax(0, 1fr) 330px; gap: 16px; }
.code-panel-inner { padding-top: 18px; }
.code-placement-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; min-height: 24px; }
.code-placement { margin: 0; flex: 1; }
.code-toggle-row { margin: 0; gap: 12px; white-space: nowrap; }
.snippet-editor { height: 506px; }
.snippet-input-wrap pre, .snippet-input-wrap textarea { font-size: 14px; line-height: 24px; }
.snippet-input-wrap code { font: inherit; }
.snippet-editor :deep(.hljs-comment) { color: #178032; }
.snippet-editor :deep(.hljs-tag), .snippet-editor :deep(.hljs-name) { color: #bd252c; }
.snippet-editor :deep(.hljs-attr), .snippet-editor :deep(.hljs-keyword), .snippet-editor :deep(.hljs-title) { color: #1b49b4; }
.snippet-editor :deep(.hljs-string) { color: #b52d35; }
.code-help-card { padding: 0; }
.code-help-card h2 { margin: 0; padding: 18px; border-bottom: 1px solid #e6ebf1; line-height: 20px; }
.help-entry { margin: 0 18px; padding: 12px 0; border-bottom: 1px solid #e6ebf1; }
.help-entry:last-child { border: 0; }
.code-help-card h3 { font-size: 15px; line-height: 21px; }
.help-entry p { font-size: 14px; line-height: 22px; color: #7a889f; }
.code-position-card { margin-top: 0; }
.code-positions { padding: 6px 18px 24px; font-size: 14px; }
.code-actions .el-button { min-width: 108px; height: 40px; }
@media (max-width: 1150px) {
  .code-workspace { grid-template-columns: minmax(0, 1fr) 260px; }
  .code-placement-row { align-items: flex-start; flex-direction: column; gap: 12px; }
  .code-toggle-row { align-self: flex-end; }
}
@media (max-width: 900px) {
  .code-workspace { grid-template-columns: minmax(0, 1fr); }
  .code-heading h1 { font-size: 22px; }
  .snippet-editor { height: 400px; }
  .code-tabs { padding: 0 16px; }
  .code-tabs :deep(.el-tabs__item) { padding: 0 14px; font-size: 14px; }
  .code-panel-inner { padding: 18px 16px 0; }
  .code-actions { margin-left: -16px; margin-right: -16px; padding: 18px 16px; }
}
</style>
