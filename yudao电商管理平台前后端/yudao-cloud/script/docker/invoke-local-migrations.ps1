param(
  [string] $ComposeFile = (Join-Path $PSScriptRoot "docker-compose-local-infra.yml"),
  [string] $Database = "ruoyi-vue-pro",
  [string] $RootPassword = "123456"
)

$ErrorActionPreference = "Stop"
$migrationDir = Resolve-Path (Join-Path $PSScriptRoot "..\..\sql\mysql\migrations")
$mysqlArgs = @("--default-character-set=utf8mb4", "-uroot", "-p$RootPassword", "-N", "-B", $Database)

function Invoke-MySql([string] $Sql) {
  $output = & docker compose -f $ComposeFile exec -T mysql mysql @mysqlArgs -e $Sql
  if ($LASTEXITCODE -ne 0) { throw "MySQL command failed." }
  return ($output | Select-Object -Last 1)
}

function Get-NormalizedSha256([string] $Path) {
  # Get-FileHash is intentionally not used directly because CRLF and LF checkouts must have the same migration checksum.
  $text = [IO.File]::ReadAllText($Path).Replace("`r`n", "`n").TrimEnd() + "`n"
  $bytes = [Text.Encoding]::UTF8.GetBytes($text)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant() }
  finally { $sha.Dispose() }
}

$ledgerSql = @"
CREATE TABLE IF NOT EXISTS schema_migrations (
  version varchar(16) NOT NULL,
  description varchar(255) NOT NULL,
  script_name varchar(255) NOT NULL,
  checksum_sha256 char(64) NOT NULL,
  installed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version),
  UNIQUE KEY uk_schema_migrations_script_name (script_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@
Invoke-MySql $ledgerSql | Out-Null

$lock = Invoke-MySql "SELECT GET_LOCK('oakved_schema_migrations',30);"
if ($lock -ne "1") { throw "Could not acquire Oakved migration lock." }

$applied = 0
try {
  $migrations = @(Get-ChildItem -LiteralPath $migrationDir -Filter "V*.sql" | Sort-Object Name)
  for ($index = 0; $index -lt $migrations.Count; $index++) {
    $file = $migrations[$index]
    if ($file.Name -notmatch '^V(\d{3})__([a-z0-9_]+)\.sql$') { throw "Invalid migration name: $($file.Name)" }
    $version = $Matches[1]
    $expected = ($index + 1).ToString("000")
    if ($version -ne $expected) { throw "Migration sequence gap: expected V$expected, found V$version" }
    $description = $Matches[2].Replace("_", " ")
    $checksum = Get-NormalizedSha256 $file.FullName
    $stored = Invoke-MySql "SELECT checksum_sha256 FROM schema_migrations WHERE version='$version';"
    if ($stored) {
      if ($stored -ne $checksum) { throw "Checksum mismatch for $($file.Name). Published migrations are immutable." }
      Write-Host "Skipping applied migration $($file.Name)"
      continue
    }

    $containerPath = "/tmp/$($file.Name)"
    docker compose -f $ComposeFile cp $file.FullName "mysql:$containerPath"
    if ($LASTEXITCODE -ne 0) { throw "Failed to copy migration $($file.Name)." }
    docker compose -f $ComposeFile exec -T mysql mysql @mysqlArgs --execute "source $containerPath"
    if ($LASTEXITCODE -ne 0) { throw "Migration failed: $($file.Name)" }
    $safeDescription = $description.Replace("'", "''")
    Invoke-MySql "INSERT INTO schema_migrations(version,description,script_name,checksum_sha256) VALUES('$version','$safeDescription','$($file.Name)','$checksum');" | Out-Null
    $applied++
    Write-Host "Applied migration $($file.Name)"
  }
} finally {
  Invoke-MySql "SELECT RELEASE_LOCK('oakved_schema_migrations');" | Out-Null
}

Write-Host "Oakved database migrations complete: applied=$applied total=$($migrations.Count)"
