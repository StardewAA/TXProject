-- =============================================================
-- HRM 全业务流程测试报告 · P3 / 前端专项修复 配套 SQL（2026-09-20）
-- 1) L-01：注册「Offer 过期处理」XXL-Job 调度任务
-- 2) F-02：用工主体信息独立权限码（不再复用 hrm:employee:update）
-- 说明：L-02（取消离职原因落库）复用 hrm_employee_change_record 表，无需 DDL
-- =============================================================

-- ---------- 1) L-01 Offer 过期定时任务 ----------
INSERT INTO `xxl_job`.`xxl_job_info`
    (`job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
     `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
     `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
     `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`,
     `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
SELECT 2, 'HRM-Offer过期处理(每小时)', NOW(), NOW(), 'OPENLAB', '',
       'CRON', '0 0 * * * ?', 'DO_NOTHING', 'FIRST',
       'hrmRecruitOfferExpireJob', '', 'SERIAL_EXECUTION', 0,
       0, 'BEAN', '', NULL, NOW(),
       '', 1, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM `xxl_job`.`xxl_job_info` WHERE `executor_handler` = 'hrmRecruitOfferExpireJob'
);

-- ---------- 2) F-02 用工主体信息独立权限 ----------
-- 用工主体信息菜单（父菜单）编号 6749
INSERT INTO `ruoyi-vue-pro`.`system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
     `updater`, `update_time`, `deleted`)
SELECT 6991, '用工主体查询', 'hrm:company-profile:query', 3, 1, 6749, '', '#', '',
       '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_menu` WHERE `permission` = 'hrm:company-profile:query'
);

INSERT INTO `ruoyi-vue-pro`.`system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
     `updater`, `update_time`, `deleted`)
SELECT 6992, '用工主体维护', 'hrm:company-profile:update', 3, 2, 6749, '', '#', '',
       '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_menu` WHERE `permission` = 'hrm:company-profile:update'
);

-- 把新权限授予「原本拥有 hrm:employee:update」的角色，保证升级后无人丢权限
INSERT INTO `ruoyi-vue-pro`.`system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT src.role_id, target.id, '1', NOW(), '1', NOW(), b'0', src.tenant_id
FROM (
    SELECT rm.role_id, r.tenant_id
    FROM `ruoyi-vue-pro`.`system_role_menu` rm
    JOIN `ruoyi-vue-pro`.`system_role` r ON r.id = rm.role_id
    WHERE rm.menu_id = 1481 AND rm.deleted = b'0'
    GROUP BY rm.role_id, r.tenant_id
) src
JOIN `ruoyi-vue-pro`.`system_menu` target
  ON target.permission IN ('hrm:company-profile:query', 'hrm:company-profile:update')
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_role_menu` exist
    WHERE exist.role_id = src.role_id AND exist.menu_id = target.id
);
