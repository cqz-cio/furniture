-- Expand the existing TRIPEER brand operator from the homepage pilot to all CMS modules.
-- Account instances, passwords, website content and other tenants remain unchanged.
CREATE TEMPORARY TABLE tripeer_cms_guard (
  valid tinyint NOT NULL, CONSTRAINT chk_tripeer_cms_guard CHECK (valid = 1)
);
SET @tripeer_tenant = (SELECT MIN(id) FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0');
SET @tripeer_package = (SELECT package_id FROM system_tenant WHERE id=@tripeer_tenant);
SET @brand_role = (SELECT MIN(id) FROM system_role
  WHERE tenant_id=@tripeer_tenant AND code='brand_operator' AND deleted=b'0');
SET @cms_root = (SELECT MIN(id) FROM system_menu WHERE path='/seo' AND type=1 AND deleted=b'0');

-- Fail before granting anything if the seed or its isolated package has drifted.
INSERT INTO tripeer_cms_guard SELECT 0 WHERE
  @tripeer_tenant IS NULL OR @brand_role IS NULL OR @cms_root IS NULL
  OR (SELECT COUNT(*) FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0') <> 1
  OR (SELECT COUNT(*) FROM system_role WHERE tenant_id=@tripeer_tenant
      AND code='brand_operator' AND deleted=b'0') <> 1
  OR NOT EXISTS (SELECT 1 FROM system_tenant_package WHERE id=@tripeer_package AND deleted=b'0')
  OR EXISTS (SELECT 1 FROM system_tenant WHERE package_id=@tripeer_package
      AND id<>@tripeer_tenant AND deleted=b'0');

CREATE TEMPORARY TABLE tripeer_cms_pages (menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_cms_pages SELECT id FROM system_menu
WHERE parent_id=@cms_root AND type=2 AND status=0 AND deleted=b'0'
  AND path IN ('page-content','site-config','navigation','blog','metadata','analysis','website-code');
INSERT INTO tripeer_cms_guard SELECT 0 WHERE (SELECT COUNT(*) FROM tripeer_cms_pages) <> 7;

CREATE TEMPORARY TABLE tripeer_cms_scope (menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_cms_scope SELECT @cms_root UNION SELECT menu_id FROM tripeer_cms_pages;
INSERT INTO tripeer_cms_scope SELECT m.id FROM system_menu m
JOIN tripeer_cms_pages p ON p.menu_id=m.parent_id
WHERE m.type=3 AND m.status=0 AND m.deleted=b'0' AND m.permission LIKE 'seo:%';

-- Retain existing package grants while adding each CMS menu exactly once.
CREATE TEMPORARY TABLE tripeer_cms_package_scope (menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_cms_package_scope SELECT menu_id FROM tripeer_cms_scope;
INSERT IGNORE INTO tripeer_cms_package_scope
SELECT j.menu_id FROM system_tenant_package p,
  JSON_TABLE(p.menu_ids, '$[*]' COLUMNS (menu_id bigint PATH '$')) j
WHERE p.id=@tripeer_package;
SET @tripeer_full_menu_ids = (SELECT CAST(JSON_ARRAYAGG(menu_id) AS CHAR CHARACTER SET utf8mb4)
  FROM tripeer_cms_package_scope);

START TRANSACTION;
UPDATE system_tenant_package SET menu_ids=@tripeer_full_menu_ids,
  remark='本租户 CMS：页面、站点、导航、文章、SEO、关键词分析、统计与广告代码',
  updater='V051', update_time=CURRENT_TIMESTAMP WHERE id=@tripeer_package;
UPDATE system_role SET remark='管理本租户完整 CMS；不授予其他租户或系统管理权限',
  updater='V051', update_time=CURRENT_TIMESTAMP WHERE id=@brand_role AND tenant_id=@tripeer_tenant;
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT @brand_role,s.menu_id,'V051','V051',b'0',@tripeer_tenant FROM tripeer_cms_scope s
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@brand_role
  AND rm.tenant_id=@tripeer_tenant AND rm.menu_id=s.menu_id AND rm.deleted=b'0');
COMMIT;

DROP TEMPORARY TABLE tripeer_cms_package_scope;
DROP TEMPORARY TABLE tripeer_cms_scope;
DROP TEMPORARY TABLE tripeer_cms_pages;
DROP TEMPORARY TABLE tripeer_cms_guard;
