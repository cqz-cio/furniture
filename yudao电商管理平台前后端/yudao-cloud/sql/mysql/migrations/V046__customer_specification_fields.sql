-- Extend the ERP-managed public specification contract with the customer
-- sheet fields that are not represented by the existing four-field summary.
-- Item Name remains the SPU name; these values live in product_spu.detail_config.

ALTER TABLE `system_tenant`
  MODIFY COLUMN `website_product_fields` varchar(1024) NOT NULL
    DEFAULT 'category,badges,introduction,skuCode,collection,heroNote,fabricSelector,optionGroups,highlights,description,itemNo,material,color,finish,dimension,service,sample,packing,accordions,skuProperties,relatedProducts,relatedLinks'
    COMMENT '网站公开商品字段，逗号分隔';

SET @default_b2b_product_fields :=
  'category,badges,introduction,skuCode,collection,heroNote,fabricSelector,optionGroups,highlights,description,itemNo,material,color,finish,dimension,service,sample,packing,accordions,skuProperties,relatedProducts,relatedLinks';

UPDATE `system_tenant`
SET `website_product_fields` = CASE
      WHEN TRIM(COALESCE(`website_product_fields`, '')) = ''
        THEN @default_b2b_product_fields
      ELSE CONCAT(
        `website_product_fields`,
        IF(FIND_IN_SET('itemNo', `website_product_fields`) = 0, ',itemNo', ''),
        IF(FIND_IN_SET('material', `website_product_fields`) = 0, ',material', ''),
        IF(FIND_IN_SET('color', `website_product_fields`) = 0, ',color', ''),
        IF(FIND_IN_SET('finish', `website_product_fields`) = 0, ',finish', ''),
        IF(FIND_IN_SET('dimension', `website_product_fields`) = 0, ',dimension', ''),
        IF(FIND_IN_SET('service', `website_product_fields`) = 0, ',service', ''),
        IF(FIND_IN_SET('sample', `website_product_fields`) = 0, ',sample', ''),
        IF(FIND_IN_SET('packing', `website_product_fields`) = 0, ',packing', '')
      )
    END,
    `updater` = 'V046',
    `update_time` = CURRENT_TIMESTAMP
WHERE `business_mode` = 'B2B'
  AND `deleted` = b'0';

-- Backfill the customer-provided specification sheet for the existing VANZ chair.
-- Do not map the customer Item No. to the ERP-generated SKU and do not treat Color
-- as Finish. The source only says 2 Pcs/Ctn, so no assembly/packing method is invented.
SET @vanz_chair_tenant_id := 162;
SET @vanz_chair_name := _utf8mb4'Nailed Upholstered Dining Chair'
  COLLATE utf8mb4_unicode_ci;

UPDATE `product_spu`
SET `detail_config` = JSON_SET(
      COALESCE(`detail_config`, JSON_OBJECT()),
      '$.itemNo', 'VZC0099',
      '$.material', 'Oak / Fabric',
      '$.color', 'As shown or according to the customer''s request',
      '$.finish', '',
      '$.dimension', JSON_OBJECT(
        'shape', 'rectangular',
        'width', 52,
        'depth', 65,
        'diameter', NULL,
        'height', 100,
        'unit', 'cm'
      ),
      '$.service', 'OEM & ODM',
      '$.sample', 'Available',
      '$.packing', JSON_OBJECT(
        'method', '',
        'itemQuantity', 2,
        'itemUnit', 'pc',
        'cartonQuantity', 1
      ),
      '$.accordions', JSON_ARRAY(
        JSON_OBJECT(
          'title', 'DETAILS',
          'rows', JSON_ARRAY(
            JSON_ARRAY('Feature', 'Oak wood/Fabric'),
            JSON_ARRAY('Application', 'Living Room, Bedroom, Hotel, Apartment'),
            JSON_ARRAY('Design Style', 'Vintage')
          )
        )
      ),
      '$.productType', 'chair',
      '$.collection', '',
      '$.heroNote', '',
      '$.fabricSelector', JSON_OBJECT(
        'stockedCount', 0,
        'specialOrderCount', 0,
        'label', '',
        'swatches', JSON_ARRAY()
      ),
      '$.highlights', JSON_ARRAY(),
      '$.optionGroups', JSON_ARRAY(),
      '$.relatedLinks', JSON_ARRAY()
    ),
    `updater` = 'V046',
    `update_time` = CURRENT_TIMESTAMP
WHERE `tenant_id` = @vanz_chair_tenant_id
  AND `name` = @vanz_chair_name
  AND `deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `customer_specification_fields_guard`;
CREATE TEMPORARY TABLE `customer_specification_fields_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_customer_specification_fields_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `customer_specification_fields_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `system_tenant`
  WHERE `business_mode` = 'B2B'
    AND `deleted` = b'0'
    AND (
      FIND_IN_SET('itemNo', `website_product_fields`) = 0
      OR FIND_IN_SET('material', `website_product_fields`) = 0
      OR FIND_IN_SET('color', `website_product_fields`) = 0
      OR FIND_IN_SET('finish', `website_product_fields`) = 0
      OR FIND_IN_SET('dimension', `website_product_fields`) = 0
      OR FIND_IN_SET('service', `website_product_fields`) = 0
      OR FIND_IN_SET('sample', `website_product_fields`) = 0
      OR FIND_IN_SET('packing', `website_product_fields`) = 0
    )
);

INSERT INTO `customer_specification_fields_guard` (`valid`)
SELECT 0
WHERE (
  SELECT COUNT(*)
  FROM `product_spu`
  WHERE `tenant_id` = @vanz_chair_tenant_id
    AND `name` = @vanz_chair_name
    AND `deleted` = b'0'
) > 1;

INSERT INTO `customer_specification_fields_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `product_spu`
  WHERE `tenant_id` = @vanz_chair_tenant_id
    AND `name` = @vanz_chair_name
    AND `deleted` = b'0'
    AND (
      NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.itemNo')) <=> 'VZC0099')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.material')) <=> 'Oak / Fabric')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.color'))
        <=> 'As shown or according to the customer''s request')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.finish')) <=> '')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.service')) <=> 'OEM & ODM')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.sample')) <=> 'Available')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.dimension.unit')) <=> 'cm')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.dimension.width')) <=> '52')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.dimension.depth')) <=> '65')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.dimension.height')) <=> '100')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemQuantity')) <=> '2')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.itemUnit')) <=> 'pc')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.packing.cartonQuantity')) <=> '1')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].title')) <=> 'DETAILS')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows[0][0]')) <=> 'Feature')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows[0][1]')) <=> 'Oak wood/Fabric')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows[1][0]')) <=> 'Application')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows[1][1]'))
        <=> 'Living Room, Bedroom, Hotel, Apartment')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows[2][0]')) <=> 'Design Style')
      OR NOT (JSON_UNQUOTE(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows[2][1]')) <=> 'Vintage')
      OR JSON_LENGTH(JSON_EXTRACT(`detail_config`, '$.accordions')) <> 1
      OR JSON_LENGTH(JSON_EXTRACT(`detail_config`, '$.accordions[0].rows')) <> 3
    )
);

DROP TEMPORARY TABLE `customer_specification_fields_guard`;
