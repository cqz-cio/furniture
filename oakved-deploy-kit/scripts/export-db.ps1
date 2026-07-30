param(
  [string]$HostName = "127.0.0.1",
  [int]$Port = 3306,
  [string]$Database = "ruoyi-vue-pro",
  [string]$User = "root",
  [string]$Password = $env:OAKVED_DB_PASSWORD,
  [string]$OutFile = "D:\code\oakved-release\oakved-full.sql"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Password)) {
  throw "Database password is required. Set OAKVED_DB_PASSWORD before running this script."
}

function Find-Mysqldump {
  $cmd = Get-Command mysqldump -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }

  $candidates = @(
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
    "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysqldump.exe",
    "C:\ProgramData\chocolatey\bin\mysqldump.exe"
  )
  foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate) { return $candidate }
  }
  return $null
}

$mysqldump = Find-Mysqldump
if (-not $mysqldump) {
  throw "mysqldump was not found. Install MySQL Client or add mysqldump.exe to PATH, then run this script again."
}

$outDir = Split-Path -Parent $OutFile
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$previousMysqlPwd = $env:MYSQL_PWD
try {
  $env:MYSQL_PWD = $Password

  & $mysqldump `
    "-h$HostName" `
    "-P$Port" `
    "-u$User" `
    "--single-transaction" `
    "--routines" `
    "--triggers" `
    "--events" `
    "--default-character-set=utf8mb4" `
    $Database |
    Set-Content -LiteralPath $OutFile -Encoding UTF8

  if ($LASTEXITCODE -ne 0) {
    throw "mysqldump failed with exit code $LASTEXITCODE."
  }
} finally {
  if ($null -eq $previousMysqlPwd) {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
  } else {
    $env:MYSQL_PWD = $previousMysqlPwd
  }
}

Write-Host "Database exported to $OutFile"
