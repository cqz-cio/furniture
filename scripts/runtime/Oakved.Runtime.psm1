Set-StrictMode -Version 2.0

$script:RepositoryAnchor = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:PlatformDirectoryName = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String('eXVkYW/nlLXllYbnrqHnkIblubPlj7DliY3lkI7nq68=')
)

function ConvertTo-OakvedNormalizedPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $fullPath = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetPathRoot($fullPath)
    if ($fullPath.Length -gt $root.Length) {
        return $fullPath.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    }

    return $fullPath
}

function Test-OakvedFullyQualifiedPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not [IO.Path]::IsPathRooted($Path)) {
        return $false
    }

    $root = [IO.Path]::GetPathRoot($Path)
    if ($root -match '^[a-zA-Z]:[\\/]$') {
        return $true
    }

    return $root -match '^[\\/]{2}[^\\/]+[\\/][^\\/]+[\\/]?$'
}

function Test-OakvedPathWithinRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Root
    )
    $normalizedPath = ConvertTo-OakvedNormalizedPath -Path $Path
    $normalizedRoot = ConvertTo-OakvedNormalizedPath -Path $Root
    if ([string]::Equals($normalizedPath, $normalizedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    $prefix = $normalizedRoot + [IO.Path]::DirectorySeparatorChar
    return $normalizedPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Get-OakvedRuntimeId {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$Branch
    )

    $slug = $Branch.ToLowerInvariant() -replace '[^a-z0-9]+', '_'
    $slug = $slug.Trim('_')
    if ($slug.Length -gt 32) {
        $slug = $slug.Substring(0, 32)
    }

    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($Branch))
    }
    finally {
        $sha256.Dispose()
    }

    $hash = ([BitConverter]::ToString($hashBytes) -replace '-', '').ToLowerInvariant().Substring(0, 8)
    return '{0}_{1}' -f $slug, $hash
}

function Get-OakvedWorktreeInventory {
    [CmdletBinding(DefaultParameterSetName = 'Git')]
    param(
        [string]$RepositoryRoot = $script:RepositoryAnchor,

        [Parameter(Mandatory = $true, ParameterSetName = 'Lines')]
        [AllowEmptyCollection()]
        [AllowEmptyString()]
        [string[]]$Lines,

        [Parameter(Mandatory = $true, ParameterSetName = 'Provider')]
        [scriptblock]$Provider
    )

    $normalizedRepositoryRoot = ConvertTo-OakvedNormalizedPath -Path $RepositoryRoot
    if ($PSCmdlet.ParameterSetName -eq 'Provider') {
        $porcelainLines = @(& $Provider $normalizedRepositoryRoot)
    }
    elseif ($PSCmdlet.ParameterSetName -eq 'Lines') {
        $porcelainLines = @($Lines)
    }
    else {
        $porcelainLines = @(& git -C $normalizedRepositoryRoot worktree list --porcelain)
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to read git worktree inventory.'
        }
    }

    $records = New-Object 'System.Collections.Generic.List[object]'
    $current = @{}

    foreach ($lineValue in @($porcelainLines) + @('')) {
        $line = [string]$lineValue
        if ([string]::IsNullOrWhiteSpace($line)) {
            if ($current.ContainsKey('Worktree')) {
                $branch = $null
                if ($current.ContainsKey('Branch')) {
                    $branch = [string]$current.Branch
                    if ($branch.StartsWith('refs/heads/', [StringComparison]::Ordinal)) {
                        $branch = $branch.Substring('refs/heads/'.Length)
                    }
                }

                $commit = $null
                if ($current.ContainsKey('Commit')) {
                    $commit = [string]$current.Commit
                }

                $records.Add([pscustomobject]@{
                    Worktree = ConvertTo-OakvedNormalizedPath -Path ([string]$current.Worktree)
                    Commit   = $commit
                    Branch   = $branch
                    Detached = $current.ContainsKey('Detached')
                })
            }

            $current = @{}
            continue
        }

        if ($line.StartsWith('worktree ', [StringComparison]::Ordinal)) {
            $current.Worktree = $line.Substring('worktree '.Length)
        }
        elseif ($line.StartsWith('HEAD ', [StringComparison]::Ordinal)) {
            $current.Commit = $line.Substring('HEAD '.Length)
        }
        elseif ($line.StartsWith('branch ', [StringComparison]::Ordinal)) {
            $current.Branch = $line.Substring('branch '.Length)
        }
        elseif ($line -eq 'detached') {
            $current.Detached = $true
        }
    }

    return $records.ToArray()
}

function Get-OakvedBranchCommit {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$Branch,

        [string]$RepositoryRoot = $script:RepositoryAnchor,

        [scriptblock]$CommitProvider
    )

    $normalizedRepositoryRoot = ConvertTo-OakvedNormalizedPath -Path $RepositoryRoot
    if ($null -ne $CommitProvider) {
        $commit = [string](& $CommitProvider $normalizedRepositoryRoot $Branch)
    }
    else {
        $reference = "refs/heads/$Branch"
        $commitLines = @(& git -C $normalizedRepositoryRoot rev-parse --verify "$reference`^{commit}" 2>$null)
        if ($LASTEXITCODE -ne 0) {
            throw "Branch $Branch does not exist."
        }
        $commit = (($commitLines | ForEach-Object { [string]$_ }) -join '').Trim()
    }

    if ($commit -cnotmatch '^[0-9a-fA-F]{40}$') {
        throw "Branch $Branch did not resolve to a complete Git commit."
    }

    return $commit.ToLowerInvariant()
}

function New-OakvedRuntimeSnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Branch,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$Commit,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$RuntimeRoot,
        [AllowEmptyCollection()][object[]]$Inventory,
        [scriptblock]$GitStatusProvider,
        [scriptblock]$WorktreeAdder
    )

    $normalizedRepositoryRoot = ConvertTo-OakvedNormalizedPath -Path $RepositoryRoot
    $normalizedRuntimeRoot = ConvertTo-OakvedNormalizedPath -Path $RuntimeRoot
    $snapshotRoot = ConvertTo-OakvedNormalizedPath -Path (Join-Path $normalizedRuntimeRoot 'worktrees')
    $runtimeId = Get-OakvedRuntimeId -Branch $Branch
    $normalizedCommit = $Commit.ToLowerInvariant()
    $snapshotPath = ConvertTo-OakvedNormalizedPath -Path (Join-Path $snapshotRoot "$runtimeId-$($normalizedCommit.Substring(0, 12))")

    if (-not (Test-OakvedPathWithinRoot -Path $snapshotPath -Root $snapshotRoot) -or
        [string]::Equals($snapshotPath, $snapshotRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Runtime snapshot path escaped the managed snapshot root.'
    }

    if (-not $PSBoundParameters.ContainsKey('Inventory')) {
        $Inventory = @(Get-OakvedWorktreeInventory -RepositoryRoot $normalizedRepositoryRoot)
    }

    $matches = @($Inventory | Where-Object {
        [string]::Equals(
            (ConvertTo-OakvedNormalizedPath -Path ([string]$_.Worktree)),
            $snapshotPath,
            [StringComparison]::OrdinalIgnoreCase
        )
    })

    if ($matches.Count -gt 1) {
        throw "Runtime snapshot path is registered more than once: $snapshotPath"
    }

    $record = if ($matches.Count -eq 1) { $matches[0] } else { $null }
    if ($null -eq $record) {
        if (Test-Path -LiteralPath $snapshotPath) {
            throw "Runtime snapshot path already exists but is not a registered Git worktree: $snapshotPath"
        }

        New-Item -ItemType Directory -Path $snapshotRoot -Force | Out-Null
        if ($null -ne $WorktreeAdder) {
            $record = & $WorktreeAdder $normalizedRepositoryRoot $snapshotPath $normalizedCommit
        }
        else {
            & git -C $normalizedRepositoryRoot worktree add --detach $snapshotPath $normalizedCommit
            if ($LASTEXITCODE -ne 0) {
                throw "Unable to create runtime snapshot at $snapshotPath."
            }
            $createdInventory = @(Get-OakvedWorktreeInventory -RepositoryRoot $normalizedRepositoryRoot)
            $createdMatches = @($createdInventory | Where-Object {
                [string]::Equals(
                    (ConvertTo-OakvedNormalizedPath -Path ([string]$_.Worktree)),
                    $snapshotPath,
                    [StringComparison]::OrdinalIgnoreCase
                )
            })
            if ($createdMatches.Count -ne 1) {
                throw "Created runtime snapshot is not registered exactly once: $snapshotPath"
            }
            $record = $createdMatches[0]
        }
    }

    if ($null -eq $record) {
        throw "Runtime snapshot provider returned no worktree for $snapshotPath."
    }
    if (-not [bool]$record.Detached) {
        throw "Managed runtime snapshot must be detached: $snapshotPath"
    }
    if ([string]$record.Commit -cne $normalizedCommit) {
        throw "Runtime snapshot commit mismatch at $snapshotPath."
    }

    if ($null -ne $GitStatusProvider) {
        $statusLines = @(& $GitStatusProvider $snapshotPath)
    }
    else {
        $statusLines = @(& git -C $snapshotPath status --porcelain --untracked-files=no)
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to inspect runtime snapshot status at $snapshotPath."
        }
    }
    $trackedChanges = @($statusLines | Where-Object {
        $null -ne $_ -and -not [string]::IsNullOrEmpty([string]$_)
    })
    if ($trackedChanges.Count -gt 0) {
        throw "Runtime snapshot has tracked changes: $snapshotPath"
    }

    return [pscustomobject]@{
        Worktree = $snapshotPath
        Commit   = $normalizedCommit
        Branch   = $null
        Detached = $true
    }
}

function Get-OakvedProjectLayout {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$Worktree
    )

    $normalizedWorktree = ConvertTo-OakvedNormalizedPath -Path $Worktree
    $furnitureWeb = ConvertTo-OakvedNormalizedPath -Path (Join-Path $normalizedWorktree 'furniture web')
    $platform = ConvertTo-OakvedNormalizedPath -Path (Join-Path $normalizedWorktree $script:PlatformDirectoryName)
    $adminUi = ConvertTo-OakvedNormalizedPath -Path (Join-Path $platform 'yudao-ui-admin-vue3')
    $yudaoCloud = ConvertTo-OakvedNormalizedPath -Path (Join-Path $platform 'yudao-cloud')

    $directories = [ordered]@{
        Worktree     = $normalizedWorktree
        FurnitureWeb = $furnitureWeb
        AdminUi      = $adminUi
        YudaoCloud   = $yudaoCloud
        Migrations   = ConvertTo-OakvedNormalizedPath -Path (Join-Path $yudaoCloud 'sql\mysql\migrations')
    }

    foreach ($requiredPath in $directories.Values) {
        if (-not (Test-Path -LiteralPath $requiredPath -PathType Container)) {
            throw "Required project directory is missing or has the wrong type: $requiredPath"
        }
    }

    $files = [ordered]@{
        FurniturePackage = ConvertTo-OakvedNormalizedPath -Path (Join-Path $furnitureWeb 'package.json')
        AdminPackage     = ConvertTo-OakvedNormalizedPath -Path (Join-Path $adminUi 'package.json')
        Baseline         = ConvertTo-OakvedNormalizedPath -Path (Join-Path $yudaoCloud 'sql\mysql\oakved-baseline.sql')
        BackendStart     = ConvertTo-OakvedNormalizedPath -Path (Join-Path $normalizedWorktree 'start-yudao-all-backend.ps1')
        BackendStop      = ConvertTo-OakvedNormalizedPath -Path (Join-Path $normalizedWorktree 'stop-yudao-all-backend.ps1')
    }

    foreach ($requiredPath in $files.Values) {
        if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
            throw "Required project file is missing or has the wrong type: $requiredPath"
        }
    }

    $mavenWrappers = @(Get-ChildItem -LiteralPath $yudaoCloud -Filter 'Invoke-MavenJdk17.ps1' -File -Recurse -Force)
    if ($mavenWrappers.Count -eq 0) {
        throw "Maven/JDK wrapper Invoke-MavenJdk17.ps1 was not found beneath $yudaoCloud."
    }
    if ($mavenWrappers.Count -ne 1) {
        throw "Maven/JDK wrapper Invoke-MavenJdk17.ps1 is ambiguous beneath $yudaoCloud."
    }

    $mavenJdk17 = ConvertTo-OakvedNormalizedPath -Path $mavenWrappers[0].FullName
    $cloudPrefix = $yudaoCloud.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $mavenJdk17.StartsWith($cloudPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Maven/JDK wrapper must be contained beneath $yudaoCloud."
    }

    $layout = [ordered]@{
        Worktree         = $directories.Worktree
        FurnitureWeb     = $directories.FurnitureWeb
        FurniturePackage = $files.FurniturePackage
        AdminUi          = $directories.AdminUi
        AdminPackage     = $files.AdminPackage
        YudaoCloud       = $directories.YudaoCloud
        Migrations       = $directories.Migrations
        Baseline         = $files.Baseline
        BackendStart     = $files.BackendStart
        BackendStop      = $files.BackendStop
        MavenJdk17       = $mavenJdk17
        ServerJar        = ConvertTo-OakvedNormalizedPath -Path (Join-Path $yudaoCloud 'yudao-server\target\yudao-server.jar')
    }

    return [pscustomobject]$layout
}

function Resolve-OakvedTarget {
    [CmdletBinding(DefaultParameterSetName = 'Branch')]
    param(
        [Parameter(Mandatory = $true, ParameterSetName = 'Branch')]
        [ValidateNotNullOrEmpty()]
        [string]$Branch,

        [Parameter(Mandatory = $true, ParameterSetName = 'Worktree')]
        [ValidateNotNullOrEmpty()]
        [string]$Worktree,

        [string]$RepositoryRoot = $script:RepositoryAnchor,

        [string]$RuntimeRoot = 'D:\code\.runtime',

        [AllowEmptyCollection()]
        [object[]]$Inventory,

        [scriptblock]$GitStatusProvider,

        [scriptblock]$BranchCommitProvider,

        [scriptblock]$SnapshotProvider
    )

    if (-not $PSBoundParameters.ContainsKey('Inventory')) {
        $Inventory = @(Get-OakvedWorktreeInventory -RepositoryRoot $RepositoryRoot)
    }

    if ($PSCmdlet.ParameterSetName -eq 'Branch') {
        $commit = Get-OakvedBranchCommit -Branch $Branch -RepositoryRoot $RepositoryRoot -CommitProvider $BranchCommitProvider
        $sourceMatches = @($Inventory | Where-Object {
            $branchProperty = $_.PSObject.Properties['Branch']
            $null -ne $branchProperty -and ([string]$branchProperty.Value -ceq $Branch)
        })
        if ($sourceMatches.Count -gt 1) {
            throw "Branch $Branch is checked out in more than one worktree."
        }
        $sourceWorktree = $null
        $sourceDirty = $false
        if ($sourceMatches.Count -eq 1) {
            $sourceWorktree = ConvertTo-OakvedNormalizedPath -Path ([string]$sourceMatches[0].Worktree)
            if ($null -ne $GitStatusProvider) {
                $sourceStatusLines = @(& $GitStatusProvider $sourceWorktree)
            }
            else {
                $sourceStatusLines = @(& git -C $sourceWorktree status --porcelain)
                if ($LASTEXITCODE -ne 0) {
                    throw "Unable to read git status for source worktree $sourceWorktree."
                }
            }
            $sourceDirty = @($sourceStatusLines | Where-Object {
                $null -ne $_ -and -not [string]::IsNullOrEmpty([string]$_)
            }).Count -gt 0
        }

        $snapshot = if ($null -ne $SnapshotProvider) {
            & $SnapshotProvider $Branch $commit (ConvertTo-OakvedNormalizedPath -Path $RepositoryRoot) (ConvertTo-OakvedNormalizedPath -Path $RuntimeRoot)
        }
        else {
            New-OakvedRuntimeSnapshot -Branch $Branch -Commit $commit -RepositoryRoot $RepositoryRoot -RuntimeRoot $RuntimeRoot
        }
        if ($null -eq $snapshot -or -not [bool]$snapshot.Detached) {
            throw 'Branch snapshot provider must return a detached worktree.'
        }
        if ([string]$snapshot.Commit -cne [string]$commit) {
            throw 'Branch snapshot provider returned the wrong commit.'
        }

        $selectedWorktree = ConvertTo-OakvedNormalizedPath -Path ([string]$snapshot.Worktree)
        $selectedBranch = $Branch
        $selectedCommit = $commit
        $dirty = $false
        $mode = 'snapshot'
    }
    else {
        if (-not (Test-OakvedFullyQualifiedPath -Path $Worktree)) {
            throw 'Worktree selector must be a fully qualified path.'
        }

        $normalizedRequestedWorktree = ConvertTo-OakvedNormalizedPath -Path $Worktree
        $matches = @($Inventory | Where-Object {
            $worktreeProperty = $_.PSObject.Properties['Worktree']
            if ($null -eq $worktreeProperty) {
                return $false
            }

            $candidate = ConvertTo-OakvedNormalizedPath -Path ([string]$worktreeProperty.Value)
            return [string]::Equals($candidate, $normalizedRequestedWorktree, [StringComparison]::OrdinalIgnoreCase)
        })

        if ($matches.Count -eq 0) {
            throw "Worktree $normalizedRequestedWorktree is not registered."
        }
        if ($matches.Count -ne 1) {
            throw 'Target must resolve to exactly one worktree.'
        }

        $selected = $matches[0]
        $detachedProperty = $selected.PSObject.Properties['Detached']
        if ($null -ne $detachedProperty -and [bool]$detachedProperty.Value) {
            throw 'Detached worktrees are not supported.'
        }

        $selectedWorktree = ConvertTo-OakvedNormalizedPath -Path ([string]$selected.Worktree)
        if ($PSBoundParameters.ContainsKey('GitStatusProvider')) {
            $statusLines = @(& $GitStatusProvider $selectedWorktree)
        }
        else {
            $statusLines = @(& git -C $selectedWorktree status --porcelain)
            if ($LASTEXITCODE -ne 0) {
                throw "Unable to read git status for worktree $selectedWorktree."
            }
        }

        $dirty = @($statusLines | Where-Object {
            $null -ne $_ -and -not [string]::IsNullOrEmpty([string]$_)
        }).Count -gt 0
        $selectedBranch = [string]$selected.Branch
        $selectedCommit = [string]$selected.Commit
        $sourceWorktree = $selectedWorktree
        $sourceDirty = $dirty
        $mode = 'live-worktree'
    }

    $projectLayout = Get-OakvedProjectLayout -Worktree $selectedWorktree

    $target = [ordered]@{
        Mode      = $mode
        Branch    = $selectedBranch
        Commit    = $selectedCommit
        Dirty     = $dirty
        SourceDirty = [bool]$sourceDirty
        SourceWorktree = $sourceWorktree
        Worktree  = $selectedWorktree
        RuntimeId = Get-OakvedRuntimeId -Branch $selectedBranch
    }

    foreach ($property in $projectLayout.PSObject.Properties) {
        if ($property.Name -ne 'Worktree') {
            $target[$property.Name] = $property.Value
        }
    }

    return [pscustomobject]$target
}

function Resolve-OakvedManifestTarget {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object]$Manifest,
        [string]$RepositoryRoot = $script:RepositoryAnchor,
        [string]$RuntimeRoot = 'D:\code\.runtime',
        [AllowEmptyCollection()][object[]]$Inventory,
        [scriptblock]$GitStatusProvider,
        [scriptblock]$BranchCommitProvider
    )

    $modeProperty = $Manifest.PSObject.Properties['Mode']
    $mode = if ($null -ne $modeProperty -and -not [string]::IsNullOrWhiteSpace([string]$modeProperty.Value)) {
        [string]$modeProperty.Value
    }
    else {
        'live-worktree'
    }

    if (-not $PSBoundParameters.ContainsKey('Inventory')) {
        $Inventory = @(Get-OakvedWorktreeInventory -RepositoryRoot $RepositoryRoot)
    }

    if ($mode -cne 'snapshot') {
        return Resolve-OakvedTarget -Worktree ([string]$Manifest.Worktree) -RepositoryRoot $RepositoryRoot `
            -RuntimeRoot $RuntimeRoot -Inventory $Inventory -GitStatusProvider $GitStatusProvider
    }

    $snapshotRoot = ConvertTo-OakvedNormalizedPath -Path (Join-Path (ConvertTo-OakvedNormalizedPath -Path $RuntimeRoot) 'worktrees')
    $snapshotPath = ConvertTo-OakvedNormalizedPath -Path ([string]$Manifest.Worktree)
    if (-not (Test-OakvedPathWithinRoot -Path $snapshotPath -Root $snapshotRoot) -or
        [string]::Equals($snapshotPath, $snapshotRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Manifest snapshot path is outside the managed runtime snapshot root.'
    }

    $matches = @($Inventory | Where-Object {
        [string]::Equals(
            (ConvertTo-OakvedNormalizedPath -Path ([string]$_.Worktree)),
            $snapshotPath,
            [StringComparison]::OrdinalIgnoreCase
        )
    })
    if ($matches.Count -ne 1) {
        throw "Manifest snapshot is not registered exactly once: $snapshotPath"
    }
    $record = $matches[0]
    if (-not [bool]$record.Detached) {
        throw "Manifest snapshot is not detached: $snapshotPath"
    }
    if ([string]$record.Commit -cne [string]$Manifest.Commit) {
        throw "Manifest snapshot commit mismatch at $snapshotPath."
    }

    if ($null -ne $GitStatusProvider) {
        $statusLines = @(& $GitStatusProvider $snapshotPath)
    }
    else {
        $statusLines = @(& git -C $snapshotPath status --porcelain --untracked-files=no)
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to inspect manifest snapshot status at $snapshotPath."
        }
    }
    $dirty = @($statusLines | Where-Object {
        $null -ne $_ -and -not [string]::IsNullOrEmpty([string]$_)
    }).Count -gt 0

    $sourceDirtyProperty = $Manifest.PSObject.Properties['SourceDirty']
    $sourceWorktreeProperty = $Manifest.PSObject.Properties['SourceWorktree']
    $refCommit = $null
    try {
        $refCommit = Get-OakvedBranchCommit -Branch ([string]$Manifest.Branch) -RepositoryRoot $RepositoryRoot -CommitProvider $BranchCommitProvider
    }
    catch {
        $refCommit = $null
    }

    $layout = Get-OakvedProjectLayout -Worktree $snapshotPath
    $target = [ordered]@{
        Mode = 'snapshot'
        Branch = [string]$Manifest.Branch
        Commit = [string]$record.Commit
        RefCommit = $refCommit
        Dirty = $dirty
        SourceDirty = $(if ($null -ne $sourceDirtyProperty) { [bool]$sourceDirtyProperty.Value } else { $false })
        SourceWorktree = $(if ($null -ne $sourceWorktreeProperty) { [string]$sourceWorktreeProperty.Value } else { $null })
        Worktree = $snapshotPath
        RuntimeId = Get-OakvedRuntimeId -Branch ([string]$Manifest.Branch)
    }
    foreach ($property in $layout.PSObject.Properties) {
        if ($property.Name -ne 'Worktree') {
            $target[$property.Name] = $property.Value
        }
    }
    return [pscustomobject]$target
}

function Get-OakvedSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Value
    )

    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
        return ([BitConverter]::ToString($sha256.ComputeHash($bytes)) -replace '-', '').ToLowerInvariant()
    }
    finally {
        $sha256.Dispose()
    }
}

function Get-OakvedMigrationCatalog {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [string[]]$Files,

        [Parameter(Mandatory = $true)]
        [scriptblock]$ContentProvider
    )

    $rows = @(
        foreach ($file in @($Files | Sort-Object)) {
            $name = [IO.Path]::GetFileName($file)
            if ($name -cnotmatch '^V(?<version>\d{3})__(?<description>[a-z0-9_]+)\.sql$') {
                throw "Invalid migration filename $name."
            }

            $version = $Matches.version
            $description = $Matches.description
            $content = [string](& $ContentProvider $file)
            $normalizedContent = ($content -replace "`r`n", "`n") -replace "`r", "`n"
            $normalizedContent = ($normalizedContent -replace '\s+$', '') + "`n"
            [pscustomobject]@{
                Version    = $version
                ScriptName = $name
                Description = $description -replace '_', ' '
                Checksum   = Get-OakvedSha256 -Value $normalizedContent
                Path       = $file
            }
        }
    )

    $duplicates = @($rows | Group-Object Version | Where-Object { $_.Count -gt 1 })
    if ($duplicates.Count -gt 0) {
        throw "Duplicate migration version $($duplicates[0].Name)."
    }

    for ($index = 0; $index -lt $rows.Count; $index++) {
        if ([int]$rows[$index].Version -ne ($index + 1)) {
            throw 'Migration catalog must be contiguous.'
        }
    }

    return @($rows)
}

function Compare-OakvedMigrationLedger {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]]$Catalog,

        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]]$Ledger
    )

    $orderedCatalog = @($Catalog | Sort-Object { [int]$_.Version })
    $orderedLedger = @($Ledger | Sort-Object { [int]$_.Version })

    $ledgerDuplicates = @($orderedLedger | Group-Object Version | Where-Object { $_.Count -gt 1 })
    if ($ledgerDuplicates.Count -gt 0) {
        throw "Duplicate applied migration version $($ledgerDuplicates[0].Name)."
    }

    for ($index = 0; $index -lt $orderedLedger.Count; $index++) {
        $applied = $orderedLedger[$index]
        $selected = @($orderedCatalog | Where-Object { [string]$_.Version -ceq [string]$applied.Version })
        if ($selected.Count -eq 0) {
            throw "Database contains migration $($applied.Version) that is not present in the selected branch."
        }

        if ($index -ge $orderedCatalog.Count -or [string]$orderedCatalog[$index].Version -cne [string]$applied.Version) {
            throw 'Applied migration ledger must be an ordered prefix of the selected branch catalog.'
        }

        $expected = $selected[0]
        if ([string]$applied.ScriptName -cne [string]$expected.ScriptName) {
            throw "Script name mismatch for migration $($applied.Version)."
        }
        if ([string]$applied.Description -cne [string]$expected.Description) {
            throw "Description mismatch for $($expected.ScriptName)."
        }
        if ([string]$applied.Checksum -cne [string]$expected.Checksum) {
            throw "Checksum mismatch for $($expected.ScriptName)."
        }
    }

    if ($orderedLedger.Count -eq 0) {
        return @($orderedCatalog)
    }

    return @($orderedCatalog | Select-Object -Skip $orderedLedger.Count)
}

function Get-OakvedDatabaseName {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true, Position = 0)]
        [ValidateNotNullOrEmpty()]
        [string]$RuntimeId
    )

    if ($RuntimeId -cnotmatch '^[a-z0-9_]+$') {
        throw 'RuntimeId must contain only lowercase letters, digits, and underscores.'
    }

    $databaseName = "oakved_$RuntimeId"
    if ($databaseName.Length -gt 64) {
        throw 'Branch database name exceeds the MySQL identifier limit.'
    }

    return $databaseName
}

function ConvertTo-OakvedSqlLiteral {
    param(
        [AllowEmptyString()]
        [string]$Value
    )

    return ([string]$Value).Replace("'", "''")
}

function ConvertFrom-OakvedMigrationLedgerOutput {
    param(
        [AllowEmptyCollection()]
        [object[]]$Output
    )

    $rows = New-Object 'System.Collections.Generic.List[object]'
    foreach ($item in @($Output)) {
        if ($null -eq $item) {
            continue
        }

        $versionProperty = $item.PSObject.Properties['Version']
        if ($null -ne $versionProperty) {
            $checksumProperty = $item.PSObject.Properties['Checksum']
            if ($null -eq $checksumProperty) {
                $checksumProperty = $item.PSObject.Properties['checksum_sha256']
            }
            $scriptNameProperty = $item.PSObject.Properties['ScriptName']
            if ($null -eq $scriptNameProperty) {
                $scriptNameProperty = $item.PSObject.Properties['script_name']
            }
            $descriptionProperty = $item.PSObject.Properties['Description']

            $rows.Add([pscustomobject]@{
                Version = [string]$versionProperty.Value
                ScriptName = [string]$scriptNameProperty.Value
                Description = [string]$descriptionProperty.Value
                Checksum = [string]$checksumProperty.Value
            })
            continue
        }

        foreach ($line in ([string]$item -split "`r?`n")) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }

            $fields = @($line -split "`t", 4)
            if ($fields.Count -ne 4) {
                throw 'Migration ledger query returned an invalid row.'
            }
            $rows.Add([pscustomobject]@{
                Version = $fields[0]
                ScriptName = $fields[1]
                Description = $fields[2]
                Checksum = $fields[3]
            })
        }
    }

    return $rows.ToArray()
}

function ConvertTo-OakvedNativeArgument {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Argument
    )

    if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') {
        return $Argument
    }

    $builder = New-Object Text.StringBuilder
    $null = $builder.Append('"')
    $backslashes = 0
    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq [char]92) {
            $backslashes++
            continue
        }

        if ($character -eq [char]34) {
            if ($backslashes -gt 0) {
                $null = $builder.Append([char]92, $backslashes * 2)
            }
            $null = $builder.Append([char]92)
            $null = $builder.Append([char]34)
        }
        else {
            if ($backslashes -gt 0) {
                $null = $builder.Append([char]92, $backslashes)
            }
            $null = $builder.Append($character)
        }
        $backslashes = 0
    }

    if ($backslashes -gt 0) {
        $null = $builder.Append([char]92, $backslashes * 2)
    }
    $null = $builder.Append('"')
    return $builder.ToString()
}

function New-OakvedProcessStartInfo {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Spec
    )

    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = [string]$Spec.FileName
    $startInfo.Arguments = (@($Spec.Arguments | ForEach-Object { ConvertTo-OakvedNativeArgument -Argument ([string]$_) }) -join ' ')
    if ($null -ne $Spec.PSObject.Properties['WorkingDirectory']) {
        $startInfo.WorkingDirectory = [string]$Spec.WorkingDirectory
    }
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    # Windows PowerShell 5.1 can receive both Path and PATH from a host. The
    # ProcessStartInfo getter uses a case-insensitive dictionary and throws
    # while materializing that environment, so construct its backing store
    # explicitly and collapse case-only duplicates before adding secrets.
    $childEnvironment = New-Object Collections.Specialized.StringDictionary
    foreach ($entry in [Environment]::GetEnvironmentVariables().GetEnumerator()) {
        $environmentKey = [string]$entry.Key
        $childEnvironment[$environmentKey] = [string]$entry.Value
    }
    foreach ($entry in $Spec.Environment.GetEnumerator()) {
        $environmentKey = [string]$entry.Key
        $environmentValue = [string]$entry.Value
        $childEnvironment[$environmentKey] = $environmentValue
    }
    $environmentField = $startInfo.GetType().GetField('environmentVariables', [Reflection.BindingFlags]'Instance,NonPublic')
    $environmentField.SetValue($startInfo, $childEnvironment)
    return $startInfo
}

function Invoke-OakvedNativeProcess {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Spec,

        [string]$InputPath,

        [string]$OutputPath,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 300000,

        [scriptblock]$NativeProcessStopper
    )

    $process = New-Object Diagnostics.Process
    $process.StartInfo = New-OakvedProcessStartInfo -Spec $Spec
    $stdoutMemory = New-Object IO.MemoryStream
    $stderrMemory = New-Object IO.MemoryStream
    $outputStream = $null
    $inputStream = $null
    $stdoutTask = $null
    $stderrTask = $null
    $inputTask = $null
    $started = $false
    $disposeProcess = $true
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        if (-not $process.Start()) {
            throw 'Native process could not be started.'
        }
        $started = $true

        if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
            $outputStream = New-Object IO.FileStream($OutputPath, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
            $stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($outputStream)
        }
        else {
            $stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutMemory)
        }
        $stderrTask = $process.StandardError.BaseStream.CopyToAsync($stderrMemory)

        if (-not [string]::IsNullOrWhiteSpace($InputPath)) {
            $inputStream = New-Object IO.FileStream($InputPath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
            $inputTask = $inputStream.CopyToAsync($process.StandardInput.BaseStream)
            $remaining = $TimeoutMilliseconds - [int]$stopwatch.ElapsedMilliseconds
            if ($remaining -le 0 -or -not $inputTask.Wait($remaining)) {
                throw (New-Object TimeoutException("Native process timed out after $TimeoutMilliseconds milliseconds."))
            }
            $null = $inputTask.GetAwaiter().GetResult()
        }
        $process.StandardInput.Close()
        $remaining = $TimeoutMilliseconds - [int]$stopwatch.ElapsedMilliseconds
        if ($remaining -le 0 -or -not $process.WaitForExit($remaining)) {
            throw (New-Object TimeoutException("Native process timed out after $TimeoutMilliseconds milliseconds."))
        }

        foreach ($task in @($stdoutTask, $stderrTask)) {
            if ($task.IsCompleted) {
                $null = $task.GetAwaiter().GetResult()
                continue
            }
            $remaining = $TimeoutMilliseconds - [int]$stopwatch.ElapsedMilliseconds
            if ($remaining -le 0 -or -not $task.Wait($remaining)) {
                throw (New-Object TimeoutException("Native process timed out after $TimeoutMilliseconds milliseconds."))
            }
            $null = $task.GetAwaiter().GetResult()
        }
        if ($null -ne $outputStream) {
            $outputStream.Flush()
            $outputStream.Dispose()
            $outputStream = $null
        }

        if ($process.ExitCode -ne 0 -and -not [string]::IsNullOrWhiteSpace($OutputPath)) {
            Remove-Item -LiteralPath $OutputPath -Force -ErrorAction SilentlyContinue
        }

        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            StdOut = [byte[]]$stdoutMemory.ToArray()
            StdErr = [byte[]]$stderrMemory.ToArray()
        }
    }
    catch {
        $primaryFailure = $_
        $cleanupFailure = $null
        if ($started) {
            try {
                if ($null -ne $NativeProcessStopper) {
                    & $NativeProcessStopper $process 5000
                }
                else {
                    Stop-OakvedNativeProcess -Process $process -ReapTimeoutMilliseconds 5000
                }
            }
            catch {
                $cleanupFailure = $_
                try { $disposeProcess = [bool]$process.HasExited } catch { $disposeProcess = $false }
            }
        }
        foreach ($task in @($inputTask, $stdoutTask, $stderrTask)) {
            if ($null -ne $task -and -not $task.IsCompleted) {
                try { $null = $task.Wait(5000) } catch { }
            }
        }
        if ($null -ne $outputStream) {
            $outputStream.Dispose()
            $outputStream = $null
        }
        if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
            Remove-Item -LiteralPath $OutputPath -Force -ErrorAction SilentlyContinue
        }
        if ($null -ne $cleanupFailure) {
            throw (New-Object InvalidOperationException("$($primaryFailure.Exception.Message) Native process cleanup also failed: $($cleanupFailure.Exception.Message)"))
        }
        throw $primaryFailure
    }
    finally {
        $stopwatch.Stop()
        if ($null -ne $inputStream) { $inputStream.Dispose() }
        if ($null -ne $outputStream) { $outputStream.Dispose() }
        $stdoutMemory.Dispose()
        $stderrMemory.Dispose()
        if ($disposeProcess) { $process.Dispose() }
    }
}

function Stop-OakvedNativeProcess {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Process,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$ReapTimeoutMilliseconds = 5000
    )

    $terminationFailed = $false
    $reapFailed = $false
    $statusFailed = $false
    $hasExited = $false

    try {
        $hasExited = [bool]$Process.HasExited
    }
    catch {
        $statusFailed = $true
    }

    if (-not $hasExited) {
        try {
            $killTreeMethod = $Process.GetType().GetMethod('Kill', [type[]]@([bool]))
            if ($null -ne $killTreeMethod) {
                $null = $killTreeMethod.Invoke($Process, @($true))
            }
            else {
                $Process.Kill()
            }
        }
        catch {
            $terminationFailed = $true
        }
    }

    try {
        if (-not [bool]$Process.WaitForExit($ReapTimeoutMilliseconds)) {
            $reapFailed = $true
        }
    }
    catch {
        $reapFailed = $true
    }

    $stillRunning = $true
    try {
        $stillRunning = -not [bool]$Process.HasExited
    }
    catch {
        $statusFailed = $true
    }

    if ($terminationFailed -or $reapFailed -or $statusFailed -or $stillRunning) {
        $reasons = New-Object Collections.Generic.List[string]
        if ($terminationFailed) { $reasons.Add('termination failed') }
        if ($reapFailed) { $reasons.Add('process was not reaped') }
        if ($statusFailed) { $reasons.Add('process exit status could not be confirmed') }
        if ($stillRunning) { $reasons.Add('process is still running') }
        throw (New-Object InvalidOperationException("Native process cleanup failed: $([string]::Join('; ', $reasons))."))
    }
}

function New-OakvedDockerProcessSpec {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$RootPassword
    )

    return [pscustomobject]@{
        FileName = 'docker'
        Arguments = @($Arguments)
        Environment = @{ MYSQL_PWD = $RootPassword }
    }
}

function Invoke-OakvedProcessRunner {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Spec,

        [string]$InputPath,

        [string]$OutputPath,

        [scriptblock]$NativeProcessRunner,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 300000
    )

    if ($null -ne $NativeProcessRunner) {
        return & $NativeProcessRunner $Spec $InputPath $OutputPath $TimeoutMilliseconds
    }
    return Invoke-OakvedNativeProcess -Spec $Spec -InputPath $InputPath -OutputPath $OutputPath -TimeoutMilliseconds $TimeoutMilliseconds
}

function Invoke-OakvedDockerMySql {
    param(
        [string]$Database,

        [Parameter(Mandatory = $true)]
        [string]$Sql,

        [Parameter(Mandatory = $true)]
        [string]$RootPassword,

        [string]$Container = 'yudao-mysql-local',

        [scriptblock]$NativeProcessRunner,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 300000
    )

    $arguments = @(
        'exec', '-i', '-e', 'MYSQL_PWD', $Container,
        'mysql', '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
        '-uroot'
    )
    if (-not [string]::IsNullOrWhiteSpace($Database)) {
        $arguments += $Database
    }
    $arguments += @('-e', $Sql)

    $spec = New-OakvedDockerProcessSpec -Arguments $arguments -RootPassword $RootPassword
    $nativeResult = Invoke-OakvedProcessRunner -Spec $spec -NativeProcessRunner $NativeProcessRunner -TimeoutMilliseconds $TimeoutMilliseconds
    if ([int]$nativeResult.ExitCode -ne 0) {
        throw "MySQL command failed in Docker container $Container."
    }

    return (New-Object Text.UTF8Encoding($false, $true)).GetString([byte[]]$nativeResult.StdOut)
}

function Invoke-OakvedSqlFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Database,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$RootPassword,

        [string]$Container = 'yudao-mysql-local',

        [scriptblock]$NativeProcessRunner,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 300000
    )

    $arguments = @(
        'exec', '-i', '-e', 'MYSQL_PWD', $Container,
        'mysql', '--default-character-set=utf8mb4', '-uroot', $Database
    )
    $spec = New-OakvedDockerProcessSpec -Arguments $arguments -RootPassword $RootPassword
    $nativeResult = Invoke-OakvedProcessRunner -Spec $spec -InputPath $Path -NativeProcessRunner $NativeProcessRunner -TimeoutMilliseconds $TimeoutMilliseconds
    if ([int]$nativeResult.ExitCode -ne 0) {
        throw "SQL file execution failed for $Path."
    }

    return (New-Object Text.UTF8Encoding($false, $true)).GetString([byte[]]$nativeResult.StdOut)
}

function Backup-OakvedDatabase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Database,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$RootPassword,

        [string]$Container = 'yudao-mysql-local',

        [scriptblock]$NativeProcessRunner,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 300000
    )

    $arguments = @(
        'exec', '-i', '-e', 'MYSQL_PWD', $Container,
        'mysqldump', '--default-character-set=utf8mb4', '--single-transaction', '--routines', '--triggers', '--hex-blob',
        '-uroot', $Database
    )
    $spec = New-OakvedDockerProcessSpec -Arguments $arguments -RootPassword $RootPassword
    try {
        $nativeResult = Invoke-OakvedProcessRunner -Spec $spec -OutputPath $Path -NativeProcessRunner $NativeProcessRunner -TimeoutMilliseconds $TimeoutMilliseconds
        if ([int]$nativeResult.ExitCode -ne 0) {
            throw 'Native dump failed.'
        }
    }
    catch {
        if (Test-Path -LiteralPath $Path) {
            Remove-Item -LiteralPath $Path -Force
        }
        throw "Database backup failed for $Database."
    }
}

function Start-OakvedNativeSession {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Spec,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 35000,

        [scriptblock]$NativeProcessStopper
    )

    $process = New-Object Diagnostics.Process
    $process.StartInfo = New-OakvedProcessStartInfo -Spec $Spec
    if (-not $process.Start()) {
        $process.Dispose()
        throw 'Native session could not be started.'
    }
    $process.StandardInput.AutoFlush = $true
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $state = [pscustomobject]@{
        Disposed = $false
        CleanupFailed = $false
    }
    $stopProcess = ${function:Stop-OakvedNativeProcess}

    $terminate = {
        if ($state.Disposed) {
            return
        }
        if ($state.CleanupFailed) {
            throw 'Native session cleanup previously failed.'
        }
        try { $process.StandardInput.BaseStream.Close() } catch { }
        try {
            if ($null -ne $NativeProcessStopper) {
                & $NativeProcessStopper $process 5000
            }
            else {
                & $stopProcess -Process $process -ReapTimeoutMilliseconds 5000
            }
        }
        catch {
            $cleanupFailure = $_
            $exitConfirmed = $false
            try { $exitConfirmed = [bool]$process.HasExited } catch { }
            if (-not $exitConfirmed) {
                $state.CleanupFailed = $true
                throw $cleanupFailure
            }
            if (-not $stderrTask.IsCompleted) {
                try { $null = $stderrTask.Wait($TimeoutMilliseconds) } catch { }
            }
            if ($stderrTask.IsCompleted) {
                try { $null = $stderrTask.GetAwaiter().GetResult() } catch { }
            }
            $process.Dispose()
            $state.Disposed = $true
            $state.CleanupFailed = $false
            throw $cleanupFailure
        }
        if (-not $stderrTask.IsCompleted) {
            try { $null = $stderrTask.Wait($TimeoutMilliseconds) } catch { }
        }
        if ($stderrTask.IsCompleted) {
            try { $null = $stderrTask.GetAwaiter().GetResult() } catch { }
        }
        $process.Dispose()
        $state.Disposed = $true
    }.GetNewClosure()

    $failAfterCleanup = {
        param([Exception]$PrimaryFailure)
        try {
            & $terminate
        }
        catch {
            throw (New-Object InvalidOperationException("$($PrimaryFailure.Message) Native session cleanup also failed: $($_.Exception.Message)"))
        }
        throw $PrimaryFailure
    }.GetNewClosure()

    $assertActive = {
        if ($state.CleanupFailed) {
            throw 'Native session cleanup previously failed.'
        }
        if ($state.Disposed) {
            throw 'Native session is not active.'
        }
        try {
            if ($process.HasExited) {
                throw 'Native session is not active.'
            }
        }
        catch {
            if ($_.Exception.Message -eq 'Native session is not active.') { throw }
            throw 'Native session is not active.'
        }
    }.GetNewClosure()

    $writeLine = {
        param($line)
        & $assertActive
        $writeTask = $null
        try {
            $writeTask = $process.StandardInput.WriteLineAsync([string]$line)
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session write failed.'))
        }
        $writeCompleted = $false
        try {
            $writeCompleted = [bool]$writeTask.Wait($TimeoutMilliseconds)
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session write failed.'))
        }
        if (-not $writeCompleted) {
            & $failAfterCleanup (New-Object TimeoutException("Native session write timed out after $TimeoutMilliseconds milliseconds."))
        }
        try {
            $null = $writeTask.GetAwaiter().GetResult()
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session write failed.'))
        }
    }.GetNewClosure()
    $readLine = {
        & $assertActive
        $readTask = $null
        try {
            $readTask = $process.StandardOutput.ReadLineAsync()
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session read failed.'))
        }
        $readCompleted = $false
        try {
            $readCompleted = [bool]$readTask.Wait($TimeoutMilliseconds)
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session read failed.'))
        }
        if (-not $readCompleted) {
            & $failAfterCleanup (New-Object TimeoutException("Native session read timed out after $TimeoutMilliseconds milliseconds."))
        }
        try {
            return [string]$readTask.GetAwaiter().GetResult()
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session read failed.'))
        }
    }.GetNewClosure()
    $isAlive = {
        if ($state.Disposed) { return $false }
        try { return -not $process.HasExited } catch { return $state.CleanupFailed }
    }.GetNewClosure()
    $close = {
        if ($state.Disposed) {
            return
        }
        if ($state.CleanupFailed) {
            throw 'Native session cleanup previously failed.'
        }
        try { $process.StandardInput.BaseStream.Close() } catch { }

        $hasExited = $false
        try {
            $hasExited = [bool]$process.HasExited
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session close failed.'))
        }
        if (-not $hasExited) {
            $exited = $false
            try {
                $exited = [bool]$process.WaitForExit($TimeoutMilliseconds)
            }
            catch {
                & $failAfterCleanup (New-Object InvalidOperationException('Native session close failed.'))
            }
            if (-not $exited) {
                & $failAfterCleanup (New-Object TimeoutException("Native session close timed out after $TimeoutMilliseconds milliseconds."))
            }
        }
        if (-not $stderrTask.IsCompleted) {
            $stderrCompleted = $false
            try {
                $stderrCompleted = [bool]$stderrTask.Wait($TimeoutMilliseconds)
            }
            catch {
                & $failAfterCleanup (New-Object InvalidOperationException('Native session stderr drain failed.'))
            }
            if (-not $stderrCompleted) {
                & $failAfterCleanup (New-Object TimeoutException("Native session stderr drain timed out after $TimeoutMilliseconds milliseconds."))
            }
        }
        try {
            $null = $stderrTask.GetAwaiter().GetResult()
        }
        catch {
            & $failAfterCleanup (New-Object InvalidOperationException('Native session stderr drain failed.'))
        }
        $process.Dispose()
        $state.Disposed = $true
    }.GetNewClosure()

    return [pscustomobject]@{
        ProcessId = $process.Id
        State = $state
        WriteLine = $writeLine
        ReadStdOutLine = $readLine
        IsAlive = $isAlive
        Close = $close
        Terminate = $terminate
    }
}

function Open-OakvedMySqlLockLease {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Database,

        [Parameter(Mandatory = $true)]
        [string]$LockName,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$RootPassword,

        [string]$Container = 'yudao-mysql-local',

        [scriptblock]$NativeSessionFactory,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$TimeoutMilliseconds = 35000
    )

    $arguments = @(
        'exec', '-i', '-e', 'MYSQL_PWD', $Container,
        'mysql', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--unbuffered',
        '-uroot', $Database
    )
    $spec = New-OakvedDockerProcessSpec -Arguments $arguments -RootPassword $RootPassword
    $session = $null
    try {
        if ($null -ne $NativeSessionFactory) {
            $session = & $NativeSessionFactory $spec $TimeoutMilliseconds
        }
        else {
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds $TimeoutMilliseconds
        }
        & $session.WriteLine "SELECT GET_LOCK('$LockName',30);"
        $acquired = ([string](& $session.ReadStdOutLine)).Trim() -eq '1'
    }
    catch {
        $acquisitionFailure = $_
        if ($null -ne $session) {
            try {
                & $session.Terminate
            }
            catch {
                if ($_.Exception.Message -eq 'Native session cleanup previously failed.') {
                    throw (New-Object InvalidOperationException("$($acquisitionFailure.Exception.Message) Lock acquisition session remains unusable because cleanup did not complete."))
                }
                throw (New-Object InvalidOperationException("$($acquisitionFailure.Exception.Message) Lock acquisition session termination also failed."))
            }
        }
        throw $acquisitionFailure
    }

    if (-not $acquired) {
        & $session.Terminate
        return [pscustomobject]@{
            Acquired = $false
            IsAlive = { return $false }
            Release = { return $false }
            Terminate = { }
        }
    }

    $released = $false
    $release = {
        if ($released) {
            return $true
        }
        if (-not (& $session.IsAlive)) {
            return $false
        }
        & $session.WriteLine "SELECT RELEASE_LOCK('$LockName');"
        $releaseResult = ([string](& $session.ReadStdOutLine)).Trim()
        & $session.WriteLine 'quit'
        & $session.Close
        $released = $true
        return $releaseResult -eq '1'
    }.GetNewClosure()
    $isAlive = { return (-not $released) -and [bool](& $session.IsAlive) }.GetNewClosure()
    $terminateLease = { & $session.Terminate }.GetNewClosure()

    return [pscustomobject]@{
        Acquired = $true
        IsAlive = $isAlive
        Release = $release
        Terminate = $terminateLease
    }
}

function Invoke-OakvedDatabaseGate {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [object]$Target,

        [Parameter(Mandatory = $true)]
        [object]$Layout,

        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$RuntimeRoot,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$MySqlRootPassword,

        [scriptblock]$MySqlCommandProvider,

        [scriptblock]$SqlFileProvider,

        [scriptblock]$BackupProvider,

        [scriptblock]$LockLeaseProvider,

        [scriptblock]$NativeProcessRunner,

        [scriptblock]$NativeSessionFactory,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$NativeTimeoutMilliseconds = 300000,

        [ValidateRange(1, [int]::MaxValue)]
        [int]$LockSessionTimeoutMilliseconds = 35000,

        [scriptblock]$UtcNowProvider
    )

    $migrationFiles = @(Get-ChildItem -LiteralPath $Layout.Migrations -Filter 'V*.sql' -File | ForEach-Object { $_.FullName })
    $catalog = @(Get-OakvedMigrationCatalog -Files $migrationFiles -ContentProvider {
        param($path)
        [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    })
    $database = Get-OakvedDatabaseName -RuntimeId ([string]$Target.RuntimeId)

    $invokeMySql = {
        param($selectedDatabase, $sql)
        if ($null -ne $MySqlCommandProvider) {
            return @(& $MySqlCommandProvider $selectedDatabase $sql)
        }
        return @(Invoke-OakvedDockerMySql -Database $selectedDatabase -Sql $sql -RootPassword $MySqlRootPassword -NativeProcessRunner $NativeProcessRunner -TimeoutMilliseconds $NativeTimeoutMilliseconds)
    }
    $invokeSqlFile = {
        param($selectedDatabase, $path)
        if ($null -ne $SqlFileProvider) {
            return & $SqlFileProvider $selectedDatabase $path
        }
        return Invoke-OakvedSqlFile -Database $selectedDatabase -Path $path -RootPassword $MySqlRootPassword -NativeProcessRunner $NativeProcessRunner -TimeoutMilliseconds $NativeTimeoutMilliseconds
    }
    $invokeBackup = {
        param($selectedDatabase, $path)
        if ($null -ne $BackupProvider) {
            return & $BackupProvider $selectedDatabase $path
        }
        return Backup-OakvedDatabase -Database $selectedDatabase -Path $path -RootPassword $MySqlRootPassword -NativeProcessRunner $NativeProcessRunner -TimeoutMilliseconds $NativeTimeoutMilliseconds
    }

    $null = & $invokeMySql $null "CREATE DATABASE IF NOT EXISTS ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    $lockName = "oakved_schema_$database"
    if ($null -ne $LockLeaseProvider) {
        $lockLease = & $LockLeaseProvider $database $lockName
    }
    else {
        $lockLease = Open-OakvedMySqlLockLease -Database $database -LockName $lockName -RootPassword $MySqlRootPassword -NativeSessionFactory $NativeSessionFactory -TimeoutMilliseconds $LockSessionTimeoutMilliseconds
    }
    if ($null -eq $lockLease -or -not [bool]$lockLease.Acquired) {
        if ($null -ne $lockLease -and $null -ne $lockLease.Terminate) {
            & $lockLease.Terminate $lockLease
        }
        throw "Could not acquire migration lock for $database."
    }

    $assertLockAlive = {
        if ($null -eq $lockLease.IsAlive -or -not [bool](& $lockLease.IsAlive $lockLease)) {
            throw "Migration lock connection was lost for $database."
        }
    }
    $failure = $null
    $result = $null
    try {
        & $assertLockAlive
        $existsOutput = @(& $invokeMySql $database "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database' AND table_name='schema_migrations';")
        $existsText = (($existsOutput | ForEach-Object { [string]$_ }) -join "`n").Trim()
        if ([int]$existsText -eq 0) {
            try {
                $null = & $invokeSqlFile $database ([string]$Layout.Baseline)
            }
            catch {
                throw "Baseline import failed for $database. MySQL DDL may have auto-committed; operator inspection is required. $($_.Exception.Message)"
            }
        }

        & $assertLockAlive
        $ledgerSql = 'SELECT version, script_name, description, checksum_sha256 FROM schema_migrations ORDER BY version;'
        $ledger = @(ConvertFrom-OakvedMigrationLedgerOutput -Output @(& $invokeMySql $database $ledgerSql))
        $pending = @(Compare-OakvedMigrationLedger -Catalog $catalog -Ledger $ledger)
        $backupPath = $null
        $appliedCount = 0

        if ($pending.Count -gt 0) {
            if ($null -ne $UtcNowProvider) {
                $utcNow = [datetime](& $UtcNowProvider)
            }
            else {
                $utcNow = [datetime]::UtcNow
            }
            $utcNow = $utcNow.ToUniversalTime()
            $backupDirectory = Join-Path $RuntimeRoot "backups\$database"
            New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
            $backupPath = Join-Path $backupDirectory ($utcNow.ToString('yyyyMMddTHHmmssZ') + '.sql')

            & $assertLockAlive
            $null = & $invokeBackup $database $backupPath
            try {
                if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
                    throw "Database backup validation failed: $backupPath was not created."
                }
                $backupFile = Get-Item -LiteralPath $backupPath
                try {
                    $backupContent = if ($backupFile.Length -gt 0) {
                        [IO.File]::ReadAllText($backupPath, (New-Object Text.UTF8Encoding($false, $true)))
                    }
                    else { '' }
                }
                catch {
                    throw "Database backup validation failed: $backupPath is not valid UTF-8 SQL."
                }
                if ($backupFile.Length -eq 0 -or $backupContent -notmatch '(?im)\b(CREATE TABLE|INSERT INTO)\b') {
                    throw "Database backup validation failed: $backupPath is empty or contains no table/data statements."
                }
            }
            catch {
                $validationFailure = $_
                Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
                throw $validationFailure
            }

            foreach ($migration in $pending) {
                & $assertLockAlive
                try {
                    $null = & $invokeSqlFile $database ([string]$migration.Path)
                }
                catch {
                    throw "Migration $($migration.ScriptName) failed. MySQL DDL may have auto-committed; operator inspection is required."
                }

                $version = ConvertTo-OakvedSqlLiteral ([string]$migration.Version)
                $description = ConvertTo-OakvedSqlLiteral ([string]$migration.Description)
                $scriptName = ConvertTo-OakvedSqlLiteral ([string]$migration.ScriptName)
                $checksum = ConvertTo-OakvedSqlLiteral ([string]$migration.Checksum)
                $insertSql = "INSERT INTO schema_migrations (version, description, script_name, checksum_sha256) VALUES ('$version','$description','$scriptName','$checksum');"
                $null = & $invokeMySql $database $insertSql
                $appliedCount++
            }
        }

        & $assertLockAlive
        $finalLedger = @(ConvertFrom-OakvedMigrationLedgerOutput -Output @(& $invokeMySql $database $ledgerSql))
        $remaining = @(Compare-OakvedMigrationLedger -Catalog $catalog -Ledger $finalLedger)
        if ($remaining.Count -ne 0 -or $finalLedger.Count -ne $catalog.Count) {
            throw 'Final migration ledger does not match selected branch catalog.'
        }

        $version = $null
        $catalogVersion = $null
        if ($finalLedger.Count -gt 0) {
            $version = [string]$finalLedger[-1].Version
        }
        if ($catalog.Count -gt 0) {
            $catalogVersion = [string]$catalog[-1].Version
        }
        $result = [pscustomobject]@{
            Name = $database
            Version = $version
            CatalogVersion = $catalogVersion
            BackupPath = $backupPath
            AppliedCount = $appliedCount
        }
    }
    catch {
        $failure = $_
    }
    finally {
        try {
            if ($null -eq $lockLease.Release -or -not [bool](& $lockLease.Release $lockLease)) {
                throw "Could not release migration lock for $database."
            }
        }
        catch {
            if ($null -eq $failure) {
                $failure = $_
            }
            else {
                $failure = New-Object Management.Automation.ErrorRecord(
                    (New-Object InvalidOperationException("$($failure.Exception.Message) Migration lock release also failed: $($_.Exception.Message)")),
                    'OakvedMigrationAndReleaseFailure',
                    [Management.Automation.ErrorCategory]::OperationStopped,
                    $database
                )
            }
        }
        finally {
            try {
                if ($null -ne $lockLease.Terminate) {
                    & $lockLease.Terminate $lockLease
                }
            }
            catch {
                if ($null -eq $failure) {
                    $failure = $_
                }
                else {
                    $failure = New-Object Management.Automation.ErrorRecord(
                        (New-Object InvalidOperationException("$($failure.Exception.Message) Migration lock session termination also failed.")),
                        'OakvedMigrationAndTerminationFailure',
                        [Management.Automation.ErrorCategory]::OperationStopped,
                        $database
                    )
                }
            }
        }
    }

    if ($null -ne $failure) {
        throw $failure
    }

    return $result
}

function Get-OakvedBuildFingerprint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [object]$Target,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$CatalogVersion,

        [System.Collections.IDictionary]$RelevantFiles,

        [object]$Layout,

        [scriptblock]$ContentProvider
    )

    if ($null -eq $RelevantFiles) {
        if ($null -eq $Layout) {
            throw 'Layout is required when RelevantFiles is not supplied.'
        }
        $RelevantFiles = [ordered]@{
            Backend = @(
                Get-ChildItem -LiteralPath $Layout.YudaoCloud -Filter 'pom.xml' -File -Recurse -Force |
                    ForEach-Object { $_.FullName }
                [string]$Layout.MavenJdk17
            )
            Admin = @(
                Join-Path $Layout.AdminUi 'package.json'
                Join-Path $Layout.AdminUi 'pnpm-lock.yaml'
            )
            Storefront = @(
                Join-Path $Layout.FurnitureWeb 'package.json'
                Join-Path $Layout.FurnitureWeb 'package-lock.json'
            )
        }
    }

    $readContent = {
        param([string]$path)
        if ($null -ne $ContentProvider) {
            return [string](& $ContentProvider $path)
        }
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            return '<missing>'
        }
        return [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    }
    $common = "commit=$([string]$Target.Commit)`ndirty=$([bool]$Target.Dirty)`ncatalog=$CatalogVersion"
    $components = [ordered]@{}
    foreach ($component in @('Backend', 'Admin', 'Storefront')) {
        $lines = New-Object 'System.Collections.Generic.List[string]'
        $lines.Add($common)
        if ($RelevantFiles.Contains($component)) {
            foreach ($path in @($RelevantFiles[$component] | Sort-Object)) {
                $lines.Add("path=$path")
                $lines.Add("content=$(& $readContent ([string]$path))")
            }
        }
        $components[$component] = Get-OakvedSha256 -Value ($lines -join "`n")
    }
    $value = Get-OakvedSha256 -Value (($components.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n")
    return [pscustomobject]@{
        Value = $value
        Backend = $components.Backend
        Admin = $components.Admin
        Storefront = $components.Storefront
        Commit = [string]$Target.Commit
        Dirty = [bool]$Target.Dirty
        CatalogVersion = $CatalogVersion
    }
}

function Get-OakvedDefaultListeners {
    return @(Get-NetTCPConnection -State Listen -ErrorAction Stop | ForEach-Object {
        [pscustomobject]@{ Port = [int]$_.LocalPort; Pid = [int]$_.OwningProcess }
    })
}

function Get-OakvedDefaultProcessTree {
    param([int]$RootPid)
    $all = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $result = New-Object 'System.Collections.Generic.List[int]'
    $pending = New-Object 'System.Collections.Generic.Queue[int]'
    $pending.Enqueue($RootPid)
    while ($pending.Count -gt 0) {
        $pidValue = $pending.Dequeue()
        if ($pidValue -in $result) { continue }
        $result.Add($pidValue)
        foreach ($child in @($all | Where-Object { [int]$_.ParentProcessId -eq $pidValue })) {
            $pending.Enqueue([int]$child.ProcessId)
        }
    }
    return $result.ToArray()
}

function Assert-OakvedPortsAvailable {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [int[]]$Ports,

        [scriptblock]$ListenerProvider,

        [int[]]$ManagedPids = @(),

        [scriptblock]$ProcessTreeProvider
    )

    if ($null -eq $ListenerProvider) {
        $ListenerProvider = { Get-OakvedDefaultListeners }
    }
    if ($null -eq $ProcessTreeProvider) {
        $ProcessTreeProvider = { param($id) Get-OakvedDefaultProcessTree -RootPid $id }
    }
    $managedTree = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($managedPid in @($ManagedPids)) {
        foreach ($treePid in @(& $ProcessTreeProvider ([int]$managedPid))) {
            $null = $managedTree.Add([int]$treePid)
        }
    }
    foreach ($listener in @(& $ListenerProvider)) {
        if ([int]$listener.Port -in $Ports -and -not $managedTree.Contains([int]$listener.Pid)) {
            throw "Port $($listener.Port) is owned by unmanaged PID $($listener.Pid)."
        }
    }
}

function Write-OakvedManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [object]$Manifest,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [scriptblock]$Writer,

        [scriptblock]$Mover
    )
    $temporaryPath = "$Path.tmp"
    $json = $Manifest | ConvertTo-Json -Depth 12
    if ($null -ne $Writer) {
        & $Writer $temporaryPath $json
    }
    else {
        $directory = Split-Path -Parent $Path
        if (-not [string]::IsNullOrWhiteSpace($directory)) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }
        [IO.File]::WriteAllText($temporaryPath, $json, (New-Object Text.UTF8Encoding($false)))
    }
    if ($null -ne $Mover) {
        & $Mover $temporaryPath $Path
    }
    else {
        Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
    }
}

function Start-OakvedManagedProcess {
    param([object]$Spec)
    $isVisible = $null -ne $Spec.PSObject.Properties['Visible'] -and [bool]$Spec.Visible
    $toLiteral = {
        param([AllowEmptyString()][string]$Value)
        return "'$($Value.Replace("'", "''"))'"
    }
    $fileLiteral = & $toLiteral ([string]$Spec.FilePath)
    $argumentLiteralItems = @($Spec.Arguments | ForEach-Object { & $toLiteral ([string]$_) })
    $argumentLiterals = $argumentLiteralItems -join ', '
    $stdoutLiteral = & $toLiteral ([string]$Spec.StdOutLog)
    $stderrLiteral = & $toLiteral ([string]$Spec.StdErrLog)
    $environmentLines = @($Spec.Environment.GetEnumerator() | ForEach-Object {
            $nameLiteral = & $toLiteral ([string]$_.Key)
            $valueLiteral = & $toLiteral ([string]$_.Value)
            "[Environment]::SetEnvironmentVariable($nameLiteral, $valueLiteral, 'Process')"
        }) -join "`r`n"
    if ($isVisible) {
        $roleLiteral = & $toLiteral ([string]$Spec.Role)
        $managedCommand = @"
$environmentLines
[Console]::Title = 'Oakved - ' + $roleLiteral
[IO.File]::WriteAllText($stdoutLiteral, '', (New-Object Text.UTF8Encoding(`$false)))
[IO.File]::WriteAllText($stderrLiteral, '', (New-Object Text.UTF8Encoding(`$false)))
Write-Host ('[Oakved] Starting ' + $roleLiteral) -ForegroundColor Cyan
& $fileLiteral @($argumentLiterals) 2>&1 | ForEach-Object {
    Write-Host `$_
    Add-Content -LiteralPath $stdoutLiteral -Value ([string]`$_) -Encoding UTF8
}
`$exitCode = `$LASTEXITCODE
if (`$exitCode -ne 0) {
    Write-Host ('[Oakved] ' + $roleLiteral + ' exited with code ' + `$exitCode) -ForegroundColor Red
    Read-Host 'Press Enter to close this window'
}
exit `$exitCode
"@
    }
    else {
        $managedCommand = "$environmentLines`r`n& $fileLiteral @($argumentLiterals) 1>> $stdoutLiteral 2>> $stderrLiteral`r`nexit `$LASTEXITCODE"
    }
    $encodedManagedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($managedCommand))
    if ($isVisible) {
        $process = Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encodedManagedCommand) `
            -WorkingDirectory ([string]$Spec.WorkingDirectory) -PassThru -WindowStyle Normal
    }
    else {
        $startInfo = New-OakvedProcessStartInfo -Spec ([pscustomobject]@{
            FileName = 'powershell.exe'; Arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encodedManagedCommand)
            WorkingDirectory = [string]$Spec.WorkingDirectory; Environment = @{}
        })
        $startInfo.RedirectStandardInput = $false
        $startInfo.RedirectStandardOutput = $false
        $startInfo.RedirectStandardError = $false
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $startInfo
        if (-not $process.Start()) { throw "Unable to start $($Spec.Role)." }
    }
    $result = [pscustomobject]@{ Id = $process.Id; StartTime = $process.StartTime.ToUniversalTime() }
    $process.Dispose()
    return $result
}

function Stop-OakvedManagedProcess {
    param([int]$Pid)
    if ($null -eq (Get-Process -Id $Pid -ErrorAction SilentlyContinue)) { return }
    $process = Start-Process -FilePath 'taskkill.exe' -ArgumentList @('/PID', [string]$Pid, '/T', '/F') -Wait -PassThru -WindowStyle Hidden
    if ($process.ExitCode -ne 0 -and $null -ne (Get-Process -Id $Pid -ErrorAction SilentlyContinue)) {
        throw "Could not stop managed PID $Pid."
    }
}

function Invoke-OakvedHealthRequest {
    param([string]$Url)
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = [string]$response.Content }
}

function Test-OakvedHttpHealth {
    param([scriptblock]$HttpProvider)
    $health = [ordered]@{ Backend = $false; Admin = $false; Storefront = $false }
    try {
        # Readiness must reflect the ERP login path, not the aggregate Actuator
        # state. Optional integrations may make /actuator/health report DOWN
        # while the database-backed application API is fully usable.
        $backend = & $HttpProvider 'http://127.0.0.1:48080/admin-api/system/tenant/get-id-by-name?name=%E8%8A%8B%E9%81%93%E6%BA%90%E7%A0%81'
        $health.Backend = [int]$backend.StatusCode -eq 200 `
            -and [string]$backend.Content -match '"code"\s*:\s*0' `
            -and [string]$backend.Content -match '"data"\s*:\s*\d+'
    }
    catch { $health.Backend = $false }
    try {
        $admin = & $HttpProvider 'http://127.0.0.1:80/'
        $health.Admin = [int]$admin.StatusCode -eq 200
    }
    catch { $health.Admin = $false }
    try {
        $storefront = & $HttpProvider 'http://127.0.0.1:5173/'
        $health.Storefront = [int]$storefront.StatusCode -eq 200
    }
    catch { $health.Storefront = $false }
    return [pscustomobject]$health
}

function Stop-OakvedRuntime {
    [CmdletBinding(DefaultParameterSetName = 'Path')]
    param(
        [Parameter(Mandatory = $true, ParameterSetName = 'Manifest')]
        [object]$Manifest,

        [Parameter(Mandatory = $true, ParameterSetName = 'Path')]
        [string]$ManifestPath,

        [scriptblock]$ProcessProvider,

        [scriptblock]$Stopper
    )
    if ($PSCmdlet.ParameterSetName -eq 'Path') {
        if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
            return [pscustomobject]@{ Stopped = @(); Skipped = @(); RemovedManifest = $false }
        }
        try { $Manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json }
        catch { throw "Runtime manifest is corrupt: $ManifestPath" }
    }
    if ($null -eq $ProcessProvider) {
        $ProcessProvider = { param($id) Get-Process -Id $id -ErrorAction SilentlyContinue }
    }
    if ($null -eq $Stopper) {
        $Stopper = { param($id) Stop-OakvedManagedProcess -Pid $id }
    }
    $stopped = New-Object 'System.Collections.Generic.List[int]'
    $skipped = New-Object 'System.Collections.Generic.List[int]'
    foreach ($record in @($Manifest.Processes | Sort-Object { [int]$_.Pid } -Descending)) {
        $current = & $ProcessProvider ([int]$record.Pid)
        if ($null -eq $current) { continue }
        try {
            $actual = ([datetime]$current.StartTime).ToUniversalTime().Ticks
            $expected = ([datetime]$record.StartTime).ToUniversalTime().Ticks
        }
        catch {
            $skipped.Add([int]$record.Pid)
            continue
        }
        if ($actual -ne $expected) {
            $skipped.Add([int]$record.Pid)
            continue
        }
        & $Stopper ([int]$record.Pid)
        $stopped.Add([int]$record.Pid)
    }
    $removed = $false
    if ($PSCmdlet.ParameterSetName -eq 'Path' -and $skipped.Count -eq 0) {
        Remove-Item -LiteralPath $ManifestPath -Force
        $removed = $true
    }
    return [pscustomobject]@{ Stopped = $stopped.ToArray(); Skipped = $skipped.ToArray(); RemovedManifest = $removed }
}

function Start-OakvedRuntime {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object]$Target,
        [Parameter(Mandatory = $true)][object]$Layout,
        [Parameter(Mandatory = $true)][string]$RuntimeRoot,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$MySqlRootPassword,
        [scriptblock]$DatabaseGateProvider,
        [scriptblock]$BuildFingerprintProvider,
        [scriptblock]$BuildProvider,
        [scriptblock]$ProcessStarter,
        [scriptblock]$ProcessProvider,
        [scriptblock]$ProcessTreeProvider,
        [scriptblock]$ListenerProvider,
        [scriptblock]$HttpProvider,
        [scriptblock]$Stopper,
        [scriptblock]$SleepProvider,
        [scriptblock]$UtcNowProvider,
        [scriptblock]$ManifestWriter,
        [switch]$VisibleProcesses,
        [ValidateRange(1, [int]::MaxValue)][int]$HealthTimeoutMilliseconds = 180000
    )
    $fixedPorts = @(80, 5173, 48080)
    $runtimeRootPath = ConvertTo-OakvedNormalizedPath -Path $RuntimeRoot
    $manifestPath = Join-Path $runtimeRootPath 'runtime.json'
    if (Test-Path -LiteralPath $manifestPath -PathType Leaf) {
        throw "A runtime manifest already exists at $manifestPath. Stop or investigate it first."
    }
    if (-not [string]::Equals((ConvertTo-OakvedNormalizedPath -Path ([string]$Target.Worktree)), (ConvertTo-OakvedNormalizedPath -Path ([string]$Layout.Worktree)), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Target and layout worktrees do not match.'
    }
    foreach ($selectedPath in @($Layout.YudaoCloud, $Layout.AdminUi, $Layout.FurnitureWeb, $Layout.Migrations, $Layout.MavenJdk17, $Layout.ServerJar)) {
        if ([string]::IsNullOrWhiteSpace([string]$selectedPath) -or -not (Test-OakvedPathWithinRoot -Path ([string]$selectedPath) -Root ([string]$Target.Worktree))) {
            throw 'Selected runtime path is outside the resolved worktree.'
        }
    }
    New-Item -ItemType Directory -Path $runtimeRootPath -Force | Out-Null
    $logRoot = Join-Path $runtimeRootPath 'logs'
    $cacheRoot = Join-Path $runtimeRootPath "cache\$($Target.RuntimeId)"
    New-Item -ItemType Directory -Path $logRoot, $cacheRoot -Force | Out-Null
    Assert-OakvedPortsAvailable -Ports $fixedPorts -ListenerProvider $ListenerProvider -ManagedPids @() -ProcessTreeProvider $ProcessTreeProvider

    if ($VisibleProcesses) { Write-Host '[1/5] Checking database and migrations...' -ForegroundColor Cyan }
    if ($null -ne $DatabaseGateProvider) {
        $database = & $DatabaseGateProvider $Target $Layout $runtimeRootPath $MySqlRootPassword
    }
    else {
        $database = Invoke-OakvedDatabaseGate -Target $Target -Layout $Layout -RuntimeRoot $runtimeRootPath -MySqlRootPassword $MySqlRootPassword
    }
    if ([string]$database.Version -cne [string]$database.CatalogVersion) {
        throw 'Database ledger version does not match selected catalog version.'
    }
    if ($VisibleProcesses) { Write-Host "[1/5] Database ready: $($database.Name) (V$($database.Version))" -ForegroundColor Green }
    if ($null -ne $BuildFingerprintProvider) {
        $fingerprint = & $BuildFingerprintProvider $Target $Layout $database
    }
    else {
        $fingerprint = Get-OakvedBuildFingerprint -Target $Target -Layout $Layout -CatalogVersion ([string]$database.CatalogVersion)
    }
    $fingerprintPath = Join-Path $cacheRoot 'build-fingerprint.json'
    $cachedBackend = $null
    if (Test-Path -LiteralPath $fingerprintPath -PathType Leaf) {
        try { $cachedBackend = [string]((Get-Content -LiteralPath $fingerprintPath -Raw -Encoding UTF8 | ConvertFrom-Json).Backend) }
        catch { $cachedBackend = $null }
    }
    if ($cachedBackend -cne [string]$fingerprint.Backend -or -not (Test-Path -LiteralPath $Layout.ServerJar -PathType Leaf)) {
        if ($VisibleProcesses) { Write-Host '[2/5] Building backend...' -ForegroundColor Cyan }
        # Windows PowerShell 5.1 cannot reliably bind a string array passed after
        # `-File`. Use an encoded command so Maven receives the complete array
        # instead of treating the second item as an unexpected positional value.
        $escapedMavenJdk17 = ([string]$Layout.MavenJdk17).Replace("'", "''")
        $mavenCommand = "& '$escapedMavenJdk17' -MavenArgs @('-pl', 'yudao-server', '-am', '-DskipTests', 'clean', 'package')"
        $encodedMavenCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($mavenCommand))
        $buildSpec = [pscustomobject]@{
            Role = 'backend-build'; FilePath = 'powershell.exe'
            Arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encodedMavenCommand)
            WorkingDirectory = [string]$Layout.YudaoCloud
            StdOutLog = Join-Path $logRoot 'backend-build.stdout.log'; StdErrLog = Join-Path $logRoot 'backend-build.stderr.log'
            Environment = @{}
        }
        if ($null -ne $BuildProvider) { & $BuildProvider $buildSpec }
        else {
            $result = Invoke-OakvedNativeProcess -Spec ([pscustomobject]@{ FileName = $buildSpec.FilePath; Arguments = $buildSpec.Arguments; WorkingDirectory = $buildSpec.WorkingDirectory; Environment = @{} }) -TimeoutMilliseconds 1200000
            [IO.File]::WriteAllBytes($buildSpec.StdOutLog, [byte[]]$result.StdOut)
            [IO.File]::WriteAllBytes($buildSpec.StdErrLog, [byte[]]$result.StdErr)
            if ($result.ExitCode -ne 0) { throw 'Backend build failed.' }
        }
        [IO.File]::WriteAllText($fingerprintPath, ($fingerprint | ConvertTo-Json -Depth 6), (New-Object Text.UTF8Encoding($false)))
    }
    elseif ($VisibleProcesses) { Write-Host '[2/5] Backend build is current.' -ForegroundColor Green }

    $dependencySpecs = @(
        [pscustomobject]@{
            Role = 'admin-dependencies'; FilePath = [string]((Get-Command 'pnpm.cmd' -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source); Arguments = @('install', '--frozen-lockfile')
            WorkingDirectory = [string]$Layout.AdminUi
            Inputs = @((Join-Path $Layout.AdminUi 'package.json'), (Join-Path $Layout.AdminUi 'pnpm-lock.yaml'))
            Marker = Join-Path $Layout.AdminUi 'node_modules\.bin\vite.cmd'
        },
        [pscustomobject]@{
            Role = 'storefront-dependencies'; FilePath = [string]((Get-Command 'npm.cmd' -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source); Arguments = @('ci')
            WorkingDirectory = [string]$Layout.FurnitureWeb
            Inputs = @((Join-Path $Layout.FurnitureWeb 'package.json'), (Join-Path $Layout.FurnitureWeb 'package-lock.json'))
            Marker = Join-Path $Layout.FurnitureWeb 'node_modules\.bin\vite.cmd'
        }
    )
    foreach ($dependencySpec in $dependencySpecs) {
        foreach ($inputPath in $dependencySpec.Inputs) {
            if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
                throw "Required dependency manifest is missing: $inputPath"
            }
        }
        $dependencyMaterial = @($dependencySpec.Inputs | ForEach-Object {
                "$_`n$([IO.File]::ReadAllText($_))"
            }) -join "`n"
        $dependencyFingerprint = Get-OakvedSha256 -Value $dependencyMaterial
        $dependencyStatePath = Join-Path $cacheRoot "$($dependencySpec.Role).sha256"
        $cachedDependencyFingerprint = if (Test-Path -LiteralPath $dependencyStatePath -PathType Leaf) {
            [IO.File]::ReadAllText($dependencyStatePath).Trim()
        } else { $null }
        $dependencyMarkerExists = Test-Path -LiteralPath $dependencySpec.Marker -PathType Leaf
        if ($dependencyMarkerExists -and [string]::IsNullOrWhiteSpace($cachedDependencyFingerprint)) {
            [IO.File]::WriteAllText($dependencyStatePath, $dependencyFingerprint, (New-Object Text.UTF8Encoding($false)))
        }
        elseif (-not $dependencyMarkerExists -or $cachedDependencyFingerprint -cne $dependencyFingerprint) {
            if ($VisibleProcesses) { Write-Host "[3/5] Installing $($dependencySpec.Role)..." -ForegroundColor Cyan }
            $dependencySpec | Add-Member -NotePropertyName StdOutLog -NotePropertyValue (Join-Path $logRoot "$($dependencySpec.Role).stdout.log")
            $dependencySpec | Add-Member -NotePropertyName StdErrLog -NotePropertyValue (Join-Path $logRoot "$($dependencySpec.Role).stderr.log")
            $dependencySpec | Add-Member -NotePropertyName Environment -NotePropertyValue @{}
            if ($null -ne $BuildProvider) { & $BuildProvider $dependencySpec }
            else {
                $result = Invoke-OakvedNativeProcess -Spec ([pscustomobject]@{
                        FileName = $dependencySpec.FilePath; Arguments = $dependencySpec.Arguments
                        WorkingDirectory = $dependencySpec.WorkingDirectory; Environment = @{}
                    }) -TimeoutMilliseconds 1200000
                [IO.File]::WriteAllBytes($dependencySpec.StdOutLog, [byte[]]$result.StdOut)
                [IO.File]::WriteAllBytes($dependencySpec.StdErrLog, [byte[]]$result.StdErr)
                if ($result.ExitCode -ne 0) {
                    throw "$($dependencySpec.Role) installation failed. See $($dependencySpec.StdErrLog)."
                }
            }
            [IO.File]::WriteAllText($dependencyStatePath, $dependencyFingerprint, (New-Object Text.UTF8Encoding($false)))
        }
    }
    if ($VisibleProcesses) { Write-Host '[3/5] Frontend dependencies ready.' -ForegroundColor Green }
    if ($null -eq $ProcessStarter) { $ProcessStarter = { param($spec) Start-OakvedManagedProcess -Spec $spec } }
    if ($null -eq $ProcessProvider) { $ProcessProvider = { param($id) Get-Process -Id $id -ErrorAction SilentlyContinue } }
    if ($null -eq $ProcessTreeProvider) { $ProcessTreeProvider = { param($id) Get-OakvedDefaultProcessTree -RootPid $id } }
    if ($null -eq $ListenerProvider) { $ListenerProvider = { Get-OakvedDefaultListeners } }
    if ($null -eq $HttpProvider) { $HttpProvider = { param($url) Invoke-OakvedHealthRequest -Url $url } }
    if ($null -eq $SleepProvider) { $SleepProvider = { param($milliseconds) Start-Sleep -Milliseconds $milliseconds } }
    if ($null -eq $UtcNowProvider) { $UtcNowProvider = { [datetime]::UtcNow } }

    $jdbc = "jdbc:mysql://127.0.0.1:3306/$($database.Name)?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true"
    $specs = @(
        [pscustomobject]@{ Role = 'backend'; FilePath = 'java.exe'; Arguments = @('-jar', [string]$Layout.ServerJar, '--spring.profiles.active=local', "--server.port=48080", "--spring.datasource.dynamic.datasource.master.url=$jdbc", "--spring.datasource.dynamic.datasource.slave.url=$jdbc"); WorkingDirectory = [string]$Layout.YudaoCloud; Environment = @{}; Port = 48080; Visible = [bool]$VisibleProcesses },
        [pscustomobject]@{ Role = 'admin'; FilePath = 'pnpm.cmd'; Arguments = @('dev', '--', '--host', '0.0.0.0', '--port', '80', '--strictPort'); WorkingDirectory = [string]$Layout.AdminUi; Environment = @{ VITE_CACHE_DIR = (Join-Path $cacheRoot 'admin'); VITE_BASE_URL = 'http://127.0.0.1:48080'; VITE_API_URL = '/admin-api'; VITE_FURNITURE_WEB_URL = 'http://127.0.0.1:5173' }; Port = 80; Visible = [bool]$VisibleProcesses },
        [pscustomobject]@{ Role = 'storefront'; FilePath = 'npm.cmd'; Arguments = @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '5173', '--strictPort'); WorkingDirectory = [string]$Layout.FurnitureWeb; Environment = @{ VITE_CACHE_DIR = (Join-Path $cacheRoot 'storefront'); VITE_YUDAO_APP_API_BASE = 'http://127.0.0.1:48080/app-api' }; Port = 5173; Visible = [bool]$VisibleProcesses }
    )
    foreach ($spec in $specs) {
        $spec | Add-Member -NotePropertyName StdOutLog -NotePropertyValue (Join-Path $logRoot "$($spec.Role).stdout.log")
        $spec | Add-Member -NotePropertyName StdErrLog -NotePropertyValue (Join-Path $logRoot "$($spec.Role).stderr.log")
    }
    $started = New-Object 'System.Collections.Generic.List[object]'
    try {
        foreach ($spec in $specs) {
            if ($VisibleProcesses) { Write-Host "[4/5] Opening $($spec.Role) window..." -ForegroundColor Cyan }
            $process = & $ProcessStarter $spec
            $started.Add([pscustomobject]@{
                Pid = [int]$process.Id; StartTime = ([datetime]$process.StartTime).ToUniversalTime().ToString('o')
                Role = $spec.Role; FilePath = $spec.FilePath; Arguments = $spec.Arguments; WorkingDirectory = $spec.WorkingDirectory
                StdOutLog = $spec.StdOutLog; StdErrLog = $spec.StdErrLog; Port = $spec.Port
            })
        }
        $deadline = [datetime]::UtcNow.AddMilliseconds($HealthTimeoutMilliseconds)
        $health = $null
        $lastPortFailure = $null
        if ($VisibleProcesses) { Write-Host '[5/5] Waiting for ports and HTTP health checks...' -ForegroundColor Cyan }
        do {
            foreach ($record in $started) {
                $current = & $ProcessProvider ([int]$record.Pid)
                if ($null -eq $current -or ([datetime]$current.StartTime).ToUniversalTime().Ticks -ne ([datetime]$record.StartTime).ToUniversalTime().Ticks) {
                    throw "Managed $($record.Role) process is no longer the recorded PID/start-time instance."
                }
            }
            $portsHealthy = $true
            try { Assert-OakvedPortsAvailable -Ports $fixedPorts -ListenerProvider $ListenerProvider -ManagedPids @($started | ForEach-Object { [int]$_.Pid }) -ProcessTreeProvider $ProcessTreeProvider }
            catch { $portsHealthy = $false; $lastPortFailure = $_.Exception.Message }
            $health = Test-OakvedHttpHealth -HttpProvider $HttpProvider
            if ($portsHealthy -and $health.Backend -and $health.Admin -and $health.Storefront) { break }
            if ([datetime]::UtcNow -ge $deadline) {
                $portDetail = if ($lastPortFailure) { $lastPortFailure } else { 'OK' }
                throw "Runtime health check timed out. Backend=$($health.Backend), Admin=$($health.Admin), Storefront=$($health.Storefront), Ports=$portsHealthy ($portDetail)"
            }
            & $SleepProvider 250
        } while ($true)
        if ($VisibleProcesses) { Write-Host '[5/5] All services are healthy.' -ForegroundColor Green }
        $startedAt = ([datetime](& $UtcNowProvider)).ToUniversalTime().ToString('o')
        $targetModeProperty = $Target.PSObject.Properties['Mode']
        $targetSourceDirtyProperty = $Target.PSObject.Properties['SourceDirty']
        $targetSourceWorktreeProperty = $Target.PSObject.Properties['SourceWorktree']
        $targetMode = if ($null -ne $targetModeProperty) { [string]$targetModeProperty.Value } else { 'live-worktree' }
        $manifest = [ordered]@{
            Mode = $targetMode; RuntimeId = [string]$Target.RuntimeId; Branch = [string]$Target.Branch; Commit = [string]$Target.Commit; Dirty = [bool]$Target.Dirty
            SourceDirty = $(if ($null -ne $targetSourceDirtyProperty) { [bool]$targetSourceDirtyProperty.Value } else { [bool]$Target.Dirty })
            SourceWorktree = $(if ($null -ne $targetSourceWorktreeProperty) { [string]$targetSourceWorktreeProperty.Value } else { [string]$Target.Worktree })
            Worktree = [string]$Target.Worktree; StartedAt = $startedAt; Database = $database; CatalogVersion = [string]$database.CatalogVersion
            BuildFingerprint = [string]$fingerprint.Value; BuildFingerprints = $fingerprint; Ports = $fixedPorts; Processes = $started.ToArray(); Health = $health
        }
        if ($null -ne $ManifestWriter) { & $ManifestWriter $manifest $manifestPath }
        else { Write-OakvedManifest -Manifest $manifest -Path $manifestPath }
        return [pscustomobject]$manifest
    }
    catch {
        $primary = $_
        try { $null = Stop-OakvedRuntime -Manifest ([pscustomobject]@{ Processes = $started.ToArray() }) -ProcessProvider $ProcessProvider -Stopper $Stopper }
        catch { throw "$($primary.Exception.Message) Runtime cleanup also failed: $($_.Exception.Message)" }
        throw $primary
    }
}

function Get-OakvedRuntimeStatus {
    [CmdletBinding(DefaultParameterSetName = 'Path')]
    param(
        [Parameter(Mandatory = $true, ParameterSetName = 'Manifest')][object]$Manifest,
        [Parameter(Mandatory = $true, ParameterSetName = 'Path')][string]$ManifestPath,
        [object]$Target,
        [AllowEmptyString()][string]$MySqlRootPassword,
        [scriptblock]$DatabaseVersionProvider,
        [scriptblock]$ProcessProvider,
        [scriptblock]$ProcessTreeProvider,
        [scriptblock]$ListenerProvider,
        [scriptblock]$HttpProvider
    )
    $mismatches = New-Object 'System.Collections.Generic.List[string]'
    if ($PSCmdlet.ParameterSetName -eq 'Path') {
        if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
            return [pscustomobject]@{ Healthy = $false; ExitCode = 1; Mismatches = @('manifest missing'); ManifestPath = $ManifestPath }
        }
        try { $Manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json }
        catch { return [pscustomobject]@{ Healthy = $false; ExitCode = 1; Mismatches = @('manifest corrupt'); ManifestPath = $ManifestPath } }
    }
    foreach ($required in @('RuntimeId', 'Branch', 'Commit', 'Dirty', 'Worktree', 'Database', 'CatalogVersion', 'Processes', 'Ports')) {
        if ($null -eq $Manifest.PSObject.Properties[$required]) { $mismatches.Add("manifest missing $required") }
    }
    if ($null -eq $Target) {
        $mismatches.Add('target provenance unverified')
    }
    $missingFields = @($mismatches | Where-Object { $_ -like 'manifest missing *' })
    if ($missingFields.Count -gt 0) {
        $getManifestValue = {
            param([string]$name)
            $property = $Manifest.PSObject.Properties[$name]
            if ($null -ne $property) { return $property.Value }
            return $null
        }
        return [pscustomobject]@{
            Healthy = $false; ExitCode = 1; Mismatches = $mismatches.ToArray()
            RuntimeId = & $getManifestValue 'RuntimeId'; Branch = & $getManifestValue 'Branch'; Commit = & $getManifestValue 'Commit'
            Dirty = & $getManifestValue 'Dirty'; Worktree = & $getManifestValue 'Worktree'; Database = $null; DatabaseVersion = $null
            CatalogVersion = & $getManifestValue 'CatalogVersion'; BuildFingerprint = & $getManifestValue 'BuildFingerprint'
            Processes = @(); Ports = @(); Health = [pscustomobject]@{ Backend = $false; Admin = $false; Storefront = $false }
        }
    }
    if ($null -ne $Target) {
        foreach ($field in @('RuntimeId', 'Branch', 'Commit', 'Dirty', 'Worktree')) {
            if ([string]$Manifest.$field -cne [string]$Target.$field) { $mismatches.Add("$($field.ToLowerInvariant()) mismatch") }
        }
        $manifestModeProperty = $Manifest.PSObject.Properties['Mode']
        $targetModeProperty = $Target.PSObject.Properties['Mode']
        if ($null -ne $manifestModeProperty -and $null -ne $targetModeProperty -and
            [string]$manifestModeProperty.Value -cne [string]$targetModeProperty.Value) {
            $mismatches.Add('mode mismatch')
        }
    }
    if ($null -eq $ProcessProvider) { $ProcessProvider = { param($id) Get-Process -Id $id -ErrorAction SilentlyContinue } }
    if ($null -eq $ProcessTreeProvider) { $ProcessTreeProvider = { param($id) Get-OakvedDefaultProcessTree -RootPid $id } }
    if ($null -eq $ListenerProvider) { $ListenerProvider = { Get-OakvedDefaultListeners } }
    if ($null -eq $HttpProvider) { $HttpProvider = { param($url) Invoke-OakvedHealthRequest -Url $url } }
    foreach ($record in @($Manifest.Processes)) {
        $current = & $ProcessProvider ([int]$record.Pid)
        if ($null -eq $current) { $mismatches.Add("$($record.Role) PID missing"); continue }
        try {
            if (([datetime]$current.StartTime).ToUniversalTime().Ticks -ne ([datetime]$record.StartTime).ToUniversalTime().Ticks) {
                $mismatches.Add("$($record.Role) PID start-time mismatch")
            }
        }
        catch { $mismatches.Add("$($record.Role) PID start-time invalid") }
    }
    try {
        Assert-OakvedPortsAvailable -Ports @(80, 5173, 48080) -ListenerProvider $ListenerProvider -ManagedPids @($Manifest.Processes | ForEach-Object { [int]$_.Pid }) -ProcessTreeProvider $ProcessTreeProvider
        $listeners = @(& $ListenerProvider)
        foreach ($port in @(80, 5173, 48080)) {
            if (@($listeners | Where-Object { [int]$_.Port -eq $port }).Count -eq 0) { $mismatches.Add("port $port is not listening") }
        }
    }
    catch { $mismatches.Add($_.Exception.Message) }
    $health = Test-OakvedHttpHealth -HttpProvider $HttpProvider
    if (-not $health.Backend) { $mismatches.Add('backend health mismatch') }
    if (-not $health.Admin) { $mismatches.Add('admin health mismatch') }
    if (-not $health.Storefront) { $mismatches.Add('storefront health mismatch') }
    $databaseVersion = $null
    if ($null -ne $DatabaseVersionProvider) {
        $databaseVersion = [string](& $DatabaseVersionProvider ([string]$Manifest.Database.Name))
    }
    elseif ($PSBoundParameters.ContainsKey('MySqlRootPassword')) {
        try {
            $databaseOutput = @(Invoke-OakvedDockerMySql -Database ([string]$Manifest.Database.Name) `
                -Sql "SELECT COALESCE(MAX(version),'') FROM schema_migrations;" -RootPassword $MySqlRootPassword)
            $databaseVersion = (($databaseOutput | ForEach-Object { [string]$_ }) -join "`n").Trim()
        }
        catch { $mismatches.Add('database ledger query failed') }
    }
    else {
        $mismatches.Add('database ledger unverified')
    }
    if ($null -ne $databaseVersion -and $databaseVersion -cne [string]$Manifest.CatalogVersion) { $mismatches.Add('database catalog version mismatch') }
    $healthy = $mismatches.Count -eq 0
    $modeProperty = $Manifest.PSObject.Properties['Mode']
    $sourceDirtyProperty = $Manifest.PSObject.Properties['SourceDirty']
    $refCommitProperty = if ($null -ne $Target) { $Target.PSObject.Properties['RefCommit'] } else { $null }
    $mode = if ($null -ne $modeProperty) { [string]$modeProperty.Value } else { 'live-worktree' }
    $refCommit = if ($null -ne $refCommitProperty) { [string]$refCommitProperty.Value } else { $null }
    return [pscustomobject]@{
        Healthy = $healthy; ExitCode = $(if ($healthy) { 0 } else { 1 }); Mismatches = $mismatches.ToArray()
        Mode = $mode
        RuntimeId = [string]$Manifest.RuntimeId; Branch = [string]$Manifest.Branch; Commit = [string]$Manifest.Commit; Dirty = [bool]$Manifest.Dirty
        SourceDirty = $(if ($null -ne $sourceDirtyProperty) { [bool]$sourceDirtyProperty.Value } else { [bool]$Manifest.Dirty })
        CurrentBranchCommit = $refCommit
        UpdateAvailable = $(if ($mode -ceq 'snapshot' -and -not [string]::IsNullOrWhiteSpace($refCommit)) { [string]$Manifest.Commit -cne $refCommit } else { $false })
        Worktree = [string]$Manifest.Worktree; Database = [string]$Manifest.Database.Name; DatabaseVersion = $databaseVersion
        CatalogVersion = [string]$Manifest.CatalogVersion
        BuildFingerprint = $(if ($null -ne $Manifest.PSObject.Properties['BuildFingerprint']) { [string]$Manifest.BuildFingerprint } else { $null })
        Processes = @($Manifest.Processes); Ports = @($Manifest.Ports); Health = $health
    }
}

Export-ModuleMember -Function @(
    'Get-OakvedWorktreeInventory',
    'Get-OakvedBranchCommit',
    'New-OakvedRuntimeSnapshot',
    'Resolve-OakvedTarget',
    'Resolve-OakvedManifestTarget',
    'Get-OakvedRuntimeId',
    'Get-OakvedProjectLayout',
    'Get-OakvedMigrationCatalog',
    'Compare-OakvedMigrationLedger',
    'Get-OakvedDatabaseName',
    'Invoke-OakvedDatabaseGate',
    'Get-OakvedBuildFingerprint',
    'Assert-OakvedPortsAvailable',
    'Write-OakvedManifest',
    'Start-OakvedRuntime',
    'Stop-OakvedRuntime',
    'Get-OakvedRuntimeStatus'
)
