-- Align the furniture B2B website product fields with an ERP-managed tenant policy.
-- Product name, images and route IDs remain protocol fields; this list controls optional public content.

ALTER TABLE `system_tenant`
  ADD COLUMN `website_product_fields` varchar(1024) NOT NULL
    DEFAULT 'category,badges,introduction,skuCode,collection,heroNote,fabricSelector,optionGroups,highlights,description,accordions,skuProperties,relatedProducts,relatedLinks'
    COMMENT '网站公开商品字段，逗号分隔'
    AFTER `business_mode`;
