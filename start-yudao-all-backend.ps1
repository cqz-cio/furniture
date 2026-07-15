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
if ($null -eq $yudaoRoot) {
    throw "Cannot find the Yudao project under $workspace."
}

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

foreach ($requiredScript in @($resolver, $mavenRunner, $aiStarter, $backendStopper)) {
    if (-not (Test-Path -LiteralPath $requiredScript -PathType Leaf)) {
        throw "Missing required script: $requiredScript"
    }
}

$jdkHomeOutput = @(& $resolver)
if ($LASTEXITCODE -ne 0 -or $jdkHomeOutput.Count -ne 1) {
    throw 'Unable to resolve exactly one Java 17 home.'
}
$jdkHome = [string]$jdkHomeOutput[0]
$javaExe = Join-Path $jdkHome 'bin\java.exe'

if ($VerifyOnly) {
    foreach ($jar in @($serverJar, $aiJar)) {
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
            throw "Artifact not found: $jar. Run without -VerifyOnly and add -Build."
        }
    }
    Write-Output "JAVA_HOME=$jdkHome"
    Write-Output 'YUDAO_SERVER_PORT=48080'
    Write-Output 'AI_SERVER_PORT=48090'
    Write-Output "YUDAO_SERVER_JAR=$serverJar"
    Write-Output "AI_SERVER_JAR=$aiJar"
    exit 0
}

function Test-PortOccupied {
    param([int]$Port)
    return $Port -in [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
}

function Wait-Healthy {
    param(
        [int]$Port,
        [Diagnostics.Process]$Process,
        [string]$Service,
        [string]$ErrorLog
    )

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "$Service exited during startup. Inspect $ErrorLog."
        }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 5
            if ($health.status -eq 'UP') {
                return
            }
        } catch {
            # The service can reject health requests while Spring is still starting.
        }
        Start-Sleep -Seconds 2
    }
    throw "$Service did not become healthy on port $Port. Inspect $ErrorLog."
}

foreach ($container in @('yudao-mysql-local', 'yudao-redis-local', 'yudao-nacos-local')) {
    $running = & docker inspect -f '{{.State.Running}}' $container 2>$null
    if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
        throw "Required Docker container '$container' is not running."
    }
}
foreach ($port in @(48080, 48090)) {
    if (Test-PortOccupied -Port $port) {
        throw "Port $port is occupied; no backend process was started."
    }
}

if ($Build) {
    Push-Location $cloudRoot
    try {
        & $mavenRunner -MavenArgs @(
            '-pl',
            'yudao-server,yudao-module-ai/yudao-module-ai-server',
            '-am',
            'package',
            '-DskipTests'
        )
    } finally {
        Pop-Location
    }
}
foreach ($jar in @($serverJar, $aiJar)) {
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Artifact not found: $jar. Run again with -Build."
    }
}

New-Item -ItemType Directory -Force -Path $runDirectory, $logDirectory | Out-Null
$serverOut = Join-Path $logDirectory 'yudao-server.out.log'
$serverErr = Join-Path $logDirectory 'yudao-server.err.log'

try {
    $serverProcess = Start-Process -FilePath $javaExe `
        -ArgumentList "-jar `"$serverJar`" --spring.profiles.active=local" `
        -WorkingDirectory $cloudRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
    @{
        Service = 'yudao-server'
        Pid = $serverProcess.Id
        JarPath = $serverJar
        Port = 48080
        StartedAt = (Get-Date).ToString('o')
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
