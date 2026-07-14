$ErrorActionPreference = 'Stop'

$startScript = Join-Path $PSScriptRoot '..\Start-Jdk17Backend.ps1'
$stopScript = Join-Path $PSScriptRoot '..\Stop-Jdk17Backend.ps1'
if (-not (Test-Path -LiteralPath $startScript -PathType Leaf)) {
    throw "Missing launcher: $startScript"
}
if (-not (Test-Path -LiteralPath $stopScript -PathType Leaf)) {
    throw "Missing stop script: $stopScript"
}

$listener = [System.Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 48090)
$listener.Start()
try {
    $rejected = $false
    try {
        & $startScript -Services @('ai-server') -SkipBuild -StartupTimeoutSeconds 2
    } catch {
        $rejected = $_.Exception.Message -match '48090|occupied|占用'
    }
    if (-not $rejected) {
        throw 'Launcher accepted an occupied ai-server port.'
    }
} finally {
    $listener.Stop()
}

$testRunDirectory = Join-Path $env:TEMP "yudao-jdk17-lifecycle-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Force -Path $testRunDirectory | Out-Null
try {
    @{
        Service = 'ai-server'
        Pid = $PID
        JarPath = 'D:\not-this-repository\unrelated.jar'
        StartedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRunDirectory 'ai-server.json') -Encoding UTF8

    & $stopScript -RunDirectory $testRunDirectory
    if (-not (Get-Process -Id $PID -ErrorAction SilentlyContinue)) {
        throw 'Stop script terminated an unrelated process.'
    }
} finally {
    Remove-Item -LiteralPath $testRunDirectory -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'Backend lifecycle contract passed.'
