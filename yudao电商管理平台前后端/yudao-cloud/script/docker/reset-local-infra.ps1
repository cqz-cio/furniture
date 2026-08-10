param(
  [string] $BackupDirectory = (Join-Path $PSScriptRoot "backups"),
  [string] $Confirmation = ""
)

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "docker-compose-local-infra.yml"
New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
$backupPath = Join-Path $BackupDirectory ("ruoyi-vue-pro-{0}.sql" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

docker compose -f $composeFile exec -T mysql mysqldump -uroot -p123456 --single-transaction --routines --triggers ruoyi-vue-pro | Set-Content -Encoding UTF8 $backupPath
if ($LASTEXITCODE -ne 0) { throw "mysqldump failed; local data was not changed." }
$backup = Get-Item -LiteralPath $backupPath
if ($backup.Length -le 0) { throw "Backup is empty; local data was not changed." }
if (-not (Select-String -LiteralPath $backupPath -Pattern "CREATE TABLE" -Quiet)) { throw "Backup validation failed; local data was not changed." }

if (-not $Confirmation) { $Confirmation = Read-Host "Type RESET OAKVED LOCAL DATA to delete and rebuild local volumes" }
if ($Confirmation -cne "RESET OAKVED LOCAL DATA") { throw "Confirmation did not match. Backup kept at $backupPath; local data was not changed." }

docker compose -f $composeFile down -v
if ($LASTEXITCODE -ne 0) { throw "Failed to remove local volumes." }
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "start-local-infra.ps1")
if ($LASTEXITCODE -ne 0) { throw "Local infrastructure rebuild failed. Restore from $backupPath." }
Write-Host "Local volumes recreated. Start yudao-server to initialize the empty database with Flyway. Pre-reset backup: $backupPath"
