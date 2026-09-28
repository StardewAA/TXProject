-- =============================================================
-- 注册「HRM 员工档案 ↔ 后台账号 自动绑定」定时任务（口径A：档案为准）
-- 幂等：可重复执行
-- =============================================================

INSERT INTO `xxl_job`.`xxl_job_info`
    (`job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
     `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
     `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
     `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`,
     `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
SELECT 2, 'HRM-员工档案与账号自动绑定(每天)', NOW(), NOW(), 'OPENLAB', '',
       'CRON', '0 30 1 * * ?', 'DO_NOTHING', 'FIRST',
       'hrmEmployeeAccountBindJob', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', NULL, NOW(),
       '', 1, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job`.`xxl_job_info` WHERE `executor_handler` = 'hrmEmployeeAccountBindJob'
);
