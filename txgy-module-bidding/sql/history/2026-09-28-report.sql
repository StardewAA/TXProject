-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：定标书面报告（招标投标法第47条）表结构
-- 依据：《招标投标法》第47条（确定中标人之日起 15 日内向有关行政监督部门提交书面报告）
-- 登记日期：2026-09-28
-- ============================================================================

-- 定标书面报告表（15 日提交时限与超期标记的事实基础）
CREATE TABLE IF NOT EXISTS `bidding_report` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `tender_project_code` varchar(64) NOT NULL COMMENT '招标项目编号（TENDER_PROJECT_CODE）',
  `bid_section_code` varchar(64) DEFAULT NULL COMMENT '标段编号（BID_SECTION_CODE，空=项目级）',
  `report_code` varchar(64) NOT NULL COMMENT '报告编号（内部编号，前缀 R）',
  `report_name` varchar(255) NOT NULL COMMENT '报告名称',
  `file_id` bigint NOT NULL COMMENT '报告文件 FileApi 主键',
  `submit_time` datetime NOT NULL COMMENT '提交时间',
  `award_time` datetime DEFAULT NULL COMMENT '定标时间（中标结果公告发布时间，招标投标法第47条 15 日时限起算点）',
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态（SUBMITTED 已提交）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tender_project` (`tender_project_code`),
  KEY `idx_report_code` (`report_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定标书面报告表（招标投标法第47条：定标后15日内提交）';
