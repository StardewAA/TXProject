-- ============================================================
-- HRM 手动审批（替换 BPM 审批流）
-- 表：hrm_employee_approval（异动审批申请单）
-- 流程：HR/主管发起 → 待审批 → 有权限的人点「通过/驳回」→ 通过后按类型生效
-- 幂等：可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `hrm_employee_approval` (
  `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id`      bigint       NOT NULL COMMENT '员工编号',
  `employee_name`    varchar(64)  DEFAULT NULL COMMENT '员工姓名（冗余，方便列表展示）',
  `job_number`       varchar(64)  DEFAULT NULL COMMENT '工号（冗余）',
  `type`             tinyint      NOT NULL COMMENT '审批类型：1 转正 2 调岗/晋升 3 离职',
  `reason`           varchar(500) DEFAULT NULL COMMENT '申请说明',
  `effect_time`      datetime     DEFAULT NULL COMMENT '期望生效时间',
  `remark`           varchar(500) DEFAULT NULL COMMENT '备注',
  `status`           tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1 待审批 2 已通过 3 已驳回',
  `applied`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否已生效',
  `applicant_name`   varchar(64)  DEFAULT NULL COMMENT '申请人',
  `apply_time`       datetime     DEFAULT NULL COMMENT '申请时间',
  `approver_name`    varchar(64)  DEFAULT NULL COMMENT '审批人',
  `approve_time`     datetime     DEFAULT NULL COMMENT '审批时间',
  `approve_comment`  varchar(500) DEFAULT NULL COMMENT '审批意见',
  `creator`          varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`          varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`        bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 员工异动审批申请单（手动审批）';
