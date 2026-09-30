-- ============================================================
-- 标书智能体：进行中任务唯一约束（P1-15 修复：任务重复提交竞态）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_task
-- 背景：generate() 先查后插判重（TOCTOU），并发双发可双双通过；@Idempotent 仅覆盖 1 秒同参数窗口。
-- 口径：running_key 生成列——进行中（PENDING/RUNNING）时 = tenant_id:project_id:bid_section_code，
--       终态时 = NULL（唯一索引不约束 NULL），由 DB 最终拦截并发重复任务。
-- 幂等：先查 information_schema 再 ALTER。
-- ============================================================

SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_task' AND column_name = 'running_key');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_task` ADD COLUMN `running_key` varchar(160) GENERATED ALWAYS AS (IF(`status` IN (''PENDING'',''RUNNING''), CONCAT(`tenant_id`, '':'', `project_id`, '':'', `bid_section_code`), NULL)) STORED COMMENT ''进行中任务的租户:项目:标段（终态为 NULL，供唯一索引防并发重复）'' AFTER `status`', 'SELECT ''task.running_key exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_task' AND index_name = 'uk_running');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_task` ADD UNIQUE KEY `uk_running` (`running_key`)', 'SELECT ''uk_running exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
