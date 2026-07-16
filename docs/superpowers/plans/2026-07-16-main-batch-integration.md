# Main Batch Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the approved storefront and post-foundation fulfillment work into the current `main` branch in three independently reviewed batches without rewriting published migrations.

**Architecture:** Start from `main` commit `d311da55` in the isolated `codex/main-batch-integration` worktree. Cherry-pick only the feature commits required by each batch, skip the dashboard patch already present in `main`, and resolve database history by appending fulfillment migrations after SEO as V021, V022, and V023. Each batch receives focused tests and independent review before the next batch begins; the final tree receives a real MySQL 8 migration pass before merging and pushing `main`.

**Tech Stack:** Git worktrees, Vue 3, Vite 8, Vitest 4, Node.js ESM migration contracts, Java 17, Spring Boot 3.5, MyBatis-Plus, H2, Maven, MySQL 8.

## Global Constraints

- The immutable catalog entering this plan is exactly V001-V020: fulfillment owns V015-V018 and SEO owns V019-V020.
- Append `V021__trade_manual_tracking_audit.sql`, then `V022__trade_fulfillment_admin_permissions.sql`, then `V023__trade_fulfillment_legacy_migration_fact.sql`; never modify V001-V020.
- Regenerate `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql` only with `node sql/mysql/build-oakved-baseline.mjs` or the repository wrapper.
- Preserve the `main` tenant-interceptor tests, `FulfillmentPersistenceTextPolicy`, V018 generated active-record indexes, SEO module, AI module, and dashboard redesign.
- V022 creates permission buttons only. It must not write `system_role_menu`; its sole valid parent is active `system_menu.id=2076` with `name='订单列表'`, `type=2`, `parent_id=2072`, `path='order'`, `component='mall/trade/order/index'`, and `component_name='TradeOrder'`. It must reject a missing, ambiguous, or wrong-shape parent and reject an existing permission with the wrong parent/type/status.
- Manual audit free text remains subject to the shared persistence text policy and must never log request bodies, credentials, tracking identifiers, or PII.
- Do not commit the root worktree's pnpm v9 lockfile rewrite, temporary directories, release output, Office artifacts, or SDD scratch files.
- Do not merge or push a batch while Critical or Important review findings remain open.

---

### Task 1: Integrate storefront navigation and the shared catalog page

**Files:**
- Create: `docs/superpowers/specs/2026-07-16-storefront-navigation-catalog-design.md`
- Create: `docs/superpowers/plans/2026-07-16-storefront-navigation-catalog.md`
- Modify: `furniture web/src/App.vue`
- Modify: `furniture web/src/components/RhHeader.vue`
- Modify: `furniture web/src/data/rhLayout.js`
- Modify: `furniture web/src/i18n.js`
- Create: `furniture web/src/pages/CatalogPage.vue`
- Modify: `furniture web/src/styles.css`
- Test: `furniture web/tests/rhLayout.test.js`
- Test: `furniture web/tests/headerLanguageMenu.test.js`
- Test: `furniture web/tests/i18n.test.js`
- Test: `furniture web/tests/mobilePurchasePolish.test.js`
- Test: `furniture web/tests/storefrontLaunchPolish.test.js`
- Create: `furniture web/tests/catalogPage.test.js`

**Interfaces:**
- Consumes: the existing storefront shell and i18n helper on `main`.
- Produces: one stable navigation model, localized desktop/mobile navigation, and a shared `/catalog` route.

- [ ] **Step 1: Cherry-pick the reviewed storefront commits in dependency order**

```powershell
git cherry-pick 38ca8cb9 67c3d6f2 3d3b51f5 4b0e628b fb57e409 d476e3e4
```

Do not cherry-pick `c8078f90`; its dashboard patch is already present in `main` with the same stable patch ID.

- [ ] **Step 2: Run the focused storefront tests**

```powershell
npm.cmd test -- tests/rhLayout.test.js tests/headerLanguageMenu.test.js tests/i18n.test.js tests/mobilePurchasePolish.test.js tests/storefrontLaunchPolish.test.js tests/catalogPage.test.js
```

Expected: all selected test files pass.

- [ ] **Step 3: Run the full storefront suite and production build**

```powershell
npm.cmd test
npm.cmd run build
```

Expected: Vitest exits 0 and Vite produces `dist`.

- [ ] **Step 4: Review and record the batch boundary**

Generate a review package from `d311da55` to the batch HEAD. An independent reviewer must confirm that the dashboard was not duplicated, all navigation uses the shared model, localization is complete, and existing routes still work.

---

### Task 2: Integrate manual tracking, fulfillment admin APIs, and RBAC as V021/V022

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V021__trade_manual_tracking_audit.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V022__trade_fulfillment_admin_permissions.sql`
- Modify: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql`
- Modify: `furniture web/tests/databaseFulfillmentMigration.test.js`
- Modify: `furniture web/tests/databaseSafetyWorkflow.test.js`
- Modify: `furniture web/tests/dbMigrations.test.js`
- Create: `furniture web/tests/databaseFulfillmentPermissionsMigration.test.js`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql`
- Modify: fulfillment command, tracking, query, mapper, controller, VO, and test files introduced by the selected commits.

**Interfaces:**
- Consumes: fulfillment core V015-V018, SEO V019-V020, server-owned TenantContext/operator/trace data, and the shared persistence text policy.
- Produces: audited manual tracking, tenant-scoped read models, guarded admin endpoints, and five unassigned permission buttons.

- [ ] **Step 1: Cherry-pick only the manual/API/RBAC commits**

```powershell
git cherry-pick 0a0653a4 c1dd3220 6ebc93e1 90cda62d db4df1b4 321b82cf bb2a381a 0f9fc55b
```

Resolve conflicts by preserving the `main` V018-V020 catalog, generated `active_record` columns, tenant-interceptor regression tests, and `FulfillmentPersistenceTextPolicy`. Preserve both active-record and manual-audit columns in the H2 fixture.

- [ ] **Step 2: Write the failing final-catalog contracts**

Update the migration tests to require this exact suffix:

```js
expect(files.slice(-5)).toEqual([
  "V018__trade_fulfillment_active_record_uniqueness.sql",
  "V019__seo_foundation.sql",
  "V020__seo_active_record_uniqueness.sql",
  "V021__trade_manual_tracking_audit.sql",
  "V022__trade_fulfillment_admin_permissions.sql",
]);
```

Add V022 contract assertions that reject a missing or wrong-shape `system_menu` parent and reject an existing permission row attached to the wrong parent or type. Run the four migration test files before renaming the SQL files.

Expected: FAIL because V021/V022 do not yet exist and the picked migrations still use V018/V019.

- [ ] **Step 3: Append V021/V022 and regenerate the baseline**

Rename only the picked migration files, update their ledger/version references and plan text, harden V022 with fail-closed temporary guard checks, then run:

```powershell
node sql/mysql/build-oakved-baseline.mjs
```

Expected: the baseline sections are ordered V018, V019, V020, V021, V022 and are byte-equivalent to the migration files.

- [ ] **Step 4: Run migration and backend tests**

```powershell
npm.cmd test -- tests/databaseFulfillmentMigration.test.js tests/databaseFulfillmentPermissionsMigration.test.js tests/databaseSafetyWorkflow.test.js tests/dbMigrations.test.js
npm.cmd run verify:db-migrations
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentTrackingServiceImplTest,FulfillmentTrackingTransactionTest,FulfillmentPersistenceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: catalog verifier reports 22 migrations and all selected Maven tests pass.

- [ ] **Step 5: Review and commit the integration correction**

The reviewer must verify tenant isolation, manual-event transactionality, identifier masking, absence of request-body logging, absence of automatic role grants, and fail-closed V022 parent/permission guards.

---

### Task 3: Integrate legacy fulfillment migration and rollout as V023

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V023__trade_fulfillment_legacy_migration_fact.sql`
- Modify: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql`
- Modify: migration catalog tests and the trade-server H2 fixture.
- Create/modify: legacy projection, cache hashing, provider redaction, fact mapper, scanner, atomic writer, job, rollout guard, YAML, runbook, and their tests from the pinned source commits.

**Interfaces:**
- Consumes: the Task 2 admin/query/manual-event state, V022 permissions, approved per-order facts, and rollout flags.
- Produces: fail-closed candidate scans, per-order atomic migration, a bounded tenant job, redacted provider failures, and reversible rollout controls.

- [ ] **Step 1: Pin and cherry-pick the reviewed legacy series**

Pin source HEAD `53d3c698` and cherry-pick in this order:

```powershell
git cherry-pick a38a0331 10217f77 5d2ea3ac 071115e4 a6186ec5 fc7dfded 9cf1e3d7 d15deb84 f6e53319 105f5f4c db904934 5558911f d90c460b ef639543 28d68854 862226d9 53d3c698
```

- [ ] **Step 2: Write the failing V023 integration contracts**

Require a contiguous 23-file catalog ending in:

```js
expect(files.slice(-3)).toEqual([
  "V021__trade_manual_tracking_audit.sql",
  "V022__trade_fulfillment_admin_permissions.sql",
  "V023__trade_fulfillment_legacy_migration_fact.sql",
]);
```

Preserve the fact-table contract: exactly one absolute row per `(tenant_id, order_id)`, no `deleted` component in that unique key, no credential/tracking/phone/address columns, and no guessed route facts.

Expected: FAIL because the picked fact migration still uses V020.

- [ ] **Step 3: Append V023 and regenerate the baseline**

Rename the picked V020 migration to V023, update its version/marker references and H2 fixture, and regenerate the baseline with the official generator.

Expected: migration tests and verifier report V001-V023 with byte-equivalent baseline sections.

- [ ] **Step 4: Run focused and full fulfillment tests**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=LegacyMigrationFactPersistenceTest,LegacyMigrationFactsTest,FulfillmentLegacyMigrationDryRunIntegrationTest,FulfillmentLegacyMigrationServiceTest,FulfillmentLegacyMigrationWriterTest,FulfillmentLegacyMigrationWriterGuardTest,FulfillmentLegacyMigrationTransactionTest,FulfillmentLegacyMigrationJobTest,FulfillmentLegacyMigrationJobAopIntegrationTest,FulfillmentLegacyMigrationMySqlIsolationTest,FulfillmentLegacyProjectionServiceTest,ExpressTrackCacheKeyGeneratorTest,TradeOrderExpressTrackCacheTest,ExpressProviderLogRedactionTest,FulfillmentPropertiesTest,FulfillmentFeatureGuardTest,FulfillmentYamlBindingTest,TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentTrackingServiceImplTest,FulfillmentTrackingTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am test
```

Expected: all focused tests and the trade-server reactor pass; disabled paths perform zero writes and do not swallow the canonical disabled error.

- [ ] **Step 5: Review the legacy safety boundary**

The reviewer must verify that route facts are never inferred, scans are bounded and read-only, each order migrates in its own transaction, feature flags default off, provider logs are redacted, and rollback never drops V015-V023 data.

---

### Task 4: Final combined verification, merge, and push

**Files:**
- Verify only; any fix requires a regression test and a separate reviewed commit.

**Interfaces:**
- Consumes: reviewed Task 1-3 commits.
- Produces: a clean `main` and matching `origin/main`.

- [ ] **Step 1: Run all database and frontend contracts**

```powershell
npm.cmd test
npm.cmd run build
node scripts/verify-db-migrations.mjs
node scripts/check-dashboard-contract.mjs
node scripts/check-seo-foundation-contract.mjs
```

- [ ] **Step 2: Run backend build and tests**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am test
mvn.cmd -pl yudao-module-seo/yudao-module-seo-server -am test
mvn.cmd -pl yudao-server -am "-DskipTests" package
```

- [ ] **Step 3: Apply the real MySQL 8 catalog**

Initialize a disposable MySQL 8 database from `ruoyi-vue-pro.sql`, apply V001-V023 in lexical order, verify a 23-row ledger, verify V018/V020 active-only indexes, verify V021-V023 objects and menu guards, and exercise representative soft-delete/recreate and legacy-fact uniqueness lifecycles.

- [ ] **Step 4: Obtain final independent review**

Review the complete `d311da55..HEAD` package. Critical and Important findings must be fixed and re-reviewed before integration.

- [ ] **Step 5: Merge and push without rewriting remote history**

Fetch `origin/main`, merge it if it advanced, merge `codex/main-batch-integration` into local `main`, rerun fast post-merge contracts, and push with ordinary `git push origin main`. Never force-push.
