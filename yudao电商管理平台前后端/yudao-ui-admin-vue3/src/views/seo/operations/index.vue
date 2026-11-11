<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import { registerTenantChangeGuard } from '@/utils/tenantChangeGuard'
import * as api from '@/api/seo/operations'
import { getCatalog, publishCatalog } from '@/api/seo/catalog'
import { getWebsiteBlogArticle, publishWebsiteBlogArticle } from '@/api/seo/blog'

defineOptions({ name: 'SeoOperations' })
const state = ref<api.OperationState>({
  documents: [],
  files: [],
  reviews: [],
  tasks: [],
  checks: []
})
const tab = ref('reviews'),
  busy = ref(false),
  error = ref(''),
  fileInput = ref<HTMLInputElement>(),
  backupInput = ref<HTMLInputElement>()
const canEdit = computed(() => hasPermission(['seo:operations:update'])),
  canApprove = computed(() => hasPermission(['seo:operations:approve'])),
  canPublish = computed(() => hasPermission(['seo:operations:publish']))
const kinds = { certificate: '证书', guide: '指南', catalog: '目录', specification: '规格书' }
const statuses = { pending: '待审核', approved: '已批准', rejected: '已退回', published: '已发布' }
const showHistory = ref(false),
  reviews = computed(() =>
    state.value.reviews.filter(
      (r) => showHistory.value || ['pending', 'approved'].includes(r.status)
    )
  )
const emptyDocument = (): api.DocumentFields => ({
  title: '',
  kind: 'guide',
  language: 'en',
  holder: '',
  scope: '',
  market: '',
  owner: '',
  issuedAt: '',
  expiresAt: '',
  assetId: 0
})
const documentOpen = ref(false),
  document = ref<api.DocumentFields & { id?: number; version?: number }>(emptyDocument()),
  documentSaved = ref('')
const task = reactive<api.OperationTask>({ title: '', owner: '', due: '', status: 'open' })
const backup = ref<api.ContentBackup>(),
  backupCheck = ref<Record<string, any>>(),
  restoreKind = ref<api.OperationReview['kind']>('catalog'),
  restoreId = ref<number>()
const documentDirty = computed(
  () => documentOpen.value && JSON.stringify(document.value) !== documentSaved.value
)
let alive = true
async function load() {
  const next = await api.getOperations()
  if (alive) state.value = next
}
async function act(fn: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await fn()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && alive)
      error.value = e?.msg || e?.message || '操作失败，请重新加载后重试。'
  } finally {
    busy.value = false
  }
}
async function leave() {
  if (busy.value) return false
  if (!documentDirty.value) return true
  try {
    await ElMessageBox.confirm('资料有未保存修改，确定离开？', '未保存修改')
    return true
  } catch {
    return false
  }
}
const unregister = registerTenantChangeGuard(leave)
onBeforeRouteLeave(leave)
onBeforeUnmount(() => {
  alive = false
  unregister()
})
function openDocument(row?: api.OperationDocument) {
  const value = row
    ? {
        id: row.id,
        version: row.version,
        ...Object.fromEntries(
          Object.keys(emptyDocument()).map((k) => [k, row[k as keyof api.DocumentFields]])
        )
      }
    : emptyDocument()
  document.value = structuredClone(value) as typeof document.value
  documentSaved.value = JSON.stringify(document.value)
  documentOpen.value = true
}
async function closeDocument(done: () => void) {
  if (await leave()) done()
}
const saveDocument = () =>
  act(async () => {
    await api.saveDocument({
      ...document.value,
      issuedAt: document.value.issuedAt || '',
      expiresAt: document.value.expiresAt || ''
    })
    documentOpen.value = false
    await load()
    ElMessage.success('资料草稿已保存，请提交审核。')
  })
const submitDocument = (row: api.OperationDocument) =>
  act(async () => {
    await api.submitReview('document', String(row.id), row.version)
    await load()
    ElMessage.success('资料版本已提交审核。')
  })
function reviewFileId(row: api.OperationReview) {
  if (row.kind !== 'document') return undefined
  return state.value.documents.find(
    (d) => String(d.id) === row.targetId && d.version === row.contentVersion
  )?.assetId
}
const downloadFile = (id?: number) =>
  act(async () => {
    if (!id) return
    const file = state.value.files.find((f) => f.id === id)
    const blob = await api.downloadDocumentFile(id)
    const url = URL.createObjectURL(blob)
    const link = window.document.createElement('a')
    link.href = url
    link.download = file?.name || 'document.pdf'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  })
function fileInUse(id: number) {
  return state.value.documents.some((d) => d.assetId === id || d.published?.assetId === id)
}
const deleteFile = (file: api.OperationFile) =>
  act(async () => {
    await ElMessageBox.confirm(
      `永久删除“${file.name}”？引用该文件的旧备份将无法恢复；已被资料草稿或发布版本引用的文件不可删除。`,
      '清理未使用文件'
    )
    await api.deleteDocumentFile(file.id)
    await load()
    ElMessage.success('未引用文件已删除。')
  })
const docAction = (action: 'publish' | 'offline' | 'delete', row: api.OperationDocument) =>
  act(async () => {
    await ElMessageBox.confirm(
      action === 'delete'
        ? '删除此资料草稿？此操作不可撤回。'
        : action === 'offline'
          ? '下线后访客不能继续下载此资料，确定下线？'
          : '发布已审核的资料版本？'
    )
    await api.documentAction(action, { id: row.id, version: row.version })
    await load()
  })
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!/\.pdf$/i.test(file.name) || file.size > 5 * 1024 * 1024 || file.size === 0) {
    ElMessage.warning('请选择 5 MB 以内的 PDF 文件。')
    return
  }
  await act(async () => {
    const uploaded = await api.uploadDocumentFile(file)
    await load()
    if (documentOpen.value) document.value.assetId = uploaded.id
    ElMessage.success('PDF 已保存；审核发布前不会公开下载。')
  })
}
const decide = (row: api.OperationReview, status: 'approved' | 'rejected') =>
  act(async () => {
    const result = await ElMessageBox.prompt(
      status === 'approved' ? '填写审核备注（可留空）' : '请填写退回原因',
      '审核具体版本',
      { inputValidator: (v: string) => status === 'approved' || !!v?.trim() || '请输入退回原因' }
    )
    await api.decideReview(row, status, result.value || '')
    await load()
  })
function mayPublish(row: api.OperationReview) {
  return row.kind === 'catalog'
    ? hasPermission(['seo:page:publish'])
    : row.kind === 'article'
      ? hasPermission(['seo:blog:publish'])
      : canPublish.value
}
const publish = (row: api.OperationReview) =>
  act(async () => {
    await ElMessageBox.confirm(`发布“${row.title}”的已批准版本 v${row.contentVersion}？`)
    if (row.kind === 'catalog') await publishCatalog(row.contentVersion)
    else if (row.kind === 'article')
      await publishWebsiteBlogArticle(Number(row.targetId), row.contentVersion)
    else
      await api.documentAction('publish', { id: Number(row.targetId), version: row.contentVersion })
    await load()
    ElMessage.success('已发布该版本。')
  })
const addTask = () =>
  act(async () => {
    await api.saveTask({ ...task })
    Object.assign(task, { title: '', owner: '', due: '', status: 'open' })
    await load()
  })
const toggleTask = (row: api.OperationTask) =>
  act(async () => {
    await api.saveTask({ ...row, status: row.status === 'open' ? 'done' : 'open' })
    await load()
  })
const deleteTask = (row: api.OperationTask) =>
  act(async () => {
    await ElMessageBox.confirm('删除此维护任务？')
    await api.deleteTask(row)
    await load()
  })
const check = () =>
  act(async () => {
    await api.runCheck()
    await load()
    ElMessage.success('巡检已完成。')
  })
const downloadBackup = () =>
  act(async () => {
    const content = await api.exportBackup()
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(content, null, 2)], { type: 'application/json' })
    )
    const a = window.document.createElement('a')
    a.href = url
    a.download = `website-content-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  })
async function readBackup(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  backup.value = undefined
  backupCheck.value = undefined
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('备份超过 20 MB，请检查文件。')
    return
  }
  await act(async () => {
    const parsed = JSON.parse(await file.text())
    const checked = await api.checkBackup(parsed)
    backup.value = parsed
    backupCheck.value = checked
    restoreId.value = undefined
    ElMessage.success('备份校验通过，可选择内容恢复为草稿。')
  })
}
const restore = () =>
  act(async () => {
    if (!backup.value) return
    let version: number
    if (restoreKind.value === 'catalog') version = (await getCatalog()).version
    else if (restoreKind.value === 'article') {
      if (!restoreId.value) throw Error('请选择文章')
      version = (await getWebsiteBlogArticle(restoreId.value)).version!
    } else {
      const row = state.value.documents.find((d) => d.id === restoreId.value)
      if (!row) throw Error('请选择现有资料')
      version = row.version
    }
    await ElMessageBox.confirm(
      '覆盖所选内容的当前草稿？已发布版本保持不变，恢复后必须重新审核。',
      '恢复草稿'
    )
    await api.restoreBackup(backup.value, restoreKind.value, version, restoreId.value)
    await load()
    ElMessage.success('已恢复为草稿。')
  })
onMounted(() => act(load))
</script>

<template>
  <ContentWrap
    title="网站运营中心"
    message="资料、审核和维护任务使用当前 ERP 租户；发布仅影响此租户绑定站点。"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <div class="ops-toolbar"
      ><el-button :loading="busy" @click="act(load)">刷新</el-button
      ><router-link to="/seo/catalog"><el-button>产品目录</el-button></router-link
      ><router-link to="/seo/blog"><el-button>文章管理</el-button></router-link
      ><router-link v-if="hasPermission(['seo:media:query'])" to="/seo/media"
        ><el-button>公开素材库</el-button></router-link
      ></div
    >
    <el-tabs v-model="tab" class="mt-16px">
      <el-tab-pane label="版本审核" name="reviews">
        <el-checkbox v-model="showHistory">显示已处理历史</el-checkbox>
        <el-table
          :data="reviews"
          v-loading="busy"
          empty-text="暂无待处理审核，请从产品、文章或资料提交版本。"
        >
          <el-table-column prop="title" label="内容" min-width="220" /><el-table-column
            prop="contentVersion"
            label="版本"
            width="80"
          /><el-table-column label="状态" width="110"
            ><template #default="{ row }"
              ><el-tag
                :type="
                  row.status === 'approved'
                    ? 'success'
                    : row.status === 'rejected'
                      ? 'danger'
                      : 'info'
                "
                >{{ statuses[row.status as keyof typeof statuses] }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column prop="note" label="审核备注" min-width="180" />
          <el-table-column label="操作" min-width="200"
            ><template #default="{ row }"
              ><el-button
                v-if="reviewFileId(row)"
                link
                type="primary"
                :disabled="busy"
                @click="downloadFile(reviewFileId(row))"
                >下载审核原件</el-button
              ><template v-if="row.status === 'pending' && canApprove"
                ><el-button link type="primary" :disabled="busy" @click="decide(row, 'approved')"
                  >批准版本</el-button
                ><el-button link type="danger" :disabled="busy" @click="decide(row, 'rejected')"
                  >退回</el-button
                ></template
              ><el-button
                v-if="row.status === 'approved' && mayPublish(row)"
                link
                type="success"
                :disabled="busy"
                @click="publish(row)"
                >发布该版本</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="资料与证书" name="documents">
        <el-alert
          title="上传资料先保存为草稿；审核发布且未过期的 PDF 才能公开下载。证书必须填写有效期和适用范围。"
          type="info"
          :closable="false"
          class="mb-16px"
        />
        <div class="ops-toolbar"
          ><el-button v-if="canEdit" type="primary" :disabled="busy" @click="openDocument()"
            >新建资料</el-button
          ><el-button v-if="canEdit" :disabled="busy" @click="fileInput?.click()"
            >上传 PDF</el-button
          ><span>{{ state.files.length }} 个文件 · {{ state.documents.length }} 份资料</span></div
        >
        <el-table
          :data="state.documents"
          v-loading="busy"
          empty-text="暂无真实供应商资料，请先上传获批文件。"
        >
          <el-table-column prop="title" label="资料名称" min-width="220" /><el-table-column
            label="类型"
            width="90"
            ><template #default="{ row }">{{
              kinds[row.kind as keyof typeof kinds]
            }}</template></el-table-column
          ><el-table-column prop="holder" label="持有人" min-width="120" /><el-table-column
            prop="expiresAt"
            label="草稿到期日"
            width="120"
          /><el-table-column label="公开状态" width="120"
            ><template #default="{ row }"
              ><el-tag :type="row.published ? 'success' : 'info'">{{
                row.published
                  ? row.published.expiresAt &&
                    row.published.expiresAt < new Date().toISOString().slice(0, 10)
                    ? '已到期'
                    : '已发布'
                  : '未发布'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="原件" width="105"
            ><template #default="{ row }"
              ><el-button link type="primary" :disabled="busy" @click="downloadFile(row.assetId)"
                >下载 PDF</el-button
              ></template
            ></el-table-column
          >
          <el-table-column label="操作" min-width="270"
            ><template #default="{ row }"
              ><el-button
                v-if="canEdit"
                link
                type="primary"
                :disabled="busy"
                @click="openDocument(row)"
                >编辑草稿</el-button
              ><el-button
                v-if="canEdit"
                link
                type="primary"
                :disabled="busy"
                @click="submitDocument(row)"
                >提交审核</el-button
              ><el-button
                v-if="canPublish && row.published"
                link
                type="warning"
                :disabled="busy"
                @click="docAction('offline', row)"
                >下线</el-button
              ><el-button
                v-if="canPublish && !row.published"
                link
                type="danger"
                :disabled="busy"
                @click="docAction('delete', row)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="PDF 文件" name="files">
        <el-alert
          title="原件仅通过 ERP 登录权限下载；资料审核发布后才允许访客下载对应版本。"
          type="info"
          :closable="false"
          class="mb-16px"
        />
        <el-button v-if="canEdit" :disabled="busy" @click="fileInput?.click()">上传 PDF</el-button>
        <el-table :data="state.files" empty-text="暂无文件">
          <el-table-column prop="name" label="文件名称" min-width="240" />
          <el-table-column label="大小" width="100"
            ><template #default="{ row }"
              >{{ Math.ceil(row.size / 1024) }} KB</template
            ></el-table-column
          >
          <el-table-column label="引用状态" width="100"
            ><template #default="{ row }">{{
              fileInUse(row.id) ? '正在使用' : '未引用'
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="200"
            ><template #default="{ row }"
              ><el-button link type="primary" :disabled="busy" @click="downloadFile(row.id)"
                >下载原件</el-button
              ><el-button
                v-if="canPublish"
                link
                type="danger"
                :disabled="busy || fileInUse(row.id)"
                @click="deleteFile(row)"
                >删除文件</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="巡检与维护" name="checks">
        <el-button v-if="canEdit" type="primary" :disabled="busy" @click="check">立即巡检</el-button
        ><p v-if="state.checks[0]">最近巡检：{{ state.checks[0].at }}</p>
        <el-table
          :data="state.checks[0]?.issues || []"
          empty-text="暂无巡检问题；如尚未检查，请先运行巡检。"
          ><el-table-column prop="target" label="内容" min-width="200" /><el-table-column
            prop="message"
            label="待处理事项"
            min-width="260"
        /></el-table>
        <h3>维护任务</h3>
        <el-form v-if="canEdit" :inline="true" :disabled="busy" @submit.prevent="addTask"
          ><el-form-item label="任务"
            ><el-input
              v-model="task.title"
              maxlength="180"
              placeholder="需要核实的内容" /></el-form-item
          ><el-form-item label="负责人"
            ><el-input v-model="task.owner" maxlength="100" /></el-form-item
          ><el-form-item label="期限"
            ><el-date-picker
              v-model="task.due"
              type="date"
              value-format="YYYY-MM-DD" /></el-form-item
          ><el-form-item
            ><el-button type="primary" native-type="submit">添加任务</el-button></el-form-item
          ></el-form
        >
        <el-table :data="state.tasks" empty-text="暂无维护任务"
          ><el-table-column prop="title" label="任务" min-width="180" /><el-table-column
            prop="owner"
            label="负责人"
          /><el-table-column prop="due" label="期限" /><el-table-column label="状态"
            ><template #default="{ row }"
              ><el-tag
                :type="
                  row.status === 'done'
                    ? 'success'
                    : row.due < new Date().toISOString().slice(0, 10)
                      ? 'danger'
                      : 'info'
                "
                >{{
                  row.status === 'done'
                    ? '已完成'
                    : row.due < new Date().toISOString().slice(0, 10)
                      ? '已逾期'
                      : '待处理'
                }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column label="操作" min-width="160"
            ><template #default="{ row }"
              ><el-button
                v-if="canEdit"
                link
                type="primary"
                :disabled="busy"
                @click="toggleTask(row)"
                >{{ row.status === 'done' ? '重新打开' : '标记完成' }}</el-button
              ><el-button
                v-if="canPublish"
                link
                type="danger"
                :disabled="busy"
                @click="deleteTask(row)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </el-tab-pane>
      <el-tab-pane v-if="canPublish" label="内容备份与恢复" name="backup">
        <el-alert
          title="此备份用于同一租户现有内容的草稿恢复，不含文件原件、账号密码或 CRM 联系人；完整系统备份仍须包含数据库和文件存储。"
          type="info"
          :closable="false"
          class="mb-16px"
        />
        <div class="ops-toolbar"
          ><el-button type="primary" :disabled="busy" @click="downloadBackup"
            >导出内容备份</el-button
          ><el-button :disabled="busy" @click="backupInput?.click()">读取并校验备份</el-button></div
        >
        <template v-if="backupCheck"
          ><p>校验通过，请选择恢复对象。</p
          ><el-form label-position="top" class="mt-16px"
            ><el-form-item label="内容类型"
              ><el-radio-group v-model="restoreKind" @change="restoreId = undefined"
                ><el-radio-button value="catalog">产品目录</el-radio-button
                ><el-radio-button value="article">文章</el-radio-button
                ><el-radio-button value="document">资料</el-radio-button></el-radio-group
              ></el-form-item
            ><el-form-item v-if="restoreKind === 'article'" label="文章"
              ><el-select v-model="restoreId" filterable class="!w-400px"
                ><el-option
                  v-for="row in backupCheck.articles || []"
                  :key="row.id"
                  :label="row.title"
                  :value="row.id" /></el-select></el-form-item
            ><el-form-item v-if="restoreKind === 'document'" label="资料"
              ><el-select v-model="restoreId" class="!w-400px"
                ><el-option
                  v-for="row in state.documents"
                  :key="row.id"
                  :label="row.title"
                  :value="row.id" /></el-select></el-form-item
            ><el-button
              type="warning"
              :disabled="busy || (restoreKind !== 'catalog' && !restoreId)"
              @click="restore"
              >恢复所选草稿</el-button
            ></el-form
          ></template
        >
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>
  <input ref="fileInput" type="file" accept=".pdf" hidden @change="upload" /><input
    ref="backupInput"
    type="file"
    accept=".json,application/json"
    hidden
    @change="readBackup"
  />
  <el-dialog
    v-model="documentOpen"
    title="资料草稿"
    width="min(760px,94vw)"
    :before-close="closeDocument"
    :close-on-click-modal="false"
    ><el-form label-position="top" :disabled="busy || !canEdit"
      ><el-form-item label="名称"
        ><el-input v-model="document.title" maxlength="180" /></el-form-item
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="类型"
            ><el-select v-model="document.kind" class="w-full"
              ><el-option
                v-for="(label, value) in kinds"
                :key="value"
                :value="value"
                :label="label" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="语言"
            ><el-input
              v-model="document.language"
              placeholder="en"
              maxlength="20" /></el-form-item></el-col></el-row
      ><el-form-item label="PDF 文件"
        ><el-select v-model="document.assetId" filterable class="w-full"
          ><el-option
            v-for="file in state.files"
            :key="file.id"
            :label="file.name"
            :value="file.id" /></el-select
        ><el-button class="mt-8px" @click="fileInput?.click()"
          >上传新的 PDF</el-button
        ></el-form-item
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="持有人"
            ><el-input v-model="document.holder" maxlength="180" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="负责人"
            ><el-input v-model="document.owner" maxlength="100" /></el-form-item></el-col></el-row
      ><el-form-item label="适用范围 / 产品型号"
        ><el-input
          v-model="document.scope"
          type="textarea"
          :rows="3"
          maxlength="2000" /></el-form-item
      ><el-form-item label="适用市场"
        ><el-input v-model="document.market" maxlength="180" /></el-form-item
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="签发日期"
            ><el-date-picker
              v-model="document.issuedAt"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item
            :label="document.kind === 'certificate' ? '到期日期（证书必填）' : '到期日期（可空）'"
            ><el-date-picker
              v-model="document.expiresAt"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full" /></el-form-item></el-col></el-row></el-form
    ><template #footer
      ><el-button :disabled="busy" @click="closeDocument(() => (documentOpen = false))"
        >取消</el-button
      ><el-button type="primary" :disabled="busy || !canEdit" @click="saveDocument"
        >保存草稿</el-button
      ></template
    ></el-dialog
  >
</template>

<style scoped>
.ops-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.ops-toolbar .el-button + .el-button {
  margin-left: 0;
}
</style>
