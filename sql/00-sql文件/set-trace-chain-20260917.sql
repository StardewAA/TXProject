-- =====================================================================
-- MES 环保一期「打基础」：全程追溯链 + 环环签字
-- 1) 新建 mes_set_trace_chain 追溯链节点表（业务动一次，链路长一节，只增不改）
-- 2) 新建 mes_set_sign_record 签字记录表（操作/复核/审批签字，只增不改不删）
-- 3) 追溯查询菜单 34303（兄弟于 34301/34302，父 34000）+ 复制角色授权
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < set-trace-chain-20260917.sql
-- =====================================================================

-- 1) 追溯链节点表
CREATE TABLE IF NOT EXISTS `mes_set_trace_chain` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `trace_code` varchar(64) NOT NULL COMMENT '本节点对象码(批次码/记录号)',
  `parent_codes` varchar(500) DEFAULT NULL COMMENT '上游来源码,逗号分隔(拆分/合并/返工时回填)',
  `node_stage` varchar(32) NOT NULL COMMENT '环节:PURCHASE_INBOUND采购入库/MATERIAL_ISSUE生产领用/WASTE_INTERMEDIATE中间废弃物/FINISHED_PRODUCT成品/SALES_OUTBOUND销售出库/DISPOSITION处置流转',
  `hazard_type` varchar(32) DEFAULT NULL COMMENT '危害类型:NORMAL正常/POLLUTED有污染受控/HAZ_WASTE危废',
  `batch_status` varchar(32) DEFAULT NULL COMMENT '批次状态快照(在库/隔离/已领用/已出库等)',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(200) DEFAULT NULL COMMENT '物料/产品名称',
  `item_spec` varchar(200) DEFAULT NULL COMMENT '规格',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `quantity` decimal(18,4) DEFAULT NULL COMMENT '数量',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '单据类型:POLLUTION_CHECK判定/POLLUTION_LEDGER台账/WM_ITEM_RECEIPT入库单/WM_PRODUCT_ISSUE领料单/WM_PRODUCT_RECEIPT成品入库单/WM_PRODUCT_SALES出库单',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联单据号',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `reviewer` varchar(64) DEFAULT NULL COMMENT '复核人',
  `approver` varchar(64) DEFAULT NULL COMMENT '审批人',
  `operate_time` datetime DEFAULT NULL COMMENT '操作时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_trace_code` (`trace_code`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_biz` (`biz_type`, `biz_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-全程追溯链节点';

-- 2) 签字记录表（只增不改不删：无 update/delete 业务入口）
CREATE TABLE IF NOT EXISTS `mes_set_sign_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` varchar(32) NOT NULL COMMENT '单据类型',
  `biz_no` varchar(64) NOT NULL COMMENT '单据号',
  `node_stage` varchar(32) DEFAULT NULL COMMENT '环节',
  `sign_role` varchar(32) NOT NULL COMMENT '签字角色:OPERATOR操作/REVIEWER复核/APPROVER审批/GATE门卫/EXTERNAL外部',
  `sign_user` varchar(64) NOT NULL COMMENT '签字人账号',
  `sign_name` varchar(64) DEFAULT NULL COMMENT '签字人姓名',
  `sign_img` varchar(500) DEFAULT NULL COMMENT '手写签名图片地址',
  `sign_time` datetime DEFAULT NULL COMMENT '签字时间',
  `sign_location` varchar(200) DEFAULT NULL COMMENT '签字地点(GPS/库位)',
  `sign_opinion` varchar(500) DEFAULT NULL COMMENT '签字/审批意见',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-环环签字记录';

-- 3) 追溯查询菜单（防重）——前端页面下期交付，先置为隐藏(visible=0)，届时改回 b'1' 即可
-- 菜单号 34305：34303/34304 已被 facility-carbon-menu-20260917.sql 占用（治污设施/换炭作业票），34321-34328 为其按钮段
DELETE FROM system_menu WHERE id = 34305;
DELETE FROM system_role_menu WHERE menu_id = 34305;
INSERT INTO system_menu (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES (34305, '全程追溯', 'mes:set-trace:query', 2, 195, 34000, 'traceChain', 'ep:share', 'mes/safetyEnv/traceChain/index', 'MesTraceChain', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
-- 复制"污染判定"(34301)的现有角色授权到追溯查询菜单
INSERT INTO system_role_menu (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `role_id`, 34305, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
FROM system_role_menu WHERE `menu_id` = 34301;
