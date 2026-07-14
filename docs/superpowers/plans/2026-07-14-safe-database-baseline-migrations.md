# Oakved Safe Database Baseline and Migrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one complete first-install SQL file, version and checksum every later migration, preserve the existing 26-product tenant catalog with ERP mappings, and remove destructive volume deletion from normal startup.

**Architecture:** Numbered MySQL files under `sql/mysql/migrations` are the migration source of truth. A deterministic Node generator concatenates the base schema, numbered migrations, demo catalog SQL, and migration ledger rows into `oakved-baseline.sql`; a PowerShell runner applies only pending migrations under a MySQL advisory lock. Normal startup calls the runner without deleting volumes, while a separate reset command requires a verified dump and exact confirmation.

**Tech Stack:** MySQL 8.0, Docker Compose, PowerShell 5.1, Node.js ESM, Vitest 4

## Global Constraints

- Work only on branch `codex/agent-rag`; preserve unrelated untracked files.
- Commit completed and verified changes to `codex/agent-rag`; do not push unless explicitly requested.
- The baseline contains tenant `121`, exactly 26 demo SPUs, 26 SKUs, 26 ERP products, stock rows, and 26 mall-to-ERP mappings.
- The baseline excludes real members, orders, payments, analytics events, and other runtime business data.
- Normal startup must never execute `docker compose down -v` and must not expose `-Recreate` or `-ReimportSql`.
- Destructive reset must create and validate a backup before accepting the exact confirmation text `RESET OAKVED LOCAL DATA`.
- Existing published migration content is immutable; checksum mismatch is fatal.
- No Flyway, Liquibase, or new runtime dependency.

---

### Task 1: Migration catalog and deterministic baseline generator

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V001__module_tables.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V002__member_email_auth.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V003__member_trade_application.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V004__member_membership.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V005__member_gift_registry.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V006__trade_gift_registry_context.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V007__mall_erp_integration.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V008__product_spu_detail_config.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V009__product_favorite_sku_wishlist.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V010__product_furniture_sku_search.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V011__member_address_verification.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V012__trade_order_address_verification.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V013__statistics_commerce_dashboard.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V014__statistics_commerce_dashboard_backfill.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs`
- Create: `furniture web/tests/databaseBaselineGenerator.test.js`
- Modify: `furniture web/package.json`

**Interfaces:**
- Consumes: existing MySQL files in `sql/mysql`, UTF-8 text, migration file names matching `^V(\d{3})__[a-z0-9_]+\.sql$`.
- Produces: `discoverMigrations(root): Migration[]`, `buildBaseline(options): string`, CLI command `npm run build:db-baseline`.

- [ ] **Step 1: Write failing generator tests**

Add tests that create temporary fixture SQL files and assert strict version ordering, duplicate-version rejection, deterministic output, migration ledger DDL, SHA-256 rows, and refusal when an expected source file is absent:

```js
import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  buildBaseline,
  discoverMigrations,
} from "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs";

it("orders migrations and embeds immutable checksums", () => {
  const root = fixtureRoot();
  writeFileSync(join(root, "migrations/V002__two.sql"), "SELECT 2;\n");
  writeFileSync(join(root, "migrations/V001__one.sql"), "SELECT 1;\n");
  const migrations = discoverMigrations(join(root, "migrations"));
  const first = buildBaseline({ baseFiles: [join(root, "base.sql")], migrations, seedFile: join(root, "seed.sql") });
  const second = buildBaseline({ baseFiles: [join(root, "base.sql")], migrations, seedFile: join(root, "seed.sql") });
  expect(migrations.map(({ version }) => version)).toEqual(["001", "002"]);
  expect(first).toBe(second);
  expect(first).toContain("CREATE TABLE IF NOT EXISTS `schema_migrations`");
  expect(first).toMatch(/INSERT INTO `schema_migrations`.*V001__one\.sql/s);
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `npm.cmd test -- databaseBaselineGenerator.test.js` from `D:\code\furniture web`

Expected: FAIL because `build-oakved-baseline.mjs` does not exist.

- [ ] **Step 3: Implement the generator and numbered catalog**

Implement pure functions with `node:fs`, `node:path`, and `node:crypto`. The generator must normalize line endings to LF, reject duplicate/gapped versions, calculate SHA-256 over each normalized migration, and emit this ledger contract:

```sql
CREATE TABLE IF NOT EXISTS `schema_migrations` (
  `version` varchar(16) NOT NULL,
  `description` varchar(255) NOT NULL,
  `script_name` varchar(255) NOT NULL,
  `checksum_sha256` char(64) NOT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`),
  UNIQUE KEY `uk_schema_migrations_script_name` (`script_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Populate numbered files with the exact existing SQL contents associated with their names; do not alter SQL semantics in this task. Add:

```json
"build:db-baseline": "node ../yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs"
```

- [ ] **Step 4: Run focused tests and generator**

Run: `npm.cmd test -- databaseBaselineGenerator.test.js`

Expected: PASS.

Run: `npm.cmd run build:db-baseline`

Expected: exit 0 and generation summary listing versions `001` through `014`.

- [ ] **Step 5: Commit**

```powershell
git add -- 'furniture web/package.json' 'furniture web/tests/databaseBaselineGenerator.test.js' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs'
git commit -m "feat: add deterministic database baseline generator"
```

### Task 2: SQL-native tenant 121 catalog and ERP seed

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-demo-data.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql`
- Create: `furniture web/tests/databaseBaselineData.test.js`
- Modify: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs`
- Modify: `seed-furniture-agent-products.ps1`
- Modify: `seed-mall-erp-products.ps1`

**Interfaces:**
- Consumes: tenant `121` from the base SQL; product and ERP tables created by the base plus migrations.
- Produces: idempotent `oakved-demo-data.sql`; generated `oakved-baseline.sql` with exactly 26 mall and ERP catalog rows.

- [ ] **Step 1: Write failing baseline-data tests**

Test required stable business keys and counts without depending on generated numeric IDs:

```js
it("seeds the complete tenant 121 mall and ERP demo catalog", () => {
  const seed = readSource("../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-demo-data.sql");
  expect(seed).toContain("SET @tenant_id = 121");
  expect(seed.match(/CALL seed_oakved_product\(/g)).toHaveLength(26);
  expect(seed).toContain("INSERT INTO erp_product");
  expect(seed).toContain("INSERT INTO mall_erp_product_mapping");
  expect(seed).toContain("INSERT INTO erp_stock");
  expect(seed).toContain("SIGNAL SQLSTATE '45000'");
});
```

Also assert that the generated baseline contains the 26 product names, ERP schema, migration ledger, and no real order/member/payment inserts.

- [ ] **Step 2: Run the test and verify RED**

Run: `npm.cmd test -- databaseBaselineData.test.js`

Expected: FAIL because `oakved-demo-data.sql` is missing.

- [ ] **Step 3: Implement idempotent SQL seed**

Translate the existing 26-product definitions into a stored procedure scoped to the seed transaction. Use stable keys `tenant_id + keyword` for SPU lookup, `tenant_id + spu_id` for SKU lookup, ERP barcode `RH-121-<sku_id>`, and existing ERP unique keys for upsert. End with count guards:

```sql
SELECT COUNT(*) INTO @mall_count
FROM product_spu
WHERE tenant_id = 121 AND creator = 'furniture-agent-seed' AND status = 1 AND deleted = b'0';
IF @mall_count <> 26 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Oakved baseline expected 26 active demo products';
END IF;
```

Keep the PowerShell seeds as compatibility wrappers that execute `oakved-demo-data.sql` and audit counts; remove embedded Java/SQL duplication.

- [ ] **Step 4: Generate and validate the baseline**

Run: `npm.cmd run build:db-baseline`

Expected: creates `oakved-baseline.sql` deterministically.

Run: `npm.cmd test -- databaseBaselineGenerator.test.js databaseBaselineData.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- 'seed-furniture-agent-products.ps1' 'seed-mall-erp-products.ps1' 'furniture web/tests/databaseBaselineData.test.js' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-demo-data.sql' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs'
git commit -m "feat: include mall and ERP demo data in baseline"
```

### Task 3: Checksum-aware incremental migration runner

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/script/docker/invoke-local-migrations.ps1`
- Create: `furniture web/tests/databaseMigrationRunner.test.js`
- Modify: `furniture web/scripts/verify-db-migrations.mjs`
- Modify: `furniture web/tests/dbMigrations.test.js`

**Interfaces:**
- Consumes: `sql/mysql/migrations/VNNN__*.sql`, MySQL container `yudao-mysql-local`, database `ruoyi-vue-pro`.
- Produces: exit 0 with applied/skipped summary; exit non-zero on checksum mismatch, partial failure, missing lock, or invalid catalog.

- [ ] **Step 1: Write failing runner contract tests**

Assert the script contains migration ledger creation, `GET_LOCK`, `RELEASE_LOCK`, SHA-256 calculation, checksum comparison, sorted migration discovery, insert-after-success ordering, and no volume deletion:

```js
it("locks, verifies checksums, and records only successful migrations", () => {
  const source = readSource("../../yudao电商管理平台前后端/yudao-cloud/script/docker/invoke-local-migrations.ps1");
  expect(source).toContain("GET_LOCK('oakved_schema_migrations'");
  expect(source).toContain("Get-FileHash");
  expect(source).toContain("checksum_sha256");
  expect(source.indexOf("source $containerPath")).toBeLessThan(source.indexOf("INSERT INTO schema_migrations"));
  expect(source).not.toContain("down -v");
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `npm.cmd test -- databaseMigrationRunner.test.js dbMigrations.test.js`

Expected: FAIL because the runner and updated verifier contract are missing.

- [ ] **Step 3: Implement the runner**

Use PowerShell 5.1-compatible code. Create the ledger if absent, acquire a 30-second advisory lock, enumerate migrations, compare stored checksums, copy/apply pending SQL, insert the ledger row only after MySQL returns zero, and release the lock in `finally`. Existing databases with no ledger run idempotent migrations; any SQL failure stops immediately and leaves that version unrecorded.

Update `verify-db-migrations.mjs` to discover the numbered catalog rather than hard-code seven legacy paths, and verify generator, runner, baseline, reset, compose, and README references.

- [ ] **Step 4: Run tests**

Run: `npm.cmd test -- databaseMigrationRunner.test.js dbMigrations.test.js`

Expected: PASS.

Run: `npm.cmd run verify:db-migrations`

Expected: exit 0 and list all 14 numbered migrations plus infrastructure artifacts.

- [ ] **Step 5: Commit**

```powershell
git add -- 'furniture web/scripts/verify-db-migrations.mjs' 'furniture web/tests/dbMigrations.test.js' 'furniture web/tests/databaseMigrationRunner.test.js' 'yudao电商管理平台前后端/yudao-cloud/script/docker/invoke-local-migrations.ps1'
git commit -m "feat: apply checksum-aware database migrations"
```

### Task 4: Safe startup and explicit backup-first reset

**Files:**
- Modify: `start-yudao-infra.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/docker/start-local-infra.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/docker/docker-compose-local-infra.yml`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/docker/reset-local-infra.ps1`
- Create: `furniture web/tests/databaseInfrastructureSafety.test.js`

**Interfaces:**
- Consumes: generated baseline and migration runner.
- Produces: non-destructive normal startup; destructive reset available only after verified backup and exact confirmation.

- [ ] **Step 1: Write failing infrastructure safety tests**

```js
it("keeps normal startup non-destructive", () => {
  const rootStart = readSource("../../start-yudao-infra.ps1");
  const localStart = readSource("../../yudao电商管理平台前后端/yudao-cloud/script/docker/start-local-infra.ps1");
  expect(rootStart).not.toMatch(/ReimportSql|Recreate|down\s+-v/i);
  expect(localStart).not.toMatch(/Recreate|down\s+-v/i);
  expect(localStart).toContain("invoke-local-migrations.ps1");
});

it("requires backup validation and exact reset confirmation", () => {
  const reset = readSource("../../yudao电商管理平台前后端/yudao-cloud/script/docker/reset-local-infra.ps1");
  expect(reset).toContain("mysqldump");
  expect(reset).toContain("RESET OAKVED LOCAL DATA");
  expect(reset).toContain("Length -le 0");
  expect(reset.indexOf("mysqldump")).toBeLessThan(reset.indexOf("down -v"));
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `npm.cmd test -- databaseInfrastructureSafety.test.js`

Expected: FAIL because normal startup remains destructive and reset script is missing.

- [ ] **Step 3: Implement safe startup and reset**

Remove `ReimportSql` and `Recreate`. Mount only:

```yaml
- ../../sql/mysql/oakved-baseline.sql:/docker-entrypoint-initdb.d/01-oakved-baseline.sql:ro
```

After MySQL readiness, normal startup invokes `invoke-local-migrations.ps1` and exits on failure.

The reset script accepts optional `-BackupDirectory` and `-Confirmation`; it creates the directory, dumps the database, validates non-zero length and `CREATE TABLE` content, prompts when confirmation was not supplied, compares exact text, then and only then executes `down -v` and calls normal startup.

- [ ] **Step 4: Run safety tests**

Run: `npm.cmd test -- databaseInfrastructureSafety.test.js databaseMigrationRunner.test.js dbMigrations.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- 'start-yudao-infra.ps1' 'furniture web/tests/databaseInfrastructureSafety.test.js' 'yudao电商管理平台前后端/yudao-cloud/script/docker/start-local-infra.ps1' 'yudao电商管理平台前后端/yudao-cloud/script/docker/reset-local-infra.ps1' 'yudao电商管理平台前后端/yudao-cloud/script/docker/docker-compose-local-infra.yml'
git commit -m "fix: prevent database replacement during startup"
```

### Task 5: Runbook, empty-database integration test, and final verification

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/docker/README-local-infra.md`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/docker/test-oakved-baseline.ps1`
- Create: `furniture web/tests/databaseRunbook.test.js`

**Interfaces:**
- Consumes: baseline, compose file, migration runner, reset command.
- Produces: operator instructions and an isolated temporary-volume smoke test with explicit counts.

- [ ] **Step 1: Write failing runbook tests**

Assert documentation includes first install, normal start, migration creation, checksum immutability, backup-first reset, restore command, and exact warning that reset deletes local volumes. Assert the smoke script uses a separate Compose project/volume and verifies these SQL counts: 26 SPUs, 26 SKUs, 26 ERP products, 26 mappings, 26 stock rows, and 14 migration records.

- [ ] **Step 2: Run the test and verify RED**

Run: `npm.cmd test -- databaseRunbook.test.js`

Expected: FAIL because the runbook and smoke script do not yet describe the safe workflow.

- [ ] **Step 3: Implement documentation and isolated smoke test**

The smoke script must never reuse `yudao_mysql_data`. It creates a unique Compose project name, imports the committed baseline into MySQL 8.0, runs count queries, runs the migration runner twice, confirms the second run applies zero versions, and removes only its uniquely named temporary resources in `finally`.

Document commands:

```powershell
# Normal non-destructive startup
.\start-yudao-infra.ps1

# Generate/verify the committed baseline after adding a migration
Set-Location 'D:\code\furniture web'
npm.cmd run build:db-baseline
npm.cmd run verify:db-migrations

# Explicit local reset with backup
.\yudao电商管理平台前后端\yudao-cloud\script\docker\reset-local-infra.ps1
```

- [ ] **Step 4: Run all static and integration verification**

Run: `npm.cmd test -- databaseBaselineGenerator.test.js databaseBaselineData.test.js databaseMigrationRunner.test.js databaseInfrastructureSafety.test.js databaseRunbook.test.js dbMigrations.test.js`

Expected: all selected Vitest tests PASS.

Run: `powershell -ExecutionPolicy Bypass -File ".\yudao电商管理平台前后端\yudao-cloud\script\docker\test-oakved-baseline.ps1"`

Expected: isolated MySQL import succeeds; `spu=26 sku=26 erp=26 mappings=26 stock=26 migrations=14`; second migration run applies zero versions; exit 0.

Run: `npm.cmd run verify:db-migrations`

Expected: exit 0.

Run: `git diff --check`

Expected: exit 0 with no whitespace errors.

- [ ] **Step 5: Commit**

```powershell
git add -- 'furniture web/tests/databaseRunbook.test.js' 'yudao电商管理平台前后端/yudao-cloud/script/docker/README-local-infra.md' 'yudao电商管理平台前后端/yudao-cloud/script/docker/test-oakved-baseline.ps1'
git commit -m "docs: document safe database deployment workflow"
```

### Task 6: Final branch audit

**Files:**
- Verify only: all files committed by Tasks 1-5

**Interfaces:**
- Consumes: committed implementation.
- Produces: evidence that the branch satisfies the approved design without staging unrelated user files.

- [ ] **Step 1: Verify requirements against the design**

Read `docs/superpowers/specs/2026-07-14-safe-database-baseline-migrations-design.md` and map every completion criterion to a passing command or inspected file.

- [ ] **Step 2: Run fresh final verification**

```powershell
Set-Location 'D:\code\furniture web'
npm.cmd test -- databaseBaselineGenerator.test.js databaseBaselineData.test.js databaseMigrationRunner.test.js databaseInfrastructureSafety.test.js databaseRunbook.test.js dbMigrations.test.js
npm.cmd run verify:db-migrations
Set-Location 'D:\code'
powershell -ExecutionPolicy Bypass -File ".\yudao电商管理平台前后端\yudao-cloud\script\docker\test-oakved-baseline.ps1"
git diff --check HEAD~5..HEAD
git status --short
```

Expected: tests and smoke test exit 0; diff check is clean; status lists only pre-existing unrelated untracked files.

- [ ] **Step 3: Confirm branch and commits**

Run: `git branch --show-current`

Expected: `codex/agent-rag`.

Run: `git log -6 --oneline`

Expected: the design commit plus the five focused implementation commits.
