-- ---------------------------------------------------------------------
-- CRM 售后模块：schema 一键同步（补齐历史 repair 脚本可能漏执行的列 / 索引）
--
-- 【背景】
--   售后模块的增量脚本放在 deploy/mysql/repair/ 下，需要**人工按顺序执行**。
--   一旦漏执行，就会出现「实体里声明了字段、数据库表却没有这一列」，
--   由于 MyBatis-Plus 的 selectList / selectPage 会带上全部实体字段，
--   任何一次列表查询都会直接 500，表现为前端只提示「系统错误」，很难定位。
--
--   已发生过两次（本脚本即为此而生）：
--     1) crm_after_sale_visit_task 缺 cycle_key / biz_no
--        → 质保档案删除失败、回访任务列表打不开、任务生成后无法落库；
--     2) crm_after_sale_visit_record 缺 status
--        → 回访任务列表（会带出回访记录）500，用户以为「生成任务失败」，
--          实际任务已生成，只是列表打不开。
--
-- 【用途】
--   一键把售后模块所有「可能缺失」的列 / 索引补齐，脚本全部幂等，
--   可安全重复执行（已存在则跳过）。新环境部署或怀疑漏脚本时执行本脚本即可。
--
-- 【历史脚本清单（仍可单独使用，本脚本是它们的幂等汇总）】
--   2026-09-07-crm-after-sale.sql                     售后模块建表
--   2026-09-08-crm-after-sale-menu.sql                售后菜单
--   2026-09-08-crm-after-sale-notify.sql              售后通知 / 商机来源 source
--   2026-09-08-crm-after-sale-tenant-grant.sql        租户授权
--   2026-09-14-crm-after-sale-visit-record-status.sql 回访记录 status
--   2026-09-17-crm-after-sale-allow-early-visit.sql   售后设置 allow_early_visit
--   2026-09-17-crm-after-sale-visit-window.sql        回访任务 window_end_date
--   2026-09-18-crm-after-sale-ticket-index.sql        工单索引
--   2026-09-18-crm-after-sale-warranty-index.sql      质保索引
--   2026-09-20-crm-after-sale-visit-task-missing-columns.sql 回访任务 cycle_key / biz_no
--   2026-09-20-crm-after-sale-warranty-one-to-one.sql 质保档案与合同一对一唯一约束
--
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
-- ---------------------------------------------------------------------

SET @db = DATABASE();

-- ============================== 1. 补齐列 ==============================

-- 1.1 回访记录：记录状态（1=待填写 2=已填写）
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_record'
               AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE `crm_after_sale_visit_record`
       ADD COLUMN `status` tinyint NOT NULL DEFAULT 2
         COMMENT ''记录状态：1=待填写 2=已填写'' AFTER `task_id`',
    'SELECT ''crm_after_sale_visit_record.status 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 售后设置：是否允许提前提交回访记录
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_config'
               AND COLUMN_NAME = 'allow_early_visit') = 0,
    'ALTER TABLE `crm_after_sale_config`
       ADD COLUMN `allow_early_visit` bit(1) NOT NULL DEFAULT b''0''
         COMMENT ''是否允许提前提交回访记录：0=不允许 1=允许'' AFTER `task_overdue_days`',
    'SELECT ''crm_after_sale_config.allow_early_visit 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 回访任务：窗口期截止日期
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_task'
               AND COLUMN_NAME = 'window_end_date') = 0,
    'ALTER TABLE `crm_after_sale_visit_task`
       ADD COLUMN `window_end_date` date DEFAULT NULL
         COMMENT ''窗口期截止日期 = 计划执行日期 + 回访任务逾期判定天数'' AFTER `task_date`',
    'SELECT ''crm_after_sale_visit_task.window_end_date 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 回访任务：周期标识（C1、C2… 或 EXPIRE）
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_task'
               AND COLUMN_NAME = 'cycle_key') = 0,
    'ALTER TABLE `crm_after_sale_visit_task`
       ADD COLUMN `cycle_key` varchar(16) DEFAULT NULL
         COMMENT ''周期标识：周期型任务为 C1/C2…，到期型为 EXPIRE，手动任务为空'' AFTER `plan_id`',
    'SELECT ''crm_after_sale_visit_task.cycle_key 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 回访任务：业务编号（按质保档案独立递增）
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_task'
               AND COLUMN_NAME = 'biz_no') = 0,
    'ALTER TABLE `crm_after_sale_visit_task`
       ADD COLUMN `biz_no` int DEFAULT NULL
         COMMENT ''业务编号：按质保档案独立递增，仅用于展示'' AFTER `cycle_key`',
    'SELECT ''crm_after_sale_visit_task.biz_no 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================== 2. 补齐索引 ==============================

-- 2.1 工单：创建时间索引
SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_ticket'
               AND INDEX_NAME = 'idx_create_time') = 0,
    'ALTER TABLE `crm_after_sale_ticket` ADD INDEX `idx_create_time` (`create_time`) USING BTREE',
    'SELECT ''crm_after_sale_ticket.idx_create_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 质保档案：到期日索引
SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_warranty'
               AND INDEX_NAME = 'idx_warranty_end_date') = 0,
    'ALTER TABLE `crm_after_sale_warranty` ADD INDEX `idx_warranty_end_date` (`warranty_end_date`) USING BTREE',
    'SELECT ''crm_after_sale_warranty.idx_warranty_end_date 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================== 3. 自查 ==============================
SELECT '--- 售后模块关键列自检（应全部存在）---' AS s;
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND ((TABLE_NAME = 'crm_after_sale_visit_record' AND COLUMN_NAME = 'status')
    OR (TABLE_NAME = 'crm_after_sale_config'      AND COLUMN_NAME = 'allow_early_visit')
    OR (TABLE_NAME = 'crm_after_sale_visit_task'  AND COLUMN_NAME IN ('window_end_date', 'cycle_key', 'biz_no')))
ORDER BY TABLE_NAME, COLUMN_NAME;

SELECT '--- 售后模块关键索引自检（应全部存在）---' AS s;
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @db
  AND ((TABLE_NAME = 'crm_after_sale_ticket' AND INDEX_NAME = 'idx_create_time')
    OR (TABLE_NAME = 'crm_after_sale_warranty' AND INDEX_NAME = 'idx_warranty_end_date'))
GROUP BY TABLE_NAME, INDEX_NAME;
