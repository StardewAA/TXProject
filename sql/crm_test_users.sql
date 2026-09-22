-- ============================================================
--  CRM 测试用户脚本
--  5 个角色：销售经理 / 普通销售业务员 / 客服专员 / 运营专员 / 部门主管
--  5 个用户（密码均为 admin123，BCrypt $2a$04$，强度匹配 SecurityProperties.passwordEncoderLength=4）
--  适用项目：txgycloud（txgy-module-system + txgy-module-crm）
--  使用方法：在 MySQL 中针对 txgycloud 数据库执行本脚本
-- ============================================================

-- ------------------------------------------------------------
-- 0. 兼容性设置（幂等执行：先删除可能存在的同名记录）
-- ------------------------------------------------------------
DELETE FROM `system_user_role`   WHERE `user_id` IN (250,251,252,253,254);
DELETE FROM `system_users`       WHERE `id`      IN (250,251,252,253,254);
DELETE FROM `system_role`        WHERE `id`      IN (170,171,172,173,174);
DELETE FROM `system_dept`        WHERE `id`      = 200;

-- ------------------------------------------------------------
-- 1. 部门：CRM部（id=200，挂在 深圳总公司=101 下）
-- ------------------------------------------------------------
INSERT INTO `system_dept`
  (`id`, `name`, `parent_id`, `sort`, `leader_user_id`, `phone`, `email`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (200, 'CRM部', 101, 99, NULL, NULL, 'crm@txgy.cn', 0, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ------------------------------------------------------------
-- 2. 角色（type=2 自定义，data_scope=1 全部数据）
-- ------------------------------------------------------------
INSERT INTO `system_role`
  (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (170, '销售经理',       'gubang_sales_manager',     1, 1, '', 0, 2, 'CRM测试-销售经理',       'admin', NOW(), 'admin', NOW(), b'0', 1),
  (171, '普通销售业务员', 'gubang_sales_rep',         2, 1, '', 0, 2, 'CRM测试-普通销售业务员', 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (172, '客服专员',       'gubang_customer_service',  3, 1, '', 0, 2, 'CRM测试-客服专员',       'admin', NOW(), 'admin', NOW(), b'0', 1),
  (173, '运营专员',       'gubang_operations',        4, 1, '', 0, 2, 'CRM测试-运营专员',       'admin', NOW(), 'admin', NOW(), b'0', 1),
  (174, '部门主管',       'gubang_dept_manager',      5, 1, '', 0, 2, 'CRM测试-部门主管',       'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ------------------------------------------------------------
-- 3. 用户（密码统一为 admin123；BCrypt $2a$04$；已用 Python bcrypt 实测校验通过）
-- ------------------------------------------------------------
INSERT INTO `system_users`
  (`id`, `username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`, `avatar`, `status`, `login_ip`, `login_date`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (250, 'gubang_sales_manager',    '$2a$04$j/y1FeRJDcGihdMnCYpoz.HHfgWUF9RO0tKx105m/8Y1o1G7TyEbq', '销售经理',       'CRM测试-销售经理',       200, NULL, 'gubang_sales_manager@txgy.cn',    '', 0, '', 0, '', NULL, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (251, 'gubang_sales_rep',        '$2a$04$noV.jHHQtU1EKb5yCweuhu/2p74qsv7DKw3AET5wX/0xITfL5DZIu', '普通销售业务员', 'CRM测试-普通销售业务员', 200, NULL, 'gubang_sales_rep@txgy.cn',        '', 0, '', 0, '', NULL, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (252, 'gubang_customer_service', '$2a$04$EUKCEDeiV02VRknvVMrY9ekKgjfD03FBNNrtbznegCbSa3XHFyEEu', '客服专员',       'CRM测试-客服专员',       200, NULL, 'gubang_customer_service@txgy.cn', '', 0, '', 0, '', NULL, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (253, 'gubang_operations',       '$2a$04$Ywr4VRKNoJs7VXT/aXfVM.W6KKBMR3SZaS/ouY55t5AImZ0w/ycty', '运营专员',       'CRM测试-运营专员',       200, NULL, 'gubang_operations@txgy.cn',       '', 0, '', 0, '', NULL, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (254, 'gubang_dept_manager',     '$2a$04$jLTyqmTqHitZOaao3uOv7.ovO55qvzYinkkHG8ynaU2bp3Vl0YFHq', '部门主管',       'CRM测试-部门主管',       200, NULL, 'gubang_dept_manager@txgy.cn',     '', 0, '', 0, '', NULL, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ------------------------------------------------------------
-- 4. 用户-角色关联
-- ------------------------------------------------------------
INSERT INTO `system_user_role`
  (`id`, `user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (100, 250, 170, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (101, 251, 171, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (102, 252, 172, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (103, 253, 173, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (104, 254, 174, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ============================================================
-- 说明
-- ============================================================
-- 1) 密码哈希用 Python bcrypt 库生成，强度 4（与 SecurityProperties.passwordEncoderLength 一致）。
--    Java BCryptPasswordEncoder 已实测可识别 $2a$04$ 哈希，登录密码 admin123 通过。
-- 2) 部门放在"深圳总公司"(id=101) 下新建"CRM部"(id=200)。
-- 3) 角色 type=2（自定义）、data_scope=1（全部数据权限）。
-- 4) 菜单权限未在此脚本中授予（CRM 模块菜单约 100+，逐条 INSERT 不易维护）。
--    执行本脚本后，请在 Admin 后台：
--      [系统管理 → 角色管理] → 进入 销售经理/普通销售业务员/客服专员/运营专员/部门主管
--      → 「菜单权限」 → 复制「CRM 管理员」角色的菜单分配（菜单树按需勾选 crm:* 模块）。
--      或直接授权所需的 CRM 模块按钮/接口（crm:customer:* / crm:contract:* ...）。
-- 5) 如需在同一租户(tenant_id)复用，请将上述 SQL 中所有 tenant_id 改为你目标租户编号。
-- 6) 自增编号续用库内已有序列：dept 200、role 170~174、user 250~254、user_role 100~104，
--    与现有最大编号（dept 117、role 162、user 240、user_role 83）不冲突。