-- ============================================================
-- 标书智能体：唯一键补 deleted（P0-2 修复）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_*
-- 背景：原唯一键不含 deleted，逻辑删除行与重建行互斥 →「取消再勾选/删除后重建」报 DuplicateKeyException。
-- 规范依据：《招投标模块开发规范》§六.1：T 轨 UNIQUE (业务编号, deleted, tenant_id)。
-- 幂等：先按 information_schema 判断索引是否已含 deleted 列再 ALTER；清理语句重复执行无害。
-- 配套代码改动：
--   1) 绑定表删除改物理删除（BidAgentProfileSkillMapper / BidAgentProfileMcpMapper）；
--   2) 技能/MCP/模型新增前物理清理同名已删行（purgeDeletedByCode / purgeDeletedByName），
--      避免「删除→重建→再删除」撞唯一键。
-- ============================================================

SET NAMES utf8mb4;

-- 0. 清理历史逻辑删除重复行（同业务键多条 deleted=1 时保留最新一条），保证新唯一键可创建
DELETE d FROM `bidding_bid_agent_skill` d
JOIN `bidding_bid_agent_skill` k
  ON k.tenant_id = d.tenant_id AND k.code = d.code AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_mcp_server` d
JOIN `bidding_bid_agent_mcp_server` k
  ON k.tenant_id = d.tenant_id AND k.name = d.name AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_model` d
JOIN `bidding_bid_agent_model` k
  ON k.tenant_id = d.tenant_id AND k.name = d.name AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_profile_skill` d
JOIN `bidding_bid_agent_profile_skill` k
  ON k.tenant_id = d.tenant_id AND k.profile_id = d.profile_id AND k.skill_id = d.skill_id AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

DELETE d FROM `bidding_bid_agent_profile_mcp` d
JOIN `bidding_bid_agent_profile_mcp` k
  ON k.tenant_id = d.tenant_id AND k.profile_id = d.profile_id AND k.mcp_server_id = d.mcp_server_id AND k.deleted = d.deleted
WHERE d.deleted = 1 AND d.id < k.id;

-- 1. 唯一键重建（幂等：仅当索引不含 deleted 列时执行）
SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_model' AND index_name = 'uk_tenant_name' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_model` DROP INDEX `uk_tenant_name`, ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)', 'SELECT ''model uk_tenant_name has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_skill' AND index_name = 'uk_tenant_code' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_skill` DROP INDEX `uk_tenant_code`, ADD UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`, `deleted`)', 'SELECT ''skill uk_tenant_code has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_mcp_server' AND index_name = 'uk_tenant_name' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_mcp_server` DROP INDEX `uk_tenant_name`, ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`, `deleted`)', 'SELECT ''mcp uk_tenant_name has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_profile_skill' AND index_name = 'uk_profile_skill' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_profile_skill` DROP INDEX `uk_profile_skill`, ADD UNIQUE KEY `uk_profile_skill` (`profile_id`, `skill_id`, `deleted`, `tenant_id`)', 'SELECT ''profile_skill uk has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_profile_mcp' AND index_name = 'uk_profile_mcp' AND column_name = 'deleted');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_profile_mcp` DROP INDEX `uk_profile_mcp`, ADD UNIQUE KEY `uk_profile_mcp` (`profile_id`, `mcp_server_id`, `deleted`, `tenant_id`)', 'SELECT ''profile_mcp uk has deleted'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
