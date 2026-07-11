$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$platformRoot = Get-ChildItem -LiteralPath $workspace -Directory | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName "yudao-cloud\script\docker\docker-compose-local-infra.yml")
} | Select-Object -First 1
if (-not $platformRoot) {
    throw "Could not locate the yudao-cloud directory."
}
$composeFile = Join-Path $platformRoot.FullName "yudao-cloud\script\docker\docker-compose-local-infra.yml"
$sql = @"
select
  (select count(*) from product_spu where tenant_id=121 and creator='furniture-agent-seed' and status=1 and deleted=b'0') as active_products,
  (select count(distinct pic_url) from product_spu where tenant_id=121 and creator='furniture-agent-seed' and status=1 and deleted=b'0') as distinct_covers,
  (select count(*) from product_spu where tenant_id=121 and creator='furniture-agent-seed' and status=1 and deleted=b'0' and (name='' or name like '%?%' or pic_url not like 'https://%' or json_length(slider_pic_urls)<2 or cost_price>=price or price>=market_price or stock<0)) as invalid_product_fields,
  (select count(*) from product_spu p left join product_category c on c.id=p.category_id and c.deleted=b'0' left join product_brand b on b.id=p.brand_id and b.deleted=b'0' where p.tenant_id=121 and p.creator='furniture-agent-seed' and p.deleted=b'0' and (c.tenant_id<>121 or b.tenant_id<>121 or c.id is null or b.id is null)) as tenant_mismatch,
  (select count(*) from product_spu p left join product_sku s on s.spu_id=p.id and s.tenant_id=p.tenant_id and s.deleted=b'0' where p.tenant_id=121 and p.creator='furniture-agent-seed' and p.deleted=b'0' and (s.id is null or s.pic_url<>p.pic_url or s.price<>p.price or s.market_price<>p.market_price or s.cost_price<>p.cost_price or s.stock<>p.stock)) as sku_mismatch;
"@

$output = & docker compose -f $composeFile exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p123456 -N -B ruoyi-vue-pro -e $sql
if ($LASTEXITCODE -ne 0) {
    throw "Catalog audit query failed."
}

$values = ($output | Select-Object -Last 1) -split "`t"
if ($values.Count -ne 5) {
    throw "Catalog audit returned an unexpected result: $output"
}

$result = [ordered]@{
    active_products = [int]$values[0]
    distinct_covers = [int]$values[1]
    invalid_product_fields = [int]$values[2]
    tenant_mismatch = [int]$values[3]
    sku_mismatch = [int]$values[4]
}

$result.GetEnumerator() | ForEach-Object { Write-Host ("audit {0}={1}" -f $_.Key, $_.Value) }

if ($result.active_products -ne 26 -or
    $result.distinct_covers -ne 26 -or
    $result.invalid_product_fields -ne 0 -or
    $result.tenant_mismatch -ne 0 -or
    $result.sku_mismatch -ne 0) {
    throw "Tenant 121 product catalog audit failed."
}
