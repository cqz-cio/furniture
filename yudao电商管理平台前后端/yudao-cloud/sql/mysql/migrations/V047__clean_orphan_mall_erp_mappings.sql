-- Historical product deletions removed mall SPUs/SKUs but left active ERP mappings.
-- Disable the linked ERP products first so they cannot remain sellable, then
-- logically delete only mappings whose mall SKU/SPU no longer exists or whose
-- SKU no longer belongs to the mapped SPU, or whose ERP product is gone.

-- Preserve all unlink history without allowing more than one active mapping.
-- The previous (..., deleted) unique keys allowed only one deleted row and
-- could make a later re-link/unlink cycle fail with a duplicate-key error.
ALTER TABLE `mall_erp_product_mapping`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_mall_erp_mapping_tenant_sku_deleted`,
  DROP INDEX `uk_mall_erp_mapping_tenant_erp_product_deleted`,
  DROP INDEX `uk_mall_erp_mapping_tenant_erp_code_deleted`,
  ADD UNIQUE KEY `uk_mall_erp_mapping_tenant_sku_active`
      (`tenant_id`, `mall_sku_id`, `active_record`),
  ADD UNIQUE KEY `uk_mall_erp_mapping_tenant_erp_product_active`
      (`tenant_id`, `erp_product_id`, `active_record`),
  ADD UNIQUE KEY `uk_mall_erp_mapping_tenant_erp_code_active`
      (`tenant_id`, `erp_product_code`, `active_record`);

UPDATE `erp_product` AS product
INNER JOIN `mall_erp_product_mapping` AS mapping
        ON mapping.`erp_product_id` = product.`id`
       AND mapping.`tenant_id` = product.`tenant_id`
LEFT JOIN `product_sku` AS sku
       ON sku.`id` = mapping.`mall_sku_id`
      AND sku.`tenant_id` = mapping.`tenant_id`
      AND sku.`deleted` = b'0'
LEFT JOIN `product_spu` AS spu
       ON spu.`id` = mapping.`mall_spu_id`
      AND spu.`tenant_id` = mapping.`tenant_id`
      AND spu.`deleted` = b'0'
SET product.`status` = 1,
    product.`updater` = 'V047',
    product.`update_time` = CURRENT_TIMESTAMP
WHERE mapping.`deleted` = b'0'
  AND product.`deleted` = b'0'
  AND (sku.`id` IS NULL OR spu.`id` IS NULL OR sku.`spu_id` <> mapping.`mall_spu_id`);

INSERT INTO `mall_erp_sync_log`
  (`entity_type`, `entity_id`, `direction`, `event_type`, `idempotency_key`,
   `request_summary`, `sync_status`, `last_error`, `retry_count`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'PRODUCT_SKU', mapping.`mall_sku_id`, 'MALL_TO_ERP', 'UNLINK',
       CONCAT('V047-orphan-unlink-', mapping.`id`),
       CONCAT('mallSkuId=', mapping.`mall_sku_id`,
              ',erpProductId=', mapping.`erp_product_id`),
       'SUCCESS', '', 0, 'V047', CURRENT_TIMESTAMP, 'V047', CURRENT_TIMESTAMP,
       b'0', mapping.`tenant_id`
FROM `mall_erp_product_mapping` AS mapping
LEFT JOIN `product_sku` AS sku
       ON sku.`id` = mapping.`mall_sku_id`
      AND sku.`tenant_id` = mapping.`tenant_id`
      AND sku.`deleted` = b'0'
LEFT JOIN `product_spu` AS spu
       ON spu.`id` = mapping.`mall_spu_id`
      AND spu.`tenant_id` = mapping.`tenant_id`
      AND spu.`deleted` = b'0'
LEFT JOIN `erp_product` AS product
       ON product.`id` = mapping.`erp_product_id`
      AND product.`tenant_id` = mapping.`tenant_id`
      AND product.`deleted` = b'0'
WHERE mapping.`deleted` = b'0'
  AND (sku.`id` IS NULL OR spu.`id` IS NULL OR sku.`spu_id` <> mapping.`mall_spu_id`
       OR product.`id` IS NULL)
  AND NOT EXISTS (
    SELECT 1
    FROM `mall_erp_sync_log` AS existing
    WHERE existing.`tenant_id` = mapping.`tenant_id`
      AND existing.`idempotency_key` = CONCAT('V047-orphan-unlink-', mapping.`id`)
      AND existing.`deleted` = b'0'
  );

UPDATE `mall_erp_product_mapping` AS mapping
LEFT JOIN `product_sku` AS sku
       ON sku.`id` = mapping.`mall_sku_id`
      AND sku.`tenant_id` = mapping.`tenant_id`
      AND sku.`deleted` = b'0'
LEFT JOIN `product_spu` AS spu
       ON spu.`id` = mapping.`mall_spu_id`
      AND spu.`tenant_id` = mapping.`tenant_id`
      AND spu.`deleted` = b'0'
LEFT JOIN `erp_product` AS product
       ON product.`id` = mapping.`erp_product_id`
      AND product.`tenant_id` = mapping.`tenant_id`
      AND product.`deleted` = b'0'
SET mapping.`deleted` = b'1',
    mapping.`sync_status` = 'UNLINKED',
    mapping.`last_error` = '',
    mapping.`last_synced_at` = CURRENT_TIMESTAMP,
    mapping.`updater` = 'V047',
    mapping.`update_time` = CURRENT_TIMESTAMP
WHERE mapping.`deleted` = b'0'
  AND (sku.`id` IS NULL OR spu.`id` IS NULL OR sku.`spu_id` <> mapping.`mall_spu_id`
       OR product.`id` IS NULL);
