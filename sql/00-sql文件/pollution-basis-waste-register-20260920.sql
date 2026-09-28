-- =====================================================================
-- MES 安全环保检测：法规依据白名单 + 产废登记
-- 日期：2026-09-20
-- 来源：从 Backup.zip（txgy-mes-env-20260918 快照）搬入，按本地口径改造
--
-- 一、法规依据白名单（本地原空白：判"有污染"拿不出法条出处）
--   1) ai_basis     —— AI 初筛援引的法规依据，取自 PollutionLegalBasis.CATALOG（8 条，硬编码白名单）
--   2) review_basis —— 人工复核勾选的判定依据，与 AI 共用同一份白名单
--   3) field_signs  —— 现场观察到的污染特征 code（8 项 SIGN_*，换行分隔）
--   4) mes_pollution_check_log 同样三列：履历是"当时的真实快照"，只增不改
--
--   分隔符一律用**换行**而非「；」：法条正文本身含「；」（如第七十八条
--   "制定危险废物管理计划；建立危险废物管理台账"），用「；」做分隔回填时会被切碎。
--
-- 二、产废登记（本地原空白：台账行只能由"复核判有污染"创建，换炭/换油称重无入口）
--   1) source_type    —— 区分台账行是判定来的还是产废登记来的
--   2) source_check_id 放宽为可空 —— 产废登记没有判定记录，靠 source_record_no（作业票号）反查
--   唯一键 uk_ledger_source_check **不用改**：MySQL 唯一索引允许多个 NULL，多条产废行可并存。
--
-- ⚠ 与备份原稿的三处差异（备份直接跑会不对）：
--   ① 备份加的是 quantity / quantity_unit 两列，本地已有同语义的 weight / unit_name
--      → 复用现有列，不制造第二套数量字段。
--   ② 备份把"判有污染必填依据"做成硬校验（错误码 1040818008），但本地该号已被
--      SET_POLLUTION_CHECK_LOCATION_ID_REQUIRED 占用；且本片口径是"依据不强制"
--      → 不新增错误码、不做非空校验。
--   ③ 备份的 registerWasteLedger 只有 service，没有端点也没有调用方（换炭作业票的
--      submit 那半截没写完）→ 本片补了 POST /mes/safety-env/pollution-ledger/waste-register。
--
-- 幂等：本文件不是幂等的（ALTER 重复跑会报 Duplicate column），重跑前先确认列是否已存在。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-basis-waste-register-20260920.sql
-- =====================================================================

-- ============ 一、法规依据白名单 ============

ALTER TABLE `mes_set_pollution_check`
    ADD COLUMN `field_signs` varchar(500) DEFAULT NULL COMMENT '现场观察到的污染特征(多选,存特征code,换行分隔)' AFTER `item_spec`,
    ADD COLUMN `ai_basis` varchar(500) DEFAULT NULL COMMENT 'AI 判定的法规依据(法规名+条款号/名录编号+要点)' AFTER `ai_reason`,
    ADD COLUMN `review_basis` varchar(1000) DEFAULT NULL COMMENT '人工复核的判定依据(复选法规条款,换行分隔)' AFTER `finished_result`;

ALTER TABLE `mes_pollution_check_log`
    ADD COLUMN `field_signs` varchar(500) DEFAULT NULL COMMENT '当时现场观察到的污染特征快照' AFTER `item_name`,
    ADD COLUMN `ai_basis` varchar(500) DEFAULT NULL COMMENT '当时 AI 判定的法规依据' AFTER `ai_reason`,
    ADD COLUMN `review_basis` varchar(1000) DEFAULT NULL COMMENT '人工复核的判定依据(仅 REVIEW 行)' AFTER `review_result`;

-- ============ 二、产废登记 ============

ALTER TABLE `mes_pollution_ledger`
    ADD COLUMN `source_type` varchar(32) DEFAULT NULL COMMENT '来源类型：POLLUTION_CHECK(判定复核登记)/WASTE_REGISTER(产废登记)' AFTER `source_record_no`,
    MODIFY COLUMN `source_check_id` bigint NULL COMMENT '来源判定记录ID(mes_set_pollution_check.id)；产废登记无判定记录，留空';

-- 存量数据回填：本片之前台账行只可能来自判定复核，全部标 POLLUTION_CHECK
UPDATE `mes_pollution_ledger` SET `source_type` = 'POLLUTION_CHECK' WHERE `source_type` IS NULL;

-- ============ 校验 ============
-- 期望 6 行；wrong_pos 应为 0（3 列必须都加在预期位置之后）
SELECT '污染判定+履历新增列' k, COUNT(*) v FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('mes_set_pollution_check', 'mes_pollution_check_log')
  AND COLUMN_NAME IN ('field_signs', 'ai_basis', 'review_basis')
UNION ALL SELECT '台账新增列', COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_pollution_ledger' AND COLUMN_NAME = 'source_type'
UNION ALL SELECT 'source_check_id 已可空(期望1)', COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_pollution_ledger'
  AND COLUMN_NAME = 'source_check_id' AND IS_NULLABLE = 'YES'
UNION ALL SELECT '存量台账已回填来源(期望0未回填)', COUNT(*) FROM `mes_pollution_ledger` WHERE `source_type` IS NULL;
