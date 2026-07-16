param(
    [string]$Target = (Join-Path $PSScriptRoot "test-fulfillment-mysql-integration.ps1")
)

$ErrorActionPreference = "Stop"
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    $Target, [ref]$null, [ref]$parseErrors) | Out-Null
if ($parseErrors.Count -ne 0) {
    throw "PowerShell parser rejected ${Target}: $($parseErrors -join '; ')"
}

$source = Get-Content -Raw -LiteralPath $Target
$requiredPatterns = @(
    'function\s+Assert-IntegrationContainerAbsent',
    'docker\s+rm\s+-f\s+\$Name',
    '\$removeExitCode\s*=\s*\$LASTEXITCODE',
    'if\s*\(\$removeExitCode\s+-ne\s+0\)',
    'docker\s+inspect\s+\$Name',
    '\$ErrorActionPreference\s*=\s*"Continue"',
    '\$ErrorActionPreference\s*=\s*\$previousErrorActionPreference',
    'No such \(object\|container\)',
    'still exists after docker rm -f',
    'Unable to verify cleanup'
)
foreach ($pattern in $requiredPatterns) {
    if ($source -notmatch $pattern) {
        throw "Cleanup contract missing pattern: $pattern"
    }
}
if ($source -match 'Docker cleanup command failed;.*may already be gone') {
    throw "Cleanup must not downgrade an unverified docker failure to a warning"
}

Write-Host "Fulfillment MySQL integration script parses and enforces fail-closed container cleanup"
