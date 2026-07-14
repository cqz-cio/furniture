SET @trade_cart_registry_id_sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_cart' AND COLUMN_NAME = 'registry_id') = 0,
  'ALTER TABLE `trade_cart` ADD COLUMN `registry_id` bigint NULL COMMENT ''Gift registry id'' AFTER `sku_id`',
  'SELECT ''trade_cart.registry_id already exists'' AS message'
);
PREPARE trade_registry_stmt FROM @trade_cart_registry_id_sql; EXECUTE trade_registry_stmt; DEALLOCATE PREPARE trade_registry_stmt;

SET @trade_cart_registry_item_id_sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_cart' AND COLUMN_NAME = 'registry_item_id') = 0,
  'ALTER TABLE `trade_cart` ADD COLUMN `registry_item_id` bigint NULL COMMENT ''Gift registry item id'' AFTER `registry_id`',
  'SELECT ''trade_cart.registry_item_id already exists'' AS message'
);
PREPARE trade_registry_stmt FROM @trade_cart_registry_item_id_sql; EXECUTE trade_registry_stmt; DEALLOCATE PREPARE trade_registry_stmt;

SET @trade_order_item_registry_id_sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order_item' AND COLUMN_NAME = 'registry_id') = 0,
  'ALTER TABLE `trade_order_item` ADD COLUMN `registry_id` bigint NULL COMMENT ''Gift registry id'' AFTER `sku_id`',
  'SELECT ''trade_order_item.registry_id already exists'' AS message'
);
PREPARE trade_registry_stmt FROM @trade_order_item_registry_id_sql; EXECUTE trade_registry_stmt; DEALLOCATE PREPARE trade_registry_stmt;

SET @trade_order_item_registry_item_id_sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order_item' AND COLUMN_NAME = 'registry_item_id') = 0,
  'ALTER TABLE `trade_order_item` ADD COLUMN `registry_item_id` bigint NULL COMMENT ''Gift registry item id'' AFTER `registry_id`',
  'SELECT ''trade_order_item.registry_item_id already exists'' AS message'
);
PREPARE trade_registry_stmt FROM @trade_order_item_registry_item_id_sql; EXECUTE trade_registry_stmt; DEALLOCATE PREPARE trade_registry_stmt;
