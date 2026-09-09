-- 考试认证细分权限：已有租户管理员且拥有考试入口时可管理本企业考试。
-- 讲师、审核员由系统管理员在角色管理中按需授予以下按钮权限。
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 41061,'考试管理','training:exam:manage',3,10,41006,'','','','',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=41061 OR permission='training:exam:manage');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 41062,'题库编写与阅卷','training:exam:author',3,20,41006,'','','','',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=41062 OR permission='training:exam:author');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 41063,'试题审核','training:exam:review',3,30,41006,'','','','',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=41063 OR permission='training:exam:review');
