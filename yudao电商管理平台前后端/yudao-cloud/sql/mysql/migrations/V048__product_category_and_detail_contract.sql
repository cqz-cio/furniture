-- Phase 1: make product category codes and detail_config the shared ERP/admin/storefront contract.
-- The read-only Phase 0 audit is a required precondition. This migration never infers a
-- product type from product_spu.name: unresolved records keep their current category and
-- remain visible to the lifecycle audit for manual classification.

ALTER TABLE `product_category`
  ADD COLUMN `code` varchar(64) NULL AFTER `parent_id`;

-- Every historical row receives a deterministic, valid code before the NOT NULL and
-- active-record uniqueness constraints are enabled. Canonical records are inserted below.
UPDATE `product_category`
SET `code` = CONCAT('legacy-', `id`),
    `updater` = 'V048',
    `update_time` = CURRENT_TIMESTAMP
WHERE `code` IS NULL OR TRIM(`code`) = '';

ALTER TABLE `product_category`
  MODIFY COLUMN `code` varchar(64) NOT NULL COMMENT '稳定分类编码，显示名称修改时保持不变',
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  ADD CONSTRAINT `chk_product_category_code_format`
      CHECK (`code` REGEXP '^[a-z0-9]+(-[a-z0-9]+)*$'),
  ADD UNIQUE KEY `uk_product_category_tenant_parent_code_active`
      (`tenant_id`, `parent_id`, `code`, `active_record`);

-- Canonical P1 rooms. Codes, not display names, are the durable identity.
INSERT INTO `product_category`
  (`parent_id`, `code`, `name`, `pic_url`, `big_pic_url`, `sort`, `status`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 0, room_catalog.`code`, room_catalog.`name`, '', NULL, room_catalog.`sort`, 0,
       'V048', CURRENT_TIMESTAMP, 'V048', CURRENT_TIMESTAMP, b'0', tenant.`id`
FROM `system_tenant` AS tenant
JOIN (
  SELECT 'dining-room' AS `code`, 'Dining Room Furniture' AS `name`, 10 AS `sort`
  UNION ALL SELECT 'living-room', 'Living Room Furniture', 20
  UNION ALL SELECT 'bedroom', 'Bedroom Furniture', 30
) AS room_catalog
-- Oakved tenant 121 is the storefront default even though it retains B2C pricing mode.
-- B2B tenants use the same taxonomy for the admin Room/Product-type selectors.
WHERE (tenant.`business_mode` = 'B2B' OR tenant.`code` = 'OAKVED')
  AND tenant.`deleted` = b'0'
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `sort` = VALUES(`sort`),
  `status` = 0,
  `updater` = 'V048',
  `update_time` = CURRENT_TIMESTAMP;

-- Canonical P2 matrix. Correct spellings are intentionally asserted by the guard below.
INSERT INTO `product_category`
  (`parent_id`, `code`, `name`, `pic_url`, `big_pic_url`, `sort`, `status`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT room.`id`, product_type.`code`, product_type.`name`, '', NULL,
       product_type.`sort`, 0, 'V048', CURRENT_TIMESTAMP, 'V048', CURRENT_TIMESTAMP,
       b'0', room.`tenant_id`
FROM `product_category` AS room
JOIN (
  SELECT 'dining-room' AS `room_code`, 'dining-chair' AS `code`,
         'DINING CHAIRS' AS `name`, 10 AS `sort`
  UNION ALL SELECT 'dining-room', 'bar-stool', 'BAR STOOLS', 20
  UNION ALL SELECT 'dining-room', 'dining-table', 'DINING TABLES', 30
  UNION ALL SELECT 'living-room', 'sofa', 'SOFA & OCCASIONAL CHAIR', 10
  UNION ALL SELECT 'living-room', 'coffee-table', 'SIDE TABLE & COFFEE TABLE', 20
  UNION ALL SELECT 'living-room', 'bookcase', 'BOOKCASE & DISPLAY CABINET', 30
  UNION ALL SELECT 'living-room', 'media-console', 'CONSOLE TABLE & BUFFET', 40
  UNION ALL SELECT 'bedroom', 'bed', 'BED & HEADBOARD', 10
  UNION ALL SELECT 'bedroom', 'nightstand', 'BEDSIDE TABLE', 20
  UNION ALL SELECT 'bedroom', 'dresser', 'CHEST OF DRAWERS', 30
  UNION ALL SELECT 'bedroom', 'bench', 'BENCH', 40
  UNION ALL SELECT 'bedroom', 'dressing-table', 'DRESSING TABLE', 50
  UNION ALL SELECT 'bedroom', 'wardrobe', 'WARDROBE', 60
) AS product_type ON product_type.`room_code` = room.`code`
WHERE room.`parent_id` = 0
  AND room.`deleted` = b'0'
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `sort` = VALUES(`sort`),
  `status` = 0,
  `updater` = 'V048',
  `update_time` = CURRENT_TIMESTAMP;

-- Build a deterministic SPU mapping from the old explicit JSON value or the old
-- category itself. Ambiguous values are only mapped when their current P1 room resolves
-- the ambiguity. No product title, keyword or description participates in this mapping.
DROP TEMPORARY TABLE IF EXISTS `phase1_product_type_map`;
CREATE TEMPORARY TABLE `phase1_product_type_map` (
  `tenant_id` bigint NOT NULL,
  `spu_id` bigint NOT NULL,
  `target_code` varchar(64) NULL,
  PRIMARY KEY (`tenant_id`, `spu_id`)
) ENGINE=InnoDB;

INSERT INTO `phase1_product_type_map` (`tenant_id`, `spu_id`, `target_code`)
SELECT product.`tenant_id`, product.`id`,
       CASE
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType'))))
              IN ('dining-chair', 'bar-stool', 'dining-table', 'sofa', 'coffee-table',
                  'bookcase', 'media-console', 'bed', 'nightstand', 'dresser', 'bench',
                  'dressing-table', 'wardrobe')
           THEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType'))))
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'bed-bench'
           THEN 'bench'
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'vanity'
           THEN 'dressing-table'
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'round-table'
           THEN 'dining-table'
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'single-sofa'
           THEN 'sofa'
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'chair'
              AND parent_category.`code` = 'dining-room'
           THEN 'dining-chair'
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'chair'
              AND parent_category.`code` = 'living-room'
           THEN 'sofa'
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType')))) = 'sideboard'
              AND parent_category.`code` = 'living-room'
           THEN 'media-console'
         -- Ambiguous legacy values must never fall through to old display-name aliases.
         -- Without a canonical P1 room they remain on the manual-review list.
         WHEN LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.`detail_config`, '$.productType'))))
              IN ('chair', 'sideboard')
           THEN NULL
         WHEN LOWER(TRIM(current_category.`name`)) = 'sofas' THEN 'sofa'
         WHEN LOWER(TRIM(current_category.`name`)) = 'dining tables' THEN 'dining-table'
         WHEN LOWER(TRIM(current_category.`name`)) = 'dining chairs' THEN 'dining-chair'
         WHEN LOWER(TRIM(current_category.`name`)) = 'coffee tables' THEN 'coffee-table'
         WHEN LOWER(TRIM(current_category.`name`)) = 'beds' THEN 'bed'
         WHEN LOWER(TRIM(current_category.`name`)) = 'wardrobes' THEN 'wardrobe'
         WHEN LOWER(TRIM(current_category.`name`)) IN ('media storage', 'media consoles')
           THEN 'media-console'
         ELSE NULL
       END
FROM `product_spu` AS product
JOIN `product_category` AS current_category
  ON current_category.`id` = product.`category_id`
 AND current_category.`tenant_id` = product.`tenant_id`
 AND current_category.`deleted` = b'0'
LEFT JOIN `product_category` AS parent_category
  ON parent_category.`id` = current_category.`parent_id`
 AND parent_category.`tenant_id` = current_category.`tenant_id`
 AND parent_category.`deleted` = b'0'
WHERE product.`deleted` = b'0';

UPDATE `product_spu` AS product
JOIN `phase1_product_type_map` AS mapping
  ON mapping.`tenant_id` = product.`tenant_id`
 AND mapping.`spu_id` = product.`id`
JOIN `product_category` AS target_category
  ON target_category.`tenant_id` = product.`tenant_id`
 AND target_category.`code` = mapping.`target_code`
 AND target_category.`deleted` = b'0'
JOIN `product_category` AS target_room
  ON target_room.`id` = target_category.`parent_id`
 AND target_room.`tenant_id` = target_category.`tenant_id`
 AND target_room.`parent_id` = 0
 AND target_room.`code` IN ('dining-room', 'living-room', 'bedroom')
 AND target_room.`deleted` = b'0'
SET product.`category_id` = target_category.`id`,
    product.`updater` = 'V048',
    product.`update_time` = CURRENT_TIMESTAMP
WHERE mapping.`target_code` IS NOT NULL
  AND product.`category_id` <> target_category.`id`;

DROP TEMPORARY TABLE `phase1_product_type_map`;

-- Canonical Packing is a free-text string. A non-blank packingDisplay wins; otherwise
-- retain an existing string or format the historical object with the established rules.
UPDATE `product_spu`
SET `detail_config` = JSON_REMOVE(
      JSON_SET(
        COALESCE(`detail_config`, JSON_OBJECT()),
        '$.packing',
        CASE
          WHEN NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packingDisplay'))), '')
               IS NOT NULL
            THEN TRIM(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packingDisplay')))
          WHEN JSON_TYPE(JSON_EXTRACT(`detail_config`, '$.packing')) = 'STRING'
            THEN TRIM(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing')))
          WHEN JSON_TYPE(JSON_EXTRACT(`detail_config`, '$.packing')) = 'OBJECT'
            THEN CASE
              WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED) > 0
                   AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.cartonQuantity')) AS UNSIGNED) > 0
                THEN CASE
                  WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemUnit')), 'pc') = 'pc'
                       AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED) = 1
                       AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.cartonQuantity')) AS UNSIGNED) > 1
                    THEN CONCAT('Ships in ',
                      CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.cartonQuantity')) AS UNSIGNED),
                      ' cartons')
                  WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.cartonQuantity')) AS UNSIGNED) = 1
                    THEN CONCAT(
                      CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED),
                      ' ',
                      CASE
                        WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemUnit')), 'pc') = 'set'
                          THEN IF(CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED) = 1, 'set', 'sets')
                        ELSE IF(CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED) = 1, 'pc', 'pcs')
                      END,
                      '/ctn')
                  ELSE CONCAT(
                    CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED),
                    ' ',
                    CASE
                      WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemUnit')), 'pc') = 'set'
                        THEN IF(CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED) = 1, 'set', 'sets')
                      ELSE IF(CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) AS UNSIGNED) = 1, 'pc', 'pcs')
                    END,
                    ' / ',
                    CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.cartonQuantity')) AS UNSIGNED),
                    ' cartons')
                END
              ELSE COALESCE(TRIM(JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.method'))), '')
            END
          ELSE ''
        END
      ),
      '$.packingDisplay'
    ),
    `updater` = 'V048',
    `update_time` = CURRENT_TIMESTAMP
WHERE JSON_CONTAINS_PATH(`detail_config`, 'one', '$.packingDisplay') = 1
   OR JSON_TYPE(JSON_EXTRACT(`detail_config`, '$.packing')) = 'OBJECT';

-- Migration guards: fail forward if the stable code matrix or packing contract is incomplete.
DROP TEMPORARY TABLE IF EXISTS `product_phase1_contract_guard`;
CREATE TEMPORARY TABLE `product_phase1_contract_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_product_phase1_contract_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `product_phase1_contract_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `product_category`
  WHERE `deleted` = b'0'
    AND (`code` = '' OR `code` NOT REGEXP '^[a-z0-9]+(-[a-z0-9]+)*$')
);

INSERT INTO `product_phase1_contract_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT tenant.`id`
  FROM `system_tenant` AS tenant
  LEFT JOIN `product_category` AS room
    ON room.`tenant_id` = tenant.`id`
   AND room.`parent_id` = 0
   AND room.`code` IN ('dining-room', 'living-room', 'bedroom')
   AND room.`deleted` = b'0'
  LEFT JOIN `product_category` AS product_type
    ON product_type.`tenant_id` = tenant.`id`
   AND product_type.`parent_id` = room.`id`
   AND product_type.`deleted` = b'0'
  WHERE (tenant.`business_mode` = 'B2B' OR tenant.`code` = 'OAKVED')
    AND tenant.`deleted` = b'0'
  GROUP BY tenant.`id`
  HAVING COUNT(DISTINCT room.`code`) <> 3
      OR COUNT(DISTINCT product_type.`code`) <> 13
);

INSERT INTO `product_phase1_contract_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `product_spu`
  WHERE `deleted` = b'0'
    AND (
      JSON_CONTAINS_PATH(`detail_config`, 'one', '$.packingDisplay') = 1
      OR JSON_TYPE(JSON_EXTRACT(`detail_config`, '$.packing')) = 'OBJECT'
    )
);

DROP TEMPORARY TABLE `product_phase1_contract_guard`;
