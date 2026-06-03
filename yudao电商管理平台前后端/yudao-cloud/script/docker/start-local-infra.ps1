param(
  [switch] $Recreate
)

$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose-local-infra.yml"

if (-not (Test-Path -LiteralPath $composeFile)) {
  throw "Cannot find compose file: $composeFile"
}

if ($Recreate) {
  docker compose -f $composeFile down -v
}

docker compose -f $composeFile up -d
docker compose -f $composeFile ps

Write-Host ""
Write-Host "Yudao local infrastructure:"
Write-Host "  MySQL: 127.0.0.1:3306"
Write-Host "  Database: ruoyi-vue-pro"
Write-Host "  User: root"
Write-Host "  Password: 123456"
Write-Host "  Redis: 127.0.0.1:6379"
Write-Host ""
Write-Host "Run YudaoServerApplication after both containers are healthy."
