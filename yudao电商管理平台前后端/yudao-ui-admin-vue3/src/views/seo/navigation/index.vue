<template>
  <div class="website-navigation-page">
    <section class="navigation-toolbar" aria-label="官网导航操作栏">
      <div class="navigation-toolbar__identity">
        <div class="navigation-toolbar__icon">
          <Icon icon="ep:guide" />
        </div>
        <div>
          <div class="navigation-toolbar__title">
            <strong>VANZ 官网导航</strong>
            <el-tag size="small" type="info" effect="plain">English</el-tag>
          </div>
          <p>两个入口都能改分类名称；确认保存后，商品中心和官网导航保持一致。</p>
        </div>
      </div>

      <div class="navigation-toolbar__actions">
        <div class="navigation-version">
          <span>草稿 v{{ draft?.version || '-' }}</span>
          <span class="navigation-version__divider"></span>
          <span>{{ publishedStatusLabel }}</span>
        </div>
        <el-button :disabled="busy" @click="openHistory">
          <Icon icon="ep:clock" class="mr-5px" />
          发布记录
        </el-button>
        <el-button :loading="refreshing" :disabled="busy" @click="refreshCategories">
          <Icon icon="ep:refresh" class="mr-5px" />
          同步商品分类
        </el-button>
        <el-button
          :loading="saving"
          :disabled="busy || !dirty"
          v-hasPermi="['seo:navigation:update']"
          @click="persistDraft(true)"
        >
          <Icon icon="ep:document-checked" class="mr-5px" />
          保存草稿
        </el-button>
        <el-button
          type="primary"
          plain
          :loading="previewLoading"
          :disabled="busy || !siteConfigured"
          v-hasPermi="['seo:navigation:preview']"
          @click="refreshInlinePreview"
        >
          <Icon icon="ep:view" class="mr-5px" />
          保存并预览
        </el-button>
        <el-button
          type="primary"
          :loading="publishing"
          :disabled="busy || changeSummary.length === 0"
          v-hasPermi="['seo:navigation:publish']"
          @click="publishDraft"
        >
          <Icon icon="ep:promotion" class="mr-5px" />
          发布到官网
        </el-button>
      </div>
    </section>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>
        <el-button type="primary" link @click="loadDraft">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="navigation-workspace">
      <main class="navigation-editor">
        <ContentWrap
          title="本次发布变化"
          message="发布前先确认访客会看到哪些变化。"
          surface="panel"
          :auto-title="false"
        >
          <div v-if="changeSummary.length" class="navigation-change-list">
            <div v-for="item in changeSummary" :key="item">
              <Icon icon="ep:circle-check" />
              <span>{{ item }}</span>
            </div>
          </div>
          <el-empty v-else :image-size="54" description="当前草稿与线上版本一致，无需重复发布" />
        </ContentWrap>
        <ContentWrap
          title="一级导航"
          message="拖动调整官网顶部顺序；页面地址已固定，业务人员不用填写链接。"
          surface="form"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <el-tag type="info" effect="plain">{{ primaryItems.length }} 项固定页面</el-tag>
            </div>
          </template>

          <p class="section-description">
            拖动调整官网顶部顺序；页面地址已固定，业务人员不用填写链接。
          </p>

          <draggable
            v-model="primaryItems"
            item-key="itemKey"
            handle=".navigation-row__handle"
            ghost-class="navigation-row--ghost"
            :animation="180"
            class="navigation-list"
            @end="onPrimarySortEnd"
          >
            <template #item="{ element, index }">
              <article
                class="navigation-row"
                :class="{ 'navigation-row--hidden': !element.visible }"
              >
                <button type="button" class="navigation-row__handle" aria-label="拖动调整顺序">
                  <Icon icon="ep:rank" />
                </button>
                <span class="navigation-row__order">{{ index + 1 }}</span>
                <div class="navigation-row__body">
                  <el-input
                    v-model="element.label"
                    maxlength="64"
                    :disabled="busy"
                    @input="markDirty"
                  />
                  <small>
                    <Icon icon="ep:link" />
                    {{ pageRouteLabel(element.pageKey) }}
                  </small>
                </div>
                <div class="navigation-row__visibility">
                  <span>{{ element.visible ? '官网显示' : '已隐藏' }}</span>
                  <el-switch v-model="element.visible" :disabled="busy" @change="markDirty" />
                </div>
              </article>
            </template>
          </draggable>
        </ContentWrap>

        <ContentWrap
          title="Products 二级目录"
          message="名称可直接修改；确认保存后会同步更新商品中心和已发布官网。"
          surface="form"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <el-tag type="success" effect="plain">名称双向同步</el-tag>
            </div>
          </template>

          <p class="section-description">
            商品分类页和这里都可以改名；保存时确认一次，两边会立即使用同一个名称。
          </p>

          <div class="category-picker">
            <el-select
              v-model="categoryToAdd"
              filterable
              clearable
              :disabled="busy || availableCategoryOptions.length === 0"
              placeholder="选择一个商品分类"
              class="category-picker__select"
            >
              <el-option
                v-for="option in availableCategoryOptions"
                :key="option.id"
                :label="`${option.name} · ${option.publishedProductCount} 个在线商品`"
                :value="option.id"
              />
            </el-select>
            <el-button type="primary" plain :disabled="busy || !categoryToAdd" @click="addCategory">
              <Icon icon="ep:plus" class="mr-5px" />
              加入二级目录
            </el-button>
          </div>

          <div v-if="categoryItems.length === 0" class="category-empty">
            <Icon icon="ep:folder-opened" />
            <div>
              <strong>Products 暂无二级目录</strong>
              <p>从上方选择商品分类即可加入，不需要手工填写链接。</p>
            </div>
          </div>

          <draggable
            v-else
            v-model="categoryItems"
            item-key="itemKey"
            handle=".navigation-row__handle"
            ghost-class="navigation-row--ghost"
            :animation="180"
            class="navigation-list"
            @end="onCategorySortEnd"
          >
            <template #item="{ element, index }">
              <article
                class="navigation-row navigation-row--category"
                :class="{
                  'navigation-row--hidden': !element.visible,
                  'navigation-row--unavailable': !element.available
                }"
              >
                <button type="button" class="navigation-row__handle" aria-label="拖动调整顺序">
                  <Icon icon="ep:rank" />
                </button>
                <span class="navigation-row__order">{{ index + 1 }}</span>
                <div class="navigation-row__body">
                  <el-input
                    v-model="element.label"
                    maxlength="64"
                    show-word-limit
                    :disabled="busy || !element.available"
                    @input="markCategoryNameDirty(element)"
                  />
                  <small v-if="element.available">
                    <Icon icon="ep:goods" />
                    分类 ID {{ element.categoryId }} ·
                    {{ element.publishedProductCount || 0 }} 个在线商品 · 保存后同步商品中心
                  </small>
                  <small v-else class="is-error">
                    <Icon icon="ep:warning-filled" />
                    分类已停用或删除，保存后将从官网隐藏
                  </small>
                </div>
                <el-tag v-if="element.available" size="small" type="info" effect="plain">
                  双向同步
                </el-tag>
                <div class="navigation-row__visibility">
                  <span>{{ element.visible ? '官网显示' : '已隐藏' }}</span>
                  <el-switch
                    v-model="element.visible"
                    :disabled="busy || !element.available"
                    @change="markDirty"
                  />
                </div>
                <el-button
                  type="danger"
                  link
                  :disabled="busy"
                  aria-label="移出二级目录"
                  @click="removeCategory(index)"
                >
                  <Icon icon="ep:delete" />
                </el-button>
              </article>
            </template>
          </draggable>

          <div class="category-sync-note">
            <Icon icon="ep:info-filled" />
            <span> 新建商品分类不会自动出现在官网；在这里勾选并发布后才会上线，避免误展示。 </span>
          </div>
        </ContentWrap>
      </main>

      <aside class="navigation-preview" aria-label="官网真实预览">
        <ContentWrap
          title="官网真实预览"
          message="预览使用真实官网组件和商品数据，不会修改线上版本。"
          surface="panel"
          :auto-title="false"
        >
          <template #header>
            <div class="section-header-actions">
              <div class="preview-actions">
                <el-tag v-if="inlinePreviewUrl" type="success" effect="plain">未发布草稿</el-tag>
                <el-button
                  v-if="inlinePreviewUrl"
                  size="small"
                  :loading="widePreviewLoading"
                  :disabled="busy"
                  @click="openWidePreview"
                >
                  <Icon icon="ep:full-screen" class="mr-4px" />
                  宽屏查看
                </el-button>
              </div>
            </div>
          </template>

          <p class="section-description"> 预览使用真实官网组件和商品数据，不会修改线上版本。 </p>

          <el-alert
            v-if="!siteConfigLoading && !siteConfigured"
            title="首次预览前，请先确认官网地址"
            type="warning"
            show-icon
            :closable="false"
            class="mb-16px"
          >
            <template #default>
              <span>系统需要知道在哪个官网打开预览；只需设置一次，不会发布导航。</span>
              <el-button type="primary" link @click="goToSiteConfig">去确认官网地址</el-button>
            </template>
          </el-alert>

          <div v-if="inlinePreviewUrl" class="preview-browser">
            <div class="preview-browser__bar">
              <Icon icon="ep:monitor" />
              <span>{{ inlinePreviewDisplayUrl }}</span>
              <el-button
                type="primary"
                link
                :loading="previewLoading"
                @click="refreshInlinePreview"
              >
                刷新预览
              </el-button>
            </div>
            <iframe
              :key="inlinePreviewUrl"
              :src="inlinePreviewUrl"
              title="VANZ 官网未发布导航预览"
              class="preview-browser__frame"
            ></iframe>
          </div>

          <div v-else class="preview-placeholder">
            <div class="preview-placeholder__icon">
              <Icon icon="ep:view" />
            </div>
            <strong>先看看改动在真实官网里的样子</strong>
            <p>点击“保存并预览”，系统会自动保存草稿并打开真实官网组件。</p>
            <el-button
              type="primary"
              :loading="previewLoading"
              :disabled="busy || !siteConfigured"
              v-hasPermi="['seo:navigation:preview']"
              @click="refreshInlinePreview"
            >
              生成真实预览
            </el-button>
          </div>

          <div class="preview-security-note">
            <Icon icon="ep:lock" />
            <div>
              <strong>预览链接为一次性临时凭证</strong>
              <p>10 分钟内有效，只能兑换一次；刷新时系统会生成新的凭证。</p>
            </div>
          </div>
        </ContentWrap>
      </aside>
    </div>

    <el-dialog
      v-model="widePreviewVisible"
      title="VANZ 官网 · 未发布导航宽屏预览"
      width="96%"
      top="2vh"
      destroy-on-close
      class="wide-preview-dialog"
    >
      <iframe
        v-if="widePreviewUrl"
        :key="widePreviewUrl"
        :src="widePreviewUrl"
        title="VANZ 官网未发布导航宽屏预览"
        class="wide-preview-frame"
      ></iframe>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="官网导航发布记录" size="520px">
      <el-alert
        class="mb-14px"
        :closable="false"
        show-icon
        type="info"
        title="恢复操作只会覆盖当前草稿；预览确认并再次发布后，官网才会变化。"
      />
      <el-empty v-if="!historyLoading && history.length === 0" description="尚无发布记录" />
      <el-table v-else v-loading="historyLoading" :data="history" stripe>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.revisionNo }}</template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="165">
          <template #default="{ row }">{{ formatHistoryTime(row.publishedTime) }}</template>
        </el-table-column>
        <el-table-column label="发布人" width="100">
          <template #default="{ row }">{{
            row.publishedBy ? `用户 #${row.publishedBy}` : '-'
          }}</template>
        </el-table-column>
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">
              {{ row.status === 'PUBLISHED' ? '当前线上' : '历史版本' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="96">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status === 'PUBLISHED' || busy"
              @click="restoreHistory(row)"
            >
              恢复为草稿
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import {
  createWebsiteNavigationPreviewTicket,
  getWebsiteNavigationHistory,
  getWebsiteNavigationDraft,
  publishWebsiteNavigation,
  saveWebsiteNavigationDraft,
  restoreWebsiteNavigationDraft,
  type WebsiteNavigationCategoryOptionRespVO,
  type WebsiteNavigationDraftRespVO,
  type WebsiteNavigationItemRespVO,
  type WebsiteNavigationItemSaveReqVO,
  type WebsiteNavigationRevisionRespVO
} from '@/api/seo/navigation'
import { getSeoSiteConfig } from '@/api/seo/siteConfig'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'SeoNavigation' })

const SITE_ID = 1
const LOCALE = 'en'

const pageRoutes: Record<string, string> = {
  HOME: '首页 /',
  PRODUCTS: '产品中心 /products',
  ABOUT_US: '品牌介绍 /about-us',
  WORKSHOP: '工坊 /workshop',
  BLOG: '博客 /blog',
  CONTACT: '联系我们 /contact'
}

const message = useMessage()
const router = useRouter()
const draft = ref<WebsiteNavigationDraftRespVO>()
const primaryItems = ref<WebsiteNavigationItemRespVO[]>([])
const categoryItems = ref<WebsiteNavigationItemRespVO[]>([])
const categoryOptions = ref<WebsiteNavigationCategoryOptionRespVO[]>([])
const originalCategoryNames = ref<Map<number, string>>(new Map())
const editedCategoryIds = ref<Set<number>>(new Set())
const categoryToAdd = ref<number>()
const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const publishing = ref(false)
const previewLoading = ref(false)
const widePreviewLoading = ref(false)
const dirty = ref(false)
const loadError = ref('')
const inlinePreviewUrl = ref('')
const widePreviewUrl = ref('')
const widePreviewVisible = ref(false)
const historyVisible = ref(false)
const historyLoading = ref(false)
const history = ref<WebsiteNavigationRevisionRespVO[]>([])
const siteConfigLoading = ref(false)
const siteConfigured = ref(false)

const busy = computed(
  () =>
    loading.value ||
    saving.value ||
    refreshing.value ||
    publishing.value ||
    previewLoading.value ||
    widePreviewLoading.value ||
    siteConfigLoading.value
)

const publishedStatusLabel = computed(() =>
  draft.value?.publishedRevisionNo ? `线上 v${draft.value.publishedRevisionNo}` : '尚未发布'
)

const changeSummary = computed(() => {
  const publishedItems = draft.value?.publishedItems || []
  const draftItems = [...primaryItems.value, ...categoryItems.value]
  if (!publishedItems.length) {
    const visibleCount = draftItems.filter((item) => item.visible).length
    return visibleCount ? [`首次发布 ${visibleCount} 个可见导航项`] : []
  }
  const messages: string[] = []
  const publishedMap = new Map(publishedItems.map((item) => [item.itemKey, item]))
  const draftMap = new Map(draftItems.map((item) => [item.itemKey, item]))
  const addedCategories = categoryItems.value.filter((item) => !publishedMap.has(item.itemKey))
  const removedCategories = publishedItems.filter(
    (item) => item.itemType === 'CATEGORY' && !draftMap.has(item.itemKey)
  )
  if (addedCategories.length)
    messages.push(`新增二级目录：${addedCategories.map((item) => item.label).join('、')}`)
  if (removedCategories.length)
    messages.push(`移除二级目录：${removedCategories.map((item) => item.label).join('、')}`)

  const renamed = draftItems.filter((item) => {
    const published = publishedMap.get(item.itemKey)
    return published && published.label !== item.label
  })
  if (renamed.length) messages.push(`修改名称：${renamed.map((item) => item.label).join('、')}`)

  const visibilityChanged = draftItems.filter((item) => {
    const published = publishedMap.get(item.itemKey)
    return published && published.visible !== item.visible
  })
  if (visibilityChanged.length) {
    messages.push(
      `调整显示状态：${visibilityChanged
        .map((item) => `${item.label}（${item.visible ? '显示' : '隐藏'}）`)
        .join('、')}`
    )
  }

  const orderChanged = (['PAGE', 'CATEGORY'] as const).some((itemType) => {
    const publishedOrder = publishedItems
      .filter((item) => item.itemType === itemType)
      .sort((left, right) => left.sort - right.sort)
      .filter((item) => draftMap.has(item.itemKey))
      .map((item) => item.itemKey)
      .join('|')
    const draftOrder = draftItems
      .filter((item) => item.itemType === itemType)
      .sort((left, right) => left.sort - right.sort)
      .filter((item) => publishedMap.has(item.itemKey))
      .map((item) => item.itemKey)
      .join('|')
    return publishedOrder !== draftOrder
  })
  if (orderChanged) messages.push('调整导航顺序')
  return messages
})

const selectedCategoryIds = computed(
  () => new Set(categoryItems.value.map((item) => item.categoryId).filter(Boolean))
)

const availableCategoryOptions = computed(() =>
  categoryOptions.value.filter((option) => !selectedCategoryIds.value.has(option.id))
)

const pendingCategoryRenames = computed(() =>
  categoryItems.value.filter(
    (item) =>
      item.available &&
      item.categoryId &&
      editedCategoryIds.value.has(item.categoryId) &&
      item.label.trim() !== (originalCategoryNames.value.get(item.categoryId) || '').trim()
  )
)

const inlinePreviewDisplayUrl = computed(() => safePreviewDisplayUrl(inlinePreviewUrl.value))

const loadSiteConfig = async () => {
  siteConfigLoading.value = true
  try {
    const config = await getSeoSiteConfig(SITE_ID)
    siteConfigured.value = Boolean(config?.siteUrl)
  } catch {
    siteConfigured.value = false
  } finally {
    siteConfigLoading.value = false
  }
}

const goToSiteConfig = () =>
  router.push({ path: '/seo/site-config', query: { returnTo: '/seo/navigation' } })

const loadDraft = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const response = await getWebsiteNavigationDraft(SITE_ID, LOCALE)
    draft.value = response
    primaryItems.value = response.items
      .filter((item) => item.itemType === 'PAGE')
      .map((item) => ({ ...item }))
    let normalizedUnavailableCategory = false
    categoryItems.value = response.items
      .filter((item) => item.itemType === 'CATEGORY')
      .map((item) => {
        if (!item.available && item.visible) {
          normalizedUnavailableCategory = true
          return { ...item, visible: false }
        }
        return { ...item }
      })
    categoryOptions.value = response.categoryOptions.map((option) => ({ ...option }))
    originalCategoryNames.value = new Map(
      categoryItems.value
        .filter((item) => item.categoryId)
        .map((item) => [item.categoryId as number, item.label])
    )
    editedCategoryIds.value = new Set()
    dirty.value = normalizedUnavailableCategory
  } catch {
    loadError.value = '官网导航加载失败，请确认 SEO 服务和商品中心已启动'
  } finally {
    loading.value = false
  }
}

const markDirty = () => {
  dirty.value = true
  inlinePreviewUrl.value = ''
}

const markCategoryNameDirty = (item: WebsiteNavigationItemRespVO) => {
  if (!item.categoryId) return
  const nextEditedIds = new Set(editedCategoryIds.value)
  const originalName = originalCategoryNames.value.get(item.categoryId) || ''
  if (item.label.trim() === originalName.trim()) {
    nextEditedIds.delete(item.categoryId)
  } else {
    nextEditedIds.add(item.categoryId)
  }
  editedCategoryIds.value = nextEditedIds
  markDirty()
}

const normalizeSort = () => {
  primaryItems.value.forEach((item, index) => {
    item.sort = (index + 1) * 10
  })
  categoryItems.value.forEach((item, index) => {
    item.sort = (index + 1) * 10
  })
}

const onPrimarySortEnd = () => {
  normalizeSort()
  markDirty()
}

const onCategorySortEnd = () => {
  normalizeSort()
  markDirty()
}

const addCategory = () => {
  if (!categoryToAdd.value) return
  const option = categoryOptions.value.find((item) => item.id === categoryToAdd.value)
  if (!option) return
  categoryItems.value.push({
    itemKey: `CATEGORY_${option.id}`,
    itemType: 'CATEGORY',
    categoryId: option.id,
    label: option.name,
    sort: (categoryItems.value.length + 1) * 10,
    visible: true,
    available: true,
    publishedProductCount: option.publishedProductCount
  })
  originalCategoryNames.value = new Map(originalCategoryNames.value).set(option.id, option.name)
  categoryToAdd.value = undefined
  markDirty()
}

const removeCategory = (index: number) => {
  const categoryId = categoryItems.value[index]?.categoryId
  categoryItems.value.splice(index, 1)
  if (categoryId) {
    const nextEditedIds = new Set(editedCategoryIds.value)
    nextEditedIds.delete(categoryId)
    editedCategoryIds.value = nextEditedIds
  }
  normalizeSort()
  markDirty()
}

const pageRouteLabel = (pageKey?: string) => (pageKey ? pageRoutes[pageKey] : '')

const validateLocalItems = () => {
  const blankPage = primaryItems.value.find((item) => !item.label.trim())
  if (blankPage) {
    message.warning('一级导航名称不能为空')
    return false
  }
  const blankCategory = categoryItems.value.find(
    (item) => item.available && !item.label.trim()
  )
  if (blankCategory) {
    message.warning('二级导航名称不能为空')
    return false
  }
  return true
}

const toSaveItem = (item: WebsiteNavigationItemRespVO): WebsiteNavigationItemSaveReqVO => ({
  itemType: item.itemType,
  pageKey: item.pageKey,
  categoryId: item.categoryId,
  label: item.label.trim(),
  syncCategoryName:
    item.itemType === 'CATEGORY' &&
    Boolean(item.categoryId && editedCategoryIds.value.has(item.categoryId)),
  sort: item.sort,
  visible: item.visible
})

const persistDraft = async (notify = true) => {
  if (!draft.value || !validateLocalItems()) return false
  const categoryRenames = [...pendingCategoryRenames.value]
  if (categoryRenames.length) {
    await message.confirm(
      `确认同步修改以下分类名称吗？\n${categoryRenames
        .map(
          (item) =>
            `• ${originalCategoryNames.value.get(item.categoryId as number) || '-'} → ${item.label.trim()}`
        )
        .join('\n')}\n确认后会同时更新商品中心和已发布官网的 Products 二级导航，无需再次发布。`,
      '同步分类名称'
    )
  }
  normalizeSort()
  saving.value = true
  try {
    await saveWebsiteNavigationDraft({
      revisionId: draft.value.revisionId,
      siteId: draft.value.siteId,
      locale: draft.value.locale,
      version: draft.value.version,
      items: [...primaryItems.value, ...categoryItems.value].map(toSaveItem)
    })
    inlinePreviewUrl.value = ''
    widePreviewUrl.value = ''
    await loadDraft()
    if (notify) {
      message.success(
        categoryRenames.length
          ? `已同步 ${categoryRenames.length} 个商品分类名称，并保存导航草稿`
          : '导航草稿已保存，线上官网未受影响'
      )
    }
    return true
  } finally {
    saving.value = false
  }
}

const ensureDraftSaved = async () => {
  if (!dirty.value) return true
  return persistDraft(false)
}

const refreshCategories = async () => {
  refreshing.value = true
  try {
    if (!(await ensureDraftSaved())) return
    await loadDraft()
    message.success('已同步商品中心的最新分类和商品数量')
  } finally {
    refreshing.value = false
  }
}

const requestPreviewUrl = async () => {
  if (!draft.value) return ''
  const ticket = await createWebsiteNavigationPreviewTicket(
    draft.value.revisionId,
    draft.value.version
  )
  return ticket.previewUrl
}

const refreshInlinePreview = async () => {
  previewLoading.value = true
  try {
    if (!(await ensureDraftSaved())) return
    inlinePreviewUrl.value = await requestPreviewUrl()
  } finally {
    previewLoading.value = false
  }
}

const openWidePreview = async () => {
  widePreviewLoading.value = true
  try {
    if (!(await ensureDraftSaved())) return
    widePreviewUrl.value = await requestPreviewUrl()
    widePreviewVisible.value = true
  } finally {
    widePreviewLoading.value = false
  }
}

const publishDraft = async () => {
  if (!(await ensureDraftSaved()) || !draft.value) return
  if (!changeSummary.value.length) {
    message.info('当前草稿与线上版本一致，无需重复发布')
    return
  }
  await message.confirm(
    `确认发布以下变化吗？\n${changeSummary.value.map((item) => `• ${item}`).join('\n')}\n发布后访客将立即看到新导航。`
  )
  publishing.value = true
  try {
    await publishWebsiteNavigation(draft.value.revisionId, draft.value.version)
    inlinePreviewUrl.value = ''
    widePreviewUrl.value = ''
    widePreviewVisible.value = false
    message.success('官网导航发布成功')
    await loadDraft()
  } finally {
    publishing.value = false
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    history.value = await getWebsiteNavigationHistory(SITE_ID, LOCALE)
  } finally {
    historyLoading.value = false
  }
}

const openHistory = async () => {
  historyVisible.value = true
  await loadHistory()
}

const restoreHistory = async (revision: WebsiteNavigationRevisionRespVO) => {
  if (!draft.value) return
  await message.confirm(
    `确认把历史版本 v${revision.revisionNo} 恢复为当前草稿吗？当前未发布草稿会被覆盖，官网暂时不会变化。`
  )
  await restoreWebsiteNavigationDraft(
    draft.value.revisionId,
    draft.value.version,
    revision.revisionId
  )
  historyVisible.value = false
  inlinePreviewUrl.value = ''
  widePreviewUrl.value = ''
  await loadDraft()
  message.success(`已恢复 v${revision.revisionNo} 为草稿，请预览确认后发布`)
}

const formatHistoryTime = (value?: string) =>
  value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'

const safePreviewDisplayUrl = (value: string) => {
  if (!value) return ''
  try {
    const url = new URL(value)
    return `${url.origin}${url.pathname}`
  } catch {
    return '官网预览'
  }
}

onMounted(() => {
  loadDraft()
  loadSiteConfig()
})
onActivated(() => {
  if (draft.value) loadSiteConfig()
})
</script>

<style scoped lang="scss">
.website-navigation-page {
  display: grid;
  gap: 14px;
}

.navigation-toolbar {
  display: flex;
  min-height: 76px;
  padding: 14px 18px;
  background: var(--furniture-admin-surface, #fff);
  border: 1px solid var(--furniture-admin-border);
  border-radius: var(--furniture-admin-radius, 7px);
  box-shadow: var(--furniture-admin-shadow, 0 1px 3px rgb(15 36 58 / 5%));
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.navigation-toolbar__identity {
  display: flex;
  min-width: 320px;
  align-items: center;
  gap: 12px;
}

.navigation-toolbar__icon {
  display: grid;
  width: 40px;
  height: 40px;
  font-size: 19px;
  color: var(--furniture-admin-primary);
  background: var(--furniture-admin-primary-soft, #edf5ff);
  border-radius: 7px;
  place-items: center;
}

.navigation-toolbar__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.navigation-toolbar__title strong {
  font-size: 15px;
  color: var(--furniture-admin-ink);
}

.navigation-toolbar__identity p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--furniture-admin-muted);
}

.navigation-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.navigation-version {
  display: inline-flex;
  min-height: 32px;
  padding: 0 11px;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border: 1px solid var(--furniture-admin-border);
  border-radius: 5px;
  align-items: center;
  gap: 8px;
}

.navigation-version__divider {
  width: 1px;
  height: 12px;
  background: var(--furniture-admin-border);
}

.navigation-workspace {
  display: grid;
  grid-template-columns: minmax(430px, 0.82fr) minmax(680px, 1.58fr);
  align-items: start;
  gap: 14px;
}

.navigation-editor {
  display: grid;
  gap: 14px;
}

.navigation-change-list {
  display: grid;
  gap: 8px;
}

.navigation-change-list > div {
  display: flex;
  padding: 9px 11px;
  color: var(--furniture-admin-body);
  background: var(--furniture-admin-panel-soft);
  border-radius: 5px;
  align-items: flex-start;
  gap: 8px;
}

.navigation-change-list > div > :first-child {
  margin-top: 2px;
  color: var(--furniture-admin-primary);
  flex: 0 0 auto;
}

.navigation-preview {
  position: sticky;
  top: 14px;
}

.section-header-actions {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: flex-end;
}

.section-description {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--furniture-admin-muted);
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.navigation-list {
  display: grid;
  gap: 8px;
}

.navigation-row {
  display: grid;
  grid-template-columns: 28px 28px minmax(0, 1fr) auto;
  min-height: 66px;
  padding: 9px 11px;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
  align-items: center;
  gap: 9px;
}

.navigation-row:hover {
  border-color: color-mix(
    in srgb,
    var(--furniture-admin-primary) 35%,
    var(--furniture-admin-border)
  );
  box-shadow: 0 4px 14px rgb(15 36 58 / 6%);
}

.navigation-row--category {
  grid-template-columns: 28px 28px minmax(0, 1fr) auto auto 28px;
}

.navigation-row--hidden {
  background: var(--furniture-admin-panel-soft);
}

.navigation-row--unavailable {
  border-color: color-mix(in srgb, var(--furniture-admin-error) 34%, var(--furniture-admin-border));
}

.navigation-row--ghost {
  background: var(--furniture-admin-primary-soft, #edf5ff);
  border-color: var(--furniture-admin-primary);
  opacity: 0.72;
}

.navigation-row__handle {
  display: grid;
  width: 28px;
  height: 32px;
  padding: 0;
  font-size: 16px;
  color: var(--furniture-admin-muted);
  cursor: grab;
  background: transparent;
  border: 0;
  border-radius: 4px;
  place-items: center;
}

.navigation-row__handle:hover,
.navigation-row__handle:focus-visible {
  color: var(--furniture-admin-primary);
  background: var(--furniture-admin-primary-soft, #edf5ff);
  outline: none;
}

.navigation-row__handle:active {
  cursor: grabbing;
}

.navigation-row__order {
  display: grid;
  width: 24px;
  height: 24px;
  font-size: 11px;
  font-weight: 650;
  color: var(--furniture-admin-body);
  background: var(--furniture-admin-panel-soft);
  border-radius: 4px;
  place-items: center;
}

.navigation-row__body {
  min-width: 0;
}

.navigation-row__body strong {
  display: block;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--furniture-admin-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navigation-row__body small {
  display: flex;
  margin-top: 5px;
  overflow: hidden;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
  align-items: center;
  gap: 4px;
}

.navigation-row__body small.is-error {
  color: var(--furniture-admin-error);
}

.navigation-row__visibility {
  display: flex;
  min-width: 88px;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
}

.navigation-row__visibility span {
  font-size: 11px;
  color: var(--furniture-admin-muted);
}

.category-picker {
  display: flex;
  padding: 12px;
  margin-bottom: 12px;
  background: var(--furniture-admin-panel-soft);
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
  gap: 8px;
}

.category-picker__select {
  flex: 1;
}

.category-empty {
  display: flex;
  min-height: 94px;
  padding: 18px;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border: 1px dashed var(--furniture-admin-border);
  border-radius: 6px;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.category-empty > :first-child {
  font-size: 25px;
}

.category-empty strong {
  font-size: 13px;
  color: var(--furniture-admin-body);
}

.category-empty p {
  margin: 4px 0 0;
  font-size: 11px;
}

.category-sync-note,
.preview-security-note {
  display: flex;
  padding: 11px 12px;
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--furniture-admin-muted);
  background: var(--furniture-admin-panel-soft);
  border-radius: 5px;
  align-items: flex-start;
  gap: 8px;
}

.category-sync-note > :first-child,
.preview-security-note > :first-child {
  margin-top: 2px;
  color: var(--furniture-admin-primary);
  flex: 0 0 auto;
}

.preview-browser {
  overflow: hidden;
  background: #111;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 7px;
}

.preview-browser__bar {
  display: flex;
  min-height: 40px;
  padding: 0 12px;
  font-size: 11px;
  color: var(--furniture-admin-muted);
  background: #f7f9fc;
  border-bottom: 1px solid var(--furniture-admin-border);
  align-items: center;
  gap: 7px;
}

.preview-browser__bar span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.preview-browser__frame {
  display: block;
  width: 100%;
  height: min(66vh, 720px);
  background: #fff;
  border: 0;
}

.preview-placeholder {
  display: flex;
  min-height: 470px;
  padding: 34px;
  text-align: center;
  background: linear-gradient(150deg, #f8fafc, #eef3f8);
  border: 1px dashed var(--furniture-admin-border);
  border-radius: 7px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}

.preview-placeholder__icon {
  display: grid;
  width: 54px;
  height: 54px;
  margin-bottom: 14px;
  font-size: 24px;
  color: var(--furniture-admin-primary);
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 50%;
  box-shadow: 0 8px 22px rgb(15 36 58 / 8%);
  place-items: center;
}

.preview-placeholder strong {
  font-size: 15px;
  color: var(--furniture-admin-ink);
}

.preview-placeholder p {
  max-width: 360px;
  margin: 8px 0 18px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--furniture-admin-muted);
}

.preview-security-note {
  align-items: flex-start;
}

.preview-security-note strong {
  font-size: 11px;
  font-weight: 600;
  color: var(--furniture-admin-body);
}

.preview-security-note p {
  margin: 2px 0 0;
}

.wide-preview-frame {
  display: block;
  width: 100%;
  height: calc(94vh - 112px);
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
}

:deep(.wide-preview-dialog .el-dialog__body) {
  padding: 0 18px 18px;
}

@media (width <= 1380px) {
  .navigation-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .navigation-toolbar__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .navigation-workspace {
    grid-template-columns: minmax(410px, 0.9fr) minmax(570px, 1.35fr);
  }
}

@media (width <= 1120px) {
  .navigation-workspace {
    grid-template-columns: 1fr;
  }

  .navigation-preview {
    position: static;
  }
}

@media (width <= 720px) {
  .navigation-toolbar__identity {
    min-width: 0;
  }

  .navigation-toolbar__actions > * {
    flex: 1 1 auto;
  }

  .navigation-version {
    flex-basis: 100%;
  }

  .category-picker {
    flex-direction: column;
  }

  .navigation-row,
  .navigation-row--category {
    grid-template-columns: 24px 24px minmax(0, 1fr) auto;
  }

  .navigation-row--category > .el-tag,
  .navigation-row--category > .navigation-row__visibility {
    grid-column: 3 / -1;
    justify-self: start;
  }
}
</style>
