<script lang="ts" setup>
defineOptions({ name: 'DashboardPageLoading' })

withDefaults(
  defineProps<{
    overlay?: boolean
  }>(),
  {
    overlay: false
  }
)
</script>

<template>
  <div
    :class="['dashboard-page-loading', { 'dashboard-page-loading--overlay': overlay }]"
    role="status"
    aria-live="polite"
    aria-label="数据看板正在加载"
  >
    <div class="dashboard-loading-shell">
      <div class="dashboard-loading-kicker">
        <span aria-hidden="true"></span>
        OAKVED CONSOLE
      </div>

      <div class="dashboard-loading-preview" aria-hidden="true">
        <div class="preview-toolbar">
          <i></i><i></i><i></i>
          <span></span>
        </div>
        <div class="preview-metrics">
          <div><span></span><strong></strong></div>
          <div><span></span><strong></strong></div>
          <div><span></span><strong></strong></div>
        </div>
        <div class="preview-chart">
          <span class="bar-1"></span>
          <span class="bar-2"></span>
          <span class="bar-3"></span>
          <span class="bar-4"></span>
          <span class="bar-5"></span>
          <span class="bar-6"></span>
          <span class="bar-7"></span>
        </div>
        <div class="dashboard-loading-orbit">
          <div class="orbit-outer"></div>
          <div class="orbit-inner"></div>
          <div class="orbit-core"> <span></span><span></span><span></span> </div>
        </div>
      </div>

      <h2>数据看板加载中</h2>
      <p>正在准备经营指标、趋势图表与商品分析</p>
      <div class="dashboard-loading-progress" aria-hidden="true"><span></span></div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.dashboard-page-loading {
  display: grid;
  min-height: clamp(420px, calc(100vh - 210px), 720px);
  padding: 40px 24px;
  color: var(--el-text-color-primary, #18181b);
  background: var(--app-content-bg-color, #f8fafc);
  place-items: center;
}

.dashboard-page-loading--overlay {
  position: absolute;
  z-index: 30;
  inset: 0;
  min-height: 100%;
}

.dashboard-loading-shell {
  width: min(100%, 520px);
  text-align: center;
}

.dashboard-loading-kicker {
  display: inline-flex;
  margin-bottom: 18px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--el-text-color-secondary, #71717a);
  align-items: center;
  gap: 8px;
}

.dashboard-loading-kicker span {
  width: 7px;
  height: 7px;
  background: var(--el-color-primary, #409eff);
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(64 158 255 / 12%);
  animation: dashboard-loader-pulse 1.4s ease-in-out infinite;
}

.dashboard-loading-preview {
  position: relative;
  width: min(100%, 430px);
  height: 224px;
  margin: 0 auto 24px;
  overflow: hidden;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color, #e4e4e7);
  border-radius: 16px;
  box-shadow: 0 18px 50px rgb(15 23 42 / 8%);
}

.preview-toolbar {
  display: flex;
  height: 35px;
  align-items: center;
  gap: 5px;
  padding: 0 13px;
  border-bottom: 1px solid var(--el-border-color-lighter, #f0f0f2);
}

.preview-toolbar i {
  width: 5px;
  height: 5px;
  background: var(--el-border-color, #d4d4d8);
  border-radius: 50%;
}

.preview-toolbar span {
  width: 72px;
  height: 5px;
  margin-left: auto;
  background: var(--el-fill-color, #f4f4f5);
  border-radius: 999px;
}

.preview-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px 13px 0;
}

.preview-metrics div {
  display: flex;
  height: 52px;
  flex-direction: column;
  gap: 9px;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter, #f0f0f2);
  border-radius: 8px;
}

.preview-metrics span,
.preview-metrics strong {
  display: block;
  height: 6px;
  background: linear-gradient(
    90deg,
    var(--el-fill-color, #f4f4f5) 25%,
    var(--el-fill-color-light, #fafafa) 45%,
    var(--el-fill-color, #f4f4f5) 65%
  );
  background-size: 220% 100%;
  border-radius: 999px;
  animation: dashboard-loader-shimmer 1.8s ease-in-out infinite;
}

.preview-metrics span {
  width: 48%;
}

.preview-metrics strong {
  width: 72%;
  height: 9px;
}

.preview-chart {
  position: absolute;
  right: 16px;
  bottom: 14px;
  left: 16px;
  display: flex;
  height: 96px;
  padding: 14px 16px 0;
  background-image: linear-gradient(
    to bottom,
    transparent 32%,
    var(--el-border-color-extra-light, #f5f5f5) 33%,
    transparent 34%,
    transparent 65%,
    var(--el-border-color-extra-light, #f5f5f5) 66%,
    transparent 67%
  );
  border-bottom: 1px solid var(--el-border-color-lighter, #f0f0f2);
  align-items: flex-end;
  justify-content: space-between;
  gap: 9px;
}

.preview-chart > span {
  width: 9%;
  background: linear-gradient(
    180deg,
    var(--el-color-primary-light-3, #79bbff),
    var(--el-color-primary, #409eff)
  );
  border-radius: 4px 4px 1px 1px;
  animation: dashboard-loader-bars 1.6s ease-in-out infinite alternate;
  transform-origin: bottom;
}

.preview-chart .bar-1 {
  height: 42%;
}

.preview-chart .bar-2 {
  height: 66%;
}

.preview-chart .bar-3 {
  height: 52%;
}

.preview-chart .bar-4 {
  height: 82%;
}

.preview-chart .bar-5 {
  height: 70%;
}

.preview-chart .bar-6 {
  height: 94%;
}

.preview-chart .bar-7 {
  height: 76%;
}

.preview-chart > span:nth-child(2n) {
  animation-delay: -0.55s;
}

.preview-chart > span:nth-child(3n) {
  animation-delay: -0.9s;
}

.dashboard-loading-orbit {
  position: absolute;
  top: 88px;
  left: 50%;
  width: 76px;
  height: 76px;
  background: var(--el-bg-color, #fff);
  border-radius: 50%;
  transform: translateX(-50%);
  box-shadow: 0 8px 26px rgb(15 23 42 / 13%);
}

.orbit-outer,
.orbit-inner {
  position: absolute;
  border-style: solid;
  border-radius: 50%;
}

.orbit-outer {
  border-color: var(--el-color-primary, #409eff) transparent var(--el-color-primary, #409eff)
    var(--el-color-primary-light-7, #c6e2ff);
  border-width: 3px;
  animation: dashboard-loader-spin 1.05s linear infinite;
  inset: 5px;
}

.orbit-inner {
  border-color: var(--el-color-primary-light-5, #a0cfff) var(--el-color-primary-light-8, #d9ecff)
    transparent var(--el-color-primary-light-5, #a0cfff);
  border-width: 3px;
  animation: dashboard-loader-spin-reverse 0.85s linear infinite;
  inset: 13px;
}

.orbit-core {
  position: absolute;
  inset: 24px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
}

.orbit-core span {
  width: 4px;
  background: var(--el-text-color-primary, #18181b);
  border-radius: 2px 2px 0 0;
  animation: dashboard-loader-core 1s ease-in-out infinite alternate;
}

.orbit-core span:nth-child(1) {
  height: 8px;
}

.orbit-core span:nth-child(2) {
  height: 14px;
  animation-delay: -0.35s;
}

.orbit-core span:nth-child(3) {
  height: 11px;
  animation-delay: -0.65s;
}

.dashboard-loading-shell h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.dashboard-loading-shell p {
  margin: 8px 0 18px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #71717a);
}

.dashboard-loading-progress {
  width: 180px;
  height: 3px;
  margin: 0 auto;
  overflow: hidden;
  background: var(--el-fill-color, #f4f4f5);
  border-radius: 999px;
}

.dashboard-loading-progress span {
  display: block;
  width: 45%;
  height: 100%;
  background: var(--el-color-primary, #409eff);
  border-radius: inherit;
  animation: dashboard-loader-progress 1.35s ease-in-out infinite;
}

@keyframes dashboard-loader-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes dashboard-loader-spin-reverse {
  to {
    transform: rotate(-360deg);
  }
}

@keyframes dashboard-loader-pulse {
  50% {
    opacity: 0.72;
    box-shadow: 0 0 0 9px rgb(64 158 255 / 4%);
  }
}

@keyframes dashboard-loader-shimmer {
  to {
    background-position: -220% 0;
  }
}

@keyframes dashboard-loader-bars {
  from {
    opacity: 0.55;
    transform: scaleY(0.45);
  }

  to {
    opacity: 1;
    transform: scaleY(1);
  }
}

@keyframes dashboard-loader-core {
  from {
    transform: scaleY(0.55);
  }

  to {
    transform: scaleY(1);
  }
}

@keyframes dashboard-loader-progress {
  from {
    transform: translateX(-115%);
  }

  to {
    transform: translateX(340%);
  }
}

@media (width <= 600px) {
  .dashboard-page-loading {
    padding: 28px 12px;
  }

  .dashboard-loading-preview {
    height: 206px;
  }

  .preview-chart {
    height: 80px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-page-loading * {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
