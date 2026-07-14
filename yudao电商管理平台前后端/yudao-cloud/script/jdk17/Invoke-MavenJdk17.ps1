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
$mavenExitCode = 1

try {
    $env:JAVA_HOME = [string] $jdkHomeOutput[0]
    $env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$oldPath"
    & $maven.Source @MavenArgs
    $mavenExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}

$global:LASTEXITCODE = $mavenExitCode
if ($mavenExitCode -ne 0) {
    throw "Maven exited with code $mavenExitCode."
}
