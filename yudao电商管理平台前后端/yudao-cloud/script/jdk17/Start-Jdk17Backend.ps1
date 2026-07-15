[CmdletBinding()]
param(
    [ValidateSet('gateway', 'system-server', 'product-server', 'ai-server')]
    [string[]]$Services = @('gateway', 'system-server', 'product-server', 'ai-server'),
    [switch]$SkipBuild,
    [ValidateRange(1, 600)]
    [int]$StartupTimeoutSeconds = 180,
    [string]$RunDirectory
)

$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($RunDirectory)) {
    $RunDirectory = Join-Path $cloudRoot '.local-run\jdk17'
}
$RunDirectory = [IO.Path]::GetFullPath($RunDirectory)
$logDirectory = Join-Path $RunDirectory 'logs'

$serviceDefinitions = [ordered]@{
    'system-server' = @{
        Port = 48081; NacosName = 'system-server'
        Project = 'yudao-module-system/yudao-module-system-server'
        Jar = 'yudao-module-system/yudao-module-system-server/target/yudao-module-system-server-exec.jar'
    }
    'product-server' = @{
        Port = 48100; NacosName = 'product-server'
        Project = 'yudao-module-mall/yudao-module-product-server'
        Jar = 'yudao-module-mall/yudao-module-product-server/target/yudao-module-product-server-exec.jar'
    }
    'ai-server' = @{
        Port = 48090; NacosName = 'ai-server'
        Project = 'yudao-module-ai/yudao-module-ai-server'
        Jar = 'yudao-module-ai/yudao-module-ai-server/target/yudao-module-ai-server.jar'
    }
    'gateway' = @{
        Port = 48080; NacosName = 'gateway-server'
        Project = 'yudao-gateway'
        Jar = 'yudao-gateway/target/yudao-gateway.jar'
    }
}

function Test-PortOccupied {
    param([int]$Port)
    return $Port -in [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
}

function Test-HttpHealth {
    param([int]$Port)
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 5
        return $response.status -eq 'UP'
    } catch {
        return $false
    }
}

function Get-NacosHosts {
    param([string]$ServiceName)
    $uri = 'http://127.0.0.1:8848/nacos/v1/ns/instance/list' +
        "?serviceName=$([uri]::EscapeDataString($ServiceName))&groupName=DEFAULT_GROUP&namespaceId=dev"
    $response = Invoke-RestMethod -Uri $uri -TimeoutSec 5
    return @($response.hosts)
}

$selectedServices = @($serviceDefinitions.Keys | Where-Object { $_ -in $Services })
if ($selectedServices.Count -eq 0) {
    throw 'At least one backend service must be selected.'
}

foreach ($service in $selectedServices) {
    $definition = $serviceDefinitions[$service]
    if (Test-PortOccupied -Port $definition.Port) {
        throw "Port $($definition.Port) is occupied; refusing to start $service."
    }

    $statePath = Join-Path $RunDirectory "$service.json"
    if (Test-Path -LiteralPath $statePath) {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if (Get-Process -Id $state.Pid -ErrorAction SilentlyContinue) {
            throw "$service already has a recorded running process (PID $($state.Pid))."
        }
        Remove-Item -LiteralPath $statePath -Force
    }
}

foreach ($container in @('yudao-mysql-local', 'yudao-redis-local', 'yudao-nacos-local')) {
    $running = & docker inspect -f '{{.State.Running}}' $container 2>$null
    if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
        throw "Required Docker container '$container' is not running."
    }
}

foreach ($service in $selectedServices) {
    $hosts = @(Get-NacosHosts -ServiceName $serviceDefinitions[$service].NacosName)
    if ($hosts | Where-Object { $_.healthy -eq $true -and $_.enabled -eq $true }) {
        throw "Nacos already has a healthy $($serviceDefinitions[$service].NacosName) instance."
    }
}

if (-not $SkipBuild) {
    $projects = ($selectedServices | ForEach-Object { $serviceDefinitions[$_].Project }) -join ','
    & (Join-Path $PSScriptRoot 'Invoke-MavenJdk17.ps1') `
        -MavenArgs @('-pl', $projects, '-am', 'package', '-DskipTests')
}

$jdkHome = @(& (Join-Path $PSScriptRoot 'Resolve-Jdk17.ps1'))
if ($LASTEXITCODE -ne 0 -or $jdkHome.Count -ne 1) {
    throw 'Unable to resolve exactly one Java 17 home.'
}
$javaExe = Join-Path ([string]$jdkHome[0]) 'bin\java.exe'

New-Item -ItemType Directory -Force -Path $RunDirectory, $logDirectory | Out-Null

foreach ($service in $selectedServices) {
    $definition = $serviceDefinitions[$service]
    $jarPath = [IO.Path]::GetFullPath((Join-Path $cloudRoot $definition.Jar))
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Artifact not found for ${service}: $jarPath"
    }

    $stdoutPath = Join-Path $logDirectory "$service.out.log"
    $stderrPath = Join-Path $logDirectory "$service.err.log"
    $arguments = "-jar `"$jarPath`" --spring.profiles.active=local"
    $process = Start-Process -FilePath $javaExe -ArgumentList $arguments `
        -WorkingDirectory $cloudRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $statePath = Join-Path $RunDirectory "$service.json"
    @{
        Service = $service
        Pid = $process.Id
        JarPath = $jarPath
        Port = $definition.Port
        NacosName = $definition.NacosName
        StartedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        if ($process.HasExited) {
            Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
            throw "$service exited during startup. Inspect $stderrPath and $stdoutPath."
        }
        if (Test-HttpHealth -Port $definition.Port) {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        throw "$service did not report Actuator health UP on port $($definition.Port) within $StartupTimeoutSeconds seconds. Inspect $stderrPath and $stdoutPath."
    }

    $registered = $false
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $hosts = @(Get-NacosHosts -ServiceName $definition.NacosName)
        if ($hosts | Where-Object { $_.healthy -eq $true -and [int]$_.port -eq $definition.Port }) {
            $registered = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $registered) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        throw "$service reported Actuator health UP but did not register in Nacos."
    }

    Write-Output "$service started: PID=$($process.Id), port=$($definition.Port)"
}
