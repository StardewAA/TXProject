-- 补上「化学安全排查」的菜单：后端 controller/service + 前端页面都在，
-- 唯独没有 system_menu 行，导致页面在菜单里进不去、接口 403（mes:set-chemical-safety:* 无人持有）。
-- 挂到 34013「常规检测」组，排在 职业危害检测 之后。
--
-- ⚠️ 若在服务运行中执行，授权后要清一次权限缓存再试——
--    授权前被查过的权限字符串会在 Redis 里留下一个空集合（负缓存，TTL 约 1 小时），
--    不清的话新授权要等 TTL 过期才生效：
--      redis-cli -n 0 del permission_menu_ids:mes:set-chemical-safety:create
--
-- 跑法：MYSQL_PWD=root mysql -uroot --default-character-set=utf8mb4 < 本文件
SET NAMES utf8mb4;

-- 让个位：检测计划 9→10、检测标准 10→11，给化学安全排查腾出 9
UPDATE system_menu SET sort = 11 WHERE id = 34690;
UPDATE system_menu SET sort = 10 WHERE id = 34680;

DELETE FROM system_menu WHERE id BETWEEN 34710 AND 34714;

INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
  (34710, '化学安全排查', '', 2, 9, 34013, 'chemical-safety', 'ep:warning',
   'mes/safetyEnv/chemicalSafety/index', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34711, '查询',   'mes:set-chemical-safety:query',  3, 1, 34710, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34712, '创建',   'mes:set-chemical-safety:create', 3, 2, 34710, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34713, '更新',   'mes:set-chemical-safety:update', 3, 3, 34710, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34714, '删除',   'mes:set-chemical-safety:delete', 3, 4, 34710, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 授权：凡已能看「常规检测」组或组内任一叶子的角色，都补上这条
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, g.gid, '1', NOW(), '1', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
JOIN (
              SELECT 34710 AS gid, 34013 AS cid
    UNION ALL SELECT 34711, 34013 UNION ALL SELECT 34712, 34013
    UNION ALL SELECT 34713, 34013 UNION ALL SELECT 34714, 34013
    UNION ALL SELECT 34710, 34600 UNION ALL SELECT 34711, 34600 UNION ALL SELECT 34712, 34600
    UNION ALL SELECT 34713, 34600 UNION ALL SELECT 34714, 34600
) g ON g.cid = rm.menu_id
WHERE rm.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = rm.role_id AND x.menu_id = g.gid AND x.deleted = b'0');

-- ============ 回滚 ============
-- DELETE FROM system_role_menu WHERE menu_id BETWEEN 34710 AND 34714;
-- DELETE FROM system_menu      WHERE id      BETWEEN 34710 AND 34714;
-- UPDATE system_menu SET sort = 9  WHERE id = 34680;
-- UPDATE system_menu SET sort = 10 WHERE id = 34690;
