-- Bind every ERP account to one immutable home tenant and one active role.
-- Mainline V035 follows the existing tenant SKU and B2B product-field migrations.

-- Resolve the two approved accounts without assuming auto-increment IDs.
SET @admin_user_id := (
  SELECT `id`
  FROM `system_users`
  WHERE `username` = 'admin'
    AND `deleted` = b'0'
  ORDER BY (`tenant_id` = 1) DESC, `id`
  LIMIT 1
);
SET @vanz_user_id := (
  SELECT `id`
  FROM `system_users`
  WHERE `username` = 'vanzadmin'
    AND `deleted` = b'0'
  ORDER BY (`tenant_id` = 162) DESC, `id`
  LIMIT 1
);

-- Fail before any destructive update if the two canonical accounts or VANZ tenant
-- are missing. The NOT NULL guard makes Flyway stop instead of locking everyone out.
DROP TEMPORARY TABLE IF EXISTS `oakved_account_binding_guard`;
CREATE TEMPORARY TABLE `oakved_account_binding_guard` (
  `admin_user_id` bigint NOT NULL,
  `vanz_user_id` bigint NOT NULL,
  `vanz_tenant_id` bigint NOT NULL
) ENGINE=InnoDB;
INSERT INTO `oakved_account_binding_guard`
  (`admin_user_id`, `vanz_user_id`, `vanz_tenant_id`)
VALUES (
  @admin_user_id,
  @vanz_user_id,
  (SELECT `id` FROM `system_tenant` WHERE `id` = 162 AND `deleted` = b'0' LIMIT 1)
);
DROP TEMPORARY TABLE `oakved_account_binding_guard`;

-- Public registration cannot choose a tenant and role, so it must remain closed.
UPDATE `infra_config`
SET `value` = 'false',
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'system.user.register-enabled'
  AND `deleted` = b'0';

UPDATE `system_users`
SET `tenant_id` = 1,
    `status` = 0,
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @admin_user_id;

UPDATE `system_users`
SET `tenant_id` = 162,
    `status` = 0,
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @vanz_user_id;

-- Historical/demo accounts are retained for audit, but cannot log in.
UPDATE `system_users`
SET `status` = 1,
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `deleted` = b'0'
  AND (`id` <> COALESCE(@admin_user_id, -1))
  AND (`id` <> COALESCE(@vanz_user_id, -1));

-- Create or normalize the VANZ operations role. It is system-managed so an assigned
-- role cannot be casually deleted and leave an account without a role.
INSERT INTO `system_role`
  (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT
  '商城运营', 'mall_operator', 1, 1, '', 0, 1,
  '商品、订单、售后、会员和商城内容日常运营；不含系统、权限、支付及财务结算',
  'V035-single-role', CURRENT_TIMESTAMP, 'V035-single-role', CURRENT_TIMESTAMP, b'0', 162
FROM `system_tenant`
WHERE `id` = 162
  AND `deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role`
    WHERE `tenant_id` = 162
      AND `code` = 'mall_operator'
      AND `deleted` = b'0'
  );

UPDATE `system_role`
SET `name` = '商城运营',
    `sort` = 1,
    `data_scope` = 1,
    `status` = 0,
    `type` = 1,
    `remark` = '商品、订单、售后、会员和商城内容日常运营；不含系统、权限、支付及财务结算',
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `tenant_id` = 162
  AND `code` = 'mall_operator'
  AND `deleted` = b'0';

SET @super_admin_role_id := (
  SELECT `id` FROM `system_role`
  WHERE `tenant_id` = 1 AND `code` = 'super_admin' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);
SET @vanz_admin_role_id := (
  SELECT `id` FROM `system_role`
  WHERE `tenant_id` = 162 AND `code` = 'tenant_admin' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);
SET @vanz_operator_role_id := (
  SELECT `id` FROM `system_role`
  WHERE `tenant_id` = 162 AND `code` = 'mall_operator' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);

-- The binding rewrite below is destructive, so verify all three target roles first.
DROP TEMPORARY TABLE IF EXISTS `oakved_role_binding_guard`;
CREATE TEMPORARY TABLE `oakved_role_binding_guard` (
  `super_admin_role_id` bigint NOT NULL,
  `vanz_admin_role_id` bigint NOT NULL,
  `vanz_operator_role_id` bigint NOT NULL
) ENGINE=InnoDB;
INSERT INTO `oakved_role_binding_guard`
  (`super_admin_role_id`, `vanz_admin_role_id`, `vanz_operator_role_id`)
VALUES (@super_admin_role_id, @vanz_admin_role_id, @vanz_operator_role_id);
DROP TEMPORARY TABLE `oakved_role_binding_guard`;

-- Build the operator permission set from explicit daily-operation permissions and
-- their menu ancestors. Intersecting with tenant_admin below keeps package boundaries.
DROP TEMPORARY TABLE IF EXISTS `oakved_operator_menu_scope`;
CREATE TEMPORARY TABLE `oakved_operator_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_operator_menu_scope` (`menu_id`)
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
      OR `permission` LIKE 'erp:stock:query'
      OR `permission` LIKE 'erp:stock:export'
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

UPDATE `system_role_menu`
SET `deleted` = b'1',
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `role_id` = @vanz_operator_role_id
  AND `deleted` = b'0';

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT
  @vanz_operator_role_id, admin_menu.`menu_id`,
  'V035-single-role', CURRENT_TIMESTAMP, 'V035-single-role', CURRENT_TIMESTAMP, b'0', 162
FROM `system_role_menu` AS admin_menu
INNER JOIN `oakved_operator_menu_scope` AS scope ON scope.`menu_id` = admin_menu.`menu_id`
WHERE admin_menu.`role_id` = @vanz_admin_role_id
  AND admin_menu.`deleted` = b'0'
  AND @vanz_operator_role_id IS NOT NULL;

DROP TEMPORARY TABLE `oakved_operator_menu_scope`;

-- Remove cross-tenant switching from every non-super-admin role already in the DB.
UPDATE `system_role_menu` AS role_menu
INNER JOIN `system_role` AS role_info ON role_info.`id` = role_menu.`role_id`
INNER JOIN `system_menu` AS menu_info ON menu_info.`id` = role_menu.`menu_id`
SET role_menu.`deleted` = b'1',
    role_menu.`updater` = 'V035-single-role',
    role_menu.`update_time` = CURRENT_TIMESTAMP
WHERE menu_info.`permission` = 'system:tenant:visit'
  AND role_info.`code` <> 'super_admin'
  AND role_menu.`deleted` = b'0';

-- Replace all active many-to-many rows with the two approved one-to-one mappings.
UPDATE `system_user_role`
SET `deleted` = b'1',
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `deleted` = b'0';

INSERT INTO `system_user_role`
  (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT
  @admin_user_id, @super_admin_role_id,
  'V035-single-role', CURRENT_TIMESTAMP, 'V035-single-role', CURRENT_TIMESTAMP, b'0', 1
WHERE @admin_user_id IS NOT NULL
  AND @super_admin_role_id IS NOT NULL;

INSERT INTO `system_user_role`
  (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT
  @vanz_user_id, @vanz_operator_role_id,
  'V035-single-role', CURRENT_TIMESTAMP, 'V035-single-role', CURRENT_TIMESTAMP, b'0', 162
WHERE @vanz_user_id IS NOT NULL
  AND @vanz_operator_role_id IS NOT NULL;

-- Force all ERP administrators to log in again after their role boundary changes.
UPDATE `system_oauth2_access_token`
SET `deleted` = b'1',
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `user_type` = 2
  AND `deleted` = b'0';

UPDATE `system_oauth2_refresh_token`
SET `deleted` = b'1',
    `updater` = 'V035-single-role',
    `update_time` = CURRENT_TIMESTAMP
WHERE `user_type` = 2
  AND `deleted` = b'0';

-- Database-level concurrency guards: one live username per tenant and one live role
-- row per user. Deleted history remains available because generated values become NULL.
ALTER TABLE `system_users`
  ADD COLUMN `active_username` varchar(30)
    GENERATED ALWAYS AS (IF(`deleted` = b'0', `username`, NULL)) STORED
    COMMENT '未删除账号名（唯一约束辅助列）'
    AFTER `tenant_id`,
  ADD UNIQUE KEY `uk_system_users_tenant_active_username` (`tenant_id`, `active_username`);

ALTER TABLE `system_user_role`
  ADD COLUMN `active_user_id` bigint
    GENERATED ALWAYS AS (IF(`deleted` = b'0', `user_id`, NULL)) STORED
    COMMENT '未删除用户编号（唯一角色约束辅助列）'
    AFTER `tenant_id`,
  ADD UNIQUE KEY `uk_system_user_role_active_user` (`active_user_id`);
