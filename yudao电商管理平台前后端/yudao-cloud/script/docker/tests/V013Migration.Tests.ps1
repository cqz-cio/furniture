$ErrorActionPreference = "Stop"

$runnerPath = Join-Path $PSScriptRoot "..\invoke-local-migrations.ps1"
$runner = [IO.File]::ReadAllText((Resolve-Path $runnerPath))

if ($runner -notmatch "standalone SQL migration runner has been retired") {
  throw "The legacy runner must remain retired so Flyway is the only migration writer."
}
if ($runner -match "source\s+\$containerPath|INSERT INTO\s+schema_migrations") {
  throw "The retired runner must not execute or record numbered migrations."
}

Write-Host "Legacy migration runner retirement regression test passed."
