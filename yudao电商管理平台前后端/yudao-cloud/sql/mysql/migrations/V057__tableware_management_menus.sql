-- Register capabilities only. No tenant, account, password or existing role grant is changed.
SET @tableware_seo_root=(SELECT MIN(parent_id) FROM system_menu WHERE component='seo/page-content/index' AND deleted=b'0');
CREATE TEMPORARY TABLE tableware_menu_guard(valid tinyint NOT NULL CHECK(valid=1));
INSERT INTO tableware_menu_guard SELECT 0 WHERE @tableware_seo_root IS NULL;
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT '餐具产品目录','',2,86,@tableware_seo_root,'catalog','ep:goods','seo/catalog/index','SeoCatalog',0,b'1',b'1',b'1','V057','V057'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE parent_id=@tableware_seo_root AND path='catalog' AND deleted=b'0');
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT '网站运营中心','',2,87,@tableware_seo_root,'operations','ep:checked','seo/operations/index','SeoOperations',0,b'1',b'1',b'1','V057','V057'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE parent_id=@tableware_seo_root AND path='operations' AND deleted=b'0');
SET @tableware_ops_menu=(SELECT MIN(id) FROM system_menu WHERE parent_id=@tableware_seo_root AND path='operations' AND deleted=b'0');
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT p.name,p.permission,3,p.sort,@tableware_ops_menu,'','','','',0,b'1',b'1',b'1','V057','V057'
FROM (SELECT '查看运营内容' name,'seo:operations:query' permission,1 sort UNION ALL
 SELECT '编辑与提交审核','seo:operations:update',2 UNION ALL
 SELECT '批准或退回审核','seo:operations:approve',3 UNION ALL
 SELECT '发布及恢复资料','seo:operations:publish',4) p
WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=p.permission AND m.deleted=b'0');
DROP TEMPORARY TABLE tableware_menu_guard;
