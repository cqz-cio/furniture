param(
  [switch] $VerifyOnly,
  [string] $Profile = "local"
)

$ErrorActionPreference = "Stop"

$workspace = "D:\code"
$env:MAVEN_HOME = Join-Path $workspace "tools\maven\apache-maven-3.9.9"

$yudaoRoot = Get-ChildItem -LiteralPath $workspace -Directory |
  Where-Object { $_.Name -like "yudao*" } |
  Select-Object -First 1

if ($null -eq $yudaoRoot) {
  throw "Cannot find a yudao project directory under $workspace."
}

$backendRoot = Join-Path $yudaoRoot.FullName "yudao-cloud"
if (-not (Test-Path -LiteralPath $backendRoot)) {
  throw "Cannot find backend directory: $backendRoot"
}

$jdkResolver = Join-Path $backendRoot "script\jdk17\Resolve-Jdk17.ps1"
$jdkHomeOutput = @(& $jdkResolver)
if ($LASTEXITCODE -ne 0 -or $jdkHomeOutput.Count -ne 1) {
  throw "Unable to resolve exactly one Java 17 home."
}
$env:JAVA_HOME = [string] $jdkHomeOutput[0]
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"

$java = Join-Path $env:JAVA_HOME "bin\java.exe"
$mvn = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
$settings = Join-Path $workspace "tools\maven-settings.xml"

if ($VerifyOnly) {
  Write-Output "JAVA_HOME=$env:JAVA_HOME"
  Write-Output "MAVEN_HOME=$env:MAVEN_HOME"
  Write-Output "BACKEND_ROOT=$backendRoot"
  Write-Output "SPRING_PROFILES_ACTIVE=$Profile"
  & cmd.exe /d /c "`"$java`" -version 2>&1"
  & $mvn -v
  exit 0
}

Set-Location $backendRoot
& $mvn -s $settings -pl yudao-server -am -DskipTests package

$serverJar = Join-Path $backendRoot "yudao-server\target\yudao-server.jar"
if (-not (Test-Path -LiteralPath $serverJar)) {
  throw "Cannot find packaged backend jar: $serverJar"
}

& $java -jar $serverJar "--spring.profiles.active=$Profile"
