$ErrorActionPreference = "Stop"

$workspace = "D:\code"

$yudaoRoot = Get-ChildItem -LiteralPath $workspace -Directory |
  Where-Object { $_.Name -like "yudao*" } |
  Select-Object -First 1

if ($null -eq $yudaoRoot) {
  throw "Cannot find a yudao project directory under $workspace."
}

$infraScript = Join-Path $yudaoRoot.FullName "yudao-cloud\script\docker\start-local-infra.ps1"
if (-not (Test-Path -LiteralPath $infraScript)) {
  throw "Cannot find local infrastructure script: $infraScript"
}

& powershell -ExecutionPolicy Bypass -File $infraScript
