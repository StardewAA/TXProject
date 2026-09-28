-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：补注册字典覆盖率门禁报出的 6 类（合同/资格预审/异议/人员/主体）
-- 登记日期：2026-09-21
-- 依据：sql/tools/check-dict-coverage.js 报 "6 类字典未注册（页面会退化成显示英文 code）"
--       —— alert_ref_type / contract_status / objection_type /
--          prequal_apply_status / prequal_status / subject_company_type
--
-- [为什么] 前端页面均为「字典优先 + 本地兜底」：字典注册后自动切换、兜底表可删；
--   未注册时页面只能回显英文 code（如 SIGNED / PASSED），运营不可读。
--
-- [号段] 710000000000009101 ~ 710000000000009118（9000 段空闲区；与契约13的 9001~9075 相邻不冲突）。
--
-- [值域来源] 前 3 类有后端枚举（中文名逐字取自枚举 getLabel()）；
--   后 3 类后端无枚举，值域**逐字取自前端既有兜底集**（已书面固化，如需调整改字典即可）。
--
-- [幂等] ON DUPLICATE KEY UPDATE，可重复执行。
-- ============================================================================

INSERT INTO `bidding_dict_data`
  (`id`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`,
   `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `version`)
VALUES
('710000000000009101', 0, '1', NOW(), '1', NOW(), b'0', 'contract_status', 'SIGNED', '已签', 'default', b'0', 1, b'1', 'ContractStatusEnum 口径（已签可编辑；已归档只读，存档≥15年）', 0),
('710000000000009102', 0, '1', NOW(), '1', NOW(), b'0', 'contract_status', 'ARCHIVED', '已归档', 'default', b'0', 2, b'1', 'ContractStatusEnum 口径（已签可编辑；已归档只读，存档≥15年）', 0),
('710000000000009103', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009104', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'PUBLISHED', '已发布', 'default', b'0', 2, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009105', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'APPLYING', '申请中', 'default', b'0', 3, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009106', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'REVIEWING', '审查中', 'default', b'0', 4, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009107', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'RESULTED', '已出结果', 'default', b'0', 5, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009108', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'CLOSED', '已关闭', 'default', b'0', 6, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009109', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_apply_status', 'SUBMITTED', '已提交', 'default', b'0', 1, b'1', 'PrequalApplyStatusEnum 口径', 0),
('710000000000009110', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_apply_status', 'PASSED', '通过', 'default', b'0', 2, b'1', 'PrequalApplyStatusEnum 口径', 0),
('710000000000009111', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_apply_status', 'FAILED', '不通过', 'default', b'0', 3, b'1', 'PrequalApplyStatusEnum 口径', 0),
('710000000000009112', 0, '1', NOW(), '1', NOW(), b'0', 'alert_ref_type', 'PERSON_CERT', '员工证书', 'default', b'0', 1, b'1', '值域取自 personnel/index.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009113', 0, '1', NOW(), '1', NOW(), b'0', 'alert_ref_type', 'PERSON_CERT_REVIEW', '复审', 'default', b'0', 2, b'1', '值域取自 personnel/index.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009114', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'QUALIFICATION', '资格条件异议', 'default', b'0', 1, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009115', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'DOCUMENT', '招标文件异议', 'default', b'0', 2, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009116', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'OPENING', '开标异议', 'default', b'0', 3, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009117', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'EVALUATION', '评标结果异议', 'default', b'0', 4, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009118', 0, '1', NOW(), '1', NOW(), b'0', 'subject_company_type', 'SELF', '本企业', 'default', b'0', 1, b'1', '值域取自 CompanyManagePanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009119', 0, '1', NOW(), '1', NOW(), b'0', 'subject_company_type', 'SUBSIDIARY', '子公司', 'default', b'0', 2, b'1', '值域取自 CompanyManagePanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009120', 0, '1', NOW(), '1', NOW(), b'0', 'subject_company_type', 'BRANCH', '分公司', 'default', b'0', 3, b'1', '值域取自 CompanyManagePanel.vue 既有兜底集（后端无枚举，已书面固化）', 0)
AS new
ON DUPLICATE KEY UPDATE
  `dict_label` = new.`dict_label`,
  `remark`     = new.`remark`,
  `sort`       = new.`sort`,
  `enabled`    = new.`enabled`,
  `deleted`    = b'0';

-- 校验段
SELECT dict_type, COUNT(*) AS items
FROM bidding_dict_data
WHERE deleted = 0 AND dict_type IN
  ('contract_status','prequal_status','prequal_apply_status','alert_ref_type','objection_type','subject_company_type')
GROUP BY dict_type ORDER BY dict_type;
