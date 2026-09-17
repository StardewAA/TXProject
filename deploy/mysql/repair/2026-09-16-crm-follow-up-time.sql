-- ============================================================================
-- CRM 跟进记录：新增「实际跟进时间」（follow_up_time）
-- 日期：2026-09-16
--
-- 背景（问题现场）：
--   跟进记录里原本只有两个时间：
--     1) create_time    = 这条跟进笔记「录入系统」的操作时间（写笔记的那一刻）
--     2) next_time      = 「下次联系时间」，业务计划时间，不是本次跟进发生的时间
--   而「客户多久没被跟进 → 自动掉入公海」的计时，读的是 crm_customer.contact_last_time
--   （最后跟进时间）。原实现在写跟进时直接把它写成 now()，也就是拿「笔记的录入时间」
--   冒充「实际跟进时间」，导致：
--     - 补录一条历史跟进（例如实际 9/1 联系过，9/16 才补录）会把公海计时错误地
--       重置为 9/16，客户永远掉不进公海；
--     - 「下次联系时间」填成过去的日期时，业务上明明已经逾期未联系，系统却认为
--       「刚刚跟进过」，公海回收与【待办-已逾期】口径互相矛盾。
--
-- 修复：
--   1) 跟进记录新增 follow_up_time（实际跟进时间），由用户填写，不填时取当前时间；
--      允许补录历史跟进（填过去的日期），不允许填未来时间；
--   2) 写跟进时，业务表的「最后跟进时间」改为取 max(已有最后跟进时间, 本次实际跟进时间)，
--      避免补录历史把计时往回拨；
--   3) 「下次联系时间」不再参与公海计时（它只用于【待办】的今天需联系/已逾期提醒）。
--
-- 执行方式（务必带 utf8mb4，否则中文注释会变成乱码）：
--   docker cp deploy/mysql/repair/2026-09-16-crm-follow-up-time.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' < /tmp/2026-09-16-crm-follow-up-time.sql"
--
-- 处理原则：
--   1. 只新增列，不修改/删除既有列；
--   2. 脚本可重复执行（通过 add_col_if_missing 存储过程实现幂等）；
--   3. 历史数据的 follow_up_time 回填为 create_time —— 这是旧模型下唯一可得的近似值
--      （旧实现就是拿它当实际跟进时间的），回填后行为与修复前保持一致，不会凭空
--      把历史客户推进公海。
-- ============================================================================

USE `ruoyi-vue-pro`;

-- ---------------------------------------------------------------------------
-- 0. 幂等加列工具：列不存在时才 ADD COLUMN
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_col_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_col_if_missing`(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 幂等加索引工具：索引不存在时才 ADD INDEX
DROP PROCEDURE IF EXISTS `add_idx_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_idx_if_missing`(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '`(', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------------
-- 1. crm_follow_up_record：新增实际跟进时间
-- ---------------------------------------------------------------------------
CALL add_col_if_missing('crm_follow_up_record', 'follow_up_time',
    'follow_up_time datetime NULL COMMENT ''实际跟进时间（本次实际联系客户的时间，公海回收计时起点）''');

-- 索引：按「实际跟进时间」查某个客户/商机的最近一次跟进
CALL add_idx_if_missing('crm_follow_up_record', 'idx_biz_follow_up_time', 'biz_type, biz_id, follow_up_time');

-- ---------------------------------------------------------------------------
-- 2. 历史数据回填：follow_up_time = create_time
--    （旧模型下「笔记录入时间」是唯一可得的近似值，回填后不改变既有公海判定结果）
-- ---------------------------------------------------------------------------
UPDATE `crm_follow_up_record`
SET `follow_up_time` = `create_time`
WHERE `follow_up_time` IS NULL;

-- ---------------------------------------------------------------------------
-- 3. 清理临时存储过程
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_col_if_missing`;
DROP PROCEDURE IF EXISTS `add_idx_if_missing`;

-- ---------------------------------------------------------------------------
-- 4. 校验
-- ---------------------------------------------------------------------------
SELECT '=== 校验：新增列是否就位 ===' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_follow_up_record'
  AND COLUMN_NAME = 'follow_up_time';

SELECT '=== 校验：是否还有未回填的历史跟进记录（应为 0） ===' AS section;
SELECT COUNT(*) AS null_follow_up_time_count
FROM `crm_follow_up_record`
WHERE `follow_up_time` IS NULL;

SELECT '=== 校验：公海配置（未跟进天数 = 公海回收阈值） ===' AS section;
SELECT id, enabled, contact_expire_days, deal_expire_days, notify_enabled, notify_days, tenant_id
FROM `crm_customer_pool_config`;
