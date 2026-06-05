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
Write-Host "Waiting for MySQL to accept connections..."
for ($attempt = 1; $attempt -le 60; $attempt++) {
  docker compose -f $composeFile exec -T mysql mysqladmin ping -uroot -p123456 --silent *> $null
  if ($LASTEXITCODE -eq 0) {
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

  $mysqlCommand = 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < ' + $containerPath
  docker compose -f $composeFile exec -T mysql sh -c $mysqlCommand
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to apply SQL migration: $SqlPath"
  }
}

$mysqlSqlDir = Resolve-Path (Join-Path $PSScriptRoot "..\..\sql\mysql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "yudao-module-tables.sql")
Invoke-MySqlMigration (Join-Path $mysqlSqlDir "member-email-auth.sql")

Write-Host ""
Write-Host "Yudao local infrastructure:"
Write-Host "  MySQL: 127.0.0.1:3306"
Write-Host "  Database: ruoyi-vue-pro"
Write-Host "  User: root"
Write-Host "  Password: 123456"
Write-Host "  Redis: 127.0.0.1:6379"
Write-Host ""
Write-Host "Run YudaoServerApplication after both containers are healthy."
