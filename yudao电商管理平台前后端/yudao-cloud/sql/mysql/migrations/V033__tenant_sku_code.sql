-- Give each tenant a stable code and use it as the ERP SKU prefix.

ALTER TABLE `system_tenant`
  ADD COLUMN `code` varchar(16) NULL
    COMMENT '租户编码：大写字母和数字，创建后不可修改'
    AFTER `name`;

UPDATE `system_tenant`
SET `code` = CASE
  WHEN `id` = 1 THEN 'SYSTEM'
  WHEN `id` = 121 THEN 'OAKVED'
  WHEN `id` = 162 THEN 'VANZ'
  ELSE CONCAT('T', UPPER(CONV(`id`, 10, 36)))
END
WHERE `code` IS NULL OR `code` = '';

ALTER TABLE `system_tenant`
  MODIFY COLUMN `code` varchar(16) NOT NULL
    COMMENT '租户编码：大写字母和数字，创建后不可修改',
  ADD UNIQUE KEY `uk_system_tenant_code_deleted` (`code`, `deleted`);

UPDATE `erp_product` AS product
INNER JOIN `mall_erp_product_mapping` AS mapping
  ON mapping.`erp_product_id` = product.`id`
 AND mapping.`tenant_id` = product.`tenant_id`
 AND mapping.`deleted` = b'0'
INNER JOIN `system_tenant` AS tenant
  ON tenant.`id` = mapping.`tenant_id`
 AND tenant.`deleted` = b'0'
SET product.`bar_code` = CONCAT(tenant.`code`, '-', mapping.`tenant_id`, '-', mapping.`mall_sku_id`),
    product.`updater` = 'V033-tenant-sku-code',
    product.`update_time` = CURRENT_TIMESTAMP
WHERE product.`deleted` = b'0';

UPDATE `mall_erp_product_mapping` AS mapping
INNER JOIN `system_tenant` AS tenant
  ON tenant.`id` = mapping.`tenant_id`
 AND tenant.`deleted` = b'0'
SET mapping.`erp_product_code` = CONCAT(tenant.`code`, '-', mapping.`tenant_id`, '-', mapping.`mall_sku_id`),
    mapping.`updater` = 'V033-tenant-sku-code',
    mapping.`update_time` = CURRENT_TIMESTAMP
WHERE mapping.`deleted` = b'0';
