CREATE TABLE IF NOT EXISTS `member_membership` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `member_id` varchar(64) NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `started_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `auto_renew` bit(1) NOT NULL DEFAULT b'0',
  `source_order_id` bigint DEFAULT NULL,
  `source_pay_order_id` bigint DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_membership_user` (`tenant_id`, `user_id`),
  UNIQUE KEY `uk_member_membership_member_id` (`tenant_id`, `member_id`),
  KEY `idx_member_membership_status` (`tenant_id`, `status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Member memberships';

-- Admin route: member/membership/index
-- View permission: member:membership:query
-- Update permission: member:membership:update
