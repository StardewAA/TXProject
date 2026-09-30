-- 修复：bidding_bid_chapter_draft 唯一键漏 tenant_id，导致跨租户共用 project_id 时同一章节撞唯一键
-- 现象：一键生成 draft 阶段报 DuplicateKeyException: Duplicate entry '0-2.1-1' for key 'uk_project_node_version'
-- 幂等：先判列再改；本文件不含 DROP/DELETE 数据。
SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_chapter_draft'
             AND index_name = 'uk_project_node_version');
SET @ddl := IF(@c = 0,
  'SELECT ''uk_project_node_version already migrated'' AS skipped',
  'ALTER TABLE `bidding_bid_chapter_draft` DROP INDEX `uk_project_node_version`');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'bidding_bid_chapter_draft'
              AND index_name = 'uk_tenant_project_node_version');
SET @ddl2 := IF(@c2 = 0,
  'ALTER TABLE `bidding_bid_chapter_draft` ADD UNIQUE KEY `uk_tenant_project_node_version` (`tenant_id`,`project_id`,`node_id`,`version`)',
  'SELECT ''uk_tenant_project_node_version exists'' AS skipped');
PREPARE stmt FROM @ddl2; EXECUTE stmt; DEALLOCATE PREPARE stmt;
