-- =====================================================================
-- MES 环保一期补齐：危废台账 建表与菜单
-- （前后端代码已存在 mes_set_hazardous_waste，缺建表与菜单 SQL）
-- 说明：治污设施 mes_set_facility 的建表与菜单由 facility-carbon-20260917.sql 负责（菜单 34303/34304），
--       本文件只补危废台账，菜单号 34306 避开 34303/34304 及其按钮段 34321-34328。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < set-hazardouswaste-20260917.sql
-- =====================================================================

-- 1) 危废台账
CREATE TABLE IF NOT EXISTS `mes_set_hazardous_waste` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `manifest_no` varchar(64) DEFAULT NULL COMMENT '联单编号(国家固废系统电子联单号)',
  `waste_code` varchar(32) NOT NULL COMMENT '危废类别代码(HW49 900-041-49 等)',
  `waste_name` varchar(200) NOT NULL COMMENT '危废名称(废包装桶/废有机溶剂/漆渣/废活性炭/废机油)',
  `quantity` decimal(18,4) NOT NULL COMMENT '数量',
  `quantity_unit` varchar(16) DEFAULT 'kg' COMMENT '数量单位',
  `stage` varchar(32) DEFAULT NULL COMMENT '产生环节',
  `storage_location` varchar(200) DEFAULT NULL COMMENT '贮存地点(危废暂存间分区)',
  `counterparty` varchar(200) DEFAULT NULL COMMENT '处置/接收单位',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单ID',
  `handle_time` datetime DEFAULT NULL COMMENT '处置时间',
  `handler` varchar(64) DEFAULT NULL COMMENT '经手人',
  `status` varchar(32) DEFAULT 'GENERATED' COMMENT '状态:GENERATED已产生/STORED暂存中/TRANSFERRED已转移/CLOSED已闭环',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_waste_code` (`waste_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-危废台账';

-- 2) 菜单（防重）：34306 危废台账（父 34000 安全环保检测）
DELETE FROM system_menu WHERE id = 34306;
DELETE FROM system_role_menu WHERE menu_id = 34306;
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34306, '危废台账', 'mes:set-hazardous-waste:query', 2, 196, 34000, 'hazardousWaste', 'ep:warning', 'mes/safetyEnv/hazardousWaste/index', 'MesSetHazardousWaste', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
-- 复制"污染判定"(34301)的现有角色授权
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `role_id`, 34306, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
FROM system_role_menu WHERE `menu_id` = 34301;
