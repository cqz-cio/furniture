-- Align the Oakved furniture tenant packages with the custom navigation that is
-- exposed by the furniture-lite admin. Role menu options are filtered by the
-- current tenant package, so adding system_menu rows alone is not sufficient.

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_path_scope`;
CREATE TEMPORARY TABLE `oakved_navigation_path_scope` (
  `full_path` varchar(255) NOT NULL,
  PRIMARY KEY (`full_path`)
) ENGINE=InnoDB;

-- BEGIN furniture-lite custom route paths
-- Keep these paths aligned with yudao-ui-admin-vue3/src/config/furnitureLite.ts.
INSERT INTO `oakved_navigation_path_scope` (`full_path`) VALUES
  ('/dashboard'),
  ('/seo'),
  ('/seo/metadata'),
  ('/seo/site-config'),
  ('/mall/home'),
  ('/mall/product'),
  ('/mall/product/spu'),
  ('/mall/product/category'),
  ('/mall/product/brand'),
  ('/mall/product/property'),
  ('/mall/product/comment'),
  ('/mall/statistics'),
  ('/mall/statistics/product'),
  ('/mall/trade'),
  ('/mall/trade/order'),
  ('/mall/trade/after-sale'),
  ('/mall/trade/delivery'),
  ('/mall/trade/delivery/express'),
  ('/mall/trade/delivery/express/express-template'),
  ('/mall/trade/delivery/express-template'),
  ('/mall/trade/delivery/pick-up-store'),
  ('/ai'),
  ('/ai/console'),
  ('/ai/console/chat'),
  ('/ai/console/image'),
  ('/ai/console/knowledge'),
  ('/ai/console/mind-map'),
  ('/ai/console/model'),
  ('/ai/console/music'),
  ('/ai/console/workflow'),
  ('/ai/console/write'),
  ('/ai/chat'),
  ('/ai/chat/index'),
  ('/ai/chat/manager'),
  ('/ai/model'),
  ('/ai/model/model'),
  ('/ai/model/api-key'),
  ('/ai/model/apiKey'),
  ('/ai/model/chat-role'),
  ('/ai/model/chatRole'),
  ('/ai/model/tool'),
  ('/ai/knowledge'),
  ('/ai/knowledge/knowledge'),
  ('/ai/knowledge/document'),
  ('/ai/knowledge/segment'),
  ('/ai/workflow'),
  ('/ai/write'),
  ('/ai/write/index'),
  ('/ai/write/manager'),
  ('/ai/image'),
  ('/ai/image/index'),
  ('/ai/image/manager'),
  ('/ai/image/square'),
  ('/ai/music'),
  ('/ai/music/index'),
  ('/ai/music/manager'),
  ('/ai/mind-map'),
  ('/ai/mind-map/index'),
  ('/ai/mind-map/manager'),
  ('/ai/mindmap'),
  ('/ai/mindmap/index'),
  ('/ai/mindmap/manager');
-- END furniture-lite custom route paths

-- /mall is retained by the frontend because it has allowed descendants, even
-- though the root path itself is not listed in allowedMenuPaths.
INSERT INTO `oakved_navigation_path_scope` (`full_path`) VALUES ('/mall');

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_route_scope`;
CREATE TEMPORARY TABLE `oakved_navigation_route_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT IGNORE INTO `oakved_navigation_route_scope` (`menu_id`)
WITH RECURSIVE `menu_routes` AS (
  SELECT
    menu.`id`,
    menu.`parent_id`,
    CONCAT('/', TRIM(BOTH '/' FROM menu.`path`)) AS `full_path`
  FROM `system_menu` AS menu
  WHERE menu.`parent_id` = 0
    AND menu.`type` IN (1, 2)
    AND menu.`status` = 0
    AND menu.`deleted` = b'0'

  UNION ALL

  SELECT
    child.`id`,
    child.`parent_id`,
    CONCAT(TRIM(TRAILING '/' FROM parent.`full_path`),
           '/', TRIM(BOTH '/' FROM child.`path`)) AS `full_path`
  FROM `system_menu` AS child
  INNER JOIN `menu_routes` AS parent ON parent.`id` = child.`parent_id`
  WHERE child.`type` IN (1, 2)
    AND child.`status` = 0
    AND child.`deleted` = b'0'
)
SELECT route.`id`
FROM `menu_routes` AS route
INNER JOIN `oakved_navigation_path_scope` AS path_scope
  ON path_scope.`full_path` = route.`full_path`;

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_menu_scope`;
CREATE TEMPORARY TABLE `oakved_navigation_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_navigation_menu_scope` (`menu_id`)
SELECT `menu_id`
FROM `oakved_navigation_route_scope`;

-- A selected page also needs its button permissions in the role tree.
INSERT IGNORE INTO `oakved_navigation_menu_scope` (`menu_id`)
SELECT button.`id`
FROM `system_menu` AS button
INNER JOIN `oakved_navigation_route_scope` AS parent
  ON parent.`menu_id` = button.`parent_id`
WHERE button.`type` = 3
  AND button.`status` = 0
  AND button.`deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_menu_parent_scope`;
CREATE TEMPORARY TABLE `oakved_navigation_menu_parent_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_navigation_menu_parent_scope` (`menu_id`)
SELECT `menu_id`
FROM `oakved_navigation_menu_scope`;

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_target_tenant`;
CREATE TEMPORARY TABLE `oakved_navigation_target_tenant` (
  `tenant_id` bigint NOT NULL,
  `package_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`)
) ENGINE=InnoDB;

-- 121 is the Oakved storefront tenant; 162 is the VANZ furniture tenant.
INSERT INTO `oakved_navigation_target_tenant` (`tenant_id`, `package_id`)
SELECT tenant.`id`, tenant.`package_id`
FROM `system_tenant` AS tenant
WHERE tenant.`id` IN (121, 162)
  AND tenant.`status` = 0
  AND tenant.`deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_permission_guard`;
CREATE TEMPORARY TABLE `oakved_navigation_permission_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_oakved_navigation_permission_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

-- The four custom navigation roots must be registered exactly once.
INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0 WHERE
  (SELECT COUNT(*) FROM `system_menu`
   WHERE `parent_id` = 0 AND `path` = '/mall' AND `type` = 1
     AND `status` = 0 AND `deleted` = b'0') <> 1
  OR
  (SELECT COUNT(*) FROM `system_menu`
   WHERE `parent_id` = 0 AND `path` = '/seo' AND `type` = 1
     AND `status` = 0 AND `deleted` = b'0') <> 1
  OR
  (SELECT COUNT(*) FROM `system_menu`
   WHERE `parent_id` = 0 AND `path` = '/dashboard' AND `type` = 2
     AND `status` = 0 AND `deleted` = b'0') <> 1
  OR
  (SELECT COUNT(*) FROM `system_menu`
   WHERE `parent_id` = 0 AND `path` = '/ai' AND `type` = 1
     AND `status` = 0 AND `deleted` = b'0') <> 1;

-- Every selected node must retain its selected parent so the permission tree
-- cannot produce orphaned rows.
INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `oakved_navigation_menu_scope` AS scope
  INNER JOIN `system_menu` AS menu ON menu.`id` = scope.`menu_id`
  WHERE menu.`parent_id` <> 0
    AND NOT EXISTS (
      SELECT 1
      FROM `oakved_navigation_menu_parent_scope` AS parent
      WHERE parent.`menu_id` = menu.`parent_id`
    )
);

-- At least one target tenant must exist and every target package must be a
-- valid JSON menu array.
INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0 WHERE NOT EXISTS (
  SELECT 1 FROM `oakved_navigation_target_tenant`
);

INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `oakved_navigation_target_tenant` AS target
  LEFT JOIN `system_tenant_package` AS package
    ON package.`id` = target.`package_id`
   AND package.`status` = 0
   AND package.`deleted` = b'0'
  WHERE package.`id` IS NULL
     OR JSON_VALID(package.`menu_ids`) = 0
     OR JSON_TYPE(CAST(package.`menu_ids` AS JSON)) <> 'ARRAY'
);

-- Each target tenant must have exactly one active built-in tenant-admin role.
INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0
FROM `oakved_navigation_target_tenant` AS tenant
WHERE (
  SELECT COUNT(*)
  FROM `system_role` AS role
  WHERE role.`tenant_id` = tenant.`tenant_id`
    AND role.`code` = 'tenant_admin'
    AND role.`type` = 1
    AND role.`status` = 0
    AND role.`deleted` = b'0'
) <> 1;

START TRANSACTION;

-- A target package may still be shared with an unrelated demo tenant. Clone it
-- first instead of widening that unrelated tenant's permissions.
DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_shared_package`;
CREATE TEMPORARY TABLE `oakved_navigation_shared_package` (
  `source_package_id` bigint NOT NULL,
  `clone_remark` varchar(256) NOT NULL,
  PRIMARY KEY (`source_package_id`),
  UNIQUE KEY `uk_oakved_navigation_clone_remark` (`clone_remark`)
) ENGINE=InnoDB;

INSERT INTO `oakved_navigation_shared_package`
  (`source_package_id`, `clone_remark`)
SELECT DISTINCT
  target.`package_id`,
  CONCAT('oakved:furniture-navigation-permissions:source-package:',
         target.`package_id`)
FROM `oakved_navigation_target_tenant` AS target
WHERE EXISTS (
  SELECT 1
  FROM `system_tenant` AS tenant
  WHERE tenant.`package_id` = target.`package_id`
    AND tenant.`deleted` = b'0'
    AND tenant.`id` NOT IN (121, 162)
);

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_existing_clone`;
CREATE TEMPORARY TABLE `oakved_navigation_existing_clone` (
  `clone_remark` varchar(256) NOT NULL,
  PRIMARY KEY (`clone_remark`)
) ENGINE=InnoDB;

INSERT IGNORE INTO `oakved_navigation_existing_clone` (`clone_remark`)
SELECT package.`remark`
FROM `system_tenant_package` AS package
WHERE package.`remark` LIKE 'oakved:furniture-navigation-permissions:%'
  AND package.`deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_source_package`;
CREATE TEMPORARY TABLE `oakved_navigation_source_package` (
  `source_package_id` bigint NOT NULL,
  `name` varchar(30) NOT NULL,
  `status` tinyint NOT NULL,
  `menu_ids` varchar(4096) NOT NULL,
  PRIMARY KEY (`source_package_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_navigation_source_package`
  (`source_package_id`, `name`, `status`, `menu_ids`)
SELECT package.`id`, package.`name`, package.`status`, package.`menu_ids`
FROM `system_tenant_package` AS package
INNER JOIN `oakved_navigation_shared_package` AS shared
  ON shared.`source_package_id` = package.`id`;

INSERT INTO `system_tenant_package`
  (`name`, `status`, `remark`, `menu_ids`, `creator`, `create_time`,
   `updater`, `update_time`, `deleted`)
SELECT
  CONCAT(LEFT(source.`name`, 20), ' 家具导航'),
  source.`status`,
  shared.`clone_remark`,
  source.`menu_ids`,
  'V027',
  CURRENT_TIMESTAMP,
  'V027',
  CURRENT_TIMESTAMP,
  b'0'
FROM `oakved_navigation_shared_package` AS shared
INNER JOIN `oakved_navigation_source_package` AS source
  ON source.`source_package_id` = shared.`source_package_id`
LEFT JOIN `oakved_navigation_existing_clone` AS existing
  ON existing.`clone_remark` = shared.`clone_remark`
WHERE existing.`clone_remark` IS NULL;

INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0
FROM `oakved_navigation_shared_package` AS shared
WHERE (
  SELECT COUNT(*)
  FROM `system_tenant_package` AS clone
  WHERE clone.`remark` = shared.`clone_remark`
    AND clone.`status` = 0
    AND clone.`deleted` = b'0'
) <> 1;

UPDATE `system_tenant` AS tenant
INNER JOIN `oakved_navigation_target_tenant` AS target
  ON target.`tenant_id` = tenant.`id`
INNER JOIN `oakved_navigation_shared_package` AS shared
  ON shared.`source_package_id` = tenant.`package_id`
INNER JOIN `system_tenant_package` AS clone
  ON clone.`remark` = shared.`clone_remark`
 AND clone.`status` = 0
 AND clone.`deleted` = b'0'
SET tenant.`package_id` = clone.`id`,
    tenant.`updater` = 'V027',
    tenant.`update_time` = CURRENT_TIMESTAMP;

UPDATE `oakved_navigation_target_tenant` AS target
INNER JOIN `system_tenant` AS tenant ON tenant.`id` = target.`tenant_id`
SET target.`package_id` = tenant.`package_id`;

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_target_package`;
CREATE TEMPORARY TABLE `oakved_navigation_target_package` (
  `package_id` bigint NOT NULL,
  PRIMARY KEY (`package_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_navigation_target_package` (`package_id`)
SELECT DISTINCT tenant.`package_id`
FROM `oakved_navigation_target_tenant` AS tenant;

-- The final target packages must be valid and isolated from unrelated tenants.
INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `oakved_navigation_target_package` AS target
  LEFT JOIN `system_tenant_package` AS package
    ON package.`id` = target.`package_id`
   AND package.`status` = 0
   AND package.`deleted` = b'0'
  WHERE package.`id` IS NULL
     OR JSON_VALID(package.`menu_ids`) = 0
     OR JSON_TYPE(CAST(package.`menu_ids` AS JSON)) <> 'ARRAY'
);

INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `oakved_navigation_target_package` AS target
  INNER JOIN `system_tenant` AS tenant
    ON tenant.`package_id` = target.`package_id`
   AND tenant.`deleted` = b'0'
  WHERE tenant.`id` NOT IN (121, 162)
);

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_package_update`;
CREATE TEMPORARY TABLE `oakved_navigation_package_update` (
  `package_id` bigint NOT NULL,
  `menu_ids` varchar(4096) NOT NULL,
  PRIMARY KEY (`package_id`)
) ENGINE=InnoDB;

-- Append only missing IDs, preserving existing package choices and making the
-- migration safe to rerun.
INSERT INTO `oakved_navigation_package_update` (`package_id`, `menu_ids`)
SELECT
  package.`id`,
  CAST(
    JSON_MERGE_PRESERVE(
      CAST(package.`menu_ids` AS JSON),
      JSON_ARRAYAGG(scope.`menu_id`)
    ) AS CHAR CHARACTER SET utf8mb4
  )
FROM `oakved_navigation_target_package` AS target
INNER JOIN `system_tenant_package` AS package
  ON package.`id` = target.`package_id`
CROSS JOIN `oakved_navigation_menu_scope` AS scope
WHERE JSON_CONTAINS(
        CAST(package.`menu_ids` AS JSON),
        CAST(scope.`menu_id` AS JSON),
        '$'
      ) = 0
GROUP BY package.`id`, package.`menu_ids`;

UPDATE `system_tenant_package` AS package
INNER JOIN `oakved_navigation_package_update` AS package_update
  ON package_update.`package_id` = package.`id`
SET package.`menu_ids` = package_update.`menu_ids`,
    package.`updater` = 'V027',
    package.`update_time` = CURRENT_TIMESTAMP;

DROP TEMPORARY TABLE IF EXISTS `oakved_navigation_existing_role_menu`;
CREATE TEMPORARY TABLE `oakved_navigation_existing_role_menu` (
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `menu_id`, `tenant_id`)
) ENGINE=InnoDB;

INSERT IGNORE INTO `oakved_navigation_existing_role_menu`
  (`role_id`, `menu_id`, `tenant_id`)
SELECT role_menu.`role_id`, role_menu.`menu_id`, role_menu.`tenant_id`
FROM `system_role_menu` AS role_menu
WHERE role_menu.`deleted` = b'0';

-- Tenant admins always receive the complete package scope. Custom roles remain
-- unchanged and can now be configured in the UI.
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`,
   `deleted`, `tenant_id`)
SELECT
  role.`id`, scope.`menu_id`, 'V027', CURRENT_TIMESTAMP,
  'V027', CURRENT_TIMESTAMP, b'0', tenant.`tenant_id`
FROM `oakved_navigation_target_tenant` AS tenant
INNER JOIN `system_role` AS role
  ON role.`tenant_id` = tenant.`tenant_id`
 AND role.`code` = 'tenant_admin'
 AND role.`type` = 1
 AND role.`status` = 0
 AND role.`deleted` = b'0'
CROSS JOIN `oakved_navigation_menu_scope` AS scope
WHERE NOT EXISTS (
  SELECT 1
  FROM `oakved_navigation_existing_role_menu` AS role_menu
  WHERE role_menu.`role_id` = role.`id`
    AND role_menu.`menu_id` = scope.`menu_id`
    AND role_menu.`tenant_id` = tenant.`tenant_id`
);

-- Verify both the package filter and the tenant-admin assignment before commit.
INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0
FROM `oakved_navigation_target_package` AS target
INNER JOIN `system_tenant_package` AS package
  ON package.`id` = target.`package_id`
WHERE EXISTS (
  SELECT 1
  FROM `oakved_navigation_menu_scope` AS scope
  WHERE JSON_CONTAINS(
          CAST(package.`menu_ids` AS JSON),
          CAST(scope.`menu_id` AS JSON),
          '$'
        ) = 0
);

INSERT INTO `oakved_navigation_permission_guard` (`valid`)
SELECT 0
FROM `oakved_navigation_target_tenant` AS tenant
WHERE EXISTS (
  SELECT 1
  FROM `oakved_navigation_menu_scope` AS scope
  WHERE NOT EXISTS (
    SELECT 1
    FROM `system_role` AS role
    INNER JOIN `system_role_menu` AS role_menu
      ON role_menu.`role_id` = role.`id`
     AND role_menu.`menu_id` = scope.`menu_id`
     AND role_menu.`tenant_id` = tenant.`tenant_id`
     AND role_menu.`deleted` = b'0'
    WHERE role.`tenant_id` = tenant.`tenant_id`
      AND role.`code` = 'tenant_admin'
      AND role.`type` = 1
      AND role.`status` = 0
      AND role.`deleted` = b'0'
  )
);

COMMIT;

DROP TEMPORARY TABLE `oakved_navigation_package_update`;
DROP TEMPORARY TABLE `oakved_navigation_existing_role_menu`;
DROP TEMPORARY TABLE `oakved_navigation_source_package`;
DROP TEMPORARY TABLE `oakved_navigation_existing_clone`;
DROP TEMPORARY TABLE `oakved_navigation_permission_guard`;
DROP TEMPORARY TABLE `oakved_navigation_target_package`;
DROP TEMPORARY TABLE `oakved_navigation_shared_package`;
DROP TEMPORARY TABLE `oakved_navigation_target_tenant`;
DROP TEMPORARY TABLE `oakved_navigation_menu_parent_scope`;
DROP TEMPORARY TABLE `oakved_navigation_menu_scope`;
DROP TEMPORARY TABLE `oakved_navigation_route_scope`;
DROP TEMPORARY TABLE `oakved_navigation_path_scope`;
