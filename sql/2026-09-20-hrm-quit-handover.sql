-- =============================================================
-- HRM 离职闭环（一）：离职交接
-- 1) hrm_employee_quit_info 增加交接字段
-- 2) 新增 hrm_employee_quit_handover_item 交接明细表
-- 幂等：可重复执行
-- =============================================================

-- ---------- 1) 离职单交接字段 ----------
SET @db := DATABASE();

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `handover_status` tinyint NOT NULL DEFAULT 0 COMMENT ''交接状态：0未开始 1进行中 2已确认 3无需交接''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'handover_status');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `handover_to_employee_id` bigint NULL COMMENT ''交接人（员工编号）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'handover_to_employee_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `handover_confirm_time` datetime NULL COMMENT ''交接确认时间''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'handover_confirm_time');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_employee_quit_info` ADD COLUMN `handover_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''交接说明（含无需交接原因）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'hrm_employee_quit_info'
      AND column_name = 'handover_remark');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2) 交接明细表 ----------
CREATE TABLE IF NOT EXISTS `hrm_employee_quit_handover_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '交接明细编号',
    `employee_id` bigint NOT NULL COMMENT '员工编号',
    `item_type` tinyint NOT NULL COMMENT '物品类型：1工器具 2劳保用品 3证件资料 4档案文件 5财务借支 6其他',
    `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物品名称',
    `quantity` decimal(12,2) DEFAULT NULL COMMENT '数量',
    `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '交接状态：0待交回 1已交回 2缺失未交回 3无需交接',
    `confirm_time` datetime DEFAULT NULL COMMENT '交回确认时间',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
    `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_hrm_quit_handover_employee` (`employee_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'HRM 员工离职交接明细表';
