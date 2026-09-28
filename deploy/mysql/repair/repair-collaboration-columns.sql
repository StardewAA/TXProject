-- ERP 供应链协同模块一次性修复脚本（幂等，可重复执行）
-- 修复内容：
-- 1. erp_wms_collaboration_b / _item / erp_wms_integration_record / erp_collaboration_confirm_log
--    四张表的 creator/updater 列改为允许 NULL DEFAULT ''
--    原因：RPC 接口无登录用户，MyBatis 自动填充 creator=null，NOT NULL 约束会报错
-- 2. 补齐库存预警所需字段；脚本幂等，可在不同企业环境重复执行

SET NAMES utf8mb4;

-- ============ 1. 协同表 creator/updater 允许 NULL ============
-- 已在 DDL 文件中永久修复，此处为对已存在的数据库做补偿性 ALTER

-- erp_wms_integration_record
ALTER TABLE `erp_wms_integration_record`
  MODIFY COLUMN `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者';

-- erp_wms_collaboration_b
ALTER TABLE `erp_wms_collaboration_b`
  MODIFY COLUMN `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者';

-- erp_wms_collaboration_b_item
ALTER TABLE `erp_wms_collaboration_b_item`
  MODIFY COLUMN `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者';

-- erp_collaboration_confirm_log（v0_3 创建的表）
ALTER TABLE `erp_collaboration_confirm_log`
  MODIFY COLUMN `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者';

SELECT '协同表 creator/updater 列已改为允许 NULL' AS result;

-- ============ 2. 库存预警功能（2026-09）一次性建列/放宽 ============
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'erp_product' AND COLUMN_NAME = 'safety_stock') = 0,
  'ALTER TABLE `erp_product` ADD COLUMN `safety_stock` decimal(20, 2) NULL COMMENT ''安全库存，低于此值触发不足预警''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'erp_product' AND COLUMN_NAME = 'critical_stock') = 0,
  'ALTER TABLE `erp_product` ADD COLUMN `critical_stock` decimal(20, 2) NULL COMMENT ''临界库存，低于此值触发严重不足预警''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'erp_product' AND COLUMN_NAME = 'standard_purchase_quantity') = 0,
  'ALTER TABLE `erp_product` ADD COLUMN `standard_purchase_quantity` decimal(20, 2) NULL COMMENT ''标准进货数量（每次采购常规批量，阈值默认按其50%/30%）''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'erp_wms_collaboration_b' AND COLUMN_NAME = 'severity') = 0,
  'ALTER TABLE `erp_wms_collaboration_b` ADD COLUMN `severity` varchar(16) NULL COMMENT ''库存预警等级：INSUFFICIENT/CRITICAL''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ERP 自生成的库存预警不来自生产工单，该列必须允许 NULL
ALTER TABLE `erp_wms_collaboration_b`
  MODIFY COLUMN `source_production_order_no` varchar(64) NULL COMMENT '来源生产工单号（库存预警可空）';

-- 库存预警幂等键较长（前缀+UUID），放宽到 128
ALTER TABLE `erp_wms_integration_record`
  MODIFY COLUMN `request_id` varchar(128) NOT NULL COMMENT '幂等请求编号';

SELECT '库存预警相关列已就绪' AS result;
