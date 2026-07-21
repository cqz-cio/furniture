$runtimeRoot = Split-Path -Parent $PSScriptRoot
$installer = Join-Path $runtimeRoot 'install-oakved-runtime.ps1'
$cli = Join-Path $runtimeRoot 'oakved.ps1'

Describe 'Oakved runtime installer and CLI' {
    It 'installs the stable launcher and managed snapshot directory without creating a main worktree' {
        $installRoot = Join-Path $TestDrive 'runtime-install'

        $output = @(& $installer -InstallRoot $installRoot -RepositoryRoot (Join-Path $TestDrive 'repository'))

        Test-Path -LiteralPath (Join-Path $installRoot 'bin\oakved.ps1') -PathType Leaf | Should Be $true
        Test-Path -LiteralPath (Join-Path $installRoot 'bin\Oakved.Runtime.psm1') -PathType Leaf | Should Be $true
        Test-Path -LiteralPath (Join-Path $installRoot 'worktrees') -PathType Container | Should Be $true
        ($output -join "`n") | Should Match 'Oakved launcher installed'
        (Get-Content -LiteralPath $installer -Raw) | Should Not Match 'worktree add'
        (Get-Content -LiteralPath $installer -Raw) | Should Not Match 'main-runtime'
    }

    It 'backs up a changed installed launcher before replacing it' {
        $installRoot = Join-Path $TestDrive 'runtime-backup'
        & $installer -InstallRoot $installRoot -RepositoryRoot (Join-Path $TestDrive 'repository') | Out-Null
        Set-Content -LiteralPath (Join-Path $installRoot 'bin\oakved.ps1') -Value '# previous launcher' -Encoding Ascii

        $output = @(& $installer -InstallRoot $installRoot -RepositoryRoot (Join-Path $TestDrive 'repository'))
        $backup = @(Get-ChildItem -LiteralPath (Join-Path $installRoot 'backups\launcher') -Filter 'oakved.ps1' -File -Recurse)

        $backup.Count | Should Be 1
        (Get-Content -LiteralPath $backup[0].FullName -Raw) | Should Match 'previous launcher'
        ($output -join "`n") | Should Match 'Previous launcher backed up'
    }

    It 'routes branch starts through the runtime snapshot root and manifest status through snapshot provenance' {
        $source = Get-Content -LiteralPath $cli -Raw

        $source | Should Match 'Resolve-OakvedTarget -Branch \$Branch -RepositoryRoot \$RepositoryRoot -RuntimeRoot \$RuntimeRoot'
        $source | Should Match 'Resolve-OakvedManifestTarget -Manifest \$manifest -RepositoryRoot \$RepositoryRoot -RuntimeRoot \$RuntimeRoot'
    }
}
