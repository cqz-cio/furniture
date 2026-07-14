[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $PSScriptRoot
$resolver = Join-Path $scriptRoot 'Resolve-Jdk17.ps1'
$mavenWrapper = Join-Path $scriptRoot 'Invoke-MavenJdk17.ps1'

if (-not (Test-Path -LiteralPath $resolver -PathType Leaf)) {
    throw "JDK 17 resolver is missing: $resolver"
}
if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "JDK 17 Maven wrapper is missing: $mavenWrapper"
}

$beforeJavaHome = $env:JAVA_HOME
$beforePath = $env:Path
$jdkHomeOutput = @(& $resolver)
if ($LASTEXITCODE -ne 0) {
    throw "Resolve-Jdk17.ps1 exited with $LASTEXITCODE"
}
if ($jdkHomeOutput.Count -ne 1) {
    throw "Resolve-Jdk17.ps1 must output exactly one path, got $($jdkHomeOutput.Count) lines"
}

$jdkHome = [string] $jdkHomeOutput[0]
$javaExe = Join-Path $jdkHome 'bin\java.exe'
$javaVersionOutput = @(cmd.exe /d /c "`"$javaExe`" -version 2>&1")
if ($LASTEXITCODE -ne 0) {
    throw "Resolved Java executable failed with $LASTEXITCODE"
}
$javaVersion = ($javaVersionOutput | Select-Object -First 1).ToString()
if ($javaVersion -notmatch 'version "17(?:\.|\")') {
    throw "Expected Java 17, got: $javaVersion"
}

& $mavenWrapper -MavenArgs @('-version')
if ($LASTEXITCODE -ne 0) {
    throw "Invoke-MavenJdk17.ps1 exited with $LASTEXITCODE"
}
if ($env:JAVA_HOME -ne $beforeJavaHome) {
    throw 'The Maven wrapper modified JAVA_HOME in the parent process'
}
if ($env:Path -ne $beforePath) {
    throw 'The Maven wrapper modified Path in the parent process'
}

Write-Host 'JDK 17 toolchain contract passed.'
