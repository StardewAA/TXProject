SET NAMES utf8mb4;

-- 2026-09-22 修正：恢复被误伤的「标书智能体」菜单。
--
-- 背景：本地库智能体菜单段为 6800~6809（master 为 6790~6799，本地因 6790~6792 被
-- 主体信息与授权管理占用而整体 +7 排号）。同日的
--   2026-09-22-retire-bid-generate-menu.sql  与
--   2026-09-22-embed-skill-mcp-into-agent.sql
-- 按 master 编号执行，误把 6800（标书智能体目录）、6803（标书智能体查询）逻辑删除、
-- 把 6801（知识库管理）/6802（智能体管理）置为不可见、把 6804/6805 挂到 6790 下，
-- 并删除了 6800/6803 的角色授权。本脚本按本地实际编号恢复原状。
-- 幂等：可重复执行。

-- 1. 恢复目录与权限点（原值 deleted=0、status=0、visible=1）
UPDATE system_menu
SET deleted = b'0', status = 0, visible = b'1', updater = '1', update_time = NOW()
WHERE id IN (6800, 6803)
  AND deleted = b'1';

-- 2. 恢复页面菜单可见性（原值 visible=1）
UPDATE system_menu
SET visible = b'1', updater = '1', update_time = NOW()
WHERE id IN (6801, 6802);

-- 3. 恢复 6804/6805 的父级与排序（原挂知识库管理 6801 下，sort 2/3）
UPDATE system_menu
SET parent_id = 6801, sort = 2, updater = '1', update_time = NOW()
WHERE id = 6804 AND deleted = b'0';

UPDATE system_menu
SET parent_id = 6801, sort = 3, updater = '1', update_time = NOW()
WHERE id = 6805 AND deleted = b'0';

-- 4. 恢复角色授权：6800/6803 授权给仍持有其余智能体菜单的角色（60346/60352，租户 2019）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.role_id, m.menu_id, '1', NOW(), '1', NOW(), b'0', 2019
FROM (SELECT 60346 AS role_id UNION ALL SELECT 60352) r
CROSS JOIN (SELECT 6800 AS menu_id UNION ALL SELECT 6803) m
WHERE NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id AND rm.deleted = b'0'
);

-- 校验：应看到 6800/6803 deleted=0、6801/6802 visible=1、6804/6805 父级 6801
SELECT id, name, parent_id, sort, status, visible, deleted
  FROM system_menu
 WHERE id IN (6800, 6801, 6802, 6803, 6804, 6805)
 ORDER BY id;

SELECT rm.role_id, rm.menu_id, rm.deleted, rm.tenant_id
  FROM system_role_menu rm
 WHERE rm.menu_id IN (6800, 6803)
 ORDER BY rm.menu_id, rm.role_id;
