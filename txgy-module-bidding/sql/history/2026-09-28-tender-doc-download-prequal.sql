-- 招投标模块：阶段四「获取招标文件/下载名单」+ 阶段五「资格预审」表结构
-- 依据：《电子招标投标全流程说明书》§5/§6；《实施条例》第16/17/18/19条
-- 登记日期：2026-09-28

-- ============ 阶段四：招标文件下载记录（下载名单 = 澄清通知的事实基础）============
CREATE TABLE IF NOT EXISTS `bidding_tender_doc_download` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `tender_project_code` varchar(64) NOT NULL COMMENT '招标项目编号（TENDER_PROJECT_CODE）',
  `bid_section_code` varchar(64) DEFAULT NULL COMMENT '标段编号（BID_SECTION_CODE，空=项目级）',
  `attachment_id` bigint DEFAULT NULL COMMENT '关联附件行ID（bidding_attachment.id，招标文件）',
  `file_id` bigint DEFAULT NULL COMMENT 'FileApi 文件对象主键（一致性依据）',
  `subject_id` bigint DEFAULT NULL COMMENT '投标主体ID（bidding_subject.id）',
  `user_id` bigint DEFAULT NULL COMMENT '下载用户ID',
  `download_time` datetime NOT NULL COMMENT '下载时间（名单排序与异议期限计算依据）',
  `ip` varchar(64) DEFAULT NULL COMMENT '下载来源IP',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招标文件下载记录（下载名单，供澄清/异议定向通知）';

-- ============ 阶段五：资格预审主记录 ============
CREATE TABLE IF NOT EXISTS `bidding_prequal` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `prequal_code` varchar(64) NOT NULL COMMENT '资格预审编号（内部编号，前缀 P）',
  `tender_project_code` varchar(64) NOT NULL COMMENT '招标项目编号（TENDER_PROJECT_CODE）',
  `bid_section_code` varchar(64) DEFAULT NULL COMMENT '标段编号（空=招标项目级资格预审）',
  `title` varchar(500) NOT NULL COMMENT '资格预审项目名称',
  `notice_code` varchar(64) DEFAULT NULL COMMENT '关联资格预审公告编号（bidding_notice.notice_code）',
  `file_id` bigint DEFAULT NULL COMMENT '资格预审文件 FileApi 主键（一致性依据）',
  `apply_start_time` datetime DEFAULT NULL COMMENT '申请开始时间（资格预审文件停止发售时间）',
  `apply_deadline` datetime DEFAULT NULL COMMENT '申请截止时间（提交资格预审申请文件截止）',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态（PrequalStatusEnum：DRAFT/PUBLISHED/APPLYING/REVIEWING/RESULTED/CLOSED）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prequal_code` (`prequal_code`),
  KEY `idx_tender_project` (`tender_project_code`),
  KEY `idx_bid_section` (`bid_section_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资格预审主记录（招标方发起：公告/文件/申请期）';

-- ============ 阶段五：资格预审申请 ============
CREATE TABLE IF NOT EXISTS `bidding_prequal_apply` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `prequal_id` bigint NOT NULL COMMENT '资格预审ID（bidding_prequal.id）',
  `prequal_code` varchar(64) NOT NULL COMMENT '资格预审编号（冗余，便于按编号追溯）',
  `tender_project_code` varchar(64) DEFAULT NULL COMMENT '招标项目编号（冗余）',
  `bid_section_code` varchar(64) DEFAULT NULL COMMENT '标段编号（冗余）',
  `subject_id` bigint DEFAULT NULL COMMENT '投标主体ID（bidding_subject.id）',
  `user_id` bigint DEFAULT NULL COMMENT '申请人用户ID',
  `file_id` bigint DEFAULT NULL COMMENT '资格预审申请文件 FileApi 主键',
  `apply_time` datetime NOT NULL COMMENT '申请提交时间',
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态（PrequalApplyStatusEnum：SUBMITTED/PASSED/FAILED）',
  `review_result` varchar(32) DEFAULT NULL COMMENT '审查结果（PASSED/FAILED）',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审查说明/不通过原因',
  `review_time` datetime DEFAULT NULL COMMENT '审查完成时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_prequal` (`prequal_id`),
  KEY `idx_subject` (`subject_id`),
  KEY `idx_tender_project` (`tender_project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资格预审申请（投标方提交 + 资格审查委员会审查）';
