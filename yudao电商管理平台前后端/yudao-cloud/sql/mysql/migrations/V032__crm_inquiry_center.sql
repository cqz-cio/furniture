-- Turn the generic CRM clue area into the VANZ inquiry collection center.
-- The original CRM tables remain available in code, while the tenant package
-- exposes only inquiry aggregation, customer records, and contacts.

ALTER TABLE `crm_clue`
  ADD COLUMN `external_inquiry_id` varchar(64) DEFAULT NULL COMMENT '官网询盘幂等编号' AFTER `id`,
  ADD COLUMN `contact_name` varchar(60) NOT NULL DEFAULT '' COMMENT '网页联系人姓名' AFTER `name`,
  ADD COLUMN `company_name` varchar(80) NOT NULL DEFAULT '' COMMENT '网页公司名称' AFTER `contact_name`,
  ADD COLUMN `country_code` varchar(8) NOT NULL DEFAULT '' COMMENT '国家或地区电话区号' AFTER `company_name`,
  ADD COLUMN `inquiry_subject` varchar(100) NOT NULL DEFAULT '' COMMENT '询盘主题' AFTER `country_code`,
  ADD COLUMN `inquiry_message` text DEFAULT NULL COMMENT '询盘原始内容' AFTER `inquiry_subject`,
  ADD COLUMN `source_page` varchar(255) NOT NULL DEFAULT '' COMMENT '提交页面' AFTER `inquiry_message`,
  ADD COLUMN `locale` varchar(32) NOT NULL DEFAULT '' COMMENT '浏览器语言' AFTER `source_page`,
  ADD COLUMN `utm_source` varchar(100) NOT NULL DEFAULT '' COMMENT 'UTM 来源' AFTER `locale`,
  ADD COLUMN `utm_medium` varchar(100) NOT NULL DEFAULT '' COMMENT 'UTM 媒介' AFTER `utm_source`,
  ADD COLUMN `utm_campaign` varchar(100) NOT NULL DEFAULT '' COMMENT 'UTM 活动' AFTER `utm_medium`,
  ADD COLUMN `submitted_at` datetime DEFAULT NULL COMMENT '网页提交时间' AFTER `utm_campaign`,
  ADD COLUMN `process_status` int NOT NULL DEFAULT 0 COMMENT '处理状态：0 待处理，10 处理中，20 已处理，30 无效' AFTER `submitted_at`,
  ADD COLUMN `processed_at` datetime DEFAULT NULL COMMENT '处理完成时间' AFTER `process_status`,
  ADD COLUMN `contact_id` bigint DEFAULT NULL COMMENT '转换后的联系人编号' AFTER `customer_id`,
  MODIFY COLUMN `telephone` varchar(64) NOT NULL DEFAULT '' COMMENT '电话或 WhatsApp',
  ADD UNIQUE KEY `uk_crm_clue_tenant_external` (`tenant_id`, `external_inquiry_id`, `deleted`),
  ADD KEY `idx_crm_clue_tenant_process` (`tenant_id`, `process_status`, `submitted_at`, `deleted`),
  ADD KEY `idx_crm_clue_tenant_contact` (`tenant_id`, `contact_id`, `deleted`);

ALTER TABLE `crm_customer`
  MODIFY COLUMN `telephone` varchar(64) NOT NULL DEFAULT '' COMMENT '电话或 WhatsApp';

ALTER TABLE `crm_contact`
  MODIFY COLUMN `telephone` varchar(64) NOT NULL DEFAULT '' COMMENT '电话或 WhatsApp';

UPDATE `system_menu`
SET `name` = CASE `id`
      WHEN 2397 THEN '询盘中心'
      WHEN 2404 THEN '询盘汇总'
      WHEN 2391 THEN '客户档案'
      WHEN 2416 THEN '联系人管理'
      ELSE `name`
    END,
    `updater` = 'V032',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN (2397, 2404, 2391, 2416)
  AND `deleted` = b'0';

SET @oakved_inquiry_target_tenant_id = COALESCE(
  (
    SELECT `id`
    FROM `system_tenant`
    WHERE `id` = 162 AND `status` = 0 AND `deleted` = b'0'
    LIMIT 1
  ),
  (
    SELECT `id`
    FROM `system_tenant`
    WHERE `id` = 121 AND `status` = 0 AND `deleted` = b'0'
    LIMIT 1
  )
);

SET @oakved_inquiry_package_id = (
  SELECT `package_id`
  FROM `system_tenant`
  WHERE `id` = @oakved_inquiry_target_tenant_id
    AND `deleted` = b'0'
  LIMIT 1
);

DROP TEMPORARY TABLE IF EXISTS `oakved_inquiry_guard`;
CREATE TEMPORARY TABLE `oakved_inquiry_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_oakved_inquiry_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `oakved_inquiry_guard` (`valid`)
SELECT 0
WHERE @oakved_inquiry_target_tenant_id IS NULL
   OR @oakved_inquiry_package_id IS NULL
   OR (
     SELECT COUNT(*)
     FROM `information_schema`.`columns`
     WHERE `table_schema` = DATABASE()
       AND `table_name` = 'crm_clue'
       AND `column_name` IN (
         'external_inquiry_id', 'contact_name', 'company_name', 'country_code',
         'inquiry_subject', 'inquiry_message', 'source_page', 'locale',
         'utm_source', 'utm_medium', 'utm_campaign', 'submitted_at',
         'process_status', 'processed_at', 'contact_id'
       )
   ) <> 15;

DROP TEMPORARY TABLE IF EXISTS `oakved_inquiry_full_crm_scope`;
CREATE TEMPORARY TABLE `oakved_inquiry_full_crm_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_inquiry_full_crm_scope` (`menu_id`)
WITH RECURSIVE `crm_menu_tree` AS (
  SELECT `id`
  FROM `system_menu`
  WHERE `id` = 2397 AND `deleted` = b'0'
  UNION ALL
  SELECT child.`id`
  FROM `system_menu` AS child
  INNER JOIN `crm_menu_tree` AS parent ON child.`parent_id` = parent.`id`
  WHERE child.`deleted` = b'0'
)
SELECT `id` FROM `crm_menu_tree`;

DROP TEMPORARY TABLE IF EXISTS `oakved_inquiry_allowed_crm_scope`;
CREATE TEMPORARY TABLE `oakved_inquiry_allowed_crm_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_inquiry_allowed_crm_scope` (`menu_id`)
SELECT `id`
FROM `system_menu`
WHERE `id` = 2397 AND `deleted` = b'0';

INSERT IGNORE INTO `oakved_inquiry_allowed_crm_scope` (`menu_id`)
WITH RECURSIVE `inquiry_menu_tree` AS (
  SELECT `id`
  FROM `system_menu`
  WHERE `id` IN (2391, 2404, 2416)
    AND `deleted` = b'0'
  UNION ALL
  SELECT child.`id`
  FROM `system_menu` AS child
  INNER JOIN `inquiry_menu_tree` AS parent ON child.`parent_id` = parent.`id`
  WHERE child.`deleted` = b'0'
)
SELECT `id` FROM `inquiry_menu_tree`;

INSERT INTO `oakved_inquiry_guard` (`valid`)
SELECT 0
WHERE (SELECT COUNT(*) FROM `oakved_inquiry_allowed_crm_scope`) < 4;

-- MySQL temporary tables cannot be reopened twice in one statement, so keep
-- the cardinality and containment guards as separate statements.
INSERT INTO `oakved_inquiry_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `oakved_inquiry_allowed_crm_scope` AS allowed
  LEFT JOIN `oakved_inquiry_full_crm_scope` AS full_scope
    ON full_scope.`menu_id` = allowed.`menu_id`
  WHERE full_scope.`menu_id` IS NULL
);

DROP TEMPORARY TABLE IF EXISTS `oakved_inquiry_package_menu`;
CREATE TEMPORARY TABLE `oakved_inquiry_package_menu` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT IGNORE INTO `oakved_inquiry_package_menu` (`menu_id`)
SELECT package_menu.`menu_id`
FROM `system_tenant_package` AS package
INNER JOIN JSON_TABLE(
  package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
) AS package_menu
LEFT JOIN `oakved_inquiry_full_crm_scope` AS crm_scope
  ON crm_scope.`menu_id` = package_menu.`menu_id`
WHERE package.`id` = @oakved_inquiry_package_id
  AND crm_scope.`menu_id` IS NULL;

INSERT IGNORE INTO `oakved_inquiry_package_menu` (`menu_id`)
SELECT `menu_id` FROM `oakved_inquiry_allowed_crm_scope`;

UPDATE `system_tenant_package`
SET `name` = 'Oakved 询盘中心',
    `remark` = CONCAT('oakved-inquiry-crm-v032:tenant-', @oakved_inquiry_target_tenant_id),
    `menu_ids` = (
      SELECT JSON_ARRAYAGG(ordered_menu.`menu_id`)
      FROM (
        SELECT `menu_id`
        FROM `oakved_inquiry_package_menu`
        ORDER BY `menu_id`
      ) AS ordered_menu
    ),
    `updater` = 'V032',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_inquiry_package_id
  AND `deleted` = b'0';

DELETE role_menu
FROM `system_role_menu` AS role_menu
INNER JOIN `oakved_inquiry_full_crm_scope` AS crm_scope
  ON crm_scope.`menu_id` = role_menu.`menu_id`
LEFT JOIN `oakved_inquiry_allowed_crm_scope` AS allowed
  ON allowed.`menu_id` = role_menu.`menu_id`
WHERE role_menu.`tenant_id` = @oakved_inquiry_target_tenant_id
  AND allowed.`menu_id` IS NULL;

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`,
 `deleted`, `tenant_id`)
SELECT role.`id`, allowed.`menu_id`, 'V032', CURRENT_TIMESTAMP,
       'V032', CURRENT_TIMESTAMP, b'0', @oakved_inquiry_target_tenant_id
FROM `system_role` AS role
CROSS JOIN `oakved_inquiry_allowed_crm_scope` AS allowed
WHERE role.`tenant_id` = @oakved_inquiry_target_tenant_id
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` AS role_menu
    WHERE role_menu.`role_id` = role.`id`
      AND role_menu.`menu_id` = allowed.`menu_id`
      AND role_menu.`tenant_id` = @oakved_inquiry_target_tenant_id
      AND role_menu.`deleted` = b'0'
  );

INSERT INTO `oakved_inquiry_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `system_tenant_package` AS package
  INNER JOIN JSON_TABLE(
    package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
  ) AS package_menu
  INNER JOIN `oakved_inquiry_full_crm_scope` AS full_scope
    ON full_scope.`menu_id` = package_menu.`menu_id`
  LEFT JOIN `oakved_inquiry_allowed_crm_scope` AS allowed
    ON allowed.`menu_id` = package_menu.`menu_id`
  WHERE package.`id` = @oakved_inquiry_package_id
    AND allowed.`menu_id` IS NULL
);

INSERT INTO `oakved_inquiry_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `system_role` AS role
  CROSS JOIN `oakved_inquiry_allowed_crm_scope` AS allowed
  WHERE role.`tenant_id` = @oakved_inquiry_target_tenant_id
    AND role.`code` = 'tenant_admin'
    AND role.`type` = 1
    AND role.`status` = 0
    AND role.`deleted` = b'0'
    AND NOT EXISTS (
      SELECT 1
      FROM `system_role_menu` AS role_menu
      WHERE role_menu.`role_id` = role.`id`
        AND role_menu.`menu_id` = allowed.`menu_id`
        AND role_menu.`tenant_id` = @oakved_inquiry_target_tenant_id
        AND role_menu.`deleted` = b'0'
    )
);

DROP TEMPORARY TABLE `oakved_inquiry_package_menu`;
DROP TEMPORARY TABLE `oakved_inquiry_allowed_crm_scope`;
DROP TEMPORARY TABLE `oakved_inquiry_full_crm_scope`;
DROP TEMPORARY TABLE `oakved_inquiry_guard`;
