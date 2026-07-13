# ERP-Aligned Web Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Web storefront expose only Mall SPUs whose sellable SKUs are completely mapped to ERP, with no hard-coded demo fallback.

**Architecture:** Add a batch ERP-alignment query to the existing Mall/ERP integration API, use its eligible SPU IDs as a constraint in App catalog pagination and detail lookup, and keep admin catalog access unchanged. Remove the Web demo dataset and represent loading, empty, and unavailable states explicitly.

**Tech Stack:** Java 8, Spring Boot, MyBatis Plus, JUnit/Mockito, Vue 3, Vite, Vitest.

## Global Constraints

- Storefront visibility requires every sellable SKU of an SPU to have a valid Mall-to-ERP mapping.
- Missing mappings and integration failures fail closed.
- Admin product management and manual ERP synchronization remain unchanged.
- No hard-coded product, price, stock, SKU, or image may be used as a storefront fallback.
- Preserve unrelated existing worktree changes.

---

### Task 1: Batch ERP alignment boundary

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-api/src/main/java/cn/iocoder/yudao/module/erp/api/integration/MallErpProductApi.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/api/integration/MallErpProductApiImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/service/integration/MallErpProductSyncService.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/service/integration/MallErpProductSyncServiceImpl.java`
- Test: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/test/java/cn/iocoder/yudao/module/erp/service/integration/MallErpProductSyncServiceImplTest.java`

**Interfaces:**
- Produces: `CommonResult<Set<Long>> getMappedMallSkuIds(Collection<Long> mallSkuIds)` and matching service method.
- Contract: return only requested SKU IDs whose mapping points to an existing enabled ERP product; never manufacture mappings.

- [ ] Write tests for fully mapped, missing mapping, deleted ERP product, and disabled ERP product cases.
- [ ] Run the focused Maven test and confirm it fails because the batch API does not exist.
- [ ] Implement a single mapping-table query plus ERP-product validation, expose it through the local/RPC API, and avoid per-SKU remote calls.
- [ ] Run the focused ERP test and module compilation; confirm both pass.
- [ ] Commit the API and service change.

### Task 2: Filter App catalog before pagination and guard detail

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuController.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuService.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/spu/ProductSpuMapper.java`
- Test: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuControllerTest.java`

**Interfaces:**
- Consumes: `getMappedMallSkuIds(Collection<Long>)` from Task 1.
- Produces: ERP-aligned results from `/product/spu/page`, `/product/spu/list-by-ids`, and `/product/spu/get-detail`.

- [ ] Write controller/service tests proving: a fully mapped SPU is returned; an unmapped, partially mapped, or SKU-less SPU is excluded; filtered totals are correct; unavailable detail throws `SPU_NOT_EXISTS` before browse history is recorded.
- [ ] Run the focused product-server test and confirm the unmapped/partial cases fail under current behavior.
- [ ] Add an eligible-SPU-ID constraint to the App SPU query path so filtering occurs in SQL before limit/offset and total counting.
- [ ] Compute eligible SPUs by grouping enabled Mall SKUs and requiring every SKU ID to appear in the batch mapped-ID response.
- [ ] Reuse the same predicate for list-by-IDs and detail, then overlay ERP stock only after eligibility is established.
- [ ] Run focused tests and product-server module tests; confirm correct data, totals, and no history side effects.
- [ ] Commit the backend storefront enforcement.

### Task 3: Remove Web demo catalog and add fail-closed UI states

**Files:**
- Delete: `furniture web/src/data/demoProducts.js`
- Delete: `furniture web/tests/demoProducts.test.js`
- Modify: `furniture web/src/pages/SofasPlpPage.vue`
- Modify: `furniture web/src/pages/SofaPdpPage.vue`
- Modify: `furniture web/src/services/furnitureAssistant.js`
- Modify: `furniture web/src/i18n.js`
- Modify: `furniture web/tests/furnitureAssistantClient.test.js`
- Create: `furniture web/tests/erpAlignedCatalogUi.test.js`

**Interfaces:**
- Consumes: existing `getProductPage` and `getProductDetail` clients.
- Produces: list state `{ loading, products, error }`, nullable detail state, translated empty/unavailable messages, and mock assistant responses with zero default products.

- [ ] Write Vitest assertions that product pages do not import `demoProducts`, initialize without fixed products, render empty/error states, and do not expose add-to-cart when detail is unavailable; update assistant tests to expect no implicit recommendations.
- [ ] Run the focused Vitest files and confirm failures reference current demo imports/defaults.
- [ ] Delete the demo dataset/test; initialize list with `[]` and detail with `null`; capture API failures in explicit error state without fallback.
- [ ] Add English, Chinese, and French catalog empty/unavailable copy and remove `offlineCatalog`/`connectedCatalog` usages from commerce pages.
- [ ] Change assistant mock defaults from `demoProducts` to `[]`, while preserving explicitly supplied aligned products.
- [ ] Run focused Web tests, the full `npm test`, and `npm run build`; confirm no `demoProducts` references remain.
- [ ] Commit the Web removal and fail-closed states.

### Task 4: End-to-end verification

**Files:**
- No production files expected.

**Interfaces:**
- Verifies the complete Mall → ERP mapping → App API → Web rendering path.

- [ ] Run `rg -n "demoProducts|Outdoor Lounge Chair|Demo catalog|演示目录|Catalogue de démonstration" "furniture web/src"` and expect no matches.
- [ ] Run the ERP integration and product-server focused Maven suites.
- [ ] Run the complete Web Vitest suite and production build.
- [ ] Inspect `git diff --check` and `git status --short`, separating pre-existing unrelated changes from this task's changes.
- [ ] Record exact verification commands and outcomes in the handoff; do not claim success if any required command fails.
