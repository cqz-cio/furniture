[CmdletBinding()]
param(
    [string]$ContainerName = 'yudao-mysql-local',
    [string]$DatabaseName = 'ruoyi-vue-pro',
    [string]$MySqlUser = 'root'
)

$ErrorActionPreference = 'Stop'

$password = $env:YUDAO_MYSQL_ROOT_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'YUDAO_MYSQL_ROOT_PASSWORD is required.'
}

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backupDirectory = Join-Path $cloudRoot '.local-backups\mysql'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$fileName = "$DatabaseName-$timestamp.sql"
$containerPath = "/tmp/$fileName"
$backupPath = Join-Path $backupDirectory $fileName

New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
if (Test-Path -LiteralPath $backupPath) {
    throw "Refusing to overwrite an existing backup: $backupPath"
}

$running = & docker inspect -f '{{.State.Running}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
    throw "MySQL container '$ContainerName' is not running."
}

try {
    & docker exec -e "MYSQL_PWD=$password" $ContainerName mysqldump `
        "-u$MySqlUser" --single-transaction --routines --triggers --events `
        --default-character-set=utf8mb4 "--result-file=$containerPath" $DatabaseName
    if ($LASTEXITCODE -ne 0) {
        throw 'mysqldump failed.'
    }

    & docker cp "${ContainerName}:$containerPath" $backupPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to copy the database backup out of the container.'
    }
} finally {
    & docker exec $ContainerName rm -f $containerPath 2>$null | Out-Null
}

$backup = Get-Item -LiteralPath $backupPath
if ($backup.Length -le 0 -or -not (Select-String -LiteralPath $backupPath -SimpleMatch 'CREATE TABLE' -Quiet)) {
    Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
    throw 'Backup validation failed: output is empty or contains no CREATE TABLE statement.'
}

Write-Output "Backup: $($backup.FullName)"
Write-Output "Bytes: $($backup.Length)"
