[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('start', 'status', 'stop')]
    [string]$Action,

    [string]$Branch,
    [string]$Worktree,
    [string]$RepositoryRoot = 'D:\code',
    [string]$RuntimeRoot = 'D:\code\.runtime',
    [AllowEmptyString()][string]$MySqlRootPassword,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Oakved.Runtime.psm1') -Force

if (-not $PSBoundParameters.ContainsKey('MySqlRootPassword')) {
    $MySqlRootPassword = if ($env:OAKVED_MYSQL_ROOT_PASSWORD) {
        $env:OAKVED_MYSQL_ROOT_PASSWORD
    }
    else {
        '123456'
    }
}

$manifestPath = Join-Path $RuntimeRoot 'runtime.json'

try {
    switch ($Action) {
        'start' {
            if ([bool]$Branch -eq [bool]$Worktree) {
                throw 'Specify exactly one of -Branch or -Worktree.'
            }

            $target = if ($Branch) {
                Resolve-OakvedTarget -Branch $Branch
            }
            else {
                Resolve-OakvedTarget -Worktree $Worktree
            }
            $layout = Get-OakvedProjectLayout -Worktree $target.Worktree
            $result = Start-OakvedRuntime -Target $target -Layout $layout -RuntimeRoot $RuntimeRoot `
                -MySqlRootPassword $MySqlRootPassword -VisibleProcesses
            $result | ConvertTo-Json -Depth 8
        }

        'status' {
            if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
                if ($Json) {
                    [pscustomobject]@{ active = $false; healthy = $false; message = 'No Oakved runtime is active.' } |
                        ConvertTo-Json -Depth 4
                }
                else {
                    Write-Output 'No Oakved runtime is active.'
                }
                exit 0
            }

            $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
            $target = Resolve-OakvedTarget -Worktree ([string]$manifest.Worktree)
            $status = Get-OakvedRuntimeStatus -Manifest $manifest -Target $target `
                -MySqlRootPassword $MySqlRootPassword
            if ($Json) {
                $status | ConvertTo-Json -Depth 8
            }
            else {
                $status | Format-List | Out-String | Write-Output
            }
            exit ([int]$status.ExitCode)
        }

        'stop' {
            $result = Stop-OakvedRuntime -ManifestPath $manifestPath
            $result | ConvertTo-Json -Depth 5
        }
    }
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
