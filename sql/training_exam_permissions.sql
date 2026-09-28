-- 考试认证细分权限：已有租户管理员且拥有考试入口时可管理本企业考试。
-- 讲师、审核员由系统管理员在角色管理中按需授予以下按钮权限。
--
-- 版本说明（2026-09-10）：
--  原按钮 ID 41061~41063 与「资源中心」子菜单（training_resource_submenu.sql）冲突，
--  重复执行时按钮会被资源子菜单覆盖且 NOT EXISTS 判断无法自愈。
--  本版本整体迁移到空闲段 41631~41633，并在文末清理历史遗留行。
SET NAMES utf8mb4;

-- 1. 考试认证按钮（41631~41633，父级 41006 考试与认证）
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 41631,'考试管理','training:exam:manage',3,10,41006,'','','','',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=41631 OR permission='training:exam:manage');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 41632,'题库编写与阅卷','training:exam:author',3,20,41006,'','','','',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=41632 OR permission='training:exam:author');
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 41633,'试题审核','training:exam:review',3,30,41006,'','','','',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=41633 OR permission='training:exam:review');

-- 2. 清理历史遗留：早期版本把考试按钮建在 41061~41063（与资源中心子菜单冲突）。
--    仅删除 type=3 且权限为考试模块的行，绝不触碰资源中心页面菜单。
DELETE FROM system_menu
WHERE deleted = b'0'
  AND id BETWEEN 41061 AND 41063
  AND type = 3
  AND permission IN ('training:exam:manage','training:exam:author','training:exam:review');
