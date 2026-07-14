[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$aiRoot = Resolve-Path (Join-Path $repositoryRoot 'yudao-module-ai')
$runtimeConfig = Join-Path $aiRoot 'yudao-module-ai-server\src\main\resources\application.yaml'
$credentialLike = '(?i)(sk-[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]{20,}|Bearer\s+[A-Za-z0-9._-]{12,}|^\s*api-key:\s*(?!\$\{[A-Z0-9_]+(?::[^}]*)?\})[^#\s][^\s]*)'

$matches = @(
    Get-ChildItem -LiteralPath $repositoryRoot -Recurse -File -Include '*.yaml', '*.yml', '*.properties' |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' } |
        Select-String -Pattern $credentialLike
)

if ($matches.Count -gt 0) {
    $repositoryPrefix = $repositoryRoot.Path.TrimEnd('\') + '\'
    $locations = $matches | ForEach-Object {
        "$($_.Path.Substring($repositoryPrefix.Length)):$($_.LineNumber)"
    }
    throw "Provider credential-like values are checked in at:`n$($locations -join "`n")"
}

$configText = Get-Content -LiteralPath $runtimeConfig -Raw
if ($configText -notmatch '\$\{[A-Z0-9_]+:\}') {
    throw 'Expected empty environment-variable defaults in AI runtime configuration'
}

Write-Host 'AI secret safety contract passed.'
