-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招标全流程时间驱动（2026-09-23）：投标时间字段 + 调度表
-- 设计：用户配置的时间精确到秒（bid_start_time/bid_end_time，datetime）；
--       调度表 bidding_flow_timer 在保存时注册（trigger_time=配置时间），
--       调度器（@Scheduled，周期默认 1s）只扫「到期且 WAITING」的轻量索引行，执行后 DONE/SKIP（幂等），
--       不做全表轮询（回应用户关切：朴素轮询定时任务不适合秒级精确）。
-- ============================================================================

-- 1) 投标起止时间（用户配置，精确到秒；触发 BIDDING_STARTED / BIDDING_ENDED）
ALTER TABLE bidding_bid_section ADD COLUMN bid_start_time datetime DEFAULT NULL COMMENT '投标开始时间（用户配置，精确到秒）' AFTER time_limit;
ALTER TABLE bidding_bid_section ADD COLUMN bid_end_time datetime DEFAULT NULL COMMENT '投标截止时间（用户配置，精确到秒）' AFTER bid_start_time;
ALTER TABLE bidding_tender_project ADD COLUMN bid_start_time datetime DEFAULT NULL COMMENT '投标开始时间（用户配置，精确到秒）' AFTER tender_project_name;
ALTER TABLE bidding_tender_project ADD COLUMN bid_end_time datetime DEFAULT NULL COMMENT '投标截止时间（用户配置，精确到秒）' AFTER bid_start_time;

-- 2) 时间驱动调度表（G 轨：后台调度无租户上下文；WAITING→DONE/SKIPPED 幂等）
CREATE TABLE IF NOT EXISTS bidding_flow_timer (
  id            bigint      NOT NULL COMMENT '主键（雪花）',
  biz_type      varchar(32) NOT NULL COMMENT '实体类型：PROJECT/TENDER_PROJECT/BID_SECTION',
  biz_code      varchar(64) NOT NULL COMMENT '业务编号',
  event         varchar(64) NOT NULL COMMENT '时间轴事件：BIDDING_STARTED/BIDDING_ENDED/EXPIRE',
  trigger_time  datetime    NOT NULL COMMENT '触发时间（用户配置，精确到秒）',
  status        varchar(16) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING待触发/DONE已执行/SKIPPED已跳过',
  executed_time datetime    DEFAULT NULL COMMENT '实际执行时间',
  remark        varchar(512) DEFAULT NULL COMMENT '来源说明（如：标段保存自动注册）',
  creator       varchar(64) DEFAULT '' COMMENT '创建者',
  create_time   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater       varchar(64) DEFAULT '' COMMENT '更新者',
  update_time   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       bit(1)     NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_trigger (status, trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招标全流程时间驱动调度表（G轨）';

-- ============================================================================
-- 状态字典登记（2026-09-23）：四类生命周期状态 → bidding_dict_data（前端回显中文/颜色）
-- dict_type 命名对齐既有：cert_status / verification_status（小写下划线）
-- ============================================================================

DELETE FROM bidding_dict_data WHERE dict_type IN ('project_status','tender_project_status','bid_section_status','announcement_status');

-- 项目状态（ProjectStatusEnum 5 态）
INSERT INTO bidding_dict_data (tenant_id, creator, create_time, updater, update_time, deleted, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, version) VALUES
(1, '1', NOW(), '1', NOW(), b'0', 'project_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '招标全流程状态机-项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'project_status', 'APPROVING', '审批中', 'info', b'0', 2, b'1', '招标全流程状态机-项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'project_status', 'ACTIVE', '已立项', 'success', b'0', 3, b'1', '招标全流程状态机-项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'project_status', 'COMPLETED', '已完成', 'success', b'1', 4, b'1', '招标全流程状态机-项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'project_status', 'CANCELLED', '已取消', 'info', b'1', 5, b'1', '招标全流程状态机-项目', 0);

-- 招标项目状态（TenderStatusEnum 12 态）
INSERT INTO bidding_dict_data (tenant_id, creator, create_time, updater, update_time, deleted, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, version) VALUES
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'APPROVING', '方案审批中', 'info', b'0', 2, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'PREPARING', '招标准备中', 'info', b'0', 3, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'ANNOUNCED', '已公告', 'info', b'0', 4, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'BIDDING', '投标中', 'info', b'0', 5, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'PENDING_OPEN', '待开标', 'warning', b'0', 6, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'EVALUATING', '评标中', 'warning', b'0', 7, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'PUBLICIZING', '中标公示中', 'warning', b'0', 8, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'AWARDED', '已定标', 'success', b'0', 9, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'CONTRACTING', '签约中', 'success', b'0', 10, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'COMPLETED', '已完成', 'success', b'1', 11, b'1', '招标全流程状态机-招标项目', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'tender_project_status', 'TERMINATED', '已终止', 'error', b'1', 12, b'1', '招标全流程状态机-招标项目', 0);

-- 标段状态（BidSectionStatusEnum 6 态）
INSERT INTO bidding_dict_data (tenant_id, creator, create_time, updater, update_time, deleted, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, version) VALUES
(1, '1', NOW(), '1', NOW(), b'0', 'bid_section_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '招标全流程状态机-标段', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'bid_section_status', 'BIDDING', '招标中', 'info', b'0', 2, b'1', '招标全流程状态机-标段', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'bid_section_status', 'EVALUATING', '评标中', 'warning', b'0', 3, b'1', '招标全流程状态机-标段', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'bid_section_status', 'AWARDED', '已定标', 'success', b'0', 4, b'1', '招标全流程状态机-标段', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'bid_section_status', 'COMPLETED', '已完成', 'success', b'1', 5, b'1', '招标全流程状态机-标段', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'bid_section_status', 'FAILED', '流标', 'error', b'1', 6, b'1', '招标全流程状态机-标段', 0);

-- 公告状态（AnnouncementStatusEnum 5 态）
INSERT INTO bidding_dict_data (tenant_id, creator, create_time, updater, update_time, deleted, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, version) VALUES
(1, '1', NOW(), '1', NOW(), b'0', 'announcement_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '招标全流程状态机-公告', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'announcement_status', 'REVIEWING', '审核中', 'info', b'0', 2, b'1', '招标全流程状态机-公告', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'announcement_status', 'PUBLISHED', '已发布', 'success', b'0', 3, b'1', '招标全流程状态机-公告', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'announcement_status', 'WITHDRAWN', '已撤回', 'warning', b'1', 4, b'1', '招标全流程状态机-公告', 0),
(1, '1', NOW(), '1', NOW(), b'0', 'announcement_status', 'EXPIRED', '已过期', 'error', b'1', 5, b'1', '招标全流程状态机-公告', 0);

-- 3) 公告接入新状态机：bidding_notice 增加 announcement_status 列（与既有 status 同步维护）
ALTER TABLE bidding_notice ADD COLUMN announcement_status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '公告生命周期状态（AnnouncementStatusEnum，招标全流程标准模型）' AFTER status;
UPDATE bidding_notice SET announcement_status = CASE status WHEN 'PUBLISHED' THEN 'PUBLISHED' WHEN 'UNPUBLISHED' THEN 'WITHDRAWN' WHEN 'ARCHIVED' THEN 'EXPIRED' ELSE 'DRAFT' END WHERE announcement_status = 'DRAFT';

-- 4) 修复：bidding_flow_timer 补 tenant_id 列（yudao 租户插件对所有表生效；与 bidding_flow_log 同解法）
ALTER TABLE bidding_flow_timer ADD COLUMN tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号' AFTER id;

-- 5) 2026-09-23 规范对齐修正（对比《VO数据对象规范·国家V2.0模版·陕西适配全量版》）：
--    投标起止时间属公告数据集（招标公告 BID_DOC_REFER_END_TIME=递交截止；变更公告 BID_END_TIME 覆盖；DOC_GET_START_TIME=文件获取开始≈投标起始），
--    主档表（TenderProjectVO/BidSectionVO 数据集）无时间字段 → DROP 自创列，时间驱动改为公告登记注册（NoticeServiceImpl → FlowTimerService.registerForTenderNotice）
ALTER TABLE bidding_tender_project DROP COLUMN bid_start_time, DROP COLUMN bid_end_time;
ALTER TABLE bidding_bid_section DROP COLUMN bid_start_time, DROP COLUMN bid_end_time;
-- 清理旧方案遗留的调度记录（旧项目 DONE/SKIPPED/WAITING 无审计价值；留痕在 bidding_flow_log）
DELETE FROM bidding_flow_timer;


-- =====================================================================
-- 2026-09-18 最严格流程补齐（投标方闭环 + 开标房间；开标视频不做）
-- 1) bidding_process_track / bidding_process_event / bidding_bid_opening / bidding_opening_member 见上
-- 2) 调整：bidding_bid_opening 去视频字段，改房间语义（room_status/room_opened_time/room_closed_time）
-- 3) bidding_opening_member 进房身份认证留痕（identity_type=CA_CERT/ACCOUNT；auth_result=PENDING/AUTHED/FAILED）
-- 4) process_track_status 字典 5 条（INTENTION→TERMINATED）
-- 5) CA 加密/身份认证待对接外部接口（见 29-外部CA身份认证对接清单-2026-09-18.md）
-- =====================================================================
ALTER TABLE bidding_bid_opening DROP COLUMN video_file_id, DROP COLUMN video_name, DROP COLUMN video_size, DROP COLUMN video_sha256;
ALTER TABLE bidding_bid_opening ADD COLUMN room_status varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '房间状态 WAITING/DECRYPTING/OPENING/DONE' AFTER opening_status;
ALTER TABLE bidding_bid_opening ADD COLUMN room_opened_time datetime DEFAULT NULL AFTER room_status;
ALTER TABLE bidding_bid_opening ADD COLUMN room_closed_time datetime DEFAULT NULL AFTER room_opened_time;


-- ============================================================================
-- 最严格流程建表（最终结构 2026-09-18；若为已执行过 ALTER 的库，本段幂等跳过）
-- 投标方闭环 + 开标房间（视频不做）；指纹统一 SM3（国密 GB/T 32905，用户裁定）
-- ============================================================================
CREATE TABLE IF NOT EXISTS bidding_process_track (
  id bigint NOT NULL COMMENT '主键',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  user_id bigint NOT NULL COMMENT '投标员工用户ID',
  follow_item_id bigint DEFAULT NULL COMMENT '关注条目ID（关联 bidding_follow_item）',
  follow_key varchar(100) NOT NULL COMMENT '维度代码（标段编号）',
  follow_type varchar(50) NOT NULL DEFAULT 'BID_SECTION' COMMENT '维度类型',
  tender_project_code varchar(64) DEFAULT NULL COMMENT '招标项目编号',
  bid_section_code varchar(64) DEFAULT NULL COMMENT '标段编号',
  project_name varchar(500) DEFAULT NULL COMMENT '项目名称（快照）',
  issuer_name varchar(200) DEFAULT NULL COMMENT '发布单位（快照）',
  current_state varchar(50) NOT NULL COMMENT '当前状态 INTENTION/QUALIFICATION_REVIEW/PENDING_BID/BIDDED/TERMINATED',
  previous_state varchar(50) DEFAULT NULL COMMENT '上一状态',
  intention_time datetime DEFAULT NULL COMMENT '意向投标时间（关注时间）',
  register_time datetime DEFAULT NULL COMMENT '报名时间',
  review_time datetime DEFAULT NULL COMMENT '审核完成时间',
  pending_bid_time datetime DEFAULT NULL COMMENT '进入待投标时间',
  bidded_time datetime DEFAULT NULL COMMENT '已投标时间（上传投标文件时间）',
  terminated_time datetime DEFAULT NULL COMMENT '流程结束时间',
  review_result varchar(20) DEFAULT NULL COMMENT '审核结果 APPROVED/REJECTED',
  review_reason varchar(500) DEFAULT NULL COMMENT '审核说明/不通过原因',
  document_file_id bigint DEFAULT NULL COMMENT '投标文件（加密）fileId',
  document_name varchar(200) DEFAULT NULL COMMENT '投标文件名',
  document_size bigint DEFAULT NULL COMMENT '投标文件大小（字节）',
  document_hash varchar(128) DEFAULT NULL COMMENT '投标文件指纹（SM3 十六进制，国密 GB/T 32905）',
  document_hash_algo varchar(16) NOT NULL DEFAULT 'SM3' COMMENT '指纹算法（SM3）',
  document_signature text COMMENT '投标文件数字签名（CA SDK 签名，SM2，base64）',
  ca_cert_no varchar(128) DEFAULT NULL COMMENT 'CA 证书序列号',
  ca_public_key text COMMENT 'CA 公钥（PEM，验签用）',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  creator varchar(64) DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_section (user_id, bid_section_code, deleted),
  KEY idx_section (bid_section_code),
  KEY idx_user_state (user_id, current_state),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投标流程跟踪表';

CREATE TABLE IF NOT EXISTS bidding_process_event (
  id bigint NOT NULL COMMENT '主键',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  track_id bigint NOT NULL COMMENT '流程跟踪ID',
  user_id bigint NOT NULL COMMENT '用户ID',
  event_type varchar(50) NOT NULL COMMENT '事件类型 FOLLOW_PROJECT/REGISTER/APPROVE/REJECT/UPLOAD_DOCUMENT/CANCEL',
  from_state varchar(50) DEFAULT NULL COMMENT '源状态',
  to_state varchar(50) NOT NULL COMMENT '目标状态',
  event_time datetime NOT NULL COMMENT '事件发生时间',
  operator_id bigint DEFAULT NULL COMMENT '操作人ID（可能是平台方审核员）',
  operator_name varchar(100) DEFAULT NULL COMMENT '操作人名称',
  operation_desc varchar(500) DEFAULT NULL COMMENT '操作描述',
  extra_data json DEFAULT NULL COMMENT '附加数据（JSON，如投标文件信息/报价）',
  creator varchar(64) DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_track_id (track_id),
  KEY idx_user_id (user_id),
  KEY idx_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投标流程事件记录表';

CREATE TABLE IF NOT EXISTS bidding_bid_opening (
  id bigint NOT NULL COMMENT '主键',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  tender_project_code varchar(64) NOT NULL COMMENT '招标项目编号',
  bid_section_code varchar(64) DEFAULT NULL COMMENT '标段编号（空=覆盖招标项目全部标段）',
  opening_time datetime DEFAULT NULL COMMENT '开标时间',
  opening_status varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '开标状态 PENDING/DECRYPTING/DONE',
  room_status varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '房间状态 WAITING/DECRYPTING/OPENING/DONE',
  room_opened_time datetime DEFAULT NULL COMMENT '房间开放时间',
  room_closed_time datetime DEFAULT NULL COMMENT '房间关闭时间',
  bidder_count int DEFAULT 0 COMMENT '投标人数量',
  open_list json DEFAULT NULL COMMENT '唱标清单 [{bidderName,bidSectionCode,price,decryptStatus}]',
  decrypt_count int DEFAULT 0 COMMENT '已解密数',
  decrypt_total int DEFAULT 0 COMMENT '应解密总数',
  operator_name varchar(100) DEFAULT NULL COMMENT '开标主持人',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  creator varchar(64) DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_tender_project (tender_project_code),
  KEY idx_section (bid_section_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开标记录表（房间语义）';

CREATE TABLE IF NOT EXISTS bidding_opening_member (
  id bigint NOT NULL COMMENT '主键',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  opening_id bigint NOT NULL COMMENT '开标记录ID（bidding_bid_opening.id）',
  tender_project_code varchar(64) NOT NULL COMMENT '招标项目编号',
  user_id bigint NOT NULL COMMENT '成员用户ID',
  user_name varchar(64) DEFAULT NULL COMMENT '成员姓名',
  subject_name varchar(200) DEFAULT NULL COMMENT '所属企业名称（投标人/招标人）',
  subject_code varchar(64) DEFAULT NULL COMMENT '统一社会信用代码',
  role varchar(20) NOT NULL COMMENT '身份 TENDERER招标方/PLATFORM平台方/BIDDER投标人/SUPERVISOR监督方',
  identity_type varchar(20) DEFAULT NULL COMMENT '身份认证方式 CA_CERT/ACCOUNT',
  ca_cert_no varchar(128) DEFAULT NULL COMMENT 'CA证书序列号',
  auth_result varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '进房认证结果 PENDING/AUTHED/FAILED',
  auth_message varchar(300) DEFAULT NULL COMMENT '认证结果说明（失败原因）',
  auth_time datetime DEFAULT NULL COMMENT '认证通过时间',
  enter_time datetime DEFAULT NULL COMMENT '进房时间',
  leave_time datetime DEFAULT NULL COMMENT '离房时间',
  online bit(1) NOT NULL DEFAULT b'1' COMMENT '是否在房间内',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  creator varchar(64) DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_opening (opening_id),
  KEY idx_tender_project (tender_project_code),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开标房间成员表（进房身份认证留痕）';

-- 投标流程状态字典（幂等）
INSERT INTO bidding_dict_data (tenant_id, creator, create_time, updater, update_time, deleted, dict_type, dict_value, dict_label, color_type, terminal, sort, enabled, remark, version)
SELECT 1, '1', NOW(), '1', NOW(), 0, 'process_track_status', v.value, v.label, v.color, v.terminal, v.sort, 1, '投标流程跟踪状态（最严格流程）', 0 FROM (
  SELECT 'INTENTION' value, '意向投标' label, 'info' color, 0 terminal, 1 sort UNION ALL
  SELECT 'QUALIFICATION_REVIEW', '资质审核', 'warning', 0, 2 UNION ALL
  SELECT 'PENDING_BID', '待投标', 'warning', 0, 3 UNION ALL
  SELECT 'BIDDED', '已投标', 'success', 0, 4 UNION ALL
  SELECT 'TERMINATED', '流程结束', 'error', 1, 5
) v WHERE NOT EXISTS (SELECT 1 FROM bidding_dict_data d WHERE d.dict_type = 'process_track_status' AND d.dict_value = v.value AND d.deleted = 0);
