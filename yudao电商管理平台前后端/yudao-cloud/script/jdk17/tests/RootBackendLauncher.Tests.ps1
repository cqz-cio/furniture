$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$workspace = (Resolve-Path (Join-Path $cloudRoot '..\..')).Path
$launcher = Join-Path $workspace 'start-yudao-backend.ps1'

$output = @(& $launcher -VerifyOnly 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Root backend launcher verification exited with $LASTEXITCODE.`n$($output -join [Environment]::NewLine)"
}

$javaHomeLine = $output | Where-Object { $_.ToString() -like 'JAVA_HOME=*' } | Select-Object -First 1
if ($null -eq $javaHomeLine -or $javaHomeLine.ToString() -match 'jdk8|1\.8\.0') {
    throw "Root backend launcher selected an obsolete Java home: $javaHomeLine"
}

$versionLine = $output | Where-Object { $_.ToString() -match 'version "' } | Select-Object -First 1
if ($null -eq $versionLine -or $versionLine.ToString() -notmatch 'version "17(?:\.|\")') {
    throw "Root backend launcher must use Java 17, got: $versionLine"
}

Write-Host 'Root backend launcher Java 17 contract passed.'
