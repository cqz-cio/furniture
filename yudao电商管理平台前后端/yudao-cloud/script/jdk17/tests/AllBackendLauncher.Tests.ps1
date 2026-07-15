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
foreach ($expected in @('JAVA_HOME=', 'YUDAO_SERVER_PORT=48080', 'AI_SERVER_PORT=48090', 'yudao-server.jar', 'ai-server')) {
    if (-not ($verifyOutput | Where-Object { $_.ToString().Contains($expected) })) {
        throw "Verification output is missing: $expected"
    }
}

$startContent = Get-Content -LiteralPath $startScript -Raw
if ($startContent -notmatch '/actuator/health' -or
    $startContent -notmatch 'Start-Jdk17Backend\.ps1' -or
    $startContent -notmatch 'Stop-Jdk17Backend\.ps1' -or
    $startContent -notmatch 'Starting yudao-server' -or
    $startContent -notmatch 'Starting ai-server' -or
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
