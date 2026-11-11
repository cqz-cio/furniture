-- Apply only during an explicitly authorized ERP deployment. Local fixture uses its own H2 schema.
-- Draft edits must not change public visibility, schedule or ordering before publication.
ALTER TABLE website_blog_article
    ADD COLUMN published_visible bit(1) NULL COMMENT 'Visibility captured at publication',
    ADD COLUMN published_at_snapshot datetime NULL COMMENT 'Publication schedule captured in snapshot',
    ADD COLUMN published_sort_order int NULL COMMENT 'Sort order captured at publication';

UPDATE website_blog_article
SET published_visible = visible,
    published_at_snapshot = COALESCE(
        STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(published_payload_json, '$.publishedAt')), '%Y-%m-%dT%H:%i:%s'),
        published_at),
    published_sort_order = COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(published_payload_json, '$.sortOrder')) AS SIGNED), sort_order),
    -- Backfill metadata without making every existing article look newly edited.
    update_time = update_time
WHERE published_payload_json IS NOT NULL;

CREATE INDEX idx_blog_published_snapshot ON website_blog_article
    (tenant_id, site_id, locale, status, published_visible, published_at_snapshot);
