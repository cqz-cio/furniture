-- Catalog shares page APIs but owns its menu ancestry. Granting these actions
-- must not require exposing the TRIPEER-only home editor to a Tableware tenant.
SET @tableware_catalog=(SELECT MIN(id) FROM system_menu WHERE component='seo/catalog/index' AND deleted=b'0');
CREATE TEMPORARY TABLE tableware_catalog_guard(valid tinyint NOT NULL CHECK(valid=1));
INSERT INTO tableware_catalog_guard SELECT 0 WHERE @tableware_catalog IS NULL;
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT p.name,p.permission,3,p.sort,@tableware_catalog,'','','','',0,b'1',b'1',b'1','V058','V058'
FROM (SELECT '查看目录' name,'seo:page:query' permission,1 sort UNION ALL
 SELECT '编辑目录','seo:page:update',2 UNION ALL
 SELECT '预览目录','seo:page:preview',3 UNION ALL
 SELECT '发布目录','seo:page:publish',4) p
WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.parent_id=@tableware_catalog AND m.permission=p.permission AND m.deleted=b'0');
DROP TEMPORARY TABLE tableware_catalog_guard;
