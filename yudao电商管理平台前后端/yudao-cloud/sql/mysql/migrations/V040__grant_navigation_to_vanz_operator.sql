-- Grant the business-editable website navigation page to the VANZ operations
-- role. V039 registered the menu and granted it to tenant administrators, but
-- the only active VANZ business account is bound to mall_operator by V035.

DROP TEMPORARY TABLE IF EXISTS `website_navigation_operator_guard`;
CREATE TEMPORARY TABLE `website_navigation_operator_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_website_navigation_operator_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

SET @website_navigation_operator_tenant_id = 162;
SET @website_navigation_operator_menu_id = (
  SELECT MIN(child.`id`)
  FROM `system_menu` AS child
  INNER JOIN `system_menu` AS parent ON parent.`id` = child.`parent_id`
  WHERE parent.`path` = '/seo'
    AND parent.`type` = 1
    AND parent.`deleted` = b'0'
    AND child.`path` = 'navigation'
    AND child.`type` = 2
    AND child.`deleted` = b'0'
);
SET @website_navigation_operator_role_id = (
  SELECT MIN(`id`)
  FROM `system_role`
  WHERE `tenant_id` = @website_navigation_operator_tenant_id
    AND `code` = 'mall_operator'
    AND `type` = 1
    AND `status` = 0
    AND `deleted` = b'0'
);

INSERT INTO `website_navigation_operator_guard` (`valid`)
SELECT 0
WHERE @website_navigation_operator_menu_id IS NULL
   OR @website_navigation_operator_role_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS `website_navigation_operator_menu_scope`;
CREATE TEMPORARY TABLE `website_navigation_operator_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `website_navigation_operator_menu_scope` (`menu_id`)
SELECT @website_navigation_operator_menu_id
UNION
SELECT `id`
FROM `system_menu`
WHERE `permission` IN (
  'seo:navigation:query',
  'seo:navigation:update',
  'seo:navigation:preview',
  'seo:navigation:publish'
)
  AND `deleted` = b'0';

INSERT INTO `website_navigation_operator_guard` (`valid`)
SELECT 0
WHERE (SELECT COUNT(*) FROM `website_navigation_operator_menu_scope`) <> 5;

START TRANSACTION;

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT @website_navigation_operator_role_id, scope.`menu_id`,
       'V040', CURRENT_TIMESTAMP, 'V040', CURRENT_TIMESTAMP, b'0',
       @website_navigation_operator_tenant_id
FROM `website_navigation_operator_menu_scope` AS scope
WHERE NOT EXISTS (
  SELECT 1
  FROM `system_role_menu` AS existing
  WHERE existing.`role_id` = @website_navigation_operator_role_id
    AND existing.`menu_id` = scope.`menu_id`
    AND existing.`tenant_id` = @website_navigation_operator_tenant_id
    AND existing.`deleted` = b'0'
);

INSERT INTO `website_navigation_operator_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `website_navigation_operator_menu_scope` AS scope
  WHERE NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` AS role_menu
    WHERE role_menu.`role_id` = @website_navigation_operator_role_id
      AND role_menu.`menu_id` = scope.`menu_id`
      AND role_menu.`tenant_id` = @website_navigation_operator_tenant_id
      AND role_menu.`deleted` = b'0'
  )
);

COMMIT;

DROP TEMPORARY TABLE `website_navigation_operator_menu_scope`;
DROP TEMPORARY TABLE `website_navigation_operator_guard`;
