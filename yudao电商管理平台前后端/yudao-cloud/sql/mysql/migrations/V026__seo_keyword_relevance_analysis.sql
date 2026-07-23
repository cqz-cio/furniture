-- SEO keyword relevance analysis history, evidence, and permissions (MySQL 8.x).

ALTER TABLE `seo_metadata`
  ADD COLUMN `latest_analysis_id` bigint DEFAULT NULL AFTER `published_time`,
  ADD KEY `idx_latest_analysis` (`tenant_id`, `latest_analysis_id`);

CREATE TABLE IF NOT EXISTS `seo_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `site_id` bigint NOT NULL,
  `source_type` varchar(16) NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` bigint DEFAULT NULL,
  `locale` varchar(32) NOT NULL DEFAULT 'zh-CN',
  `focus_keyphrase` varchar(255) NOT NULL,
  `input_snapshot` json NOT NULL,
  `content_hash` char(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `previous_analysis_id` bigint DEFAULT NULL,
  `overall_relevance_percent` int DEFAULT NULL,
  `confidence_percent` int DEFAULT NULL,
  `total_score` int DEFAULT NULL,
  `engine_version` varchar(64) NOT NULL,
  `rule_profile_version` varchar(64) NOT NULL,
  `dictionary_version` varchar(64) NOT NULL,
  `semantic_model_version` varchar(128) DEFAULT NULL,
  `analysis_status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_message` varchar(500) DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `active_record` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_idempotency_active`
      (`tenant_id`, `idempotency_key`, `active_record`),
  KEY `idx_analysis_entity_history`
      (`tenant_id`, `site_id`, `entity_type`, `entity_id`, `locale`, `create_time`),
  KEY `idx_analysis_previous` (`tenant_id`, `previous_analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SEO immutable analysis run';

CREATE TABLE IF NOT EXISTS `seo_analysis_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `rule_code` varchar(96) NOT NULL,
  `category` varchar(32) NOT NULL,
  `status` varchar(24) NOT NULL,
  `score` decimal(8,4) DEFAULT NULL,
  `max_score` decimal(8,4) DEFAULT NULL,
  `evidence` json DEFAULT NULL,
  `message` varchar(1000) NOT NULL DEFAULT '',
  `recommendation` varchar(1000) NOT NULL DEFAULT '',
  `sort` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_rule` (`tenant_id`, `analysis_id`, `rule_code`),
  KEY `idx_analysis_item_sort` (`tenant_id`, `analysis_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SEO non-keyword rule evidence';

CREATE TABLE IF NOT EXISTS `seo_keyword_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `keyword_type` varchar(16) NOT NULL,
  `keyword` varchar(255) NOT NULL,
  `normalized_keyword` varchar(255) NOT NULL,
  `sort` int NOT NULL,
  `key_position_percent` int DEFAULT NULL,
  `lexical_match_percent` int DEFAULT NULL,
  `semantic_percent` int DEFAULT NULL,
  `distribution_percent` int DEFAULT NULL,
  `intent_coverage_percent` int DEFAULT NULL,
  `relevance_percent` int DEFAULT NULL,
  `confidence_percent` int NOT NULL DEFAULT 0,
  `grade` varchar(16) DEFAULT NULL,
  `analysis_status` varchar(16) NOT NULL,
  `exact_match_count` int NOT NULL DEFAULT 0,
  `variant_match_count` int NOT NULL DEFAULT 0,
  `matched_locations` json DEFAULT NULL,
  `dictionary_version` varchar(64) NOT NULL,
  `semantic_model_version` varchar(128) DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `active_record` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword_order_active`
      (`tenant_id`, `analysis_id`, `keyword_type`, `sort`, `active_record`),
  UNIQUE KEY `uk_keyword_normalized_active`
      (`tenant_id`, `analysis_id`, `normalized_keyword`, `active_record`),
  KEY `idx_keyword_analysis` (`tenant_id`, `analysis_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Per-keyword SEO relevance result';

CREATE TABLE IF NOT EXISTS `seo_keyword_analysis_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `keyword_analysis_id` bigint NOT NULL,
  `rule_code` varchar(96) NOT NULL,
  `dimension` varchar(24) NOT NULL,
  `severity` varchar(16) NOT NULL,
  `status` varchar(24) NOT NULL,
  `score` decimal(8,4) DEFAULT NULL,
  `max_score` decimal(8,4) DEFAULT NULL,
  `content_location` varchar(64) DEFAULT NULL,
  `evidence` json DEFAULT NULL,
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `recommendation` varchar(1000) NOT NULL DEFAULT '',
  `recoverable_score` decimal(8,4) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword_rule` (`tenant_id`, `keyword_analysis_id`, `rule_code`),
  KEY `idx_keyword_item_sort` (`tenant_id`, `keyword_analysis_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Per-keyword SEO evidence and suggestions';

SET @seo_root_menu_id = (SELECT MIN(`id`) FROM `system_menu`
  WHERE `path` = '/seo' AND `deleted` = b'0');

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '关键词分析','',2,3,@seo_root_menu_id,'analysis','ep:data-analysis','seo/analysis/index','SeoAnalysis',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE @seo_root_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @seo_root_menu_id AND `path` = 'analysis' AND `deleted` = b'0');

SET @seo_analysis_menu_id = (SELECT MIN(`id`) FROM `system_menu`
  WHERE `parent_id` = @seo_root_menu_id AND `path` = 'analysis' AND `deleted` = b'0');

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '运行分析','seo:analysis:run',3,1,@seo_analysis_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE @seo_analysis_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:analysis:run' AND `deleted`=b'0');

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '分析查询','seo:analysis:query',3,2,@seo_analysis_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE @seo_analysis_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:analysis:query' AND `deleted`=b'0');
