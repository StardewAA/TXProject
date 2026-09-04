-- =====================================================================
-- MES 环保污染监控 P2「真落地管控」DDL
-- 1) mes_wm_batch 加环保污染戳 4 列（A1）
-- 2) 新建 mes_pollution_ledger 污染/危废暂存台账（A2，需求§6）
-- 3) 台账菜单 34302（兄弟于 34301 污染判定，父 34000）+ 复制 34301 的角色授权
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-landing-20260903.sql
-- =====================================================================

-- 1) 批次环保戳列（仅 POLLUTED 触发门禁；NULL/空=未判定可正常流转）
ALTER TABLE `mes_wm_batch`
  ADD COLUMN `pollution_status` varchar(20) DEFAULT NULL COMMENT '环保污染状态：CLEAN(无污染)/POLLUTED(有污染受控)；空=未判定',
  ADD COLUMN `pollution_location` varchar(255) DEFAULT NULL COMMENT '环保去向/库位快照',
  ADD COLUMN `pollution_marked` bit(1) DEFAULT NULL COMMENT '环保标记（有污染/需管控）',
  ADD COLUMN `pollution_src_record` varchar(64) DEFAULT NULL COMMENT '源污染判定记录编号(PC-...)';

-- 2) 污染/危废暂存台账
CREATE TABLE IF NOT EXISTS `mes_pollution_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `source_check_id` bigint NOT NULL COMMENT '来源判定记录ID(mes_set_pollution_check.id)',
  `source_record_no` varchar(64) NOT NULL COMMENT '来源判定记录编号(PC-...)',
  `stage` varchar(32) DEFAULT NULL COMMENT '环节',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联单号',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(200) DEFAULT NULL COMMENT '物料/产品名称',
  `item_spec` varchar(200) DEFAULT NULL COMMENT '规格',
  `disposition` varchar(32) DEFAULT NULL COMMENT '处置方式',
  `storage_method` varchar(200) DEFAULT NULL COMMENT '最终存储方法',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `marked` bit(1) DEFAULT NULL COMMENT '是否标记',
  `status` varchar(32) NOT NULL DEFAULT 'STORED' COMMENT '台账状态：STORED(暂存)/PROCESSING(处置中)/REUSED(已回用)/DISCHARGED(已排放)/DISPOSED(已处置)',
  `status_by` varchar(64) DEFAULT NULL COMMENT '最近流转人',
  `status_time` datetime DEFAULT NULL COMMENT '最近流转时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_source_check_id` (`source_check_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-污染/危废暂存台账';
-- 台账唯一约束：一条复核为有污染的判定记录至多一条台账（R5）
CREATE UNIQUE INDEX uk_ledger_source_check ON `mes_pollution_ledger` (`source_check_id`);

-- 3) 台账菜单（防重）
DELETE FROM system_menu WHERE id = 34302;
DELETE FROM system_role_menu WHERE menu_id = 34302;
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34302, '污染暂存台账', '', 2, 191, 34000, 'pollutionLedger', 'ep:box', 'mes/safetyEnv/pollutionLedger/index', 'MesPollutionLedger', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
-- 复制"污染判定"(34301)的现有角色授权到台账菜单（按钮权限复用 mes:set-pollution-check:query/review）
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `role_id`, 34302, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
FROM system_role_menu WHERE `menu_id` = 34301;
