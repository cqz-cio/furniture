[CmdletBinding()]
param(
    [string]$ContainerName = 'yudao-mysql-local',
    [string]$DatabaseName = 'ruoyi-vue-pro',
    [string]$MySqlUser = 'root',
    [switch]$SkipBackup
)

$ErrorActionPreference = 'Stop'

$password = $env:YUDAO_MYSQL_ROOT_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'YUDAO_MYSQL_ROOT_PASSWORD is required.'
}

$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'sql\mysql\ai-module-enable.sql'
if (-not (Test-Path -LiteralPath $migrationPath -PathType Leaf)) {
    throw "AI migration file not found: $migrationPath"
}

if (-not $SkipBackup) {
    & (Join-Path $PSScriptRoot 'Backup-AiDatabase.ps1') `
        -ContainerName $ContainerName -DatabaseName $DatabaseName -MySqlUser $MySqlUser
    if ($LASTEXITCODE -ne 0) {
        throw 'Database backup failed; migration was not applied.'
    }
}

$containerPath = '/tmp/yudao-ai-module-enable.sql'
try {
    & docker cp $migrationPath "${ContainerName}:$containerPath" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to copy the AI migration into the MySQL container.'
    }

    & docker exec -e "MYSQL_PWD=$password" $ContainerName mysql `
        "-u$MySqlUser" --default-character-set=utf8mb4 $DatabaseName `
        -e "source $containerPath"
    if ($LASTEXITCODE -ne 0) {
        throw 'AI database migration failed.'
    }
} finally {
    & docker exec $ContainerName rm -f $containerPath 2>$null | Out-Null
}

Write-Output 'AI database migration applied successfully.'
