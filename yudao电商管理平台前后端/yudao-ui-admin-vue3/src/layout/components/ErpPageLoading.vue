<script lang="ts" setup>
import type { ErpPageKind } from '@/config/erpPageCatalog'

defineOptions({ name: 'ErpPageLoading' })

const props = withDefaults(
  defineProps<{
    title?: string
    overlay?: boolean
    variant?: ErpPageKind
  }>(),
  {
    title: '业务页面',
    overlay: false,
    variant: 'list'
  }
)

const loadingDescription = computed(() => {
  const descriptions: Record<ErpPageKind, string> = {
    overview: '正在汇总经营指标与业务提醒',
    list: '正在同步筛选项、数据与操作权限',
    hierarchy: '正在加载结构关系与可用操作',
    analytics: '正在计算指标与分析结果',
    settings: '正在读取配置、校验规则与权限',
    form: '正在准备字段、选项与业务校验',
    detail: '正在聚合单据、状态与处理记录',
    workspace: '正在连接模型、工具与工作上下文'
  }
  return descriptions[props.variant]
})
</script>

<template>
  <div
    :class="[
      'erp-page-loading',
      `erp-page-loading--${variant}`,
      { 'erp-page-loading--overlay': overlay }
    ]"
    role="status"
    aria-live="polite"
    :aria-label="`${title}正在加载`"
  >
    <div class="erp-page-loading__panel">
      <div class="erp-page-loading__brand">
        <span aria-hidden="true"></span>
        OAKVED ERP
      </div>

      <div class="erp-page-loading__status">
        <Icon class="erp-page-loading__spinner" icon="ep:loading" :size="22" />
        <div>
          <strong>{{ title }}正在加载</strong>
          <small>{{ loadingDescription }}</small>
        </div>
      </div>

      <div class="erp-page-loading__skeleton" aria-hidden="true">
        <template v-if="variant === 'list' || variant === 'hierarchy'">
          <div class="erp-page-loading__filters">
            <i></i>
            <i></i>
            <i></i>
            <i class="is-action"></i>
          </div>
          <div class="erp-page-loading__table">
            <div v-for="row in 5" :key="row">
              <i></i>
              <i></i>
              <i></i>
              <i></i>
            </div>
          </div>
        </template>

        <template v-else-if="variant === 'overview' || variant === 'analytics'">
          <div class="erp-page-loading__metrics">
            <div v-for="metric in 4" :key="metric">
              <i></i>
              <i></i>
            </div>
          </div>
          <div class="erp-page-loading__analytics">
            <div class="erp-page-loading__chart">
              <i v-for="bar in 8" :key="bar" :style="{ height: `${24 + (bar % 4) * 13}%` }"></i>
            </div>
            <div class="erp-page-loading__summary">
              <i v-for="item in 5" :key="item"></i>
            </div>
          </div>
        </template>

        <template v-else-if="variant === 'form' || variant === 'settings'">
          <div class="erp-page-loading__form">
            <div v-for="field in 6" :key="field">
              <i></i>
              <i></i>
            </div>
          </div>
          <div class="erp-page-loading__form-actions">
            <i></i>
            <i class="is-action"></i>
          </div>
        </template>

        <template v-else-if="variant === 'detail'">
          <div class="erp-page-loading__detail-summary">
            <i></i>
            <i></i>
            <i></i>
            <i></i>
          </div>
          <div class="erp-page-loading__detail">
            <div v-for="section in 3" :key="section">
              <i></i>
              <i></i>
              <i></i>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="erp-page-loading__workspace">
            <div>
              <i v-for="item in 5" :key="item"></i>
            </div>
            <div>
              <i></i>
              <i></i>
              <i></i>
            </div>
          </div>
        </template>
      </div>

      <div class="erp-page-loading__progress" aria-hidden="true"><span></span></div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.erp-page-loading {
  display: grid;
  min-height: clamp(360px, calc(100vh - 190px), 650px);
  padding: 32px;
  color: var(--furniture-admin-ink, #132033);
  background: var(--app-content-bg-color, #f5f7fa);
  place-items: center;
}

.erp-page-loading--overlay {
  position: absolute;
  z-index: 40;
  inset: 0;
  min-height: 100%;
}

.erp-page-loading__panel {
  width: min(100%, 680px);
  padding: 22px;
  background: rgb(255 255 255 / 96%);
  border: 1px solid var(--furniture-admin-border, #dfe4ea);
  border-radius: 12px;
  box-shadow: 0 20px 50px rgb(15 36 58 / 10%);
}

.erp-page-loading__brand {
  display: flex;
  margin-bottom: 20px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--furniture-admin-muted, #7b8796);
  align-items: center;
  gap: 8px;
}

.erp-page-loading__brand span {
  width: 7px;
  height: 7px;
  background: var(--el-color-primary, #176bdb);
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(23 107 219 / 10%);
  animation: erp-loader-pulse 1.4s ease-in-out infinite;
}

.erp-page-loading__status {
  display: flex;
  margin-bottom: 18px;
  align-items: center;
  gap: 12px;
}

.erp-page-loading__spinner {
  flex: 0 0 auto;
  color: var(--el-color-primary, #176bdb);
  animation: erp-loader-spin 0.9s linear infinite;
}

.erp-page-loading__status strong,
.erp-page-loading__status small {
  display: block;
}

.erp-page-loading__status strong {
  font-size: 15px;
  font-weight: 650;
}

.erp-page-loading__status small {
  margin-top: 3px;
  font-size: 12px;
  color: var(--furniture-admin-muted, #7b8796);
}

.erp-page-loading__skeleton {
  overflow: hidden;
  border: 1px solid var(--furniture-admin-border, #dfe4ea);
  border-radius: 8px;
}

.erp-page-loading__filters {
  display: grid;
  grid-template-columns: 1fr 1fr 1.4fr 80px;
  gap: 10px;
  padding: 13px;
  background: #fff;
  border-bottom: 1px solid var(--furniture-admin-border, #dfe4ea);
}

.erp-page-loading__filters i,
.erp-page-loading__table i,
.erp-page-loading__metrics i,
.erp-page-loading__analytics i,
.erp-page-loading__form i,
.erp-page-loading__form-actions i,
.erp-page-loading__detail-summary i,
.erp-page-loading__detail i,
.erp-page-loading__workspace i {
  display: block;
  height: 12px;
  background: linear-gradient(90deg, #eef1f4 25%, #f8f9fb 45%, #eef1f4 68%);
  background-size: 240% 100%;
  border-radius: 4px;
  animation: erp-loader-shimmer 1.45s ease-in-out infinite;
}

.erp-page-loading__filters i {
  height: 34px;
  background-color: #f7f8fa;
  border: 1px solid #e6e9ed;
}

.erp-page-loading__filters .is-action {
  background: rgb(23 107 219 / 12%);
  border-color: rgb(23 107 219 / 15%);
}

.erp-page-loading__table > div {
  display: grid;
  grid-template-columns: 2fr 0.7fr 1fr 0.6fr;
  gap: 24px;
  height: 50px;
  padding: 0 16px;
  border-bottom: 1px solid #edf0f3;
  align-items: center;
}

.erp-page-loading__table > div:last-child {
  border-bottom: 0;
}

.erp-page-loading__table i:nth-child(2n) {
  animation-delay: -0.4s;
}

.erp-page-loading__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 13px;
  background: #fff;
  border-bottom: 1px solid var(--furniture-admin-border, #dfe4ea);
}

.erp-page-loading__metrics > div {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e7ebef;
  border-radius: 6px;
}

.erp-page-loading__metrics i:first-child {
  width: 55%;
  height: 10px;
}

.erp-page-loading__metrics i:last-child {
  width: 38%;
  height: 24px;
}

.erp-page-loading__analytics {
  display: grid;
  grid-template-columns: 1.55fr 0.8fr;
  gap: 18px;
  min-height: 210px;
  padding: 18px;
}

.erp-page-loading__chart {
  display: flex;
  height: 174px;
  padding: 10px 12px 0;
  border-bottom: 1px solid #e7ebef;
  align-items: flex-end;
  justify-content: space-between;
  gap: 9px;
}

.erp-page-loading__chart i {
  width: 100%;
  min-height: 32px;
  background: rgb(23 107 219 / 14%);
  animation-delay: -0.25s;
}

.erp-page-loading__summary {
  display: grid;
  align-content: center;
  gap: 16px;
}

.erp-page-loading__summary i {
  height: 14px;
}

.erp-page-loading__form {
  display: grid;
  gap: 15px;
  padding: 18px;
}

.erp-page-loading__form > div {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.erp-page-loading__form > div i:first-child {
  width: 78%;
  justify-self: end;
}

.erp-page-loading__form > div i:last-child {
  height: 34px;
  border: 1px solid #e6e9ed;
}

.erp-page-loading__form-actions {
  display: flex;
  padding: 13px 18px;
  background: #fafbfc;
  border-top: 1px solid var(--furniture-admin-border, #dfe4ea);
  justify-content: flex-end;
  gap: 10px;
}

.erp-page-loading__form-actions i {
  width: 72px;
  height: 32px;
}

.erp-page-loading__form-actions .is-action {
  background: rgb(23 107 219 / 13%);
}

.erp-page-loading__detail-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  background: var(--furniture-admin-border, #dfe4ea);
  border-bottom: 1px solid var(--furniture-admin-border, #dfe4ea);
}

.erp-page-loading__detail-summary i {
  height: 58px;
  background-color: #fff;
  border-radius: 0;
}

.erp-page-loading__detail {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.erp-page-loading__detail > div {
  display: grid;
  grid-template-columns: 0.55fr 1.5fr 1fr;
  gap: 16px;
  padding: 14px;
  border: 1px solid #e7ebef;
  border-radius: 6px;
}

.erp-page-loading__workspace {
  display: grid;
  grid-template-columns: 0.36fr 1fr;
  min-height: 300px;
  background: #f7f9fb;
}

.erp-page-loading__workspace > div {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 18px;
}

.erp-page-loading__workspace > div:first-child {
  background: #fff;
  border-right: 1px solid var(--furniture-admin-border, #dfe4ea);
}

.erp-page-loading__workspace > div:last-child {
  align-content: center;
  padding: 42px;
}

.erp-page-loading__workspace > div:last-child i:nth-child(2) {
  width: 72%;
}

.erp-page-loading__workspace > div:last-child i:nth-child(3) {
  width: 44%;
}

.erp-page-loading__progress {
  height: 3px;
  margin-top: 18px;
  overflow: hidden;
  background: #edf1f5;
  border-radius: 999px;
}

.erp-page-loading__progress span {
  display: block;
  width: 32%;
  height: 100%;
  background: var(--el-color-primary, #176bdb);
  border-radius: inherit;
  animation: erp-loader-progress 1.2s ease-in-out infinite;
}

@keyframes erp-loader-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes erp-loader-pulse {
  50% {
    opacity: 0.65;
    box-shadow: 0 0 0 9px rgb(23 107 219 / 4%);
  }
}

@keyframes erp-loader-shimmer {
  to {
    background-position: -240% 0;
  }
}

@keyframes erp-loader-progress {
  from {
    transform: translateX(-110%);
  }

  to {
    transform: translateX(320%);
  }
}

@media (width <= 760px) {
  .erp-page-loading {
    padding: 16px;
  }

  .erp-page-loading__panel {
    padding: 16px;
  }

  .erp-page-loading__filters {
    grid-template-columns: 1fr 1fr 72px;
  }

  .erp-page-loading__filters i:nth-child(3) {
    display: none;
  }

  .erp-page-loading__metrics,
  .erp-page-loading__detail-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .erp-page-loading__analytics,
  .erp-page-loading__workspace {
    grid-template-columns: 1fr;
  }

  .erp-page-loading__workspace > div:first-child {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .erp-page-loading * {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
