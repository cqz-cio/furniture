[CmdletBinding()]
param([string]$RunDirectory)

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

foreach ($stateFile in Get-ChildItem -LiteralPath $RunDirectory -Filter '*.json' -File) {
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

    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop
    if (-not $processInfo.CommandLine -or
        $processInfo.CommandLine.IndexOf($jarPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        Write-Warning "$($state.Service): PID $processId was not stopped because its command line does not match the recorded jar; state record retained."
        continue
    }

    Stop-Process -Id $processId -Force -ErrorAction Stop
    Wait-Process -Id $processId -Timeout 15 -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stateFile.FullName -Force
    Write-Output "$($state.Service) stopped: PID=$processId"
}
