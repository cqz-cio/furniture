<script lang="ts" setup>
import { useTagsViewStore } from '@/store/modules/tagsView'
import { useAppStore } from '@/store/modules/app'
import { Footer } from '@/layout/components/Footer'
import ErpPageLoading from '@/layout/components/ErpPageLoading.vue'
import { ERP_PAGE_CONTEXT_KEY, resolveErpPageContext } from '@/config/erpPageCatalog'

defineOptions({ name: 'AppView' })

const appStore = useAppStore()
const route = useRoute()
const { t } = useI18n()

const footer = computed(() => appStore.getFooter)

const tagsViewStore = useTagsViewStore()

const getCaches = computed((): string[] => {
  return tagsViewStore.getCachedViews
})

const pageTitle = computed(() => {
  const rawTitle = String(route.meta?.title || '业务页面')
  return rawTitle ? t(rawTitle) : '业务页面'
})

const pageContext = computed(() => resolveErpPageContext(route.path, pageTitle.value))
provide(ERP_PAGE_CONTEXT_KEY, pageContext)

const showPageHeading = computed(
  () =>
    !['/dashboard', '/index'].includes(route.path) &&
    route.meta?.hidePageHeading !== true &&
    !pageContext.value.immersive
)

const showPageLoading = computed(() => appStore.getPageLoading)
const canToggleDensity = computed(() => ['list', 'hierarchy'].includes(pageContext.value.kind))
const compactView = ref(false)
const densityStorageKey = 'oakved:erp-table-density'

const toggleDensity = () => {
  compactView.value = !compactView.value
}

onMounted(() => {
  compactView.value = window.localStorage.getItem(densityStorageKey) === 'compact'
})

watch(compactView, (value) => {
  window.localStorage.setItem(densityStorageKey, value ? 'compact' : 'standard')
})

//region 无感刷新
const routerAlive = ref(true)
// 无感刷新，防止出现页面闪烁白屏
const reload = () => {
  routerAlive.value = false
  nextTick(() => (routerAlive.value = true))
}
// 为组件后代提供刷新方法
provide('reload', reload)
//endregion
</script>

<template>
  <section
    :class="[
      'relative p-[var(--app-content-padding)] w-full bg-[var(--app-content-bg-color)] dark:bg-[var(--el-bg-color)]',
      'erp-app-view',
      `erp-page--${pageContext.kind}`,
      `erp-module--${pageContext.moduleKey}`,
      {
        '!min-h-[calc(100vh-var(--top-tool-height)-var(--tags-view-height)-var(--app-footer-height))] pb-0':
          footer,
        'erp-app-view--immersive': pageContext.immersive,
        'erp-density--compact': compactView
      }
    ]"
    :aria-busy="showPageLoading"
    :data-erp-page-kind="pageContext.kind"
    :data-erp-module="pageContext.moduleKey"
  >
    <Transition name="erp-loading-fade">
      <ErpPageLoading
        v-if="showPageLoading"
        :title="pageTitle"
        :variant="pageContext.kind"
        overlay
      />
    </Transition>
    <header v-if="showPageHeading" class="erp-page-heading">
      <div class="erp-page-heading__copy">
        <div class="erp-page-heading__eyebrow">
          <Icon icon="ep:collection-tag" :size="14" />
          <span>{{ pageContext.moduleLabel }}</span>
          <span aria-hidden="true">/</span>
          <span>{{ pageContext.kindLabel }}</span>
        </div>
        <h1>{{ pageTitle }}</h1>
        <p class="erp-page-heading__description">{{ pageContext.description }}</p>
      </div>
      <div v-if="canToggleDensity" class="erp-page-heading__actions">
        <el-tooltip :content="compactView ? '切换为标准密度' : '切换为紧凑密度'" placement="bottom">
          <el-button
            class="erp-density-toggle"
            :aria-label="compactView ? '切换为标准密度' : '切换为紧凑密度'"
            :aria-pressed="compactView"
            @click="toggleDensity"
          >
            <Icon :icon="compactView ? 'ep:expand' : 'ep:grid'" :size="15" />
            <span>{{ compactView ? '紧凑' : '标准' }}</span>
          </el-button>
        </el-tooltip>
      </div>
    </header>
    <div v-if="routerAlive" class="erp-page-stage">
      <router-view>
        <template #default="{ Component, route: viewRoute }">
          <keep-alive :include="getCaches">
            <component :is="Component" :key="viewRoute.fullPath" />
          </keep-alive>
        </template>
      </router-view>
    </div>
  </section>
  <Footer v-if="footer" />
</template>

<style scoped>
.erp-loading-fade-enter-active,
.erp-loading-fade-leave-active {
  transition: opacity 0.16s ease;
}

.erp-loading-fade-enter-from,
.erp-loading-fade-leave-to {
  opacity: 0;
}
</style>
