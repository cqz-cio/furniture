param()

$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose-local-infra.yml"
$mysqlClientArgs = @("--protocol=TCP", "-h127.0.0.1", "-P3306", "-uroot", "-p123456", "ruoyi-vue-pro")
$tables = @(
  "member_tag",
  "member_level",
  "member_group",
  "member_trade_application"
)

foreach ($table in $tables) {
  Write-Host "Checking table: $table"
  docker compose -f $composeFile exec -T mysql mysql @mysqlClientArgs --execute "SHOW TABLES LIKE '$table';"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to check table: $table"
  }
}
