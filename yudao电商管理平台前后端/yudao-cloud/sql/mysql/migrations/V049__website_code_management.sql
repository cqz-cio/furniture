-- Website code is tenant/site scoped, with separate draft and published snapshots.
CREATE TABLE IF NOT EXISTS website_code_config (
  id bigint NOT NULL AUTO_INCREMENT,
  site_id bigint NOT NULL,
  version int NOT NULL DEFAULT 0,
  draft_json mediumtext NOT NULL,
  published_json mediumtext DEFAULT NULL,
  published_version int DEFAULT NULL,
  published_time datetime DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,

  active_record tinyint GENERATED ALWAYS AS (CASE WHEN deleted = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_website_code_site (tenant_id, site_id, active_record)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS website_code_history (
  id bigint NOT NULL AUTO_INCREMENT,
  site_id bigint NOT NULL,
  version int NOT NULL,
  action varchar(16) NOT NULL,
  snapshot_json mediumtext NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uk_website_code_version (tenant_id, site_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Keep existing URLs and grants; only organize the CMS labels and order.
SET @cms_root = (SELECT MIN(id) FROM system_menu WHERE path = '/seo' AND type = 1 AND deleted = b'0');
UPDATE system_menu SET name = '网站管理（CMS）', updater = 'V049', update_time = CURRENT_TIMESTAMP
WHERE id = @cms_root;
UPDATE system_menu SET name = CASE path WHEN 'blog' THEN '文章管理' WHEN 'metadata' THEN 'SEO 管理' ELSE name END,
  sort = CASE path WHEN 'blog' THEN 1 WHEN 'navigation' THEN 2 WHEN 'metadata' THEN 3
                  WHEN 'analysis' THEN 4 WHEN 'site-config' THEN 6 ELSE sort END,
  updater = 'V049', update_time = CURRENT_TIMESTAMP
WHERE parent_id = @cms_root AND deleted = b'0';

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '统计与广告代码','',2,5,@cms_root,'website-code','ep:coin','seo/website-code/index','SeoWebsiteCode',
  0,b'1',b'0',b'1','V049','V049',b'0'
WHERE @cms_root IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_menu WHERE parent_id = @cms_root AND path = 'website-code' AND deleted = b'0');
SET @cms_code_menu = (SELECT MIN(id) FROM system_menu WHERE parent_id = @cms_root AND path = 'website-code' AND deleted = b'0');

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT scope.name, scope.permission, 3, scope.sort, @cms_code_menu, '', '', '', '',
  0,b'1',b'0',b'1','V049','V049',b'0'
FROM (
  SELECT '代码查询' AS name, 'seo:website-code:query' AS permission, 1 AS sort
  UNION ALL SELECT '代码保存与恢复','seo:website-code:update',2
  UNION ALL SELECT '代码发布','seo:website-code:publish',3
) AS scope
WHERE @cms_code_menu IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_menu WHERE permission = scope.permission AND deleted = b'0');

CREATE TEMPORARY TABLE cms_code_menu_scope (menu_id bigint PRIMARY KEY);
INSERT INTO cms_code_menu_scope SELECT id FROM system_menu
WHERE (id = @cms_code_menu OR permission IN ('seo:website-code:query','seo:website-code:update','seo:website-code:publish'))
  AND deleted = b'0';

CREATE TEMPORARY TABLE cms_code_packages (package_id bigint PRIMARY KEY, menu_ids varchar(8192));
INSERT INTO cms_code_packages
SELECT package.id, CAST(JSON_MERGE_PRESERVE(CAST(package.menu_ids AS JSON), JSON_ARRAYAGG(scope.menu_id))
 AS CHAR CHARACTER SET utf8mb4)
FROM system_tenant_package package CROSS JOIN cms_code_menu_scope scope
WHERE package.deleted = b'0'
  AND EXISTS (SELECT 1 FROM system_tenant tenant WHERE tenant.package_id = package.id AND tenant.id IN (121,162) AND tenant.deleted = b'0')
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(scope.menu_id AS JSON), '$') = 0
GROUP BY package.id, package.menu_ids;
UPDATE system_tenant_package package INNER JOIN cms_code_packages updates ON updates.package_id = package.id
SET package.menu_ids = updates.menu_ids, package.updater = 'V049', package.update_time = CURRENT_TIMESTAMP;

-- Raw JavaScript publishing is initially assigned to existing tenant administrators only.
-- Administrators can explicitly delegate the separate query/update/publish permissions.
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT role.id, scope.menu_id, 'V049','V049',b'0',role.tenant_id
FROM system_role role CROSS JOIN cms_code_menu_scope scope
WHERE role.tenant_id IN (121,162) AND role.code = 'tenant_admin' AND role.type = 1 AND role.status = 0 AND role.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id = role.id
  AND existing.menu_id = scope.menu_id AND existing.tenant_id = role.tenant_id AND existing.deleted = b'0');
DROP TEMPORARY TABLE cms_code_packages;
DROP TEMPORARY TABLE cms_code_menu_scope;
