# Oakved Frontend Field Audit Workbook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one product-manager-friendly Excel that inventories and evaluates only the visible and interactive fields in the Oakved storefront and Yudao ERP admin frontend.

**Architecture:** Build two independently validated JSON datasets from frontend routes, Vue templates, i18n copy, menu configuration, and directly rendered frontend enums. Merge them through one workbook builder into seven sheets, with business-facing columns in the main sheets and technical evidence isolated in an appendix. Verify the workbook at data, formula, OOXML-style, and rendered-image levels.

**Tech Stack:** PowerShell, Node.js from the bundled workspace runtime, `@oai/artifact-tool`, JSON, Vue source inspection, OOXML inspection.

## Global Constraints

- Include both the customer-facing furniture storefront and the employee-facing Yudao ERP admin UI.
- One main-list row represents one visible or interactive UI element or UI state.
- Do not create main-list rows whose subject is a Java class, API, VO, DTO, DO, database column, environment variable, service constant, or test contract.
- Every main-list row must map to at least one frontend source file and 1-based line number.
- Sort the core journey before later-value services: product list → product detail → cart → checkout → payment → order; then account, Membership, Trade, Gift Registry, SEO, and AI.
- Use black text for all spreadsheet content.
- Use a solid white background for every worksheet and table cell, including titles, headers, data, status, priority, and conditional-formatting results.
- Do not use black, gray, or colored cell fills.
- Use bold text, font size, spacing, and black or light-gray borders for hierarchy.
- Export exactly one final workbook to `D:/code/outputs/019f6463-c2d7-7e73-baef-3bcc9a7b21d2/Oakved_前端页面字段适配审计清单_2026-07-15.xlsx`.

---

### Task 1: Define and test the UI-finding contract

**Files:**
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/validate-ui-findings.mjs`
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/fixtures/valid-ui-finding.json`
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/fixtures/invalid-backend-finding.json`

**Interfaces:**
- Consumes: JSON objects produced by Tasks 2 and 3.
- Produces: `validateDataset(dataset, expectedSurface)` and a stable field contract used by the workbook builder.

- [ ] **Step 1: Create the valid fixture**

```json
{
  "findings": [{
    "id": "SF-UI-001",
    "surface": "家具商城",
    "module": "结账",
    "entryPath": "商品详情 → 购物车 → 结账",
    "pageName": "结账页",
    "pageRoute": "/checkout",
    "pageArea": "配送地址",
    "elementType": "下拉框",
    "currentUi": "Ship to United States",
    "classification": "需要业务确认",
    "issue": "国家被固定为美国",
    "mismatchReason": "首发市场尚未确定，且加拿大地址无法选择",
    "recommendation": "由市场配置提供 Country，并联动州省、邮编、币种与税费",
    "checkoutImpact": "是",
    "stage": "现在",
    "decision": "确认首发国家与可售国家",
    "status": "待评审",
    "decisionNotes": "",
    "sourceKind": "Vue模板",
    "component": "CheckoutPage.vue",
    "i18nKey": "",
    "technicalField": "deliveryAddress.country",
    "sourceFile": "furniture web/src/pages/CheckoutPage.vue",
    "line": 661,
    "dependency": "市场、地址、税务和仓配规则",
    "flowOrder": 50,
    "pageOrder": 10,
    "itemOrder": 1
  }]
}
```

- [ ] **Step 2: Create the backend-subject fixture**

```json
{
  "findings": [{
    "id": "BAD-001",
    "surface": "ERP管理端",
    "pageName": "OrderVO",
    "elementType": "DTO字段",
    "currentUi": "receiverPostCode",
    "sourceFile": "yudao电商管理平台前后端/yudao-cloud/OrderVO.java",
    "line": 41
  }]
}
```

- [ ] **Step 3: Write the validator so the valid fixture passes and the backend fixture fails**

The validator must require every property shown in the valid fixture, require `surface` to equal `家具商城` or `ERP管理端`, require `line` and the three order fields to be positive integers, require `checkoutImpact` to be `是` or `否`, require `stage` to be `现在`, `正式上线前`, or `后续优化`, and reject `.java`, `.sql`, `/yudao-cloud/`, `/src/api/`, `DTO`, `VO`, `DO`, `数据库字段`, and `环境变量` as primary row subjects.

```js
const forbiddenSource = /(?:\.java$|\.sql$|\/yudao-cloud\/|\/src\/api\/)/i;
const forbiddenSubject = /(?:DTO|VO|DO|数据库字段|环境变量|API类型)/i;

export function validateDataset(dataset, expectedSurface) {
  const errors = [];
  if (!Array.isArray(dataset?.findings)) return ["findings must be an array"];
  const ids = new Set();
  dataset.findings.forEach((row, index) => {
    const at = `findings[${index}]`;
    const required = [
      "id", "surface", "module", "entryPath", "pageName", "pageRoute", "pageArea",
      "elementType", "currentUi", "classification", "issue", "mismatchReason",
      "recommendation", "checkoutImpact", "stage", "decision", "status",
      "sourceKind", "component", "sourceFile", "line", "dependency",
      "flowOrder", "pageOrder", "itemOrder"
    ];
    for (const key of required) if (row[key] === "" || row[key] == null) errors.push(`${at}.${key} is required`);
    if (row.surface !== expectedSurface) errors.push(`${at}.surface must be ${expectedSurface}`);
    if (ids.has(row.id)) errors.push(`${at}.id is duplicated`); else ids.add(row.id);
    if (forbiddenSource.test(String(row.sourceFile))) errors.push(`${at}.sourceFile is not a frontend source`);
    if (forbiddenSubject.test(`${row.pageName} ${row.elementType} ${row.currentUi}`)) errors.push(`${at} is a backend subject`);
    if (!["是", "否"].includes(row.checkoutImpact)) errors.push(`${at}.checkoutImpact is invalid`);
    if (!["现在", "正式上线前", "后续优化"].includes(row.stage)) errors.push(`${at}.stage is invalid`);
    for (const key of ["line", "flowOrder", "pageOrder", "itemOrder"]) {
      if (!Number.isInteger(row[key]) || row[key] < 1) errors.push(`${at}.${key} must be a positive integer`);
    }
  });
  return errors;
}
```

- [ ] **Step 4: Run the contract test**

Run: `node D:/code/.codex-temp/oakved-frontend-field-audit/validate-ui-findings.mjs --self-test`

Expected: `valid fixture: PASS`, `backend fixture rejected: PASS`, exit code `0`.

### Task 2: Build the storefront UI dataset

**Files:**
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/storefront-ui-findings.json`
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/storefront-coverage.json`

**Interfaces:**
- Consumes: `furniture web/src/App.vue`, `src/pages/**/*.vue`, `src/components/**/*.vue`, `src/i18n.js`, visible navigation/config data, and direct-render model fallbacks.
- Produces: a `findings` array conforming to Task 1 plus a page coverage list.

- [ ] **Step 1: Enumerate the storefront route inventory**

Record every route in `furniture web/src/App.vue:55-113`, its resolved component from lines 136-166, and its page family. Include shared header, footer, cart drawer, authentication modal, and AI assistant as global UI surfaces.

- [ ] **Step 2: Record core-journey UI findings first**

Create rows for every confirmed visible issue in product list, product detail, cart, checkout, payment, order, account address, and account order pages. Include the already confirmed cases: exposed `Yudao/ERP/Preview` copy, fake PDP price and stock fallbacks, non-functional SKU selectors, fixed cart specifications, fixed ZIP and delivery fee, fixed country/state behavior, raw but unused card fields, forced RH membership renewal, fixed tax/delivery/address fallbacks, and placeholder payment methods.

- [ ] **Step 3: Record later-value and global UI findings**

Create rows for navigation links that reach `/missing`, hard-coded Chinese navigation/AI copy, newsletter without submit behavior, incorrect footer routes, Gift Registry SPU/SKU inputs, Membership/Trade RH copy, incomplete Trade country/state choices, incomplete route SEO, and development-preview pages.

- [ ] **Step 4: Validate source evidence**

For each row, verify that `sourceFile` exists and that the 1-based `line` contains the stated UI text, binding, i18n key, or directly rendered fallback. Record page coverage as `{ route, pageName, component, scanned: true, findingsCount, runtimeChecked, note }`.

- [ ] **Step 5: Run the storefront dataset validator**

Run: `node D:/code/.codex-temp/oakved-frontend-field-audit/validate-ui-findings.mjs --file D:/code/.codex-temp/oakved-frontend-field-audit/storefront-ui-findings.json --surface 家具商城`

Expected: `0 validation errors`, all IDs unique, and no backend source accepted.

### Task 3: Build the ERP admin UI dataset

**Files:**
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/admin-ui-findings.json`
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/admin-coverage.json`

**Interfaces:**
- Consumes: `yudao-ui-admin-vue3/src/views`, visible menu overrides, frontend i18n, and directly rendered frontend enums.
- Produces: a `findings` array conforming to Task 1 plus a page coverage list.

- [ ] **Step 1: Establish the admin page boundary**

Include visible UI under `mall`, `member`, `pay`, `erp`, `wms`, `dashboard`, `Home`, and the Oakved furniture menu configuration. Exclude infrastructure/developer pages unless they appear in the Oakved runtime menu, in which case record the visible menu item as a removal candidate.

- [ ] **Step 2: Record P0 admin UI findings**

Create rows for virtual comments, virtual sales, RMB symbols and `元`, `Asia/Shanghai` paired with USD, domestic address trees, missing large-furniture delivery-access fields, incomplete returns/RMA terminology, `kg/m³`-only SKU data, single manual tax rate, Alipay/WeChat-first payment configuration, and the misrouted group-buy shortcut.

- [ ] **Step 3: Record P1/P2 admin UI findings**

Create rows for SPU/PV/UV terminology, phone/nickname-centric customers, member address codes, Chinese province statistics, market/sale/cost price labels, domestic freight templates, carrier/tracking gaps, pickup verification terminology, after-sales terminology, warehouse charge units, Chinese discount notation, wallet recharge, multi-level distribution, bargain/group-buy/seckill, point mall, sign-in rewards, and SKU-level multi-tier commission fields.

- [ ] **Step 4: Validate source evidence and role visibility**

Verify every source line. Use the coverage note `受角色权限控制` where runtime visibility depends on permissions; do not claim complete dynamic menu coverage when the menu name comes only from backend configuration.

- [ ] **Step 5: Run the admin dataset validator**

Run: `node D:/code/.codex-temp/oakved-frontend-field-audit/validate-ui-findings.mjs --file D:/code/.codex-temp/oakved-frontend-field-audit/admin-ui-findings.json --surface ERP管理端`

Expected: `0 validation errors`, all IDs unique, and no API/Java/database subject in the main rows.

### Task 4: Build the seven-sheet workbook

**Files:**
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/build-frontend-audit-workbook.mjs`
- Test: `D:/code/.codex-temp/oakved-frontend-field-audit/verify-frontend-audit-workbook.mjs`
- Output: `D:/code/outputs/019f6463-c2d7-7e73-baef-3bcc9a7b21d2/Oakved_前端页面字段适配审计清单_2026-07-15.xlsx`

**Interfaces:**
- Consumes: the two validated finding datasets and two coverage datasets.
- Produces: exactly seven sheets named `01_前端总清单（打开即看）`, `02_家具商城页面`, `03_ERP管理界面`, `04_真实付费闭环`, `05_上线前增值服务`, `06_技术定位附录`, and `07_覆盖范围`.

- [ ] **Step 1: Write a failing workbook-structure verifier**

The verifier must fail when the output is absent or when sheet names/order, first active sheet, headers, finding counts, source mappings, stage filters, or backend-subject exclusions do not match the validated JSON datasets.

- [ ] **Step 2: Run the verifier before building**

Run: `node D:/code/.codex-temp/oakved-frontend-field-audit/verify-frontend-audit-workbook.mjs`

Expected: non-zero exit with `workbook missing or structure mismatch`.

- [ ] **Step 3: Build the workbook with the bundled artifact runtime**

Use only the runtime and `node_modules` path returned by `codex_app__load_workspace_dependencies`. In the scratch directory, create a junction to that `node_modules`; do not install packages. Use `@oai/artifact-tool` to write tables, filters, frozen headers, validation lists for status, and wrapped text.

The first five sheets use the 15 business columns from the design. Sheet 6 contains `编号`, `页面路由`, `Vue组件`, `证据类型`, `i18n key`, `技术字段`, `源文件`, `行号`, `精确定位`, and `前端依赖说明`. Sheet 7 contains route/page coverage and the scan limitation note.

- [ ] **Step 4: Apply the required white-background style**

Set every used range fill to `#FFFFFF`; set every font color to `#000000`; set title and header hierarchy with size, bold, row height, and `#D9D9D9` or black borders. Disable table row/column banding. Conditional-format results, if used, must retain white fill and black text.

- [ ] **Step 5: Run the workbook verifier after export**

Run: `node D:/code/.codex-temp/oakved-frontend-field-audit/verify-frontend-audit-workbook.mjs`

Expected: exit code `0`; seven sheets; total count equals storefront plus admin; sheet 2 and sheet 3 counts reconcile; sheet 4 contains only `checkoutImpact=是`; sheet 5 contains only `stage=正式上线前`; formula-error count `0`; external-reference count `0`.

### Task 5: Verify OOXML style and visual quality

**Files:**
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/verify-frontend-audit-ooxml.ps1`
- Create: `D:/code/.codex-temp/oakved-frontend-field-audit/previews/*.png`

**Interfaces:**
- Consumes: the exported workbook.
- Produces: machine-readable style verification output and one render per sheet.

- [ ] **Step 1: Write the OOXML style verifier**

Resolve worksheet cells through their `cellXfs` styles and reject any effective non-white fill or explicit non-black font. Reject gradient fills, patterned fills other than solid white, colored number formats, table banding, data bars, icon sets, color scales, drawings, and media. Allow black and `#D9D9D9` borders only.

- [ ] **Step 2: Run the OOXML verifier**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:/code/.codex-temp/oakved-frontend-field-audit/verify-frontend-audit-ooxml.ps1`

Expected: exit code `0`, `nonWhiteCellFills=0`, `nonBlackFonts=0`, `coloredConditionalFormats=0`, `unexpectedMedia=0`.

- [ ] **Step 3: Render every sheet**

Render the used range of all seven sheets through `workbook.render`, plus middle and tail ranges of the two long page sheets. Save PNG files under the preview directory.

- [ ] **Step 4: Inspect every render and repair severe defects**

Check titles, headers, wrapped text, row heights, column widths, frozen panes, source paths, and table endpoints. Repair clipped headers, unreadable text, blank sections, duplicated titles, or content outside the working area, then re-export and re-run Tasks 4 and 5 checks.

### Task 6: Perform key runtime spot checks and deliver

**Files:**
- Modify only if verification reveals a workbook-content error: the two JSON datasets or workbook builder.
- Final artifact: `D:/code/outputs/019f6463-c2d7-7e73-baef-3bcc9a7b21d2/Oakved_前端页面字段适配审计清单_2026-07-15.xlsx`

**Interfaces:**
- Consumes: verified source-backed workbook and available local frontend runtime.
- Produces: final user-facing workbook with documented runtime-check coverage.

- [ ] **Step 1: Run the storefront and ERP frontends when locally available**

Use existing project start scripts/package commands without changing business code. Spot-check storefront product list, PDP, cart, checkout, and account address; spot-check ERP product, order, payment, shipping, and customer pages. If authentication or data prevents a page check, record the exact limitation in `07_覆盖范围` instead of inventing visibility.

- [ ] **Step 2: Reconcile runtime observations**

For each spot-checked page, confirm the `currentUi` value appears or that the source-controlled conditional state is accurately described. Correct only evidence wording, route, element type, or source location when a mismatch is found.

- [ ] **Step 3: Run final verification commands**

Run both workbook and OOXML verifiers after the last export. Re-render all seven sheets after the final content change. Confirm the SHA-256 hash and file size.

- [ ] **Step 4: Final handoff**

Provide a concise Chinese summary, state the storefront/admin finding counts and verification results, and include exactly one standalone Markdown link to the final `.xlsx`. Do not link scratch builders, JSON data, or previews.
