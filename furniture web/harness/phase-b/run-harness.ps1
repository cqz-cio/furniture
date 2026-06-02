param(
  [switch]$SkipTests,
  [switch]$SkipBuild,
  [switch]$SkipBoundary
)

$ErrorActionPreference = "Stop"

$HarnessDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $HarnessDir "..\..")
$AllowlistPath = Join-Path $HarnessDir "boundary-allowlist.txt"
$BaselinePath = Join-Path $HarnessDir "baseline-dirty-files.txt"
$TempDist = Join-Path $HarnessDir ".tmp-dist"

function Normalize-RepoPath {
  param([string]$PathText)
  return $PathText.Trim().Trim('"').Replace("\", "/")
}

function Read-PatternFile {
  param([string]$Path)
  if (-not (Test-Path $Path)) {
    return @()
  }
  return Get-Content -Path $Path |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith("#") } |
    ForEach-Object { Normalize-RepoPath $_ }
}

function Get-ChangedRepoPaths {
  Push-Location $RepoRoot
  try {
    $lines = git -c core.quotepath=false status --short
  } finally {
    Pop-Location
  }

  $paths = New-Object System.Collections.Generic.List[string]
  foreach ($line in $lines) {
    if ($line.Length -lt 4) {
      continue
    }
    $pathText = $line.Substring(3)
    if ($pathText.Contains(" -> ")) {
      foreach ($part in ($pathText -split " -> ")) {
        $paths.Add((Normalize-RepoPath $part))
      }
    } else {
      $paths.Add((Normalize-RepoPath $pathText))
    }
  }
  return $paths | Sort-Object -Unique
}

function Test-PathAllowed {
  param(
    [string]$ChangedPath,
    [string[]]$Allowlist,
    [string[]]$Baseline
  )

  foreach ($baselinePath in $Baseline) {
    if ($ChangedPath -like $baselinePath) {
      return $true
    }
  }

  foreach ($pattern in $Allowlist) {
    if ($ChangedPath -like $pattern) {
      return $true
    }
  }
  return $false
}

function Invoke-BoundaryCheck {
  $allowlist = @(Read-PatternFile $AllowlistPath)
  $baseline = @(Read-PatternFile $BaselinePath)
  $changed = @(Get-ChangedRepoPaths)

  $violations = @()
  foreach ($path in $changed) {
    if (-not (Test-PathAllowed -ChangedPath $path -Allowlist $allowlist -Baseline $baseline)) {
      $violations += $path
    }
  }

  if ($violations.Count -gt 0) {
    Write-Host "Phase B boundary violations:" -ForegroundColor Red
    foreach ($violation in $violations) {
      Write-Host " - $violation" -ForegroundColor Red
    }
    exit 1
  }

  Write-Host "Boundary check passed." -ForegroundColor Green
}

function Invoke-RepoCommand {
  param(
    [string]$Label,
    [scriptblock]$Command
  )

  Write-Host $Label -ForegroundColor Cyan
  Push-Location $RepoRoot
  try {
    & $Command
    if ($LASTEXITCODE -ne 0) {
      throw "$Label failed with exit code $LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }
}

function Remove-TempDist {
  if (-not (Test-Path $TempDist)) {
    return
  }
  $resolvedTemp = Resolve-Path $TempDist
  $resolvedHarness = Resolve-Path $HarnessDir
  if (-not $resolvedTemp.Path.StartsWith($resolvedHarness.Path)) {
    throw "Refusing to remove temp dist outside harness directory: $($resolvedTemp.Path)"
  }
  Remove-Item -LiteralPath $resolvedTemp.Path -Recurse -Force
}

if (-not $SkipBoundary) {
  Invoke-BoundaryCheck
}

if (-not $SkipTests) {
  Invoke-RepoCommand "Running Vitest" { npm.cmd test }
}

if (-not $SkipBuild) {
  Remove-TempDist
  Invoke-RepoCommand "Running Vite build into harness temp dist" {
    npm.cmd run build -- --outDir "harness/phase-b/.tmp-dist" --emptyOutDir
  }
  Remove-TempDist
}

if (-not $SkipBoundary) {
  Invoke-BoundaryCheck
}

Write-Host "Phase B harness completed." -ForegroundColor Green
