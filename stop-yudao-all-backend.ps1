[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspace = $PSScriptRoot
$yudaoRoot = Get-ChildItem -LiteralPath $workspace -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'yudao-cloud') -PathType Container } |
    Select-Object -First 1
if ($null -eq $yudaoRoot) {
    throw "Cannot find the Yudao project under $workspace."
}

$cloudRoot = Join-Path $yudaoRoot.FullName 'yudao-cloud'
$stopScript = Join-Path $cloudRoot 'script\jdk17\Stop-Jdk17Backend.ps1'
$runDirectory = Join-Path $cloudRoot '.local-run\jdk17'
& $stopScript -RunDirectory $runDirectory -Services @('yudao-server', 'ai-server')
