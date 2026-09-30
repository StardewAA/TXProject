-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：补注册契约 13 要求的枚举字典（19 类 / 75 行）
-- 登记日期：2026-09-21
-- 依据：《枚举字典注册请求-资质规则采集公告域-2026-09-14》（契约 13）
--       + 用户硬约束「状态码、枚举值、阈值必须支持动态配置，严禁硬编码」
--
-- [为什么] 前端棘轮门禁 check-hardcoded-enums.js 把"code→中文映射表"冻结为棘轮基线（只减不增）；
--   基线里剩余条目并非前端不肯改，而是**对应字典未注册** —— 字典缺席时纯字典化只会直出英文 code
--   （比硬编码更糟）。注册后前端即可「字典优先、映射表兜底」并逐条删除硬编码。
--
-- [号段] 710000000000009001 ~ 710000000000009075（接 cert_op_source 8215 之后；已核无冲突）。
--   ⚠️ 本模块 bidding_dict_data 主键为**手工指派**（无 AUTO_INCREMENT），撞号会**静默丢数据**，
--   故新增字典必须走本文件并跑 sql/tools/check-seed-id-collision.js 自检。
--
-- [A 类 vs B 类] 前 13 类有后端枚举类（值域权威，中文名取自契约 13 表格）；
--   后 6 类（rule_hit_source / crawl_job_status / crawl_trigger_type / qual_alert_ref_type /
--   crawl_parse_status / ai_review_status）原为「值域无权威来源」，本次按**既有实现/字段注释**书面固化，
--   如后续业务调整值域，改字典即可（这正是"动态配置"的意义）。
--
-- [幂等] 全部 INSERT ... ON DUPLICATE KEY UPDATE（按主键），可重复执行。
-- ============================================================================

INSERT INTO `bidding_dict_data`
  (`id`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`,
   `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `version`)
VALUES
('710000000000009001', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'REGISTERED_BUILDER', '注册建造师', 'default', b'0', 1, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009002', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'TECH_LEAD', '技术负责人', 'default', b'0', 2, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009003', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'MID_TITLE', '中级职称', 'default', b'0', 3, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009004', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'SKILLED_WORKER', '技术工人', 'default', b'0', 4, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009005', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'SITE_MGR', '现场管理人员', 'default', b'0', 5, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009006', 0, '1', NOW(), '1', NOW(), b'0', 'qual_performance_role', 'GENERAL', '总承包', 'default', b'0', 1, b'1', '资质业绩角色（PerformanceRoleEnum 口径）', 0),
('710000000000009007', 0, '1', NOW(), '1', NOW(), b'0', 'qual_performance_role', 'SUB', '分包', 'default', b'0', 2, b'1', '资质业绩角色（PerformanceRoleEnum 口径）', 0),
('710000000000009008', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'NET_ASSET', '净资产', 'default', b'0', 1, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009009', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'PLANT', '厂房', 'default', b'0', 2, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009010', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'MAJOR_EQUIP', '主要设备', 'default', b'0', 3, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009011', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'TEST_INSTR', '检测仪器', 'default', b'0', 4, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009012', 0, '1', NOW(), '1', NOW(), b'0', 'qual_review_type', 'SELF', '企业自查', 'default', b'0', 1, b'1', '资质核查类型（QualificationReviewTypeEnum 口径）', 0),
('710000000000009013', 0, '1', NOW(), '1', NOW(), b'0', 'qual_review_type', 'DYNAMIC', '动态核查', 'default', b'0', 2, b'1', '资质核查类型（QualificationReviewTypeEnum 口径）', 0),
('710000000000009014', 0, '1', NOW(), '1', NOW(), b'0', 'qual_review_type', 'PRE_APPLY', '申报前核查', 'default', b'0', 3, b'1', '资质核查类型（QualificationReviewTypeEnum 口径）', 0),
('710000000000009015', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'INT', '整数', 'default', b'0', 1, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009016', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'DECIMAL', '小数', 'default', b'0', 2, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009017', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'BOOL', '布尔', 'default', b'0', 3, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009018', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'STRING', '字符串', 'default', b'0', 4, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009019', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'JSON', 'JSON 结构', 'default', b'0', 5, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009020', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009021', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'PUBLISHED', '已发布', 'default', b'0', 2, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009022', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'GRAY', '灰度', 'default', b'0', 3, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009023', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'ROLLED_BACK', '已回滚', 'default', b'0', 4, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009024', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'CERT', '证照', 'default', b'0', 1, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009025', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'ALERT', '预警', 'default', b'0', 2, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009026', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'PERSONNEL', '人员', 'default', b'0', 3, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009027', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'QUAL', '资质', 'default', b'0', 4, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009028', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'SAFETY', '安许', 'default', b'0', 5, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009029', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'INSPECTION', '检查', 'default', b'0', 6, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009030', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'EQUIPMENT', '设备', 'default', b'0', 7, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009031', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'ANNOTATION', '批注', 'default', b'0', 8, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009032', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'BIDDOC', '投标文件', 'default', b'0', 9, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009033', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'CRYPTO', '密码应用', 'default', b'0', 10, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009034', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009035', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'PUBLISHED', '已发布', 'default', b'0', 2, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009036', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'UNPUBLISHED', '已撤下', 'default', b'0', 3, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009037', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'ARCHIVED', '已归档', 'default', b'0', 4, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009038', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'PENDING', '待执行', 'default', b'0', 1, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009039', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'RUNNING', '执行中', 'default', b'0', 2, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009040', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'SUCCESS', '成功', 'default', b'0', 3, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009041', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'FAILED', '失败', 'default', b'0', 4, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009042', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'SKIPPED', '已跳过', 'default', b'0', 5, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009043', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'HTML', 'HTML', 'default', b'0', 1, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009044', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'JSON', 'JSON', 'default', b'0', 2, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009045', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'FILE', '文件', 'default', b'0', 3, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009046', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'PDF', 'PDF', 'default', b'0', 4, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009047', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_result', 'PASS', '齐备', 'default', b'0', 1, b'1', '材料齐备结论（EnterpriseMaterialResultEnum 口径）', 0),
('710000000000009048', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_result', 'RISK', '部分缺失或临近到期', 'default', b'0', 2, b'1', '材料齐备结论（EnterpriseMaterialResultEnum 口径）', 0),
('710000000000009049', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_result', 'FAIL', '必备材料缺失', 'default', b'0', 3, b'1', '材料齐备结论（EnterpriseMaterialResultEnum 口径）', 0),
('710000000000009050', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'READY', '齐备', 'default', b'0', 1, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009051', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'EXPIRING', '临近到期', 'default', b'0', 2, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009052', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'MISSING', '缺失', 'default', b'0', 3, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009053', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'EXPIRED', '已过期', 'default', b'0', 4, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009054', 0, '1', NOW(), '1', NOW(), b'0', 'notice_audit_source', 'AI', 'AI 审查', 'default', b'0', 1, b'1', '公告审查来源（NoticeAuditSourceEnum 口径）', 0),
('710000000000009055', 0, '1', NOW(), '1', NOW(), b'0', 'notice_audit_source', 'HUMAN', '人工审查', 'default', b'0', 2, b'1', '公告审查来源（NoticeAuditSourceEnum 口径）', 0),
('710000000000009056', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'TENANT', '租户自定义', 'default', b'0', 1, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009057', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'PLATFORM', '平台默认', 'default', b'0', 2, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009058', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'GRAY', '灰度', 'default', b'0', 3, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009059', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'FALLBACK', '兜底', 'default', b'0', 4, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009060', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'RUNNING', '执行中', 'default', b'0', 1, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009061', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'SUCCESS', '成功', 'default', b'0', 2, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009062', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'PARTIAL', '部分成功', 'default', b'0', 3, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009063', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'FAILED', '失败', 'default', b'0', 4, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009064', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_trigger_type', 'SCHEDULE', '定时', 'default', b'0', 1, b'1', '采集触发类型（契约13 B类：值域取自既有实现，已书面固化）', 0),
('710000000000009065', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_trigger_type', 'MANUAL', '手动', 'default', b'0', 2, b'1', '采集触发类型（契约13 B类：值域取自既有实现，已书面固化）', 0),
('710000000000009066', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_trigger_type', 'REPAIR', '补采', 'default', b'0', 3, b'1', '采集触发类型（契约13 B类：值域取自既有实现，已书面固化）', 0),
('710000000000009067', 0, '1', NOW(), '1', NOW(), b'0', 'qual_alert_ref_type', 'QUAL', '资质', 'default', b'0', 1, b'1', '资质预警来源类型（契约13 B类：值域取自现有实现，已书面固化）', 0),
('710000000000009068', 0, '1', NOW(), '1', NOW(), b'0', 'qual_alert_ref_type', 'SAFETY_PERMIT', '安许证', 'default', b'0', 2, b'1', '资质预警来源类型（契约13 B类：值域取自现有实现，已书面固化）', 0),
('710000000000009069', 0, '1', NOW(), '1', NOW(), b'0', 'qual_alert_ref_type', 'STAFF', '人员配置', 'default', b'0', 3, b'1', '资质预警来源类型（契约13 B类：值域取自现有实现，已书面固化）', 0),
('710000000000009070', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_parse_status', 'SUCCESS', '成功', 'default', b'0', 1, b'1', '采集解析状态（契约13 B类：值域取自 CrawlParseTestRespVO 注释，已书面固化）', 0),
('710000000000009071', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_parse_status', 'PARTIAL', '部分成功', 'default', b'0', 2, b'1', '采集解析状态（契约13 B类：值域取自 CrawlParseTestRespVO 注释，已书面固化）', 0),
('710000000000009072', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_parse_status', 'FAILED', '失败', 'default', b'0', 3, b'1', '采集解析状态（契约13 B类：值域取自 CrawlParseTestRespVO 注释，已书面固化）', 0),
('710000000000009073', 0, '1', NOW(), '1', NOW(), b'0', 'ai_review_status', 'PASS', 'AI 审查通过', 'default', b'0', 1, b'1', '外部公告 AI 审查状态（契约13 B类：值域取自 NoticeExternalManageRespVO 注释，已书面固化）', 0),
('710000000000009074', 0, '1', NOW(), '1', NOW(), b'0', 'ai_review_status', 'REJECT', 'AI 审查未通过（转人工）', 'default', b'0', 2, b'1', '外部公告 AI 审查状态（契约13 B类：值域取自 NoticeExternalManageRespVO 注释，已书面固化）', 0),
('710000000000009075', 0, '1', NOW(), '1', NOW(), b'0', 'ai_review_status', 'FAILED', 'AI 审查执行失败', 'default', b'0', 3, b'1', '外部公告 AI 审查状态（契约13 B类：值域取自 NoticeExternalManageRespVO 注释，已书面固化）', 0)
AS new
ON DUPLICATE KEY UPDATE
  `dict_label` = new.`dict_label`,
  `remark`     = new.`remark`,
  `sort`       = new.`sort`,
  `enabled`    = new.`enabled`,
  `deleted`    = b'0';

-- 校验段：19 类字典的条数（期望与下方注释一致）
SELECT dict_type, COUNT(*) AS items
FROM bidding_dict_data
WHERE deleted = 0
  AND dict_type IN ('qual_staff_role', 'qual_performance_role', 'qual_asset_type', 'qual_review_type', 'rule_value_type', 'rule_status', 'rule_group', 'notice_status', 'crawl_task_status', 'crawl_payload_type', 'material_check_result', 'material_check_status', 'notice_audit_source', 'rule_hit_source', 'crawl_job_status', 'crawl_trigger_type', 'qual_alert_ref_type', 'crawl_parse_status', 'ai_review_status')
GROUP BY dict_type
ORDER BY dict_type;