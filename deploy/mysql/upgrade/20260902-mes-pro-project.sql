-- ============================================================================
-- MES「MES 系统(5100) → 项目管理」功能升级脚本
-- ----------------------------------------------------------------------------
-- 内容：
--   1. 新建项目主档表 mes_pro_project
--   2. 生产工单表 mes_pro_work_order 增加所属项目 project_id
--   3. 新增项目状态字典 mes_pro_project_status
--   4. 新增项目管理菜单（MES 系统(5100) 下顶级菜单，id 6750~6755）
-- 适用库：rpg/mes 任意含 system_menu / system_dict_* 的库（与 MES 系统 id 5100 配套）。
-- 重复执行：本脚本为幂等设计，可重复执行（已存在的表/列/数据会被跳过或覆盖重建）。
--   注意：若执行库中 system_menu 已有 id >= 6750 的菜单，请先调整本脚本菜单 id 起点。
-- ============================================================================

-- 0. 字符集
SET NAMES utf8mb4;

-- 1. 新建项目主档表 ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mes_pro_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `order_source_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '项目状态：0未开始 1进行中 2已完成 3已暂停 4已取消',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 项目管理';

-- 2. 生产工单表增加 project_id（幂等：不存在才加列） ----------------------
SET @project_col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_pro_work_order' AND COLUMN_NAME = 'project_id');
SET @project_col_ddl := IF(@project_col_exists = 0,
    'ALTER TABLE `mes_pro_work_order` ADD COLUMN `project_id` bigint NULL DEFAULT NULL COMMENT ''所属项目编号'' AFTER `order_source_code`',
    'SELECT 1');
PREPARE project_col_stmt FROM @project_col_ddl;
EXECUTE project_col_stmt;
DEALLOCATE PREPARE project_col_stmt;

-- 3. 项目状态字典 ------------------------------------------------------------
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_pro_project_status';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_pro_project_status';

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('MES 项目状态', 'mes_pro_project_status', 0, NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (0, '未开始', '0', 'mes_pro_project_status', 0, 'default', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (1, '进行中', '1', 'mes_pro_project_status', 0, 'primary', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (2, '已完成', '2', 'mes_pro_project_status', 0, 'success', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (3, '已暂停', '3', 'mes_pro_project_status', 0, 'warning', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
  (4, '已取消', '4', 'mes_pro_project_status', 0, 'info', '', NULL, '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 4. 项目管理菜单（挂载：MES 系统(5100) 下顶级菜单，id 6750~6755） ----------
-- 先清理可能存在的旧菜单（按名称定位，避免误删）
DELETE FROM `system_menu` WHERE `name` = '项目管理' AND `parent_id` = 5100;
DELETE FROM `system_menu` WHERE `parent_id` BETWEEN 6750 AND 6755;
DELETE FROM `system_menu` WHERE `id` BETWEEN 6750 AND 6755;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(6750, '项目管理', '', 2, 55, 5100, 'project', 'ep:flag', 'mes/pro/project/index', 'MesProProject', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(6751, '项目管理查询', 'mes:pro-project:query', 3, 1, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6752, '项目管理创建', 'mes:pro-project:create', 3, 2, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6753, '项目管理更新', 'mes:pro-project:update', 3, 3, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6754, '项目管理删除', 'mes:pro-project:delete', 3, 4, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6755, '项目管理导出', 'mes:pro-project:export', 3, 5, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');
