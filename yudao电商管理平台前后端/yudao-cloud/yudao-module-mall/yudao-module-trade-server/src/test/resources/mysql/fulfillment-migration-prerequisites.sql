-- Production-compatible prerequisites for applying the real V015-V018 and V021-V023 fulfillment migrations in isolation.
-- These are existing tables owned by older modules; fulfillment migrations only reference them.
CREATE TABLE IF NOT EXISTS `trade_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `no` varchar(32) NOT NULL,
  `status` int NOT NULL,
  `logistics_id` bigint DEFAULT NULL,
  `logistics_no` varchar(64) DEFAULT NULL,
  `delivery_time` datetime DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_trade_order_tenant_status` (`tenant_id`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `cart_id` int DEFAULT NULL,
  `registry_id` bigint DEFAULT NULL,
  `registry_item_id` bigint DEFAULT NULL,
  `spu_id` bigint NOT NULL,
  `spu_name` varchar(255) NOT NULL,
  `sku_id` bigint NOT NULL,
  `properties` json DEFAULT NULL,
  `pic_url` varchar(1024) DEFAULT NULL,
  `count` int NOT NULL,
  `comment_status` bit(1) DEFAULT NULL,
  `price` int NOT NULL,
  `cost_price` bigint DEFAULT NULL,
  `cost_estimated` bit(1) NOT NULL DEFAULT b'0',
  `discount_price` int NOT NULL,
  `delivery_price` int DEFAULT NULL,
  `adjust_price` int DEFAULT NULL,
  `pay_price` int NOT NULL,
  `coupon_price` int DEFAULT NULL,
  `point_price` int DEFAULT NULL,
  `use_point` int DEFAULT NULL,
  `give_point` int DEFAULT NULL,
  `vip_price` int DEFAULT NULL,
  `after_sale_id` bigint DEFAULT NULL,
  `after_sale_status` int NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_trade_order_item_order` (`tenant_id`,`order_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `erp_warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `system_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `permission` varchar(100) DEFAULT '',
  `type` tinyint NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `path` varchar(200) DEFAULT '',
  `icon` varchar(100) DEFAULT '',
  `component` varchar(255) DEFAULT NULL,
  `component_name` varchar(255) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `visible` bit(1) NOT NULL DEFAULT b'1',
  `keep_alive` bit(1) NOT NULL DEFAULT b'1',
  `always_show` bit(1) NOT NULL DEFAULT b'1',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_system_menu_permission` (`permission`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
VALUES
(2076,'订单列表','',2,1,2072,'order','','mall/trade/order/index','TradeOrder',
 0,b'1',b'1',b'1','test','test',b'0');
