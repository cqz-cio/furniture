-- Tenant-scoped website asset metadata; existing shared file storage and credentials are unchanged.
CREATE TABLE IF NOT EXISTS website_media (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name varchar(160) NOT NULL, alt varchar(240) NOT NULL DEFAULT '',
 kind varchar(16) NOT NULL, mime_type varchar(80) NOT NULL, size bigint NOT NULL,
 url varchar(1024) NOT NULL, width int DEFAULT NULL, height int DEFAULT NULL,
 archived bit(1) NOT NULL DEFAULT b'0',
 creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 KEY idx_website_media_library(tenant_id,deleted,archived,kind,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户官网公开素材';

CREATE TEMPORARY TABLE tripeer_media_guard (valid tinyint NOT NULL, CONSTRAINT chk_tripeer_media_guard CHECK(valid=1));
SET @media_tenant=(SELECT MIN(id) FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0');
SET @media_package=(SELECT package_id FROM system_tenant WHERE id=@media_tenant);
SET @media_role=(SELECT MIN(id) FROM system_role WHERE tenant_id=@media_tenant AND code='brand_operator' AND deleted=b'0');
SET @media_root=(SELECT parent_id FROM system_menu WHERE id=(SELECT parent_id FROM system_menu WHERE permission='seo:page:query' AND deleted=b'0' LIMIT 1));
INSERT INTO tripeer_media_guard SELECT 0 WHERE @media_tenant IS NULL OR @media_role IS NULL OR @media_root IS NULL
 OR (SELECT COUNT(*) FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0')<>1
 OR (SELECT COUNT(*) FROM system_role WHERE tenant_id=@media_tenant AND code='brand_operator' AND deleted=b'0')<>1
 OR NOT EXISTS(SELECT 1 FROM system_tenant_package WHERE id=@media_package AND deleted=b'0')
 OR EXISTS(SELECT 1 FROM system_tenant WHERE package_id=@media_package AND id<>@media_tenant AND deleted=b'0');
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT '素材库','',2,85,@media_root,'media','ep:picture','seo/media/index','SeoMedia',0,b'1',b'1',b'1','V053','V053'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE parent_id=@media_root AND path='media' AND deleted=b'0');
SET @media_page=(SELECT MIN(id) FROM system_menu WHERE parent_id=@media_root AND path='media' AND deleted=b'0');
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT p.name,p.permission,3,p.sort,@media_page,'','','','',0,b'1',b'1',b'1','V053','V053'
FROM (SELECT '查看素材' name,'seo:media:query' permission,1 sort UNION ALL
 SELECT '上传素材','seo:media:upload',2 UNION ALL SELECT '编辑素材','seo:media:update',3 UNION ALL
 SELECT '移出与恢复素材','seo:media:archive',4) p
WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=p.permission AND m.deleted=b'0');
CREATE TEMPORARY TABLE tripeer_media_scope(menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_media_scope SELECT @media_root UNION SELECT @media_page;
INSERT IGNORE INTO tripeer_media_scope SELECT id FROM system_menu WHERE deleted=b'0' AND status=0 AND permission IN
 ('seo:media:query','seo:media:upload','seo:media:update','seo:media:archive');
INSERT INTO tripeer_media_guard SELECT 0 WHERE (SELECT COUNT(*) FROM tripeer_media_scope)<>6;
CREATE TEMPORARY TABLE tripeer_media_package(menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_media_package SELECT menu_id FROM tripeer_media_scope;
INSERT IGNORE INTO tripeer_media_package SELECT j.menu_id FROM system_tenant_package p,
 JSON_TABLE(p.menu_ids,'$[*]' COLUMNS(menu_id bigint PATH '$')) j WHERE p.id=@media_package;
SET @media_menu_ids=(SELECT CAST(JSON_ARRAYAGG(menu_id) AS CHAR CHARACTER SET utf8mb4) FROM tripeer_media_package);
START TRANSACTION;
UPDATE system_tenant_package SET menu_ids=@media_menu_ids,updater='V053',update_time=CURRENT_TIMESTAMP WHERE id=@media_package;
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT @media_role,s.menu_id,'V053','V053',b'0',@media_tenant FROM tripeer_media_scope s
WHERE NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@media_role AND rm.tenant_id=@media_tenant AND rm.menu_id=s.menu_id AND rm.deleted=b'0');
COMMIT;
DROP TEMPORARY TABLE tripeer_media_package;
DROP TEMPORARY TABLE tripeer_media_scope;
DROP TEMPORARY TABLE tripeer_media_guard;
