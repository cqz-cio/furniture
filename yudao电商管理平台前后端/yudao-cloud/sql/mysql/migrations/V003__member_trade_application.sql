CREATE TABLE IF NOT EXISTS `member_trade_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_name` varchar(255) NOT NULL,
  `country` varchar(64) DEFAULT NULL,
  `street` varchar(255) DEFAULT NULL,
  `address2` varchar(255) DEFAULT NULL,
  `city` varchar(128) DEFAULT NULL,
  `state` varchar(128) DEFAULT NULL,
  `postal_code` varchar(32) DEFAULT NULL,
  `business_description` text,
  `website` varchar(512) DEFAULT NULL,
  `portfolio` varchar(512) DEFAULT NULL,
  `instagram` varchar(255) DEFAULT NULL,
  `pinterest` varchar(255) DEFAULT NULL,
  `houzz` varchar(255) DEFAULT NULL,
  `linkedin` varchar(255) DEFAULT NULL,
  `primary_email` varchar(255) NOT NULL,
  `authorized_users_json` json DEFAULT NULL,
  `business_documents_json` json DEFAULT NULL,
  `tax_documents_json` json DEFAULT NULL,
  `email_opt_in` bit(1) NOT NULL DEFAULT b'0',
  `status` tinyint NOT NULL DEFAULT 0,
  `trade_id` varchar(64) DEFAULT NULL,
  `review_reason` varchar(1024) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_trade_application_trade_id` (`tenant_id`, `trade_id`),
  KEY `idx_member_trade_application_status` (`tenant_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Member trade applications';

-- Admin route: member/trade/application/index
-- View permission: member:trade-application:query
-- Review permission: member:trade-application:review
-- Component: MemberTradeApplication
