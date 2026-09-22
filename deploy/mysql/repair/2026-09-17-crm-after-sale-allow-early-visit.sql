-- ---------------------------------------------------------------------
-- 售后设置：新增「是否允许提前提交回访记录」开关 allow_early_visit
--
-- 背景：
--   窗口期截止日期与「是否允许提前提交」都属于售后设置，改配置需立即对存量任务生效，
--   因此判定口径统一改为「按当前配置实时计算」，任务的 window_end_date 仅作留痕快照。
--
-- 判定口径（today = 当前日期，taskDate = 计划回访日期，N = task_overdue_days）：
--   窗口期截止日期         = taskDate + N
--   1) allow_early_visit = 0（默认）
--        today <  taskDate                -> 硬时间锁，【回访完成】按钮置灰，禁止提交
--        taskDate <= today <= taskDate+N  -> 窗口期内，状态正常，允许回访，不标记逾期
--        today >  taskDate + N            -> 状态【已逾期】并标红，仍可提交，提交时弹窗提示
--   2) allow_early_visit = 1
--        不受 taskDate 限制，today < taskDate 也可提交；逾期判定同 1)
-- ---------------------------------------------------------------------

-- 1. 新增字段（默认 0 = 不允许提前提交，保持原有硬时间锁行为）
ALTER TABLE `crm_after_sale_config`
  ADD COLUMN `allow_early_visit` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许提前提交回访记录：0=不允许（未到计划回访日期禁止提交）1=允许' AFTER `task_overdue_days`;

-- 2. 说明：窗口期截止日期已改为按配置实时计算，任务上的 window_end_date 仅作留痕快照，
--    不再参与锁定/逾期判定，历史数据无需回填。
