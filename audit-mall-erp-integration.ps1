$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$platformRoot = Get-ChildItem -LiteralPath $workspace -Directory | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName "yudao-cloud\script\docker\docker-compose-local-infra.yml")
} | Select-Object -First 1
if (-not $platformRoot) { throw "Could not locate the yudao-cloud directory." }
$composeFile = Join-Path $platformRoot.FullName "yudao-cloud\script\docker\docker-compose-local-infra.yml"
$sql = @"
select
 (select count(*) from product_sku s join product_spu p on p.id=s.spu_id and p.tenant_id=s.tenant_id and p.deleted=b'0' where s.tenant_id=121 and s.deleted=b'0' and p.status=1) active_mall_skus,
 (select count(distinct m.erp_product_id) from mall_erp_product_mapping m join product_sku s on s.id=m.mall_sku_id and s.tenant_id=m.tenant_id and s.deleted=b'0' join product_spu p on p.id=s.spu_id and p.tenant_id=s.tenant_id and p.status=1 and p.deleted=b'0' join erp_product e on e.id=m.erp_product_id and e.tenant_id=m.tenant_id and e.deleted=b'0' where m.tenant_id=121 and m.deleted=b'0') erp_products,
 (select count(distinct m.mall_sku_id) from mall_erp_product_mapping m join product_sku s on s.id=m.mall_sku_id and s.tenant_id=m.tenant_id and s.deleted=b'0' join product_spu p on p.id=s.spu_id and p.tenant_id=s.tenant_id and p.status=1 and p.deleted=b'0' where m.tenant_id=121 and m.deleted=b'0') unique_mappings,
 (select count(*) from mall_erp_product_mapping m left join product_sku s on s.id=m.mall_sku_id and s.tenant_id=m.tenant_id and s.deleted=b'0' left join erp_product e on e.id=m.erp_product_id and e.tenant_id=m.tenant_id and e.deleted=b'0' where m.tenant_id=121 and m.deleted=b'0' and (s.id is null or e.id is null)) orphan_mappings,
 (select count(*) from mall_erp_product_mapping m join product_sku s on s.id=m.mall_sku_id and s.deleted=b'0' join erp_product e on e.id=m.erp_product_id and e.deleted=b'0' where m.tenant_id=121 and m.deleted=b'0' and (s.tenant_id<>m.tenant_id or e.tenant_id<>m.tenant_id)) cross_tenant_mappings,
 (select count(*) from erp_stock st left join erp_warehouse w on w.id=st.warehouse_id and w.tenant_id=st.tenant_id and w.deleted=b'0' where st.tenant_id=121 and st.deleted=b'0' and w.id is null) stock_without_warehouse,
 (select count(*) from erp_product e left join mall_erp_product_mapping m on m.erp_product_id=e.id and m.tenant_id=e.tenant_id and m.deleted=b'0' where e.tenant_id=121 and e.status=0 and e.deleted=b'0' and m.id is null) unmapped_erp_products;
"@
$output = & docker compose -f $composeFile exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -B ruoyi-vue-pro -e $sql
if ($LASTEXITCODE -ne 0) { throw "Mall ERP audit query failed." }
$values = ($output | Select-Object -Last 1) -split "`t"
if ($values.Count -ne 7) { throw "Unexpected audit result: $output" }
$result = [ordered]@{ active_mall_skus=[int]$values[0]; erp_products=[int]$values[1]; unique_mappings=[int]$values[2]; orphan_mappings=[int]$values[3]; cross_tenant_mappings=[int]$values[4]; stock_without_warehouse=[int]$values[5]; unmapped_erp_products=[int]$values[6] }
$result.GetEnumerator() | ForEach-Object { Write-Host ("audit {0}={1}" -f $_.Key,$_.Value) }
if ($result.active_mall_skus -le 0 -or $result.erp_products -ne $result.active_mall_skus -or $result.unique_mappings -ne $result.active_mall_skus -or $result.orphan_mappings -ne 0 -or $result.cross_tenant_mappings -ne 0 -or $result.stock_without_warehouse -ne 0 -or $result.unmapped_erp_products -ne 0) { throw "Tenant 121 mall ERP audit failed." }
