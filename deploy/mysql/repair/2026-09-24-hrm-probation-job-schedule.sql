-- 与 deploy/mysql/repair/2026-09-24-hrm-probation-job-schedule.sql 内容一致
-- HRM：试用期到期提醒 Job 的 XXL-Job 调度配置（补齐 3.2 的调度端，否则 handler 永远不跑）
-- 说明：handler `hrmEmployeeProbationExpireJob` 已在 HRM 服务里实现并注册执行器（hrm-server），
--       但 xxl_job_info 缺少对应调度行，导致「每日扫描试用期到期员工并提醒」实际不会执行。

INSERT INTO `xxl_job`.`xxl_job_info`
  (`job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
   `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
   `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
   `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`,
   `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
SELECT 2, 'HRM-试用期到期提醒(每日9:10)', NOW(), NOW(), 'hrm-dev', '',
       'CRON', '0 10 9 * * ?', 'DO_NOTHING', 'FIRST',
       'hrmEmployeeProbationExpireJob', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', '试用期到期提醒（提前 7 天）', NOW(),
       '', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `xxl_job`.`xxl_job_info`
                  WHERE `executor_handler` = 'hrmEmployeeProbationExpireJob');

-- 校验：应返回 1 行（trigger_status=1）
-- SELECT id, job_desc, executor_handler, schedule_conf, trigger_status
-- FROM xxl_job.xxl_job_info WHERE executor_handler = 'hrmEmployeeProbationExpireJob';
