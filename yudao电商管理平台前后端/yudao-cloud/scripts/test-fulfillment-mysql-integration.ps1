param(
    [int]$Port = 33316,
    [string]$Image = "mysql:8.0",
    [string]$TestPattern = "FulfillmentLegacyMigrationCandidateScanIntegrationTest,FulfillmentLegacyMigrationMySqlProductionIntegrationTest,FulfillmentLegacyMigrationMySqlIsolationTest"
)

$ErrorActionPreference = "Stop"
$container = "fulfillment-mysql-integration-$PID"
$password = "test-secret"
$database = "fulfillment_test"
$root = Split-Path -Parent $PSScriptRoot
$migrationRoot = Join-Path $root "sql\mysql\migrations"
$prerequisites = Join-Path $root "yudao-module-mall\yudao-module-trade-server\src\test\resources\mysql\fulfillment-migration-prerequisites.sql"

function Invoke-MySqlFile([string]$Path) {
    Write-Host "Applying $([System.IO.Path]::GetFileName($Path))"
    Get-Content -Raw -LiteralPath $Path |
        docker exec -i -e "MYSQL_PWD=$password" $container mysql --protocol=tcp -uroot $database
    if ($LASTEXITCODE -ne 0) {
        throw "mysql failed while applying $Path"
    }
}

try {
    Write-Host "Starting temporary $Image container $container on 127.0.0.1:$Port"
    docker run --name $container --rm -d `
        -e "MYSQL_ROOT_PASSWORD=$password" `
        -e "MYSQL_DATABASE=$database" `
        -p "127.0.0.1:${Port}:3306" `
        $Image --default-authentication-plugin=mysql_native_password | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "docker run failed"
    }

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec -e "MYSQL_PWD=$password" $container mysqladmin --protocol=tcp -uroot ping --silent 2>$null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw "MySQL did not become ready within 60 seconds"
    }

    Invoke-MySqlFile $prerequisites
    @(
        "V015__trade_fulfillment_core.sql",
        "V016__trade_tracking_status_mapping.sql",
        "V017__trade_tracking_event_watermarks.sql",
        "V018__trade_manual_tracking_audit.sql",
        "V019__trade_fulfillment_admin_permissions.sql",
        "V020__trade_fulfillment_legacy_migration_fact.sql"
    ) | ForEach-Object { Invoke-MySqlFile (Join-Path $migrationRoot $_) }

    $env:FULFILLMENT_MYSQL_TEST_URL = "jdbc:mysql://127.0.0.1:$Port/${database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    $env:FULFILLMENT_MYSQL_TEST_USER = "root"
    $env:FULFILLMENT_MYSQL_TEST_PASSWORD = $password
    Write-Host "Running H2 candidate scan and real MySQL production-writer integration tests"
    mvn -pl yudao-module-mall/yudao-module-trade-server -am `
        "-Dtest=$TestPattern" `
        "-Dsurefire.failIfNoSpecifiedTests=false" test
    if ($LASTEXITCODE -ne 0) {
        throw "Maven integration tests failed"
    }
} finally {
    Remove-Item Env:FULFILLMENT_MYSQL_TEST_URL -ErrorAction SilentlyContinue
    Remove-Item Env:FULFILLMENT_MYSQL_TEST_USER -ErrorAction SilentlyContinue
    Remove-Item Env:FULFILLMENT_MYSQL_TEST_PASSWORD -ErrorAction SilentlyContinue
    try {
        docker rm -f $container 2>$null | Out-Null
    } catch {
        Write-Warning "Docker cleanup command failed; the container uses --rm and may already be gone: $($_.Exception.Message)"
    }
    Write-Host "Removed temporary container $container"
}
