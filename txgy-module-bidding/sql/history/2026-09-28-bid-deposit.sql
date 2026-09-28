-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：阶段六「投标保证金」表结构
-- 依据：《电子招标投标全流程说明书》§7；《实施条例》第26条（投标保证金不超过估算价2%）、第57/58条
-- 登记日期：2026-09-28
-- ============================================================================

-- 投标保证金登记表（登记时校验金额 ≤ 估算价 2%，条例第26条；退还状态为合同签订后5日内退还的事实基础）
CREATE TABLE IF NOT EXISTS `bidding_bid_deposit` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `bid_section_code` varchar(64) NOT NULL COMMENT '标段编号（BID_SECTION_CODE）',
  `tender_project_code` varchar(64) DEFAULT NULL COMMENT '招标项目编号（TENDER_PROJECT_CODE，冗余便于按项目汇总）',
  `subject_id` bigint NOT NULL COMMENT '投标主体ID（bidding_subject.id）',
  `amount` decimal(18,2) NOT NULL COMMENT '保证金金额（元）',
  `currency_code` varchar(16) DEFAULT NULL COMMENT '币种（CURRENCY_CODE）',
  `pay_type` varchar(32) DEFAULT NULL COMMENT '缴纳方式（MARGIN_PAY_TYPE 字典）',
  `status` varchar(32) DEFAULT NULL COMMENT '缴纳状态（DEPOSIT_STATUS 字典）',
  `file_id` bigint DEFAULT NULL COMMENT '缴纳凭证文件ID（FileApi）',
  `deposit_time` datetime DEFAULT NULL COMMENT '缴纳时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退还时间',
  `refund_status` varchar(32) DEFAULT NULL COMMENT '退还状态（字典）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_bid_section` (`bid_section_code`),
  KEY `idx_tender_project` (`tender_project_code`),
  KEY `idx_subject` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投标保证金登记表（2%限额校验依据）';
