[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$mergeScript = Join-Path (Split-Path -Parent $PSScriptRoot) 'Merge-Jdk17Baseline.ps1'
if (-not (Test-Path -LiteralPath $mergeScript -PathType Leaf)) {
    throw "JDK 17 baseline merge script is missing: $mergeScript"
}

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "yudao-jdk17-merge-$([guid]::NewGuid().ToString('N'))"
$baseRoot = Join-Path $fixtureRoot 'base'
$oursRoot = Join-Path $fixtureRoot 'ours'
$theirsRoot = Join-Path $fixtureRoot 'theirs'
$outputRoot = Join-Path $fixtureRoot 'output'

function Write-FixtureFile {
    param([string] $Root, [string] $RelativePath, [string] $Content)
    $path = Join-Path $Root $RelativePath
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $path)) | Out-Null
    [System.IO.File]::WriteAllText($path, $Content, [System.Text.UTF8Encoding]::new($false))
}

try {
    @($baseRoot, $oursRoot, $theirsRoot) | ForEach-Object {
        [System.IO.Directory]::CreateDirectory($_) | Out-Null
    }

    Write-FixtureFile $baseRoot 'upstream.txt' "base`n"
    Write-FixtureFile $oursRoot 'upstream.txt' "base`n"
    Write-FixtureFile $theirsRoot 'upstream.txt' "jdk17`n"

    Write-FixtureFile $baseRoot 'local.txt' "base`n"
    Write-FixtureFile $oursRoot 'local.txt' "custom`n"
    Write-FixtureFile $theirsRoot 'local.txt' "base`n"

    Write-FixtureFile $baseRoot 'conflict.txt' "base`n"
    Write-FixtureFile $oursRoot 'conflict.txt' "custom`n"
    Write-FixtureFile $theirsRoot 'conflict.txt' "jdk17`n"

    Write-FixtureFile $oursRoot 'ours-added.txt' "custom addition`n"
    Write-FixtureFile $theirsRoot 'theirs-added.txt' "jdk17 addition`n"

    Write-FixtureFile $baseRoot 'theirs-deleted.txt' "remove upstream`n"
    Write-FixtureFile $oursRoot 'theirs-deleted.txt' "remove upstream`n"

    Write-FixtureFile $baseRoot 'ours-deleted.txt' "remove locally`n"
    Write-FixtureFile $theirsRoot 'ours-deleted.txt' "remove locally`n"

    & $mergeScript -FixtureRoot $fixtureRoot
    if ($LASTEXITCODE -eq 0) {
        throw 'A fixture with a concurrent edit must report a conflict.'
    }

    $expectations = @{
        'upstream.txt' = "jdk17`n"
        'local.txt' = "custom`n"
        'ours-added.txt' = "custom addition`n"
        'theirs-added.txt' = "jdk17 addition`n"
    }
    foreach ($entry in $expectations.GetEnumerator()) {
        $actual = [System.IO.File]::ReadAllText((Join-Path $outputRoot $entry.Key))
        if ($actual -ne $entry.Value) {
            throw "Unexpected merged content for $($entry.Key)"
        }
    }
    foreach ($deleted in @('theirs-deleted.txt', 'ours-deleted.txt')) {
        if (Test-Path -LiteralPath (Join-Path $outputRoot $deleted)) {
            throw "Deleted fixture was retained: $deleted"
        }
    }

    $conflict = [System.IO.File]::ReadAllText((Join-Path $outputRoot 'conflict.txt'))
    if ($conflict -notmatch '(?m)^<<<<<<< ours$' -or $conflict -notmatch '(?m)^>>>>>>> theirs$') {
        throw 'Concurrent edits did not retain standard conflict markers.'
    }
    $report = @(Get-Content -LiteralPath (Join-Path $fixtureRoot 'conflicts.txt'))
    if ($report.Count -ne 1 -or $report[0] -ne 'conflict.txt') {
        throw "Unexpected conflict report: $($report -join ', ')"
    }

    Write-Host 'JDK 17 baseline merge fixture contract passed.'

    $localRepository = Join-Path $fixtureRoot 'local-repository'
    $localBackend = Join-Path $localRepository 'backend'
    $referenceRepository = Join-Path $fixtureRoot 'reference-repository'
    $preparationRoot = Join-Path $fixtureRoot 'prepared'
    [System.IO.Directory]::CreateDirectory($localBackend) | Out-Null
    [System.IO.Directory]::CreateDirectory($referenceRepository) | Out-Null

    & git -C $localRepository init --quiet
    & git -C $localRepository config user.email 'merge-test@example.invalid'
    & git -C $localRepository config user.name 'Merge Test'
    & git -C $localRepository config core.autocrlf false
    Write-FixtureFile $localBackend 'pom.xml' "java8`n"
    Write-FixtureFile $localBackend 'custom.txt' "base`n"
    & git -C $localRepository add backend
    & git -C $localRepository commit --quiet -m 'base'
    $baseCommit = (& git -C $localRepository rev-parse HEAD).Trim()
    Write-FixtureFile $localBackend 'custom.txt' "custom`n"
    & git -C $localRepository add backend/custom.txt
    & git -C $localRepository commit --quiet -m 'custom'

    & git -C $referenceRepository init --quiet
    & git -C $referenceRepository config user.email 'merge-test@example.invalid'
    & git -C $referenceRepository config user.name 'Merge Test'
    & git -C $referenceRepository config core.autocrlf false
    Write-FixtureFile $referenceRepository 'pom.xml' "java17`n"
    Write-FixtureFile $referenceRepository 'custom.txt' "base`n"
    & git -C $referenceRepository add pom.xml custom.txt
    & git -C $referenceRepository commit --quiet -m 'jdk17'
    $referenceCommit = (& git -C $referenceRepository rev-parse HEAD).Trim()

    & $mergeScript `
        -BaseCommit $baseCommit `
        -ReferenceRoot $referenceRepository `
        -ReferenceCommit $referenceCommit `
        -PreparationRoot $preparationRoot `
        -LocalRepositoryRoot $localRepository `
        -LocalBackendRoot $localBackend
    if ($LASTEXITCODE -ne 0) {
        throw 'Repository preparation fixture should merge without conflicts.'
    }
    $preparedOutput = Join-Path $preparationRoot 'output'
    if ([System.IO.File]::ReadAllText((Join-Path $preparedOutput 'pom.xml')).Trim() -ne 'java17') {
        throw 'Repository preparation did not take the JDK 17 baseline.'
    }
    if ([System.IO.File]::ReadAllText((Join-Path $preparedOutput 'custom.txt')).Trim() -ne 'custom') {
        throw 'Repository preparation did not retain the local customization.'
    }
    foreach ($artifact in @('metadata.json', 'prepared.patch', 'conflicts.txt')) {
        if (-not (Test-Path -LiteralPath (Join-Path $preparationRoot $artifact) -PathType Leaf)) {
            throw "Repository preparation artifact is missing: $artifact"
        }
    }

    Write-Host 'JDK 17 repository preparation contract passed.'
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
