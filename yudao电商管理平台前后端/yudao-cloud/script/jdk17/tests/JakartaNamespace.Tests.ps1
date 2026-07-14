[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$repositoryPrefix = $repositoryRoot.Path.TrimEnd('\') + '\'
$forbiddenImport = '^import javax\.(validation(?:\.|;)|annotation\.(Resource|PostConstruct|PreDestroy|security\.)|servlet(?:\.|;))'

$matches = @(
    Get-ChildItem -LiteralPath $repositoryRoot -Recurse -Filter '*.java' -File |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' } |
        Select-String -Pattern $forbiddenImport
)

if ($matches.Count -gt 0) {
    $details = $matches | ForEach-Object {
        $relativePath = $_.Path.Substring($repositoryPrefix.Length)
        "${relativePath}:$($_.LineNumber):$($_.Line.Trim())"
    }
    throw "Spring Boot 3 production and test sources still contain legacy Java EE imports:`n$($details -join "`n")"
}

Write-Host 'Jakarta namespace contract passed.'
