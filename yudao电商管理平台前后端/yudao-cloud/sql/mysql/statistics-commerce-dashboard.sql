-- Furniture commerce dashboard schema migration (MySQL 8.x).
-- Run after the read-only preflight documented in the dashboard migration spec.
-- DDL is intentionally separate from the bounded historical cost backfill.

SELECT GET_LOCK(CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2'), 60) AS migration_lock;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS `statistics_dashboard_hmac_day` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day` date NOT NULL,
  `hash_key_version` smallint unsigned NOT NULL,
  `activated_at` datetime(3) NOT NULL,
  `destroy_after` datetime(3) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hmac_tenant_day` (`tenant_id`,`day`),
  UNIQUE KEY `uk_hmac_tenant_day_version` (`tenant_id`,`day`,`hash_key_version`),
  CONSTRAINT `chk_hmac_day_boundary` CHECK (DATE(`activated_at`) = `day` AND TIME(`activated_at`) = '00:00:00')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dashboard daily HMAC version registry; contains no secret material';

CREATE TABLE IF NOT EXISTS `statistics_behavior_ingestion_gap` (
  `id` bigint NOT NULL AUTO_INCREMENT, `day` date NOT NULL,
  `reason_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `bucket_start` datetime(3) NOT NULL, `first_seen_at` datetime(3) NOT NULL, `last_seen_at` datetime(3) NOT NULL,
  `rejected_count` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gap_tenant_day_reason_bucket` (`tenant_id`,`day`,`reason_code`,`bucket_start`),
  KEY `idx_gap_tenant_day` (`tenant_id`,`day`),
  CONSTRAINT `chk_behavior_gap_day` CHECK (`day` = DATE(`bucket_start`)),
  CONSTRAINT `chk_behavior_gap_rejected` CHECK (`rejected_count` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dashboard public ingestion coverage gaps without personal identifiers';

CREATE TABLE IF NOT EXISTS `statistics_behavior_event` (
  `id` bigint NOT NULL AUTO_INCREMENT, `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_type` tinyint NOT NULL COMMENT '1 HOME_VIEW 2 PRODUCT_DETAIL_VIEW 3 ADD_TO_CART 4 CHECKOUT_START',
  `event_source` tinyint NOT NULL COMMENT '1 PUBLIC_WEB 2 SERVER_CART',
  `visitor_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `session_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `hash_key_version` smallint unsigned NOT NULL, `user_id` bigint DEFAULT NULL,
  `spu_id` bigint DEFAULT NULL, `sku_id` bigint DEFAULT NULL, `quantity` int DEFAULT NULL,
  `page_path` varchar(255) NOT NULL, `referrer_host` varchar(255) DEFAULT NULL,
  `device_type` tinyint NOT NULL DEFAULT 9, `traffic_quality` tinyint NOT NULL DEFAULT 1,
  `exclusion_reason` varchar(64) DEFAULT NULL, `occurred_at` datetime(3) NOT NULL, `event_day` date NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_event` (`tenant_id`,`event_id`),
  KEY `idx_tenant_quality_type_time` (`tenant_id`,`traffic_quality`,`event_type`,`occurred_at`),
  KEY `idx_tenant_spu_type_time` (`tenant_id`,`spu_id`,`event_type`,`occurred_at`),
  KEY `idx_tenant_time_visitor` (`tenant_id`,`occurred_at`,`hash_key_version`,`visitor_hash`),
  CONSTRAINT `chk_statistics_behavior_event_type` CHECK (`event_type` IN (1,2,3,4)),
  CONSTRAINT `chk_statistics_behavior_event_source` CHECK (`event_source` IN (1,2)),
  CONSTRAINT `chk_statistics_behavior_event_source_type` CHECK ((`event_source`=1 AND `event_type` IN (1,2,4)) OR (`event_source`=2 AND `event_type`=3)),
  CONSTRAINT `chk_statistics_behavior_event_quantity` CHECK (`quantity` IS NULL OR `quantity` BETWEEN 1 AND 999),
  CONSTRAINT `chk_statistics_behavior_event_quality` CHECK (`traffic_quality` BETWEEN 1 AND 5),
  CONSTRAINT `chk_statistics_behavior_event_day` CHECK (`event_day` = DATE(`occurred_at`)),
  CONSTRAINT `fk_behavior_event_hmac_day` FOREIGN KEY (`tenant_id`,`event_day`,`hash_key_version`)
    REFERENCES `statistics_dashboard_hmac_day` (`tenant_id`,`day`,`hash_key_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dashboard pseudonymous behavior events; never store IP, raw visitor/session IDs or full user agent';

CREATE TABLE IF NOT EXISTS `statistics_traffic_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT, `day` date NOT NULL,
  `currency_code` char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `home_pv` bigint DEFAULT NULL, `home_uv` bigint DEFAULT NULL,
  `product_detail_pv` bigint DEFAULT NULL, `product_detail_uv` bigint DEFAULT NULL,
  `add_cart_count` bigint DEFAULT NULL, `add_cart_user_count` bigint DEFAULT NULL,
  `checkout_start_count` bigint DEFAULT NULL,
  `paid_order_count` bigint NOT NULL DEFAULT 0, `paid_buyer_count` bigint NOT NULL DEFAULT 0,
  `paid_item_count` bigint NOT NULL DEFAULT 0, `paid_revenue` bigint NOT NULL DEFAULT 0,
  `refund_amount` bigint NOT NULL DEFAULT 0, `net_revenue` bigint NOT NULL DEFAULT 0,
  `known_cost_amount` bigint NOT NULL DEFAULT 0,
  `cost_amount` bigint DEFAULT NULL, `gross_profit` bigint DEFAULT NULL,
  `gross_margin_percent` decimal(12,4) DEFAULT NULL,
  `exact_cost_item_count` bigint NOT NULL DEFAULT 0, `estimated_cost_item_count` bigint NOT NULL DEFAULT 0,
  `missing_cost_item_count` bigint NOT NULL DEFAULT 0, `profit_data_quality` tinyint NOT NULL DEFAULT 5,
  `accepted_event_count` bigint NOT NULL DEFAULT 0, `excluded_event_count` bigint NOT NULL DEFAULT 0,
  `traffic_data_status` tinyint NOT NULL DEFAULT 3,
  `traffic_watermark` datetime(3) DEFAULT NULL, `trade_watermark` datetime(3) DEFAULT NULL,
  `refund_watermark` datetime(3) DEFAULT NULL, `last_successful_run_at` datetime(3) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_traffic_tenant_day` (`tenant_id`,`day`), KEY `idx_traffic_tenant_update` (`tenant_id`,`update_time`),
  CONSTRAINT `chk_statistics_traffic_status` CHECK (`traffic_data_status` BETWEEN 1 AND 3),
  CONSTRAINT `chk_statistics_profit_quality` CHECK (`profit_data_quality` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dashboard site daily aggregate';

CREATE TABLE IF NOT EXISTS `statistics_dashboard_migration_checkpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT, `phase` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `last_id` bigint NOT NULL DEFAULT 0, `upper_bound_id` bigint NOT NULL DEFAULT 0,
  `processed_rows` bigint NOT NULL DEFAULT 0, `updated_rows` bigint NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1, `started_at` datetime(3) DEFAULT NULL, `completed_at` datetime(3) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_checkpoint_tenant_phase` (`tenant_id`,`phase`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Resumable dashboard migration checkpoint';

CREATE TABLE IF NOT EXISTS `statistics_dashboard_export_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `export_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `filter_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `row_count` int NOT NULL DEFAULT 0,
  `file_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_dashboard_export_tenant_user_time` (`tenant_id`,`user_id`,`create_time`),
  CONSTRAINT `chk_dashboard_export_rows` CHECK (`row_count` BETWEEN 0 AND 10000),
  CONSTRAINT `chk_dashboard_export_result` CHECK (`result` IN ('SUCCESS','FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Privacy-safe dashboard export audit';

-- Guarded forward-compatible columns. Existing incompatible definitions must fail preflight review.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_item' AND COLUMN_NAME='cost_price')=0,
  'ALTER TABLE `trade_order_item` ADD COLUMN `cost_price` bigint DEFAULT NULL COMMENT ''cost snapshot in minor currency unit''', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_item' AND COLUMN_NAME='cost_estimated')=0,
  'ALTER TABLE `trade_order_item` ADD COLUMN `cost_estimated` bit(1) DEFAULT NULL COMMENT ''0 exact, 1 historical estimate, NULL missing''', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Product aggregate financial and coverage columns (all amounts are minor currency units).
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='browse_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `browse_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='browse_user_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `browse_user_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='favorite_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `favorite_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='cart_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `cart_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='order_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `order_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='order_pay_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `order_pay_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='after_sale_count')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `after_sale_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='order_pay_price')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `order_pay_price` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='order_pay_price')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `order_pay_price` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='after_sale_refund_price')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `after_sale_refund_price` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='after_sale_refund_price')<>'bigint', 'ALTER TABLE `product_statistics` MODIFY COLUMN `after_sale_refund_price` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='known_cost_amount')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `known_cost_amount` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='cost_amount')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `cost_amount` bigint DEFAULT NULL', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='gross_profit')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `gross_profit` bigint DEFAULT NULL', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='gross_margin_percent')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `gross_margin_percent` decimal(12,4) DEFAULT NULL', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='exact_cost_item_count')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `exact_cost_item_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='estimated_cost_item_count')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `estimated_cost_item_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='missing_cost_item_count')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `missing_cost_item_count` bigint NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='profit_data_quality')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `profit_data_quality` tinyint NOT NULL DEFAULT 5', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='traffic_data_status')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `traffic_data_status` tinyint NOT NULL DEFAULT 3', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND COLUMN_NAME='traffic_watermark')=0, 'ALTER TABLE `product_statistics` ADD COLUMN `traffic_watermark` datetime(3) DEFAULT NULL', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Release preflight must return no rows before the guarded unique index is added.
SELECT `tenant_id`,`time`,`spu_id`,COUNT(*) AS duplicate_count FROM `product_statistics`
WHERE `deleted`=b'0' GROUP BY `tenant_id`,`time`,`spu_id` HAVING COUNT(*)>1;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_statistics' AND INDEX_NAME='uk_tenant_time_spu')=0,
  'ALTER TABLE `product_statistics` ADD UNIQUE KEY `uk_tenant_time_spu` (`tenant_id`,`time`,`spu_id`)', 'SELECT 1'); PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Navigation contract: independent /dashboard route, component dashboard/index, component name FurnitureDashboard.
-- Permissions are menu-button records and are not assigned to any role by this migration:
-- statistics:dashboard:query
-- statistics:dashboard:profit-query
-- statistics:dashboard:export
-- statistics:dashboard:profit-export

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7990,'数据看板','',2,90,0,'dashboard','ep:data-analysis','dashboard/index','FurnitureDashboard',0,b'1',b'1',b'1','dashboard-migration',NOW(),'dashboard-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `path`='dashboard' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7991,'看板查询','statistics:dashboard:query',3,1,7990,'','','','',0,b'1',b'1',b'1','dashboard-migration',NOW(),'dashboard-migration',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='statistics:dashboard:query' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7992,'利润查询','statistics:dashboard:profit-query',3,2,7990,'','','','',0,b'1',b'1',b'1','dashboard-migration',NOW(),'dashboard-migration',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='statistics:dashboard:profit-query' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7993,'看板导出','statistics:dashboard:export',3,3,7990,'','','','',0,b'1',b'1',b'1','dashboard-migration',NOW(),'dashboard-migration',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='statistics:dashboard:export' AND `deleted`=b'0');
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7994,'利润导出','statistics:dashboard:profit-export',3,4,7990,'','','','',0,b'1',b'1',b'1','dashboard-migration',NOW(),'dashboard-migration',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='statistics:dashboard:profit-export' AND `deleted`=b'0');

-- Scheduler contract: create these four jobs STOPPED through the deployed infra scheduler migration/API.
-- dashboardStatisticsJob | TODAY_AND_YESTERDAY         | 0 */5 * * * ?
-- dashboardStatisticsJob | FINALIZE_YESTERDAY          | 0 10 0 * * ?
-- dashboardStatisticsJob | ROLLING_7_COMPLETE_DAYS     | 0 40 2 * * ?
-- dashboardBehaviorCleanupJob | (empty)                | 0 30 3 * * ?
-- The legacy productStatisticsJob must be stopped before any dashboard job is enabled.

SELECT RELEASE_LOCK(CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2')) AS released;
