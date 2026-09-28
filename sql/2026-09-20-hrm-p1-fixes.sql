-- ============================================================
-- HRM P1 缺陷修复配套迁移
--
-- 背景（来自《HRM全业务流程测试报告》P1）：
--   H-03 Offer 创建竞态：先查后插、无行锁、无唯一索引，同一应聘可产生重复 Offer
--
-- 幂等：可重复执行
-- ============================================================

-- 1. 先处理历史重复：同一 application_id 只保留最早一条，其余软删（避免唯一索引创建失败）
UPDATE hrm_recruit_offer o
JOIN (
    SELECT id
    FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY application_id ORDER BY id) rn
        FROM hrm_recruit_offer
        WHERE deleted = b'0'
    ) t
    WHERE t.rn > 1
) dup ON dup.id = o.id
SET o.deleted = b'1', o.updater = 'p1-fix-20260920', o.update_time = NOW();

-- 2. hrm_recruit_offer.application_id 增加唯一索引（一个应聘只能有一份 Offer）
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'hrm_recruit_offer'
               AND index_name = 'uk_hrm_recruit_offer_application');
SET @sql := IF(@idx = 0,
    'ALTER TABLE `hrm_recruit_offer` ADD UNIQUE KEY `uk_hrm_recruit_offer_application` (`application_id`)',
    'SELECT ''uk_hrm_recruit_offer_application 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 核对：应返回 1 行唯一索引、0 行重复数据
-- SHOW INDEX FROM hrm_recruit_offer WHERE Key_name = 'uk_hrm_recruit_offer_application';
-- SELECT application_id, COUNT(*) c FROM hrm_recruit_offer GROUP BY application_id HAVING c > 1;
