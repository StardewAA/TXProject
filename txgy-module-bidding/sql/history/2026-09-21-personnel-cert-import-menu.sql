-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 员工证书导入：注册权限码 + 菜单承载 + 角色授权（契约 e2e P2）
-- 登记日期：2026-09-21
--
-- [为什么] 新增 POST /bidding/personnel-certificate/import（含 import-template），
--   注解要求 bidding:personnel:cert:import。本框架**权限码只能经菜单授予**
--   （hasAnyPermission 先按 permission_menu_ids:{码} 找菜单，再按 menu_role_ids:{租户}:{菜单} 找角色），
--   没有承载菜单 ⇒ 任何角色都授不了 ⇒ 除超管外永久 403。故三件事必须同时做：
--   ① 注册 system_menu 行（承载权限码）；② 授权给已有同类能力的角色；③ 清权限缓存。
--
-- [号段] 60005311（60005300 员工资质段内空闲位，与 60005310 批量续期同段同父级）。
-- [授权口径] 跟随 60005310「员工证书批量续期」的既有授权（35 个角色）——
--   能批量续期就能批量导入，二者同属"批量维护员工证书"能力，不该出现"能续期不能导入"。
-- [幂等] INSERT IGNORE + 已存在不重复授；可重复执行。
-- ============================================================================

-- ① 菜单承载
INSERT IGNORE INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
   `updater`, `update_time`, `deleted`)
SELECT 60005311, '员工证书导入', 'bidding:personnel:cert:import', 3, 11, 60005300, '', '', '',
       '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE id = 60005311);

-- ② 角色授权（跟随 60005310 的授权集合；同租户跟随，避免跨租户误授）
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT DISTINCT rm.role_id, 60005311, '1', NOW(), '1', NOW(), b'0'
FROM `system_role_menu` rm
WHERE rm.menu_id = 60005310 AND rm.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x
                  WHERE x.role_id = rm.role_id AND x.menu_id = 60005311 AND x.deleted = 0);

-- 校验段
SELECT '菜单' AS kind, id, name, permission FROM system_menu WHERE id = 60005311
UNION ALL
SELECT '授权角色数', COUNT(DISTINCT role_id), '', '' FROM system_role_menu WHERE menu_id = 60005311 AND deleted = 0;
