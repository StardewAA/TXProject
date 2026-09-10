-- =====================================================================
-- CRM 售后菜单：租户套餐授权 + 租户超管角色授权
-- 日期：2026-09-08
-- 背景：售后菜单（32600~32652）只插入了 system_menu，租户能否看到菜单受
--       两块控制：
--       1) system_tenant_package.menu_ids —— 租户套餐的菜单范围（硬门槛）
--       2) system_role_menu —— 角色拥有的菜单/按钮权限
-- 说明：本脚本幂等，可重复执行。
--       套餐：仅给「已包含 CRM 顶级目录(2397)」的套餐追加售后菜单。
--       角色：给所有租户超级管理员角色(code=tenant_admin)授予售后菜单。
-- 应用范围：ruoyi-vue-pro 业务库
-- =====================================================================

USE `ruoyi-vue-pro`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 租户套餐追加售后菜单（仅对已开通 CRM 的套餐）
-- ---------------------------------------------------------------------
UPDATE `system_tenant_package`
SET `menu_ids` = CONCAT(
  SUBSTRING(`menu_ids`, 1, CHAR_LENGTH(`menu_ids`) - 1),
  ',32600,32601,32602,32603,32604,32605,32610,32611,32612,32613,32614,32620,32621,32622,32623,32624,32630,32631,32632,32633,32634,32640,32641,32642,32643,32644,32650,32651,32652]'
)
WHERE `deleted` = b'0'
  AND `menu_ids` IS NOT NULL
  AND `menu_ids` LIKE '%]'
  AND `menu_ids` NOT LIKE '%32600%'
  AND (
    `menu_ids` LIKE '[2397,%'
    OR `menu_ids` LIKE '%,2397,%'
    OR `menu_ids` LIKE '%,2397]'
  );

-- ---------------------------------------------------------------------
-- 2. 租户超级管理员角色(tenant_admin)授予售后菜单与按钮权限
-- ---------------------------------------------------------------------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`,
       m.`id`,
       '1',
       NOW(),
       '1',
       NOW(),
       b'0',
       r.`tenant_id`
FROM `system_role` r
       JOIN `system_menu` m
            ON m.`id` BETWEEN 32600 AND 32699 AND m.`deleted` = b'0'
WHERE r.`code` = 'tenant_admin'
  AND r.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` rm
    WHERE rm.`role_id` = r.`id`
      AND rm.`menu_id` = m.`id`
      AND rm.`deleted` = b'0'
  );
