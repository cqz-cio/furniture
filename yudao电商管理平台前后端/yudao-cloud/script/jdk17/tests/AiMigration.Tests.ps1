[CmdletBinding()]
param(
    [string]$ContainerName = 'yudao-mysql-local',
    [string]$DatabaseName = 'ruoyi-vue-pro',
    [string]$MySqlUser = 'root'
)

$ErrorActionPreference = 'Stop'

$password = $env:YUDAO_MYSQL_ROOT_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'YUDAO_MYSQL_ROOT_PASSWORD is required.'
}

$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path 'sql\mysql\ai-module-enable.sql'
$migrationText = Get-Content -LiteralPath $migrationPath -Raw
if ($migrationText -notmatch '(?s)INSERT INTO system_role_menu.*?WITH RECURSIVE ai_menus') {
    throw 'AI role-menu migration must populate the complete recursive menu subtree.'
}

function Invoke-MySqlQuery {
    param([Parameter(Mandatory)][string]$Sql)

    $output = & docker exec -e "MYSQL_PWD=$password" $ContainerName `
        mysql "-u$MySqlUser" -D $DatabaseName -Nse $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $($output -join [Environment]::NewLine)"
    }
    return @($output | Where-Object { $_ -is [string] -and $_.Trim().Length -gt 0 })
}

$requiredTables = @(
    'ai_api_key',
    'ai_model',
    'ai_chat_role',
    'ai_chat_conversation',
    'ai_chat_message',
    'ai_image',
    'ai_knowledge',
    'ai_knowledge_document',
    'ai_knowledge_segment',
    'ai_mind_map',
    'ai_tool',
    'ai_music',
    'ai_workflow',
    'ai_write'
)

$actualTables = Invoke-MySqlQuery -Sql (
    "SELECT table_name FROM information_schema.tables " +
    "WHERE table_schema = '$DatabaseName' AND table_name LIKE 'ai\_%' ORDER BY table_name"
)
$missingTables = @($requiredTables | Where-Object { $_ -notin $actualTables })
if ($missingTables.Count -gt 0) {
    throw "Missing AI tables: $($missingTables -join ', ')"
}

$tenantTables = Invoke-MySqlQuery -Sql (
    "SELECT table_name FROM information_schema.columns " +
    "WHERE table_schema = '$DatabaseName' AND column_name = 'tenant_id' " +
    "AND table_name LIKE 'ai\_%' ORDER BY table_name"
)
$tablesWithoutTenant = @($requiredTables | Where-Object { $_ -notin $tenantTables })
if ($tablesWithoutTenant.Count -gt 0) {
    throw "AI tables missing tenant_id: $($tablesWithoutTenant -join ', ')"
}

$duplicateMenus = Invoke-MySqlQuery -Sql @"
WITH RECURSIVE ai_menus AS (
  SELECT id, parent_id, name, permission FROM system_menu WHERE deleted = 0 AND path IN ('ai', '/ai')
  UNION ALL
  SELECT m.id, m.parent_id, m.name, m.permission
  FROM system_menu m JOIN ai_menus p ON m.parent_id = p.id
  WHERE m.deleted = 0
)
SELECT CONCAT(parent_id, ':', name, ':', COALESCE(permission, ''))
FROM ai_menus
GROUP BY parent_id, name, permission
HAVING COUNT(*) > 1
"@
if ($duplicateMenus.Count -gt 0) {
    throw "Duplicate AI menus: $($duplicateMenus -join ', ')"
}

$duplicateRoleMenus = Invoke-MySqlQuery -Sql @"
WITH RECURSIVE ai_menus AS (
  SELECT id FROM system_menu WHERE deleted = 0 AND path IN ('ai', '/ai')
  UNION ALL
  SELECT m.id FROM system_menu m JOIN ai_menus p ON m.parent_id = p.id WHERE m.deleted = 0
)
SELECT CONCAT(rm.role_id, ':', rm.menu_id)
FROM system_role_menu rm JOIN ai_menus m ON m.id = rm.menu_id
WHERE rm.deleted = 0
GROUP BY rm.role_id, rm.menu_id
HAVING COUNT(*) > 1
"@
if ($duplicateRoleMenus.Count -gt 0) {
    throw "Duplicate role-menu mappings: $($duplicateRoleMenus -join ', ')"
}

$unmappedAiMenus = Invoke-MySqlQuery -Sql @"
WITH RECURSIVE ai_menus AS (
  SELECT id FROM system_menu WHERE deleted = 0 AND path IN ('ai', '/ai')
  UNION ALL
  SELECT m.id FROM system_menu m JOIN ai_menus p ON m.parent_id = p.id WHERE m.deleted = 0
)
SELECT m.id
FROM ai_menus m
JOIN system_role r ON r.code = 'super_admin' AND r.deleted = 0
LEFT JOIN system_role_menu rm ON rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
WHERE rm.id IS NULL
"@
if ($unmappedAiMenus.Count -gt 0) {
    throw "AI menus missing super-admin mapping: $($unmappedAiMenus -join ', ')"
}

$enabledKeysWithoutValue = Invoke-MySqlQuery -Sql @"
SELECT id
FROM ai_api_key
WHERE deleted = 0 AND status = 0 AND (api_key IS NULL OR TRIM(api_key) = '')
"@
if ($enabledKeysWithoutValue.Count -gt 0) {
    throw "Enabled AI API key rows have no key value: $($enabledKeysWithoutValue -join ', ')"
}

Write-Output "AI migration contract passed: $($requiredTables.Count) tables present."
