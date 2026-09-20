-- ---------------------------------------------------------------------
-- 售后回访任务：补齐实体已声明、但建表脚本遗漏的两个字段
--
-- 【问题】
--   CrmAfterSaleVisitTaskDO 声明了 cycleKey / bizNo 两个字段，
--   但 crm_after_sale_visit_task 表缺少对应列。由于 MyBatis-Plus 的
--   selectList / selectPage 会带上全部实体字段，任何一次查询都会抛
--       Unknown column 'cycle_key' in 'field list'
--   表现为：
--     * 质保档案「删除」失败（删除前需校验是否已有回访任务）→ 500 系统异常；
--     * 回访任务列表 / 详情打不开；
--     * 回访任务生成后无法落库（该表长期为 0 条数据）。
--
-- 【补齐】
--   cycle_key  周期标识：周期型任务为 C1、C2…，到期型为 EXPIRE，手动任务为空；
--              与 plan_id 联合约束「同一回访计划同一周期仅生成一条任务」
--              （应用层校验，见 CrmAfterSaleVisitTaskMapper#selectCountByPlanIdAndCycleKey）
--   biz_no     业务编号：按质保档案独立递增（同一档案下 1、2、3…），仅用于页面展示
--
-- 【特性】幂等，可重复执行（列已存在则跳过）。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
-- ---------------------------------------------------------------------

SET @db = DATABASE();

SET @sql1 = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_task'
                  AND COLUMN_NAME = 'cycle_key') = 0,
    'ALTER TABLE `crm_after_sale_visit_task`
       ADD COLUMN `cycle_key` varchar(16) DEFAULT NULL
         COMMENT ''周期标识：周期型任务为 C1/C2…，到期型为 EXPIRE，手动任务为空'' AFTER `plan_id`',
    'SELECT ''cycle_key 已存在，跳过'' AS msg');
PREPARE s1 FROM @sql1;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @sql2 = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_task'
                  AND COLUMN_NAME = 'biz_no') = 0,
    'ALTER TABLE `crm_after_sale_visit_task`
       ADD COLUMN `biz_no` int DEFAULT NULL
         COMMENT ''业务编号：按质保档案独立递增，仅用于展示'' AFTER `cycle_key`',
    'SELECT ''biz_no 已存在，跳过'' AS msg');
PREPARE s2 FROM @sql2;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- 自查
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'crm_after_sale_visit_task'
  AND COLUMN_NAME IN ('cycle_key', 'biz_no');
