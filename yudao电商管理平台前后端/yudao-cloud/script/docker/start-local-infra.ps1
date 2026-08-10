$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose-local-infra.yml"
$mysqlContainerName = "yudao-mysql-local"
$mysqlDatabase = "ruoyi-vue-pro"
$mysqlRootPassword = "123456"

if (-not (Test-Path -LiteralPath $composeFile)) {
  throw "Cannot find compose file: $composeFile"
}

docker compose -f $composeFile up -d
docker compose -f $composeFile ps

Write-Host ""
Write-Host "Waiting for MySQL container to become healthy..."
for ($attempt = 1; $attempt -le 90; $attempt++) {
  $mysqlHealth = ""
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $mysqlHealth = docker inspect -f "{{.State.Health.Status}}" $mysqlContainerName 2> $null
  } catch {
    $mysqlHealth = ""
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }

  if ($mysqlHealth -eq "healthy") {
    break
  }
  if ($attempt -eq 90) {
    throw "MySQL container did not become healthy in time."
  }
  Start-Sleep -Seconds 2
}

Write-Host "Waiting for MySQL to accept authenticated connections..."
$mysqlClientArgs = @("--protocol=TCP", "-h127.0.0.1", "-P3306", "-uroot", "-p$mysqlRootPassword", $mysqlDatabase)
for ($attempt = 1; $attempt -le 60; $attempt++) {
  $mysqlReady = $false
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    docker compose -f $composeFile exec -T mysql mysql @mysqlClientArgs -e "SELECT 1" *> $null
    $mysqlReady = $LASTEXITCODE -eq 0
  } catch {
    $mysqlReady = $false
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }

  if ($mysqlReady) {
    break
  }
  if ($attempt -eq 60) {
    throw "MySQL did not become ready in time."
  }
  Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "Yudao local infrastructure:"
Write-Host "  MySQL: 127.0.0.1:3306"
Write-Host "  Database: ruoyi-vue-pro"
Write-Host "  User: root"
Write-Host "  Password: 123456"
Write-Host "  Redis: 127.0.0.1:6379"
Write-Host ""
Write-Host "Infrastructure is ready. Start yudao-server; packaged Flyway migrations will initialize or upgrade MySQL."
