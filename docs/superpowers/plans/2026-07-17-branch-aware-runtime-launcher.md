# Branch-Aware Oakved Runtime Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and install one branch-aware launcher that starts the ERP admin UI, furniture storefront, backend, and matching branch database only after provenance and migration gates pass.

**Architecture:** Keep a versioned PowerShell implementation under `scripts/runtime`, split into pure worktree/catalog functions, database migration orchestration, and managed-process lifecycle functions. Install a stable copy under `D:\code\.runtime\bin`, resolve targets only from explicit `-Branch` or `-Worktree` arguments, use one active fixed-port runtime, and isolate MySQL state by deterministic branch database names.

**Tech Stack:** Windows PowerShell 5.1, Pester 3.4-compatible tests, Git worktrees, Docker CLI, MySQL 8, Maven/JDK 17, pnpm, Vite.

## Global Constraints

- The launcher entry point is `D:\code\.runtime\bin\oakved.ps1`.
- It supports `start -Branch`, `start -Worktree`, `status`, and `stop`.
- It never infers the target from the caller's current directory and never performs `git pull`, merge, checkout, reset, commit, or push.
- `main` must be clean; feature worktrees may be dirty and must report `dirty=true`.
- One runtime is active at a time on admin `80`, storefront `5173`, and backend `48080`; unknown port owners fail closed.
- Every branch uses a deterministic branch-scoped MySQL database and runtime identifier.
- Database validation, backup, pending migrations, and exact ledger verification complete before any application process starts.
- Applied migration checksum mismatch, database-ahead state, backup failure, migration failure, or ledger mismatch blocks startup.
- No automatic database drop, volume deletion, reset, downgrade, or claimed DDL rollback.
- Runtime state, logs, locks, and backups live under `D:\code\.runtime` and are not committed.
- Preserve all unrelated dirty and untracked files in `D:\code`.

---

### Task 1: Worktree resolution and runtime identity

**Files:**
- Create: `scripts/runtime/Oakved.Runtime.psm1`
- Create: `scripts/runtime/tests/Oakved.Runtime.Worktree.Tests.ps1`

**Interfaces:**
- Consumes: repository root `D:\code`, output of `git worktree list --porcelain`.
- Produces: `Get-OakvedWorktreeInventory`, `Resolve-OakvedTarget`, `Get-OakvedRuntimeId`, `Get-OakvedProjectLayout`, and a target object with `Branch`, `Commit`, `Dirty`, `Worktree`, `RuntimeId`, and validated project paths.

- [ ] **Step 1: Write failing worktree and identity tests**

```powershell
$module = Join-Path $PSScriptRoot '..\Oakved.Runtime.psm1'
Import-Module $module -Force

Describe 'Resolve-OakvedTarget' {
    It 'resolves an exact branch and never falls back to repository root' {
        $inventory = @(
            [pscustomobject]@{ Worktree='D:\code'; Branch='codex/agent-rag'; Commit='aaaa'; Detached=$false },
            [pscustomobject]@{ Worktree='D:\code\.worktrees\main-runtime'; Branch='main'; Commit='bbbb'; Detached=$false }
        )
        $result = Resolve-OakvedTarget -Branch main -Inventory $inventory -GitStatusProvider { param($p) '' }
        $result.Worktree | Should Be 'D:\code\.worktrees\main-runtime'
        $result.Branch | Should Be 'main'
    }

    It 'rejects a missing branch instead of using the current directory' {
        { Resolve-OakvedTarget -Branch missing -Inventory @() -GitStatusProvider { '' } } |
            Should Throw 'Branch missing is not checked out in a worktree.'
    }

    It 'rejects detached and ambiguous targets' {
        $detached = @([pscustomobject]@{ Worktree='D:\w'; Branch=$null; Commit='aaaa'; Detached=$true })
        { Resolve-OakvedTarget -Worktree 'D:\w' -Inventory $detached -GitStatusProvider { '' } } |
            Should Throw 'Detached worktrees are not supported.'
    }

    It 'rejects dirty main and reports dirty feature branches' {
        $main = @([pscustomobject]@{ Worktree='D:\main'; Branch='main'; Commit='aaaa'; Detached=$false })
        { Resolve-OakvedTarget -Branch main -Inventory $main -GitStatusProvider { ' M tracked.txt' } } |
            Should Throw 'main worktree must be clean.'
        $feature = @([pscustomobject]@{ Worktree='D:\feature'; Branch='codex/x'; Commit='bbbb'; Detached=$false })
        (Resolve-OakvedTarget -Branch 'codex/x' -Inventory $feature -GitStatusProvider { ' M tracked.txt' }).Dirty |
            Should Be $true
    }
}

Describe 'Get-OakvedRuntimeId' {
    It 'is stable and collision-resistant for sanitized branch collisions' {
        (Get-OakvedRuntimeId 'codex/a-b') | Should Not Be (Get-OakvedRuntimeId 'codex/a_b')
        (Get-OakvedRuntimeId 'main') | Should Match '^main_[0-9a-f]{8}$'
    }
}
```

- [ ] **Step 2: Run the tests to verify RED**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Worktree.Tests.ps1 -PassThru
```

Expected: FAIL because `Oakved.Runtime.psm1` and exported functions do not exist.

- [ ] **Step 3: Implement target resolution and layout validation**

```powershell
function Get-OakvedRuntimeId([string]$Branch) {
    $slug = ($Branch.ToLowerInvariant() -replace '[^a-z0-9]+','_').Trim('_')
    if ($slug.Length -gt 32) { $slug = $slug.Substring(0, 32).TrimEnd('_') }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Branch)))).Replace('-','').ToLowerInvariant()
    } finally { $sha.Dispose() }
    return "${slug}_$($hash.Substring(0,8))"
}

function Resolve-OakvedTarget {
    param([string]$Branch, [string]$Worktree, [object[]]$Inventory, [scriptblock]$GitStatusProvider)
    $matches = if ($Branch) { @($Inventory | Where-Object Branch -eq $Branch) } else {
        @($Inventory | Where-Object { [IO.Path]::GetFullPath($_.Worktree) -eq [IO.Path]::GetFullPath($Worktree) })
    }
    if ($matches.Count -eq 0) {
        if ($Branch) { throw "Branch $Branch is not checked out in a worktree." }
        throw "Worktree $Worktree is not registered."
    }
    if ($matches.Count -ne 1) { throw 'Target must resolve to exactly one worktree.' }
    $match = $matches[0]
    if ($match.Detached) { throw 'Detached worktrees are not supported.' }
    $dirty = -not [string]::IsNullOrWhiteSpace((& $GitStatusProvider $match.Worktree) -join "`n")
    if ($match.Branch -eq 'main' -and $dirty) { throw 'main worktree must be clean.' }
    [pscustomobject]@{
        Branch=$match.Branch; Commit=$match.Commit; Dirty=$dirty; Worktree=$match.Worktree
        RuntimeId=Get-OakvedRuntimeId $match.Branch
    }
}
```

Parse porcelain worktree blocks without shell-current-directory assumptions, validate the required `furniture web`, admin UI, `yudao-cloud`, migration, baseline, backend start/stop, and package files beneath the resolved path, and export only public functions.

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Worktree.Tests.ps1 -PassThru
```

Expected: all worktree tests pass with `FailedCount = 0`.

- [ ] **Step 5: Commit Task 1**

```bash
git add scripts/runtime/Oakved.Runtime.psm1 scripts/runtime/tests/Oakved.Runtime.Worktree.Tests.ps1
git commit -m "feat: resolve explicit Oakved runtime worktrees"
```

---

### Task 2: Branch database catalog and migration gate

**Files:**
- Modify: `scripts/runtime/Oakved.Runtime.psm1`
- Create: `scripts/runtime/tests/Oakved.Runtime.Migrations.Tests.ps1`

**Interfaces:**
- Consumes: target `RuntimeId`, selected worktree migration directory and baseline, Docker container `yudao-mysql-local`, MySQL root credential from the selected compose configuration.
- Produces: `Get-OakvedMigrationCatalog`, `Compare-OakvedMigrationLedger`, `Get-OakvedDatabaseName`, `Invoke-OakvedDatabaseGate`, and a database result with `Name`, `Version`, `CatalogVersion`, `BackupPath`, and `AppliedCount`.

- [ ] **Step 1: Write failing pure migration contract tests**

```powershell
Import-Module (Join-Path $PSScriptRoot '..\Oakved.Runtime.psm1') -Force

Describe 'Oakved migration catalog' {
    It 'rejects gaps and duplicate versions' {
        { Get-OakvedMigrationCatalog -Files @('V001__one.sql','V003__three.sql') -ContentProvider { param($f) 'SELECT 1;' } } |
            Should Throw 'Migration catalog must be contiguous.'
        { Get-OakvedMigrationCatalog -Files @('V001__one.sql','V001__other.sql') -ContentProvider { param($f) 'SELECT 1;' } } |
            Should Throw 'Duplicate migration version 001.'
    }

    It 'normalizes line endings before hashing' {
        $a = Get-OakvedMigrationCatalog -Files @('V001__one.sql') -ContentProvider { "SELECT 1;`r`n" }
        $b = Get-OakvedMigrationCatalog -Files @('V001__one.sql') -ContentProvider { "SELECT 1;`n" }
        $a[0].Checksum | Should Be $b[0].Checksum
    }

    It 'rejects checksum mismatch and database-ahead state' {
        $catalog = @([pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='abc'})
        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='def'}) } |
            Should Throw 'Checksum mismatch for V001__one.sql.'
        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='002';ScriptName='V002__two.sql';Description='two';Checksum='abc'}) } |
            Should Throw 'Database contains migration 002 that is not present in the selected branch.'
    }
}

Describe 'Get-OakvedDatabaseName' {
    It 'uses the runtime id and remains within MySQL identifier limits' {
        $name = Get-OakvedDatabaseName 'codex_feature_12345678'
        $name | Should Be 'oakved_codex_feature_12345678'
        $name.Length | Should BeLessThan 65
    }
}
```

- [ ] **Step 2: Run migration tests to verify RED**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Migrations.Tests.ps1 -PassThru
```

Expected: FAIL because catalog and database functions are absent.

- [ ] **Step 3: Implement the pure catalog comparison**

```powershell
function Get-OakvedMigrationCatalog {
    param([string[]]$Files, [scriptblock]$ContentProvider)
    $rows = foreach ($file in ($Files | Sort-Object)) {
        $name = [IO.Path]::GetFileName($file)
        if ($name -notmatch '^V(?<version>\d{3})__(?<description>[a-z0-9_]+)\.sql$') {
            throw "Invalid migration filename $name."
        }
        $content = ((& $ContentProvider $file) -replace "`r`n", "`n")
        [pscustomobject]@{
            Version=$Matches.version; ScriptName=$name; Description=($Matches.description -replace '_',' ')
            Checksum=Get-OakvedSha256 $content; Path=$file
        }
    }
    $duplicates = @($rows | Group-Object Version | Where-Object Count -gt 1)
    if ($duplicates) { throw "Duplicate migration version $($duplicates[0].Name)." }
    for ($i=0; $i -lt $rows.Count; $i++) {
        if ([int]$rows[$i].Version -ne $i + 1) { throw 'Migration catalog must be contiguous.' }
    }
    return @($rows)
}
```

`Compare-OakvedMigrationLedger` must compare every applied row by version, filename, description, and checksum, return only the ordered pending catalog suffix, and reject applied rows not present in the selected catalog.

- [ ] **Step 4: Implement the MySQL gate around the pure comparison**

Use injected command wrappers for tests and real Docker commands in production. The implementation must:

```powershell
$database = Get-OakvedDatabaseName $Target.RuntimeId
Invoke-OakvedDockerMySql -Sql "CREATE DATABASE IF NOT EXISTS ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
$exists = Invoke-OakvedDockerMySql -Database $database -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database' AND table_name='schema_migrations';"
if ([int]$exists -eq 0) {
    Invoke-OakvedSqlFile -Database $database -Path $Layout.Baseline
}
$lockName = "oakved_schema_$database"
if ((Invoke-OakvedDockerMySql -Database $database -Sql "SELECT GET_LOCK('$lockName',30);").Trim() -ne '1') {
    throw "Could not acquire migration lock for $database."
}
```

When pending migrations exist, create `D:\code\.runtime\backups\<database>\<UTC>.sql`, require a non-zero file containing `CREATE TABLE` or `INSERT INTO`, execute each pending file sequentially, and insert its ledger row only after successful execution. Always release the lock. Re-read and compare the complete ledger after migration. Return the exact resulting database and versions.

- [ ] **Step 5: Run migration tests to verify GREEN**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Migrations.Tests.ps1 -PassThru
```

Expected: all migration contract tests pass with `FailedCount = 0`.

- [ ] **Step 6: Commit Task 2**

```bash
git add scripts/runtime/Oakved.Runtime.psm1 scripts/runtime/tests/Oakved.Runtime.Migrations.Tests.ps1
git commit -m "feat: gate branch database migrations"
```

---

### Task 3: Managed process lifecycle, build fingerprints, and provenance

**Files:**
- Modify: `scripts/runtime/Oakved.Runtime.psm1`
- Create: `scripts/runtime/tests/Oakved.Runtime.Processes.Tests.ps1`

**Interfaces:**
- Consumes: resolved target, validated layout, database gate result, fixed ports `80`, `5173`, and `48080`.
- Produces: `Get-OakvedBuildFingerprint`, `Start-OakvedRuntime`, `Stop-OakvedRuntime`, `Get-OakvedRuntimeStatus`, atomic `runtime.json`, logs, and verified managed process metadata.

- [ ] **Step 1: Write failing process safety tests**

```powershell
Import-Module (Join-Path $PSScriptRoot '..\Oakved.Runtime.psm1') -Force

Describe 'Oakved managed lifecycle' {
    It 'refuses an unknown fixed-port owner' {
        { Assert-OakvedPortsAvailable -Ports @(80,5173,48080) -ListenerProvider { @([pscustomobject]@{Port=5173;Pid=999}) } -ManagedPids @() } |
            Should Throw 'Port 5173 is owned by unmanaged PID 999.'
    }

    It 'does not stop a reused PID with a different start time' {
        $manifest = [pscustomobject]@{ Processes=@([pscustomobject]@{Pid=42;StartTime='2026-01-01T00:00:00Z';Name='backend'}) }
        $stopped = @()
        Stop-OakvedRuntime -Manifest $manifest -ProcessProvider { param($id) [pscustomobject]@{Id=$id;StartTime=[datetime]'2026-01-02T00:00:00Z'} } -Stopper { param($id) $script:stopped += $id }
        $stopped.Count | Should Be 0
    }

    It 'writes manifest atomically only after all health gates pass' {
        $writes = @()
        Write-OakvedManifest -Manifest @{branch='main'} -Path 'D:\state\runtime.json' `
            -Writer { param($path,$content) $script:writes += $path } `
            -Mover { param($source,$destination) $script:writes += $destination }
        $writes[0] | Should Be 'D:\state\runtime.json.tmp'
        $writes[-1] | Should Be 'D:\state\runtime.json'
    }
}
```

- [ ] **Step 2: Run process tests to verify RED**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Processes.Tests.ps1 -PassThru
```

Expected: FAIL because lifecycle functions are absent.

- [ ] **Step 3: Implement safe stop, ports, fingerprints, and manifest writes**

```powershell
function Assert-OakvedPortsAvailable {
    param([int[]]$Ports, [scriptblock]$ListenerProvider, [int[]]$ManagedPids)
    foreach ($listener in @(& $ListenerProvider)) {
        if ($listener.Port -in $Ports -and $listener.Pid -notin $ManagedPids) {
            throw "Port $($listener.Port) is owned by unmanaged PID $($listener.Pid)."
        }
    }
}

function Write-OakvedManifest {
    param($Manifest,[string]$Path,[scriptblock]$Writer,[scriptblock]$Mover)
    $json = $Manifest | ConvertTo-Json -Depth 8
    & $Writer "$Path.tmp" $json
    & $Mover "$Path.tmp" $Path
}
```

Verify PID start time before `taskkill /T`, never kill by port alone, include commit/dirty/catalog/lockfile hashes in the build fingerprint, and maintain state under `D:\code\.runtime`.

- [ ] **Step 4: Implement exact child startup commands**

Start backend from the selected `yudao-cloud` directory with branch database overrides:

```powershell
$backendArgs = @(
    '-jar', $Layout.ServerJar, '--spring.profiles.active=local',
    "--spring.datasource.dynamic.datasource.master.url=jdbc:mysql://127.0.0.1:3306/$($Database.Name)?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true",
    "--spring.datasource.dynamic.datasource.slave.url=jdbc:mysql://127.0.0.1:3306/$($Database.Name)?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true"
)
```

Build it with the selected worktree's `Invoke-MavenJdk17.ps1` when the fingerprint changed. Start admin with `pnpm.cmd dev -- --host 0.0.0.0 --port 80 --strictPort`, and storefront with `npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort`. Give both worktree-scoped `VITE_CACHE_DIR` values and explicit backend/storefront URLs. Record wrapper PID, start time, command role, working directory, log files, ports, branch, commit, runtime ID, database, and versions.

- [ ] **Step 5: Implement health and provenance status**

Require backend `/actuator/health` to report `UP`, both frontend URLs to return HTTP 200, every managed PID to match recorded start time, fixed ports to be owned by managed process trees, and database ledger version to equal catalog version. `status` must return non-zero for a stale manifest or mismatch and print every provenance field.

- [ ] **Step 6: Run process tests to verify GREEN**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Processes.Tests.ps1 -PassThru
```

Expected: all process safety tests pass with `FailedCount = 0`.

- [ ] **Step 7: Commit Task 3**

```bash
git add scripts/runtime/Oakved.Runtime.psm1 scripts/runtime/tests/Oakved.Runtime.Processes.Tests.ps1
git commit -m "feat: manage Oakved branch runtime processes"
```

---

### Task 4: CLI, stable installation, and cross-session policy

**Files:**
- Create: `scripts/runtime/oakved.ps1`
- Create: `scripts/runtime/install-oakved-runtime.ps1`
- Create: `scripts/runtime/README.md`
- Create: `scripts/runtime/tests/Oakved.Runtime.Cli.Tests.ps1`
- Modify: `.gitignore`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: public module functions from Tasks 1-3.
- Produces: installed `D:\code\.runtime\bin\oakved.ps1`, `start`, `status`, `stop`, optional creation of `D:\code\.worktrees\main-runtime` during installation, and durable instructions for future Codex sessions.

- [ ] **Step 1: Write failing CLI and installation tests**

```powershell
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$cli = Join-Path $repo 'scripts\runtime\oakved.ps1'
$installer = Join-Path $repo 'scripts\runtime\install-oakved-runtime.ps1'

Describe 'oakved CLI contract' {
    It 'requires exactly one target for start' {
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cli start 2>&1 | Out-String
        $output | Should Match 'Specify exactly one of -Branch or -Worktree'
        $LASTEXITCODE | Should Not Be 0
    }
    It 'accepts status and stop without a target' {
        (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cli status -RuntimeRoot $TestDrive) |
            Out-String | Should Match 'No Oakved runtime is active'
        $LASTEXITCODE | Should Be 0
    }
    It 'installs the launcher outside worktrees' {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer `
            -InstallRoot (Join-Path $TestDrive '.runtime') -RepositoryRoot $repo -SkipMainWorktree
        Test-Path (Join-Path $TestDrive '.runtime\bin\oakved.ps1') | Should Be $true
        Test-Path (Join-Path $TestDrive '.runtime\bin\Oakved.Runtime.psm1') | Should Be $true
    }
}
```

- [ ] **Step 2: Run CLI tests to verify RED**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Cli.Tests.ps1 -PassThru
```

Expected: FAIL because CLI and installer are absent.

- [ ] **Step 3: Implement the command dispatcher**

```powershell
[CmdletBinding()]
param(
    [Parameter(Position=0,Mandatory=$true)]
    [ValidateSet('start','status','stop')][string]$Action,
    [string]$Branch,
    [string]$Worktree,
    [string]$RepositoryRoot='D:\code',
    [string]$RuntimeRoot='D:\code\.runtime',
    [switch]$Json
)
Import-Module (Join-Path $PSScriptRoot 'Oakved.Runtime.psm1') -Force
switch ($Action) {
    'start' {
        if ([bool]$Branch -eq [bool]$Worktree) { throw 'Specify exactly one of -Branch or -Worktree.' }
        Start-OakvedRuntime -RepositoryRoot $RepositoryRoot -RuntimeRoot $RuntimeRoot -Branch $Branch -Worktree $Worktree
    }
    'status' { Get-OakvedRuntimeStatus -RuntimeRoot $RuntimeRoot -Json:$Json }
    'stop' { Stop-OakvedInstalledRuntime -RuntimeRoot $RuntimeRoot }
}
```

- [ ] **Step 4: Implement stable installation and ignore runtime state**

The installer accepts `-InstallRoot`, `-RepositoryRoot`, and test-only `-SkipMainWorktree`; copies the CLI and module atomically to `D:\code\.runtime\bin`; creates `state`, `logs`, `backups`, `locks`, and `cache`; and never overwrites runtime state. If `main` lacks a worktree and `-SkipMainWorktree` is absent, it creates only `D:\code\.worktrees\main-runtime` with `git worktree add ... main`; otherwise it reports the existing path. Add `/.runtime/` to `.gitignore`.

- [ ] **Step 5: Replace lifecycle instructions in `AGENTS.md`**

Add exact rules:

```markdown
# Branch-Aware Local Runtime

- For any request to start, stop, inspect, or provide commands for ERP admin, furniture storefront, Yudao backend, or their local database, use `D:\code\.runtime\bin\oakved.ps1`.
- Never infer a branch from the current directory and never call a worktree's legacy backend or Vite start command directly when the installed launcher exists.
- `start` must receive the exact user-requested `-Branch` or `-Worktree`; if it cannot resolve that target, stop and report the error rather than falling back to `D:\code`.
- Starting `main` uses: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Branch main`.
- The launcher owns database backup, migration validation/application, backend build, ERP admin, and furniture storefront startup. Do not bypass its migration gate.
- Use the launcher's `status` output as the source of truth for branch, commit, worktree, database, migration version, ports, and PIDs.
```

- [ ] **Step 6: Run CLI tests to verify GREEN and install locally**

Run:

```powershell
Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.Cli.Tests.ps1 -PassThru
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\runtime\install-oakved-runtime.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\code\.runtime\bin\oakved.ps1 status
```

Expected: Pester `FailedCount = 0`; installed files exist; status prints either the exact managed runtime or `No Oakved runtime is active` without inspecting arbitrary current-directory scripts.

- [ ] **Step 7: Commit Task 4**

```bash
git add .gitignore AGENTS.md scripts/runtime
git commit -m "feat: install branch-aware Oakved launcher"
```

---

### Task 5: Real database and two-worktree acceptance

**Files:**
- Create: `scripts/runtime/tests/Oakved.Runtime.Acceptance.ps1`
- Modify: `scripts/runtime/README.md`

**Interfaces:**
- Consumes: installed launcher, Docker MySQL/Redis/Nacos, `main` worktree, and `codex/agent-rag` worktree.
- Produces: verified runnable launcher across divergent branches and documented operational evidence.

- [ ] **Step 1: Write the acceptance verifier before running live startup**

```powershell
param(
    [Parameter(Mandatory=$true)][string]$ExpectedBranch,
    [string]$Launcher='D:\code\.runtime\bin\oakved.ps1'
)
$ErrorActionPreference='Stop'

$status = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Launcher status -Json |
    ConvertFrom-Json
if ($status.branch -ne $ExpectedBranch) { throw "Expected $ExpectedBranch, got $($status.branch)" }
if ($status.databaseVersion -ne $status.catalogVersion) { throw 'Database/catalog mismatch.' }
if (-not $status.backendHealthy -or -not $status.adminHealthy -or -not $status.storefrontHealthy) {
    throw 'One or more applications are unhealthy.'
}
foreach ($process in $status.processes) {
    if (-not $process.workingDirectory.StartsWith($status.worktree,[StringComparison]::OrdinalIgnoreCase)) {
        throw "Process $($process.name) escaped selected worktree."
    }
}
if ($status.database -notlike "oakved_$($status.runtimeId)*") { throw 'Database/runtime identifier mismatch.' }
if (@($status.processes | Where-Object { $_.healthy -ne $true }).Count -ne 0) {
    throw 'A recorded process failed provenance validation.'
}
```

- [ ] **Step 2: Run all unit tests**

Run:

```powershell
$result = Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.*.Tests.ps1 -PassThru
if ($result.FailedCount -ne 0) { exit 1 }
```

Expected: every worktree, migration, process, and CLI test passes.

- [ ] **Step 3: Verify real `main` startup and pending migration behavior**

Run:

```powershell
& D:\code\.runtime\bin\oakved.ps1 stop
& D:\code\.runtime\bin\oakved.ps1 start -Branch main
& .\scripts\runtime\tests\Oakved.Runtime.Acceptance.ps1 -ExpectedBranch main
```

Expected: main resolves to `main-runtime`; its branch database reaches that worktree's catalog version; backend, ERP, and storefront are healthy; no process working directory points to `D:\code` unless that exact path is the resolved main worktree.

- [ ] **Step 4: Verify a divergent feature worktree and branch isolation**

Run:

```powershell
& D:\code\.runtime\bin\oakved.ps1 stop
& D:\code\.runtime\bin\oakved.ps1 start -Branch codex/agent-rag
& .\scripts\runtime\tests\Oakved.Runtime.Acceptance.ps1 -ExpectedBranch codex/agent-rag
```

Expected: the runtime uses a different database name and catalog appropriate to `codex/agent-rag`, all source paths are under `D:\code`, and no main database downgrade or checksum rewrite occurs.

- [ ] **Step 5: Verify fail-closed scenarios without mutating published migrations**

Use temporary test catalogs and disposable databases to prove checksum mismatch, database-ahead state, backup failure, migration SQL failure, missing branch, dirty main, unknown port ownership, and stale PID manifests all return non-zero before any application process starts.

- [ ] **Step 6: Update operating documentation with verified commands and evidence**

Document the exact installed commands, branch database naming, startup output, backup location, recovery steps after migration failure, and how to interpret `status`. Include the verified main and feature branch/runtime IDs without embedding passwords.

- [ ] **Step 7: Commit Task 5**

```bash
git add scripts/runtime/tests/Oakved.Runtime.Acceptance.ps1 scripts/runtime/README.md
git commit -m "test: verify Oakved launcher across worktrees"
```

---

### Task 6: Final review and verification

**Files:**
- Verify all files changed by Tasks 1-5.

**Interfaces:**
- Consumes: complete launcher implementation and acceptance evidence.
- Produces: reviewed commits, installed stable launcher, clean tracked worktree, and a final handoff with exact commands.

- [ ] **Step 1: Run syntax, test, and hygiene gates**

```powershell
$errors = $null
[void][Management.Automation.Language.Parser]::ParseFile((Resolve-Path .\scripts\runtime\Oakved.Runtime.psm1),[ref]$null,[ref]$errors)
if ($errors.Count) { $errors | Format-List; exit 1 }
$result = Invoke-Pester .\scripts\runtime\tests\Oakved.Runtime.*.Tests.ps1 -PassThru
if ($result.FailedCount -ne 0) { exit 1 }
git diff --check HEAD~5..HEAD
git status --short
```

Expected: no parser errors, no failed tests, no whitespace errors, and only pre-existing unrelated untracked files outside the task scope.

- [ ] **Step 2: Obtain independent spec and code-quality review**

Review against `docs/superpowers/specs/2026-07-17-branch-aware-runtime-launcher-design.md`. Critical and Important findings must be fixed with a regression test and re-reviewed.

- [ ] **Step 3: Reinstall the reviewed launcher and run final status**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\runtime\install-oakved-runtime.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\code\.runtime\bin\oakved.ps1 status
```

Expected: installed hashes match repository source and status reports exact provenance without fallback behavior.

- [ ] **Step 4: Commit any review corrections**

```bash
git add .gitignore AGENTS.md scripts/runtime docs/superpowers
git commit -m "fix: close Oakved launcher review gaps"
```

Skip this commit when review requires no changes.
