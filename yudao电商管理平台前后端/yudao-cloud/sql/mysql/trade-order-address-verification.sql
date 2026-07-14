SET @address_verification_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'address_verification'
);

SET @address_verification_sql = IF(
    @address_verification_exists = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `address_verification` json DEFAULT NULL COMMENT ''Address verification audit payload'' AFTER `user_remark`',
    'SELECT ''trade_order.address_verification already exists'' AS message'
);

PREPARE address_verification_stmt FROM @address_verification_sql;
EXECUTE address_verification_stmt;
DEALLOCATE PREPARE address_verification_stmt;
