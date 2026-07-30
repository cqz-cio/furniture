-- Allow soft-deleted SEO records to retain history while enforcing one active business key.

ALTER TABLE `seo_site_config`
  DROP INDEX `uk_tenant_site_deleted`,
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY `uk_tenant_site_active` (`tenant_id`, `site_id`, `active_record`);

ALTER TABLE `seo_metadata`
  DROP INDEX `uk_entity_locale_deleted`,
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY `uk_entity_locale_active` (`tenant_id`, `site_id`, `entity_type`, `entity_id`, `locale`, `active_record`);
