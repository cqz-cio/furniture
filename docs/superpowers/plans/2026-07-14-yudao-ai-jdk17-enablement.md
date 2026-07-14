# Yudao AI JDK 17 Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the customized Yudao Cloud backend to the matching official JDK 17/Spring Boot 3 baseline and make the complete AI module usable without breaking existing commerce features or requiring provider credentials at startup.

**Architecture:** Use official `yudao-cloud` commit `ff4ed31c1b1141e9c9b25c7e8edd61d8cd8745d6` as the JDK 17 counterpart to this repository's `2026.04-SNAPSHOT` baseline, then replay local changes with a deterministic three-way merge whose common base is local commit `57087aa6`. JDK 17 is selected only by repository PowerShell wrappers; database changes are backed up first and applied through a repeatable migration; `ai-server` remains isolated behind Gateway and only contacts providers for explicit feature requests.

**Tech Stack:** Microsoft OpenJDK 17 or Temurin JDK 17, Maven, Spring Boot 3.5.x, Spring Cloud 2025.0.x, Spring AI 1.1.x, MyBatis Plus, Nacos, Redis, MySQL 8, PowerShell 7/Windows PowerShell 5.1, Docker.

## Global Constraints

- Work only on `codex/agent-rag`; do not modify or merge `main` during implementation.
- Preserve all repository-specific storefront, furniture assistant, ERP, member, trade, statistics, dashboard, and payment behavior.
- Keep the installed JDK 8 and machine-wide `JAVA_HOME`/`PATH` unchanged.
- Use official baseline commit `ff4ed31c1b1141e9c9b25c7e8edd61d8cd8745d6`, not a newer Yudao release.
- Back up `ruoyi-vue-pro` before any schema write and never mutate Docker volumes.
- Do not commit, print, or place provider keys, passwords, bearer tokens, or authorization headers in frontend files.
- `ai-server` must start with no provider key and no external network; provider-dependent failures stay request-scoped.
- The migration and verification scripts must be safe to rerun.
- Stop at the first failed merge, migration, build, test, port check, registration check, or health check.

---

## File Map

- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Resolve-Jdk17.ps1`: locate and validate a Java 17 installation without changing the parent shell.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Invoke-MavenJdk17.ps1`: run Maven with child-process-only Java 17 environment variables.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Merge-Jdk17Baseline.ps1`: deterministic base/ours/theirs migration and conflict report.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Backup-AiDatabase.ps1`: timestamped logical MySQL backup with redacted output.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Apply-AiMigration.ps1`: apply the checked-in SQL transactionally and stop on error.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Verify-AiEnvironment.ps1`: verify Java, schema, menus, role mappings, ports, Nacos, and health.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Start-Jdk17Backend.ps1`: validate prerequisites and start selected JDK 17 services with per-service logs and process IDs.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Stop-Jdk17Backend.ps1`: stop only process IDs created by the launcher.
- `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/*.Tests.ps1`: executable contract tests for wrappers, merge safety, secrets, and SQL idempotency.
- `yudao电商管理平台前后端/yudao-cloud/sql/mysql/ai-module-enable.sql`: complete repeatable AI schema/menu/dictionary/role migration.
- `yudao电商管理平台前后端/yudao-cloud/pom.xml` and module POMs: official JDK 17 dependency and reactor alignment with AI enabled.
- `yudao电商管理平台前后端/yudao-cloud/yudao-module-ai/**`: JDK 17 AI implementation and keyless configuration.
- `yudao电商管理平台前后端/yudao-cloud/docs/jdk17-ai-local-development.md`: build, launch, verification, credential setup, and rollback instructions.

### Task 1: Repository-Scoped Java 17 Toolchain

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Resolve-Jdk17.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Invoke-MavenJdk17.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/Jdk17Toolchain.Tests.ps1`

**Interfaces:**
- Produces: `Resolve-Jdk17.ps1` writes a validated JDK home path; `Invoke-MavenJdk17.ps1 -MavenArgs string[]` forwards Maven's exit code.

- [x] **Step 1: Write the failing PowerShell contract test**

```powershell
$beforeJavaHome = $env:JAVA_HOME
$jdkHome = & "$PSScriptRoot/../Resolve-Jdk17.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Resolve-Jdk17 failed' }
$major = & (Join-Path $jdkHome 'bin/java.exe') -version 2>&1 | Select-Object -First 1
if ($major -notmatch 'version "17\.') { throw "Expected Java 17, got $major" }
& "$PSScriptRoot/../Invoke-MavenJdk17.ps1" -MavenArgs @('-version')
if ($LASTEXITCODE -ne 0) { throw 'Maven wrapper failed' }
if ($env:JAVA_HOME -ne $beforeJavaHome) { throw 'Parent JAVA_HOME was modified' }
```

- [x] **Step 2: Run it and confirm the wrapper is missing**

Run: `powershell -NoProfile -File .\script\jdk17\tests\Jdk17Toolchain.Tests.ps1`

Expected: non-zero exit because `Resolve-Jdk17.ps1` does not exist.

- [x] **Step 3: Install a trusted OpenJDK 17 side by side if no Java 17 is installed**

Preferred run: `winget install --id EclipseAdoptium.Temurin.17.JDK --exact --scope machine --accept-package-agreements --accept-source-agreements`

Fallback when GitHub Releases is unreachable: `winget install --id Microsoft.OpenJDK.17 --exact --scope machine --accept-package-agreements --accept-source-agreements`

Expected: Java 17 appears under its vendor's `Program Files` directory; the existing Temurin 8 directory remains present.

- [x] **Step 4: Implement the resolver and Maven wrapper**

The resolver checks `YUDao_JAVA17_HOME`, then `C:\Program Files\Eclipse Adoptium\jdk-17*`, executes `bin\java.exe -version`, accepts only major version 17, and returns a single resolved path. The Maven wrapper saves the child environment, assigns `JAVA_HOME` and prepends `bin` only while invoking `mvn.cmd`, then exits with Maven's exit code.

- [x] **Step 5: Run the contract and verify the system default is unchanged**

Run: `powershell -NoProfile -File .\script\jdk17\tests\Jdk17Toolchain.Tests.ps1`

Expected: exit 0; Maven reports Java 17; a new `java -version` in the original shell still reports Java 8.

- [x] **Step 6: Commit the scoped toolchain**

```powershell
git add script/jdk17/Resolve-Jdk17.ps1 script/jdk17/Invoke-MavenJdk17.ps1 script/jdk17/tests/Jdk17Toolchain.Tests.ps1
git commit -m "build: add repository scoped JDK 17 toolchain"
```

### Task 2: Deterministic Three-Way JDK 17 Baseline Migration

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Merge-Jdk17Baseline.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/MergeJdk17Baseline.Tests.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/**` for official baseline changes and preserved local changes.

**Interfaces:**
- Consumes: local base `57087aa6`, current working tree, official reference commit `ff4ed31c1b1141e9c9b25c7e8edd61d8cd8745d6`.
- Produces: migrated tree plus `.codex-temp/jdk17-merge/conflicts.txt`; exits non-zero while conflict markers remain.

- [x] **Step 1: Write a fixture test covering unchanged, locally changed, upstream changed, added, and deleted files**

```powershell
$cases = @('unchanged.txt','ours-only.txt','theirs-only.txt','both.txt','ours-added.txt','theirs-added.txt')
& "$PSScriptRoot/../Merge-Jdk17Baseline.ps1" -FixtureRoot "$PSScriptRoot/fixtures/merge"
if ($LASTEXITCODE -eq 0) { throw 'Fixture with both.txt conflict must stop' }
if (-not (Select-String -Quiet -Path "$PSScriptRoot/fixtures/merge/output/both.txt" -Pattern '^<<<<<<<')) {
    throw 'Conflict marker was not retained for manual resolution'
}
```

- [x] **Step 2: Run the fixture test and confirm it fails before the script exists**

Run: `powershell -NoProfile -File .\script\jdk17\tests\MergeJdk17Baseline.Tests.ps1`

Expected: non-zero exit caused by the missing merge script.

- [x] **Step 3: Implement the merge script**

For each path in the union of the three trees, extract base and ours with `git show`, read theirs from the detached official reference, take theirs when ours equals base, retain ours when theirs equals base, preserve one-sided additions, and use `git merge-file -p ours base theirs` for true concurrent changes. Write every unresolved path to the conflict report and never overwrite untracked paths.

- [x] **Step 4: Run the fixture test**

Run: `powershell -NoProfile -File .\script\jdk17\tests\MergeJdk17Baseline.Tests.ps1`

Expected: the test confirms deterministic outputs and deliberate non-zero status for the conflict fixture.

- [x] **Step 5: Run the migration against the repository and resolve every reported conflict**

Run: `powershell -NoProfile -File .\script\jdk17\Merge-Jdk17Baseline.ps1 -BaseCommit 57087aa6 -ReferenceRoot D:\code\.codex-temp\yudao-cloud-jdk17-ref -ReferenceCommit ff4ed31c1b1141e9c9b25c7e8edd61d8cd8745d6`

Expected: the initial run may stop with a finite conflict list. Resolve Maven/config/framework conflicts by preserving local business configuration inside the official JDK 17 structure, then rerun marker scanning until `rg "^(<<<<<<<|=======|>>>>>>>)"` finds nothing.

- [x] **Step 6: Verify migration provenance and custom-file retention**

Run: `git diff --check`

Run: `git diff --name-status 57087aa6 -- yudao-module-mall yudao-module-member yudao-module-pay yudao-module-erp yudao-module-statistics yudao-gateway yudao-module-system`

Expected: no whitespace/conflict errors and all intentional custom modules still exist.

- [x] **Step 7: Commit the mechanical baseline migration**

```powershell
git add pom.xml yudao-dependencies yudao-framework yudao-gateway yudao-module-* yudao-server script/jdk17/Merge-Jdk17Baseline.ps1 script/jdk17/tests/MergeJdk17Baseline.Tests.ps1
git commit -m "build: migrate backend to official JDK 17 baseline"
```

### Task 3: Compile and Repair Customized Business Modules

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-framework/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-gateway/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-system/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-member/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-pay/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/**`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-statistics/**`

**Interfaces:**
- Consumes: Task 2 migrated Spring Boot 3/Jakarta dependency graph.
- Produces: compiling framework, Gateway, system, and customized commerce services on Java 17.

- [x] **Step 1: Compile shared infrastructure and capture the first concrete incompatibility**

Run: `powershell -NoProfile -File .\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-dependencies,yudao-framework','-am','-DskipTests','install')`

Expected: either success or a compiler error naming an exact obsolete import/API; never perform blind global namespace replacement.

- [x] **Step 2: Repair shared code with official JDK 17 equivalents and rerun until green**

Apply `javax.servlet` to `jakarta.servlet`, `javax.validation` to `jakarta.validation`, Spring Security 6 configuration, and Boot 3 property changes only where compilation or configuration tests prove they are needed. Preserve the custom API access-log filter and global exception behavior.

- [x] **Step 3: Compile Gateway and system-server**

Run: `powershell -NoProfile -File .\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-gateway,yudao-module-system/yudao-module-system-server','-am','-DskipTests','package')`

Expected: `BUILD SUCCESS` on Java 17.

- [x] **Step 4: Compile commerce services in bounded groups and repair only reported compatibility issues**

Run: `powershell -NoProfile -File .\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-module-mall,yudao-module-member,yudao-module-pay,yudao-module-erp,yudao-module-statistics','-am','-DskipTests','package')`

Expected: `BUILD SUCCESS`; furniture assistant, checkout, ERP, member, and dashboard source files remain present.

- [x] **Step 5: Run module tests**

Run: `powershell -NoProfile -File .\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-gateway,yudao-module-system/yudao-module-system-server,yudao-module-mall,yudao-module-member,yudao-module-pay,yudao-module-erp,yudao-module-statistics','-am','test')`

Expected: all selected tests pass; provider-dependent integration tests are excluded only by their existing integration-test profile, not deleted.

- [x] **Step 6: Commit compatibility repairs**

```powershell
git add yudao-framework yudao-gateway yudao-module-system yudao-module-mall yudao-module-member yudao-module-pay yudao-module-erp yudao-module-statistics
git commit -m "fix: preserve commerce services on Spring Boot 3"
```

### Task 4: Enable AI Reactor and Keyless Startup

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/pom.xml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-dependencies/pom.xml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-ai/**`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AiSecretSafety.Tests.ps1`

**Interfaces:**
- Produces: buildable `ai-server` on port 48090; administrative endpoints initialize with zero configured providers; provider invocation returns an existing Yudao business error instead of terminating the application.

- [x] **Step 1: Write the secret and configuration contract test**

```powershell
$files = Get-ChildItem "$PSScriptRoot/../../../yudao-module-ai" -Recurse -File -Include *.yaml,*.yml,*.properties
$forbidden = '(?i)(sk-[A-Za-z0-9_-]{12,}|Bearer\s+[A-Za-z0-9._-]{12,}|api-key:\s*[^$\s][^\s]+)'
if ($files | Select-String -Pattern $forbidden) { throw 'Provider credential-like value is checked in' }
$local = Get-Content -Raw "$PSScriptRoot/../../../yudao-module-ai/yudao-module-ai-server/src/main/resources/application-local.yaml"
if ($local -notmatch '\$\{[A-Z0-9_]+:}') { throw 'Expected empty environment-variable provider defaults' }
```

- [x] **Step 2: Run the safety test and confirm current demonstration values fail it**

Run: `powershell -NoProfile -File .\script\jdk17\tests\AiSecretSafety.Tests.ps1`

Expected: non-zero exit identifying file and line only, with matched secret text redacted from console output.

- [x] **Step 3: Enable the AI module and sanitize configuration**

Uncomment `yudao-module-ai` in the root reactor, retain official Spring AI dependency management, keep `server.port: 48090`, and replace all checked-in provider values with empty environment-variable defaults such as `${OPENAI_API_KEY:}`. Do not add provider auto-configuration that requires a non-empty key during bean creation.

- [x] **Step 4: Add keyless service tests**

Create/adjust AI Spring tests so an empty provider repository can construct API-key, model, role, knowledge-metadata, and workflow services, while chat/model lookup without an enabled key throws the module's explicit configuration error. Assert the exception is returned by the request and does not close the Spring context.

- [x] **Step 5: Build and test AI**

Run: `powershell -NoProfile -File .\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-module-ai/yudao-module-ai-server','-am','test')`

Expected: `BUILD SUCCESS`; tests requiring live providers remain opt-in and do not execute in the default suite.

- [x] **Step 6: Re-run the secret contract and scan the repository**

Run: `powershell -NoProfile -File .\script\jdk17\tests\AiSecretSafety.Tests.ps1`

Run: `rg -l "(?i)(sk-[A-Za-z0-9_-]{12,}|Bearer [A-Za-z0-9._-]{12,})" --glob '!target/**' --glob '!.git/**'`

Expected: the AI safety test passes and no credential-like provider value remains in tracked runtime configuration or release SQL.

- [x] **Step 7: Commit AI enablement**

```powershell
git add pom.xml yudao-dependencies/pom.xml yudao-module-ai script/jdk17/tests/AiSecretSafety.Tests.ps1
git commit -m "feat: enable AI module with keyless startup"
```

### Task 5: Backed-Up, Repeatable AI Database Migration

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/ai-module-enable.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Backup-AiDatabase.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Apply-AiMigration.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AiMigration.Tests.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/.gitignore`

**Interfaces:**
- Produces: `.local-backups/mysql/ruoyi-vue-pro-<timestamp>.sql`; 14 required tables; intact existing rows; menu and super-admin role mappings.

- [x] **Step 1: Write the schema contract test**

```powershell
$required = @('ai_api_key','ai_model','ai_chat_role','ai_chat_conversation','ai_chat_message','ai_image','ai_knowledge','ai_knowledge_document','ai_knowledge_segment','ai_mind_map','ai_tool','ai_music','ai_workflow','ai_write')
$actual = docker exec yudao-mysql-local mysql -uroot -p$env:YUDAO_MYSQL_ROOT_PASSWORD -Nse "SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'ai_%'"
$missing = $required | Where-Object { $_ -notin $actual }
if ($missing) { throw "Missing AI tables: $($missing -join ', ')" }
```

- [x] **Step 2: Run the contract and verify it reports the eleven missing tables**

Run: `powershell -NoProfile -File .\script\jdk17\tests\AiMigration.Tests.ps1`

Expected: non-zero exit listing missing table names and no database changes.

- [x] **Step 3: Implement backup and restore-safe output handling**

The backup script requires `YUDAO_MYSQL_ROOT_PASSWORD`, verifies `mysqldump` inside `yudao-mysql-local`, writes under ignored `.local-backups/mysql`, checks that the output is non-empty and contains `CREATE TABLE`, and prints only the backup path and byte count. Add `.local-backups/` to `.gitignore`.

- [x] **Step 4: Define the complete idempotent schema and metadata migration**

Create all 14 tables from the JDK 17 data objects and mapper usage with `CREATE TABLE IF NOT EXISTS`, including BaseDO audit columns, soft-delete fields, tenant columns where the module's annotations require them, JSON/text column widths, indexes on conversation/user/knowledge/document/model/status fields, and matching primary-key types. Use guarded `ALTER TABLE` procedures for missing columns on the existing three tables. Upsert dictionaries and the full `/ai` menu tree by stable menu name/permission, then insert missing `system_role_menu` rows for the super-admin role. Disable existing unverified `ai_api_key` rows and do not insert a usable key or enabled fake model.

- [x] **Step 5: Back up and apply once**

Run: `powershell -NoProfile -File .\script\jdk17\Backup-AiDatabase.ps1`

Run: `powershell -NoProfile -File .\script\jdk17\Apply-AiMigration.ps1`

Expected: a timestamped non-empty backup exists before SQL execution; migration exits 0.

- [x] **Step 6: Apply a second time and verify stable counts**

Run: `powershell -NoProfile -File .\script\jdk17\Apply-AiMigration.ps1`

Run: `powershell -NoProfile -File .\script\jdk17\tests\AiMigration.Tests.ps1`

Expected: second application exits 0; all 14 tables exist; menu names and role-menu pairs have no duplicates; existing business table counts are unchanged.

- [x] **Step 7: Commit database enablement**

```powershell
git add .gitignore sql/mysql/ai-module-enable.sql script/jdk17/Backup-AiDatabase.ps1 script/jdk17/Apply-AiMigration.ps1 script/jdk17/tests/AiMigration.Tests.ps1
git commit -m "feat: add repeatable AI database migration"
```

### Task 6: Safe One-Command Backend Lifecycle

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Start-Jdk17Backend.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Stop-Jdk17Backend.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Verify-AiEnvironment.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/BackendLifecycle.Tests.ps1`

**Interfaces:**
- Produces: `Start-Jdk17Backend.ps1 -Services string[]` and `Stop-Jdk17Backend.ps1`; state under ignored `.local-run/jdk17`; non-zero exit on duplicate port/service or failed health.

- [ ] **Step 1: Write lifecycle contract tests with a deliberately occupied test port**

```powershell
$listener = [System.Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 48090)
$listener.Start()
try {
    & "$PSScriptRoot/../Start-Jdk17Backend.ps1" -Services @('ai-server') -SkipBuild
    if ($LASTEXITCODE -eq 0) { throw 'Launcher accepted an occupied ai-server port' }
} finally { $listener.Stop() }
```

- [ ] **Step 2: Run the test and confirm the launcher is missing**

Run: `powershell -NoProfile -File .\script\jdk17\tests\BackendLifecycle.Tests.ps1`

Expected: non-zero exit caused by the missing launcher.

- [ ] **Step 3: Implement start, stop, and verification scripts**

Map services to artifact, port, Nacos name, and health URL; validate Docker dependencies; reject occupied ports; reject an already registered same-name instance; start Java 17 child processes hidden with log and PID files; poll health with a bounded timeout; redact secrets from displayed commands. Stop only recorded PIDs after confirming their command line belongs to this repository.

- [ ] **Step 4: Run lifecycle tests**

Run: `powershell -NoProfile -File .\script\jdk17\tests\BackendLifecycle.Tests.ps1`

Expected: occupied ports are rejected and stale/unrelated PIDs are never terminated.

- [ ] **Step 5: Build and start Gateway, system, product, and AI services**

Run: `powershell -NoProfile -File .\script\jdk17\Start-Jdk17Backend.ps1 -Services @('gateway','system-server','product-server','ai-server')`

Expected: each selected service reports a PID, health success, and Nacos registration; Gateway exposes `ai-server` without any external provider connection during startup.

- [ ] **Step 6: Verify and stop cleanly**

Run: `powershell -NoProfile -File .\script\jdk17\Verify-AiEnvironment.ps1`

Run: `powershell -NoProfile -File .\script\jdk17\Stop-Jdk17Backend.ps1`

Expected: verification passes and only launcher-created processes stop; Docker containers continue running.

- [ ] **Step 7: Commit lifecycle automation**

```powershell
git add script/jdk17/Start-Jdk17Backend.ps1 script/jdk17/Stop-Jdk17Backend.ps1 script/jdk17/Verify-AiEnvironment.ps1 script/jdk17/tests/BackendLifecycle.Tests.ps1
git commit -m "dev: add safe JDK 17 backend launcher"
```

### Task 7: End-to-End Regression and Operator Documentation

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/docs/jdk17-ai-local-development.md`
- Modify: tests and fixtures only where verified JDK 17 behavior requires compatibility updates.

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: evidence that AI administration works keylessly and commerce behavior remains intact; exact local run and rollback guide.

- [ ] **Step 1: Run the full backend test reactor**

Run: `powershell -NoProfile -File .\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('clean','test')`

Expected: `BUILD SUCCESS`; any live-provider test is explicitly tagged/integration-profiled rather than silently weakened.

- [ ] **Step 2: Run existing furniture, product, checkout, ERP, and dashboard regression commands documented in the repository**

Run each command from the relevant existing README/package script using the already-running local dependencies. Expected: every previously passing suite remains green and the furniture assistant returns its deterministic fallback when its remote provider is unavailable.

- [ ] **Step 3: Exercise keyless AI through Gateway**

Start the required services, log in as the local administrator, request menu data, API-key/model/role/knowledge/workflow list endpoints, and make one chat request with no enabled key. Expected: pages/list endpoints succeed; chat returns the defined configuration error; `ai-server` health remains up after the request.

- [ ] **Step 4: Document secure credential validation without checking in a credential**

Document entering a user-owned key in the backend AI API-key page, associating chat and embedding models, then running one chat and one knowledge vectorization request. Mark this as a manual credential-dependent acceptance check; never save its value in scripts, YAML, shell history examples, screenshots, or logs.

- [ ] **Step 5: Write the local-development and rollback guide**

Include prerequisites, scoped Java behavior, build commands, migration/backup paths, one-command start/stop, service ports, AI menu/permission checks, keyless expected behavior, credential setup, log locations, and rollback: stop JDK 17 processes, restore the named logical backup, and launch the preserved JDK 8 services.

- [ ] **Step 6: Perform final verification**

Run: `git diff --check`

Run: `git status --short`

Run: `powershell -NoProfile -File .\script\jdk17\Verify-AiEnvironment.ps1`

Expected: no patch errors, only intentional tracked changes plus pre-existing unrelated untracked files, and all automated environment checks pass.

- [ ] **Step 7: Commit verified documentation and test adjustments**

```powershell
git add docs/jdk17-ai-local-development.md
git add -u
git commit -m "docs: document JDK 17 AI operations and rollback"
```

### Task 8: Review Gate Before Main Integration

**Files:**
- Review only; no source mutation unless review finds a concrete defect.

**Interfaces:**
- Produces: a clean, pushed `codex/agent-rag` branch ready for user-approved PR/merge; does not merge `main`.

- [ ] **Step 1: Review the complete branch diff against main**

Run: `git diff --stat main...HEAD`

Run: `git diff --check main...HEAD`

Expected: changes are limited to JDK 17 compatibility, AI enablement, safe tooling, schema, tests, and documentation.

- [ ] **Step 2: Re-run the release verification set from a clean process state**

Stop recorded services, run the full Maven suite, apply the AI migration twice, start the required services, run environment verification, perform the keyless request, and stop recorded services. Expected: every command exits 0 except the intentionally controlled provider request response.

- [ ] **Step 3: Push the development branch**

Run: `git push origin codex/agent-rag`

Expected: remote branch points at the verified local HEAD.

- [ ] **Step 4: Present integration evidence and wait for explicit merge authorization**

Report commit IDs, test commands/results, backup path, running/stopped service state, known credential-dependent manual check, and the exact PR/merge choice. Do not merge into `main` until the user explicitly authorizes it.
