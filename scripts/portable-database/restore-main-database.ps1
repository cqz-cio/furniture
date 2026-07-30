[CmdletBinding()]
param(
    [string]$RepositoryRoot,
    [string]$RuntimeRoot,
    [string]$SnapshotPath,
    [AllowEmptyString()][string]$SnapshotPassword,
    [AllowEmptyString()][string]$MySqlRootPassword,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
    $RuntimeRoot = Join-Path $RepositoryRoot '.runtime'
}
if ([string]::IsNullOrWhiteSpace($SnapshotPath)) {
    $SnapshotPath = Join-Path $RepositoryRoot 'database-snapshots\oakved-main-latest.oakveddb'
}
if ([string]::IsNullOrWhiteSpace($MySqlRootPassword)) {
    $MySqlRootPassword = if ($env:OAKVED_MYSQL_ROOT_PASSWORD) {
        $env:OAKVED_MYSQL_ROOT_PASSWORD
    }
    else {
        '123456'
    }
}
if ([string]::IsNullOrWhiteSpace($SnapshotPassword)) {
    $SnapshotPassword = $env:OAKVED_SNAPSHOT_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($SnapshotPassword)) {
    $securePassword = Read-Host 'Snapshot password' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $SnapshotPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$portableModule = Join-Path $PSScriptRoot 'Oakved.PortableDatabase.psm1'
Import-Module $portableModule -Force

$launcher = Join-Path $RuntimeRoot 'bin\oakved.ps1'
$manifestPath = Join-Path $RuntimeRoot 'runtime.json'
$metadataPath = [IO.Path]::ChangeExtension($SnapshotPath, '.json')
foreach ($requiredPath in @($launcher, $manifestPath, $SnapshotPath, $metadataPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required file is missing: $requiredPath"
    }
}

$statusJson = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher status -Json
if ($LASTEXITCODE -ne 0) {
    throw 'Oakved runtime status check failed.'
}
$status = $statusJson | Out-String | ConvertFrom-Json
if (-not [bool]$status.Healthy -or [string]$status.Branch -cne 'main' -or [bool]$status.UpdateAvailable) {
    throw 'Restore requires a healthy, current main runtime with no pending branch update.'
}

$database = [string]$status.Database
if ($database -notmatch '^oakved_main_[0-9a-f]{8}$') {
    throw "Refusing to replace unexpected database name: $database"
}

$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
$snapshotHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $SnapshotPath).Hash
if ($snapshotHash -cne [string]$metadata.SnapshotSha256) {
    throw 'Encrypted snapshot hash does not match its metadata.'
}

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('oakved-restore-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
$gzipPath = Join-Path $temporaryRoot 'database.sql.gz'
$sqlPath = Join-Path $temporaryRoot 'database.sql'
$containerImport = '/tmp/oakved-restore-' + [guid]::NewGuid().ToString('N') + '.sql'
$containerBackup = '/tmp/oakved-before-restore-' + [guid]::NewGuid().ToString('N') + '.sql'
$stopped = $false
$started = $false
$backupPath = $null

try {
    Unprotect-OakvedPortableFile -InputPath $SnapshotPath -OutputPath $gzipPath -Password $SnapshotPassword
    Expand-OakvedPortableFile -InputPath $gzipPath -OutputPath $sqlPath

    $sqlHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sqlPath).Hash
    if ($sqlHash -cne [string]$metadata.SqlSha256 -or
        -not (Select-String -LiteralPath $sqlPath -Pattern 'CREATE TABLE' -Quiet)) {
        throw 'Decrypted SQL dump validation failed.'
    }

    if (-not $Force) {
        $confirmation = Read-Host "Type RESTORE OAKVED MAIN DATABASE to replace $database"
        if ($confirmation -cne 'RESTORE OAKVED MAIN DATABASE') {
            throw 'Restore cancelled.'
        }
    }

    $backupDirectory = Join-Path $RuntimeRoot 'backups\portable-restore'
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $backupPath = Join-Path $backupDirectory ($database + '-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '.sql')

    & docker exec -e "MYSQL_PWD=$MySqlRootPassword" yudao-mysql-local mysqldump `
        --default-character-set=utf8mb4 --single-transaction --routines --triggers --events --hex-blob `
        --set-gtid-purged=OFF "--result-file=$containerBackup" -uroot $database
    if ($LASTEXITCODE -ne 0) {
        throw 'Pre-restore database backup failed.'
    }
    & docker cp "yudao-mysql-local:$containerBackup" $backupPath
    if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $backupPath).Length -le 0) {
        throw 'Pre-restore database backup could not be copied.'
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher stop | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not stop the Oakved runtime before restore.'
    }
    $stopped = $true

    $replaceSql = "DROP DATABASE IF EXISTS ``$database``; CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    & docker exec -e "MYSQL_PWD=$MySqlRootPassword" yudao-mysql-local mysql `
        --default-character-set=utf8mb4 -uroot -e $replaceSql
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not recreate the target database.'
    }

    & docker cp $sqlPath "yudao-mysql-local:$containerImport"
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not copy the SQL dump into MySQL.'
    }
    & docker exec -e "MYSQL_PWD=$MySqlRootPassword" yudao-mysql-local mysql `
        --default-character-set=utf8mb4 -uroot $database -e "source $containerImport"
    if ($LASTEXITCODE -ne 0) {
        throw 'Snapshot import failed.'
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher start -Branch main | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Snapshot imported, but the main runtime did not restart successfully.'
    }
    $started = $true

    [pscustomobject]@{
        RestoredDatabase = $database
        SnapshotPath = $SnapshotPath
        PreviousDatabaseBackup = $backupPath
        RuntimeRestarted = $true
    } | ConvertTo-Json
}
finally {
    & docker exec yudao-mysql-local rm -f $containerImport $containerBackup 2>$null
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
    if ($stopped -and -not $started) {
        Write-Warning "The runtime is stopped because restore did not finish. Pre-restore backup: $backupPath"
    }
}
