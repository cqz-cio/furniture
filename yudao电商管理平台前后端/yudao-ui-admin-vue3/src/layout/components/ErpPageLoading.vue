<script lang="ts" setup>
defineOptions({ name: 'ErpPageLoading' })

withDefaults(
  defineProps<{
    title?: string
    overlay?: boolean
  }>(),
  {
    title: '业务页面',
    overlay: false
  }
)
</script>

<template>
  <div
    :class="['erp-page-loading', { 'erp-page-loading--overlay': overlay }]"
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
          <small>正在同步页面数据与操作权限</small>
        </div>
      </div>

      <div class="erp-page-loading__skeleton" aria-hidden="true">
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
  color: var(--furniture-admin-muted, #7b8796);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
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
  color: var(--furniture-admin-muted, #7b8796);
  font-size: 12px;
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
.erp-page-loading__table i {
  display: block;
  height: 12px;
  background: linear-gradient(90deg, #eef1f4 25%, #f8f9fb 45%, #eef1f4 68%);
  background-size: 240% 100%;
  border-radius: 4px;
  animation: erp-loader-shimmer 1.45s ease-in-out infinite;
}

.erp-page-loading__filters i {
  height: 34px;
  border: 1px solid #e6e9ed;
  background-color: #f7f8fa;
}

.erp-page-loading__filters .is-action {
  border-color: rgb(23 107 219 / 15%);
  background: rgb(23 107 219 / 12%);
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
}

@media (prefers-reduced-motion: reduce) {
  .erp-page-loading * {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
