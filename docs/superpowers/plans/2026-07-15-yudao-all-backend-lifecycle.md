# Yudao One-Command Backend Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe root-level PowerShell commands that start and stop `yudao-server` and `ai-server` together.

**Architecture:** A root start wrapper performs shared preflight checks, optionally builds both artifacts with the repository JDK 17 helper, starts the monolith itself, and delegates AI startup to the existing lifecycle script. A root stop wrapper delegates to an enhanced service-filtered stop script so only the two intended recorded processes are stopped.

**Tech Stack:** PowerShell 5.1+, Java 17, Maven, Spring Boot Actuator, Docker, existing repository JDK 17 scripts.

## Global Constraints

- Work on `codex/agent-rag`; do not modify or merge `main`.
- Preserve the user's modified `yudao电商管理平台前后端/yudao-ui-admin-vue3/pnpm-lock.yaml` and all unrelated untracked files.
- Manage only `yudao-server` on port `48080` and `ai-server` on port `48090`.
- Default startup reuses existing JAR files; `-Build` explicitly rebuilds both services.
- Background processes must write logs and state under `yudao-cloud/.local-run/jdk17`.
- Shutdown must validate state, PID, repository-scoped JAR path, and process command line before stopping a process.
- Existing `start-yudao-backend.ps1` and the default service list in `Start-Jdk17Backend.ps1` remain unchanged.

---

### Task 1: Add service filtering to safe shutdown

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/Stop-Jdk17Backend.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/BackendLifecycle.Tests.ps1`

**Interfaces:**
- Consumes: existing JSON state records with `Service`, `Pid`, and `JarPath`.
- Produces: `Stop-Jdk17Backend.ps1 -RunDirectory <path> -Services @('yudao-server', 'ai-server')`; omitting `-Services` preserves the current all-records behavior.

- [ ] **Step 1: Write the failing service-filter test**

Append this isolated contract to `BackendLifecycle.Tests.ps1`:

```powershell
$filteredRunDirectory = Join-Path $env:TEMP "yudao-jdk17-filter-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Force -Path $filteredRunDirectory | Out-Null
try {
    @{
        Service = 'yudao-server'
        Pid = $PID
        JarPath = 'D:\not-this-repository\yudao-server.jar'
        StartedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $filteredRunDirectory 'yudao-server.json') -Encoding UTF8
    @{
        Service = 'ai-server'
        Pid = [int]::MaxValue
        JarPath = 'D:\not-this-repository\ai-server.jar'
        StartedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $filteredRunDirectory 'ai-server.json') -Encoding UTF8

    & $stopScript -RunDirectory $filteredRunDirectory -Services @('ai-server')

    if (-not (Test-Path -LiteralPath (Join-Path $filteredRunDirectory 'yudao-server.json'))) {
        throw 'Service-filtered shutdown removed an unselected service state record.'
    }
    if (Test-Path -LiteralPath (Join-Path $filteredRunDirectory 'ai-server.json')) {
        throw 'Service-filtered shutdown did not clean the selected stale state record.'
    }
} finally {
    Remove-Item -LiteralPath $filteredRunDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
```

- [ ] **Step 2: Run the lifecycle test and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/BackendLifecycle.Tests.ps1"
```

Expected: FAIL because `Stop-Jdk17Backend.ps1` does not accept a `-Services` parameter.

- [ ] **Step 3: Implement the optional filter**

Change the stop script parameter block and state-file selection to:

```powershell
[CmdletBinding()]
param(
    [string]$RunDirectory,
    [string[]]$Services = @()
)

$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($RunDirectory)) {
    $RunDirectory = Join-Path $cloudRoot '.local-run\jdk17'
}
$RunDirectory = [IO.Path]::GetFullPath($RunDirectory)

if (-not (Test-Path -LiteralPath $RunDirectory -PathType Container)) {
    Write-Output 'No recorded JDK 17 backend processes.'
    exit 0
}

$stateFiles = @(Get-ChildItem -LiteralPath $RunDirectory -Filter '*.json' -File)
if ($Services.Count -gt 0) {
    $selectedServices = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]$Services,
        [StringComparer]::OrdinalIgnoreCase
    )
    $stateFiles = @($stateFiles | Where-Object { $selectedServices.Contains($_.BaseName) })
}

if ($stateFiles.Count -eq 0) {
    Write-Output 'No matching recorded JDK 17 backend processes.'
    exit 0
}

foreach ($stateFile in $stateFiles) {
    $state = Get-Content -LiteralPath $stateFile.FullName -Raw | ConvertFrom-Json
    $processId = [int]$state.Pid
    $jarPath = [IO.Path]::GetFullPath([string]$state.JarPath)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) {
        Remove-Item -LiteralPath $stateFile.FullName -Force
        Write-Output "$($state.Service): stale PID record removed."
        continue
    }

    if (-not $jarPath.StartsWith($cloudRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Write-Warning "$($state.Service): PID $processId was not stopped because its recorded jar is outside this repository; state record retained."
        continue
    }

    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop
    if (-not $processInfo.CommandLine -or
        $processInfo.CommandLine.IndexOf($jarPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        Write-Warning "$($state.Service): PID $processId was not stopped because its command line does not match the recorded jar; state record retained."
        continue
    }

    Stop-Process -Id $processId -Force -ErrorAction Stop
    Wait-Process -Id $processId -Timeout 15 -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stateFile.FullName -Force
    Write-Output "$($state.Service) stopped: PID=$processId"
}
```

- [ ] **Step 4: Run the lifecycle contracts and verify GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/BackendLifecycle.Tests.ps1"
```

Expected: `Backend lifecycle contract passed.` and exit code `0`.

- [ ] **Step 5: Commit Task 1**

```powershell
git add -- "yudao电商管理平台前后端/yudao-cloud/script/jdk17/Stop-Jdk17Backend.ps1" "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/BackendLifecycle.Tests.ps1"
git commit -m "feat: filter recorded backend shutdown by service"
```

### Task 2: Add root one-command start and stop wrappers

**Files:**
- Create: `start-yudao-all-backend.ps1`
- Create: `stop-yudao-all-backend.ps1`
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1`

**Interfaces:**
- Consumes: `Resolve-Jdk17.ps1`, `Invoke-MavenJdk17.ps1`, `Start-Jdk17Backend.ps1`, and Task 1's filtered `Stop-Jdk17Backend.ps1`.
- Produces: `start-yudao-all-backend.ps1 [-Build] [-VerifyOnly] [-StartupTimeoutSeconds <1..600>]` and `stop-yudao-all-backend.ps1`.

- [ ] **Step 1: Write the failing root-launcher contract**

Create `AllBackendLauncher.Tests.ps1`:

```powershell
$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$workspace = (Resolve-Path (Join-Path $cloudRoot '..\..')).Path
$startScript = Join-Path $workspace 'start-yudao-all-backend.ps1'
$stopScript = Join-Path $workspace 'stop-yudao-all-backend.ps1'

foreach ($script in @($startScript, $stopScript)) {
    if (-not (Test-Path -LiteralPath $script -PathType Leaf)) {
        throw "Missing one-command backend script: $script"
    }
}

$verifyOutput = @(& $startScript -VerifyOnly 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "One-command launcher verification failed.`n$($verifyOutput -join [Environment]::NewLine)"
}
foreach ($expected in @('JAVA_HOME=', 'YUDao_SERVER_PORT=48080', 'AI_SERVER_PORT=48090', 'yudao-server.jar', 'ai-server')) {
    if (-not ($verifyOutput | Where-Object { $_.ToString().Contains($expected) })) {
        throw "Verification output is missing: $expected"
    }
}

$startContent = Get-Content -LiteralPath $startScript -Raw
if ($startContent -notmatch '/actuator/health' -or
    $startContent -notmatch 'Start-Jdk17Backend\.ps1' -or
    $startContent -notmatch 'Stop-Jdk17Backend\.ps1' -or
    $startContent -notmatch 'yudao-mysql-local' -or
    $startContent -notmatch 'yudao-redis-local' -or
    $startContent -notmatch 'yudao-nacos-local') {
    throw 'One-command launcher is missing health, delegation, rollback, or Docker preflight behavior.'
}

$stopContent = Get-Content -LiteralPath $stopScript -Raw
if ($stopContent -notmatch "'yudao-server', 'ai-server'" -or
    $stopContent -notmatch 'Stop-Jdk17Backend\.ps1') {
    throw 'One-command stop script must delegate a precise two-service shutdown.'
}

Write-Output 'One-command backend launcher contract passed.'
```

- [ ] **Step 2: Run the launcher contract and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1"
```

Expected: FAIL with `Missing one-command backend script`.

- [ ] **Step 3: Implement `start-yudao-all-backend.ps1`**

Implement the root script with these exact behaviors:

```powershell
[CmdletBinding()]
param(
    [switch]$Build,
    [switch]$VerifyOnly,
    [ValidateRange(1, 600)]
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$workspace = $PSScriptRoot
$yudaoRoot = Get-ChildItem -LiteralPath $workspace -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'yudao-cloud') -PathType Container } |
    Select-Object -First 1
if ($null -eq $yudaoRoot) { throw "Cannot find the Yudao project under $workspace." }

$cloudRoot = Join-Path $yudaoRoot.FullName 'yudao-cloud'
$scriptRoot = Join-Path $cloudRoot 'script\jdk17'
$resolver = Join-Path $scriptRoot 'Resolve-Jdk17.ps1'
$mavenRunner = Join-Path $scriptRoot 'Invoke-MavenJdk17.ps1'
$aiStarter = Join-Path $scriptRoot 'Start-Jdk17Backend.ps1'
$backendStopper = Join-Path $scriptRoot 'Stop-Jdk17Backend.ps1'
$runDirectory = Join-Path $cloudRoot '.local-run\jdk17'
$logDirectory = Join-Path $runDirectory 'logs'
$serverJar = Join-Path $cloudRoot 'yudao-server\target\yudao-server.jar'
$aiJar = Join-Path $cloudRoot 'yudao-module-ai\yudao-module-ai-server\target\yudao-module-ai-server.jar'

foreach ($required in @($resolver, $mavenRunner, $aiStarter, $backendStopper)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing required script: $required" }
}

$jdkHomeOutput = @(& $resolver)
if ($LASTEXITCODE -ne 0 -or $jdkHomeOutput.Count -ne 1) { throw 'Unable to resolve exactly one Java 17 home.' }
$jdkHome = [string]$jdkHomeOutput[0]
$javaExe = Join-Path $jdkHome 'bin\java.exe'

if ($VerifyOnly) {
    foreach ($jar in @($serverJar, $aiJar)) {
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
            throw "Artifact not found: $jar. Run without -VerifyOnly and add -Build."
        }
    }
    Write-Output "JAVA_HOME=$jdkHome"
    Write-Output 'YUDao_SERVER_PORT=48080'
    Write-Output 'AI_SERVER_PORT=48090'
    Write-Output "YUDao_SERVER_JAR=$serverJar"
    Write-Output "AI_SERVER_JAR=$aiJar"
    exit 0
}

function Test-PortOccupied([int]$Port) {
    return $Port -in [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
}

function Wait-Healthy([int]$Port, [Diagnostics.Process]$Process, [string]$Service, [string]$ErrorLog) {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) { throw "$Service exited during startup. Inspect $ErrorLog." }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 5
            if ($health.status -eq 'UP') { return }
        } catch {}
        Start-Sleep -Seconds 2
    }
    throw "$Service did not become healthy on port $Port. Inspect $ErrorLog."
}

foreach ($container in @('yudao-mysql-local', 'yudao-redis-local', 'yudao-nacos-local')) {
    $running = & docker inspect -f '{{.State.Running}}' $container 2>$null
    if ($LASTEXITCODE -ne 0 -or $running -ne 'true') { throw "Required Docker container '$container' is not running." }
}
foreach ($port in @(48080, 48090)) {
    if (Test-PortOccupied $port) { throw "Port $port is occupied; no backend process was started." }
}

if ($Build) {
    Push-Location $cloudRoot
    try {
        & $mavenRunner -MavenArgs @('-pl', 'yudao-server,yudao-module-ai/yudao-module-ai-server', '-am', 'package', '-DskipTests')
    } finally { Pop-Location }
}
foreach ($jar in @($serverJar, $aiJar)) {
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Artifact not found: $jar. Run again with -Build." }
}

New-Item -ItemType Directory -Force -Path $runDirectory, $logDirectory | Out-Null
$serverOut = Join-Path $logDirectory 'yudao-server.out.log'
$serverErr = Join-Path $logDirectory 'yudao-server.err.log'
$serverProcess = $null
try {
    $serverProcess = Start-Process -FilePath $javaExe `
        -ArgumentList "-jar `"$serverJar`" --spring.profiles.active=local" `
        -WorkingDirectory $cloudRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
    @{
        Service = 'yudao-server'; Pid = $serverProcess.Id; JarPath = $serverJar
        Port = 48080; StartedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDirectory 'yudao-server.json') -Encoding UTF8
    Wait-Healthy -Port 48080 -Process $serverProcess -Service 'yudao-server' -ErrorLog $serverErr

    & $aiStarter -Services @('ai-server') -SkipBuild `
        -StartupTimeoutSeconds $StartupTimeoutSeconds -RunDirectory $runDirectory

    $mainHealth = (Invoke-RestMethod -Uri 'http://127.0.0.1:48080/actuator/health' -TimeoutSec 5).status
    $aiHealth = (Invoke-RestMethod -Uri 'http://127.0.0.1:48090/actuator/health' -TimeoutSec 5).status
    Write-Output "All backends started: yudao-server=$mainHealth, ai-server=$aiHealth"
    Write-Output "Logs: $logDirectory"
} catch {
    & $backendStopper -RunDirectory $runDirectory -Services @('yudao-server', 'ai-server')
    throw
}
```

- [ ] **Step 4: Implement `stop-yudao-all-backend.ps1`**

Create the root stop wrapper:

```powershell
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspace = $PSScriptRoot
$yudaoRoot = Get-ChildItem -LiteralPath $workspace -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'yudao-cloud') -PathType Container } |
    Select-Object -First 1
if ($null -eq $yudaoRoot) { throw "Cannot find the Yudao project under $workspace." }

$cloudRoot = Join-Path $yudaoRoot.FullName 'yudao-cloud'
$stopScript = Join-Path $cloudRoot 'script\jdk17\Stop-Jdk17Backend.ps1'
$runDirectory = Join-Path $cloudRoot '.local-run\jdk17'
& $stopScript -RunDirectory $runDirectory -Services @('yudao-server', 'ai-server')
```

- [ ] **Step 5: Run launcher and lifecycle contracts and verify GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/BackendLifecycle.Tests.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/RootBackendLauncher.Tests.ps1"
```

Expected: all three contracts print their `passed` message and exit `0`.

- [ ] **Step 6: Commit Task 2**

```powershell
git add -- "start-yudao-all-backend.ps1" "stop-yudao-all-backend.ps1" "yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1"
git commit -m "feat: add one-command local backend lifecycle"
```

### Task 3: Run live lifecycle acceptance and publish

**Files:**
- Verify only; no planned file changes.

**Interfaces:**
- Consumes: Task 2's root start and stop commands.
- Produces: evidence that both health endpoints become `UP`, both ports are released after shutdown, and the branch is pushed without unrelated files.

- [ ] **Step 1: Confirm Docker dependencies are running and target ports are free**

Run:

```powershell
docker inspect -f '{{.State.Running}}' yudao-mysql-local yudao-redis-local yudao-nacos-local
Get-NetTCPConnection -LocalPort 48080,48090 -State Listen -ErrorAction SilentlyContinue
```

Expected: Docker prints `true` three times; no listener rows are returned.

- [ ] **Step 2: Execute the one-command startup**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\start-yudao-all-backend.ps1"
```

Expected: `All backends started: yudao-server=UP, ai-server=UP` and a log directory path.

- [ ] **Step 3: Independently verify both health endpoints**

Run:

```powershell
(Invoke-RestMethod -Uri 'http://127.0.0.1:48080/actuator/health' -TimeoutSec 10).status
(Invoke-RestMethod -Uri 'http://127.0.0.1:48090/actuator/health' -TimeoutSec 10).status
```

Expected: `UP` twice.

- [ ] **Step 4: Execute the one-command shutdown and verify both ports are released**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\stop-yudao-all-backend.ps1"
Get-NetTCPConnection -LocalPort 48080,48090 -State Listen -ErrorAction SilentlyContinue
```

Expected: both services report stopped; no listener rows are returned.

- [ ] **Step 5: Audit scope and push the working branch**

Run:

```powershell
git diff --check
git status --short --branch
git log --oneline origin/codex/agent-rag..HEAD
git push origin codex/agent-rag
```

Expected: only the user's pre-existing `pnpm-lock.yaml` modification and unrelated untracked files remain; implementation commits push to `origin/codex/agent-rag`.
