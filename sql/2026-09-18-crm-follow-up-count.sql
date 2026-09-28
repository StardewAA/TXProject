-- ============================================================================
-- CRM 跟进次数统计：客户 / 线索 / 商机 新增「累计跟进次数」并回填历史
-- 日期：2026-09-18
--
-- 背景：此前每写一次跟进都会在 crm_follow_up_record 落一条记录，但业务对象上
--       没有「跟进了几次」的字段，页面上看不到累计次数。
-- 方案：在 crm_customer / crm_clue / crm_business 上新增 follow_up_count，
--       每次新增跟进记录 +1、删除跟进记录 -1（后端处理），本脚本负责建列与回填历史。
--
-- 幂等，可重复执行。
-- 执行方式：
--   docker cp sql/2026-09-18-crm-follow-up-count.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' ruoyi-vue-pro < /tmp/2026-09-18-crm-follow-up-count.sql"
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 建列（幂等）
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'follow_up_count');
SET @ddl := IF(@c = 0,
    'ALTER TABLE `crm_customer` ADD COLUMN `follow_up_count` int NOT NULL DEFAULT 0 COMMENT ''累计跟进次数'' AFTER `follow_up_status`',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_clue' AND COLUMN_NAME = 'follow_up_count');
SET @ddl := IF(@c = 0,
    'ALTER TABLE `crm_clue` ADD COLUMN `follow_up_count` int NOT NULL DEFAULT 0 COMMENT ''累计跟进次数'' AFTER `follow_up_status`',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_business' AND COLUMN_NAME = 'follow_up_count');
SET @ddl := IF(@c = 0,
    'ALTER TABLE `crm_business` ADD COLUMN `follow_up_count` int NOT NULL DEFAULT 0 COMMENT ''累计跟进次数'' AFTER `follow_up_status`',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 2. 回填历史次数（按 crm_follow_up_record 统计）
--    biz_type：线索=1 客户=2 商机=4
-- ---------------------------------------------------------------------------
UPDATE `crm_customer` c
LEFT JOIN (
    SELECT biz_id, COUNT(*) AS cnt FROM `crm_follow_up_record`
    WHERE biz_type = 2 AND deleted = 0 GROUP BY biz_id
) r ON r.biz_id = c.id
SET c.follow_up_count = IFNULL(r.cnt, 0),
    c.follow_up_status = IF(IFNULL(r.cnt, 0) > 0, 1, c.follow_up_status);

UPDATE `crm_clue` c
LEFT JOIN (
    SELECT biz_id, COUNT(*) AS cnt FROM `crm_follow_up_record`
    WHERE biz_type = 1 AND deleted = 0 GROUP BY biz_id
) r ON r.biz_id = c.id
SET c.follow_up_count = IFNULL(r.cnt, 0),
    c.follow_up_status = IF(IFNULL(r.cnt, 0) > 0, 1, c.follow_up_status);

UPDATE `crm_business` b
LEFT JOIN (
    SELECT biz_id, COUNT(*) AS cnt FROM `crm_follow_up_record`
    WHERE biz_type = 4 AND deleted = 0 GROUP BY biz_id
) r ON r.biz_id = b.id
SET b.follow_up_count = IFNULL(r.cnt, 0),
    b.follow_up_status = IF(IFNULL(r.cnt, 0) > 0, 1, b.follow_up_status);

-- ---------------------------------------------------------------------------
-- 3. 自查
-- ---------------------------------------------------------------------------
SELECT '=== 客户跟进次数（>0 的前 10 条） ===' AS info;
SELECT id, name, follow_up_status, follow_up_count, contact_last_time
FROM crm_customer WHERE deleted = 0 AND follow_up_count > 0 ORDER BY follow_up_count DESC LIMIT 10;

SELECT '=== 三表汇总（应无 0 与记录数不一致的情况） ===' AS info;
SELECT 'customer' AS t, COUNT(*) AS 有次数客户数 FROM crm_customer WHERE deleted = 0 AND follow_up_count > 0
UNION ALL SELECT 'clue', COUNT(*) FROM crm_clue WHERE deleted = 0 AND follow_up_count > 0
UNION ALL SELECT 'business', COUNT(*) FROM crm_business WHERE deleted = 0 AND follow_up_count > 0;

SELECT '=== 一致性校验：字段值与跟进记录数不一致的客户（应为空） ===' AS info;
SELECT c.id, c.name, c.follow_up_count, IFNULL(r.cnt, 0) AS 实际记录数
FROM crm_customer c
LEFT JOIN (SELECT biz_id, COUNT(*) AS cnt FROM crm_follow_up_record WHERE biz_type = 2 AND deleted = 0 GROUP BY biz_id) r
  ON r.biz_id = c.id
WHERE c.deleted = 0 AND c.follow_up_count <> IFNULL(r.cnt, 0);
