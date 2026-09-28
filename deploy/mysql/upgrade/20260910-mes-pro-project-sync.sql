-- ============================================================================
-- MES「项目管理」拓展：销售订单审批通过 → 自动生成生产项目 升级脚本
-- ----------------------------------------------------------------------------
-- 内容：
--   1. 新建项目同步任务表 mes_pro_project_sync_task（BPM 事件先落表，定时任务异步消费）
--   2. mes_pro_project 增加 (source_type, order_source_code) 唯一索引（幂等兜底）
-- 注意：
--   * 唯一索引建立前请先检查历史数据是否有重复来源单据（如下查询应返回空）：
--       SELECT source_type, order_source_code, COUNT(*) FROM mes_pro_project
--       WHERE deleted = 0 AND order_source_code IS NOT NULL AND order_source_code <> ''
--       GROUP BY source_type, order_source_code HAVING COUNT(*) > 1;
--   * 软删除的项目同样占用唯一索引（deleted=1 也参与约束），如需重建项目请物理清理对应软删记录。
-- 适用库：rpg/mes 任意含 mes_pro_project 的库。
-- 重复执行：本脚本为幂等设计，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 项目同步任务表 ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mes_pro_project_sync_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `sale_order_id` bigint NOT NULL COMMENT 'ERP 销售订单编号（幂等键）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '任务状态：0 待处理、1 成功、2 失败、3 跳过',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` datetime NULL DEFAULT NULL COMMENT '下次重试时间',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '失败原因',
  `project_id` bigint NULL DEFAULT NULL COMMENT '生成的项目编号（成功后回填）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_sync_task_sale_order_id` (`sale_order_id`) USING BTREE,
  KEY `idx_sync_task_status_retry` (`status`, `next_retry_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES 项目同步任务';

-- 2. 项目表来源单据唯一索引（幂等兜底） --------------------------------------
SET @schema_name = DATABASE();
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'mes_pro_project'
      AND index_name = 'uk_pro_project_source');
SET @sql = IF(@index_exists = 0,
    'ALTER TABLE `mes_pro_project` ADD UNIQUE INDEX `uk_pro_project_source` (`source_type`, `order_source_code`)',
    'SELECT ''索引已存在，跳过'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
