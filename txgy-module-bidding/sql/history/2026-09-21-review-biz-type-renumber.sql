-- =====================================================================
-- review_biz_type 字典号段迁移：...0030xx → ...0046xx（审查任务域）
-- =====================================================================
-- 依据：契约《后端变更请求-平台审查任务中心-2026-09-20》§8 已知问题与风险——
--   初版把 review_biz_type 三条字典（QUALIFICATION/CERTIFICATE/PERSON_CERT）放在
--   ...003011~003013，落在 0030xx「员工状态」号段（personnel_status 占用 003001~3004）内，
--   违反脚本自声明的"按域划分号段、审查任务归 0046xx"约定；同段未来新增字典会
--   INSERT IGNORE 静默丢行（已有真实事故先例：见 2026-09-20-review-task-center.sql 头部注释）。
--   review_task_status 当时已修正到 004601~004603，本迁移把漏网的 review_biz_type 补齐到
--   004604~004606（经 sql/tools/check-seed-id-collision.js 全量扫描确认空闲）。
--
-- 执行库：**业务库**（bidding_dict_data 所在库）。幂等：按旧 id 匹配 UPDATE，可重复执行
--   （已迁移过的库旧 id 不存在，UPDATE 影响 0 行，无害）。
-- 前置：建议先备份 bidding_dict_data。
-- =====================================================================
SET NAMES utf8mb4;

-- 003011 → 004604（QUALIFICATION 企业资质）
UPDATE `bidding_dict_data`
   SET `id` = 710000000000004604
 WHERE `id` = 710000000000003011
   AND `tenant_id` = 0
   AND `dict_type` = 'review_biz_type'
   AND `dict_value` = 'QUALIFICATION';

-- 003012 → 004605（CERTIFICATE 证照）
UPDATE `bidding_dict_data`
   SET `id` = 710000000000004605
 WHERE `id` = 710000000000003012
   AND `tenant_id` = 0
   AND `dict_type` = 'review_biz_type'
   AND `dict_value` = 'CERTIFICATE';

-- 003013 → 004606（PERSON_CERT 员工证书）
UPDATE `bidding_dict_data`
   SET `id` = 710000000000004606
 WHERE `id` = 710000000000003013
   AND `tenant_id` = 0
   AND `dict_type` = 'review_biz_type'
   AND `dict_value` = 'PERSON_CERT';

-- 校验段：应返回 3 行（004604/004605/004606），且无残留 003011~003013
SELECT `id`, `dict_type`, `dict_value`, `dict_label`
  FROM `bidding_dict_data`
 WHERE `dict_type` = 'review_biz_type'
 ORDER BY `sort`;
SELECT COUNT(*) AS leftover_old_segment
  FROM `bidding_dict_data`
 WHERE `id` BETWEEN 710000000000003011 AND 710000000000003013;
