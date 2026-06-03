param(
  [switch] $VerifyOnly
)

$ErrorActionPreference = "Stop"

$workspace = "D:\code"
$env:JAVA_HOME = Join-Path $workspace "tools\jdk8\jdk1.8.0_492"
$env:MAVEN_HOME = Join-Path $workspace "tools\maven\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"

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

$mvn = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
$settings = Join-Path $workspace "tools\maven-settings.xml"

if ($VerifyOnly) {
  Write-Host "JAVA_HOME=$env:JAVA_HOME"
  Write-Host "MAVEN_HOME=$env:MAVEN_HOME"
  Write-Host "BACKEND_ROOT=$backendRoot"
  & java -version
  & $mvn -v
  exit 0
}

Set-Location $backendRoot
& $mvn -s $settings -pl yudao-server -am -DskipTests package

$serverJar = Join-Path $backendRoot "yudao-server\target\yudao-server.jar"
if (-not (Test-Path -LiteralPath $serverJar)) {
  throw "Cannot find packaged backend jar: $serverJar"
}

& java -jar $serverJar
