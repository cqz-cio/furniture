<script lang="ts" setup>
import type { VNode } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { useDesign } from '@/hooks/web/useDesign'
import { isFurnitureLiteMode } from '@/config/furnitureLite'
import { ERP_PAGE_CONTEXT_KEY } from '@/config/erpPageCatalog'

defineOptions({ name: 'ContentWrap' })

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('content-wrap')

const props = defineProps({
  title: propTypes.string.def(''),
  message: propTypes.string.def(''),
  bodyStyle: propTypes.object.def({ padding: '10px' }),
  surface: propTypes
    .oneOf(['auto', 'filter', 'data', 'form', 'detail', 'tabs', 'panel'])
    .def('auto'),
  autoTitle: propTypes.bool.def(true)
})

type ContentSurface = 'filter' | 'data' | 'form' | 'detail' | 'tabs' | 'panel'

const slots = useSlots()
const pageContext = inject(ERP_PAGE_CONTEXT_KEY, undefined)

const collectNodes = (nodes: VNode[], result: VNode[] = []) => {
  nodes.forEach((node) => {
    result.push(node)
    if (Array.isArray(node.children)) {
      collectNodes(node.children as VNode[], result)
    }
  })
  return result
}

const nodeName = (node: VNode) => {
  if (typeof node.type === 'string') return node.type.toLowerCase()
  if (typeof node.type !== 'object' || node.type === null) return ''
  const component = node.type as { name?: string; __name?: string }
  return String(component.name || component.__name || '').toLowerCase()
}

const resolvedSurface = computed<ContentSurface>(() => {
  if (props.surface !== 'auto') return props.surface as ContentSurface

  const nodes = collectNodes(slots.default?.() || [])
  const hasComponent = (name: string) => nodes.some((node) => nodeName(node).includes(name))
  const form = nodes.find((node) => nodeName(node).includes('elform'))

  if (hasComponent('eltable')) return 'data'
  if (hasComponent('eldescriptions')) return 'detail'
  if (hasComponent('eltabs')) return 'tabs'
  if (form) return form.props?.inline === true ? 'filter' : 'form'
  return 'panel'
})

const surfaceIcon = computed(() => {
  const icons: Record<ContentSurface, string> = {
    filter: 'ep:filter',
    data: 'ep:list',
    form: 'ep:edit-pen',
    detail: 'ep:document',
    tabs: 'ep:collection',
    panel: 'ep:files'
  }
  return icons[resolvedSurface.value]
})

const autoSurfaceTitle = computed(() => {
  if (!isFurnitureLiteMode() || !props.autoTitle || !pageContext?.value) return ''

  const { kind, recordLabel } = pageContext.value
  if (resolvedSurface.value === 'filter' && ['list', 'hierarchy'].includes(kind)) {
    return `${recordLabel}筛选`
  }
  if (resolvedSurface.value === 'data' && ['list', 'hierarchy'].includes(kind)) {
    return kind === 'hierarchy' ? `${recordLabel}结构` : `${recordLabel}列表`
  }
  return ''
})

const displayTitle = computed(() => props.title || autoSurfaceTitle.value)
</script>

<template>
  <ElCard
    :body-style="bodyStyle"
    :class="[
      prefixCls,
      'furniture-admin-content-wrap',
      `erp-content-wrap--${resolvedSurface}`,
      'mb-15px'
    ]"
    :data-erp-surface="resolvedSurface"
    shadow="never"
  >
    <template v-if="displayTitle" #header>
      <div class="erp-content-wrap__header">
        <div class="erp-content-wrap__title">
          <Icon :icon="surfaceIcon" :size="15" />
          <span>{{ displayTitle }}</span>
          <ElTooltip v-if="message" effect="dark" placement="right">
            <template #content>
              <div class="max-w-200px">{{ message }}</div>
            </template>
            <Icon :size="14" class="erp-content-wrap__help" icon="ep:question-filled" />
          </ElTooltip>
        </div>
        <div class="flex flex-grow pl-20px">
          <slot name="header"></slot>
        </div>
      </div>
    </template>
    <slot></slot>
  </ElCard>
</template>
