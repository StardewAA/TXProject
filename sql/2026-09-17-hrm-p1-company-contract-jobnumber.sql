-- ============================================================
-- HRM P1：公司信息（用工主体） + 合同模板 + 合同要素扩展
-- 执行库：ruoyi-vue-pro
-- 说明：ALTER 语句只允许执行一次（MySQL 不支持 ADD COLUMN IF NOT EXISTS）
-- ============================================================

-- 【1】公司信息（用工主体）：合同甲方信息来源
CREATE TABLE IF NOT EXISTS `hrm_company_profile` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name`                 varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公司名称（用工主体）',
  `credit_code`          varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一社会信用代码',
  `address`              varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '住所',
  `legal_person`         varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法定代表人',
  `phone`                varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `bank_name`            varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户银行',
  `bank_account`         varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账号',
  `commercial_insurance` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商业保险说明（雇主责任险/团体意外险）',
  `default_status`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否默认主体',
  `sort`                 int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `remark`               varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator`              varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`              varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`            bigint       NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_hrm_company_profile_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 公司信息（用工主体）表';

-- 【2】合同模板：合同/协议模板文件与适用条件
CREATE TABLE IF NOT EXISTS `hrm_contract_template` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name`           varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `contract_type`  tinyint      NOT NULL COMMENT '合同类型，枚举 HrmEmployeeContractTypeEnum',
  `employ_type`    varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用用工形式',
  `version`        int          NOT NULL DEFAULT 1 COMMENT '版本号',
  `status`         tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 启用，1 停用',
  `default_status` bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否该类型默认模板',
  `file_url`       varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板文件地址（docx）',
  `file_hash`      varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板文件 SHA-256',
  `remark`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator`        varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_hrm_contract_template_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 合同模板表';

-- 【3】合同要素扩展（供模板渲染取值）
ALTER TABLE `hrm_employee_contract`
  ADD COLUMN `template_id`          bigint        NULL COMMENT '合同模板编号',
  ADD COLUMN `source_offer_id`      bigint        NULL COMMENT '来源 Offer 编号',
  ADD COLUMN `work_content`         varchar(255)  NULL COMMENT '工作内容（劳务内容）',
  ADD COLUMN `work_location`        varchar(255)  NULL COMMENT '工作地点',
  ADD COLUMN `work_task`            varchar(255)  NULL COMMENT '工作任务（以完成任务为期限时）',
  ADD COLUMN `pay_mode`             tinyint       NULL COMMENT '计薪方式：1 月薪，2 日薪，3 计件',
  ADD COLUMN `pay_amount`           decimal(12,2) NULL COMMENT '计薪金额/单价',
  ADD COLUMN `pay_unit`             varchar(16)   NULL COMMENT '计薪单位，如 元/月、元/日、元/件',
  ADD COLUMN `pay_day`              tinyint       NULL COMMENT '每月发薪日',
  ADD COLUMN `commercial_insurance` varchar(255)  NULL COMMENT '商业保险说明',
  ADD COLUMN `attendance_mode`      varchar(64)   NULL COMMENT '考勤方式';

-- 【4】合同允许在 Offer 阶段先生成草稿（此时员工尚未建档），入职后再回写员工编号
ALTER TABLE `hrm_employee_contract`
  MODIFY COLUMN `employee_id` bigint NULL COMMENT '员工编号；Offer 阶段草稿可为空，入职建档后回写';
