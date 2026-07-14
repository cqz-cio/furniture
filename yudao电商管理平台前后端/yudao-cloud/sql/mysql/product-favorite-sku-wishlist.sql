CREATE TABLE IF NOT EXISTS `product_favorite` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Favorite id',
    `user_id` bigint NOT NULL COMMENT 'Member user id',
    `spu_id` bigint NOT NULL COMMENT 'Product SPU id',
    `sku_id` bigint NULL COMMENT 'Product SKU id',
    `count` int NOT NULL DEFAULT 1 COMMENT 'Favorite quantity',
    `spu_name` varchar(255) NULL COMMENT 'SPU name snapshot',
    `pic_url` varchar(512) NULL COMMENT 'Product image snapshot',
    `price` int NULL COMMENT 'Product price snapshot in cents',
    `market_price` int NULL COMMENT 'Market price snapshot in cents',
    `color` varchar(128) NULL COMMENT 'Color snapshot',
    `fabric` varchar(255) NULL COMMENT 'Fabric snapshot',
    `width` varchar(128) NULL COMMENT 'Width snapshot',
    `delivery` varchar(255) NULL COMMENT 'Delivery note snapshot',
    `dimensions` varchar(255) NULL COMMENT 'Dimensions snapshot',
    `creator` varchar(64) NULL DEFAULT '' COMMENT 'Creator',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `updater` varchar(64) NULL DEFAULT '' COMMENT 'Updater',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    PRIMARY KEY (`id`),
    KEY `idx_product_favorite_user_spu_sku` (`user_id`, `spu_id`, `sku_id`),
    KEY `idx_product_favorite_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product favorite wishlist';

SET @product_favorite_sku_id_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'sku_id') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `sku_id` bigint NULL COMMENT ''Product SKU id'' AFTER `spu_id`', 'SELECT ''product_favorite.sku_id already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_sku_id_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_count_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'count') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `count` int NOT NULL DEFAULT 1 COMMENT ''Favorite quantity'' AFTER `sku_id`', 'SELECT ''product_favorite.count already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_count_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_spu_name_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'spu_name') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `spu_name` varchar(255) NULL COMMENT ''SPU name snapshot'' AFTER `count`', 'SELECT ''product_favorite.spu_name already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_spu_name_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_pic_url_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'pic_url') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `pic_url` varchar(512) NULL COMMENT ''Product image snapshot'' AFTER `spu_name`', 'SELECT ''product_favorite.pic_url already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_pic_url_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_price_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'price') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `price` int NULL COMMENT ''Product price snapshot in cents'' AFTER `pic_url`', 'SELECT ''product_favorite.price already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_price_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_market_price_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'market_price') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `market_price` int NULL COMMENT ''Market price snapshot in cents'' AFTER `price`', 'SELECT ''product_favorite.market_price already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_market_price_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_color_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'color') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `color` varchar(128) NULL COMMENT ''Color snapshot'' AFTER `market_price`', 'SELECT ''product_favorite.color already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_color_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_fabric_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'fabric') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `fabric` varchar(255) NULL COMMENT ''Fabric snapshot'' AFTER `color`', 'SELECT ''product_favorite.fabric already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_fabric_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_width_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'width') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `width` varchar(128) NULL COMMENT ''Width snapshot'' AFTER `fabric`', 'SELECT ''product_favorite.width already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_width_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_delivery_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'delivery') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `delivery` varchar(255) NULL COMMENT ''Delivery note snapshot'' AFTER `width`', 'SELECT ''product_favorite.delivery already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_delivery_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_dimensions_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'dimensions') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `dimensions` varchar(255) NULL COMMENT ''Dimensions snapshot'' AFTER `delivery`', 'SELECT ''product_favorite.dimensions already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_dimensions_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_tenant_id_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND COLUMN_NAME = 'tenant_id') = 0, 'ALTER TABLE `product_favorite` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT ''Tenant id'' AFTER `deleted`', 'SELECT ''product_favorite.tenant_id already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_tenant_id_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_user_spu_sku_index_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND INDEX_NAME = 'idx_product_favorite_user_spu_sku') = 0, 'CREATE INDEX `idx_product_favorite_user_spu_sku` ON `product_favorite` (`user_id`, `spu_id`, `sku_id`)', 'SELECT ''idx_product_favorite_user_spu_sku already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_user_spu_sku_index_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;

SET @product_favorite_tenant_user_index_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_favorite' AND INDEX_NAME = 'idx_product_favorite_tenant_user') = 0, 'CREATE INDEX `idx_product_favorite_tenant_user` ON `product_favorite` (`tenant_id`, `user_id`)', 'SELECT ''idx_product_favorite_tenant_user already exists'' AS message');
PREPARE product_favorite_stmt FROM @product_favorite_tenant_user_index_sql; EXECUTE product_favorite_stmt; DEALLOCATE PREPARE product_favorite_stmt;
