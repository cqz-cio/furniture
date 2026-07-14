SET @detail_config_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_spu'
      AND COLUMN_NAME = 'detail_config'
);

SET @detail_config_sql = IF(
    @detail_config_exists = 0,
    'ALTER TABLE `product_spu` ADD COLUMN `detail_config` json DEFAULT NULL COMMENT ''Furniture Web product detail page configuration'' AFTER `slider_pic_urls`',
    'SELECT ''product_spu.detail_config already exists'' AS message'
);

PREPARE detail_config_stmt FROM @detail_config_sql;
EXECUTE detail_config_stmt;
DEALLOCATE PREPARE detail_config_stmt;
