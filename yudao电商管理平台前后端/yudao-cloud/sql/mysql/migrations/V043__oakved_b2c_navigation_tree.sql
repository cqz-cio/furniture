-- Extend the shared website-navigation engine for the Oakved B2C header.
-- The selected renderer remains a site-level setting; business_mode alone is intentionally not used.

ALTER TABLE `seo_site_config`
  ADD COLUMN `navigation_template` varchar(32) NOT NULL DEFAULT 'VANZ_B2B'
    COMMENT '导航模板：VANZ_B2B/OAKVED_B2C'
    AFTER `default_locale`;

ALTER TABLE `website_navigation_item`
  ADD COLUMN `target_key` varchar(64) DEFAULT NULL
    COMMENT '服务端安全路由/筛选目标标识'
    AFTER `page_key`,
  ADD COLUMN `style_variant` varchar(32) NOT NULL DEFAULT 'DEFAULT'
    COMMENT '导航视觉样式：DEFAULT/SALE'
    AFTER `open_mode`;

-- Bootstrap the existing Oakved site without coupling the application frontend to a numeric tenant id.
UPDATE `seo_site_config` config
JOIN `system_tenant` tenant
  ON tenant.`id` = config.`tenant_id` AND tenant.`deleted` = b'0'
SET config.`navigation_template` = 'OAKVED_B2C'
WHERE LOWER(COALESCE(tenant.`code`, '')) = 'oakved'
   OR LOWER(config.`site_name`) LIKE '%oakved%'
   OR LOWER(config.`site_url`) LIKE '%oakved%';
