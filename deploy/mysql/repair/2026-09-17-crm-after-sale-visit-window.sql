-- ---------------------------------------------------------------------
-- 售后回访任务：新增「窗口期截止日期」window_end_date
--
-- 规则：
--   计划回访日期        = 上次回访完成时间 + 回访周期
--   窗口期截止日期      = 计划回访日期 + 回访任务逾期判定天数(task_overdue_days)
--
-- 三段式判定（today = 当前日期）：
--   1) today <  task_date        -> 硬时间锁，【回访完成】按钮置灰，禁止提交回访记录
--   2) task_date <= today <= window_end_date -> 窗口期内，状态「待处理」，允许正常回访，不标记逾期
--   3) today >  window_end_date  -> 状态「已逾期」并标红，按钮仍可点击，提交时弹窗提示已逾期
-- ---------------------------------------------------------------------

-- 1. 新增字段
ALTER TABLE `crm_after_sale_visit_task`
  ADD COLUMN `window_end_date` date DEFAULT NULL COMMENT '窗口期截止日期 = 计划执行日期 + 回访任务逾期判定天数；当前日期超过该日期判定为已逾期' AFTER `task_date`;

-- 2. 历史数据回填：按「回访任务逾期判定天数」补算窗口期截止日期
--    售后设置为单行配置（应用侧固定按 id = 1 读取，见 CrmAfterSaleConfigServiceImpl#getConfig），
--    因此这里同样按 id = 1 取值；取不到时回落到系统默认值 3 天。
UPDATE `crm_after_sale_visit_task` t
SET t.`window_end_date` = DATE_ADD(
    t.`task_date`,
    INTERVAL COALESCE(
        (SELECT c.`task_overdue_days` FROM `crm_after_sale_config` c WHERE c.`id` = 1 LIMIT 1), 3) DAY)
WHERE t.`window_end_date` IS NULL;

-- 3. 校验：应无未回填的未完成任务
-- SELECT id, task_date, window_end_date, `status`
-- FROM `crm_after_sale_visit_task`
-- WHERE `window_end_date` IS NULL AND `status` <> 2;
