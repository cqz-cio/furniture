[CmdletBinding()]
param(
    [string]$RunDirectory,
    [string[]]$Services = @()
)

$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($RunDirectory)) {
    $RunDirectory = Join-Path $cloudRoot '.local-run\jdk17'
}
$RunDirectory = [IO.Path]::GetFullPath($RunDirectory)

if (-not (Test-Path -LiteralPath $RunDirectory -PathType Container)) {
    Write-Output 'No recorded JDK 17 backend processes.'
    exit 0
}

$stateFiles = @(Get-ChildItem -LiteralPath $RunDirectory -Filter '*.json' -File)
if ($Services.Count -gt 0) {
    $selectedServices = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]$Services,
        [StringComparer]::OrdinalIgnoreCase
    )
    $stateFiles = @($stateFiles | Where-Object { $selectedServices.Contains($_.BaseName) })
}

if ($stateFiles.Count -eq 0) {
    Write-Output 'No matching recorded JDK 17 backend processes.'
    exit 0
}

foreach ($stateFile in $stateFiles) {
    $state = Get-Content -LiteralPath $stateFile.FullName -Raw | ConvertFrom-Json
    $processId = [int]$state.Pid
    $jarPath = [IO.Path]::GetFullPath([string]$state.JarPath)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) {
        Remove-Item -LiteralPath $stateFile.FullName -Force
        Write-Output "$($state.Service): stale PID record removed."
        continue
    }

    if (-not $jarPath.StartsWith($cloudRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Write-Warning "$($state.Service): PID $processId was not stopped because its recorded jar is outside this repository; state record retained."
        continue
    }

    if ($process.ProcessName -ne 'java') {
        Write-Warning "$($state.Service): PID $processId was not stopped because it is not a Java process; state record retained."
        continue
    }

    try {
        $recordedStartTime = [DateTimeOffset]::Parse([string]$state.StartedAt)
        $processStartTime = [DateTimeOffset]$process.StartTime
        $startTimeDifference = [Math]::Abs(($processStartTime - $recordedStartTime).TotalSeconds)
    } catch {
        Write-Warning "$($state.Service): PID $processId was not stopped because its start time could not be verified; state record retained."
        continue
    }
    if ($startTimeDifference -gt 120) {
        Write-Warning "$($state.Service): PID $processId was not stopped because its start time does not match the recorded process; state record retained."
        continue
    }

    Stop-Process -Id $processId -Force -ErrorAction Stop
    Wait-Process -Id $processId -Timeout 15 -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stateFile.FullName -Force
    Write-Output "$($state.Service) stopped: PID=$processId"
}
