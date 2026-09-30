-- HRM 资格证件域增量脚本。
--
-- 目标（对应《HRM人力资源系统功能设计文档》5.7 节 UI-CER-01 / UI-CER-02、6.2 节 API-CER-01、7 节每日任务）：
-- 1. 证件台账主表 hrm_employee_certificate 补齐证件类型、复审日期、附件与状态字段；
-- 2. 新增证件类型配置表 hrm_certificate_type；
-- 3. 新增续证/复审历史表 hrm_certificate_renewal（只追加，不覆盖原证件）；
-- 4. 新增预警记录表 hrm_certificate_warning（按「证件 + 预警类型 + 预警日期」去重）；
-- 5. 补插站内信模板与「资格证件」菜单权限，并授予现有 HR 角色。
--
-- 幂等说明：建表使用 IF NOT EXISTS；菜单与模板使用 WHERE NOT EXISTS 兜底；
-- MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，重复执行会报重复列，属预期行为。

-- ----------------------------------------------------------------------
-- 1. 证件台账主表补齐资格证件域字段
-- ----------------------------------------------------------------------
ALTER TABLE `hrm_employee_certificate`
  ADD COLUMN `type_id` bigint DEFAULT NULL COMMENT '证件类型编号，关联 hrm_certificate_type.id',
  ADD COLUMN `review_time` datetime DEFAULT NULL COMMENT '复审日期',
  ADD COLUMN `attachment_url` varchar(512) DEFAULT NULL COMMENT '证件附件地址',
  ADD COLUMN `status` tinyint DEFAULT NULL COMMENT '证件状态：1 有效，2 即将到期，3 过期，4 注销',
  ADD INDEX `idx_hrm_employee_certificate_type_id` (`type_id` ASC),
  ADD INDEX `idx_hrm_employee_certificate_status` (`status` ASC);

-- ----------------------------------------------------------------------
-- 2. 证件类型表
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_certificate_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '证件类型编号',
  `name` varchar(128) NOT NULL COMMENT '证件类型名称',
  `code` varchar(64) DEFAULT NULL COMMENT '证件类型编码',
  `special_operation` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否特种作业',
  `need_review` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要复审',
  `review_cycle_months` int DEFAULT NULL COMMENT '复审周期（月）',
  `advance_days` int NOT NULL DEFAULT 30 COMMENT '默认提前预警天数',
  `require_attachment` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必须上传证件附件',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 开启，1 关闭',
  `sort` int DEFAULT NULL COMMENT '显示顺序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hrm_certificate_type_status` (`status`),
  KEY `idx_hrm_certificate_type_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 证件类型表';

-- ----------------------------------------------------------------------
-- 3. 证件续证/复审历史表
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_certificate_renewal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '续证/复审记录编号',
  `certificate_id` bigint NOT NULL COMMENT '证件台账编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `type` tinyint NOT NULL COMMENT '类型：1 续证，2 复审',
  `old_no` varchar(255) DEFAULT NULL COMMENT '办理前证件编号',
  `new_no` varchar(255) DEFAULT NULL COMMENT '办理后证件编号',
  `old_start_time` datetime DEFAULT NULL COMMENT '办理前有效开始日期',
  `old_end_time` datetime DEFAULT NULL COMMENT '办理前有效结束日期',
  `new_start_time` datetime DEFAULT NULL COMMENT '办理后有效开始日期',
  `new_end_time` datetime DEFAULT NULL COMMENT '办理后有效结束日期',
  `review_time` datetime DEFAULT NULL COMMENT '复审日期',
  `issuing_authority` varchar(255) DEFAULT NULL COMMENT '发证机构',
  `issuing_time` datetime DEFAULT NULL COMMENT '发证日期',
  `attachment_url` varchar(512) DEFAULT NULL COMMENT '新证件附件地址',
  `operator_user_id` bigint DEFAULT NULL COMMENT '经办人用户编号',
  `renew_time` datetime DEFAULT NULL COMMENT '办理时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hrm_certificate_renewal_certificate_id` (`certificate_id`),
  KEY `idx_hrm_certificate_renewal_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 证件续证/复审记录表';

-- ----------------------------------------------------------------------
-- 4. 证件预警记录表
--    去重键为「证件 + 预警类型 + 预警日期」，由应用层保证；这里建组合索引支撑幂等查询。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_certificate_warning` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预警记录编号',
  `certificate_id` bigint NOT NULL COMMENT '证件台账编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `type` tinyint NOT NULL COMMENT '预警类型：1 证件到期，2 证件复审',
  `warning_date` date NOT NULL COMMENT '预警触发日期',
  `expire_date` datetime DEFAULT NULL COMMENT '到期日期或复审日期',
  `remain_days` int DEFAULT NULL COMMENT '剩余天数，负数表示已过期',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0 待处理，1 已处理，2 已忽略',
  `handle_user_id` bigint DEFAULT NULL COMMENT '处理人用户编号',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理意见',
  `notify_status` tinyint NOT NULL DEFAULT 0 COMMENT '通知状态：0 待通知，1 已通知，2 通知失败',
  `notify_time` datetime DEFAULT NULL COMMENT '通知时间',
  `notify_fail_reason` varchar(500) DEFAULT NULL COMMENT '通知失败原因',
  `notify_retry_count` int NOT NULL DEFAULT 0 COMMENT '通知重试次数',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hrm_certificate_warning_unique` (`certificate_id`, `type`, `warning_date`),
  KEY `idx_hrm_certificate_warning_status` (`status`),
  KEY `idx_hrm_certificate_warning_notify` (`notify_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 证件预警记录表';

-- ----------------------------------------------------------------------
-- 5. 站内信模板兜底（缺省自动补插，幂等）
--    变量：{employeeName} {warningType} {expireDate} {remainDays}
-- ----------------------------------------------------------------------
INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '证件到期与复审预警', 'hrm_certificate_warning', 'HRM 人力资源',
       '您好，{employeeName}，您的证件【{warningType}】预警：到期/复审日期为 {expireDate}，剩余 {remainDays} 天。请及时办理续证或复审，避免证件失效影响上岗资格核查。',
       2, '["employeeName","warningType","expireDate","remainDays"]', 0, '资格证件域：证件到期与复审预警提醒',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'hrm_certificate_warning' AND `deleted` = b'0');

-- ----------------------------------------------------------------------
-- 6. 菜单与权限点
--    目录与页面按 (name, parent_id) 判重，按钮按 (permission, parent_id) 判重，重复执行不产生重复数据。
-- ----------------------------------------------------------------------
CREATE TEMPORARY TABLE tmp_hrm_certificate_pages (
    name VARCHAR(50) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    sort INT NOT NULL,
    path VARCHAR(100) NOT NULL,
    icon VARCHAR(50) NOT NULL,
    component VARCHAR(200) NOT NULL,
    component_name VARCHAR(100) NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_certificate_pages (name, permission, sort, path, icon, component, component_name) VALUES
    ('证件台账', 'hrm:certificate:query', 1, 'ledger', 'ep:document', 'hrm/certificate/ledger/index', 'HrmCertificateLedger'),
    ('证件类型', 'hrm:certificate:query', 2, 'type', 'ep:collection-tag', 'hrm/certificate/type/index', 'HrmCertificateType'),
    ('到期预警', 'hrm:certificate:warning:query', 3, 'warning', 'ep:warning', 'hrm/certificate/warning/index', 'HrmCertificateWarning');

CREATE TEMPORARY TABLE tmp_hrm_certificate_buttons (
    permission VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort INT NOT NULL,
    parent_name VARCHAR(50) NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_certificate_buttons (permission, name, sort, parent_name) VALUES
    ('hrm:certificate:query', '证件台账查询', 1, '证件台账'),
    ('hrm:certificate:create', '证件台账新增', 2, '证件台账'),
    ('hrm:certificate:update', '证件台账修改', 3, '证件台账'),
    ('hrm:certificate:delete', '证件台账删除', 4, '证件台账'),
    ('hrm:certificate:renewal', '证件续证复审', 5, '证件台账'),
    ('hrm:certificate:warning:query', '证件预警查询', 1, '到期预警'),
    ('hrm:certificate:warning:handle', '证件预警处理', 2, '到期预警');

-- 6.1 一级目录「资格证件」，挂在「HRM 人力资源」下
INSERT INTO system_menu (
    name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT '资格证件', '', 1, 25, parent.id, 'certificate', 'ep:postcard', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.name = 'HRM 人力资源' AND parent.type = 1 AND parent.parent_id = 0 AND parent.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_menu existing
      WHERE existing.name = '资格证件' AND existing.parent_id = parent.id AND existing.deleted = b'0'
  );

-- 6.2 二级页面
INSERT INTO system_menu (
    name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT item.name, item.permission, 2, item.sort, parent.id, item.path, item.icon, item.component, item.component_name,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM tmp_hrm_certificate_pages item
JOIN system_menu parent
  ON parent.name = '资格证件' AND parent.type = 1 AND parent.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu existing
    WHERE existing.name = item.name AND existing.parent_id = parent.id AND existing.deleted = b'0'
);

-- 6.3 页面按钮权限点
INSERT INTO system_menu (
    name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT item.name, item.permission, 3, item.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM tmp_hrm_certificate_buttons item
JOIN system_menu parent
  ON parent.name = item.parent_name AND parent.type = 2 AND parent.deleted = b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu existing
    WHERE existing.permission = item.permission AND existing.parent_id = parent.id AND existing.deleted = b'0'
);

-- 6.4 授予所有已使用 HRM 的角色
--     口径：凡是已经持有「员工档案」菜单权限的角色，都补授本次新增的证件域菜单，
--     与库内「所有在用租户的角色均持有全套 HRM 菜单」的既有分布保持一致，
--     避免只授权 hr_role_2019~2024 而导致其余租户看不到新菜单。
INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT DISTINCT base_role_menu.role_id, target_menu.id, 'admin', NOW(), 'admin', NOW(), b'0',
       base_role_menu.tenant_id
FROM system_role_menu base_role_menu
JOIN system_menu base_menu
  ON base_menu.id = base_role_menu.menu_id
 AND base_menu.deleted = b'0'
 AND base_menu.permission = 'hrm:employee:query'
JOIN system_menu target_menu
  ON target_menu.deleted = b'0'
 AND (target_menu.permission LIKE 'hrm:certificate%'
      OR target_menu.name IN ('资格证件', '证件台账', '证件类型', '到期预警'))
WHERE base_role_menu.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id = base_role_menu.role_id
        AND existing.menu_id = target_menu.id
        AND existing.tenant_id = base_role_menu.tenant_id
        AND existing.deleted = b'0'
  );

DROP TEMPORARY TABLE tmp_hrm_certificate_pages;
DROP TEMPORARY TABLE tmp_hrm_certificate_buttons;
