-- Re-enable ERP self-registration after the backend can atomically bind each new
-- account to its selected tenant and the tenant's system-managed mall_operator role.

-- Ensure every active business tenant has the non-administrator registration role.
INSERT INTO `system_role`
  (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT
  '商城运营', 'mall_operator', 1, 1, '', 0, 1,
  '公开注册默认角色；商品、订单、售后、会员和商城内容日常运营',
  'V036-register', CURRENT_TIMESTAMP, 'V036-register', CURRENT_TIMESTAMP, b'0', tenant.`id`
FROM `system_tenant` AS tenant
WHERE tenant.`id` <> 1
  AND tenant.`status` = 0
  AND tenant.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role` AS existing_role
    WHERE existing_role.`tenant_id` = tenant.`id`
      AND existing_role.`code` = 'mall_operator'
      AND existing_role.`deleted` = b'0'
  );

UPDATE `system_role`
SET `name` = '商城运营',
    `sort` = 1,
    `data_scope` = 1,
    `status` = 0,
    `type` = 1,
    `remark` = '公开注册默认角色；商品、订单、售后、会员和商城内容日常运营',
    `updater` = 'V036-register',
    `update_time` = CURRENT_TIMESTAMP
WHERE `tenant_id` <> 1
  AND `code` = 'mall_operator'
  AND `deleted` = b'0';

-- Fail before enabling registration if an active business tenant does not have both
-- its tenant administrator source role and its registration target role.
DROP TEMPORARY TABLE IF EXISTS `oakved_registration_role_guard`;
CREATE TEMPORARY TABLE `oakved_registration_role_guard` (
  `tenant_id` bigint NOT NULL,
  `tenant_admin_role_id` bigint NOT NULL,
  `operator_role_id` bigint NOT NULL
) ENGINE=InnoDB;
INSERT INTO `oakved_registration_role_guard`
  (`tenant_id`, `tenant_admin_role_id`, `operator_role_id`)
SELECT
  tenant.`id`,
  (
    SELECT admin_role.`id`
    FROM `system_role` AS admin_role
    WHERE admin_role.`tenant_id` = tenant.`id`
      AND admin_role.`code` = 'tenant_admin'
      AND admin_role.`deleted` = b'0'
    ORDER BY admin_role.`id`
    LIMIT 1
  ),
  (
    SELECT operator_role.`id`
    FROM `system_role` AS operator_role
    WHERE operator_role.`tenant_id` = tenant.`id`
      AND operator_role.`code` = 'mall_operator'
      AND operator_role.`deleted` = b'0'
    ORDER BY operator_role.`id`
    LIMIT 1
  )
FROM `system_tenant` AS tenant
WHERE tenant.`id` <> 1
  AND tenant.`status` = 0
  AND tenant.`deleted` = b'0';
DROP TEMPORARY TABLE `oakved_registration_role_guard`;

-- Build the safe daily-operation permission set and include all menu ancestors.
DROP TEMPORARY TABLE IF EXISTS `oakved_registration_menu_scope`;
CREATE TEMPORARY TABLE `oakved_registration_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_registration_menu_scope` (`menu_id`)
WITH RECURSIVE `operator_menu_tree` AS (
  SELECT `id`, `parent_id`
  FROM `system_menu`
  WHERE `status` = 0
    AND `deleted` = b'0'
    AND (
      `permission` LIKE 'product:%'
      OR `permission` LIKE 'trade:order:%'
      OR `permission` LIKE 'trade:after-sale:%'
      OR `permission` LIKE 'trade:fulfillment:%'
      OR `permission` LIKE 'trade:delivery:%'
      OR `permission` LIKE 'member:user:%'
      OR `permission` LIKE 'member:group:%'
      OR `permission` LIKE 'member:tag:%'
      OR `permission` LIKE 'member:level:%'
      OR `permission` LIKE 'member:trade-application:%'
      OR `permission` LIKE 'promotion:%'
      OR `permission` LIKE 'seo:%'
      OR `permission` LIKE 'statistics:member:%'
      OR `permission` LIKE 'statistics:product:%'
      OR `permission` LIKE 'statistics:trade:%'
      OR `permission` = 'statistics:dashboard:query'
      OR `permission` LIKE 'crm:clue:%'
      OR `permission` LIKE 'crm:customer:%'
      OR `permission` LIKE 'crm:contact:%'
      OR `permission` LIKE 'crm:business:%'
      OR `permission` LIKE 'crm:product:%'
      OR `permission` LIKE 'crm:product-category:%'
      OR `permission` LIKE 'erp:product:%'
      OR `permission` LIKE 'erp:product-category:%'
      OR `permission` LIKE 'erp:product-unit:%'
      OR `permission` LIKE 'erp:customer:%'
      OR `permission` LIKE 'erp:sale-order:%'
      OR `permission` LIKE 'erp:sale-out:%'
      OR `permission` LIKE 'erp:sale-return:%'
      OR `permission` = 'erp:stock:query'
      OR `permission` = 'erp:stock:export'
      OR `permission` LIKE 'erp:stock-record:%'
    )
  UNION DISTINCT
  SELECT parent.`id`, parent.`parent_id`
  FROM `system_menu` AS parent
  INNER JOIN `operator_menu_tree` AS child ON child.`parent_id` = parent.`id`
  WHERE parent.`status` = 0
    AND parent.`deleted` = b'0'
)
SELECT DISTINCT `id`
FROM `operator_menu_tree`;

-- Rebuild every registration role from the matching tenant administrator's package
-- permissions, intersected with the safe operation scope above.
UPDATE `system_role_menu` AS role_menu
INNER JOIN `system_role` AS operator_role ON operator_role.`id` = role_menu.`role_id`
SET role_menu.`deleted` = b'1',
    role_menu.`updater` = 'V036-register',
    role_menu.`update_time` = CURRENT_TIMESTAMP
WHERE operator_role.`tenant_id` <> 1
  AND operator_role.`code` = 'mall_operator'
  AND operator_role.`deleted` = b'0'
  AND role_menu.`deleted` = b'0';

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT
  operator_role.`id`, admin_menu.`menu_id`,
  'V036-register', CURRENT_TIMESTAMP, 'V036-register', CURRENT_TIMESTAMP, b'0',
  operator_role.`tenant_id`
FROM `system_role` AS operator_role
INNER JOIN `system_role` AS admin_role
  ON admin_role.`tenant_id` = operator_role.`tenant_id`
 AND admin_role.`code` = 'tenant_admin'
 AND admin_role.`deleted` = b'0'
INNER JOIN `system_role_menu` AS admin_menu
  ON admin_menu.`role_id` = admin_role.`id`
 AND admin_menu.`deleted` = b'0'
INNER JOIN `oakved_registration_menu_scope` AS scope
  ON scope.`menu_id` = admin_menu.`menu_id`
WHERE operator_role.`tenant_id` <> 1
  AND operator_role.`code` = 'mall_operator'
  AND operator_role.`deleted` = b'0';

DROP TEMPORARY TABLE `oakved_registration_menu_scope`;

-- Keep the switch record available on installations that did not seed it, and turn it
-- on only after all role prerequisites and permission rewrites have completed.
INSERT INTO `infra_config`
  (`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT
  '用户管理', 2, '用户管理-注册开关', 'system.user.register-enabled', 'true', b'0',
  '注册账号自动绑定当前租户的商城运营角色',
  'V036-register', CURRENT_TIMESTAMP, 'V036-register', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `infra_config`
  WHERE `config_key` = 'system.user.register-enabled'
    AND `deleted` = b'0'
);

UPDATE `infra_config`
SET `value` = 'true',
    `remark` = '注册账号自动绑定当前租户的商城运营角色',
    `updater` = 'V036-register',
    `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'system.user.register-enabled'
  AND `deleted` = b'0';
