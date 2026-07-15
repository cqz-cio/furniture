$ErrorActionPreference = "Stop"

$runnerPath = Join-Path $PSScriptRoot "..\invoke-local-migrations.ps1"
$runner = [IO.File]::ReadAllText((Resolve-Path $runnerPath))

$normalizationPattern = @'
UPDATE\s+`system_menu`\s+SET\s+`path`\s*=\s*'dashboard'\s+WHERE\s+`id`\s*=\s*7990\s+AND\s+`path`\s*=\s*'/dashboard'\s+AND\s+`component`\s*=\s*'dashboard/index'\s+AND\s+`component_name`\s*=\s*'FurnitureDashboard'\s+AND\s+`deleted`\s*=\s*b'0'\s*;
'@

$normalization = [regex]::Match($runner, $normalizationPattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $normalization.Success) {
  throw "The migration runner must normalize the legacy /dashboard menu before applying V013."
}

$v013Guard = $runner.IndexOf('if ($version -eq "013")')
if ($v013Guard -lt 0) {
  throw "The migration runner must scope the legacy dashboard repair to V013."
}
if ($normalization.Index -lt $v013Guard) {
  throw "The legacy dashboard repair must run inside the V013 guard."
}

Write-Host "V013 legacy dashboard prerequisite regression test passed."
