[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$aiRoot = Resolve-Path (Join-Path $repositoryRoot 'yudao-module-ai')
$runtimeConfig = Join-Path $aiRoot 'yudao-module-ai-server\src\main\resources\application.yaml'
$credentialLike = '(?i)(sk-[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]{20,}|Bearer\s+[A-Za-z0-9._-]{12,}|^\s*api-key:\s*(?!\$\{[A-Z0-9_]+(?::[^}]*)?\})[^#\s][^\s]*)'
$gitRoot = (& git -C $repositoryRoot.Path rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitRoot)) {
    throw 'Unable to resolve the Git worktree root.'
}
$repositoryPrefix = $repositoryRoot.Path.Substring($gitRoot.Length).TrimStart('\').Replace('\', '/') + '/'
$trackedConfigFiles = @(& git -C $gitRoot ls-files -- `
        "$repositoryPrefix*.yaml" "$repositoryPrefix*.yml" "$repositoryPrefix*.properties")
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to enumerate tracked configuration files.'
}
if ($trackedConfigFiles.Count -eq 0) {
    throw 'No tracked configuration files were found; refusing to pass an empty secret scan.'
}

$matches = @(
    $trackedConfigFiles | ForEach-Object {
        Select-String -LiteralPath (Join-Path $gitRoot $_) -Pattern $credentialLike
    }
)

if ($matches.Count -gt 0) {
    $locations = $matches | ForEach-Object {
        "$($_.Path.Substring($gitRoot.Length).TrimStart('\')):$($_.LineNumber)"
    }
    throw "Provider credential-like values are checked in at:`n$($locations -join "`n")"
}

$configText = Get-Content -LiteralPath $runtimeConfig -Raw
if ($configText -notmatch '\$\{[A-Z0-9_]+:\}') {
    throw 'Expected empty environment-variable defaults in AI runtime configuration'
}

Write-Host 'AI secret safety contract passed.'
