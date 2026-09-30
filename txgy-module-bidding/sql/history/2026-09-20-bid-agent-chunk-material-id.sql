-- ============================================================
-- 标书智能体：切片表补 material_id（P1-3 修复：同名材料互相误删）
-- 日期：2026-09-20 ｜ 域：bidding_bid_agent_chunk
-- 背景：材料删除/重建按 source_doc_name 软关联清理，同名文件（如两个标段的「招标文件.pdf」）会互相误删切片与向量。
-- 口径：材料侧入库/清理/分页全部改为按 material_id 精确级联；契约端（按文档名入库）切片 material_id 为空。
-- 幂等：列/索引均先查 information_schema 再执行；回填语句重复执行无害（只填 material_id IS NULL 的行）。
-- 回填口径：按 (tenant_id, source_doc_name) 关联未删除的材料；历史上同名多材料无法区分，取最小 id（尽力而为）。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 补列（幂等）
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_chunk' AND column_name = 'material_id');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_chunk` ADD COLUMN `material_id` bigint DEFAULT NULL COMMENT ''材料编号（bidding_bid_material.id；按材料精确级联清理/重建，契约端按名入库时为空）'' AFTER `project_id`', 'SELECT ''chunk.material_id exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 索引（幂等）
SET @c := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_agent_chunk' AND index_name = 'idx_material');
SET @ddl := IF(@c = 0, 'ALTER TABLE `bidding_bid_agent_chunk` ADD KEY `idx_material` (`tenant_id`, `material_id`)', 'SELECT ''idx_material exists'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 回填（按名关联未删除材料，取最小 id；尽力而为，历史同名多材料无法精确区分）
--    两表 name/source_doc_name 列的 collation 可能不一致（unicode_ci vs 0900_ai_ci），显式 COLLATE 到 chunk 侧口径
UPDATE `bidding_bid_agent_chunk` c
JOIN `bidding_bid_material` m
  ON m.tenant_id = c.tenant_id AND m.name = c.source_doc_name COLLATE utf8mb4_0900_ai_ci AND m.deleted = 0
SET c.material_id = m.id
WHERE c.material_id IS NULL;
