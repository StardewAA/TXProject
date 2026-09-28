-- =====================================================================
-- MES 安全环保检测（SET）模块 - 危废链 DDL
-- 依据：《危险品全生命周期管理-优化设计文档-2026-09-09.md》§8 危废链 / 《MES环保模块质优化设计-最终版》§四-7 启运出厂
-- 命名：沿用本仓 SET 新表 mes_set_* 前缀（设计稿原文写 mes_dg_manifest，此处按仓库既有约定改名）
--
--   1) 新表 mes_set_hazwaste_manifest —— 国家固废管理信息系统「电子转移联单」
--      五方角色（产生/运输/接收/贮存/处置）+ 各自确认时间 + 申报时限，一单一号。
--      生效判定：已申报 + 运输/接收两方已确认（见 MesSetHazwasteServiceImpl#validateEffective）。
--   2) 扩列 mes_set_hazardous_waste —— 危废台账补「一桶一码」容器码 + HJ1276 标签归档 URL。
--      该表此前是空壳（无 DO/Service/菜单），本片同时把它落成模块。
--
-- 说明：公共 7 列 creator/create_time/updater/update_time/deleted/tenant_id；签字复用既有 mes_set_sign_record。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < hazwaste-chain-20260910-ddl.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `mes_set_hazwaste_manifest` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `manifest_no` varchar(64) NOT NULL COMMENT '国家固废系统电子转移联单号',
  `waste_code` varchar(64) DEFAULT NULL COMMENT '危险废物类别代码(HW08/HW49等)',
  `waste_name` varchar(200) DEFAULT NULL COMMENT '危险废物名称',
  `quantity` decimal(12,3) DEFAULT NULL COMMENT '申报转移量',
  `quantity_unit` varchar(16) DEFAULT '吨' COMMENT '数量单位',
  `generate_unit` varchar(200) DEFAULT NULL COMMENT '产生单位(五方之一)',
  `carrier_unit` varchar(200) DEFAULT NULL COMMENT '运输单位',
  `receive_unit` varchar(200) DEFAULT NULL COMMENT '接收单位',
  `storage_unit` varchar(200) DEFAULT NULL COMMENT '贮存单位',
  `dispose_unit` varchar(200) DEFAULT NULL COMMENT '处置单位',
  `carrier_confirm_time` datetime DEFAULT NULL COMMENT '运输方确认时间',
  `receive_confirm_time` datetime DEFAULT NULL COMMENT '接收方确认时间',
  `storage_confirm_time` datetime DEFAULT NULL COMMENT '贮存方确认时间',
  `dispose_confirm_time` datetime DEFAULT NULL COMMENT '处置方确认时间',
  `declared_time` datetime DEFAULT NULL COMMENT '产生方申报时间',
  `declare_deadline` datetime DEFAULT NULL COMMENT '申报/确认时限(国家平台倒排提醒基准)',
  `vehicle_no` varchar(32) DEFAULT NULL COMMENT '运输车牌号(门卫扫牌核对)',
  `net_weight` decimal(12,3) DEFAULT NULL COMMENT '地磅净重(吨)',
  `transfer_time` datetime DEFAULT NULL COMMENT '启运出厂时间',
  `gate_release_time` datetime DEFAULT NULL COMMENT '门卫放行时间',
  `gate_guard` varchar(64) DEFAULT NULL COMMENT '放行门卫',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/DECLARED已申报/EFFECTIVE已生效/TRANSFERRED已出厂/CLOSED已回执归档',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  -- 联单号刻意不建唯一键：查重交给服务层（createManifest/updateManifest 已按 deleted=0 查重并返回业务码）。
  -- 理由见 drop-code-unique-20260910.sql —— 唯一键只含业务列时，与逻辑删除冲突会导致"建-删-再建"稳定 500。
  KEY `idx_hazwaste_manifest_no` (`manifest_no`),
  KEY `idx_hazwaste_manifest_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-危废转移联单(国家固废五方)';

-- 台账扩列：一桶一码 + 标签归档。已存在则跳过（MySQL 8 无 IF NOT EXISTS，重复执行报 1060 可忽略）
ALTER TABLE `mes_set_hazardous_waste`
  ADD COLUMN `container_code` varchar(64) DEFAULT NULL COMMENT '容器码(一桶一码，危废标签二维码内容)' AFTER `storage_location`,
  ADD COLUMN `label_url` varchar(500) DEFAULT NULL COMMENT 'HJ1276 标签归档文件 URL' AFTER `container_code`;
ALTER TABLE `mes_set_hazardous_waste` ADD KEY `idx_hazwaste_container_code` (`container_code`);

-- 同 drop-code-unique-20260910.sql 的口径：本表本轮落成模块，服务层已按 deleted=0 查重，
-- 故一并拆掉只含业务列的唯一键（该表被那份脚本明确点名为同类隐患，本次属其交付物）。
ALTER TABLE `mes_set_hazardous_waste` DROP INDEX `uk_manifest_no`;
ALTER TABLE `mes_set_hazardous_waste` ADD KEY `idx_hazwaste_manifest_no` (`manifest_no`);
