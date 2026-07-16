# Yudao ERP Dashboard Figma Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and visually verify an editable 1440px desktop redesign of the Yudao ERP data dashboard in the existing Figma audit file.

**Architecture:** The redesign is created on one new Figma page as a single desktop application frame containing the Yudao shell and a vertically structured dashboard content area. Work proceeds top-down in small `use_figma` calls, returning node IDs after every write and validating each major section with metadata and screenshots before continuing.

**Tech Stack:** Figma Design file, Figma Plugin API through `use_figma`, Figma screenshot/metadata tools, Noto Sans SC, existing Yudao Vue 3 + Element Plus + ECharts implementation constraints.

## Global Constraints

- Target file: `https://www.figma.com/design/IqGVhFyza36A06sADO2bJ4` (`fileKey=IqGVhFyza36A06sADO2bJ4`).
- Source spec: `docs/superpowers/specs/2026-07-16-dashboard-ui-redesign-design.md`.
- Create one new page named exactly `修改版｜桌面端`; preserve the existing audit page and its nodes.
- Target frame size: 1440px wide; height may grow to contain the full dashboard without clipping.
- Use an editable Figma layout, not a screenshot as the redesigned screen.
- Use Noto Sans SC with exact available styles `Regular`, `Medium`, and `Bold`.
- Use neutral shadcn-like styling: white cards, black/gray text, light gray 1px borders, restrained shadows, 10-12px radii, blue only for selection and primary actions.
- Use example business data only; do not place customer data, orders, credentials, tokens, secrets, or production identifiers in Figma.
- Preserve permission semantics: profit cards/actions are visibly identified as permission-controlled; the mockup does not weaken backend authorization.
- Do not modify Vue, Java, SQL, API, database, menu, permission, or tracking code during this plan.
- Every `use_figma` write must pass `skillNames: "figma-use"`, return every created/mutated node ID, and stop for diagnosis on error.
- At most 10 logical node operations per `use_figma` call; create large sections incrementally.
- Final handoff requires a direct Figma link and a locally saved preview PNG.

---

## File and Node Structure

No repository source file is modified during Figma execution. The plan creates these Figma nodes:

```text
Page: 修改版｜桌面端
└── Frame: Dashboard Redesign / Desktop 1440
    ├── Frame: App Sidebar
    ├── Frame: App Topbar
    └── Frame: Dashboard Content
        ├── Frame: Page Header
        ├── Frame: Filter Toolbar
        ├── Frame: Data Quality Bar
        ├── Frame: Core KPI Grid
        ├── Frame: Traffic Summary Grid
        ├── Frame: Trend and Summary
        ├── Frame: Operation Tasks
        └── Frame: Product Table
```

The root uses a fixed 1440px width. The sidebar uses 208px; the dashboard content uses the remaining width with 24px internal padding. Major content sections use vertical auto-layout so text and rows do not overlap when content changes.

---

### Task 1: Inspect the Figma File and Create the Redesign Skeleton

**Figma target:**
- Inspect: file `IqGVhFyza36A06sADO2bJ4`
- Create page: `修改版｜桌面端`
- Create frame: `Dashboard Redesign / Desktop 1440`

**Interfaces:**
- Consumes: existing Figma audit file and confirmed design spec.
- Produces: page ID, root frame ID, sidebar ID, topbar ID, and dashboard content ID for later tasks.

- [ ] **Step 1: Inspect existing pages and conventions**

Call `get_metadata` without `nodeId`, then inspect the existing page list. Expected: the existing audit page remains available and no page named `修改版｜桌面端` is overwritten.

- [ ] **Step 2: Discover exact fonts**

Call `use_figma` read-only with `listAvailableFontsAsync()` and return Noto Sans SC entries. Expected styles: `Regular`, `Medium`, `Bold`.

- [ ] **Step 3: Create the page and root frame**

Create a new design page and these fixed top-level dimensions:

```text
Dashboard Redesign / Desktop 1440: 1440 x 2400 initially
App Sidebar: 208 x 2400
App Topbar: 1232 x 56, x=208, y=0
Dashboard Content: 1232 x 2344, x=208, y=56
```

Use `figma.createPage()`, switch once with `await figma.setCurrentPageAsync(page)`, create the root frame, and return all IDs.

- [ ] **Step 4: Validate the skeleton**

Call `get_metadata` on the root frame. Expected: exactly three first-level areas (sidebar, topbar, content), no overlap, no content at page coordinate `(0,0)` outside the root frame.

---

### Task 2: Build the Yudao Shell, Page Header, and Filters

**Figma nodes:**
- Modify: `App Sidebar`, `App Topbar`, `Dashboard Content`
- Create: `Page Header`, `Filter Toolbar`, filter control groups and labels.

**Interfaces:**
- Consumes: skeleton node IDs from Task 1.
- Produces: header ID, toolbar ID, active navigation item ID, and filter control IDs.

- [ ] **Step 1: Build the sidebar**

Create the brand `Oakved.` and six navigation labels. Highlight `数据看板` with a blue-tinted background and blue text; keep other items neutral. Do not use emoji or text-symbol icons.

- [ ] **Step 2: Build the topbar**

Create a breadcrumb label `数据看板` on the left and account label `运营管理员` on the right. Keep the topbar 56px high with a bottom border.

- [ ] **Step 3: Build the compact page header**

Add:

```text
数据看板
快速了解销售、利润、转化和需要处理的商品问题
数据截至 2026-07-15 23:50（Asia/Shanghai）
刷新数据 | 导出
```

The title area must fit within 112px height and must not recreate the previous oversized promotional card.

- [ ] **Step 4: Build the filter toolbar**

Create editable controls for:

```text
全站经营 | 商品分析
今日 | 近 7 日 | 近 30 日 | 近 90 日
2026-06-16 — 2026-07-15
对比上一周期：开启
更多筛选
重置
```

Use a single 72px toolbar row at desktop width. Show `近 30 日` as selected. Place advanced product filters behind `更多筛选` in the default mockup to reduce first-screen density.

- [ ] **Step 5: Validate shell and filters**

Render a screenshot of the upper 1440x360 region. Expected: dashboard navigation is visually selected, every control has readable text, header actions fit, and no element clips at the right edge.

---

### Task 3: Build Data Quality and Core Result Metrics

**Figma nodes:**
- Create: `Data Quality Bar`, `Core KPI Grid`, six KPI cards.

**Interfaces:**
- Consumes: dashboard content ID from Task 1 and toolbar ID from Task 2.
- Produces: data quality bar ID and six KPI card IDs.

- [ ] **Step 1: Create the consolidated quality bar**

Use one compact status row:

```text
数据状态：部分流量数据可用，当前结果截至 7 月 15 日 23:50
受影响：浏览量、访客数和转化率 | 查看详情 | 查看任务
```

Use a pale amber background, dark text, and a visible `部分数据` label. Do not repeat the message in multiple alerts.

- [ ] **Step 2: Create KPI card foundations**

Create a 3-column by 2-row grid within the 1184px content width. Each card is approximately 381px wide and 132px high with a 16px gap.

- [ ] **Step 3: Populate six result cards**

Use these exact sample values and comparison copy:

| Metric | Value | Comparison |
|---|---:|---|
| 净销售额 | `$196,910` | `较上周期 +8.4%` |
| 支付订单 | `870` | `较上周期 +5.2%` |
| 毛利润 | `$63,480` | `较上周期 +11.6%` |
| 毛利率 | `32.24%` | `较上周期 +0.9 个百分点` |
| 浏览至下单转化率 | `4.86%` | `较上周期 -0.3 个百分点` |
| 退款率 | `9.58%` | `较上周期 +1.2 个百分点` |

Use red only for the adverse conversion/refund direction; use neutral or blue for other comparisons. Add a small `口径` text action on each card.

- [ ] **Step 4: Validate KPI readability**

Render the quality bar and KPI grid. Expected: all six labels, values, units, and comparisons are visible; no color is the only indicator of direction; no number wraps unexpectedly.

---

### Task 4: Build Traffic Summary, Trend, and Business Summary

**Figma nodes:**
- Create: `Traffic Summary Grid`, `Trend Panel`, `Business Summary Panel`.

**Interfaces:**
- Consumes: core KPI grid ID from Task 3.
- Produces: six traffic summary IDs, trend panel ID, and business summary panel ID.

- [ ] **Step 1: Build six compact traffic summaries**

Use one row of six compact cards:

```text
首页浏览量 42,680
首页访客数 26,430
商品详情浏览量 17,890
商品详情访客数 11,240
加购人数 2,581
支付买家 812
```

Add a secondary caption stating that traffic represents measurable consented visits and may have coverage bias.

- [ ] **Step 2: Build the trend panel**

Create a 760px-wide panel titled `经营趋势`, with metric toggles `净销售额 / 毛利润 / 浏览量`. Draw a restrained 30-day line/bar visualization with axis labels, a legend, and a text action `查看数据表`.

- [ ] **Step 3: Build the business summary panel**

Create a 408px-wide panel titled `本周期摘要` with four rows:

```text
净销售额增长 8.4%
毛利润增长 11.6%
退款率上升 1.2 个百分点
3 个商品需要关注
```

Each row includes a plain-language explanation; the adverse rows include a text label, not color alone.

- [ ] **Step 4: Validate trend section**

Render the complete trend and summary row. Expected: the panels align, the chart remains readable at 1440px, the summary does not overflow, and `查看数据表` is visible.

---

### Task 5: Build Operational Tasks and the Product Table

**Figma nodes:**
- Create: `Operation Tasks`, four task cards, `Product Table`, table header, three data rows, pagination/footer.

**Interfaces:**
- Consumes: dashboard content layout and business summary from Task 4.
- Produces: task card IDs and product table ID.

- [ ] **Step 1: Build the operational task section**

Create four cards with exact business-first titles:

```text
高浏览低转化｜云朵模块沙发｜建议检查价格和商品页内容
高退款风险｜奶油布艺沙发｜退款率 18.4%，查看售后原因
负毛利风险｜真皮休闲沙发｜毛利率 -3.2%，检查折扣和成本
成本缺失｜北欧单椅｜12 条销售明细缺少成本
```

Each card displays severity, reason, impact, suggested next action, assignee/status placeholders, and `查看商品`. Technical enums appear only in muted secondary text.

- [ ] **Step 2: Build table controls**

Add title `商品经营明细`, a keyword search, column presets `流量 / 销售 / 利润`, `列设置`, and `保存视图`. Show `销售` as selected.

- [ ] **Step 3: Build the default nine-column table**

Use these columns:

```text
排名 | 商品 | 详情 PV | 支付订单 | 转化率 | 净销售额 | 毛利润 | 风险 | 操作
```

Add three realistic furniture product rows using only sample data. The product and operation columns appear visually fixed. Use text tags such as `高退款` or `成本缺失` so status is not color-only.

- [ ] **Step 4: Add pagination and table explanation**

Add `共 128 件商品`, page size `20 条/页`, and pagination controls. Add a small note: `分类、库存和上架状态为当前值；历史指标按所选日期统计。`

- [ ] **Step 5: Validate the lower dashboard**

Render tasks and table. Expected: task actions are obvious, the table has no more than nine default columns, product names and dollar amounts do not clip, and the bottom of the table remains inside the root frame.

---

### Task 6: Final Visual QA and Handoff

**Files:**
- Create local preview: `C:/Users/admin/.codex/visualizations/2026/07/10/019f49e0-d404-79a3-bcd3-13be8e138cbf/dashboard-audit-2026-07-15/figma-dashboard-redesign-desktop.png`
- Preserve local reference: `C:/Users/admin/.codex/visualizations/2026/07/10/019f49e0-d404-79a3-bcd3-13be8e138cbf/dashboard-audit-2026-07-15/01-site-overview.png`

**Interfaces:**
- Consumes: completed root frame and original accepted audit screenshot.
- Produces: verified Figma link, root node link, and local preview image.

- [ ] **Step 1: Inspect final metadata**

Call `get_metadata` on the root frame. Expected: all eight dashboard content sections exist in order; no top-level node is misplaced outside the redesign page.

- [ ] **Step 2: Render the full frame**

Call `get_screenshot` with a maximum dimension high enough to inspect text and spacing. Download the short-lived PNG URL to the exact local preview path.

- [ ] **Step 3: Compare against the original reference**

Open both the original screenshot and redesign preview. Check:

- the new header is substantially shorter;
- duplicate alerts are consolidated;
- six result metrics are visible before traffic details;
- risk cards use Chinese business copy and actions;
- the product table uses nine default columns;
- there is no clipped text, overlap, broken border, or inconsistent radius.

- [ ] **Step 4: Fix any visible defects**

If a defect is found, use a targeted `use_figma` update on only the affected nodes, return the mutated IDs, and repeat Steps 1-3. Do not rebuild unaffected sections.

- [ ] **Step 5: Handoff**

Return:

```text
Figma file link
Direct link to the redesigned desktop frame
Local preview PNG path
Short note that this is a design draft and formal Vue implementation has not started
```

---

## Plan Self-Review Results

- **Spec coverage:** Every section of the confirmed design spec maps to Tasks 1-6. Mobile is intentionally deferred as specified.
- **Placeholder scan:** The plan contains no unfinished implementation placeholder. Words such as assignee/status in task-card content describe visible UI fields, not missing plan work.
- **Type and naming consistency:** Page, root frame, and eight section names are defined once and reused consistently. All later tasks consume IDs returned by earlier tasks.
- **Scope check:** The plan changes only one Figma file and one local preview image; formal frontend and backend work remain out of scope.
