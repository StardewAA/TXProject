-- =====================================================================
-- CRM 产品管理改为实时取自 WMS 仓库库存（只读）
-- 日期：2026-09-15
-- 背景：CRM 产品管理全部数据实时取自 WMS 仓库库存接口，不在 CRM 本地落库：
--       1. WMS 仓库产品新增、修改、删除时，CRM 产品管理页面立即同步；
--       2. WMS 存在的产品 CRM 才展示，WMS 无产品则不展示；
--       3. CRM 不支持手动新增产品，产品编号直接使用 WMS 规格编号（skuId）。
-- 内容：
--   1. 删除产品的手动维护按钮权限：crm:product:create / update / delete
--   2. 删除已废弃的「从 WMS 同步」按钮权限 crm:product:sync（实时读取不再需要同步）
--   3. 清理上述按钮的角色授权，避免菜单残留
--   4. 停用 WMS 产品同步定时任务（如已注册）
-- 说明：幂等，可重复执行；仅删除按钮级权限，产品菜单 crm:product:query 保留只读访问。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 清理角色授权：移除产品新增/修改/删除/同步按钮
-- ---------------------------------------------------------------------
DELETE rm
FROM `system_role_menu` rm
         JOIN `system_menu` m ON m.id = rm.menu_id
WHERE m.permission IN ('crm:product:create', 'crm:product:update', 'crm:product:delete', 'crm:product:sync');

-- ---------------------------------------------------------------------
-- 2. 删除菜单按钮
-- ---------------------------------------------------------------------
DELETE FROM `system_menu`
WHERE permission IN ('crm:product:create', 'crm:product:update', 'crm:product:delete', 'crm:product:sync');

-- ---------------------------------------------------------------------
-- 3. 停用 WMS 产品同步定时任务
-- ---------------------------------------------------------------------
UPDATE `infra_job`
SET `status` = 1,
    `updater` = '1',
    `update_time` = NOW()
WHERE `handler_name` = 'crmProductWmsSyncJob'
  AND `status` = 0;
