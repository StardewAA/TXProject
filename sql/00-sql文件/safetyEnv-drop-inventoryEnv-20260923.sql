-- ============================================================
--  移除「在库环保视图」菜单（34010 污染管控 下）
--  原因：用户明确不要这个菜单项。
--  说明：safetyEnv-align-20260923.sql 是按原机器 dump 对齐的，其中包含 34303；
--        本文件是用户在这之上的**有意偏离**。若将来重跑对齐脚本，34303 会被重新加回来，
--        需要再跑一次本文件，或把对齐脚本里 34303 的那条 INSERT 删掉。
--  ⚠️ 删完必须清 Redis 权限缓存，并注意各服务有 1 分钟的进程内权限缓存
-- ============================================================
SET NAMES utf8mb4;
USE `ruoyi-vue-pro`;

DELETE FROM system_role_menu WHERE menu_id = 34303;
DELETE FROM system_menu WHERE id = 34303;

-- 回滚（如需恢复）：
-- INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,
--   `component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,
--   `creator`,`create_time`,`updater`,`update_time`,`deleted`)
-- VALUES (34303,'在库环保视图','',2,6,34010,'inventoryEnv','ep:search',
--   'mes/safetyEnv/inventoryEnv/index','MesInventoryEnv',0,b'1',b'1',b'1',
--   '1',NOW(),'1',NOW(),b'0');
-- INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
-- VALUES (60185,34303,'sql',NOW(),'sql',NOW(),0,2010),(60466,34303,'sql',NOW(),'sql',NOW(),0,2010),
--        (60190,34303,'sql',NOW(),'sql',NOW(),0,2010),(60192,34303,'sql',NOW(),'sql',NOW(),0,2010),
--        (60196,34303,'sql',NOW(),'sql',NOW(),0,2010);

-- 校验：应无输出
SELECT id, name FROM system_menu WHERE id = 34303;

-- 校验：34010 污染管控 下剩余的菜单
SELECT id, name, sort FROM system_menu WHERE parent_id = 34010 AND deleted = 0 ORDER BY sort;
