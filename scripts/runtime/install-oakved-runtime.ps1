[CmdletBinding()]
param(
    [string]$InstallRoot = 'D:\code\.runtime',
    [string]$RepositoryRoot = 'D:\code',
    [switch]$SkipMainWorktree
)

$ErrorActionPreference = 'Stop'
$bin = Join-Path $InstallRoot 'bin'
foreach ($directory in @($bin, (Join-Path $InstallRoot 'state'), (Join-Path $InstallRoot 'logs'),
        (Join-Path $InstallRoot 'backups'), (Join-Path $InstallRoot 'locks'), (Join-Path $InstallRoot 'cache'))) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

foreach ($name in @('oakved.ps1', 'Oakved.Runtime.psm1')) {
    $source = Join-Path $PSScriptRoot $name
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Runtime source file is missing: $source"
    }
    $temporary = Join-Path $bin ($name + '.tmp')
    Copy-Item -LiteralPath $source -Destination $temporary -Force
    Move-Item -LiteralPath $temporary -Destination (Join-Path $bin $name) -Force
}

if (-not $SkipMainWorktree) {
    $mainPath = Join-Path $RepositoryRoot '.worktrees\main-runtime'
    $mainRecord = @(& git -C $RepositoryRoot worktree list --porcelain) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Git worktrees.' }
    if ($mainRecord -notmatch '(?m)^branch refs/heads/main$') {
        & git -C $RepositoryRoot worktree add $mainPath main
        if ($LASTEXITCODE -ne 0) { throw 'Unable to create the main runtime worktree.' }
    }
}

Write-Output "Oakved launcher installed: $(Join-Path $bin 'oakved.ps1')"
