-- Business-editable website navigation, category synchronization and secure
-- draft preview. This migration is forward-only and intentionally leaves V038
-- unchanged.

CREATE TABLE IF NOT EXISTS `website_navigation_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `site_id` bigint NOT NULL,
  `locale` varchar(32) NOT NULL DEFAULT 'en',
  `revision_no` int NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `version` int NOT NULL DEFAULT 1,
  `published_time` datetime DEFAULT NULL,
  `published_by` varchar(64) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `draft_active` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' AND `status` = 'DRAFT' THEN 1 ELSE NULL END) STORED,
  `published_active` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' AND `status` = 'PUBLISHED' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_navigation_revision_no`
      (`tenant_id`, `site_id`, `locale`, `revision_no`),
  UNIQUE KEY `uk_navigation_draft_active`
      (`tenant_id`, `site_id`, `locale`, `draft_active`),
  UNIQUE KEY `uk_navigation_published_active`
      (`tenant_id`, `site_id`, `locale`, `published_active`),
  KEY `idx_navigation_public`
      (`tenant_id`, `site_id`, `locale`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Website navigation revisions';

CREATE TABLE IF NOT EXISTS `website_navigation_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `revision_id` bigint NOT NULL,
  `item_key` varchar(96) NOT NULL,
  `parent_item_key` varchar(96) NOT NULL DEFAULT '',
  `item_type` varchar(16) NOT NULL,
  `label` varchar(64) NOT NULL,
  `page_key` varchar(32) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `visible` bit(1) NOT NULL DEFAULT b'1',
  `open_mode` varchar(16) NOT NULL DEFAULT '_self',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `active_record` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_navigation_item_active`
      (`tenant_id`, `revision_id`, `item_key`, `active_record`),
  KEY `idx_navigation_item_order`
      (`tenant_id`, `revision_id`, `sort`, `id`),
  KEY `idx_navigation_category`
      (`tenant_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Website navigation items';

DROP TEMPORARY TABLE IF EXISTS `website_navigation_menu_guard`;
CREATE TEMPORARY TABLE `website_navigation_menu_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_website_navigation_menu_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

SET @website_navigation_seo_root_id = (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `path` = '/seo' AND `type` = 1 AND `deleted` = b'0'
);

INSERT INTO `website_navigation_menu_guard` (`valid`)
SELECT 0 WHERE @website_navigation_seo_root_id IS NULL;

START TRANSACTION;

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT
  '导航管理','',2,3,@website_navigation_seo_root_id,'navigation','ep:guide',
  'seo/navigation/index','SeoNavigation',0,b'1',b'1',b'1',
  'V039',CURRENT_TIMESTAMP,'V039',CURRENT_TIMESTAMP,b'0'
WHERE @website_navigation_seo_root_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @website_navigation_seo_root_id
      AND `path` = 'navigation' AND `deleted` = b'0'
  );

SET @website_navigation_menu_id = (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `parent_id` = @website_navigation_seo_root_id
    AND `path` = 'navigation' AND `deleted` = b'0'
);

-- Keep keyword analysis after the new business-facing navigation page.
UPDATE `system_menu`
SET `sort` = 4, `updater` = 'V039', `update_time` = CURRENT_TIMESTAMP
WHERE `parent_id` = @website_navigation_seo_root_id
  AND `path` = 'analysis' AND `deleted` = b'0';

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '导航查询','seo:navigation:query',3,1,@website_navigation_menu_id,'','','','',
  0,b'1',b'1',b'1','V039',CURRENT_TIMESTAMP,'V039',CURRENT_TIMESTAMP,b'0'
WHERE @website_navigation_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:navigation:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '导航保存','seo:navigation:update',3,2,@website_navigation_menu_id,'','','','',
  0,b'1',b'1',b'1','V039',CURRENT_TIMESTAMP,'V039',CURRENT_TIMESTAMP,b'0'
WHERE @website_navigation_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:navigation:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '导航预览','seo:navigation:preview',3,3,@website_navigation_menu_id,'','','','',
  0,b'1',b'1',b'1','V039',CURRENT_TIMESTAMP,'V039',CURRENT_TIMESTAMP,b'0'
WHERE @website_navigation_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:navigation:preview' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '导航发布','seo:navigation:publish',3,4,@website_navigation_menu_id,'','','','',
  0,b'1',b'1',b'1','V039',CURRENT_TIMESTAMP,'V039',CURRENT_TIMESTAMP,b'0'
WHERE @website_navigation_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:navigation:publish' AND `deleted` = b'0');

DROP TEMPORARY TABLE IF EXISTS `website_navigation_menu_scope`;
CREATE TEMPORARY TABLE `website_navigation_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `website_navigation_menu_scope` (`menu_id`)
SELECT @website_navigation_menu_id
UNION SELECT `id` FROM `system_menu`
  WHERE `permission` IN (
    'seo:navigation:query','seo:navigation:update',
    'seo:navigation:preview','seo:navigation:publish'
  ) AND `deleted` = b'0';

INSERT INTO `website_navigation_menu_guard` (`valid`)
SELECT 0 WHERE (SELECT COUNT(*) FROM `website_navigation_menu_scope`) <> 5;

DROP TEMPORARY TABLE IF EXISTS `website_navigation_package_update`;
CREATE TEMPORARY TABLE `website_navigation_package_update` (
  `package_id` bigint NOT NULL,
  `menu_ids` varchar(8192) NOT NULL,
  PRIMARY KEY (`package_id`)
) ENGINE=InnoDB;

INSERT INTO `website_navigation_package_update` (`package_id`, `menu_ids`)
SELECT package.`id`,
       CAST(JSON_MERGE_PRESERVE(
         CAST(package.`menu_ids` AS JSON), JSON_ARRAYAGG(scope.`menu_id`)
       ) AS CHAR CHARACTER SET utf8mb4)
FROM `system_tenant` AS tenant
INNER JOIN `system_tenant_package` AS package ON package.`id` = tenant.`package_id`
CROSS JOIN `website_navigation_menu_scope` AS scope
WHERE tenant.`id` = 162
  AND tenant.`deleted` = b'0'
  AND package.`deleted` = b'0'
  AND JSON_CONTAINS(CAST(package.`menu_ids` AS JSON), CAST(scope.`menu_id` AS JSON), '$') = 0
GROUP BY package.`id`, package.`menu_ids`;

UPDATE `system_tenant_package` AS package
INNER JOIN `website_navigation_package_update` AS package_update
  ON package_update.`package_id` = package.`id`
SET package.`menu_ids` = package_update.`menu_ids`,
    package.`updater` = 'V039',
    package.`update_time` = CURRENT_TIMESTAMP;

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.`id`, scope.`menu_id`, 'V039', CURRENT_TIMESTAMP,
       'V039', CURRENT_TIMESTAMP, b'0', 162
FROM `system_role` AS role
CROSS JOIN `website_navigation_menu_scope` AS scope
WHERE role.`tenant_id` = 162
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` AS existing
    WHERE existing.`role_id` = role.`id`
      AND existing.`menu_id` = scope.`menu_id`
      AND existing.`tenant_id` = 162
      AND existing.`deleted` = b'0'
  );

INSERT INTO `website_navigation_menu_guard` (`valid`)
SELECT 0 FROM `system_tenant` AS tenant
INNER JOIN `system_tenant_package` AS package ON package.`id` = tenant.`package_id`
WHERE tenant.`id` = 162 AND tenant.`deleted` = b'0'
  AND EXISTS (
    SELECT 1 FROM `website_navigation_menu_scope` AS scope
    WHERE JSON_CONTAINS(
      CAST(package.`menu_ids` AS JSON), CAST(scope.`menu_id` AS JSON), '$'
    ) = 0
  );

INSERT INTO `website_navigation_menu_guard` (`valid`)
SELECT 0 FROM `system_role` AS role
WHERE role.`tenant_id` = 162
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND EXISTS (
    SELECT 1 FROM `website_navigation_menu_scope` AS scope
    WHERE NOT EXISTS (
      SELECT 1 FROM `system_role_menu` AS role_menu
      WHERE role_menu.`role_id` = role.`id`
        AND role_menu.`menu_id` = scope.`menu_id`
        AND role_menu.`tenant_id` = 162
        AND role_menu.`deleted` = b'0'
    )
  );

COMMIT;

DROP TEMPORARY TABLE `website_navigation_package_update`;
DROP TEMPORARY TABLE `website_navigation_menu_scope`;
DROP TEMPORARY TABLE `website_navigation_menu_guard`;
