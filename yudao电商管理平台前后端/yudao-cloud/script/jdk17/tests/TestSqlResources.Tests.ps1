[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$moduleRoots = @(
    'yudao-module-bpm\yudao-module-bpm-server',
    'yudao-module-infra\yudao-module-infra-server',
    'yudao-module-iot\yudao-module-iot-server',
    'yudao-module-mall\yudao-module-product-server',
    'yudao-module-mall\yudao-module-promotion-server',
    'yudao-module-mall\yudao-module-trade-server',
    'yudao-module-member\yudao-module-member-server',
    'yudao-module-mes\yudao-module-mes-server',
    'yudao-module-pay\yudao-module-pay-server',
    'yudao-module-report\yudao-module-report-server',
    'yudao-module-system\yudao-module-system-server',
    'yudao-module-wms\yudao-module-wms-server'
)

$missing = foreach ($moduleRoot in $moduleRoots) {
    foreach ($name in @('create_tables.sql', 'clean.sql')) {
        $relativePath = Join-Path $moduleRoot "src\test\resources\sql\$name"
        if (-not (Test-Path -LiteralPath (Join-Path $backendRoot $relativePath) -PathType Leaf)) {
            $relativePath
        }
    }
}

if ($missing.Count -gt 0) {
    throw "Required unit-test SQL resources are missing:`n$($missing -join [Environment]::NewLine)"
}

Write-Output "Unit-test SQL resource contract passed: $($moduleRoots.Count * 2) files present."
