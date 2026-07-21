$ErrorActionPreference = "Stop"
$TenantId = 121
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$platformRoot = Get-ChildItem -LiteralPath $workspace -Directory | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName "yudao-cloud\script\docker\docker-compose-local-infra.yml")
} | Select-Object -First 1
if (-not $platformRoot) { throw "Could not locate the yudao-cloud directory." }
$composeFile = Join-Path $platformRoot.FullName "yudao-cloud\script\docker\docker-compose-local-infra.yml"

function Invoke-MySql([string]$Sql) {
    $output = & docker compose -f $composeFile exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -B ruoyi-vue-pro -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed." }
    return $output
}

$mallSkuCount = [int](Invoke-MySql "select count(*) from product_sku s join product_spu p on p.id=s.spu_id and p.tenant_id=s.tenant_id and p.deleted=b'0' where s.tenant_id=$TenantId and s.deleted=b'0' and p.status=1;")
if ($mallSkuCount -le 0) { throw "No active tenant-$TenantId mall SKUs were found." }
$ExpectedSkuCount = $mallSkuCount

$sql = @"
START TRANSACTION;
INSERT INTO erp_product_unit(name,status,creator,updater,tenant_id)
VALUES('Piece',0,'mall-erp-seed','mall-erp-seed',$TenantId)
ON DUPLICATE KEY UPDATE status=VALUES(status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO erp_product_category(parent_id,name,code,sort,status,creator,updater,tenant_id)
VALUES(0,'Furniture','FURNITURE',10,0,'mall-erp-seed','mall-erp-seed',$TenantId)
ON DUPLICATE KEY UPDATE name=VALUES(name),status=VALUES(status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
SET @erp_root_category_id = (SELECT id FROM erp_product_category WHERE tenant_id=$TenantId AND code='FURNITURE' AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO erp_product_category(parent_id,name,code,sort,status,creator,updater,tenant_id)
SELECT @erp_root_category_id,c.name,CONCAT('MALL_CATEGORY_',c.id),c.sort,0,'mall-erp-seed','mall-erp-seed',$TenantId
FROM product_category c
WHERE c.tenant_id=$TenantId AND c.deleted=b'0' AND EXISTS (
  SELECT 1 FROM product_spu p WHERE p.tenant_id=c.tenant_id AND p.category_id=c.id AND p.status=1 AND p.deleted=b'0'
)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),sort=VALUES(sort),status=VALUES(status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO erp_warehouse(name,address,sort,remark,principal,warehouse_price,truckage_price,status,default_status,creator,updater,tenant_id)
VALUES('Main Warehouse','',10,'Tenant 121 demo inventory','',0,0,0,b'1','mall-erp-seed','mall-erp-seed',$TenantId)
ON DUPLICATE KEY UPDATE status=VALUES(status),default_status=VALUES(default_status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;

INSERT INTO erp_product(name,bar_code,category_id,unit_id,status,standard,remark,expiry_day,weight,purchase_price,sale_price,min_price,creator,updater,tenant_id)
SELECT p.name,CONCAT('RH-', 121, '-', s.id),c.id,u.id,0,CONCAT('Mall SKU ',s.id),'Synchronized from tenant 121 mall catalog',0,COALESCE(s.weight,0),s.cost_price/100,s.price/100,s.price/100,'mall-erp-seed','mall-erp-seed',$TenantId
FROM product_sku s JOIN product_spu p ON p.id=s.spu_id AND p.tenant_id=s.tenant_id AND p.deleted=b'0'
JOIN product_category pc ON pc.id=p.category_id AND pc.tenant_id=p.tenant_id AND pc.deleted=b'0'
JOIN erp_product_category c ON c.tenant_id=$TenantId AND c.code=CONCAT('MALL_CATEGORY_',pc.id) AND c.deleted=b'0'
JOIN erp_product_unit u ON u.tenant_id=$TenantId AND u.name='Piece' AND u.deleted=b'0'
WHERE s.tenant_id=$TenantId AND s.deleted=b'0' AND p.status=1
ON DUPLICATE KEY UPDATE name=VALUES(name),category_id=VALUES(category_id),unit_id=VALUES(unit_id),purchase_price=VALUES(purchase_price),sale_price=VALUES(sale_price),min_price=VALUES(min_price),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;

INSERT INTO mall_erp_product_mapping(mall_spu_id,mall_sku_id,erp_product_id,erp_product_code,sync_status,last_synced_at,last_error,version,creator,updater,tenant_id)
SELECT s.spu_id,s.id,e.id,e.bar_code,'SUCCESS',CURRENT_TIMESTAMP,'',0,'mall-erp-seed','mall-erp-seed',$TenantId
FROM product_sku s JOIN product_spu p ON p.id=s.spU_id AND p.tenant_id=s.tenant_id AND p.deleted=b'0'
JOIN erp_product e ON e.tenant_id=$TenantId AND e.bar_code=CONCAT('RH-', 121, '-', s.id) AND e.deleted=b'0'
WHERE s.tenant_id=$TenantId AND s.deleted=b'0' AND p.status=1
ON DUPLICATE KEY UPDATE erp_product_id=VALUES(erp_product_id),erp_product_code=VALUES(erp_product_code),sync_status='SUCCESS',last_synced_at=CURRENT_TIMESTAMP,last_error='',updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;

INSERT INTO erp_stock(product_id,warehouse_id,count,creator,updater,tenant_id)
SELECT m.erp_product_id,w.id,s.stock,'mall-erp-seed','mall-erp-seed',$TenantId
FROM mall_erp_product_mapping m JOIN product_sku s ON s.id=m.mall_sku_id AND s.tenant_id=m.tenant_id AND s.deleted=b'0'
JOIN erp_warehouse w ON w.tenant_id=$TenantId AND w.name='Main Warehouse' AND w.deleted=b'0'
WHERE m.tenant_id=$TenantId AND m.deleted=b'0'
ON DUPLICATE KEY UPDATE count=VALUES(count),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
COMMIT;
"@
Invoke-MySql $sql | Out-Null

$mappingCount = [int](Invoke-MySql "select count(distinct m.mall_sku_id) from mall_erp_product_mapping m join product_sku s on s.id=m.mall_sku_id and s.tenant_id=m.tenant_id and s.deleted=b'0' join product_spu p on p.id=s.spu_id and p.tenant_id=s.tenant_id and p.deleted=b'0' where m.tenant_id=$TenantId and m.deleted=b'0' and p.status=1;")
if ($mappingCount -ne $ExpectedSkuCount) { throw "Expected $ExpectedSkuCount ERP mappings, found $mappingCount." }
Write-Host "Tenant $TenantId ERP bootstrap complete: $mappingCount product mappings."

# Idempotency lookup used by the service/bootstrap contract:
# select id from mall_erp_product_mapping where tenant_id=? and mall_sku_id=? and deleted=b'0'
