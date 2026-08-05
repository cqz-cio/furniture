-- Add the four stable public product-information fields to the B2B tenant
-- allowlist. The value is a comma-separated varchar mapped to List<String>.

ALTER TABLE `system_tenant`
  MODIFY COLUMN `website_product_fields` varchar(1024) NOT NULL
    DEFAULT 'category,badges,introduction,skuCode,collection,heroNote,fabricSelector,optionGroups,highlights,description,material,finish,dimension,packing,accordions,skuProperties,relatedProducts,relatedLinks'
    COMMENT '网站公开商品字段，逗号分隔';

SET @default_b2b_product_fields :=
  'category,badges,introduction,skuCode,collection,heroNote,fabricSelector,optionGroups,highlights,description,material,finish,dimension,packing,accordions,skuProperties,relatedProducts,relatedLinks';

UPDATE `system_tenant`
SET `website_product_fields` = CASE
      WHEN TRIM(COALESCE(`website_product_fields`, '')) = ''
        THEN @default_b2b_product_fields
      ELSE CONCAT(
        `website_product_fields`,
        IF(FIND_IN_SET('material', `website_product_fields`) = 0, ',material', ''),
        IF(FIND_IN_SET('finish', `website_product_fields`) = 0, ',finish', ''),
        IF(FIND_IN_SET('dimension', `website_product_fields`) = 0, ',dimension', ''),
        IF(FIND_IN_SET('packing', `website_product_fields`) = 0, ',packing', '')
      )
    END,
    `updater` = 'V042',
    `update_time` = CURRENT_TIMESTAMP
WHERE `business_mode` = 'B2B'
  AND `deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `product_information_fields_guard`;
CREATE TEMPORARY TABLE `product_information_fields_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_product_information_fields_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `product_information_fields_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `system_tenant`
  WHERE `business_mode` = 'B2B'
    AND `deleted` = b'0'
    AND (
      FIND_IN_SET('material', `website_product_fields`) = 0
      OR FIND_IN_SET('finish', `website_product_fields`) = 0
      OR FIND_IN_SET('dimension', `website_product_fields`) = 0
      OR FIND_IN_SET('packing', `website_product_fields`) = 0
    )
);

DROP TEMPORARY TABLE `product_information_fields_guard`;
