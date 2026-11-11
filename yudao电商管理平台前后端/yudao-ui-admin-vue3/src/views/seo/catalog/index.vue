<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import { registerTenantChangeGuard } from '@/utils/tenantChangeGuard'
import WebsiteMediaLibrary from '@/components/WebsiteMediaLibrary/index.vue'
import { getSeoSiteConfig } from '@/api/seo/siteConfig'
import type { WebsiteMedia } from '@/api/seo/media'
import * as api from '@/api/seo/catalog'
import { submitReview } from '@/api/seo/operations'
import { prepareCatalogContent } from '@/utils/websiteCatalogPayload'

defineOptions({ name: 'SeoCatalog' })
const draft = ref<api.CatalogDraft>(),
  content = ref<api.CatalogContent>(),
  saved = ref(''),
  query = ref(''),
  selected = ref('')
const busy = ref(false),
  error = ref(''),
  mediaOpen = ref(false),
  historyOpen = ref(false),
  history = ref<api.CatalogDraft[]>([]),
  siteUrl = ref('')
const canEdit = computed(() => hasPermission(['seo:page:update'])),
  canPublish = computed(() => hasPermission(['seo:page:publish']))
const dirty = computed(() => !!content.value && JSON.stringify(content.value) !== saved.value)
const products = computed(
  () =>
    content.value?.products.filter((p) =>
      [p.name, p.slug, p.material, ...p.specs.map((s) => s.code)]
        .join(' ')
        .toLowerCase()
        .includes(query.value.toLowerCase())
    ) || []
)
const product = computed(() => content.value?.products.find((p) => p.slug === selected.value))
let alive = true
function apply(row: api.CatalogDraft) {
  draft.value = row
  content.value = structuredClone(row.content)
  saved.value = JSON.stringify(row.content)
  if (!row.content.products.some((p) => p.slug === selected.value))
    selected.value = row.content.products[0]?.slug || ''
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
async function confirmLeave() {
  if (busy.value) return false
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('未保存的目录修改将丢失，继续离开？', '未保存修改')
    return true
  } catch {
    return false
  }
}
const unregister = registerTenantChangeGuard(confirmLeave)
onBeforeRouteLeave(confirmLeave)
onBeforeUnmount(() => {
  alive = false
  unregister()
})
const reload = () =>
  act(async () => {
    if (dirty.value) await ElMessageBox.confirm('重新加载会丢弃未保存修改，是否继续？')
    const [row, config] = await Promise.all([api.getCatalog(), getSeoSiteConfig(1)])
    if (alive) {
      apply(row)
      siteUrl.value = config?.siteUrl || ''
    }
  })
function imageUrl(path: string) {
  try {
    return new URL(path, siteUrl.value).href
  } catch {
    return path
  }
}
function selectImage(asset: WebsiteMedia) {
  if (product.value && canEdit.value) {
    product.value.image = asset.url
    mediaOpen.value = false
  }
}
const save = () =>
  act(async () => {
    if (!draft.value || !content.value) return
    apply(await api.saveCatalog(draft.value.version, prepareCatalogContent(content.value)))
    ElMessage.success('目录草稿已保存，已发布版本未改变。')
  })
const review = () =>
  act(async () => {
    if (!draft.value || dirty.value) return
    await submitReview('catalog', 'catalog', draft.value.version)
    ElMessage.success('已提交该版本，请发布员在网站运营中心审核。')
  })
const publish = () =>
  act(async () => {
    if (!draft.value || dirty.value) return
    await ElMessageBox.confirm('发布当前已审核的目录版本到绑定站点？', '发布目录')
    apply(await api.publishCatalog(draft.value.version))
    ElMessage.success('目录已发布。')
  })
const preview = () =>
  act(async () => {
    if (!draft.value || dirty.value) return
    const ticket = await api.previewCatalog(draft.value.version)
    window.open(ticket.previewUrl, '_blank', 'noopener,noreferrer')
  })
const showHistory = () =>
  act(async () => {
    history.value = await api.getCatalogHistory()
    historyOpen.value = true
  })
const restore = (row: api.CatalogDraft) =>
  act(async () => {
    if (!draft.value || !row.revisionId) return
    await ElMessageBox.confirm(
      '以历史版本覆盖当前草稿？已发布内容保持不变，恢复后需要重新审核。',
      '恢复草稿'
    )
    apply(await api.restoreCatalog(draft.value.version, row.revisionId))
    historyOpen.value = false
  })
onMounted(reload)
</script>

<template>
  <ContentWrap title="餐具产品目录" message="维护绑定站点的产品与规格；保存草稿后先审核，再发布。">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <div class="catalog-actions">
      <el-input v-model="query" placeholder="搜索产品、材质或型号" clearable class="!w-260px" />
      <el-button :disabled="busy" @click="reload">重新加载</el-button>
      <el-button v-if="canEdit" type="primary" :disabled="busy || !dirty" @click="save"
        >保存草稿</el-button
      >
      <el-button
        v-hasPermi="['seo:page:preview']"
        :disabled="busy || dirty || !draft"
        @click="preview"
        >整页预览</el-button
      >
      <el-button
        v-hasPermi="['seo:operations:update']"
        :disabled="busy || dirty || !draft"
        @click="review"
        >提交审核</el-button
      >
      <el-button
        v-if="canPublish"
        type="success"
        :disabled="busy || dirty || !draft"
        @click="publish"
        >发布已审核版本</el-button
      >
      <el-button :disabled="busy || !draft" @click="showHistory">发布历史</el-button>
      <el-tag v-if="draft">草稿 v{{ draft.version }}</el-tag
      ><el-tag v-if="dirty" type="warning">有未保存修改</el-tag>
    </div>
    <el-row v-loading="busy" :gutter="20" class="mt-16px">
      <el-col :lg="8" :md="9" :sm="24">
        <el-table
          :data="products"
          row-key="slug"
          highlight-current-row
          @row-click="(row: api.CatalogProduct) => (selected = row.slug)"
          empty-text="没有匹配产品"
        >
          <el-table-column label="图片" width="82"
            ><template #default="{ row }"
              ><el-image :src="imageUrl(row.image)" fit="contain" class="w-56px h-56px" /></template
          ></el-table-column>
          <el-table-column prop="name" label="产品" min-width="150" /><el-table-column
            label="规格"
            width="65"
            ><template #default="{ row }">{{ row.specs.length }}</template></el-table-column
          >
        </el-table>
        <p
          >共 {{ content?.products.length || 0 }} 个产品 ·
          {{ content?.products.reduce((n, p) => n + p.specs.length, 0) || 0 }} 条规格</p
        >
      </el-col>
      <el-col :lg="16" :md="15" :sm="24">
        <el-form v-if="product" label-position="top" :disabled="busy || !canEdit">
          <el-row :gutter="16"
            ><el-col :span="12"
              ><el-form-item label="产品名称"
                ><el-input v-model="product.name" maxlength="180" /></el-form-item></el-col
            ><el-col :span="12"
              ><el-form-item label="固定页面标识"
                ><el-input :model-value="product.slug" disabled /></el-form-item></el-col
          ></el-row>
          <el-row :gutter="16"
            ><el-col :span="12"
              ><el-form-item label="大类"
                ><el-select v-model="product.category" class="w-full"
                  ><el-option label="杯与吸管" value="cups-straws" /><el-option
                    label="食品容器"
                    value="food-containers" /><el-option label="餐具" value="tableware" /><el-option
                    label="纸袋"
                    value="bags" /></el-select></el-form-item></el-col
            ><el-col :span="12"
              ><el-form-item label="子分类"
                ><el-input v-model="product.subcategory" /></el-form-item></el-col
          ></el-row>
          <el-row :gutter="16"
            ><el-col :span="12"
              ><el-form-item label="材质"
                ><el-input v-model="product.material" /></el-form-item></el-col
            ><el-col :span="12"
              ><el-form-item label="类型"><el-input v-model="product.type" /></el-form-item></el-col
          ></el-row>
          <el-form-item label="产品介绍"
            ><el-input v-model="product.description" type="textarea" :rows="3"
          /></el-form-item>
          <el-form-item label="图片"
            ><div class="catalog-image"
              ><el-image
                :src="imageUrl(product.image)"
                fit="contain"
                class="w-90px h-90px"
              /><el-input v-model="product.image" /><el-button
                v-hasPermi="['seo:media:query']"
                @click="mediaOpen = true"
                >素材库选图</el-button
              ></div
            ></el-form-item
          >
          <el-row :gutter="16"
            ><el-col :span="16"
              ><el-form-item label="来源地址"
                ><el-input v-model="product.sourceUrl" /></el-form-item></el-col
            ><el-col :span="8"
              ><el-form-item label="来源核验日"
                ><el-date-picker
                  v-model="product.sourceDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="!w-full" /></el-form-item></el-col
          ></el-row>
          <el-form-item label="采购说明（每行一条）"
            ><el-input
              :model-value="product.notes.join('\n')"
              type="textarea"
              :rows="3"
              @update:model-value="(value) => product && (product.notes = value.split('\n'))"
          /></el-form-item>
          <h3>规格与装箱</h3
          ><el-alert
            v-if="!product.specs.length"
            title="此产品规格尚待供应商确认，请勿填写猜测数据。"
            type="warning"
            :closable="false"
            class="mb-12px"
          />
          <div v-for="(spec, index) in product.specs" :key="index" class="spec-row">
            <el-row :gutter="12"
              ><el-col :span="10"
                ><el-form-item :label="`型号 ${index + 1}`"
                  ><el-input v-model="spec.code" /></el-form-item></el-col
              ><el-col :span="14"
                ><el-form-item label="规格描述"
                  ><el-input v-model="spec.description" /></el-form-item></el-col
            ></el-row>
            <el-row :gutter="12"
              ><el-col :span="8"
                ><el-form-item label="容量 oz（可空）"
                  ><el-input-number
                    :key="`capacity-${busy || !canEdit}`"
                    v-model="spec.capacityOz"
                    :min="0.1"
                    :controls="false"
                    class="!w-full" /></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="口径 mm（可空）"
                  ><el-input-number
                    :key="`rim-${busy || !canEdit}`"
                    v-model="spec.rimMm"
                    :min="0.1"
                    :controls="false"
                    class="!w-full" /></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="尺寸"
                  ><el-input v-model="spec.dimensions" /></el-form-item></el-col
            ></el-row>
            <el-row :gutter="12"
              ><el-col :span="14"
                ><el-form-item label="装箱"
                  ><el-input v-model="spec.packaging" /></el-form-item></el-col
              ><el-col :span="10"
                ><el-form-item label="外箱体积"
                  ><el-input v-model="spec.cartonCbm" /></el-form-item></el-col
            ></el-row>
            <el-button type="danger" plain @click="product.specs.splice(index, 1)"
              >移除此规格</el-button
            >
          </div>
          <el-button v-if="canEdit" @click="product.specs.push({ code: '', description: '' })"
            >添加规格</el-button
          > </el-form
        ><el-empty v-else description="请先选择产品" />
      </el-col>
    </el-row>
  </ContentWrap>
  <el-dialog v-model="mediaOpen" title="选择公开图片" width="min(1050px,94vw)" destroy-on-close
    ><WebsiteMediaLibrary selectable images-only @select="selectImage"
  /></el-dialog>
  <el-dialog v-model="historyOpen" title="目录发布历史" width="min(800px,94vw)"
    ><el-table :data="history"
      ><el-table-column prop="version" label="版本" /><el-table-column label="产品数"
        ><template #default="{ row }">{{ row.content.products.length }}</template></el-table-column
      ><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button v-if="canEdit" :disabled="busy" link type="primary" @click="restore(row)"
            >恢复为草稿</el-button
          ></template
        ></el-table-column
      ></el-table
    ></el-dialog
  >
</template>

<style scoped>
.catalog-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.catalog-actions .el-button + .el-button {
  margin-left: 0;
}
.catalog-image {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.spec-row {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 14px;
  margin-bottom: 16px;
}
@media (max-width: 767px) {
  .catalog-image {
    flex-wrap: wrap;
  }
  .catalog-image .el-input {
    flex: 1;
  }
}
</style>
