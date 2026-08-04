-- Add an explicit business/test boundary and the minimum B2B sales-operation
-- fields required by the VANZ inquiry center. Existing test fixtures are
-- backfilled only for tenant 162; all other tenants keep business-data defaults.

ALTER TABLE `crm_clue`
  ADD COLUMN `test_data` bit(1) NOT NULL DEFAULT b'0'
    COMMENT '是否测试、QA 或 E2E 数据' AFTER `processed_at`,
  ADD COLUMN `priority` int NOT NULL DEFAULT 20
    COMMENT '询盘优先级：10 高，20 普通，30 低' AFTER `test_data`,
  ADD COLUMN `sales_stage` int NOT NULL DEFAULT 0
    COMMENT '销售阶段：0 新询盘，10 需求确认，20 报价，30 打样，40 谈判，50 赢单，60 丢单' AFTER `priority`,
  ADD COLUMN `first_response_at` datetime DEFAULT NULL
    COMMENT '首次开始处理时间' AFTER `sales_stage`,
  ADD KEY `idx_crm_clue_tenant_business`
    (`tenant_id`, `test_data`, `process_status`, `sales_stage`, `submitted_at`, `deleted`);

UPDATE `crm_clue`
SET `test_data` = b'1',
    `updater` = 'V041',
    `update_time` = CURRENT_TIMESTAMP
WHERE `tenant_id` = 162
  AND `external_inquiry_id` IS NOT NULL
  AND `deleted` = b'0'
  AND (
    UPPER(TRIM(`company_name`)) REGEXP '^(TEST|QA|E2E)([[:space:]_:-]|$)'
    OR UPPER(TRIM(`inquiry_subject`)) REGEXP '^(\\[?TEST\\]?|QA|E2E)([[:space:]_:-]|$)'
    OR LOWER(TRIM(`email`)) REGEXP '@example\\.(com|net|org)$'
    OR LOWER(TRIM(`email`)) REGEXP '\\.test$'
  );

UPDATE `crm_clue`
SET `sales_stage` = CASE
      WHEN `process_status` = 30 THEN 60
      WHEN `transform_status` = b'1' THEN 50
      WHEN `process_status` IN (10, 20) THEN 10
      ELSE 0
    END,
    `first_response_at` = CASE
      WHEN `process_status` IN (10, 20, 30)
        THEN COALESCE(`contact_last_time`, `processed_at`, `update_time`)
      ELSE NULL
    END,
    `updater` = 'V041',
    `update_time` = CURRENT_TIMESTAMP
WHERE `tenant_id` = 162
  AND `external_inquiry_id` IS NOT NULL
  AND `deleted` = b'0';

-- Full ERP synchronization is materially broader than editing one product.
-- Give it an explicit permission and grant it only to the VANZ operator role.
SET @vanz_spu_menu_id = (
  SELECT MIN(`parent_id`)
  FROM `system_menu`
  WHERE `permission` = 'product:spu:query'
    AND `type` = 3
    AND `deleted` = b'0'
);

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 'ERP 全量同步','product:spu:sync-all',3,8,@vanz_spu_menu_id,'','','','',
       0,b'1',b'1',b'1','V041',CURRENT_TIMESTAMP,'V041',CURRENT_TIMESTAMP,b'0'
WHERE @vanz_spu_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'product:spu:sync-all'
      AND `deleted` = b'0'
  );

SET @vanz_spu_sync_all_menu_id = (
  SELECT MIN(`id`)
  FROM `system_menu`
  WHERE `permission` = 'product:spu:sync-all'
    AND `type` = 3
    AND `deleted` = b'0'
);
SET @vanz_operator_role_id = (
  SELECT MIN(`id`)
  FROM `system_role`
  WHERE `tenant_id` = 162
    AND `code` = 'mall_operator'
    AND `type` = 1
    AND `status` = 0
    AND `deleted` = b'0'
);

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT @vanz_operator_role_id,@vanz_spu_sync_all_menu_id,
       'V041',CURRENT_TIMESTAMP,'V041',CURRENT_TIMESTAMP,b'0',162
WHERE @vanz_operator_role_id IS NOT NULL
  AND @vanz_spu_sync_all_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu`
    WHERE `role_id` = @vanz_operator_role_id
      AND `menu_id` = @vanz_spu_sync_all_menu_id
      AND `tenant_id` = 162
      AND `deleted` = b'0'
  );

DROP TEMPORARY TABLE IF EXISTS `vanz_inquiry_operations_guard`;
CREATE TEMPORARY TABLE `vanz_inquiry_operations_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_vanz_inquiry_operations_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `vanz_inquiry_operations_guard` (`valid`)
SELECT 0
WHERE (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'crm_clue'
    AND `column_name` IN ('test_data', 'priority', 'sales_stage', 'first_response_at')
) <> 4
   OR @vanz_spu_menu_id IS NULL
   OR @vanz_spu_sync_all_menu_id IS NULL
   OR @vanz_operator_role_id IS NULL
   OR NOT EXISTS (
     SELECT 1
     FROM `system_role_menu`
     WHERE `role_id` = @vanz_operator_role_id
       AND `menu_id` = @vanz_spu_sync_all_menu_id
       AND `tenant_id` = 162
       AND `deleted` = b'0'
   );

DROP TEMPORARY TABLE `vanz_inquiry_operations_guard`;
