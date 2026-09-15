<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import { registerTenantChangeGuard } from '@/utils/tenantChangeGuard'
import * as api from '@/api/seo/page-content'
import WebsiteMediaLibrary from '@/components/WebsiteMediaLibrary/index.vue'
import type { WebsiteMedia } from '@/api/seo/media'

defineOptions({ name: 'SeoPageContent' })
const locale = ref<'zh-CN' | 'en'>('zh-CN')
const loadedLocale = ref<'zh-CN' | 'en'>('zh-CN')
const draft = ref<api.PageDraft>()
const schema = ref<api.PageSchema>()
const content = ref<api.HomeContent>()
const saved = ref('')
const busy = ref(false)
const loadError = ref('')
const uninitialized = ref(false)
const history = ref<api.PageDraft[]>([])
const historyOpen = ref(false)
const mediaOpen = ref(false)
const canSelectMedia = computed(() => hasPermission(['seo:media:query']))
function selectMedia(asset: WebsiteMedia) {
  if (!content.value || !canEdit.value) return
  writeField('image.url', asset.url)
  writeField('image.alt', asset.alt || asset.name)
  mediaOpen.value = false
}
const previewUrl = ref('')
const canEdit = computed(() => hasPermission(['seo:page:update']))
const canPublish = computed(() => hasPermission(['seo:page:publish']))
const canPreview = computed(() => hasPermission(['seo:page:preview']))
const dirty = computed(() => Boolean(content.value && JSON.stringify(content.value) !== saved.value))
let generation = 0
const key = (): api.PageKey => ({ siteId: 1, pageKey: 'home', locale: locale.value })
const version = (): api.PageVersion => ({ ...key(), expectedVersion: draft.value!.version })
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
function apply(value: api.PageDraft) {
  draft.value = value
  content.value = clone(value.content)
  saved.value = JSON.stringify(content.value)
  loadedLocale.value = value.locale
  uninitialized.value = false
  previewUrl.value = ''
}
function readField(path: string): string {
  const hero = content.value!.modules.hero
  if (path === 'image.url') return hero.image.url
  if (path === 'image.alt') return hero.image.alt
  return hero[path as 'title' | 'subtitle' | 'body']
}
function writeField(path: string, value: string) {
  const hero = content.value!.modules.hero
  if (path === 'image.url') hero.image.url = value
  else if (path === 'image.alt') hero.image.alt = value
  else if (['title', 'subtitle', 'body'].includes(path)) hero[path as 'title' | 'subtitle' | 'body'] = value
  previewUrl.value = ''
}
async function load() {
  const current = ++generation
  busy.value = true
  loadError.value = ''
  uninitialized.value = false
  draft.value = undefined
  content.value = undefined
  previewUrl.value = ''
  history.value = []
  try {
    const shape = await api.getSchema(key())
    if (current !== generation) return
    schema.value = shape
    const value = await api.getDraft(key())
    if (current !== generation) return
    apply(value)
  } catch (error: any) {
    if (current !== generation) return
    uninitialized.value = Number(error?.code) === 1070008001
    loadError.value = error?.msg || error?.message || '页面内容加载失败；请确认当前租户已配置 TRIPEER 官网。'
  } finally { if (current === generation) busy.value = false }
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try { await action() }
  catch (error: any) { ElMessage.error(error?.msg || error?.message || '操作失败，请重新加载后重试。') }
  finally { busy.value = false }
}
async function save() {
  if (!draft.value || !content.value || !canEdit.value) return
  apply(await api.saveDraft({ ...version(), content: clone(content.value) }))
}
const saveClick = () => run(async () => { await save(); ElMessage.success('草稿已保存，尚未发布。') })
const initialize = () => run(async () => { apply(await api.initialize(key())); loadError.value = ''; ElMessage.success('已导入初始草稿，请预览后发布。') })
const publish = () => run(async () => {
  if (dirty.value || !draft.value) return
  await ElMessageBox.confirm('将当前语言已保存的首页首屏发布到官网？', '发布确认', { type: 'warning' })
  apply(await api.publish(version()))
  ElMessage.success('已发布，官网刷新后生效。')
})
const preview = () => run(async () => {
  if (dirty.value) await save()
  if (!draft.value) return
  const result = await api.preview(version())
  const target = new URL(result.previewUrl)
  if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password) throw new Error('预览地址无效')
  previewUrl.value = result.previewUrl
  // An explicit link avoids losing the window to browser popup blocking.
  ElMessage.success('预览已准备好，请点击“打开整页预览”。')
})
const restore = (row: api.PageDraft) => run(async () => {
  if (!draft.value || !row.revisionId || !canEdit.value) return
  await ElMessageBox.confirm('将此历史版本恢复为草稿？当前未发布修改将被覆盖，线上内容保持不变。', '恢复草稿')
  apply(await api.restoreDraft({ ...version(), revisionId: row.revisionId }))
  historyOpen.value = false
  ElMessage.success('已恢复为草稿，请预览确认后重新发布。')
})
const openHistory = () => run(async () => { history.value = await api.getHistory(key()); historyOpen.value = true })
async function mayLeave() {
  if (busy.value) return false
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('草稿尚未保存，确认离开并丢弃修改？', '未保存的修改'); return true }
  catch { return false }
}
async function changeLanguage(value: 'zh-CN' | 'en') {
  if (value === loadedLocale.value) return
  if (!(await mayLeave())) return
  locale.value = value
  await load()
}
const unregister = registerTenantChangeGuard(mayLeave)
onBeforeRouteLeave(mayLeave)
const unload = (event: BeforeUnloadEvent) => { if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
watch(content, () => { if (dirty.value) previewUrl.value = '' }, { deep: true })
onMounted(() => { load(); window.addEventListener('beforeunload', unload) })
onBeforeUnmount(() => { generation++; unregister(); window.removeEventListener('beforeunload', unload); previewUrl.value = '' })
</script>

<template>
  <ContentWrap title="官网页面内容" message="第一批开放首页首屏；保存草稿后预览，发布后官网刷新生效。">
    <el-form label-position="top" :disabled="busy">
      <el-form-item label="编辑页面">首页 · 首屏</el-form-item>
      <el-form-item label="内容语言">
        <el-select :model-value="locale" @update:model-value="changeLanguage">
          <el-option label="简体中文" value="zh-CN" />
          <el-option label="English" value="en" />
        </el-select>
      </el-form-item>
      <el-alert v-if="loadError" :title="loadError" type="warning" :closable="false" show-icon />
      <el-button v-if="!draft" :disabled="busy" @click="load">重新加载</el-button>
      <el-button v-if="!draft && schema && canEdit" :disabled="busy" @click="initialize">
        导入初始内容（已有内容不会覆盖）
      </el-button>
      <template v-if="content && schema && draft">
        <el-form-item v-for="field in schema.fields" :key="field.path" :label="field.label" :required="field.required">
          <el-input :model-value="readField(field.path)" :type="field.type === 'textarea' ? 'textarea' : 'text'"
            :rows="field.path === 'body' ? 5 : 2" :maxlength="field.maxLength" show-word-limit
            :disabled="busy || !canEdit" @update:model-value="value => writeField(field.path, value)" />
          <el-button v-if="field.path === 'image.url' && canEdit && canSelectMedia" class="mt-8px" :disabled="busy" @click="mediaOpen = true">从素材库选择图片</el-button>
        </el-form-item>
        <p>可从素材库选择图片，也可填写官网 /assets/ 路径或稳定的 HTTPS 图片地址。选择后需保存、预览并发布。</p>
        <p>草稿版本 {{ draft.version }} · 已发布 {{ draft.publishedVersion || '尚未发布' }} · {{ dirty ? '有未保存修改' : '已保存' }}</p>
        <el-button v-if="canEdit" :disabled="busy || !dirty" @click="saveClick">保存草稿</el-button>
        <el-button v-if="canPreview" :disabled="busy || (dirty && !canEdit)" @click="preview">{{ dirty ? '保存并准备预览' : '准备预览' }}</el-button>
        <el-button v-if="canPublish" type="primary" :disabled="busy || dirty || draft.version === draft.publishedVersion" @click="publish">发布首页首屏</el-button>
        <el-button :disabled="busy" @click="openHistory">发布记录</el-button>
        <p v-if="previewUrl"><a :href="previewUrl" target="_blank" rel="noopener noreferrer">打开整页预览（链接两分钟内有效）</a></p>
      </template>
    </el-form>
    <el-dialog v-model="mediaOpen" title="选择官网图片" width="min(1000px, 96vw)" destroy-on-close :close-on-click-modal="false">
      <WebsiteMediaLibrary v-if="mediaOpen" selectable images-only @select="selectMedia" />
    </el-dialog>
    <el-dialog v-model="historyOpen" title="最近发布版本" width="640px">
      <el-table :data="history">
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column prop="locale" label="语言" width="100" />
        <el-table-column label="首页标题"><template #default="{ row }">{{ row.content.modules.hero.title }}</template></el-table-column>
        <el-table-column label="操作" width="140"><template #default="{ row }"><el-button v-if="canEdit" :disabled="busy" link type="primary" @click="restore(row)">恢复为草稿</el-button></template></el-table-column>
      </el-table>
    </el-dialog>
  </ContentWrap>
</template>
