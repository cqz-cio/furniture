$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path

function Import-DotEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            return
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

Import-DotEnv -Path (Join-Path $workspace ".env")

$env:JAVA_HOME = "D:\code\tools\jdk8\jdk1.8.0_492"
$env:MAVEN_HOME = "D:\code\tools\maven\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"

$backendRoot = Get-ChildItem -LiteralPath $workspace -Directory |
    ForEach-Object { Join-Path $_.FullName "yudao-cloud" } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1

if (-not $backendRoot) {
    throw "Cannot find yudao-cloud under $workspace."
}

$maven = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
$settings = "D:\code\tools\maven-settings.xml"
$serverJar = Join-Path $backendRoot "yudao-server\target\yudao-server.jar"

Set-Location $backendRoot
& $maven -s $settings -pl yudao-server -am -DskipTests package

if (-not (Test-Path -LiteralPath $serverJar)) {
    throw "Cannot find packaged backend jar: $serverJar"
}

& java -jar $serverJar
