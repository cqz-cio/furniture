-- Schema only. Tenant, accounts, menus and grants are provisioned separately.
CREATE TABLE IF NOT EXISTS website_operation_record (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 site_id bigint NOT NULL, kind varchar(20) NOT NULL,
 target_kind varchar(20) NOT NULL DEFAULT '', target_key varchar(100) NOT NULL DEFAULT '',
 content_version int NOT NULL DEFAULT 0, status varchar(20) NOT NULL DEFAULT '',
 version int NOT NULL, payload_json mediumtext NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 KEY idx_website_operation_scope(tenant_id,site_id,kind,deleted,id),
 KEY idx_website_review_version(tenant_id,site_id,target_kind,target_key,content_version,status,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS website_document (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, site_id bigint NOT NULL,
 version int NOT NULL, draft_json text NOT NULL, published_json text DEFAULT NULL,
 creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 KEY idx_website_document_scope(tenant_id,site_id,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PDF content stays private. Only the checked document download controller can return bytes.
CREATE TABLE IF NOT EXISTS website_private_file (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, site_id bigint NOT NULL,
 name varchar(160) NOT NULL, size bigint NOT NULL, sha256 char(64) NOT NULL, content mediumblob NOT NULL,
 creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 UNIQUE KEY uk_website_private_hash(tenant_id,site_id,sha256,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
