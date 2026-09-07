-- ============================================================================
-- MES「项目管理」P1 阶段：项目附件 升级脚本
-- ----------------------------------------------------------------------------
-- 内容：
--   1. 新建项目附件表 mes_pro_project_file（只存关联，文件实体由 infra 文件服务管理）
--   2. 新增附件权限菜单（项目管理 6750 下，id 6765~6768）
--   3. 角色授权：把新增权限点授权给「已拥有项目管理(6750)」的角色
-- 适用库：rpg/mes 任意含 system_menu / system_role_menu 的库。
-- 重复执行：本脚本为幂等设计，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 项目附件表 -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mes_pro_project_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `file_id` bigint NULL DEFAULT NULL COMMENT 'infra 文件编号',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '文件名称',
  `file_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '文件访问地址',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小（字节）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_project_file_project_id` (`project_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 项目附件';

-- 2. 附件权限菜单（挂载：项目管理 6750 下，id 6765~6768） -------------------
DELETE FROM `system_menu` WHERE `id` BETWEEN 6765 AND 6768;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(6765, '项目附件查询', 'mes:pro-project-file:query',  3, 15, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6766, '项目附件创建', 'mes:pro-project-file:create', 3, 16, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6767, '项目附件更新', 'mes:pro-project-file:update', 3, 17, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'),
(6768, '项目附件删除', 'mes:pro-project-file:delete', 3, 18, 6750, '', '', '', '', 0, b'1', b'1', b'1', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 3. 角色授权：附件权限点（对齐「已拥有项目管理 6750」的角色集合） ---------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6765 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6765);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6766 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6766);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6767 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6767);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, 6768 FROM `system_role_menu` r
WHERE r.menu_id = 6750
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM `system_role_menu`) x WHERE x.role_id = r.role_id AND x.menu_id = 6768);
