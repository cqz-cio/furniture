<script setup lang="ts">
import { provide, computed, watch, onMounted } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { ComponentSize, ElConfigProvider } from 'element-plus'
import type { Language } from 'element-plus/es/locale'
import { useLocaleStore } from '@/store/modules/locale'
import { useWindowSize } from '@vueuse/core'
import { useAppStore } from '@/store/modules/app'
import { setCssVar } from '@/utils'
import { useDesign } from '@/hooks/web/useDesign'

const { variables } = useDesign()

const appStore = useAppStore()

const props = defineProps({
  size: propTypes.oneOf<ComponentSize>(['default', 'small', 'large']).def('default')
})

provide('configGlobal', props)

// 初始化所有主题色
onMounted(() => {
  appStore.setCssVarTheme()
})

const { width } = useWindowSize()

// 监听窗口变化
watch(
  () => width.value,
  (width: number) => {
    if (width < 768) {
      !appStore.getMobile ? appStore.setMobile(true) : undefined
      setCssVar('--left-menu-min-width', '0')
      appStore.setCollapse(true)
      appStore.getLayout !== 'classic' ? appStore.setLayout('classic') : undefined
    } else {
      appStore.getMobile ? appStore.setMobile(false) : undefined
      setCssVar('--left-menu-min-width', '64px')
    }
  },
  {
    immediate: true
  }
)

// 多语言相关
const localeStore = useLocaleStore()

const currentLocale = computed(() => localeStore.currentLocale)
const erpLocale = computed<Language | undefined>(() => {
  const locale = currentLocale.value.elLocale
  if (!locale) {
    return undefined
  }
  const tableLocale =
    typeof locale.el.table === 'object' && !Array.isArray(locale.el.table) ? locale.el.table : {}
  return {
    ...locale,
    el: {
      ...locale.el,
      table: {
        ...tableLocale,
        emptyText: '暂无符合当前条件的数据'
      }
    }
  }
})
</script>

<template>
  <ElConfigProvider
    :namespace="variables.elNamespace"
    :locale="erpLocale"
    :message="{ max: 5 }"
    :size="size"
  >
    <slot></slot>
  </ElConfigProvider>
</template>
