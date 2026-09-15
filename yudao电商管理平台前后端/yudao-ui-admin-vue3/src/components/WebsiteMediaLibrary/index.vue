<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import { registerTenantChangeGuard } from '@/utils/tenantChangeGuard'
import { onBeforeRouteLeave } from 'vue-router'
import * as api from '@/api/seo/media'

const props = withDefaults(defineProps<{ selectable?: boolean; imagesOnly?: boolean }>(), { selectable: false, imagesOnly: false })
const emit = defineEmits<{ select: [asset: api.WebsiteMedia] }>()
const rows = ref<api.WebsiteMedia[]>([])
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement>()
const editOpen = ref(false)
const editing = reactive({ id: 0, name: '', alt: '' })
const query = reactive<api.MediaQuery>({ pageNo: 1, pageSize: 20, name: '', kind: props.imagesOnly ? 'image' : undefined, archived: false })
const canUpload = computed(() => hasPermission(['seo:media:upload']))
const canUpdate = computed(() => hasPermission(['seo:media:update']))
const canArchive = computed(() => hasPermission(['seo:media:archive']))
let generation = 0
let alive = true
async function load(reset = false) {
  if (reset) query.pageNo = 1
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    const result = await api.getMediaPage({ ...query })
    if (current === generation) { rows.value = result.list; total.value = result.total }
  } catch (e: any) { if (current === generation) { rows.value = []; total.value = 0; error.value = e?.msg || e?.message || '素材加载失败，请重试。' } }
  finally { if (current === generation) loading.value = false }
}
async function act(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try { await action() } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && alive) ElMessage.error(e?.msg || e?.message || '操作失败，请重试。')
  } finally { busy.value = false }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]; input.value = ''
  if (!file || !canUpload.value) return
  if (!/\.(jpe?g|png|pdf)$/i.test(file.name) || (props.imagesOnly && /\.pdf$/i.test(file.name)) || !file.size || file.size > 10 * 1024 * 1024) {
    ElMessage.warning(props.imagesOnly ? '请选择 10 MB 以内的 JPG 或 PNG 图片。' : '请选择 10 MB 以内的 JPG、PNG 或 PDF。'); return
  }
  await act(async () => {
    await api.uploadMedia(file)
    if (!alive) return
    query.archived = false; query.name = ''
    ElMessage.success('已上传到素材库，官网内容尚未更改。')
    await load(true)
  })
}
function edit(row: api.WebsiteMedia) { Object.assign(editing, { id: row.id, name: row.name, alt: row.alt }); editOpen.value = true }
const saveEdit = () => act(async () => {
  if (!editing.name.trim()) { ElMessage.warning('请输入素材名称。'); return }
  await api.updateMedia({ ...editing }); editOpen.value = false; await load(); ElMessage.success('素材信息已保存。')
})
const archive = (row: api.WebsiteMedia) => act(async () => {
  const archived = !row.archived
  await ElMessageBox.confirm(row.archived ? '将此素材恢复到可选列表？' : '移出后不再出现在可选列表中，已经发布的页面仍可正常使用，之后可以恢复。', row.archived ? '恢复素材' : '移出素材库')
  await api.archiveMedia(row.id, archived); await load(); ElMessage.success(archived ? '已移出素材库。' : '已恢复。')
})
async function copy(row: api.WebsiteMedia) {
  try { await navigator.clipboard.writeText(row.url); ElMessage.success('素材链接已复制。') }
  catch { await ElMessageBox.alert(row.url, '素材链接（可选中复制）') }
}
const size = (bytes: number) => bytes >= 1024 * 1024 ? (bytes / 1024 / 1024).toFixed(1) + ' MB' : Math.ceil(bytes / 1024) + ' KB'
const unregister = registerTenantChangeGuard(() => !busy.value)
onBeforeRouteLeave(() => !busy.value)
onMounted(() => load())
onBeforeUnmount(() => { alive = false; generation++; unregister() })
</script>

<template>
  <el-alert title="这里存放用于官网公开展示的图片和资料。上传不会自动发布页面；请勿上传内部资料或简历。" type="info" :closable="false" class="mb-16px" />
  <el-form :inline="true" :disabled="busy">
    <el-form-item label="素材名称"><el-input v-model="query.name" maxlength="160" placeholder="搜索素材" clearable @keyup.enter="load(true)" /></el-form-item>
    <el-form-item v-if="!imagesOnly" label="类型"><el-select v-model="query.kind" clearable placeholder="全部类型" class="!w-140px" @change="load(true)"><el-option label="图片" value="image" /><el-option label="PDF 资料" value="document" /></el-select></el-form-item>
    <el-form-item v-if="!selectable" label="范围"><el-select v-model="query.archived" class="!w-140px" @change="load(true)"><el-option label="可用素材" :value="false" /><el-option label="已移出素材" :value="true" /></el-select></el-form-item>
    <el-form-item><el-button @click="load(true)">查询</el-button><el-button v-if="canUpload" type="primary" :loading="busy" @click="fileInput?.click()">上传{{ imagesOnly ? '图片' : '素材' }}</el-button></el-form-item>
  </el-form>
  <input ref="fileInput" type="file" :accept="imagesOnly ? '.jpg,.jpeg,.png' : '.jpg,.jpeg,.png,.pdf'" hidden :disabled="busy" @change="upload" />
  <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-16px" />
  <el-table v-loading="loading" :data="rows" empty-text="暂无素材，可先上传图片或调整筛选条件。">
    <el-table-column label="预览" width="100"><template #default="{ row }"><el-image v-if="row.kind === 'image'" :src="row.url" :preview-src-list="[row.url]" preview-teleported fit="contain" class="h-64px w-72px" /><el-tag v-else>PDF</el-tag></template></el-table-column>
    <el-table-column prop="name" label="素材名称" min-width="150" show-overflow-tooltip />
    <el-table-column prop="alt" label="图片说明" min-width="150" show-overflow-tooltip />
    <el-table-column label="规格" width="140"><template #default="{ row }"><div>{{ size(row.size) }}</div><div v-if="row.width">{{ row.width }} × {{ row.height }}</div></template></el-table-column>
    <el-table-column label="操作" :min-width="selectable ? 190 : 230"><template #default="{ row }">
      <el-button v-if="selectable && !row.archived && (!imagesOnly || row.kind === 'image')" type="primary" size="small" :disabled="busy" @click="emit('select', row)">使用此图片</el-button>
      <el-button v-if="canUpdate" link type="primary" :disabled="busy" @click="edit(row)">编辑</el-button>
      <el-button link type="primary" @click="copy(row)">复制链接</el-button>
      <el-button v-if="canArchive && !selectable" link :disabled="busy" @click="archive(row)">{{ row.archived ? '恢复' : '移出' }}</el-button>
    </template></el-table-column>
  </el-table>
  <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" :disabled="busy" @pagination="load()" />
  <el-dialog v-model="editOpen" title="编辑素材信息" width="min(520px, 94vw)" :close-on-click-modal="false" :show-close="!busy">
    <el-form label-position="top" :disabled="busy"><el-form-item label="素材名称" required><el-input v-model="editing.name" maxlength="160" show-word-limit /></el-form-item><el-form-item label="图片说明"><el-input v-model="editing.alt" type="textarea" maxlength="240" show-word-limit placeholder="说明图片内容，选择到页面后仍可单独修改。" /></el-form-item></el-form>
    <template #footer><el-button :disabled="busy" @click="editOpen = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveEdit">保存</el-button></template>
  </el-dialog>
</template>
