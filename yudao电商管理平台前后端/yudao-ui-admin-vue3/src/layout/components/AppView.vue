<script lang="ts" setup>
import { useTagsViewStore } from '@/store/modules/tagsView'
import { useAppStore } from '@/store/modules/app'
import { Footer } from '@/layout/components/Footer'
import ErpPageLoading from '@/layout/components/ErpPageLoading.vue'

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

const parentTitle = computed(() => {
  const parent = route.matched
    .slice(0, -1)
    .reverse()
    .find((item) => item.meta?.title)
  if (!parent?.meta?.title) return ''
  const title = t(String(parent.meta.title))
  return title === pageTitle.value ? '' : title
})

const showPageHeading = computed(
  () => !['/dashboard', '/index'].includes(route.path) && route.meta?.hidePageHeading !== true
)

const showPageLoading = computed(() => appStore.getPageLoading)

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
      {
        '!min-h-[calc(100vh-var(--top-tool-height)-var(--tags-view-height)-var(--app-footer-height))] pb-0':
          footer
      }
    ]"
    class="erp-app-view"
    :aria-busy="showPageLoading"
  >
    <Transition name="erp-loading-fade">
      <ErpPageLoading v-if="showPageLoading" :title="pageTitle" overlay />
    </Transition>
    <header v-if="showPageHeading" class="erp-page-heading">
      <div>
        <p v-if="parentTitle">{{ parentTitle }}</p>
        <h1>{{ pageTitle }}</h1>
      </div>
    </header>
    <router-view v-if="routerAlive">
      <template #default="{ Component, route: viewRoute }">
        <keep-alive :include="getCaches">
          <component :is="Component" :key="viewRoute.fullPath" />
        </keep-alive>
      </template>
    </router-view>
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
