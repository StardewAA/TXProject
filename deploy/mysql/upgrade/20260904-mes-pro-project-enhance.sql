-- ============================================================================
-- MES「项目管理」增强升级脚本（P0 基础增强）
-- ----------------------------------------------------------------------------
-- 内容：
--   1. mes_pro_project 表增加字段：优先级 / 负责人 / 责任部门 / 计划起止时间 / 实际起止时间
--   2. 新增项目优先级字典 mes_pro_project_priority（1紧急 2高 3中 4低）
--   3. 新增「项目管理导入」菜单权限（id 6756，权限 mes:pro-project:import）
-- 适用库：rpg/mes 任意含 system_menu / system_dict_* 的库。
-- 重复执行：本脚本为幂等设计，已存在的列/字典/菜单会被跳过或覆盖重建。
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 幂等加列 ---------------------------------------------------------------
SET @db = DATABASE();
SET @tbl = 'mes_pro_project';

-- 1.1 优先级
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'priority');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN priority tinyint NOT NULL DEFAULT 0 COMMENT '项目优先级：1紧急 2高 3中 4低'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.2 负责人
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'owner');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN owner varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '负责人'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.3 责任部门
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'dept');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN dept varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '责任部门'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.4 计划开始时间
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'plan_start_time');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN plan_start_time datetime NULL DEFAULT NULL COMMENT '计划开始时间'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.5 计划结束时间
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'plan_end_time');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN plan_end_time datetime NULL DEFAULT NULL COMMENT '计划结束时间'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.6 实际开始时间
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'actual_start_time');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN actual_start_time datetime NULL DEFAULT NULL COMMENT '实际开始时间'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.7 实际结束时间
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'actual_end_time');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN actual_end_time datetime NULL DEFAULT NULL COMMENT '实际结束时间'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.8 工期天数（由计划开始/结束时间自动计算）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'duration_days');
SET @ddl := IF(@c = 0,
    "ALTER TABLE mes_pro_project ADD COLUMN duration_days int NULL DEFAULT NULL COMMENT '工期天数（由计划开始/结束时间自动计算）'",
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 项目优先级字典 ----------------------------------------------------------
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_pro_project_priority';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_pro_project_priority';

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('MES 项目优先级', 'mes_pro_project_priority', 0, NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, '紧急', '1', 'mes_pro_project_priority', 0, 'danger', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (2, '高',   '2', 'mes_pro_project_priority', 0, 'warning', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (3, '中',   '3', 'mes_pro_project_priority', 0, 'primary', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (4, '低',   '4', 'mes_pro_project_priority', 0, 'info',    '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 3. 导入权限菜单（挂载：项目管理 6750 下，id 6756） -------------------------
DELETE FROM `system_menu` WHERE `id` = 6756;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(6756, '项目管理导入', 'mes:pro-project:import', 3, 6, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 4. 项目成员表 mes_pro_project_member --------------------------------------
CREATE TABLE IF NOT EXISTS `mes_pro_project_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `user_id` bigint NULL DEFAULT NULL COMMENT '成员用户编号',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '成员姓名',
  `role` tinyint NULL DEFAULT NULL COMMENT '项目角色：1项目经理 2生产负责 3技术负责 4质检负责 5其他',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 项目成员';

-- 5. 项目成员角色字典 --------------------------------------------------------
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_pro_project_member_role';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_pro_project_member_role';

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('MES 项目成员角色', 'mes_pro_project_member_role', 0, NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, '项目经理', '1', 'mes_pro_project_member_role', 0, 'danger',  '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (2, '生产负责', '2', 'mes_pro_project_member_role', 0, 'primary', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (3, '技术负责', '3', 'mes_pro_project_member_role', 0, 'success', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (4, '质检负责', '4', 'mes_pro_project_member_role', 0, 'warning', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (5, '其他',     '5', 'mes_pro_project_member_role', 0, 'info',    '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 6. 项目成员权限菜单（挂载：项目管理 6750 下，id 6757~6760） ----------------
DELETE FROM `system_menu` WHERE `id` BETWEEN 6757 AND 6760;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(6757, '项目成员查询', 'mes:pro-project-member:query',  3, 7, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6758, '项目成员创建', 'mes:pro-project-member:create', 3, 8, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6759, '项目成员更新', 'mes:pro-project-member:update', 3, 9, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6760, '项目成员删除', 'mes:pro-project-member:delete', 3, 10, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');
