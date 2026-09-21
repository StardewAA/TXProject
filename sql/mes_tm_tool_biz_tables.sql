-- =============================================
-- MES 工具管理 - 领用/归还/维修/报废 单据表
-- =============================================

-- ----------------------------
-- Table structure for mes_tm_tool_issue (工具领用单)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_issue`;
CREATE TABLE `mes_tm_tool_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领用单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领用单名称',
  `workstation_id` bigint NULL DEFAULT NULL COMMENT '工位ID',
  `issue_date` datetime NULL DEFAULT NULL COMMENT '领用日期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0草稿 1已确认）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具领用单';

-- ----------------------------
-- Table structure for mes_tm_tool_issue_line (工具领用单行)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_issue_line`;
CREATE TABLE `mes_tm_tool_issue_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '领用单ID',
  `tool_id` bigint NOT NULL COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具编码',
  `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具名称',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '领用数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具领用单行';

-- ----------------------------
-- Table structure for mes_tm_tool_return (工具归还单)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_return`;
CREATE TABLE `mes_tm_tool_return` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归还单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归还单名称',
  `workstation_id` bigint NULL DEFAULT NULL COMMENT '工位ID',
  `return_date` datetime NULL DEFAULT NULL COMMENT '归还日期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0草稿 1已确认）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具归还单';

-- ----------------------------
-- Table structure for mes_tm_tool_return_line (工具归还单行)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_return_line`;
CREATE TABLE `mes_tm_tool_return_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `return_id` bigint NOT NULL COMMENT '归还单ID',
  `tool_id` bigint NOT NULL COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具编码',
  `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具名称',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '归还数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具归还单行';

-- ----------------------------
-- Table structure for mes_tm_tool_repair (工具维修单)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_repair`;
CREATE TABLE `mes_tm_tool_repair` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修单名称',
  `repair_date` datetime NULL DEFAULT NULL COMMENT '维修日期',
  `finish_date` datetime NULL DEFAULT NULL COMMENT '完成日期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0草稿 1已确认/维修中 2维修完成）',
  `repair_result` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '维修结果',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具维修单';

-- ----------------------------
-- Table structure for mes_tm_tool_repair_line (工具维修单行)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_repair_line`;
CREATE TABLE `mes_tm_tool_repair_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `repair_id` bigint NOT NULL COMMENT '维修单ID',
  `tool_id` bigint NOT NULL COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具编码',
  `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具名称',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '维修数量',
  `repair_result` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '维修结果',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具维修单行';

-- ----------------------------
-- Table structure for mes_tm_tool_scrap (工具报废单)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_scrap`;
CREATE TABLE `mes_tm_tool_scrap` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报废单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报废单名称',
  `scrap_date` datetime NULL DEFAULT NULL COMMENT '报废日期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0草稿 1已确认）',
  `scrap_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报废原因',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具报废单';

-- ----------------------------
-- Table structure for mes_tm_tool_scrap_line (工具报废单行)
-- ----------------------------
DROP TABLE IF EXISTS `mes_tm_tool_scrap_line`;
CREATE TABLE `mes_tm_tool_scrap_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scrap_id` bigint NOT NULL COMMENT '报废单ID',
  `tool_id` bigint NOT NULL COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具编码',
  `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具名称',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '报废数量',
  `scrap_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报废原因',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 工具报废单行';

-- =============================================
-- 字典类型 + 字典数据（工具状态、保养类型、4种单据状态）
-- =============================================

-- --- 工具领用单状态 ---
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES ('MES 工具领用单状态', 'mes_tm_tool_issue_status', 0, '', '1', NOW(), '1', NOW(), b'0', NULL);
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (0, '草稿', '0', 'mes_tm_tool_issue_status', 0, 'info', '', '', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (1, '已确认', '1', 'mes_tm_tool_issue_status', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0');

-- --- 工具状态（幂等：先清后插，可重复执行） ---
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_tm_tool_status';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_tm_tool_status';
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES ('MES 工具状态', 'mes_tm_tool_status', 0, '', '1', NOW(), '1', NOW(), b'0', NULL);
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, '在库', '1', 'mes_tm_tool_status', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0'),
  (2, '领用中', '2', 'mes_tm_tool_status', 0, 'warning', '', '', '1', NOW(), '1', NOW(), b'0'),
  (3, '维修中', '3', 'mes_tm_tool_status', 0, 'info', '', '', '1', NOW(), '1', NOW(), b'0'),
  (4, '报废', '4', 'mes_tm_tool_status', 0, 'danger', '', '', '1', NOW(), '1', NOW(), b'0');

-- --- 保养维护类型 ---
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_tm_mainten_type';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_tm_mainten_type';
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES ('MES 保养维护类型', 'mes_tm_mainten_type', 0, '', '1', NOW(), '1', NOW(), b'0', NULL);
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, '定期维护', '1', 'mes_tm_mainten_type', 0, 'primary', '', '', '1', NOW(), '1', NOW(), b'0'),
  (2, '按使用次数维护', '2', 'mes_tm_mainten_type', 0, 'primary', '', '', '1', NOW(), '1', NOW(), b'0');

-- --- 工具归还单状态 ---
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_tm_tool_return_status';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_tm_tool_return_status';
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES ('MES 工具归还单状态', 'mes_tm_tool_return_status', 0, '', '1', NOW(), '1', NOW(), b'0', NULL);
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (0, '草稿', '0', 'mes_tm_tool_return_status', 0, 'info', '', '', '1', NOW(), '1', NOW(), b'0'),
  (1, '已确认', '1', 'mes_tm_tool_return_status', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0');

-- --- 工具维修单状态 ---
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_tm_tool_repair_status';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_tm_tool_repair_status';
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES ('MES 工具维修单状态', 'mes_tm_tool_repair_status', 0, '', '1', NOW(), '1', NOW(), b'0', NULL);
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (0, '草稿', '0', 'mes_tm_tool_repair_status', 0, 'info', '', '', '1', NOW(), '1', NOW(), b'0'),
  (1, '已确认', '1', 'mes_tm_tool_repair_status', 0, 'warning', '', '', '1', NOW(), '1', NOW(), b'0'),
  (2, '维修完成', '2', 'mes_tm_tool_repair_status', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0');

-- --- 工具报废单状态 ---
DELETE FROM `system_dict_data` WHERE `dict_type` = 'mes_tm_tool_scrap_status';
DELETE FROM `system_dict_type` WHERE `type` = 'mes_tm_tool_scrap_status';
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES ('MES 工具报废单状态', 'mes_tm_tool_scrap_status', 0, '', '1', NOW(), '1', NOW(), b'0', NULL);
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (0, '草稿', '0', 'mes_tm_tool_scrap_status', 0, 'info', '', '', '1', NOW(), '1', NOW(), b'0'),
  (1, '已确认', '1', 'mes_tm_tool_scrap_status', 0, 'success', '', '', '1', NOW(), '1', NOW(), b'0');
