-- TRIPEER homepage CMS. No users, passwords or password hashes are seeded.
ALTER TABLE seo_site_config ADD COLUMN preview_base_url varchar(512) NULL AFTER site_url;

CREATE TABLE website_page (
  id bigint NOT NULL AUTO_INCREMENT,
  site_id bigint NOT NULL,
  page_key varchar(64) NOT NULL,
  locale varchar(32) NOT NULL,
  schema_version int NOT NULL DEFAULT 1,
  draft_json mediumtext NOT NULL,
  draft_version int NOT NULL DEFAULT 1,
  published_revision_id bigint NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  active_record tinyint GENERATED ALWAYS AS (CASE WHEN deleted = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_website_page (tenant_id, site_id, page_key, locale, active_record)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE website_page_revision (
  id bigint NOT NULL AUTO_INCREMENT,
  page_id bigint NOT NULL,
  revision int NOT NULL,
  schema_version int NOT NULL,
  content_json mediumtext NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_website_page_revision (tenant_id, page_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @cms_root = (SELECT MIN(id) FROM system_menu WHERE path = '/seo' AND type = 1 AND deleted = b'0');
INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '页面内容','',2,0,@cms_root,'page-content','ep:document','seo/page-content/index','SeoPageContent',
0,b'1',b'0',b'1','V050','V050',b'0'
WHERE @cms_root IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_menu WHERE parent_id=@cms_root AND path='page-content' AND deleted=b'0');
SET @page_menu = (SELECT MIN(id) FROM system_menu WHERE parent_id=@cms_root AND path='page-content' AND deleted=b'0');

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT scope.name,scope.permission,3,scope.sort,@page_menu,'','','','',0,b'1',b'0',b'1','V050','V050',b'0'
FROM (
 SELECT '页面查询' AS name,'seo:page:query' AS permission,1 AS sort
 UNION ALL SELECT '保存页面草稿','seo:page:update',2
 UNION ALL SELECT '发布页面','seo:page:publish',3
 UNION ALL SELECT '预览页面','seo:page:preview',4
) scope
WHERE @page_menu IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_menu WHERE permission=scope.permission AND deleted=b'0');

CREATE TEMPORARY TABLE tripeer_page_menu_scope (menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_page_menu_scope
SELECT id FROM system_menu WHERE deleted=b'0' AND (
 id IN (@cms_root,@page_menu)
 OR permission IN ('seo:page:query','seo:page:update','seo:page:publish','seo:page:preview',
                   'seo:site-config:query')
 OR (parent_id=@cms_root AND path='site-config')
);
SET @tripeer_menus = (SELECT CAST(JSON_ARRAYAGG(menu_id) AS CHAR CHARACTER SET utf8mb4) FROM tripeer_page_menu_scope);

INSERT INTO system_tenant_package (name,status,remark,menu_ids,creator,updater,deleted)
SELECT 'TRIPEER 企业官网运营',0,'首页内容编辑、预览与发布；不包含账号管理及脚本发布',@tripeer_menus,'V050','V050',b'0'
WHERE @page_menu IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_tenant_package WHERE name='TRIPEER 企业官网运营' AND deleted=b'0');
SET @tripeer_package = (SELECT MIN(id) FROM system_tenant_package WHERE name='TRIPEER 企业官网运营' AND deleted=b'0');

INSERT INTO system_tenant
(name,code,contact_name,status,websites,business_mode,package_id,expire_time,account_count,creator,updater,deleted)
SELECT 'TRIPEER','TRIPEER','品牌运营负责人（待配置）',0,'[]','B2B',@tripeer_package,'2099-12-31 23:59:59',20,'V050','V050',b'0'
WHERE @tripeer_package IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0');
SET @tripeer_tenant = (SELECT id FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0');

-- Empty URLs require the administrator to supply actual deployment/preview addresses.
INSERT INTO seo_site_config
(site_id,site_name,site_url,default_locale,navigation_template,tenant_id,creator,updater)
SELECT 1,'TRIPEER 企业官网','','zh-CN','TRIPEER_CORPORATE',@tripeer_tenant,'V050','V050'
WHERE @tripeer_tenant IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM seo_site_config WHERE tenant_id=@tripeer_tenant AND site_id=1 AND deleted=b'0');

INSERT INTO system_role
(name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,tenant_id,creator,updater)
SELECT '品牌运营','brand_operator',1,1,'[]',0,2,'仅管理本租户官网内容',@tripeer_tenant,'V050','V050'
WHERE @tripeer_tenant IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_role WHERE tenant_id=@tripeer_tenant AND code='brand_operator' AND deleted=b'0');
SET @brand_role = (SELECT MIN(id) FROM system_role WHERE tenant_id=@tripeer_tenant AND code='brand_operator' AND deleted=b'0');

INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT @brand_role,scope.menu_id,'V050','V050',b'0',@tripeer_tenant FROM tripeer_page_menu_scope scope
WHERE @brand_role IS NOT NULL AND NOT EXISTS
(SELECT 1 FROM system_role_menu WHERE role_id=@brand_role AND menu_id=scope.menu_id AND tenant_id=@tripeer_tenant AND deleted=b'0');
DROP TEMPORARY TABLE tripeer_page_menu_scope;
