# Mall ERP Product and Stock Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize tenant-121 ERP product and stock data, map all 26 mall SKUs one-to-one to ERP products, and make storefront stock validation use ERP-backed sellable stock.

**Architecture:** Add mapping and sync-log persistence to the ERP module, expose cross-module behavior through an ERP API rather than direct table access, and consume that API from product and trade services. Storefront browser contracts remain unchanged.

**Tech Stack:** Java 8, Spring Boot, MyBatis Plus, MySQL 8, JUnit 5, Mockito, Vue 3, Vitest.

## Global Constraints

- ERP owns product code, base name, unit, cost and physical stock.
- Mall owns images, marketing copy, retail/member pricing and storefront status.
- Storefront code must not call `/admin-api/erp/*`.
- Every query and mapping is tenant-scoped; tenant `1` must remain untouched.
- Existing mall SPU/SKU IDs remain stable.
- All sync operations are idempotent.

---

### Task 1: ERP and Integration Schema

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/mall-erp-integration.sql`
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/docker/start-local-infra.ps1`
- Test: `furniture web/tests/mallErpSchema.test.js`

**Interfaces:**
- Produces ERP product/unit/category/warehouse/stock tables plus `mall_erp_product_mapping` and `mall_erp_sync_log`.

- [ ] Write a failing Vitest source test asserting the SQL contains `erp_product_unit`, `erp_product_category`, `erp_product`, `erp_warehouse`, `erp_stock`, `mall_erp_product_mapping`, `mall_erp_sync_log`, tenant columns and the three mapping uniqueness constraints.
- [ ] Run `npm.cmd test -- --run tests/mallErpSchema.test.js` from `furniture web`; expect failure because the migration does not exist.
- [ ] Create idempotent MySQL DDL matching the existing ERP DO field names and standard BaseDO columns. The mapping table must include `mall_spu_id`, `mall_sku_id`, `erp_product_id`, `erp_product_code`, `sync_status`, `last_synced_at`, `last_error`, `version`, `tenant_id`, standard audit columns and soft deletion.
- [ ] Add the migration to local-infra initialization after base and module table SQL.
- [ ] Run the focused test and import the migration twice; expect both imports to succeed.
- [ ] Commit with `feat: initialize mall ERP integration schema`.

### Task 2: ERP Cross-Module Product Stock API

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-api/src/main/java/cn/iocoder/yudao/module/erp/api/integration/MallErpProductApi.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-api/src/main/java/cn/iocoder/yudao/module/erp/api/integration/dto/MallErpProductDTO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-api/src/main/java/cn/iocoder/yudao/module/erp/api/integration/dto/MallErpStockDTO.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/pom.xml`
- Test: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-api/src/test/java/cn/iocoder/yudao/module/erp/api/integration/MallErpProductApiContractTest.java`

**Interfaces:**
- Produces `syncMallSku(Long mallSpuId, Long mallSkuId)`, `syncAllMallSkus()`, `getByMallSkuId(Long mallSkuId)`, `getSellableStock(Long mallSkuId)`, and `validateSellableStock(List<MallErpStockRequestDTO>)`.

- [ ] Write the failing API contract test that compiles against the exact methods above and checks DTOs expose ERP product ID/code, base name, cost price, enabled status and sellable stock.
- [ ] Run the ERP API module test; expect compilation failure.
- [ ] Add the ERP API module to the ERP parent and define the interfaces/DTOs without implementation dependencies.
- [ ] Run the focused module test; expect pass.
- [ ] Commit with `feat: define mall ERP product stock API`.

### Task 3: Mapping Persistence and Idempotent Product Sync

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/dal/dataobject/integration/MallErpProductMappingDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/dal/dataobject/integration/MallErpSyncLogDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/dal/mysql/integration/MallErpProductMappingMapper.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/dal/mysql/integration/MallErpSyncLogMapper.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/service/integration/MallErpProductSyncService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/service/integration/MallErpProductSyncServiceImpl.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/api/integration/MallErpProductApiImpl.java`
- Test: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/test/java/cn/iocoder/yudao/module/erp/service/integration/MallErpProductSyncServiceImplTest.java`

**Interfaces:**
- Consumes mall SKU/SPU data through `ProductSkuApi` and existing product APIs.
- Produces one ERP product and one mapping per mall SKU using code `RH-<tenantId>-<mallSkuId>`.

- [ ] Write failing tests for first sync, repeat sync, base-name update, cost update, image/retail-price preservation, missing SKU failure and tenant isolation.
- [ ] Run the focused tests; expect failure because the service is absent.
- [ ] Add the mapping/log DOs and tenant-scoped mappers.
- [ ] Implement sync in one local transaction: look up mapping; create or update ERP product; persist mapping; write a sanitized sync log. Never identify products by name.
- [ ] Make a repeated call return the existing ERP product and mapping without inserts.
- [ ] Run the focused tests; expect all cases pass.
- [ ] Commit with `feat: synchronize mall SKUs to ERP products`.

### Task 4: Tenant-121 Bootstrap and Audit

**Files:**
- Create: `seed-mall-erp-products.ps1`
- Create: `audit-mall-erp-integration.ps1`
- Test: `furniture web/tests/mallErpSeed.test.js`

**Interfaces:**
- Produces one unit, ERP category tree, default warehouse, 26 ERP products, 26 mappings and initial stock for tenant `121`.

- [ ] Write a failing source test requiring tenant `121`, stable product codes, no tenant `1` mutations, 26 mappings, read-only audit queries and idempotent inserts/updates.
- [ ] Run the focused test and confirm failure.
- [ ] Implement a repeatable bootstrap using current mall SKU IDs and costs; do not delete rows or recreate IDs.
- [ ] Implement an audit that requires: 26 active mall SKUs, 26 ERP products, 26 unique mappings, zero orphan mappings, zero cross-tenant mappings and zero stock rows without a warehouse.
- [ ] Run bootstrap twice and audit once; expect success and unchanged counts on the second run.
- [ ] Commit with `feat: seed tenant ERP product mappings`.

### Task 5: ERP-Backed Sellable Stock in Product and Trade

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/pom.xml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/pom.xml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/CartServiceImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImpl.java`
- Test: focused product response, cart and settlement service tests.

**Interfaces:**
- Consumes `MallErpProductApi.getSellableStock` and `validateSellableStock`.
- Produces storefront stock values and product-level insufficient-stock errors without changing frontend API shapes.

- [ ] Write failing product tests proving response stock comes from ERP while image, introduction and retail price remain mall values.
- [ ] Write failing cart tests for zero stock and quantity above ERP sellable stock.
- [ ] Write failing settlement tests proving stock is revalidated for every line.
- [ ] Add the ERP API dependency only; do not access ERP mappers or tables from mall modules.
- [ ] Implement ERP stock overlay and validation with explicit error messages containing mall SKU ID and available quantity.
- [ ] Run all focused Java tests and confirm pass.
- [ ] Commit with `feat: validate storefront stock against ERP`.

### Task 6: Admin Visibility and End-to-End Verification

**Files:**
- Modify mall product admin response/view files to show ERP code, mapping state, ERP stock and last sync time.
- Add admin integration controller methods for single sync, full sync, audit and retry.
- Add frontend API wrappers and table columns in the mall product admin page.

- [ ] Write failing controller and UI source tests for mapping visibility and sync actions.
- [ ] Implement admin-only integration endpoints and permission checks.
- [ ] Add compact, non-colored columns/actions consistent with project document styling constraints.
- [ ] Run ERP/product/trade Java tests, admin type checks, storefront Vitest and storefront build.
- [ ] Run bootstrap and audit against local MySQL.
- [ ] Verify 26 products appear in ERP admin and mall admin with matching codes and stock.
- [ ] Verify product list, detail, cart and settlement all use the same stock value.
- [ ] Commit with `feat: expose mall ERP product sync status`.
