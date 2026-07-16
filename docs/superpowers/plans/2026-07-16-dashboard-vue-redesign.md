# Yudao ERP Dashboard Vue Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing oversized, traffic-first dashboard presentation with the approved operation-result-first desktop experience while preserving the current Vue API, permissions, routing, and backend contracts.

**Architecture:** Keep `src/views/dashboard/index.vue` as the route component and reuse the existing dashboard API calls. Split the existing metric presentation into core-result and traffic-summary view models, consolidate quality messaging, add readable operation-task labels and a nine-column product table preset, then verify the page through source contracts, TypeScript checks, a production build, and a browser screenshot.

**Tech Stack:** Vue 3, TypeScript, Element Plus 2.13, ECharts 6, SCSS, Node.js contract tests.

## Global Constraints

- Keep the independent `/dashboard` menu and `FurnitureDashboard` component name unchanged.
- Do not change Java, SQL, database migrations, tracking rules, API endpoints, tenant isolation, or backend permission enforcement.
- Preserve `statistics:dashboard:query`, `statistics:dashboard:profit-query`, `statistics:dashboard:export`, and `statistics:dashboard:profit-export` semantics.
- Unknown values remain `—`; they must never be rendered as business zero.
- Profit cards and columns are absent when profit-query permission is absent; CSS-only hiding is not allowed.
- Use the approved neutral shadcn-like direction through Vue and Element Plus only; do not add React, Radix, or shadcn runtime dependencies.
- Desktop 1440px is the acceptance target; existing responsive fallbacks must continue to avoid horizontal content overflow.
- Preserve unrelated working-tree changes, especially `pnpm-lock.yaml` and existing untracked folders.

---

### Task 1: Lock the Approved Information Hierarchy in the Dashboard Contract

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`
- Test: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`

**Interfaces:**
- Consumes: the rendered source of `src/views/dashboard/index.vue`.
- Produces: a failing-then-passing executable contract for the redesign structure and safety boundaries.

- [ ] **Step 1: Add source assertions before changing the Vue page**

Add assertions for these exact UI contracts:

```js
for (const token of [
  'coreMetricCards', 'trafficMetricCards', 'qualityStatus', 'periodHighlights',
  '经营结果', '流量与转化', '数据质量', '经营趋势', '周期经营摘要',
  '高流量低转化', '高退款', '低毛利或负毛利', '成本缺失',
  'trafficPreset', 'salesPreset', 'profitPreset', 'tablePreset',
  '排名', '详情 PV', '支付订单', '净销售额', '毛利润', '风险', '操作'
]) assert.ok(page.includes(token), `missing redesigned dashboard token: ${token}`)

assert.equal((page.match(/class="quality-alert"/g) || []).length, 0,
  'quality messages must be consolidated into one quality status panel')
assert.match(page, /v-if="canProfit"[^>]*class="metric-card[^\"]*profit-metric/s,
  'profit result cards must be structurally permission guarded')
```

- [ ] **Step 2: Run the contract and verify RED**

Run: `pnpm.cmd check:dashboard`

Expected: FAIL with `missing redesigned dashboard token: coreMetricCards` because the old page still uses the single `metricCards` collection.

- [ ] **Step 3: Do not modify production code in this task**

The failing contract is the deliverable and the evidence that the test detects the old information hierarchy.

---

### Task 2: Implement the Operation-Result-First Dashboard Page

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/dashboard/index.vue`
- Test: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`

**Interfaces:**
- Consumes: existing `DashboardSummary`, `DashboardTrendItem`, `DashboardAttention`, `DashboardStageOverview`, `DashboardProductPage`, current permission computed values, and the failing contract from Task 1.
- Produces: `coreMetricCards`, `trafficMetricCards`, `qualityStatus`, `periodHighlights`, risk labels/actions, and `tablePreset` state consumed by the template.

- [ ] **Step 1: Replace the single metric model with two explicit models**

The first row must be generated in this order:

```ts
const coreMetricCards = computed(() => [
  { key: 'netRevenue', label: '净销售额', value: money(summary.value?.netRevenue), change: changeText('netRevenue', 'money') },
  { key: 'paidOrderCount', label: '支付订单', value: integer(summary.value?.paidOrderCount), change: changeText('paidOrderCount') },
  ...(canProfit.value ? [
    { key: 'grossProfit', label: '毛利润', value: money(summary.value?.grossProfit), change: changeText('grossProfit', 'money'), profit: true },
    { key: 'grossMarginPercent', label: '毛利率', value: percent(summary.value?.grossMarginPercent), change: changeText('grossMarginPercent', 'rate'), profit: true }
  ] : []),
  { key: 'browseOrderConversionPercent', label: '浏览至下单转化率', value: percent(summary.value?.browseOrderConversionPercent), change: changeText('browseOrderConversionPercent', 'rate') },
  { key: 'refundRate', label: '退款率', value: refundRate.value, change: '' }
])
```

The second row must contain home PV/UV, detail PV/UV, add-cart users, and paid buyers, omitting home metrics in PRODUCT scope.

- [ ] **Step 2: Consolidate data-quality information**

Create one `qualityStatus` computed object with Chinese labels for COMPLETE/PARTIAL/UNAVAILABLE and FRESH/DELAYED/STALE. It must expose summary text, severity, readable `asOf`, earliest available traffic date, affected metrics, and an expandable detail list. Remove the three separate `quality-alert` blocks.

- [ ] **Step 3: Create readable operational task content**

Map risk enums to Chinese business titles and suggested actions:

```ts
const riskMeta = {
  HIGH_TRAFFIC_LOW_CONVERSION: { title: '高流量低转化', action: '检查详情页卖点、价格和购买路径' },
  HIGH_REFUND: { title: '高退款', action: '检查退款原因、商品描述和履约质量' },
  LOW_OR_NEGATIVE_MARGIN: { title: '低毛利或负毛利', action: '核对售价、促销和采购成本' },
  MISSING_COST: { title: '成本缺失', action: '补齐商品成本后重新评估利润' }
} as const
```

Keep the original enum as secondary text and keep the click behavior that switches to product scope and focuses the product table.

- [ ] **Step 4: Recompose the template top-to-bottom**

Render in this order: compact page header, filter toolbar, active filter chips, one data-quality panel, `经营结果`, `流量与转化`, `经营趋势` with `周期经营摘要`, `运营任务`, and product detail. Use native Element Plus controls and keep loading, error, retry, export, permission, and empty states.

- [ ] **Step 5: Reduce the default product table to nine business columns**

Add `tablePreset` with `trafficPreset`, `salesPreset`, and `profitPreset`. The default `salesPreset` renders exactly: ranking, product, detail PV, paid orders, conversion, net sales, gross profit (permission guarded), risk, and action. The action focuses the selected product, while additional legacy fields remain available through the other presets instead of appearing simultaneously.

- [ ] **Step 6: Apply the approved desktop visual system**

Use a white/light-gray background, 1px neutral borders, 10–12px radii, compact 16–20px card padding, black/gray text, blue only for selection/primary action, and semantic colors only for genuine risk. Remove Georgia and the promotional hero treatment. At 1440px, the first viewport must show the result cards and the upper part of the trend section.

- [ ] **Step 7: Run the focused contract and verify GREEN**

Run: `pnpm.cmd check:dashboard`

Expected: `dashboard contract: OK`.

---

### Task 3: Verify, Launch, and Visually Inspect the Real Page

**Files:**
- Verify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/dashboard/index.vue`
- Create local-only screenshot: `C:/Users/admin/.codex/visualizations/2026/07/10/019f49e0-d404-79a3-bcd3-13be8e138cbf/dashboard-audit-2026-07-15/vue-dashboard-redesign-desktop.png`

**Interfaces:**
- Consumes: completed Vue redesign and the existing local runtime.
- Produces: verification evidence, a running local URL, and a visual comparison artifact for user acceptance.

- [ ] **Step 1: Run static and build verification**

Run in the UI directory:

```powershell
pnpm.cmd check:dashboard
pnpm.cmd ts:check
pnpm.cmd build:local
```

Expected: all commands exit 0 without dashboard-related errors.

- [ ] **Step 2: Start the project using the existing local environment**

Start the frontend with `pnpm.cmd dev -- --host 127.0.0.1`. If updated backend code is not required, reuse the running backend; otherwise use the repository lifecycle script without `-Build` because this plan does not change backend code.

- [ ] **Step 3: Capture the dashboard at the same desktop viewport as the audit**

Open `/dashboard`, use the existing authenticated local session if available, and capture the default site-overview state at 1440px wide. Do not enter production credentials or copy production data into artifacts.

- [ ] **Step 4: Compare the old and new screenshots**

Verify the header is shorter, one quality panel replaces three alerts, six result metrics lead the page, risk tasks use Chinese business language, the product table has nine default business columns, and no text, chart, border, or control is clipped.

- [ ] **Step 5: Fix any visible defects and repeat verification**

Apply only targeted CSS/template fixes, rerun the three verification commands, and recapture until the desktop page passes the visual checklist.

## Plan Self-Review Results

- **Spec coverage:** The plan covers the approved desktop hierarchy, filters, consolidated quality state, result and traffic metrics, trend/summary, operation tasks, nine-column table, permissions, safety boundaries, and visual acceptance. Mobile remains a responsive fallback rather than a separate design deliverable.
- **Placeholder scan:** No TBD/TODO or deferred implementation placeholders remain.
- **Type consistency:** The plan reuses the existing dashboard API types and introduces only local computed view models; no backend or shared API signature changes are required.
- **Scope change:** This plan supersedes only the execution medium of the Figma plan. The confirmed design intent remains the source of truth, while formal code implementation is now explicitly authorized by the user.
