[CmdletBinding()]
param(
    [string]$RepositoryRoot,
    [string]$RuntimeRoot,
    [string]$SnapshotPath,
    [AllowEmptyString()][string]$SnapshotPassword,
    [AllowEmptyString()][string]$MySqlRootPassword,
    [switch]$GeneratePassword
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

$portableModule = Join-Path $PSScriptRoot 'Oakved.PortableDatabase.psm1'
Import-Module $portableModule -Force

$launcher = Join-Path $RuntimeRoot 'bin\oakved.ps1'
$manifestPath = Join-Path $RuntimeRoot 'runtime.json'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "Oakved launcher is not installed at $launcher."
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'Start the main runtime before exporting the database.'
}

$statusJson = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher status -Json
if ($LASTEXITCODE -ne 0) {
    throw 'Oakved runtime status check failed.'
}
$status = $statusJson | Out-String | ConvertFrom-Json
if (-not [bool]$status.Healthy -or [string]$status.Branch -cne 'main' -or [bool]$status.UpdateAvailable) {
    throw 'Export requires a healthy, current main runtime with no pending branch update.'
}

$database = [string]$status.Database
if ($database -notmatch '^oakved_main_[0-9a-f]{8}$') {
    throw "Refusing to export unexpected database name: $database"
}

if ([string]::IsNullOrWhiteSpace($SnapshotPassword)) {
    $SnapshotPassword = $env:OAKVED_SNAPSHOT_PASSWORD
}
$generatedPassword = $false
if ([string]::IsNullOrWhiteSpace($SnapshotPassword) -and $GeneratePassword) {
    $SnapshotPassword = New-OakvedPortablePassword
    $generatedPassword = $true
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
if ($SnapshotPassword.Length -lt 20) {
    throw 'Snapshot password must contain at least 20 characters.'
}

$snapshotDirectory = Split-Path -Parent $SnapshotPath
New-Item -ItemType Directory -Path $snapshotDirectory -Force | Out-Null

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('oakved-portable-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
$sqlPath = Join-Path $temporaryRoot 'database.sql'
$gzipPath = Join-Path $temporaryRoot 'database.sql.gz'
$containerSql = '/tmp/oakved-portable-' + [guid]::NewGuid().ToString('N') + '.sql'
$metadataPath = [IO.Path]::ChangeExtension($SnapshotPath, '.json')

try {
    & docker exec -e "MYSQL_PWD=$MySqlRootPassword" yudao-mysql-local mysqldump `
        --default-character-set=utf8mb4 --single-transaction --routines --triggers --events --hex-blob `
        --set-gtid-purged=OFF "--result-file=$containerSql" -uroot $database
    if ($LASTEXITCODE -ne 0) {
        throw 'mysqldump failed.'
    }

    & docker cp "yudao-mysql-local:$containerSql" $sqlPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not copy the database dump from MySQL.'
    }

    $sqlFile = Get-Item -LiteralPath $sqlPath
    if ($sqlFile.Length -le 0 -or -not (Select-String -LiteralPath $sqlPath -Pattern 'CREATE TABLE' -Quiet)) {
        throw 'Database dump validation failed.'
    }

    Compress-OakvedPortableFile -InputPath $sqlPath -OutputPath $gzipPath
    Protect-OakvedPortableFile -InputPath $gzipPath -OutputPath $SnapshotPath -Password $SnapshotPassword

    $metadata = [ordered]@{
        Format = 'OAKVEDDB1'
        Database = $database
        Branch = [string]$status.Branch
        RuntimeCommit = [string]$status.Commit
        CatalogVersion = [string]$status.CatalogVersion
        ExportedAtUtc = [DateTime]::UtcNow.ToString('o')
        RawBytes = $sqlFile.Length
        SnapshotBytes = (Get-Item -LiteralPath $SnapshotPath).Length
        SqlSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $sqlPath).Hash
        SnapshotSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $SnapshotPath).Hash
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8

    $result = [ordered]@{
        SnapshotPath = $SnapshotPath
        MetadataPath = $metadataPath
        Database = $database
        RawBytes = $metadata.RawBytes
        SnapshotBytes = $metadata.SnapshotBytes
        SnapshotSha256 = $metadata.SnapshotSha256
    }
    if ($generatedPassword) {
        $result.SnapshotPassword = $SnapshotPassword
    }
    [pscustomobject]$result | ConvertTo-Json
}
finally {
    & docker exec yudao-mysql-local rm -f $containerSql 2>$null
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
