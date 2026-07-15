[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Test-Java17Home {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return $false
    }
    $javaExe = Join-Path $Path 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        return $false
    }

    $versionOutput = @(cmd.exe /d /c "`"$javaExe`" -version 2>&1")
    $javaExitCode = $LASTEXITCODE
    $versionLine = $versionOutput | Select-Object -First 1
    return $javaExitCode -eq 0 -and [string] $versionLine -match 'version "17(?:\.|\")'
}

$candidates = [System.Collections.Generic.List[string]]::new()
if (-not [string]::IsNullOrWhiteSpace($env:YUDAO_JAVA17_HOME)) {
    $candidates.Add($env:YUDAO_JAVA17_HOME)
}

@(
    'C:\Program Files\Microsoft\jdk-17*',
    'C:\Program Files\Eclipse Adoptium\jdk-17*'
) | ForEach-Object {
    Get-ChildItem -Path $_ -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { $candidates.Add($_.FullName) }
}

foreach ($candidate in $candidates | Select-Object -Unique) {
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    if (Test-Java17Home -Path $resolved) {
        Write-Output $resolved
        exit 0
    }
}

Write-Error 'Java 17 was not found. Install Microsoft OpenJDK 17 or Temurin 17, or set YUDAO_JAVA17_HOME.'
exit 1
