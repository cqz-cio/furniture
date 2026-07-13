-- Resumable, tenant-bounded historical cost snapshot backfill.
-- Execute one batch per transaction. Approved tenant IDs are validated by the release runner.
SET @tenant_id = 121;
SET @after_id = 0;
SET @batch_size = 10000;

START TRANSACTION;

SELECT COALESCE(MAX(id), @after_id) INTO @batch_end
FROM (
  SELECT id FROM trade_order_item
  WHERE tenant_id = @tenant_id AND deleted = b'0' AND id > @after_id
  ORDER BY id LIMIT 10000
) batch_ids;

UPDATE trade_order_item item
JOIN product_sku sku ON sku.id = item.sku_id
 AND sku.tenant_id = item.tenant_id AND sku.deleted = b'0'
SET item.cost_price = sku.cost_price,
    item.cost_estimated = b'1',
    item.update_time = NOW(3)
WHERE item.tenant_id = @tenant_id
  AND item.deleted = b'0'
  AND item.id > @after_id
  AND item.id <= @batch_end
  AND item.cost_price IS NULL
  AND sku.cost_price IS NOT NULL;

SET @updated_rows = ROW_COUNT();
SET @after_id = @batch_end;
SELECT @tenant_id AS tenant_id, @after_id AS after_id, @batch_size AS batch_size, @updated_rows AS updated_rows;
COMMIT;
