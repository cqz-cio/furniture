param(
  [Parameter(Mandatory = $true)]
  [string]$PublicOrigin,
  [string]$ServerName = "",
  [string]$TenantId = "121",
  [string]$ReleaseRoot = "D:\code\oakved-release",
  [switch]$SkipBackend,
  [switch]$SkipFurniture,
  [switch]$SkipAdmin,
  [switch]$SkipZip
)

$ErrorActionPreference = "Stop"

$RepoRoot = "D:\code"
$KitRoot = Join-Path $RepoRoot "oakved-deploy-kit"
$FurnitureRoot = Join-Path $RepoRoot "furniture web"
$AdminRoot = Join-Path $RepoRoot "yudao电商管理平台前后端\yudao-ui-admin-vue3"
$BackendRoot = Join-Path $RepoRoot "yudao电商管理平台前后端\yudao-cloud"

function Resolve-RepoChild([string]$leafName) {
  $direct = Join-Path $RepoRoot $leafName
  if (Test-Path -LiteralPath $direct) {
    return (Resolve-Path -LiteralPath $direct).Path
  }

  foreach ($child in Get-ChildItem -LiteralPath $RepoRoot -Directory) {
    $candidate = Join-Path $child.FullName $leafName
    if (Test-Path -LiteralPath $candidate) {
      return (Resolve-Path -LiteralPath $candidate).Path
    }
  }

  throw "Could not find project directory '$leafName' under $RepoRoot."
}

$FurnitureRoot = Resolve-RepoChild "furniture web"
$AdminRoot = Resolve-RepoChild "yudao-ui-admin-vue3"
$BackendRoot = Resolve-RepoChild "yudao-cloud"

function Normalize-Origin([string]$value) {
  return $value.TrimEnd("/")
}

function Replace-Tokens([string]$templatePath, [hashtable]$tokens) {
  $content = Get-Content -LiteralPath $templatePath -Raw
  foreach ($key in $tokens.Keys) {
    $content = $content.Replace($key, [string]$tokens[$key])
  }
  return $content
}

function Backup-And-Write([string]$path, [string]$content) {
  $backup = $null
  if (Test-Path -LiteralPath $path) {
    $backup = "$path.codex-backup"
    Copy-Item -LiteralPath $path -Destination $backup -Force
  }
  Set-Content -LiteralPath $path -Value $content -Encoding UTF8
  return $backup
}

function Restore-Backup([string]$path, [string]$backup) {
  if ($backup -and (Test-Path -LiteralPath $backup)) {
    Move-Item -LiteralPath $backup -Destination $path -Force
  } elseif (-not $backup -and (Test-Path -LiteralPath $path)) {
    Remove-Item -LiteralPath $path -Force
  }
}

function Assert-NativeSuccess([string]$label) {
  if ($LASTEXITCODE -ne 0) {
    throw "$label failed with exit code $LASTEXITCODE."
  }
}

$PublicOrigin = Normalize-Origin $PublicOrigin
if (-not $ServerName) {
  try {
    $uri = [Uri]$PublicOrigin
    $ServerName = if ($uri.Host) { $uri.Host } else { "_" }
  } catch {
    $ServerName = "_"
  }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReleaseDir = Join-Path $ReleaseRoot "oakved-release-$timestamp"
$tokens = @{
  "__PUBLIC_ORIGIN__" = $PublicOrigin
  "__TENANT_ID__" = $TenantId
  "__SERVER_NAME__" = $ServerName
}

New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ReleaseDir "backend") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ReleaseDir "frontend\furniture") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ReleaseDir "frontend\admin") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ReleaseDir "nginx") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ReleaseDir "server") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $ReleaseDir "sql") | Out-Null

Set-Content -LiteralPath (Join-Path $ReleaseDir "backend\backend.env") `
  -Value (Replace-Tokens (Join-Path $KitRoot "env\backend.prod.env.template") $tokens) `
  -Encoding UTF8
Set-Content -LiteralPath (Join-Path $ReleaseDir "nginx\oakved.conf") `
  -Value (Replace-Tokens (Join-Path $KitRoot "nginx\oakved.conf.template") $tokens) `
  -Encoding UTF8
Copy-Item -LiteralPath (Join-Path $KitRoot "server\import-db.sh") -Destination (Join-Path $ReleaseDir "server\import-db.sh") -Force
Copy-Item -LiteralPath (Join-Path $KitRoot "server\start-backend.sh") -Destination (Join-Path $ReleaseDir "server\start-backend.sh") -Force
Copy-Item -LiteralPath (Join-Path $KitRoot "server\oakved-yudao.service") -Destination (Join-Path $ReleaseDir "server\oakved-yudao.service") -Force

if (-not $SkipFurniture) {
  $envPath = Join-Path $FurnitureRoot ".env.production"
  $envBackup = $null
  try {
    $envContent = Replace-Tokens (Join-Path $KitRoot "env\furniture.production.env.template") $tokens
    $envBackup = Backup-And-Write $envPath $envContent
    Push-Location $FurnitureRoot
    npm run build
    Assert-NativeSuccess "Furniture build"
    Pop-Location
    Copy-Item -Path (Join-Path $FurnitureRoot "dist\*") -Destination (Join-Path $ReleaseDir "frontend\furniture") -Recurse -Force
  } finally {
    if ((Get-Location).Path -ne $RepoRoot) { Pop-Location -ErrorAction SilentlyContinue }
    Restore-Backup $envPath $envBackup
  }
}

if (-not $SkipAdmin) {
  $envPath = Join-Path $AdminRoot ".env.prod"
  $envBackup = $null
  try {
    $envContent = Replace-Tokens (Join-Path $KitRoot "env\admin.prod.env.template") $tokens
    $envBackup = Backup-And-Write $envPath $envContent
    Push-Location $AdminRoot
    pnpm build:prod
    Assert-NativeSuccess "Admin build"
    Pop-Location
    Copy-Item -Path (Join-Path $AdminRoot "dist-prod\*") -Destination (Join-Path $ReleaseDir "frontend\admin") -Recurse -Force
  } finally {
    if ((Get-Location).Path -ne $RepoRoot) { Pop-Location -ErrorAction SilentlyContinue }
    Restore-Backup $envPath $envBackup
  }
}

if (-not $SkipBackend) {
  Push-Location $BackendRoot
  mvn -pl yudao-server -am -DskipTests clean package
  Assert-NativeSuccess "Backend build"
  Pop-Location
  $jar = Get-ChildItem -LiteralPath (Join-Path $BackendRoot "yudao-server\target") -Filter "*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc|original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $jar) {
    throw "yudao-server jar was not found."
  }
  Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $ReleaseDir "backend\yudao-server.jar") -Force
}

Set-Content -LiteralPath (Join-Path $ReleaseDir "sql\README.txt") `
  -Value "Put the exported oakved-full.sql database dump in this directory before uploading to the cloud server." `
  -Encoding UTF8

if (-not $SkipZip) {
  $zipPath = "$ReleaseDir.zip"
  Compress-Archive -Path (Join-Path $ReleaseDir "*") -DestinationPath $zipPath -Force
  Write-Host "Release zip created: $zipPath"
}

Write-Host "Release directory: $ReleaseDir"
