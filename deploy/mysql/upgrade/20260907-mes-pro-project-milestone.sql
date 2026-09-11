-- ============================================================================
-- MES「项目管理」P1 阶段：里程碑/阶段管理 升级脚本
-- ----------------------------------------------------------------------------
-- 内容：
--   1. 新建项目里程碑表 mes_pro_project_milestone
--   2. 新增里程碑状态字典 mes_pro_project_milestone_status
--   3. 新增里程碑权限菜单（项目管理 6750 下，id 6761~6764）
--   4. 角色授权：把新增权限点授权给「已拥有项目管理(6750)」的角色
--   5. 补授权：P0 遗留的 6756~6760 权限点（幂等，已授权则跳过）
-- 适用库：rpg/mes 任意含 system_menu / system_role_menu 的库。
-- 重复执行：本脚本为幂等设计，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 项目里程碑表 -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mes_pro_project_milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '里程碑名称',
  `plan_date` datetime NULL DEFAULT NULL COMMENT '计划完成日期',
  `actual_date` datetime NULL DEFAULT NULL COMMENT '实际完成日期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '里程碑状态：0未开始 1进行中 2已完成',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_milestone_project_id` (`project_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 项目里程碑';

-- 2. 里程碑状态字典 ----------------------------------------------------------
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_pro_project_milestone_status';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_pro_project_milestone_status';

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('MES 项目里程碑状态', 'mes_pro_project_milestone_status', 0, NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (0, '未开始', '0', 'mes_pro_project_milestone_status', 0, 'default', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (1, '进行中', '1', 'mes_pro_project_milestone_status', 0, 'processing', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (2, '已完成', '2', 'mes_pro_project_milestone_status', 0, 'success', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 3. 里程碑权限菜单（挂载：项目管理 6750 下，id 6761~6764） -----------------
DELETE FROM `system_menu` WHERE `id` BETWEEN 6761 AND 6764;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(6761, '项目里程碑查询', 'mes:pro-project-milestone:query',  3, 11, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6762, '项目里程碑创建', 'mes:pro-project-milestone:create', 3, 12, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6763, '项目里程碑更新', 'mes:pro-project-milestone:update', 3, 13, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6764, '项目里程碑删除', 'mes:pro-project-milestone:delete', 3, 14, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 4. 角色授权：里程碑权限点（对齐「已拥有项目管理 6750」的角色集合） -------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6761 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6761);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6762 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6762);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6763 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6763);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6764 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6764);

-- 5. 补授权：P0 遗留的 6756~6760（导入 + 项目成员，幂等） -------------------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6756 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6756);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6757 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6757);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6758 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6758);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6759 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6759);
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6760 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6760);
