-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：异议（资格预审/招标文件异议，条例第22条）表结构
-- 依据：《电子招标投标全流程说明书》§6.3 第4条；《实施条例》第22条
--   （资格预审文件异议申请截止2日前提出、招标文件异议投标截止10日前提出，招标人收到后3日内答复）
-- 登记日期：2026-09-28
-- ============================================================================

-- 投标异议表（提出时限校验 + 3日答复超期标记的事实基础）
CREATE TABLE IF NOT EXISTS `bidding_objection` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `tender_project_code` varchar(64) NOT NULL COMMENT '招标项目编号（TENDER_PROJECT_CODE）',
  `bid_section_code` varchar(64) NOT NULL COMMENT '标段编号（BID_SECTION_CODE，异议落实到标段以对齐截止时限）',
  `objection_type` varchar(32) NOT NULL COMMENT '异议类型（OBJECTION_TYPE：PREQUAL_FILE 资格预审文件 / TENDER_FILE 招标文件）',
  `subject_id` bigint NOT NULL COMMENT '提出主体ID（bidding_subject.id）',
  `content` varchar(2000) NOT NULL COMMENT '异议内容',
  `propose_time` datetime NOT NULL COMMENT '提出时间',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING 待答复 / REPLIED 已答复）',
  `reply_time` datetime DEFAULT NULL COMMENT '答复时间',
  `reply_content` varchar(2000) DEFAULT NULL COMMENT '答复内容',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tender_project` (`tender_project_code`),
  KEY `idx_bid_section` (`bid_section_code`),
  KEY `idx_subject` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投标异议表（条例第22条：提出时限2/10日、3日答复）';
