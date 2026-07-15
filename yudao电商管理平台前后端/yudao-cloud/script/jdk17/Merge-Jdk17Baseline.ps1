[CmdletBinding(DefaultParameterSetName = 'Prepare')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Fixture')]
    [string] $FixtureRoot,

    [Parameter(Mandatory = $true, ParameterSetName = 'Prepare')]
    [string] $BaseCommit,

    [Parameter(Mandatory = $true, ParameterSetName = 'Prepare')]
    [string] $ReferenceRoot,

    [Parameter(Mandatory = $true, ParameterSetName = 'Prepare')]
    [string] $ReferenceCommit,

    [Parameter(Mandatory = $false, ParameterSetName = 'Prepare')]
    [Parameter(Mandatory = $true, ParameterSetName = 'Apply')]
    [string] $PreparationRoot,

    [Parameter(Mandatory = $false, ParameterSetName = 'Prepare')]
    [string] $LocalRepositoryRoot,

    [Parameter(Mandatory = $false, ParameterSetName = 'Prepare')]
    [string] $LocalBackendRoot,

    [Parameter(Mandatory = $false, ParameterSetName = 'Prepare')]
    [switch] $ForcePrepare,

    [Parameter(Mandatory = $true, ParameterSetName = 'Apply')]
    [switch] $ApplyPrepared
)

$ErrorActionPreference = 'Stop'

function Get-RelativeFileSet {
    param([Parameter(Mandatory = $true)][string] $Root)

    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        return @()
    }
    $resolvedRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    return @(Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File | ForEach-Object {
        $_.FullName.Substring($resolvedRoot.Length).TrimStart('\', '/').Replace('\', '/')
    })
}

function Test-FileEqual {
    param([string] $Left, [string] $Right)

    if (-not (Test-Path -LiteralPath $Left -PathType Leaf) -or
        -not (Test-Path -LiteralPath $Right -PathType Leaf)) {
        return $false
    }
    $leftInfo = Get-Item -LiteralPath $Left
    $rightInfo = Get-Item -LiteralPath $Right
    if ($leftInfo.Length -ne $rightInfo.Length) {
        return $false
    }
    return (Get-FileHash -LiteralPath $Left -Algorithm SHA256).Hash -eq
        (Get-FileHash -LiteralPath $Right -Algorithm SHA256).Hash
}

function Copy-MergeFile {
    param([string] $Source, [string] $Destination)

    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Destination)) | Out-Null
    [System.IO.File]::Copy($Source, $Destination, $true)
}

function Write-DeleteModifyConflict {
    param(
        [string] $Destination,
        [string] $Ours,
        [string] $Theirs
    )

    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Destination)) | Out-Null
    $oursText = if ($Ours -and (Test-Path -LiteralPath $Ours)) {
        [System.IO.File]::ReadAllText($Ours)
    } else { '' }
    $theirsText = if ($Theirs -and (Test-Path -LiteralPath $Theirs)) {
        [System.IO.File]::ReadAllText($Theirs)
    } else { '' }
    $content = "<<<<<<< ours`n$oursText=======`n$theirsText>>>>>>> theirs`n"
    [System.IO.File]::WriteAllText($Destination, $content, [System.Text.UTF8Encoding]::new($false))
}

function Merge-DirectoryTrees {
    param(
        [Parameter(Mandatory = $true)][string] $BaseRoot,
        [Parameter(Mandatory = $true)][string] $OursRoot,
        [Parameter(Mandatory = $true)][string] $TheirsRoot,
        [Parameter(Mandatory = $true)][string] $OutputRoot,
        [Parameter(Mandatory = $true)][string] $ConflictReport
    )

    if (Test-Path -LiteralPath $OutputRoot) {
        Remove-Item -LiteralPath $OutputRoot -Recurse -Force
    }
    [System.IO.Directory]::CreateDirectory($OutputRoot) | Out-Null

    $relativePaths = @(
        (Get-RelativeFileSet $BaseRoot) +
        (Get-RelativeFileSet $OursRoot) +
        (Get-RelativeFileSet $TheirsRoot) |
            Sort-Object -Unique
    )
    $conflicts = [System.Collections.Generic.List[string]]::new()

    foreach ($relativePath in $relativePaths) {
        $platformPath = $relativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $base = Join-Path $BaseRoot $platformPath
        $ours = Join-Path $OursRoot $platformPath
        $theirs = Join-Path $TheirsRoot $platformPath
        $output = Join-Path $OutputRoot $platformPath
        $hasBase = Test-Path -LiteralPath $base -PathType Leaf
        $hasOurs = Test-Path -LiteralPath $ours -PathType Leaf
        $hasTheirs = Test-Path -LiteralPath $theirs -PathType Leaf

        if ($hasOurs -and $hasTheirs -and (Test-FileEqual $ours $theirs)) {
            Copy-MergeFile $ours $output
            continue
        }
        if ($hasBase -and $hasOurs -and (Test-FileEqual $base $ours)) {
            if ($hasTheirs) { Copy-MergeFile $theirs $output }
            continue
        }
        if ($hasBase -and $hasTheirs -and (Test-FileEqual $base $theirs)) {
            if ($hasOurs) { Copy-MergeFile $ours $output }
            continue
        }
        if (-not $hasBase -and $hasOurs -and -not $hasTheirs) {
            Copy-MergeFile $ours $output
            continue
        }
        if (-not $hasBase -and -not $hasOurs -and $hasTheirs) {
            Copy-MergeFile $theirs $output
            continue
        }
        if ($hasBase -and -not $hasOurs -and -not $hasTheirs) {
            continue
        }
        if ($hasBase -and ($hasOurs -xor $hasTheirs)) {
            Write-DeleteModifyConflict -Destination $output -Ours $(if ($hasOurs) { $ours } else { $null }) -Theirs $(if ($hasTheirs) { $theirs } else { $null })
            $conflicts.Add($relativePath)
            continue
        }

        Copy-MergeFile $ours $output
        $mergeBase = $base
        $temporaryBase = $null
        if (-not $hasBase) {
            $temporaryBase = Join-Path ([System.IO.Path]::GetTempPath()) "yudao-empty-$([guid]::NewGuid().ToString('N'))"
            [System.IO.File]::WriteAllBytes($temporaryBase, [byte[]]::new(0))
            $mergeBase = $temporaryBase
        }
        try {
            & git merge-file -L ours -L base -L theirs -- $output $mergeBase $theirs
            $mergeExitCode = $LASTEXITCODE
        } finally {
            if ($temporaryBase -and (Test-Path -LiteralPath $temporaryBase)) {
                Remove-Item -LiteralPath $temporaryBase -Force
            }
        }
        if ($mergeExitCode -gt 0) {
            $conflicts.Add($relativePath)
        } elseif ($mergeExitCode -lt 0) {
            throw "git merge-file failed for $relativePath with exit code $mergeExitCode"
        }
    }

    $reportContent = if ($conflicts.Count -gt 0) { ($conflicts -join "`n") + "`n" } else { '' }
    [System.IO.File]::WriteAllText($ConflictReport, $reportContent, [System.Text.UTF8Encoding]::new($false))
    return $conflicts.Count
}

function Invoke-GitArchive {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [Parameter(Mandatory = $true)][string] $Commit,
        [Parameter(Mandatory = $true)][string] $ArchivePath,
        [string] $PathSpec,
        [switch] $TrustRepository
    )

    $arguments = [System.Collections.Generic.List[string]]::new()
    if ($TrustRepository) {
        $arguments.Add('-c')
        $arguments.Add("safe.directory=$RepositoryRoot")
    }
    $arguments.Add('-C')
    $arguments.Add($RepositoryRoot)
    $arguments.Add('archive')
    $arguments.Add('--format=zip')
    $arguments.Add("--output=$ArchivePath")
    $treeish = if ([string]::IsNullOrWhiteSpace($PathSpec)) {
        $Commit
    } else {
        "${Commit}:$PathSpec"
    }
    $arguments.Add($treeish)

    & git @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git archive failed for commit $Commit in $RepositoryRoot"
    }
}

function Expand-GitTree {
    param(
        [Parameter(Mandatory = $true)][string] $ArchivePath,
        [Parameter(Mandatory = $true)][string] $ExtractRoot,
        [string] $NestedPath,
        [Parameter(Mandatory = $true)][string] $DestinationRoot
    )

    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $ExtractRoot
    $sourceRoot = if ([string]::IsNullOrWhiteSpace($NestedPath)) {
        $ExtractRoot
    } else {
        Join-Path $ExtractRoot $NestedPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    }
    if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
        throw "Archived tree root is missing: $sourceRoot"
    }
    [System.IO.Directory]::CreateDirectory($DestinationRoot) | Out-Null
    Get-ChildItem -LiteralPath $sourceRoot -Force | Copy-Item -Destination $DestinationRoot -Recurse -Force
}

if ($PSCmdlet.ParameterSetName -eq 'Fixture') {
    $resolvedFixture = [System.IO.Path]::GetFullPath($FixtureRoot)
    $conflictCount = Merge-DirectoryTrees `
        -BaseRoot (Join-Path $resolvedFixture 'base') `
        -OursRoot (Join-Path $resolvedFixture 'ours') `
        -TheirsRoot (Join-Path $resolvedFixture 'theirs') `
        -OutputRoot (Join-Path $resolvedFixture 'output') `
        -ConflictReport (Join-Path $resolvedFixture 'conflicts.txt')
    if ($conflictCount -gt 0) {
        Write-Warning "$conflictCount merge conflict(s) require manual resolution."
        exit 1
    }
    exit 0
}

if ($PSCmdlet.ParameterSetName -eq 'Apply') {
    $preparationRootResolved = [System.IO.Path]::GetFullPath($PreparationRoot)
    $metadataPath = Join-Path $preparationRootResolved 'metadata.json'
    $currentRoot = Join-Path $preparationRootResolved 'current'
    $outputRoot = Join-Path $preparationRootResolved 'output'
    $patchPath = Join-Path $preparationRootResolved 'prepared.patch'
    foreach ($requiredPath in @($metadataPath, $currentRoot, $outputRoot)) {
        if (-not (Test-Path -LiteralPath $requiredPath)) {
            throw "Prepared migration artifact is missing: $requiredPath"
        }
    }

    $metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $repositoryRoot = [System.IO.Path]::GetFullPath([string] $metadata.repositoryRoot)
    $backendRoot = [System.IO.Path]::GetFullPath([string] $metadata.backendRoot)
    $scopeRelative = [string] $metadata.scopeRelative
    $currentHead = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $currentHead -ne [string] $metadata.oursCommit) {
        throw "Local HEAD changed after preparation. Expected $($metadata.oursCommit), got $currentHead."
    }

    & rg --no-ignore -l '^(<<<<<<< ours|>>>>>>> theirs)$' $outputRoot
    $markerExitCode = $LASTEXITCODE
    if ($markerExitCode -eq 0) {
        throw 'Prepared output still contains unresolved merge markers.'
    }
    if ($markerExitCode -ne 1) {
        throw "Unable to scan prepared output for conflict markers; rg exited with $markerExitCode."
    }

    Push-Location $preparationRootResolved
    try {
        & git -c core.autocrlf=false diff --no-index --binary --no-renames --output=prepared.patch -- current output
        $patchExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($patchExitCode -notin @(0, 1)) {
        throw "Unable to finalize prepared.patch; git diff exited with $patchExitCode."
    }
    [System.IO.File]::WriteAllText((Join-Path $preparationRootResolved 'conflicts.txt'), '', [System.Text.UTF8Encoding]::new($false))

    & git -C $repositoryRoot apply --check --ignore-space-change --ignore-whitespace -p2 "--directory=$scopeRelative" -- $patchPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Prepared patch does not apply cleanly. No repository files were changed.'
    }
    & git -C $repositoryRoot apply --ignore-space-change --ignore-whitespace -p2 "--directory=$scopeRelative" -- $patchPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Prepared patch application failed after a successful check.'
    }
    Write-Host "Applied prepared JDK 17 migration to $backendRoot"
    exit 0
}

$defaultBackendRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$backendRoot = if ([string]::IsNullOrWhiteSpace($LocalBackendRoot)) {
    $defaultBackendRoot
} else {
    [System.IO.Path]::GetFullPath($LocalBackendRoot)
}
$repositoryRoot = if ([string]::IsNullOrWhiteSpace($LocalRepositoryRoot)) {
    (& git -C $backendRoot rev-parse --show-toplevel).Trim()
} else {
    [System.IO.Path]::GetFullPath($LocalRepositoryRoot)
}
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $repositoryRoot '.git'))) {
    throw "Local repository root is invalid: $repositoryRoot"
}
$repositoryRoot = [System.IO.Path]::GetFullPath($repositoryRoot).TrimEnd('\', '/')
$backendRoot = [System.IO.Path]::GetFullPath($backendRoot).TrimEnd('\', '/')
if (-not $backendRoot.StartsWith($repositoryRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Backend root must be inside the local repository: $backendRoot"
}
$scopeRelative = $backendRoot.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
$referenceRootResolved = [System.IO.Path]::GetFullPath($ReferenceRoot).TrimEnd('\', '/')

& git -C $repositoryRoot cat-file -e "$BaseCommit`^{commit}"
if ($LASTEXITCODE -ne 0) { throw "Base commit is invalid: $BaseCommit" }
& git -c "safe.directory=$referenceRootResolved" -C $referenceRootResolved cat-file -e "$ReferenceCommit`^{commit}"
if ($LASTEXITCODE -ne 0) { throw "Reference commit is invalid: $ReferenceCommit" }
$oursCommit = (& git -C $repositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve local HEAD.' }

$preparationRootResolved = if ([string]::IsNullOrWhiteSpace($PreparationRoot)) {
    Join-Path $repositoryRoot '.codex-temp\jdk17-merge'
} else {
    [System.IO.Path]::GetFullPath($PreparationRoot)
}
if (Test-Path -LiteralPath $preparationRootResolved) {
    if (-not $ForcePrepare) {
        throw "Preparation root already exists. Preserve manual resolutions or rerun with -ForcePrepare: $preparationRootResolved"
    }
    $allowedForceRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot '.codex-temp')).TrimEnd('\', '/')
    if (-not $preparationRootResolved.StartsWith($allowedForceRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "-ForcePrepare may only remove a directory below $allowedForceRoot"
    }
    Remove-Item -LiteralPath $preparationRootResolved -Recurse -Force
}
[System.IO.Directory]::CreateDirectory($preparationRootResolved) | Out-Null

$baseArchive = Join-Path $preparationRootResolved 'base.zip'
$oursArchive = Join-Path $preparationRootResolved 'current.zip'
$theirsArchive = Join-Path $preparationRootResolved 'theirs.zip'
Invoke-GitArchive -RepositoryRoot $repositoryRoot -Commit $BaseCommit -ArchivePath $baseArchive -PathSpec $scopeRelative
Invoke-GitArchive -RepositoryRoot $repositoryRoot -Commit $oursCommit -ArchivePath $oursArchive -PathSpec $scopeRelative
Invoke-GitArchive -RepositoryRoot $referenceRootResolved -Commit $ReferenceCommit -ArchivePath $theirsArchive -TrustRepository

$baseTree = Join-Path $preparationRootResolved 'base'
$oursTree = Join-Path $preparationRootResolved 'current'
$theirsTree = Join-Path $preparationRootResolved 'theirs'
$baseExtract = Join-Path $preparationRootResolved '_base-extract'
$oursExtract = Join-Path $preparationRootResolved '_current-extract'
$theirsExtract = Join-Path $preparationRootResolved '_theirs-extract'
Expand-GitTree -ArchivePath $baseArchive -ExtractRoot $baseExtract -DestinationRoot $baseTree
Expand-GitTree -ArchivePath $oursArchive -ExtractRoot $oursExtract -DestinationRoot $oursTree
Expand-GitTree -ArchivePath $theirsArchive -ExtractRoot $theirsExtract -DestinationRoot $theirsTree
Remove-Item -LiteralPath $baseExtract, $oursExtract, $theirsExtract -Recurse -Force
Remove-Item -LiteralPath $baseArchive, $oursArchive, $theirsArchive -Force

$outputRoot = Join-Path $preparationRootResolved 'output'
$conflictReport = Join-Path $preparationRootResolved 'conflicts.txt'
$conflictCount = Merge-DirectoryTrees -BaseRoot $baseTree -OursRoot $oursTree -TheirsRoot $theirsTree -OutputRoot $outputRoot -ConflictReport $conflictReport

$metadata = [ordered]@{
    baseCommit = $BaseCommit
    oursCommit = $oursCommit
    referenceCommit = $ReferenceCommit
    referenceRoot = $referenceRootResolved
    repositoryRoot = $repositoryRoot
    backendRoot = $backendRoot
    scopeRelative = $scopeRelative
}
$metadataJson = $metadata | ConvertTo-Json
[System.IO.File]::WriteAllText((Join-Path $preparationRootResolved 'metadata.json'), $metadataJson + "`n", [System.Text.UTF8Encoding]::new($false))

Push-Location $preparationRootResolved
try {
    & git -c core.autocrlf=false diff --no-index --binary --no-renames --output=prepared.patch -- current output
    $patchExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($patchExitCode -notin @(0, 1)) {
    throw "Unable to generate prepared.patch; git diff exited with $patchExitCode"
}

Write-Host "Prepared JDK 17 merge at $preparationRootResolved"
if ($conflictCount -gt 0) {
    Write-Warning "$conflictCount merge conflict(s) require manual resolution in the output directory."
    exit 1
}
exit 0
