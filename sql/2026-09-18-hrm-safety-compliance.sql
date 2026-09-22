-- ============================================================
-- HRM 第 5 步（第四批·上）：安全合规（三级安全教育 + 体检/职业健康）
--   对应需求文档 R4 的安全教育与体检部分，并为「准入四查」补上两项
--   1) 新建 hrm_safety_education（三级安全教育台账）
--   2) 新建 hrm_health_check（体检 / 职业健康记录）
--   3) HRM 下新增「安全合规」菜单（含教育、体检两个维护权限）
-- 执行库：ruoyi-vue-pro
-- 卡控口径：三级安全教育（公司级/项目级/班组级）未全部合格，或无有效期内合格体检 → 不允许进场。
-- ============================================================

-- ------------------------------------------------------------
-- 【1】三级安全教育台账
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_safety_education` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id`    bigint       NOT NULL COMMENT '员工编号',
  `level`          tinyint      NOT NULL COMMENT '教育级别：1 公司级，2 项目级，3 班组级',
  `education_date` date         NOT NULL COMMENT '教育日期',
  `educator_name`  varchar(64)  DEFAULT NULL COMMENT '教育人',
  `hours`          decimal(5,1) DEFAULT NULL COMMENT '学时',
  `content`        varchar(1000) DEFAULT NULL COMMENT '教育内容',
  `exam_score`     decimal(5,1) DEFAULT NULL COMMENT '考核成绩',
  `qualified`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否合格',
  `file_urls`      varchar(2048) DEFAULT NULL COMMENT '签字表/试卷/照片（逗号分隔地址串）',
  `remark`         varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`        varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 三级安全教育台账';

-- ------------------------------------------------------------
-- 【2】体检 / 职业健康记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hrm_health_check` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id`  bigint       NOT NULL COMMENT '员工编号',
  `check_type`   tinyint      NOT NULL DEFAULT 1 COMMENT '体检类型：1 入职，2 在岗，3 离职，4 职业健康',
  `check_date`   date         NOT NULL COMMENT '体检日期',
  `institution`  varchar(128) DEFAULT NULL COMMENT '体检机构',
  `result`       tinyint      NOT NULL DEFAULT 1 COMMENT '体检结论：1 合格，2 不合格，3 需复查',
  `recheck_date` date         DEFAULT NULL COMMENT '复查日期',
  `valid_until`  date         DEFAULT NULL COMMENT '有效期至（为空表示长期有效）',
  `file_urls`    varchar(2048) DEFAULT NULL COMMENT '体检报告附件（逗号分隔地址串）',
  `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 体检与职业健康记录';

-- ------------------------------------------------------------
-- 【3】菜单：HRM → 安全合规（6793）+ 维护权限（6794/6795）
-- ------------------------------------------------------------
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6793, '安全合规', 'hrm:safety:query', 2, 29, 1476, 'safety', 'ep:warning-filled', 'hrm/safety/index', NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6793);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6794, '安全教育维护', 'hrm:safety:education:update', 3, 1, 6793, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6794);

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6795, '体检记录维护', 'hrm:safety:health:update', 3, 2, 6793, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6795);

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', IFNULL(r.`tenant_id`, 0)
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
JOIN `system_menu` m ON m.`id` IN (6793, 6794, 6795)
WHERE rm.`deleted` = b'0' AND rm.`menu_id` = 1476
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0'
  );

-- 核对
SELECT `id`, `name`, `parent_id`, `permission` FROM `system_menu` WHERE `id` IN (6793, 6794, 6795);
