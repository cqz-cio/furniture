-- SEO foundation schema and navigation contracts (MySQL 8.x).

CREATE TABLE IF NOT EXISTS `seo_site_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `site_id` bigint NOT NULL,
  `site_name` varchar(128) NOT NULL,
  `site_url` varchar(512) NOT NULL,
  `default_title_suffix` varchar(128) NOT NULL DEFAULT '',
  `default_description` varchar(500) NOT NULL DEFAULT '',
  `default_robots` varchar(64) NOT NULL DEFAULT 'index,follow',
  `default_og_image` varchar(1024) NOT NULL DEFAULT '',
  `default_locale` varchar(32) NOT NULL DEFAULT 'zh-CN',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_site_deleted` (`tenant_id`, `site_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SEO site defaults';

CREATE TABLE IF NOT EXISTS `seo_metadata` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `site_id` bigint NOT NULL,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` bigint NOT NULL,
  `locale` varchar(32) NOT NULL DEFAULT 'zh-CN',
  `seo_title` varchar(255) NOT NULL DEFAULT '',
  `meta_description` varchar(500) NOT NULL DEFAULT '',
  `focus_keyphrase` varchar(255) NOT NULL DEFAULT '',
  `related_keyphrases` json DEFAULT NULL,
  `canonical_url` varchar(1024) NOT NULL DEFAULT '',
  `robots_index` bit(1) NOT NULL DEFAULT b'1',
  `robots_follow` bit(1) NOT NULL DEFAULT b'1',
  `og_title` varchar(255) NOT NULL DEFAULT '',
  `og_description` varchar(500) NOT NULL DEFAULT '',
  `og_image` varchar(1024) NOT NULL DEFAULT '',
  `schema_type` varchar(64) NOT NULL DEFAULT '',
  `publish_status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `version` int NOT NULL DEFAULT 1,
  `published_time` datetime DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_locale_deleted`
      (`tenant_id`, `site_id`, `entity_type`, `entity_id`, `locale`, `deleted`),
  KEY `idx_public_resolve`
      (`tenant_id`, `site_id`, `entity_type`, `entity_id`, `locale`, `publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SEO metadata by entity and locale';

-- Menu records use deterministic IDs 8100 through 8109. Existing menus may use
-- different IDs, so every parent is resolved after its idempotent insert.
-- A CHECK-backed temporary table turns every integrity violation into a hard error.
DROP TEMPORARY TABLE IF EXISTS `seo_menu_id_guard`;
CREATE TEMPORARY TABLE `seo_menu_id_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_seo_menu_id_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

-- Reserved IDs may only belong to their intended SEO menu/button identities.
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 FROM `system_menu` WHERE `id` = 8100
  AND NOT (`path` = '/seo' AND `type` = 1 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8101
  AND NOT (`path` = 'metadata' AND `type` = 2 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8102
  AND NOT (`path` = 'site-config' AND `type` = 2 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8103
  AND NOT (`permission` = 'seo:metadata:query' AND `type` = 3 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8104
  AND NOT (`permission` = 'seo:metadata:create' AND `type` = 3 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8105
  AND NOT (`permission` = 'seo:metadata:update' AND `type` = 3 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8106
  AND NOT (`permission` = 'seo:metadata:delete' AND `type` = 3 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8107
  AND NOT (`permission` = 'seo:metadata:publish' AND `type` = 3 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8108
  AND NOT (`permission` = 'seo:site-config:query' AND `type` = 3 AND `deleted` = b'0')
UNION ALL SELECT 0 FROM `system_menu` WHERE `id` = 8109
  AND NOT (`permission` = 'seo:site-config:update' AND `type` = 3 AND `deleted` = b'0');

-- Insert and resolve the SEO root menu.
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8100,'SEO 管理','',1,80,0,'/seo','ep:promotion','',NULL,0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `path` = '/seo' AND `deleted` = b'0');

SET @seo_root_menu_id = (SELECT MIN(`id`) FROM `system_menu`
  WHERE `path` = '/seo' AND `deleted` = b'0');
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu`
  WHERE `path` = '/seo' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `id` = @seo_root_menu_id
    AND `parent_id` = 0 AND `type` = 1 AND `deleted` = b'0') <> 1;

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8101,'内容优化','',2,1,@seo_root_menu_id,'metadata','ep:document','seo/metadata/index','SeoMetadata',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE
  `parent_id` = @seo_root_menu_id AND `path` = 'metadata' AND `deleted` = b'0');

SET @seo_metadata_menu_id = (SELECT MIN(`id`) FROM `system_menu`
  WHERE `parent_id` = @seo_root_menu_id AND `path` = 'metadata' AND `deleted` = b'0');
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 WHERE EXISTS (SELECT 1 FROM `system_menu`
  WHERE `id` = 8101 AND `id` <> @seo_metadata_menu_id);
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu`
  WHERE `parent_id` = @seo_root_menu_id AND `path` = 'metadata' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `id` = @seo_metadata_menu_id
    AND `parent_id` = @seo_root_menu_id AND `type` = 2 AND `deleted` = b'0') <> 1;

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8102,'站点设置','',2,2,@seo_root_menu_id,'site-config','ep:setting','seo/site-config/index','SeoSiteConfig',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE
  `parent_id` = @seo_root_menu_id AND `path` = 'site-config' AND `deleted` = b'0');

SET @seo_site_config_menu_id = (SELECT MIN(`id`) FROM `system_menu`
  WHERE `parent_id` = @seo_root_menu_id AND `path` = 'site-config' AND `deleted` = b'0');
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 WHERE EXISTS (SELECT 1 FROM `system_menu`
  WHERE `id` = 8102 AND `id` <> @seo_site_config_menu_id);
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu`
  WHERE `parent_id` = @seo_root_menu_id AND `path` = 'site-config' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `id` = @seo_site_config_menu_id
    AND `parent_id` = @seo_root_menu_id AND `type` = 2 AND `deleted` = b'0') <> 1;

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8103,'内容查询','seo:metadata:query',3,1,@seo_metadata_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:metadata:query' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8104,'内容创建','seo:metadata:create',3,2,@seo_metadata_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:metadata:create' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8105,'内容更新','seo:metadata:update',3,3,@seo_metadata_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:metadata:update' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8106,'内容删除','seo:metadata:delete',3,4,@seo_metadata_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:metadata:delete' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8107,'内容发布','seo:metadata:publish',3,5,@seo_metadata_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:metadata:publish' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8108,'站点查询','seo:site-config:query',3,1,@seo_site_config_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:site-config:query' AND `deleted`=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8109,'站点更新','seo:site-config:update',3,2,@seo_site_config_menu_id,'','','','',0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='seo:site-config:update' AND `deleted`=b'0');

-- Existing permission rows must be unique and already attached to the resolved child.
INSERT INTO `seo_menu_id_guard` (`valid`)
SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:query' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:query' AND `parent_id` = @seo_metadata_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1
UNION ALL SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:create' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:create' AND `parent_id` = @seo_metadata_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1
UNION ALL SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:update' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:update' AND `parent_id` = @seo_metadata_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1
UNION ALL SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:delete' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:delete' AND `parent_id` = @seo_metadata_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1
UNION ALL SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:publish' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:metadata:publish' AND `parent_id` = @seo_metadata_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1
UNION ALL SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:site-config:query' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:site-config:query' AND `parent_id` = @seo_site_config_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1
UNION ALL SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:site-config:update' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu` WHERE `permission` = 'seo:site-config:update' AND `parent_id` = @seo_site_config_menu_id AND `type` = 3 AND `deleted` = b'0') <> 1;

DROP TEMPORARY TABLE `seo_menu_id_guard`;
