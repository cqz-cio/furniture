$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) 'Oakved.Runtime.psm1'
Import-Module $modulePath -Force

function Get-CaughtMessage {
    param([scriptblock]$Action)

    try {
        & $Action
        throw 'Expected the action to throw.'
    }
    catch {
        return $_.Exception.Message
    }
}

function New-InventoryItem {
    param(
        [string]$Worktree,
        [string]$Branch,
        [string]$Commit = '0123456789abcdef0123456789abcdef01234567',
        [bool]$Detached = $false
    )

    [pscustomobject]@{
        Worktree = $Worktree
        Branch   = $Branch
        Commit   = $Commit
        Detached = $Detached
    }
}

function New-ProjectLayoutFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,

        [string[]]$MavenWrapperRelativePaths = @('tools\jdk\Invoke-MavenJdk17.ps1')
    )

    $platformName = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('eXVkYW/nlLXllYbnrqHnkIblubPlj7DliY3lkI7nq68='))
    $furniture = Join-Path $Root 'furniture web'
    $platform = Join-Path $Root $platformName
    $admin = Join-Path $platform 'yudao-ui-admin-vue3'
    $cloud = Join-Path $platform 'yudao-cloud'
    $migrations = Join-Path $cloud 'sql\mysql\migrations'

    New-Item -ItemType Directory -Path $furniture, $admin, $migrations -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $furniture 'package.json') -Value '{}' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $admin 'package.json') -Value '{}' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $cloud 'sql\mysql\oakved-baseline.sql') -Value '-- baseline' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $Root 'start-yudao-all-backend.ps1') -Value '# start' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $Root 'stop-yudao-all-backend.ps1') -Value '# stop' -Encoding Ascii

    foreach ($relativePath in $MavenWrapperRelativePaths) {
        $wrapper = Join-Path $cloud $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $wrapper) -Force | Out-Null
        Set-Content -LiteralPath $wrapper -Value '# wrapper' -Encoding Ascii
    }

    [pscustomobject]@{
        Furniture = $furniture
        Admin      = $admin
        Cloud      = $cloud
        Migrations = $migrations
    }
}

Describe 'Oakved.Runtime public surface' {
    It 'exports exactly the cumulative Task 1 Task 2 and Task 3 functions' {
        $module = Get-Module Oakved.Runtime
        $actual = @($module.ExportedFunctions.Keys | Sort-Object) -join ','
        $actual | Should Be 'Assert-OakvedPortsAvailable,Compare-OakvedMigrationLedger,Get-OakvedBuildFingerprint,Get-OakvedDatabaseName,Get-OakvedMigrationCatalog,Get-OakvedProjectLayout,Get-OakvedRuntimeId,Get-OakvedRuntimeStatus,Get-OakvedWorktreeInventory,Invoke-OakvedDatabaseGate,Resolve-OakvedTarget,Start-OakvedRuntime,Stop-OakvedRuntime,Write-OakvedManifest'
    }
}

Describe 'Get-OakvedWorktreeInventory' {
    It 'parses normal, detached, and space-containing porcelain records independent of the current directory' {
        $lines = @(
            'worktree D:\code',
            'HEAD aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
            'branch refs/heads/codex/agent-rag',
            '',
            'worktree D:\code\.worktrees\feature with spaces',
            'HEAD bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
            'branch refs/heads/feature/spaces',
            '',
            'worktree D:\code\.worktrees\detached build',
            'HEAD cccccccccccccccccccccccccccccccccccccccc',
            'detached'
        )

        Push-Location $TestDrive
        try {
            $inventory = @(Get-OakvedWorktreeInventory -Lines $lines)
        }
        finally {
            Pop-Location
        }

        $inventory.Count | Should Be 3
        $inventory[0].Branch | Should Be 'codex/agent-rag'
        $inventory[1].Worktree | Should Be 'D:\code\.worktrees\feature with spaces'
        $inventory[1].Branch | Should Be 'feature/spaces'
        $inventory[2].Detached | Should Be $true
        $inventory[2].Branch | Should Be $null
    }

    It 'accepts an injectable porcelain provider' {
        $provider = {
            param([string]$RepositoryAnchor)

            @(
                'worktree D:\code\.worktrees\provided',
                'HEAD dddddddddddddddddddddddddddddddddddddddd',
                'branch refs/heads/provided'
            )
        }

        $inventory = @(Get-OakvedWorktreeInventory -Provider $provider)

        $inventory.Count | Should Be 1
        $inventory[0].Branch | Should Be 'provided'
    }
}

Describe 'Get-OakvedRuntimeId' {
    It 'produces a stable main runtime ID with an eight-character SHA-256 suffix' {
        $first = Get-OakvedRuntimeId -Branch 'main'
        $second = Get-OakvedRuntimeId -Branch 'main'

        $first | Should Be $second
        $first | Should Be 'main_0d6e4079'
        $first | Should Match '^main_[0-9a-f]{8}$'
    }

    It 'keeps branches with colliding slugs collision-resistant' {
        (Get-OakvedRuntimeId -Branch 'codex/a-b') | Should Not Be (Get-OakvedRuntimeId -Branch 'codex/a_b')
    }

    It 'normalizes and caps the slug at 32 characters' {
        $runtimeId = Get-OakvedRuntimeId -Branch 'Feature/ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/More'
        ($runtimeId -split '_[0-9a-f]{8}$')[0].Length | Should Be 32
        $runtimeId | Should Match '^[a-z0-9_]+_[0-9a-f]{8}$'
    }
}

Describe 'Resolve-OakvedTarget' {
    $cleanStatus = { param([string]$Worktree) @() }

    Mock Get-OakvedProjectLayout -ModuleName Oakved.Runtime -MockWith {
        param([string]$Worktree)

        [pscustomobject]@{
            Worktree         = $Worktree
            FurnitureWeb     = Join-Path $Worktree 'furniture web'
            FurniturePackage = Join-Path $Worktree 'furniture web\package.json'
            AdminUi          = Join-Path $Worktree 'admin'
            AdminPackage     = Join-Path $Worktree 'admin\package.json'
            YudaoCloud       = Join-Path $Worktree 'cloud'
            Migrations       = Join-Path $Worktree 'cloud\migrations'
            Baseline         = Join-Path $Worktree 'cloud\baseline.sql'
            BackendStart     = Join-Path $Worktree 'start-yudao-all-backend.ps1'
            BackendStop      = Join-Path $Worktree 'stop-yudao-all-backend.ps1'
            MavenJdk17       = Join-Path $Worktree 'cloud\Invoke-MavenJdk17.ps1'
        }
    }

    It 'does not expose a project layout provider that can bypass validation' {
        (@((Get-Command Resolve-OakvedTarget).Parameters.Keys) -contains 'ProjectLayoutProvider') | Should Be $false
    }

    It 'resolves main only from its exact worktree instead of falling back to the repository root' {
        $inventory = @(
            (New-InventoryItem -Worktree 'D:\code' -Branch 'codex/agent-rag' -Commit ('a' * 40)),
            (New-InventoryItem -Worktree 'D:\code\.worktrees\main-runtime' -Branch 'main' -Commit ('b' * 40))
        )

        $target = Resolve-OakvedTarget -Branch 'main' -Inventory $inventory -GitStatusProvider $cleanStatus

        $target.Worktree | Should Be 'D:\code\.worktrees\main-runtime'
        $target.Branch | Should Be 'main'
        $target.Commit | Should Be ('b' * 40)
        $target.Dirty | Should Be $false
        $target.RuntimeId | Should Match '^main_[0-9a-f]{8}$'
        $target.BackendStart | Should Be 'D:\code\.worktrees\main-runtime\start-yudao-all-backend.ps1'
    }

    It 'reports an exact error when the requested branch is missing' {
        $inventory = @((New-InventoryItem -Worktree 'D:\code' -Branch 'codex/agent-rag'))

        $message = Get-CaughtMessage { Resolve-OakvedTarget -Branch 'missing' -Inventory $inventory -GitStatusProvider $cleanStatus }

        $message | Should Be 'Branch missing is not checked out in a worktree.'
    }

    It 'rejects a selected detached worktree with the exact error' {
        $inventory = @((New-InventoryItem -Worktree 'D:\code\.worktrees\detached' -Branch $null -Detached $true))

        $message = Get-CaughtMessage { Resolve-OakvedTarget -Worktree 'D:\code\.worktrees\detached' -Inventory $inventory -GitStatusProvider $cleanStatus }

        $message | Should Be 'Detached worktrees are not supported.'
    }

    It 'rejects ambiguous branch targets with the exact error' {
        $inventory = @(
            (New-InventoryItem -Worktree 'D:\code\.worktrees\one' -Branch 'feature/duplicate'),
            (New-InventoryItem -Worktree 'D:\code\.worktrees\two' -Branch 'feature/duplicate')
        )

        $message = Get-CaughtMessage { Resolve-OakvedTarget -Branch 'feature/duplicate' -Inventory $inventory -GitStatusProvider $cleanStatus }

        $message | Should Be 'Target must resolve to exactly one worktree.'
    }

    It 'rejects dirty main with the exact error' {
        $inventory = @((New-InventoryItem -Worktree 'D:\code\.worktrees\main-runtime' -Branch 'main'))
        $dirtyStatus = { param([string]$Worktree) ' M user-change.txt' }

        $message = Get-CaughtMessage { Resolve-OakvedTarget -Branch 'main' -Inventory $inventory -GitStatusProvider $dirtyStatus }

        $message | Should Be 'main worktree must be clean.'
    }

    It 'allows a dirty feature worktree and reports Dirty true' {
        $inventory = @((New-InventoryItem -Worktree 'D:\code\.worktrees\feature' -Branch 'feature/runtime'))
        $dirtyStatus = { param([string]$Worktree) '?? local-notes.txt' }

        $target = Resolve-OakvedTarget -Branch 'feature/runtime' -Inventory $inventory -GitStatusProvider $dirtyStatus

        $target.Dirty | Should Be $true
    }

    It 'selects a registered worktree case-insensitively and returns the inventory path' {
        $inventory = @((New-InventoryItem -Worktree 'D:\Code\.worktrees\Feature' -Branch 'feature/runtime'))

        $target = Resolve-OakvedTarget -Worktree 'd:\code\.WORKTREES\feature' -Inventory $inventory -GitStatusProvider $cleanStatus

        $target.Worktree | Should Be 'D:\Code\.worktrees\Feature'
    }

    It 'rejects an unregistered worktree path clearly' {
        $inventory = @((New-InventoryItem -Worktree 'D:\code\.worktrees\feature' -Branch 'feature/runtime'))

        $message = Get-CaughtMessage { Resolve-OakvedTarget -Worktree 'D:\code\.worktrees\not-registered' -Inventory $inventory -GitStatusProvider $cleanStatus }

        $message | Should Be 'Worktree D:\code\.worktrees\not-registered is not registered.'
    }

    It 'rejects a relative worktree selector before caller location can affect resolution' {
        $message = Get-CaughtMessage { Resolve-OakvedTarget -Worktree '.\feature' -Inventory @() -GitStatusProvider $cleanStatus }

        $message | Should Be 'Worktree selector must be a fully qualified path.'
    }

    It 'rejects a drive-relative worktree selector before caller location can affect resolution' {
        $message = Get-CaughtMessage { Resolve-OakvedTarget -Worktree 'D:feature' -Inventory @() -GitStatusProvider $cleanStatus }

        $message | Should Be 'Worktree selector must be a fully qualified path.'
    }
}

Describe 'Get-OakvedProjectLayout' {
    It 'validates the real project layout and discovers a uniquely named Maven wrapper anywhere beneath yudao-cloud' {
        $root = Join-Path $TestDrive 'layout root'
        $fixture = New-ProjectLayoutFixture -Root $root -MavenWrapperRelativePaths 'custom\java\Invoke-MavenJdk17.ps1'

        $layout = Get-OakvedProjectLayout -Worktree $root

        $layout.Worktree | Should Be ([IO.Path]::GetFullPath($root))
        $layout.FurnitureWeb | Should Be ([IO.Path]::GetFullPath($fixture.Furniture))
        $layout.AdminUi | Should Be ([IO.Path]::GetFullPath($fixture.Admin))
        $layout.YudaoCloud | Should Be ([IO.Path]::GetFullPath($fixture.Cloud))
        $layout.Migrations | Should Be ([IO.Path]::GetFullPath($fixture.Migrations))
        $layout.BackendStart | Should Be ([IO.Path]::GetFullPath((Join-Path $root 'start-yudao-all-backend.ps1')))
        $layout.BackendStop | Should Be ([IO.Path]::GetFullPath((Join-Path $root 'stop-yudao-all-backend.ps1')))
        $layout.MavenJdk17 | Should Be ([IO.Path]::GetFullPath((Join-Path $fixture.Cloud 'custom\java\Invoke-MavenJdk17.ps1')))
    }

    It 'reports the exact missing required path' {
        $root = Join-Path $TestDrive 'missing layout'
        New-Item -ItemType Directory -Path $root -Force | Out-Null
        $expectedMissing = Join-Path $root 'furniture web'

        $message = Get-CaughtMessage { Get-OakvedProjectLayout -Worktree $root }

        $message | Should Be "Required project directory is missing or has the wrong type: $expectedMissing"
    }

    It 'rejects a required directory that exists as a file' {
        $root = Join-Path $TestDrive 'directory is file'
        New-Item -ItemType Directory -Path $root -Force | Out-Null
        $furniture = Join-Path $root 'furniture web'
        Set-Content -LiteralPath $furniture -Value 'not a directory' -Encoding Ascii

        $message = Get-CaughtMessage { Get-OakvedProjectLayout -Worktree $root }

        $message | Should Be "Required project directory is missing or has the wrong type: $furniture"
    }

    It 'rejects a required file that exists as a directory' {
        $root = Join-Path $TestDrive 'file is directory'
        $fixture = New-ProjectLayoutFixture -Root $root
        $package = Join-Path $fixture.Furniture 'package.json'
        Remove-Item -LiteralPath $package
        New-Item -ItemType Directory -Path $package | Out-Null

        $message = Get-CaughtMessage { Get-OakvedProjectLayout -Worktree $root }

        $message | Should Be "Required project file is missing or has the wrong type: $package"
    }

    It 'rejects a missing Maven wrapper beneath yudao-cloud' {
        $root = Join-Path $TestDrive 'missing wrapper'
        $fixture = New-ProjectLayoutFixture -Root $root -MavenWrapperRelativePaths @()

        $message = Get-CaughtMessage { Get-OakvedProjectLayout -Worktree $root }

        $message | Should Be "Maven/JDK wrapper Invoke-MavenJdk17.ps1 was not found beneath $($fixture.Cloud)."
    }

    It 'rejects ambiguous Maven wrappers beneath yudao-cloud' {
        $root = Join-Path $TestDrive 'ambiguous wrapper'
        $fixture = New-ProjectLayoutFixture -Root $root -MavenWrapperRelativePaths @(
            'one\Invoke-MavenJdk17.ps1',
            'two\Invoke-MavenJdk17.ps1'
        )

        $message = Get-CaughtMessage { Get-OakvedProjectLayout -Worktree $root }

        $message | Should Be "Maven/JDK wrapper Invoke-MavenJdk17.ps1 is ambiguous beneath $($fixture.Cloud)."
    }
}
