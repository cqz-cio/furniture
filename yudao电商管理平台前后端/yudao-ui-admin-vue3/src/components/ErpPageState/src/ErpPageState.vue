<script lang="ts" setup>
type ErpPageStateType = 'empty' | 'error' | 'forbidden' | 'not-found' | 'unavailable'

defineOptions({ name: 'ErpPageState' })

const props = withDefaults(
  defineProps<{
    type?: ErpPageStateType
    eyebrow?: string
    title: string
    description: string
    primaryText?: string
    secondaryText?: string
    primaryLoading?: boolean
    compact?: boolean
  }>(),
  {
    type: 'empty',
    eyebrow: '页面状态',
    primaryText: '',
    secondaryText: '',
    primaryLoading: false,
    compact: false
  }
)

const emit = defineEmits<{
  primary: []
  secondary: []
}>()

const stateMeta = computed(() => {
  const metadata: Record<
    ErpPageStateType,
    { icon: string; tone: 'neutral' | 'warning' | 'danger' }
  > = {
    empty: { icon: 'ep:document', tone: 'neutral' },
    error: { icon: 'ep:warning-filled', tone: 'danger' },
    forbidden: { icon: 'ep:lock', tone: 'warning' },
    'not-found': { icon: 'ep:search', tone: 'neutral' },
    unavailable: { icon: 'ep:connection', tone: 'warning' }
  }
  return metadata[props.type]
})
</script>

<template>
  <section
    :aria-live="type === 'error' || type === 'unavailable' ? 'assertive' : 'polite'"
    :class="[
      'erp-page-state',
      `erp-page-state--${stateMeta.tone}`,
      { 'erp-page-state--compact': compact }
    ]"
    :role="type === 'error' || type === 'unavailable' ? 'alert' : 'status'"
  >
    <div class="erp-page-state__icon" aria-hidden="true">
      <Icon :icon="stateMeta.icon" :size="compact ? 22 : 26" />
    </div>
    <div class="erp-page-state__content">
      <p class="erp-page-state__eyebrow">{{ eyebrow }}</p>
      <h2>{{ title }}</h2>
      <p class="erp-page-state__description">{{ description }}</p>
      <div v-if="primaryText || secondaryText" class="erp-page-state__actions">
        <el-button
          v-if="primaryText"
          :loading="primaryLoading"
          type="primary"
          @click="emit('primary')"
        >
          <Icon v-if="!primaryLoading" class="mr-6px" icon="ep:refresh" :size="15" />
          {{ primaryText }}
        </el-button>
        <el-button v-if="secondaryText" @click="emit('secondary')">
          {{ secondaryText }}
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.erp-page-state {
  display: grid;
  min-height: 250px;
  padding: 40px clamp(24px, 5vw, 64px);
  background: linear-gradient(135deg, rgb(23 107 219 / 4%), transparent 42%), var(--el-bg-color);
  border: 1px solid var(--furniture-admin-border, var(--el-border-color-light));
  border-radius: 10px;
  grid-template-columns: auto minmax(0, 620px);
  place-content: center;
  gap: 18px;
}

.erp-page-state--compact {
  min-height: 168px;
  padding-block: 28px;
}

.erp-page-state__icon {
  display: flex;
  width: 48px;
  height: 48px;
  color: var(--furniture-admin-primary, var(--el-color-primary));
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 10px;
  align-items: center;
  justify-content: center;
}

.erp-page-state--warning .erp-page-state__icon {
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-7);
}

.erp-page-state--danger .erp-page-state__icon {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-7);
}

.erp-page-state__content {
  min-width: 0;
}

.erp-page-state__eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 680;
  letter-spacing: 0.08em;
  color: var(--furniture-admin-muted, var(--el-text-color-secondary));
  text-transform: uppercase;
}

.erp-page-state h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 680;
  line-height: 1.35;
  color: var(--furniture-admin-ink, var(--el-text-color-primary));
}

.erp-page-state__description {
  max-width: 600px;
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--furniture-admin-body, var(--el-text-color-regular));
}

.erp-page-state__actions {
  display: flex;
  margin-top: 20px;
  flex-wrap: wrap;
  gap: 8px;
}

.erp-page-state__actions :deep(.el-button) {
  min-width: 96px;
  margin-left: 0;
}

@media (width <= 640px) {
  .erp-page-state {
    min-height: 220px;
    padding: 28px 20px;
    grid-template-columns: 1fr;
  }
}
</style>
