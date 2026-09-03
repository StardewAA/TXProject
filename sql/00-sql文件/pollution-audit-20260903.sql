-- =====================================================================
-- MES 环保污染监控「全链路有迹可循」审计 DDL
-- 1) mes_pollution_check_log  判定履历：CREATE/UPDATE(每次AI重筛)/REVIEW 各留一条快照
-- 2) mes_pollution_ledger_log 台账流转历史：登记/处置中/终态/自动解除(CLEARED)每步留痕
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-audit-20260903.sql
-- =====================================================================

-- 1) 污染判定履历（一条判定 = 从创建到复核的完整操作时间线；每行是"操作后"的 AI/人工 结果快照）
CREATE TABLE IF NOT EXISTS `mes_pollution_check_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `check_id` bigint NOT NULL COMMENT '判定记录ID(mes_set_pollution_check.id)',
  `record_no` varchar(64) DEFAULT NULL COMMENT '判定记录编号(PC-...)',
  `op_type` varchar(32) NOT NULL COMMENT '操作类型：CREATE(创建+AI初筛)/UPDATE(改单+AI重筛)/REVIEW(人工复核终态)',
  `item_name` varchar(200) DEFAULT NULL COMMENT '当时的物料/产品名称',
  `ai_result` varchar(32) DEFAULT NULL COMMENT '当时 AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN',
  `ai_confidence` int DEFAULT NULL COMMENT '当时 AI 置信度(%)',
  `ai_reason` varchar(500) DEFAULT NULL COMMENT '当时 AI 判定依据',
  `suggested_storage` varchar(500) DEFAULT NULL COMMENT '当时 AI 推荐存储方法',
  `review_result` varchar(32) DEFAULT NULL COMMENT '人工复核结果(仅REVIEW行)：CLEAN/POLLUTED',
  `storage_method` varchar(200) DEFAULT NULL COMMENT '最终存储方法',
  `disposition` varchar(32) DEFAULT NULL COMMENT '处置方式',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `marked` bit(1) DEFAULT NULL COMMENT '是否标记',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注/复核备注',
  `op_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `op_time` datetime DEFAULT NULL COMMENT '操作时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_check_id` (`check_id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-污染判定履历(操作+AI/人工快照)';

-- 2) 台账流转历史（每步流转一行；登记 STORED、人工流转、CLEAN 自动解除 CLEARED 均留痕）
CREATE TABLE IF NOT EXISTS `mes_pollution_ledger_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ledger_id` bigint NOT NULL COMMENT '台账ID(mes_pollution_ledger.id)',
  `source_record_no` varchar(64) DEFAULT NULL COMMENT '来源判定记录编号(PC-...)',
  `from_status` varchar(32) DEFAULT NULL COMMENT '原状态(空=初始登记)',
  `to_status` varchar(32) NOT NULL COMMENT '新状态：STORED/PROCESSING/REUSED/DISCHARGED/DISPOSED/CLEARED',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人(自动流转填复核人)',
  `op_time` datetime DEFAULT NULL COMMENT '流转时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '流转说明/备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ledger_id` (`ledger_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-污染/危废暂存台账-流转历史';

-- 台账状态列注释补 CLEARED（无污染复核自动解除，非人工流转目标）
ALTER TABLE `mes_pollution_ledger`
  MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'STORED' COMMENT '台账状态：STORED(暂存)/PROCESSING(处置中)/REUSED(已回用)/DISCHARGED(已排放)/DISPOSED(已处置)/CLEARED(已解除)';
