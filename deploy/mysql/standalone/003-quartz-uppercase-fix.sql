-- =====================================================================
-- Quartz 表名大小写修复（新库初始化时执行）
--
-- 背景：0069 全库 dump 中的 Quartz 表为小写 qrtz_*，而 Quartz 在
--       MySQL（lower_case_table_names=0）下按大写 QRTZ_* 查询，
--       导致调度器启动后持续报错：
--         Table 'ruoyi-vue-pro.QRTZ_LOCKS' doesn't exist
--
-- 方案：将小写表改名为大写（与 002-flowable-uppercase-fix.sql 同理）。
-- =====================================================================

RENAME TABLE `qrtz_blob_triggers`      TO `QRTZ_BLOB_TRIGGERS`;
RENAME TABLE `qrtz_calendars`          TO `QRTZ_CALENDARS`;
RENAME TABLE `qrtz_cron_triggers`      TO `QRTZ_CRON_TRIGGERS`;
RENAME TABLE `qrtz_fired_triggers`     TO `QRTZ_FIRED_TRIGGERS`;
RENAME TABLE `qrtz_job_details`        TO `QRTZ_JOB_DETAILS`;
RENAME TABLE `qrtz_locks`              TO `QRTZ_LOCKS`;
RENAME TABLE `qrtz_paused_trigger_grps` TO `QRTZ_PAUSED_TRIGGER_GRPS`;
RENAME TABLE `qrtz_scheduler_state`    TO `QRTZ_SCHEDULER_STATE`;
RENAME TABLE `qrtz_simple_triggers`    TO `QRTZ_SIMPLE_TRIGGERS`;
RENAME TABLE `qrtz_simprop_triggers`   TO `QRTZ_SIMPROP_TRIGGERS`;
RENAME TABLE `qrtz_triggers`           TO `QRTZ_TRIGGERS`;
