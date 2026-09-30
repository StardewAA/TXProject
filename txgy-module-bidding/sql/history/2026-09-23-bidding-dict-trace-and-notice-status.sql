-- =====================================================================
-- 追溯/备案/主体/外部公告 状态类字典注册（承接 2026-09-22-bidding-dict-employment-type.sql）
-- =====================================================================
-- 背景：项目全景追溯抽屉（views/bidding/project）本轮补齐了"外部公告 / 备案记录 / 关注项 / 参与主体"四张表，
--       表里的状态、类型字段此前只能直出后端 code（英文码直出）。本脚本把这些枚举登记为字典，前端改为
--       dictLabel/dictTag 取值 —— 与用户硬约束一致："状态码/枚举值必须支持动态配置，严禁硬编码"。
-- 取值来源（不臆造，逐条给出出处）：
--   filing_status                ← FilingStatusEnum（PENDING/ACCEPTED/REJECTED；BID-ENT-REG-01 提交→PENDING，
--                                   监管 BID-SUP-FILE-01 回推→ACCEPTED/REJECTED）
--   subject_auth_status          ← SubjectAuthStatusEnum（ENABLED 已授权 / DISABLED 已停用；平台侧核发）
--   subject_type                 ← SubjectTypeEnum（BIDDER 投标人 / TENDERER 招标人 / AGENCY 招标代理机构，
--                                   其中 AGENCY 为扩展位"本期未落地，预留枚举防止脏数据入库"——照登记）
--   trace_subject_source         ← ProjectTraceRespVO.SubjectItem#source 的 Javadoc
--                                   （TENDER_PROJECT_SNAPSHOT=招标项目主档快照 / TENANT_AUTH=本租户平台授权主体）
--   notice_external_status       ← NoticeExternalStatusEnum（NEW/ACTIVE/INACTIVE/ARCHIVED）
--   notice_external_audit_status ← NoticeExternalAuditStatusEnum（UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN）
--   notice_external_content_type ← NoticeExternalContentTypeEnum（HTML/PDF/ATTACHMENT/IMAGE/OTHER）
-- 不登记（有意，避免制造第二套编码）：
--   tenderer_code_type —— TenderProjectDO.tendererCodeType 注释只写"招标人代码类型（TENDERER_CODE_TYPE）"，
--                         取值来自外部标准（《VO数据规范》/陕 V2.0 主体类别码），模块内无枚举可对齐；
--                         前端暂按原码展示，待口径明确后再登记。
-- 设计说明：
--   1) id 分块：每类占用一个 100 段（36xx filing / 37xx auth / 38xx subject_type / 39xx source /
--      40xx ext-status / 41xx ext-audit / 42xx ext-content），沿用 09-19~09-22 的约定，不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 着色依据业务语义而非配色偏好：驳回/停用/失效/下线标 danger 或 warning，终态标 default，
--      与既有字典（personnel_status 等）的用法保持一致。
-- 执行库：ruoyi-vue-pro（与前序字典注册脚本同库）。
-- 校验：SELECT dict_type, COUNT(*) FROM bidding_dict_data
--       WHERE dict_type IN ('filing_status','subject_auth_status','subject_type','trace_subject_source',
--                           'notice_external_status','notice_external_audit_status','notice_external_content_type')
--       GROUP BY dict_type ORDER BY dict_type;  -- 期望 3 / 2 / 3 / 2 / 4 / 4 / 5
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- filing_status（3 条）
(710000000000003601,0,'filing_status','PENDING','待监管受理','processing',b'0',1,b'1','已提交，等待监管平台 BID-SUP-FILE-01 回推','1',NOW(),'1',NOW(),b'0'),
(710000000000003602,0,'filing_status','ACCEPTED','受理通过','success',b'1',2,b'1','监管回推终态：受理通过','1',NOW(),'1',NOW(),b'0'),
(710000000000003603,0,'filing_status','REJECTED','受理驳回','danger',b'1',3,b'1','监管回推终态：驳回（需按驳回意见整改后重新提交）','1',NOW(),'1',NOW(),b'0'),
-- subject_auth_status（2 条）
(710000000000003701,0,'subject_auth_status','ENABLED','已授权','success',b'0',1,b'1','可进入后续流程（投标人可完善档案后投标；招标人可完善后发布公告）','1',NOW(),'1',NOW(),b'0'),
(710000000000003702,0,'subject_auth_status','DISABLED','已停用','danger',b'0',2,b'1','平台撤销授权：失去投标/发布资格，档案保留（留痕不删）','1',NOW(),'1',NOW(),b'0'),
-- subject_type（3 条）
(710000000000003801,0,'subject_type','BIDDER','投标人','info',b'0',1,b'1','响应招标、参加投标竞争的法人或其他组织（《招标投标法》第 25 条）','1',NOW(),'1',NOW(),b'0'),
(710000000000003802,0,'subject_type','TENDERER','招标人','success',b'0',2,b'1','依法提出招标项目、进行招标的法人或其他组织（《招标投标法》第 8 条）','1',NOW(),'1',NOW(),b'0'),
(710000000000003803,0,'subject_type','AGENCY','招标代理机构','default',b'0',3,b'1','扩展位：本期未落地，预留枚举防止脏数据入库','1',NOW(),'1',NOW(),b'0'),
-- trace_subject_source（2 条）
(710000000000003901,0,'trace_subject_source','TENDER_PROJECT_SNAPSHOT','招标项目主档快照','info',b'0',1,b'1','来自关联招标项目主档的招标人信息','1',NOW(),'1',NOW(),b'0'),
(710000000000003902,0,'trace_subject_source','TENANT_AUTH','本租户平台授权主体','success',b'0',2,b'1','来自本租户的主体授权记录（含授权状态与档案完备度）','1',NOW(),'1',NOW(),b'0'),
-- notice_external_status（4 条）
(710000000000004001,0,'notice_external_status','NEW','待审核','processing',b'0',1,b'1','采集入库，等待审核','1',NOW(),'1',NOW(),b'0'),
(710000000000004002,0,'notice_external_status','ACTIVE','生效中（可展示）','success',b'0',2,b'1','审核通过后生效并触发 ES 索引事件（MQ）','1',NOW(),'1',NOW(),b'0'),
(710000000000004003,0,'notice_external_status','INACTIVE','已失效','default',b'1',3,b'1','超生效期或源站下线','1',NOW(),'1',NOW(),b'0'),
(710000000000004004,0,'notice_external_status','ARCHIVED','已归档（只读留痕）','default',b'1',4,b'1','归档只读，不再展示','1',NOW(),'1',NOW(),b'0'),
-- notice_external_audit_status（4 条）
(710000000000004101,0,'notice_external_audit_status','UNREVIEWED','待审核','processing',b'0',1,b'1','采集入库后的初始审核态','1',NOW(),'1',NOW(),b'0'),
(710000000000004102,0,'notice_external_audit_status','APPROVED','已审核通过','success',b'0',2,b'1','审核通过并转 ACTIVE 展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004103,0,'notice_external_audit_status','REJECTED','已驳回','danger',b'0',3,b'1','驳回：不展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004104,0,'notice_external_audit_status','TAKEN_DOWN','已下线','warning',b'0',4,b'1','曾上线后被运营撤下，展示接口随即拒绝','1',NOW(),'1',NOW(),b'0'),
-- notice_external_content_type（5 条）
(710000000000004201,0,'notice_external_content_type','HTML','HTML 原文','default',b'0',1,b'1','原文原样展示 + 原网址','1',NOW(),'1',NOW(),b'0'),
(710000000000004202,0,'notice_external_content_type','PDF','PDF 原文件','default',b'0',2,b'1','pdfFileId 前端预览 + 下载 + 原网址','1',NOW(),'1',NOW(),b'0'),
(710000000000004203,0,'notice_external_content_type','ATTACHMENT','附件下载','default',b'0',3,b'1','以附件下载列表形式展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004204,0,'notice_external_content_type','IMAGE','图片','default',b'0',4,b'1','图片形态展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004205,0,'notice_external_content_type','OTHER','其它','default',b'0',5,b'1','兜底：展示原网址','1',NOW(),'1',NOW(),b'0');
