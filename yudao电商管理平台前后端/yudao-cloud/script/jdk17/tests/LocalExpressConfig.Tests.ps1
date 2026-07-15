$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$localConfig = Join-Path $cloudRoot 'yudao-server\src\main\resources\application-local.yaml'
$yaml = [IO.File]::ReadAllText($localConfig)

foreach ($required in @(
    'client: not_provide',
    'api-key: local-disabled',
    'business-id: local-disabled',
    'key: local-disabled',
    'customer: local-disabled'
)) {
    if (-not $yaml.Contains($required)) {
        throw "Local profile must contain '$required' so optional express integrations do not block startup."
    }
}

Write-Host 'Local express configuration contract passed.'
