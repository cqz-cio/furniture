<script lang="ts" setup>
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'

defineOptions({ name: 'Footer' })

const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('footer')

const appStore = useAppStore()
const title = computed(() => appStore.getTitle)
const currentYear = computed(() => new Date().getFullYear())
</script>

<template>
  <div
    :class="prefixCls"
    class="h-[var(--app-footer-height)] overflow-hidden bg-[var(--app-content-bg-color)] text-center leading-[var(--app-footer-height)] text-[var(--el-text-color-placeholder)] dark:bg-[var(--el-bg-color)]"
  >
    <span class="text-14px">&copy; {{ currentYear }} {{ title }}</span>
  </div>
</template>
