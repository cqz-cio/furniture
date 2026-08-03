-- Give the VANZ B2B tenant an isolated, inquiry-oriented package. The package
-- keeps non-business administration choices from its source package, while
-- replacing commerce navigation with Product Center, SEO, Inquiry Center and
-- the B2B dashboard. Oakved/B2C and unrelated tenants are never widened or
-- reduced by this migration.

SET @vanz_b2b_tenant_id = 162;
SET @vanz_b2b_package_marker =
  _utf8mb4'vanz:tenant-162:b2b-navigation:v038' COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_permission_guard`;
CREATE TEMPORARY TABLE `vanz_b2b_permission_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_vanz_b2b_permission_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

-- Fail closed when the known VANZ tenant or its source package has drifted.
INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_tenant` AS tenant
  INNER JOIN `system_tenant_package` AS package ON package.`id` = tenant.`package_id`
  WHERE tenant.`id` = @vanz_b2b_tenant_id
    AND tenant.`business_mode` = 'B2B'
    AND tenant.`status` = 0
    AND tenant.`deleted` = b'0'
    AND package.`status` = 0
    AND package.`deleted` = b'0'
    AND JSON_VALID(package.`menu_ids`)
) <> 1;

INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_role`
  WHERE `tenant_id` = @vanz_b2b_tenant_id
    AND `code` = 'tenant_admin'
    AND `type` = 1
    AND `status` = 0
    AND `deleted` = b'0'
) <> 1;

SET @vanz_b2b_existing_package_id = (
  SELECT `id`
  FROM `system_tenant_package`
  WHERE `remark` = @vanz_b2b_package_marker
    AND `deleted` = b'0'
  ORDER BY `id`
  LIMIT 1
);

SET @vanz_b2b_source_package_id = COALESCE(
  @vanz_b2b_existing_package_id,
  (
    SELECT `package_id`
    FROM `system_tenant`
    WHERE `id` = @vanz_b2b_tenant_id
      AND `deleted` = b'0'
    LIMIT 1
  )
);

START TRANSACTION;

INSERT INTO `system_tenant_package`
  (`name`, `status`, `remark`, `menu_ids`, `creator`, `create_time`,
   `updater`, `update_time`, `deleted`)
SELECT
  'Vanz B2B 询盘版',
  source.`status`,
  @vanz_b2b_package_marker,
  source.`menu_ids`,
  'V038',
  CURRENT_TIMESTAMP,
  'V038',
  CURRENT_TIMESTAMP,
  b'0'
FROM `system_tenant_package` AS source
WHERE source.`id` = @vanz_b2b_source_package_id
  AND source.`deleted` = b'0'
  AND @vanz_b2b_existing_package_id IS NULL;

SET @vanz_b2b_package_id = (
  SELECT `id`
  FROM `system_tenant_package`
  WHERE `remark` = @vanz_b2b_package_marker
    AND `status` = 0
    AND `deleted` = b'0'
  ORDER BY `id`
  LIMIT 1
);

INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_tenant_package`
  WHERE `remark` = @vanz_b2b_package_marker
    AND `status` = 0
    AND `deleted` = b'0'
) <> 1;

INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `system_tenant`
  WHERE `id` <> @vanz_b2b_tenant_id
    AND `package_id` = @vanz_b2b_package_id
    AND `deleted` = b'0'
);

UPDATE `system_tenant`
SET `package_id` = @vanz_b2b_package_id,
    `updater` = 'V038',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @vanz_b2b_tenant_id
  AND `business_mode` = 'B2B'
  AND `deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_menu_routes`;
CREATE TEMPORARY TABLE `vanz_b2b_menu_routes` (
  `menu_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL,
  `full_path` varchar(512) NOT NULL,
  PRIMARY KEY (`menu_id`),
  KEY `idx_vanz_b2b_menu_routes_path` (`full_path`)
) ENGINE=InnoDB;

INSERT INTO `vanz_b2b_menu_routes` (`menu_id`, `parent_id`, `full_path`)
WITH RECURSIVE `menu_routes` AS (
  SELECT
    menu.`id`,
    menu.`parent_id`,
    CONCAT('/', TRIM(BOTH '/' FROM menu.`path`)) AS `full_path`
  FROM `system_menu` AS menu
  WHERE menu.`parent_id` = 0
    AND menu.`type` IN (1, 2)
    AND menu.`deleted` = b'0'

  UNION ALL

  SELECT
    child.`id`,
    child.`parent_id`,
    CONCAT(
      TRIM(TRAILING '/' FROM parent.`full_path`),
      '/',
      TRIM(BOTH '/' FROM child.`path`)
    ) AS `full_path`
  FROM `system_menu` AS child
  INNER JOIN `menu_routes` AS parent ON parent.`id` = child.`parent_id`
  WHERE child.`type` IN (1, 2)
    AND child.`deleted` = b'0'
)
SELECT `id`, `parent_id`, `full_path`
FROM `menu_routes`;

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_managed_route_scope`;
CREATE TEMPORARY TABLE `vanz_b2b_managed_route_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

-- All routes below these business roots are controlled by the business mode.
INSERT INTO `vanz_b2b_managed_route_scope` (`menu_id`)
SELECT route.`menu_id`
FROM `vanz_b2b_menu_routes` AS route
WHERE route.`full_path` IN ('/mall', '/member', '/pay', '/crm', '/seo', '/dashboard', '/ai')
   OR route.`full_path` LIKE '/mall/%'
   OR route.`full_path` LIKE '/member/%'
   OR route.`full_path` LIKE '/pay/%'
   OR route.`full_path` LIKE '/crm/%'
   OR route.`full_path` LIKE '/seo/%'
   OR route.`full_path` LIKE '/dashboard/%'
   OR route.`full_path` LIKE '/ai/%';

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_managed_menu_scope`;
CREATE TEMPORARY TABLE `vanz_b2b_managed_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `vanz_b2b_managed_menu_scope` (`menu_id`)
SELECT `menu_id`
FROM `vanz_b2b_managed_route_scope`;

-- Include button/API permission children of every managed business route.
INSERT IGNORE INTO `vanz_b2b_managed_menu_scope` (`menu_id`)
SELECT child.`id`
FROM `system_menu` AS child
INNER JOIN `vanz_b2b_managed_route_scope` AS parent
  ON parent.`menu_id` = child.`parent_id`
WHERE child.`type` = 3
  AND child.`deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_allowed_path_scope`;
CREATE TEMPORARY TABLE `vanz_b2b_allowed_path_scope` (
  `full_path` varchar(512) NOT NULL,
  PRIMARY KEY (`full_path`)
) ENGINE=InnoDB;

-- BEGIN vanz-b2b business route paths
-- Keep aligned with navigation/furniture-b2b-menu-paths.json.
INSERT INTO `vanz_b2b_allowed_path_scope` (`full_path`) VALUES
  ('/dashboard'),
  ('/seo'),
  ('/seo/metadata'),
  ('/seo/site-config'),
  ('/crm'),
  ('/crm/clue'),
  ('/crm/customer'),
  ('/crm/contact'),
  ('/mall/product'),
  ('/mall/product/spu'),
  ('/mall/product/category'),
  ('/mall/product/brand'),
  ('/mall/product/property'),
  ('/mall/product/comment');
-- END vanz-b2b business route paths

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_allowed_route_scope`;
CREATE TEMPORARY TABLE `vanz_b2b_allowed_route_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `vanz_b2b_allowed_route_scope` (`menu_id`)
SELECT route.`menu_id`
FROM `vanz_b2b_menu_routes` AS route
INNER JOIN `vanz_b2b_allowed_path_scope` AS allowed
  ON allowed.`full_path` = route.`full_path`;

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_allowed_menu_scope`;
CREATE TEMPORARY TABLE `vanz_b2b_allowed_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

-- Selected routes need all menu ancestors so the permission tree remains valid.
INSERT IGNORE INTO `vanz_b2b_allowed_menu_scope` (`menu_id`)
WITH RECURSIVE `allowed_ancestors` AS (
  SELECT route.`id` AS `menu_id`, route.`parent_id`
  FROM `system_menu` AS route
  INNER JOIN `vanz_b2b_allowed_route_scope` AS allowed
    ON allowed.`menu_id` = route.`id`

  UNION ALL

  SELECT parent.`id` AS `menu_id`, parent.`parent_id`
  FROM `system_menu` AS parent
  INNER JOIN `allowed_ancestors` AS child ON child.`parent_id` = parent.`id`
  WHERE parent.`type` IN (1, 2)
    AND parent.`deleted` = b'0'
)
SELECT `menu_id`
FROM `allowed_ancestors`;

-- Every selected page retains its button/API permissions.
INSERT IGNORE INTO `vanz_b2b_allowed_menu_scope` (`menu_id`)
SELECT button.`id`
FROM `system_menu` AS button
INNER JOIN `vanz_b2b_allowed_route_scope` AS route
  ON route.`menu_id` = button.`parent_id`
WHERE button.`type` = 3
  AND button.`status` = 0
  AND button.`deleted` = b'0';

-- The required B2B business routes must exist before any permission is pruned.
INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*) FROM `vanz_b2b_allowed_route_scope`
) <> (
  SELECT COUNT(*) FROM `vanz_b2b_allowed_path_scope`
);

INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE NOT EXISTS (
  SELECT 1
  FROM `vanz_b2b_allowed_menu_scope` AS allowed
  INNER JOIN `system_menu` AS menu ON menu.`id` = allowed.`menu_id`
  WHERE menu.`permission` = 'statistics:dashboard:query'
    AND menu.`type` = 3
    AND menu.`deleted` = b'0'
);

DROP TEMPORARY TABLE IF EXISTS `vanz_b2b_final_menu_scope`;
CREATE TEMPORARY TABLE `vanz_b2b_final_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

-- Preserve source-package administration permissions outside the managed
-- business roots, then add the exact B2B business capability set.
INSERT IGNORE INTO `vanz_b2b_final_menu_scope` (`menu_id`)
SELECT package_menu.`menu_id`
FROM `system_tenant_package` AS package
INNER JOIN JSON_TABLE(
  package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
) AS package_menu
WHERE package.`id` = @vanz_b2b_package_id
  AND NOT EXISTS (
    SELECT 1
    FROM `vanz_b2b_managed_menu_scope` AS managed
    WHERE managed.`menu_id` = package_menu.`menu_id`
  );

INSERT IGNORE INTO `vanz_b2b_final_menu_scope` (`menu_id`)
SELECT `menu_id`
FROM `vanz_b2b_allowed_menu_scope`;

UPDATE `system_tenant_package` AS package
SET package.`menu_ids` = (
      SELECT CAST(JSON_ARRAYAGG(final_menu.`menu_id`) AS CHAR CHARACTER SET utf8mb4)
      FROM (
        SELECT `menu_id`
        FROM `vanz_b2b_final_menu_scope`
        ORDER BY `menu_id`
      ) AS final_menu
    ),
    package.`name` = 'Vanz B2B 询盘版',
    package.`updater` = 'V038',
    package.`update_time` = CURRENT_TIMESTAMP
WHERE package.`id` = @vanz_b2b_package_id;

-- Custom roles only lose permissions outside the package. The built-in tenant
-- administrator receives the complete package, matching TenantService behavior.
UPDATE `system_role_menu` AS role_menu
INNER JOIN `system_role` AS role ON role.`id` = role_menu.`role_id`
SET role_menu.`deleted` = b'1',
    role_menu.`updater` = 'V038',
    role_menu.`update_time` = CURRENT_TIMESTAMP
WHERE role.`tenant_id` = @vanz_b2b_tenant_id
  AND role.`deleted` = b'0'
  AND role_menu.`tenant_id` = @vanz_b2b_tenant_id
  AND role_menu.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `vanz_b2b_final_menu_scope` AS allowed
    WHERE allowed.`menu_id` = role_menu.`menu_id`
  );

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`,
   `deleted`, `tenant_id`)
SELECT
  role.`id`, allowed.`menu_id`, 'V038', CURRENT_TIMESTAMP,
  'V038', CURRENT_TIMESTAMP, b'0', @vanz_b2b_tenant_id
FROM `system_role` AS role
CROSS JOIN `vanz_b2b_final_menu_scope` AS allowed
WHERE role.`tenant_id` = @vanz_b2b_tenant_id
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` AS existing
    WHERE existing.`role_id` = role.`id`
      AND existing.`menu_id` = allowed.`menu_id`
      AND existing.`tenant_id` = @vanz_b2b_tenant_id
      AND existing.`deleted` = b'0'
  );

-- Verify package isolation, business exclusions and role enforcement.
INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `system_tenant_package` AS package
  INNER JOIN JSON_TABLE(
    package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
  ) AS package_menu
  INNER JOIN `vanz_b2b_managed_menu_scope` AS managed
    ON managed.`menu_id` = package_menu.`menu_id`
  WHERE package.`id` = @vanz_b2b_package_id
    AND NOT EXISTS (
      SELECT 1
      FROM `vanz_b2b_allowed_menu_scope` AS allowed
      WHERE allowed.`menu_id` = managed.`menu_id`
    )
);

INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `system_role_menu` AS role_menu
  INNER JOIN `system_role` AS role ON role.`id` = role_menu.`role_id`
  WHERE role.`tenant_id` = @vanz_b2b_tenant_id
    AND role.`deleted` = b'0'
    AND role_menu.`tenant_id` = @vanz_b2b_tenant_id
    AND role_menu.`deleted` = b'0'
    AND NOT EXISTS (
      SELECT 1
      FROM `vanz_b2b_final_menu_scope` AS allowed
      WHERE allowed.`menu_id` = role_menu.`menu_id`
    )
);

INSERT INTO `vanz_b2b_permission_guard` (`valid`)
SELECT 0
FROM `system_role` AS role
WHERE role.`tenant_id` = @vanz_b2b_tenant_id
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND EXISTS (
    SELECT 1
    FROM `vanz_b2b_final_menu_scope` AS allowed
    WHERE NOT EXISTS (
      SELECT 1
      FROM `system_role_menu` AS role_menu
      WHERE role_menu.`role_id` = role.`id`
        AND role_menu.`menu_id` = allowed.`menu_id`
        AND role_menu.`tenant_id` = @vanz_b2b_tenant_id
        AND role_menu.`deleted` = b'0'
    )
  );

COMMIT;

DROP TEMPORARY TABLE `vanz_b2b_final_menu_scope`;
DROP TEMPORARY TABLE `vanz_b2b_allowed_menu_scope`;
DROP TEMPORARY TABLE `vanz_b2b_allowed_route_scope`;
DROP TEMPORARY TABLE `vanz_b2b_allowed_path_scope`;
DROP TEMPORARY TABLE `vanz_b2b_managed_menu_scope`;
DROP TEMPORARY TABLE `vanz_b2b_managed_route_scope`;
DROP TEMPORARY TABLE `vanz_b2b_menu_routes`;
DROP TEMPORARY TABLE `vanz_b2b_permission_guard`;
