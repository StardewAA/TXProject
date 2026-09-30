-- =====================================================================
-- MES 安全环保检测（SET）- 污染物超标事件
-- 表：mes_set_monitor_exceed
-- 说明：
--   1. 库内原本没有任何表能承载「超标事件 + 派单/闭环状态」：
--      mes_set_pollution_discharge 是排污去向登记（无状态机、无处置人），
--      mes_set_emergency_event 是应急事件（语义不符）。
--   2. 本表只存**判定结果与处置状态**，读数原文仍在 mes_set_exhaust_gas /
--      mes_set_wastewater —— 不复制浓度，避免两处不同步。
--   3. 「同一小时同一因子只记 1 条」= 口径里的「同一小时连续超标计 1 次」。
--      该约束在 Service 层做幂等 upsert，**不建 UNIQUE**：
--      本仓已统一移除业务唯一键（见 drop-code-unique-20260910.sql），
--      且无租户维度的唯一键跨租户会撞。
-- 处置轨迹复用 mes_set_trace_chain（trace_type = biz_type = 'MONITOR'，与 LEDGER/EMERGENCY 同惯例），不另建表。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < monitor-20260921-ddl.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `mes_set_monitor_exceed` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `outlet_id` bigint NOT NULL COMMENT '排放口编号(mes_set_emission_outlet.id)',
  `outlet_code` varchar(64) NOT NULL COMMENT '排放口编号快照(排口改名/停用后历史仍可读)',
  `outlet_name` varchar(200) NOT NULL COMMENT '排放口名称快照',
  `pollutant_code` varchar(32) NOT NULL COMMENT '监控因子编码(与读数表 pollutant_code 同域)',
  `pollutant_name` varchar(64) NOT NULL COMMENT '监控因子名称',
  `monitor_value` decimal(12,3) NOT NULL COMMENT '小时均值',
  `limit_value` decimal(12,3) NOT NULL COMMENT '限值(取自排放口 permit_limits)',
  `unit` varchar(16) DEFAULT '' COMMENT '单位 mg/m3 或 mg/L',
  `multiple` decimal(8,3) NOT NULL COMMENT '超标倍数 = 小时均值 / 限值',
  `occur_time` datetime NOT NULL COMMENT '超标发生时间(取整到小时，即小时起点)',
  `duration_min` int NOT NULL DEFAULT 0 COMMENT '持续时长(分钟)，同因子连续超标小时数 x 60',
  `handle_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '处置状态：PENDING待处置/PROCESSING处置中/CLOSED已闭环',
  `handler` varchar(64) DEFAULT '' COMMENT '处置人(派单时按当前登录人回填，前端不猜)',
  `dispatch_time` datetime DEFAULT NULL COMMENT '派单时间',
  `closed_time` datetime DEFAULT NULL COMMENT '闭环时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_outlet_time` (`outlet_id`, `occur_time`),
  KEY `idx_occur_time` (`occur_time`),
  KEY `idx_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SET-污染物超标事件';

-- 读数表原本只有主键，在线监测按 (outlet_id, monitor_time) 区间取数会全表扫
ALTER TABLE `mes_set_exhaust_gas` ADD KEY `idx_outlet_time` (`outlet_id`, `monitor_time`);
ALTER TABLE `mes_set_wastewater` ADD KEY `idx_outlet_time` (`outlet_id`, `monitor_time`);

SELECT '超标事件表' k, COUNT(*) v FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_set_monitor_exceed'
UNION ALL SELECT '废气索引', COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_set_exhaust_gas' AND INDEX_NAME = 'idx_outlet_time'
UNION ALL SELECT '废水索引', COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_set_wastewater' AND INDEX_NAME = 'idx_outlet_time';
