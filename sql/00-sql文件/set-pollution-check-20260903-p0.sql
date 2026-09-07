-- =====================================================================
-- MES 安全环保检测（SET）模块 - 环保污染监控 P0 单表 DDL
-- 表：mes_set_pollution_check 污染判定记录
-- 依据：《MES环保污染监控需求文档.md》§4.1~4.4 四环节（采购入库/生产领用/中间废弃物/成品）
-- 规则：AI 初筛(mock)三值 CLEAN/POLLUTED/UNCERTAIN；人工复核收口二值 CLEAN/POLLUTED，
--       review_result 为空 = 待复核（仅待复核可编辑/删除/复核）
-- 说明：
--   1. 公共 7 列：creator/create_time/updater/update_time/deleted/tenant_id
--   2. record_no 记录编号 PC-yyyyMMddHHmmss+3位随机，唯一
--   3. 枚举用 VARCHAR 代码存储（与 SET 其余表一致，避免字典骨架）
-- 执行：mysql -uroot -proot ruoyi-vue-pro < set-pollution-check-20260903-p0.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `mes_set_pollution_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号(PC-yyyyMMddHHmmss+随机)',
  `stage` varchar(32) NOT NULL COMMENT '环节：PURCHASE_INBOUND(采购入库)/MATERIAL_ISSUE(生产领用)/WASTE_INTERMEDIATE(中间废弃物)/FINISHED_PRODUCT(成品)',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联单号（入库单/领料单/报工单）',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(200) NOT NULL COMMENT '物料/产品名称（AI 据此初筛）',
  `item_spec` varchar(200) DEFAULT NULL COMMENT '规格',
  `ai_result` varchar(16) DEFAULT NULL COMMENT 'AI 初筛结果：CLEAN(无污染)/POLLUTED(有污染)/UNCERTAIN(不确定)',
  `ai_confidence` int DEFAULT NULL COMMENT 'AI 置信度(0-100)',
  `ai_reason` varchar(1000) DEFAULT NULL COMMENT 'AI 判定依据',
  `suggested_storage` varchar(200) DEFAULT NULL COMMENT 'AI 推荐存储方法',
  `review_result` varchar(16) DEFAULT NULL COMMENT '人工复核结果：CLEAN/POLLUTED；空=待复核',
  `review_by` varchar(64) DEFAULT NULL COMMENT '复核人',
  `review_time` datetime DEFAULT NULL COMMENT '复核时间',
  `disposition` varchar(32) DEFAULT NULL COMMENT '处置方式：NORMAL_INBOUND/CONTROLLED_STORAGE/ISSUE_ALLOWED/REJECT_ISSUE/REUSE/DISCHARGE/ISOLATE_STORAGE/MARKED_STORAGE',
  `storage_method` varchar(200) DEFAULT NULL COMMENT '最终存储方法',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `marked` bit(1) DEFAULT NULL COMMENT '是否标记（有污染默认标记）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-环保污染判定记录';
