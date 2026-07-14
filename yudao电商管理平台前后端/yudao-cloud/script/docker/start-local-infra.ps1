param(
  [switch] $Recreate
)

$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose-local-infra.yml"
$mysqlContainerName = "yudao-mysql-local"
$mysqlDatabase = "ruoyi-vue-pro"
$mysqlRootPassword = "123456"

if (-not (Test-Path -LiteralPath $composeFile)) {
  throw "Cannot find compose file: $composeFile"
}

if ($Recreate) {
  docker compose -f $composeFile down -v
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

function Invoke-MySqlMigration {
  param(
    [Parameter(Mandatory = $true)]
    [string] $SqlPath
  )

  if (-not (Test-Path -LiteralPath $SqlPath)) {
    throw "Cannot find SQL migration: $SqlPath"
  }

  $containerPath = "/tmp/" + [System.IO.Path]::GetFileName($SqlPath)
  Write-Host "Applying MySQL migration: $SqlPath"
  docker compose -f $composeFile cp $SqlPath "mysql:$containerPath"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to copy SQL migration into MySQL container: $SqlPath"
  }

  docker compose -f $composeFile exec -T mysql mysql @mysqlClientArgs --execute "source $containerPath"
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to apply SQL migration: $SqlPath"
  }
}

$mysqlSqlDir = Resolve-Path (Join-Path $PSScriptRoot "..\..\sql\mysql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "yudao-module-tables.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "member-email-auth.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "mall-erp-integration.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "member-trade-application.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "member-membership.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "member-gift-registry.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "trade-gift-registry-context.sql")

Write-Host ""
Write-Host "Yudao local infrastructure:"
Write-Host "  MySQL: 127.0.0.1:3306"
Write-Host "  Database: ruoyi-vue-pro"
Write-Host "  User: root"
Write-Host "  Password: 123456"
Write-Host "  Redis: 127.0.0.1:6379"
Write-Host ""
Write-Host "Run YudaoServerApplication after both containers are healthy."
