-- =====================================================================
-- MES 安全环保检测（SET）模块 - 排放合规流水 DDL
-- 表：mes_set_pollution_discharge 排放合规流水（台账 已排放 DISCHARGED 流转时自动落档）
-- 依据：《MES环保污染监控需求文档.md》§5.3/§6 建议表 mes_pollution_discharge；命名沿用 SET 新表 mes_set_* 前缀（permit/trace/sign/weigh 一致）
-- 语义：无污染中间物/废水"排放"出产线的合规留档；人工登记=台账流转弹窗填 排放去向/执行标准 触发，登记人取当前登录人
-- 说明：
--   1. 公共 7 列：creator/create_time/updater/update_time/deleted/tenant_id
--   2. 只增不改（审计流水），前端只读
--   3. 与台账以 ledger_id 关联、与追溯链以 source_record_no 关联
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-discharge-20260910-ddl.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `mes_set_pollution_discharge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ledger_id` bigint DEFAULT NULL COMMENT '来源台账编号',
  `source_record_no` varchar(64) DEFAULT NULL COMMENT '来源判定记录号(PC-...；与追溯链同键)',
  `stage` varchar(32) DEFAULT NULL COMMENT '环节（中间废弃物等）',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号（可为空，中间废弃物无批次）',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(128) DEFAULT NULL COMMENT '物料/产品名称',
  `item_spec` varchar(128) DEFAULT NULL COMMENT '规格',
  `destination` varchar(200) DEFAULT NULL COMMENT '排放去向（管线/处理站/回用渠等）',
  `standard` varchar(200) DEFAULT NULL COMMENT '执行排放标准/达标口径',
  `discharge_time` datetime DEFAULT NULL COMMENT '排放(登记)时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '登记人(创建者)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_source_record_no` (`source_record_no`),
  KEY `idx_ledger_id` (`ledger_id`),
  KEY `idx_discharge_time` (`discharge_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-排放合规流水';
