[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string[]] $MavenArgs = @()
)

$ErrorActionPreference = 'Stop'
$resolver = Join-Path $PSScriptRoot 'Resolve-Jdk17.ps1'
$jdkHomeOutput = @(& $resolver)
if ($LASTEXITCODE -ne 0 -or $jdkHomeOutput.Count -ne 1) {
    throw 'Unable to resolve exactly one Java 17 home.'
}

$maven = Get-Command 'mvn.cmd' -ErrorAction Stop
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path
$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$mavenExitCode = 1
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tempDirectory = Join-Path $repositoryRoot '.local-run\jdk17\tmp'

try {
    New-Item -ItemType Directory -Path $tempDirectory -Force | Out-Null
    $env:JAVA_HOME = [string] $jdkHomeOutput[0]
    $env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$oldPath"
    $requiredOptions = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=`"$tempDirectory`""
    $env:JAVA_TOOL_OPTIONS = if ([string]::IsNullOrWhiteSpace($oldJavaToolOptions)) {
        $requiredOptions
    } else {
        "$requiredOptions $oldJavaToolOptions"
    }
    & $maven.Source @MavenArgs
    $mavenExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
    $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
}

$global:LASTEXITCODE = $mavenExitCode
if ($mavenExitCode -ne 0) {
    throw "Maven exited with code $mavenExitCode."
}
