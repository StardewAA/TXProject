-- ============================================================
-- 新增「员工」角色（租户隔离，每个租户一个），并授予 HRM 员工端菜单
-- 目的：招聘转员工/建档时自动开通的员工账号，HR 可以直接把这个角色分配给员工；
--      后续需要什么权限，HR 在「系统管理 → 角色管理」里自行调整即可。
-- 执行库：ruoyi-vue-pro
-- 可重复执行（存在即跳过）
-- ============================================================

-- 【1】为每个租户创建「员工」角色（数据范围＝仅本人，类型＝自定义）
INSERT INTO `system_role`
  (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`,
   `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '员工', CONCAT('employee_role_', t.`id`), 50, 5, '', 0, 2,
       '员工端角色：我的档案、我的工资条、我的社保、考勤报表、绩效管理、招聘应聘；权限可由 HR 按需调整',
       'admin', NOW(), 'admin', NOW(), b'0', t.`id`
FROM `system_tenant` t
WHERE t.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role` r
    WHERE r.`tenant_id` = t.`id` AND r.`code` = CONCAT('employee_role_', t.`id`) AND r.`deleted` = b'0'
  );

-- 【2】授予 HRM 员工端菜单（1614 子树，共 17 条）
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`menu_id`, 'admin', NOW(), 'admin', NOW(), b'0', r.`tenant_id`
FROM `system_role` r
JOIN (
            SELECT 1476 AS menu_id  -- HRM 人力资源（顶级菜单，必须授权：框架要求父菜单在授权集合内，否则整棵子树会被当作禁用过滤）
  UNION ALL SELECT 1614             -- HRM 员工端（父级菜单）
  UNION ALL SELECT 1624             -- 个人工作台
  UNION ALL SELECT 1625             -- 个人工作台查询
  UNION ALL SELECT 1626             -- 备忘新增
  UNION ALL SELECT 1627             -- 备忘删除
  UNION ALL SELECT 1645             -- 招聘应聘
  UNION ALL SELECT 1615             -- 我的档案
  UNION ALL SELECT 1620             -- 员工档案修改
  UNION ALL SELECT 1616             -- 我的社保
  UNION ALL SELECT 1617             -- 我的工资条
  UNION ALL SELECT 1618             -- 考勤报表
  UNION ALL SELECT 1621             -- 员工请假申请
  UNION ALL SELECT 1628             -- 我的流程查询
  UNION ALL SELECT 1619             -- 绩效管理（父级）
  UNION ALL SELECT 1622             -- KPI 考核
  UNION ALL SELECT 1623             -- 员工绩效操作
  UNION ALL SELECT 1629             -- 我的绩效档案
) m
WHERE r.`deleted` = b'0' AND r.`code` LIKE 'employee\_role\_%'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`menu_id` AND x.`deleted` = b'0'
  );

-- 【3】核对：各租户「员工」角色及其菜单数
SELECT r.`tenant_id`, r.`id` AS role_id, r.`name`, r.`code`, r.`data_scope`,
       COUNT(rm.`menu_id`) AS menu_count
FROM `system_role` r
LEFT JOIN `system_role_menu` rm ON rm.`role_id` = r.`id` AND rm.`deleted` = b'0'
WHERE r.`deleted` = b'0' AND r.`code` LIKE 'employee\_role\_%'
GROUP BY r.`tenant_id`, r.`id`, r.`name`, r.`code`, r.`data_scope`
ORDER BY r.`tenant_id`;

-- 【4】执行后清理权限缓存（否则菜单/权限不生效）：
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'menu_role_ids:*' | xargs -r redis-cli del"
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'permission_menu_ids:*' | xargs -r redis-cli del"
--   docker exec txgy-redis sh -c "redis-cli --scan --pattern 'user_role_ids:*' | xargs -r redis-cli del"
