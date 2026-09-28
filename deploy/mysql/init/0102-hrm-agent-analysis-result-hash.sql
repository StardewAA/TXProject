-- HRM 招聘 Agent 分析结果补 result_hash 列。
--
-- 背景：HrmRecruitAgentAnalysisDO 早已有 resultHash 字段，SceneRunner 也在写入时
-- 计算了结构化结果的 SHA-256，但 create_tables 与 0076 里的建表语句都没跟上这一列。
-- 老库因此会在「保存结论」那一步直接报 Unknown column 'result_hash'，
-- 表现为接口 500、前端只看到「系统异常」——而模型其实已经正常返回了结果。
--
-- 后补的库走 0076 时不会有这一列（0076 保持原样、不改历史脚本），因此本脚本同时
-- 承担「新建库补齐」和「已有库升级」两件事。

SET @schema_name := DATABASE();
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'hrm_recruit_agent_analysis'
     AND COLUMN_NAME = 'result_hash') = 0,
  'ALTER TABLE `hrm_recruit_agent_analysis` ADD COLUMN `result_hash` varchar(64) DEFAULT NULL COMMENT ''结构化结果 SHA-256 指纹'' AFTER `model`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
