[CmdletBinding()]
param(
    [string]$InstallRoot = 'D:\code\.runtime',
    [string]$RepositoryRoot = 'D:\code',
    [switch]$SkipMainWorktree
)

$ErrorActionPreference = 'Stop'
$bin = Join-Path $InstallRoot 'bin'
foreach ($directory in @($bin, (Join-Path $InstallRoot 'state'), (Join-Path $InstallRoot 'logs'),
        (Join-Path $InstallRoot 'backups'), (Join-Path $InstallRoot 'locks'), (Join-Path $InstallRoot 'cache'),
        (Join-Path $InstallRoot 'worktrees'))) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

$launcherBackupRoot = $null
foreach ($name in @('oakved.ps1', 'Oakved.Runtime.psm1')) {
    $source = Join-Path $PSScriptRoot $name
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Runtime source file is missing: $source"
    }
    $destination = Join-Path $bin $name
    if (Test-Path -LiteralPath $destination -PathType Leaf) {
        $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        $destinationHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
        if ($sourceHash -cne $destinationHash) {
            if ($null -eq $launcherBackupRoot) {
                $launcherBackupRoot = Join-Path $InstallRoot ("backups\launcher\{0}" -f [datetime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
                New-Item -ItemType Directory -Path $launcherBackupRoot -Force | Out-Null
            }
            Copy-Item -LiteralPath $destination -Destination (Join-Path $launcherBackupRoot $name) -Force
        }
    }
    $temporary = Join-Path $bin ($name + '.tmp')
    Copy-Item -LiteralPath $source -Destination $temporary -Force
    Move-Item -LiteralPath $temporary -Destination $destination -Force
}

Write-Output "Oakved launcher installed: $(Join-Path $bin 'oakved.ps1')"
if ($null -ne $launcherBackupRoot) {
    Write-Output "Previous launcher backed up: $launcherBackupRoot"
}
