DELIMITER //

DROP PROCEDURE IF EXISTS `add_member_user_column_if_missing`//
CREATE PROCEDURE `add_member_user_column_if_missing`(
    IN column_name varchar(64),
    IN column_definition varchar(255),
    IN after_column varchar(64)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'member_user'
          AND COLUMN_NAME = column_name
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `member_user` ADD COLUMN `',
            column_name,
            '` ',
            column_definition,
            IF(after_column IS NULL OR after_column = '', '', CONCAT(' AFTER `', after_column, '`'))
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

ALTER TABLE `member_user`
    MODIFY COLUMN `mobile` varchar(11) NULL;

CALL `add_member_user_column_if_missing`('email', 'varchar(255) NULL', 'mobile');
CALL `add_member_user_column_if_missing`('email_verified', 'bit(1) NOT NULL DEFAULT b''0''', 'email');
CALL `add_member_user_column_if_missing`('email_verified_time', 'datetime NULL', 'email_verified');
CALL `add_member_user_column_if_missing`('trade_id', 'varchar(64) NULL', 'email_verified_time');
CALL `add_member_user_column_if_missing`('register_terminal', 'tinyint NULL', 'register_ip');

DROP PROCEDURE IF EXISTS `add_member_user_column_if_missing`;

SET @member_user_email_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND INDEX_NAME = 'uk_email'
);
SET @member_user_email_index_sql = IF(
    @member_user_email_index_exists = 0,
    'CREATE UNIQUE INDEX `uk_email` ON `member_user` (`email`)',
    'SELECT 1'
);
PREPARE stmt FROM @member_user_email_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `system_login_log`
    MODIFY COLUMN `username` varchar(255) NULL DEFAULT NULL;

CREATE TABLE IF NOT EXISTS `member_email_auth` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
    `user_id` bigint NULL COMMENT 'member user id',
    `email` varchar(255) NOT NULL COMMENT 'email',
    `scene` tinyint NOT NULL COMMENT 'scene',
    `credential_type` tinyint NOT NULL COMMENT '1 token, 2 code',
    `credential_hash` varchar(64) NOT NULL COMMENT 'sha256 credential hash',
    `expires_time` datetime NOT NULL COMMENT 'expires time',
    `used` bit(1) NOT NULL DEFAULT b'0' COMMENT 'used flag',
    `used_time` datetime NULL COMMENT 'used time',
    `create_ip` varchar(45) NULL COMMENT 'create ip',
    `used_ip` varchar(45) NULL COMMENT 'used ip',
    `creator` varchar(64) NULL DEFAULT '' COMMENT 'creator',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `updater` varchar(64) NULL DEFAULT '' COMMENT 'updater',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'deleted flag',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'tenant id',
    PRIMARY KEY (`id`),
    KEY `idx_email_scene_type` (`email`, `scene`, `credential_type`),
    KEY `idx_credential_lookup` (`credential_hash`, `email`, `scene`, `credential_type`)
) COMMENT='member email auth records';
INSERT INTO `system_mail_template` (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT 'Member email verification', 'member-email-verify-link', 2, 'Restoration Hardware', 'Verify your email', '<p>Please verify your email.</p><p><a href="{link}">Verify email</a></p><p>This link expires in {expireMinutes} minutes.</p>', '["link","token","expireMinutes"]', 0, 'member email auth default template', '', '', b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'member-email-verify-link' AND `deleted` = b'0');

INSERT INTO `system_mail_template` (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT 'Member secure login', 'member-email-secure-login-link', 2, 'Restoration Hardware', 'Your secure login link', '<p>Use this link to sign in.</p><p><a href="{link}">Sign in</a></p><p>This link expires in {expireMinutes} minutes.</p>', '["link","token","expireMinutes"]', 0, 'member email auth default template', '', '', b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'member-email-secure-login-link' AND `deleted` = b'0');

INSERT INTO `system_mail_template` (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT 'Member reset password', 'member-email-reset-password-link', 2, 'Restoration Hardware', 'Reset your password', '<p>Use this link to reset your password.</p><p><a href="{link}">Reset password</a></p><p>This link expires in {expireMinutes} minutes.</p>', '["link","token","expireMinutes"]', 0, 'member email auth default template', '', '', b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'member-email-reset-password-link' AND `deleted` = b'0');

INSERT INTO `system_mail_template` (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT 'Member update password code', 'member-email-update-password-code', 2, 'Restoration Hardware', 'Your password verification code', '<p>Your verification code is <strong>{code}</strong>.</p><p>It expires in {expireMinutes} minutes.</p>', '["code","expireMinutes"]', 0, 'member email auth default template', '', '', b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'member-email-update-password-code' AND `deleted` = b'0');

INSERT INTO `system_mail_template` (`name`, `code`, `account_id`, `nickname`, `title`, `content`, `params`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT 'Member email code', 'member-email-code', 2, 'Restoration Hardware', 'Your verification code', '<p>Your verification code is <strong>{code}</strong>.</p><p>It expires in {expireMinutes} minutes.</p>', '["code","expireMinutes"]', 0, 'member email auth default template', '', '', b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_mail_template` WHERE `code` = 'member-email-code' AND `deleted` = b'0');
