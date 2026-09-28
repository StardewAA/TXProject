-- 2026-09-20 WMS 库存流水：增加导出权限
-- 库存流水的计量单位来自商品主数据，操作人来自库存流水审计字段 creator，
-- 因此本次功能不需要改动 wms_inventory_history 表结构。

SET @inventory_history_export_menu_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM `system_menu`);

INSERT INTO `system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT @inventory_history_export_menu_id,
       '库存流水导出', 'wms:inventory-history:export', 3, 2,
       (SELECT id FROM `system_menu` WHERE permission = '' AND component = 'wms/inventory/history/index' LIMIT 1),
       '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM `system_menu`
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` WHERE permission = 'wms:inventory-history:export' AND deleted = b'0'
)
-- LIMIT 1 必须保留：上面的 NOT EXISTS 是非关联子查询，缺省会对 system_menu 全部行求值，
-- 导致 INSERT 多行同一主键而报 Duplicate entry。
LIMIT 1;

-- 已经拥有“库存流水查询”的角色同步获得导出权限。
-- 注意：id 基数必须先用变量取出来。若把 (SELECT MAX(id) FROM system_role_menu) 直接写在
-- INSERT ... SELECT 的 SELECT 列表里，MySQL 会在插入过程中重复读取目标表，同一条语句内
-- 就会出现主键冲突（Duplicate entry ... for key 'system_role_menu.PRIMARY'）。
SET @inventory_history_role_menu_base_id := (SELECT COALESCE(MAX(id), 0) FROM `system_role_menu`);

INSERT INTO `system_role_menu`
    (`id`, `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @inventory_history_role_menu_base_id + src.`rn`,
       src.`role_id`, src.`menu_id`, '1', NOW(), '1', NOW(), b'0', src.`tenant_id`
FROM (
    -- 先对 (角色, 租户, 菜单) 去重，再编号，避免同一个角色被重复授权
    SELECT DISTINCT t.`role_id`, t.`tenant_id`, t.`menu_id`,
           ROW_NUMBER() OVER (ORDER BY t.`tenant_id`, t.`role_id`, t.`menu_id`) AS `rn`
    FROM (
        SELECT DISTINCT query_role.`role_id`, query_role.`tenant_id`, export_menu.`id` AS `menu_id`
        FROM `system_role_menu` query_role
        JOIN `system_menu` query_menu
          ON query_menu.`id` = query_role.`menu_id`
         AND query_menu.`permission` = 'wms:inventory-history:query'
         AND query_menu.`deleted` = b'0'
        JOIN `system_menu` export_menu
          ON export_menu.`permission` = 'wms:inventory-history:export'
         AND export_menu.`deleted` = b'0'
        WHERE NOT EXISTS (
            SELECT 1
            FROM `system_role_menu` granted
            WHERE granted.`role_id` = query_role.`role_id`
              AND granted.`menu_id` = export_menu.`id`
              AND granted.`tenant_id` = query_role.`tenant_id`
              AND granted.`deleted` = b'0'
        )
    ) t
) src;
