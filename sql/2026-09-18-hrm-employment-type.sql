-- ============================================================
-- HRM P0-3：用工形式（需求文档 R5）
--   1) 新建「用工形式」字典表 hrm_employment_type
--   2) hrm_employee 增加 employment_type_id（员工当前用工形式）
--   3) 「员工管理」下新增菜单「用工形式」（含 4 个权限按钮）
--   4) 种子 10 类行业用工形式，并按合同模板的 employ_type 自动绑定默认模板
-- 执行库：ruoyi-vue-pro
-- 说明：ALTER 与 CREATE 不支持重复执行（重复执行会报已存在，可忽略）；
--       数据部分使用 WHERE NOT EXISTS 保护，可重复执行。
-- ============================================================

-- ------------------------------------------------------------
-- 【1】用工形式字典表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_employment_type` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name`                 varchar(64)  NOT NULL COMMENT '用工形式名称',
  `code`                 varchar(32)  NOT NULL COMMENT '用工形式编码',
  `contract_template_id` bigint       DEFAULT NULL COMMENT '默认合同模板编号（hrm_contract_template.id）',
  `salary_mode`          tinyint      NOT NULL DEFAULT 1 COMMENT '默认计薪方式：1 月薪，2 日薪，3 计件，4 月薪+计件',
  `need_work_day`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否按工日统计出勤',
  `need_face_clock`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否要求人脸打卡',
  `join_regular`         bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否参与自动转正',
  `sort`                 int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 启用，1 停用',
  `remark`               varchar(255) DEFAULT NULL COMMENT '备注',
  `creator`              varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`              varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 用工形式字典';

-- ------------------------------------------------------------
-- 【2】员工档案扩展：当前用工形式
-- ------------------------------------------------------------
ALTER TABLE `hrm_employee`
  ADD COLUMN `employment_type_id` bigint DEFAULT NULL COMMENT '用工形式编号（hrm_employment_type.id）' AFTER `type`;

-- ------------------------------------------------------------
-- 【3】菜单：员工管理 → 用工形式（6764）+ 权限按钮（6765~6768）
-- ------------------------------------------------------------
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6764, '用工形式', 'hrm:employment-type:query', 2, 3, 1477, 'employment-type', 'ep:files', 'hrm/employee/employment-type/index', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6764);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6765, '用工形式新增', 'hrm:employment-type:create', 3, 1, 6764, '', '', '', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6765);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6766, '用工形式修改', 'hrm:employment-type:update', 3, 2, 6764, '', '', '', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6766);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6767, '用工形式删除', 'hrm:employment-type:delete', 3, 3, 6764, '', '', '', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6767);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6768, '用工形式导出', 'hrm:employment-type:export', 3, 4, 6764, '', '', '', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6768);

-- 给已经拥有「员工管理」菜单的角色补授新菜单与按钮，避免新菜单不可见
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.`tenant_id`, 0)
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
JOIN `system_menu` m ON m.`id` IN (6764, 6765, 6766, 6767, 6768)
WHERE rm.`deleted` = b'0' AND rm.`menu_id` = 1477
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0'
  );

-- ------------------------------------------------------------
-- 【4】种子：10 类防腐保温行业用工形式（租户 2020 秦川）
--     默认合同模板先留空，随后按模板的 employ_type 自动绑定
-- ------------------------------------------------------------
INSERT INTO `hrm_employment_type`
  (`name`, `code`, `salary_mode`, `need_work_day`, `need_face_clock`, `join_regular`, `sort`, `status`, `remark`, `tenant_id`, `creator`, `updater`)
SELECT t.`name`, t.`code`, t.`salary_mode`, t.`need_work_day`, t.`need_face_clock`, t.`join_regular`, t.`sort`, 0, t.`remark`, 2020, 'admin', 'admin'
FROM (
  SELECT '正式员工' AS `name`, 'FORMAL' AS `code`, 1 AS `salary_mode`, b'0' AS `need_work_day`, b'0' AS `need_face_clock`, b'1' AS `join_regular`, 1 AS `sort`, '长期固定用工，签订固定期限劳动合同' AS `remark`
  UNION ALL SELECT '正式长期', 'FORMAL_LONG', 1, b'0', b'0', b'1', 2, '签订无固定期限劳动合同'
  UNION ALL SELECT '临时工', 'TEMPORARY', 2, b'1', b'1', b'0', 3, '项目制短工，以完成一定工作任务为期限'
  UNION ALL SELECT '劳务用工', 'LABOR', 1, b'0', b'0', b'0', 4, '签订劳务合同，按劳务关系管理'
  UNION ALL SELECT '劳务派遣', 'DISPATCH', 1, b'0', b'0', b'0', 5, '派遣单位派入，签订劳务派遣劳动合同'
  UNION ALL SELECT '兼职', 'PART_TIME', 2, b'1', b'0', b'0', 6, '非全日制用工，按日计酬'
  UNION ALL SELECT '实习', 'INTERN', 1, b'1', b'0', b'0', 7, '在校生实习，签实习/三方协议'
  UNION ALL SELECT '返聘', 'REHIRE', 1, b'0', b'0', b'0', 8, '退休返聘人员'
  UNION ALL SELECT '日结工', 'DAILY', 2, b'1', b'1', b'0', 9, '当日结算，按工日计酬，需人脸打卡'
  UNION ALL SELECT '计件工', 'PIECE', 3, b'1', b'1', b'0', 10, '按工程量计件计酬'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `hrm_employment_type` x WHERE x.`tenant_id` = 2020 AND x.`code` = t.`code` AND x.`deleted` = b'0'
);

-- 按模板上维护的「适用用工形式」自动绑定默认合同模板
UPDATE `hrm_employment_type` e
JOIN (
  SELECT 'FORMAL' AS `code`, '正式员工' AS `employ_type`
  UNION ALL SELECT 'FORMAL_LONG', '正式长期'
  UNION ALL SELECT 'LABOR', '劳务'
  UNION ALL SELECT 'DISPATCH', '劳务派遣'
  UNION ALL SELECT 'PART_TIME', '兼职'
) m ON m.`code` = e.`code`
JOIN (
  SELECT `tenant_id`, `employ_type`, MIN(`id`) AS `template_id`
  FROM `hrm_contract_template`
  WHERE `deleted` = b'0' AND `category` = 1 AND `employ_type` IS NOT NULL
  GROUP BY `tenant_id`, `employ_type`
) t ON t.`tenant_id` = e.`tenant_id` AND t.`employ_type` = m.`employ_type`
SET e.`contract_template_id` = t.`template_id`, e.`updater` = 'admin', e.`update_time` = NOW()
WHERE e.`deleted` = b'0' AND e.`contract_template_id` IS NULL;

-- 返聘没有对应的标准合同模板，保持为空由 HR 自行绑定；
-- 若误绑成「试用期劳动合同」，这里纠正回空（防止返聘人员签试用期合同）
UPDATE `hrm_employment_type` e
JOIN `hrm_contract_template` t ON t.`id` = e.`contract_template_id`
SET e.`contract_template_id` = NULL, e.`updater` = 'admin', e.`update_time` = NOW()
WHERE e.`deleted` = b'0' AND e.`code` = 'REHIRE' AND t.`name` = '试用期劳动合同';

-- 临时工按需求文档指定「以完成一定工作任务为期限劳动合同」
UPDATE `hrm_employment_type` e
JOIN `hrm_contract_template` t
  ON t.`tenant_id` = e.`tenant_id` AND t.`deleted` = b'0'
 AND t.`name` = '以完成一定工作任务为期限劳动合同'
SET e.`contract_template_id` = t.`id`, e.`updater` = 'admin', e.`update_time` = NOW()
WHERE e.`deleted` = b'0' AND e.`code` = 'TEMPORARY';

-- ------------------------------------------------------------
-- 【5】核对
-- ------------------------------------------------------------
SELECT e.`id`, e.`name`, e.`code`, e.`salary_mode`, e.`need_work_day`, e.`need_face_clock`,
       e.`join_regular`, e.`contract_template_id`, t.`name` AS `template_name`
FROM `hrm_employment_type` e
LEFT JOIN `hrm_contract_template` t ON t.`id` = e.`contract_template_id`
WHERE e.`deleted` = b'0'
ORDER BY e.`sort`;
