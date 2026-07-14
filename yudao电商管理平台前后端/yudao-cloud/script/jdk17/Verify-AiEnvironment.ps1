[CmdletBinding()]
param(
    [ValidateSet('gateway', 'system-server', 'product-server', 'ai-server')]
    [string[]]$Services = @('gateway', 'system-server', 'product-server', 'ai-server'),
    [string]$RunDirectory
)

$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($RunDirectory)) {
    $RunDirectory = Join-Path $cloudRoot '.local-run\jdk17'
}

$definitions = @{
    'gateway' = @{ Port = 48080; NacosName = 'gateway-server' }
    'system-server' = @{ Port = 48081; NacosName = 'system-server' }
    'product-server' = @{ Port = 48100; NacosName = 'product-server' }
    'ai-server' = @{ Port = 48090; NacosName = 'ai-server' }
}

function Test-TcpPort {
    param([int]$Port)
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(2000) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

foreach ($container in @('yudao-mysql-local', 'yudao-redis-local', 'yudao-nacos-local')) {
    $running = & docker inspect -f '{{.State.Running}}' $container 2>$null
    if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
        throw "Required Docker container '$container' is not running."
    }
}

if (-not [string]::IsNullOrWhiteSpace($env:YUDAO_MYSQL_ROOT_PASSWORD)) {
    & (Join-Path $PSScriptRoot 'tests\AiMigration.Tests.ps1')
} else {
    Write-Warning 'YUDAO_MYSQL_ROOT_PASSWORD is not set; database contract verification was skipped.'
}

foreach ($service in $Services) {
    $definition = $definitions[$service]
    $statePath = Join-Path $RunDirectory "$service.json"
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        throw "$service has no launcher state file."
    }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if (-not (Get-Process -Id $state.Pid -ErrorAction SilentlyContinue)) {
        throw "$service recorded PID $($state.Pid) is not running."
    }

    if (-not (Test-TcpPort -Port $definition.Port)) {
        throw "$service is running but port $($definition.Port) is not accepting connections."
    }

    $uri = 'http://127.0.0.1:8848/nacos/v1/ns/instance/list' +
        "?serviceName=$($definition.NacosName)&groupName=DEFAULT_GROUP&namespaceId=dev"
    $hosts = @((Invoke-RestMethod -Uri $uri -TimeoutSec 5).hosts)
    if (-not ($hosts | Where-Object { $_.healthy -eq $true -and [int]$_.port -eq $definition.Port })) {
        throw "$service is healthy locally but missing from Nacos."
    }
    Write-Output "$service verified: PID=$($state.Pid), port=$($definition.Port)"
}

Write-Output 'AI environment verification passed.'
