CREATE TABLE IF NOT EXISTS `erp_product_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_product_unit_tenant_name_deleted` (`tenant_id`, `name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `erp_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `name` varchar(128) NOT NULL,
  `code` varchar(64) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_product_category_tenant_code_deleted` (`tenant_id`, `code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `erp_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `bar_code` varchar(128) NOT NULL,
  `category_id` bigint NOT NULL,
  `unit_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `standard` varchar(255) NOT NULL DEFAULT '',
  `remark` varchar(512) NOT NULL DEFAULT '',
  `expiry_day` int NOT NULL DEFAULT 0,
  `weight` decimal(18,3) NOT NULL DEFAULT 0,
  `purchase_price` decimal(18,2) NOT NULL DEFAULT 0,
  `sale_price` decimal(18,2) NOT NULL DEFAULT 0,
  `min_price` decimal(18,2) NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_product_tenant_bar_code_deleted` (`tenant_id`, `bar_code`, `deleted`),
  KEY `idx_erp_product_tenant_category` (`tenant_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `erp_warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `address` varchar(255) NOT NULL DEFAULT '',
  `sort` bigint NOT NULL DEFAULT 0,
  `remark` varchar(512) NOT NULL DEFAULT '',
  `principal` varchar(64) NOT NULL DEFAULT '',
  `warehouse_price` decimal(18,2) NOT NULL DEFAULT 0,
  `truckage_price` decimal(18,2) NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `default_status` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_warehouse_tenant_name_deleted` (`tenant_id`, `name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `erp_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `count` decimal(18,3) NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erp_stock_tenant_product_warehouse_deleted` (`tenant_id`, `product_id`, `warehouse_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `mall_erp_product_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mall_spu_id` bigint NOT NULL,
  `mall_sku_id` bigint NOT NULL,
  `erp_product_id` bigint NOT NULL,
  `erp_product_code` varchar(128) NOT NULL,
  `sync_status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `last_synced_at` datetime NULL,
  `last_error` varchar(1024) NOT NULL DEFAULT '',
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_erp_mapping_tenant_sku_deleted` (`tenant_id`, `mall_sku_id`, `deleted`),
  UNIQUE KEY `uk_mall_erp_mapping_tenant_erp_product_deleted` (`tenant_id`, `erp_product_id`, `deleted`),
  UNIQUE KEY `uk_mall_erp_mapping_tenant_erp_code_deleted` (`tenant_id`, `erp_product_code`, `deleted`),
  KEY `idx_mall_erp_mapping_tenant_spu` (`tenant_id`, `mall_spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `mall_erp_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` bigint NOT NULL,
  `direction` varchar(32) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `idempotency_key` varchar(191) NOT NULL,
  `request_summary` varchar(2048) NOT NULL DEFAULT '',
  `sync_status` varchar(16) NOT NULL,
  `last_error` varchar(1024) NOT NULL DEFAULT '',
  `retry_count` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_erp_sync_log_tenant_idempotency_deleted` (`tenant_id`, `idempotency_key`, `deleted`),
  KEY `idx_mall_erp_sync_log_tenant_status` (`tenant_id`, `sync_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
