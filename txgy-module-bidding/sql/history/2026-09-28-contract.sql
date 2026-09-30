-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：阶段十「合同签署与项目完结」表结构
-- 依据：《电子招标投标全流程说明书》§11；招标投标法第46/47条；《实施条例》第57/58条
-- 登记日期：2026-09-28
-- ============================================================================

-- 工程合同表（登记时校验履约保证金 ≤ 合同价 10%、签约 ≤ 中标通知书发出 30 日）
CREATE TABLE IF NOT EXISTS `bidding_contract` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `tender_project_code` varchar(64) NOT NULL COMMENT '招标项目编号（TENDER_PROJECT_CODE）',
  `bid_section_code` varchar(64) DEFAULT NULL COMMENT '标段编号（BID_SECTION_CODE，空=项目级）',
  `contract_code` varchar(64) NOT NULL COMMENT '合同编号（内部编号，前缀 C）',
  `contract_name` varchar(500) DEFAULT NULL COMMENT '合同名称',
  `contract_price` decimal(18,2) DEFAULT NULL COMMENT '合同价（元）',
  `performance_bond` decimal(18,2) DEFAULT NULL COMMENT '履约保证金（元）',
  `win_notice_time` datetime DEFAULT NULL COMMENT '中标通知书发出时间（签约30日时限起算点）',
  `sign_time` datetime DEFAULT NULL COMMENT '合同签订时间',
  `file_id` bigint DEFAULT NULL COMMENT '合同文件 FileApi 主键（电子签名文件）',
  `status` varchar(32) DEFAULT NULL COMMENT '合同状态（SIGNED 已签 / ARCHIVED 已归档）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tender_project` (`tender_project_code`),
  KEY `idx_bid_section` (`bid_section_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工程合同表（履约保证金10%/签约30日校验依据）';
