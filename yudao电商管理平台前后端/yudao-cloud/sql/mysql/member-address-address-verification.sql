SET @member_address_verification_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_address'
      AND COLUMN_NAME = 'address_verification'
);

SET @member_address_verification_sql = IF(
    @member_address_verification_exists = 0,
    'ALTER TABLE `member_address` ADD COLUMN `address_verification` json DEFAULT NULL COMMENT ''Address verification audit payload'' AFTER `default_status`',
    'SELECT ''member_address.address_verification already exists'' AS message'
);

PREPARE member_address_verification_stmt FROM @member_address_verification_sql;
EXECUTE member_address_verification_stmt;
DEALLOCATE PREPARE member_address_verification_stmt;
