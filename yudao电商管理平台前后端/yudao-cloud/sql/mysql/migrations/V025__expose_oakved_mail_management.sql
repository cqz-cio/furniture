-- Expose the built-in mail-management pages only to the Oakved storefront tenant.
-- Tenant 121 currently shares its package with another tenant, so clone the
-- package before adding mail permissions instead of widening the shared package.
SET @oakved_mail_tenant_id = 121;
SET @oakved_mail_package_marker =
  _utf8mb4'oakved:tenant-121:mail-management' COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS `oakved_mail_menu_scope`;
CREATE TEMPORARY TABLE `oakved_mail_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_mail_menu_scope` (`menu_id`) VALUES
  (1), (2739),
  (2130), (2131), (2132), (2133), (2134), (2135),
  (2136), (2137), (2138), (2139), (2140),
  (2141), (2142), (2143);

-- Fail closed if the target tenant, role, or published mail routes drifted.
DROP TEMPORARY TABLE IF EXISTS `oakved_mail_management_guard`;
CREATE TEMPORARY TABLE `oakved_mail_management_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_oakved_mail_management_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_tenant` AS tenant
  INNER JOIN `system_tenant_package` AS package ON package.`id` = tenant.`package_id`
  WHERE tenant.`id` = @oakved_mail_tenant_id
    AND tenant.`status` = 0
    AND tenant.`deleted` = b'0'
    AND package.`status` = 0
    AND package.`deleted` = b'0'
) <> 1;

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_role`
  WHERE `tenant_id` = @oakved_mail_tenant_id
    AND `code` = 'tenant_admin'
    AND `type` = 1
    AND `status` = 0
    AND `deleted` = b'0'
) <> 1;

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_menu` AS menu
  INNER JOIN `oakved_mail_menu_scope` AS scope ON scope.`menu_id` = menu.`id`
  WHERE menu.`status` = 0
    AND menu.`visible` = b'1'
    AND menu.`deleted` = b'0'
) <> 16;

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE
  (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = 2739 AND `parent_id` = 1 AND `type` = 1
      AND `path` = 'messages' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = 2130 AND `parent_id` = 2739 AND `type` = 2
      AND `path` = 'mail' AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = 2131 AND `parent_id` = 2130 AND `type` = 2
      AND `path` = 'mail-account' AND `component` = 'system/mail/account/index'
      AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = 2136 AND `parent_id` = 2130 AND `type` = 2
      AND `path` = 'mail-template' AND `component` = 'system/mail/template/index'
      AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = 2141 AND `parent_id` = 2130 AND `type` = 2
      AND `path` = 'mail-log' AND `component` = 'system/mail/log/index'
      AND `deleted` = b'0') <> 1
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` BETWEEN 2132 AND 2135 AND `parent_id` = 2131 AND `type` = 3
      AND `deleted` = b'0') <> 4
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE (`id` BETWEEN 2137 AND 2140 OR `id` = 2143)
      AND `parent_id` = 2136 AND `type` = 3 AND `deleted` = b'0') <> 5
  OR (SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = 2142 AND `parent_id` = 2141 AND `type` = 3
      AND `deleted` = b'0') <> 1;

SET @oakved_mail_source_package_id = (
  SELECT `package_id`
  FROM `system_tenant`
  WHERE `id` = @oakved_mail_tenant_id AND `deleted` = b'0'
  LIMIT 1
);

INSERT INTO `system_tenant_package`
(`name`, `status`, `remark`, `menu_ids`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`)
SELECT 'Oakved 邮箱管理', source.`status`, @oakved_mail_package_marker,
       source.`menu_ids`, 'V025', CURRENT_TIMESTAMP,
       'V025', CURRENT_TIMESTAMP, b'0'
FROM `system_tenant_package` AS source
WHERE source.`id` = @oakved_mail_source_package_id
  AND source.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_tenant_package`
    WHERE `remark` = @oakved_mail_package_marker AND `deleted` = b'0'
  );

SET @oakved_mail_package_id = (
  SELECT `id`
  FROM `system_tenant_package`
  WHERE `remark` = @oakved_mail_package_marker AND `deleted` = b'0'
  ORDER BY `id`
  LIMIT 1
);

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(*)
  FROM `system_tenant_package`
  WHERE `remark` = @oakved_mail_package_marker AND `deleted` = b'0'
) <> 1;

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE NOT EXISTS (
  SELECT 1
  FROM `system_tenant_package`
  WHERE `id` = @oakved_mail_package_id
    AND `status` = 0
    AND JSON_VALID(`menu_ids`)
    AND `deleted` = b'0'
);

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE EXISTS (
  SELECT 1
  FROM `system_tenant`
  WHERE `id` <> @oakved_mail_tenant_id
    AND `package_id` = @oakved_mail_package_id
    AND `deleted` = b'0'
);

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 1),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '1', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2739),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2739', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2130),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2130', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2131),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2131', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2132),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2132', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2133),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2133', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2134),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2134', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2135),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2135', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2136),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2136', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2137),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2137', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2138),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2138', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2139),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2139', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2140),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2140', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2141),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2141', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2142),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2142', '$') = 0;

UPDATE `system_tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 2143),
    `updater` = 'V025', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_package_id
  AND JSON_CONTAINS(`menu_ids`, '2143', '$') = 0;

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(DISTINCT scope.`menu_id`)
  FROM `system_tenant_package` AS package
  INNER JOIN JSON_TABLE(
    package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
  ) AS package_menu
  INNER JOIN `oakved_mail_menu_scope` AS scope
    ON scope.`menu_id` = package_menu.`menu_id`
  WHERE package.`id` = @oakved_mail_package_id
) <> 16;

UPDATE `system_tenant`
SET `package_id` = @oakved_mail_package_id,
    `updater` = 'V025',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_mail_tenant_id
  AND `deleted` = b'0';

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`,
 `deleted`, `tenant_id`)
SELECT role.`id`, scope.`menu_id`, 'V025', CURRENT_TIMESTAMP,
       'V025', CURRENT_TIMESTAMP, b'0', @oakved_mail_tenant_id
FROM `system_role` AS role
CROSS JOIN `oakved_mail_menu_scope` AS scope
WHERE role.`tenant_id` = @oakved_mail_tenant_id
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` AS role_menu
    WHERE role_menu.`role_id` = role.`id`
      AND role_menu.`menu_id` = scope.`menu_id`
      AND role_menu.`tenant_id` = @oakved_mail_tenant_id
      AND role_menu.`deleted` = b'0'
  );

INSERT INTO `oakved_mail_management_guard` (`valid`)
SELECT 0 WHERE (
  SELECT COUNT(DISTINCT role_menu.`menu_id`)
  FROM `system_role_menu` AS role_menu
  INNER JOIN `system_role` AS role ON role.`id` = role_menu.`role_id`
  INNER JOIN `oakved_mail_menu_scope` AS scope ON scope.`menu_id` = role_menu.`menu_id`
  WHERE role.`tenant_id` = @oakved_mail_tenant_id
    AND role.`code` = 'tenant_admin'
    AND role.`deleted` = b'0'
    AND role_menu.`tenant_id` = @oakved_mail_tenant_id
    AND role_menu.`deleted` = b'0'
) <> 16;

DROP TEMPORARY TABLE `oakved_mail_management_guard`;
DROP TEMPORARY TABLE `oakved_mail_menu_scope`;
