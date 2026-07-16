$ErrorActionPreference = 'Stop'

$cloudRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$workspace = (& git -C $cloudRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($workspace)) {
    throw 'Cannot resolve the Git workspace root.'
}
$serverPom = Join-Path $cloudRoot 'yudao-server\pom.xml'
$serverConfig = Join-Path $cloudRoot 'yudao-server\src\main\resources\application.yaml'
$localConfig = Join-Path $cloudRoot 'yudao-server\src\main\resources\application-local.yaml'
$startScript = Join-Path $workspace 'start-yudao-all-backend.ps1'

$pomContent = Get-Content -LiteralPath $serverPom -Raw
if ($pomContent -notmatch '<artifactId>yudao-module-ai-server</artifactId>') {
    throw 'yudao-server must include yudao-module-ai-server for local monolith mode.'
}

$serverConfigContent = Get-Content -LiteralPath $serverConfig -Raw
if ($serverConfigContent -notmatch 'DASHSCOPE_AGENT_ENABLED:false' -or
    $serverConfigContent -notmatch 'SPRING_AI_MODEL_CHAT:none' -or
    $serverConfigContent -notmatch 'SPRING_AI_MODEL_EMBEDDING:none' -or
    $serverConfigContent -notmatch 'SPRING_AI_MODEL_IMAGE:none' -or
    $serverConfigContent -notmatch 'SPRING_AI_ALIBABA_TOOL_ASYNC_ENABLED:false') {
    throw 'The monolith must start without provider API keys and leave provider models disabled by default.'
}

$localConfigContent = Get-Content -LiteralPath $localConfig -Raw
if ($localConfigContent -notmatch '(?ms)^xxl:\s*\r?\n\s+job:\s*\r?\n\s+enabled:\s*false\b') {
    throw 'Local monolith mode must disable XXL-Job when no local admin service is started.'
}

$startContent = Get-Content -LiteralPath $startScript -Raw
if ($startContent -match 'AI_SERVER_PORT=48090|Starting ai-server|Start-Jdk17Backend\.ps1') {
    throw 'The local one-command launcher must not start or advertise a standalone AI service.'
}
if ($startContent -notmatch 'YUDAO_SERVER_PORT=48080' -or
    $startContent -notmatch 'Starting yudao-server' -or
    $startContent -notmatch '/actuator/health') {
    throw 'The local one-command launcher must start and health-check the unified server on 48080.'
}

Write-Output 'Local monolith AI integration contract passed.'
