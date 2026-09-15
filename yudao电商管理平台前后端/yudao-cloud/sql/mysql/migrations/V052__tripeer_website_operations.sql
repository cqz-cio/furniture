-- Extend only the existing TRIPEER brand operator. No accounts or credentials are seeded.
CREATE TEMPORARY TABLE tripeer_operations_guard (
  valid tinyint NOT NULL, CONSTRAINT chk_tripeer_operations_guard CHECK (valid = 1)
);
SET @ops_tenant = (SELECT MIN(id) FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0');
SET @ops_package = (SELECT package_id FROM system_tenant WHERE id=@ops_tenant);
SET @ops_role = (SELECT MIN(id) FROM system_role WHERE tenant_id=@ops_tenant AND code='brand_operator' AND deleted=b'0');
SET @ops_clue_query = (SELECT MIN(id) FROM system_menu WHERE permission='crm:clue:query' AND deleted=b'0' AND status=0);
SET @ops_clue_page = (SELECT parent_id FROM system_menu WHERE id=@ops_clue_query);
SET @ops_crm_root = (SELECT parent_id FROM system_menu WHERE id=@ops_clue_page);
SET @ops_dashboard = (SELECT MIN(id) FROM system_menu WHERE path='/dashboard' AND type=2 AND status=0 AND deleted=b'0');
INSERT INTO tripeer_operations_guard SELECT 0 WHERE
  @ops_tenant IS NULL OR @ops_role IS NULL OR @ops_clue_page IS NULL OR @ops_crm_root IS NULL OR @ops_dashboard IS NULL
  OR (SELECT COUNT(*) FROM system_tenant WHERE code='TRIPEER' AND deleted=b'0')<>1
  OR (SELECT COUNT(*) FROM system_role WHERE tenant_id=@ops_tenant AND code='brand_operator' AND deleted=b'0')<>1
  OR NOT EXISTS (SELECT 1 FROM system_tenant_package WHERE id=@ops_package AND deleted=b'0')
  OR EXISTS (SELECT 1 FROM system_tenant WHERE package_id=@ops_package AND id<>@ops_tenant AND deleted=b'0');

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT '官网询盘初筛','crm:clue:triage',3,90,@ops_clue_page,'','','','',0,b'1',b'1',b'1','V052','V052'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='crm:clue:triage' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT '官网访问数据查询','statistics:website:query',3,90,@ops_dashboard,'','','','',0,b'1',b'1',b'1','V052','V052'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='statistics:website:query' AND deleted=b'0');

CREATE TEMPORARY TABLE tripeer_operations_scope (menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_operations_scope SELECT @ops_clue_page UNION SELECT @ops_crm_root UNION SELECT @ops_dashboard;
INSERT IGNORE INTO tripeer_operations_scope SELECT id FROM system_menu WHERE deleted=b'0' AND status=0
  AND permission IN ('crm:clue:query','crm:clue:triage','statistics:website:query');
INSERT INTO tripeer_operations_guard SELECT 0 WHERE (SELECT COUNT(*) FROM tripeer_operations_scope)<>6;

CREATE TEMPORARY TABLE tripeer_operations_package (menu_id bigint PRIMARY KEY);
INSERT INTO tripeer_operations_package SELECT menu_id FROM tripeer_operations_scope;
INSERT IGNORE INTO tripeer_operations_package
SELECT j.menu_id FROM system_tenant_package p,
  JSON_TABLE(p.menu_ids,'$[*]' COLUMNS(menu_id bigint PATH '$')) j WHERE p.id=@ops_package;
SET @ops_menu_ids=(SELECT CAST(JSON_ARRAYAGG(menu_id) AS CHAR CHARACTER SET utf8mb4) FROM tripeer_operations_package);
START TRANSACTION;
UPDATE system_tenant_package SET menu_ids=@ops_menu_ids,updater='V052',update_time=CURRENT_TIMESTAMP,
  remark='本官网 CMS、询盘初筛与只读访问数据' WHERE id=@ops_package;
UPDATE system_role SET remark='本官网 CMS、询盘查看/处理/跟进与访问数据；不含销售、财务、系统或共享文件管理',
  updater='V052',update_time=CURRENT_TIMESTAMP WHERE id=@ops_role AND tenant_id=@ops_tenant;
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT @ops_role,s.menu_id,'V052','V052',b'0',@ops_tenant FROM tripeer_operations_scope s
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=@ops_role AND rm.tenant_id=@ops_tenant AND rm.menu_id=s.menu_id AND rm.deleted=b'0');
COMMIT;
DROP TEMPORARY TABLE tripeer_operations_package;
DROP TEMPORARY TABLE tripeer_operations_scope;
DROP TEMPORARY TABLE tripeer_operations_guard;
