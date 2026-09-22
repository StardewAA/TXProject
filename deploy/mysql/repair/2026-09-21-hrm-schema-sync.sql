-- 自动生成：HRM 缺失表结构与缺失列（来源：sql/hrm_202609201427.sql 全库快照）
-- 只做 CREATE/ALTER/INSERT，幂等可重复执行

SET NAMES utf8mb4;

-- ============ 1. 缺失表 ============
-- hrm_company_profile
CREATE TABLE IF NOT EXISTS `hrm_company_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公司名称（用工主体）',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一社会信用代码',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '住所',
  `legal_person` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法定代表人',
  `establish_date` date DEFAULT NULL COMMENT '成立日期',
  `registered_capital` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '注册资本',
  `company_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '企业类型',
  `business_scope` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '经营范围',
  `insured_count` int DEFAULT NULL COMMENT '参保人数',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `bank_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账号',
  `commercial_insurance` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商业保险说明（雇主责任险/团体意外险）',
  `default_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认主体',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_hrm_company_profile_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 公司信息（用工主体）表';;

-- hrm_contract_sign_record
CREATE TABLE IF NOT EXISTS `hrm_contract_sign_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号（hrm_employee_contract.id）',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号（冗余，便于台账展示）',
  `sign_employer_id` bigint DEFAULT NULL COMMENT '签署用工主体编号（hrm_company_profile.id）',
  `sign_employer_name` varchar(128) DEFAULT NULL COMMENT '签署用工主体名称（冗余）',
  `sign_person` varchar(64) DEFAULT NULL COMMENT '签署人（乙方签署人姓名）',
  `sign_way` tinyint DEFAULT NULL COMMENT '签署方式：1 线下签署，2 电子签章',
  `sign_time` datetime DEFAULT NULL COMMENT '签署时间',
  `file_urls` varchar(2048) DEFAULT NULL COMMENT '签署件附件地址列表',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 待签，1 已签，2 已作废',
  `urge_count` int NOT NULL DEFAULT '0' COMMENT '催办次数',
  `last_urge_time` datetime DEFAULT NULL COMMENT '最后催办时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_employee_id` (`employee_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 合同签署记录';;

-- hrm_contract_template
CREATE TABLE IF NOT EXISTS `hrm_contract_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `category` tinyint NOT NULL DEFAULT '1' COMMENT '模板分类：1 劳动合同，2 个人电子档案，3 培训协议',
  `contract_type` tinyint DEFAULT NULL COMMENT '合同类型（仅劳动合同分类使用）',
  `employ_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用用工形式',
  `employer_id` bigint DEFAULT NULL COMMENT '适用用工主体编号（hrm_company_profile.id）',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 启用，1 停用',
  `default_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否该类型默认模板',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序（同分类内排序）',
  `source_dir` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源目录（批量导入时保留，便于追溯）',
  `file_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板文件地址（docx）',
  `thumbnail_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缩略图地址（PNG，列表卡片展示）',
  `preview_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预览文件地址（PDF，浏览器内嵌预览）',
  `file_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板文件 SHA-256',
  `variables` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板变量清单（逗号分隔，如 ${companyName},${employeeName}）',
  `change_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '本次变更说明',
  `change_user_id` bigint DEFAULT NULL COMMENT '变更人编号',
  `change_time` datetime DEFAULT NULL COMMENT '变更时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_hrm_contract_template_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 合同模板表';;

-- hrm_dept_headcount
CREATE TABLE IF NOT EXISTS `hrm_dept_headcount` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `dept_id` bigint NOT NULL COMMENT '部门编号（关联 system 部门）',
  `headcount` int NOT NULL DEFAULT '0' COMMENT '编制人数',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 部门编制';;

-- hrm_employee_approval
CREATE TABLE IF NOT EXISTS `hrm_employee_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `employee_name` varchar(64) DEFAULT NULL COMMENT '员工姓名（冗余，方便列表展示）',
  `job_number` varchar(64) DEFAULT NULL COMMENT '工号（冗余）',
  `type` tinyint NOT NULL COMMENT '审批类型：1 转正 2 调岗/晋升 3 离职',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请说明',
  `effect_time` datetime DEFAULT NULL COMMENT '期望生效时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 待审批 2 已通过 3 已驳回',
  `applied` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已生效',
  `applicant_name` varchar(64) DEFAULT NULL COMMENT '申请人',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `approver_name` varchar(64) DEFAULT NULL COMMENT '审批人',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_comment` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 员工异动审批申请单（手动审批）';;

-- hrm_employee_blacklist
CREATE TABLE IF NOT EXISTS `hrm_employee_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id` bigint DEFAULT NULL COMMENT '员工编号（可空）',
  `name` varchar(64) NOT NULL COMMENT '姓名',
  `mobile` varchar(32) NOT NULL COMMENT '手机号（命中校验主要依据）',
  `id_card_no` varchar(32) DEFAULT NULL COMMENT '身份证号',
  `reason` varchar(500) NOT NULL COMMENT '加入原因',
  `source` tinyint DEFAULT '1' COMMENT '来源：1 HRM 手工登记 2 现场上报 3 其他',
  `join_time` datetime DEFAULT NULL COMMENT '加入时间',
  `release_time` datetime DEFAULT NULL COMMENT '解除时间',
  `permanent_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否永久',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 生效 2 已解除',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_mobile` (`mobile`,`status`),
  KEY `idx_name` (`name`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 员工黑名单';;

-- hrm_employee_no_rule
CREATE TABLE IF NOT EXISTS `hrm_employee_no_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则编号',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `tenant_abbr` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户缩写（工号前缀）',
  `serial_length` tinyint NOT NULL DEFAULT '6' COMMENT '顺序号位数（左补零）',
  `prefix_separator` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '前缀与序号之间的分隔符，默认空（避免使用保留字 separator）',
  `reset_period` tinyint NOT NULL DEFAULT '0' COMMENT '重置周期：0 不重置，1 按年重置',
  `allow_manual` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许人工指定工号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 启用，1 停用',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_hrm_employee_no_rule_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 工号生成规则表';;

-- hrm_employee_no_sequence
CREATE TABLE IF NOT EXISTS `hrm_employee_no_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `rule_id` bigint NOT NULL COMMENT '规则编号',
  `period_key` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL' COMMENT '周期键：ALL 或年份',
  `current_value` bigint NOT NULL DEFAULT '0' COMMENT '当前已使用的序号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_hrm_employee_no_sequence` (`tenant_id`,`rule_id`,`period_key`,`deleted`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 工号序号表';;

-- hrm_employment_type
CREATE TABLE IF NOT EXISTS `hrm_employment_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(64) NOT NULL COMMENT '用工形式名称',
  `code` varchar(32) NOT NULL COMMENT '用工形式编码',
  `contract_template_id` bigint DEFAULT NULL COMMENT '默认合同模板编号（hrm_contract_template.id）',
  `salary_mode` tinyint NOT NULL DEFAULT '1' COMMENT '默认计薪方式：1 月薪，2 日薪，3 计件，4 月薪+计件',
  `need_work_day` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否按工日统计出勤',
  `need_face_clock` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否要求人脸打卡',
  `join_regular` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否参与自动转正',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 启用，1 停用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 用工形式字典';;

-- hrm_health_check
CREATE TABLE IF NOT EXISTS `hrm_health_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `check_type` tinyint NOT NULL DEFAULT '1' COMMENT '体检类型：1 入职，2 在岗，3 离职，4 职业健康',
  `check_date` date NOT NULL COMMENT '体检日期',
  `institution` varchar(128) DEFAULT NULL COMMENT '体检机构',
  `result` tinyint NOT NULL DEFAULT '1' COMMENT '体检结论：1 合格，2 不合格，3 需复查',
  `recheck_date` date DEFAULT NULL COMMENT '复查日期',
  `valid_until` date DEFAULT NULL COMMENT '有效期至（为空表示长期有效）',
  `file_urls` varchar(2048) DEFAULT NULL COMMENT '体检报告附件（逗号分隔地址串）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 体检与职业健康记录';;

-- hrm_policy
CREATE TABLE IF NOT EXISTS `hrm_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '政策编号',
  `name` varchar(100) NOT NULL COMMENT '政策名称',
  `category` varchar(32) NOT NULL DEFAULT 'other' COMMENT '类别：social_insurance 社保，provident_fund 公积金，labor_contract 劳动合同，training 培训，other 其他',
  `doc_no` varchar(64) DEFAULT NULL COMMENT '文号',
  `publish_org` varchar(100) DEFAULT NULL COMMENT '发布机关',
  `publish_date` date DEFAULT NULL COMMENT '发布日期',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expire_date` date DEFAULT NULL COMMENT '失效日期',
  `scope` varchar(16) NOT NULL DEFAULT 'tenant' COMMENT '适用范围：group 集团，tenant 租户，region 地区',
  `scope_value` varchar(64) DEFAULT NULL COMMENT '范围明细（地区名称等）',
  `content` text COMMENT '政策正文',
  `file_urls` json DEFAULT NULL COMMENT '附件地址（数组）',
  `tags` json DEFAULT NULL COMMENT '标签（数组）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 启用，1 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_hrm_policy_category` (`category`),
  KEY `idx_hrm_policy_expire` (`expire_date`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 政策库';;

-- hrm_recruit_demand
CREATE TABLE IF NOT EXISTS `hrm_recruit_demand` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '需求编号',
  `demand_no` varchar(64) NOT NULL COMMENT '需求单号（REQ-yyyyMMdd-序号）',
  `demand_name` varchar(128) NOT NULL COMMENT '需求名称',
  `dept_id` bigint DEFAULT NULL COMMENT '用工部门/项目部编号',
  `company_profile_id` bigint DEFAULT NULL COMMENT '用工主体编号',
  `work_type` varchar(64) DEFAULT NULL COMMENT '工种',
  `cert_require` varchar(512) DEFAULT NULL COMMENT '证照要求（多个用、分隔）',
  `headcount` int NOT NULL DEFAULT '0' COMMENT '需求人数',
  `onboard_count` int NOT NULL DEFAULT '0' COMMENT '已到岗人数',
  `plan_entry_date` date DEFAULT NULL COMMENT '计划进场日期',
  `expect_salary` varchar(64) DEFAULT NULL COMMENT '参考单价',
  `urgency` tinyint DEFAULT '1' COMMENT '紧急程度：1 普通 2 紧急 3 特急',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 待招聘 2 招聘中 3 已完成 4 已取消',
  `applicant` varchar(64) DEFAULT NULL COMMENT '提出人',
  `contact_mobile` varchar(32) DEFAULT NULL COMMENT '联系方式',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_demand_no` (`demand_no`),
  KEY `idx_status` (`status`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 用工需求单';;

-- hrm_safety_education
CREATE TABLE IF NOT EXISTS `hrm_safety_education` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id` bigint NOT NULL COMMENT '员工编号',
  `level` tinyint NOT NULL COMMENT '教育级别：1 公司级，2 项目级，3 班组级',
  `education_date` date NOT NULL COMMENT '教育日期',
  `educator_name` varchar(64) DEFAULT NULL COMMENT '教育人',
  `hours` decimal(5,1) DEFAULT NULL COMMENT '学时',
  `content` varchar(1000) DEFAULT NULL COMMENT '教育内容',
  `exam_score` decimal(5,1) DEFAULT NULL COMMENT '考核成绩',
  `qualified` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否合格',
  `file_urls` varchar(2048) DEFAULT NULL COMMENT '签字表/试卷/照片（逗号分隔地址串）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 三级安全教育台账';;

-- hrm_salary_attendance_rule
CREATE TABLE IF NOT EXISTS `hrm_salary_attendance_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) NOT NULL COMMENT '规则名称',
  `full_attendance_days` decimal(5,2) NOT NULL DEFAULT '26.00' COMMENT '满勤天数',
  `daily_wage_mode` tinyint NOT NULL DEFAULT '1' COMMENT '日薪折算方式：1 月薪÷满勤天数 2 按日薪',
  `absence_deduct_rate` decimal(6,2) NOT NULL DEFAULT '100.00' COMMENT '缺勤扣款比例（%）',
  `full_attendance_bonus_rate` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '满勤奖比例（%，按日薪）',
  `personal_leave_rate` decimal(6,2) NOT NULL DEFAULT '100.00' COMMENT '事假扣款比例（%）',
  `sick_leave_rate` decimal(6,2) NOT NULL DEFAULT '60.00' COMMENT '病假扣款比例（%）',
  `late_to_absence_times` int NOT NULL DEFAULT '0' COMMENT '迟到早退几次折算 1 天缺勤',
  `default_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认规则',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 启用 1 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_default` (`default_flag`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=6901 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HRM 考勤计薪规则';;

-- ============ 2. 缺失列 ============
ALTER TABLE `hrm_attendance_clock` ADD COLUMN `photo_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '打卡现场照片（人脸/工地照）地址';
ALTER TABLE `hrm_certificate_type` ADD COLUMN `mandatory` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必持（缺证不得上岗）';
ALTER TABLE `hrm_certificate_type` ADD COLUMN `warn_days` int NOT NULL DEFAULT '60' COMMENT '到期预警提前天数';
ALTER TABLE `hrm_certificate_type` ADD COLUMN `issuing_authority` varchar(128) DEFAULT NULL COMMENT '发证机关';
ALTER TABLE `hrm_employee` ADD COLUMN `employment_type_id` bigint DEFAULT NULL COMMENT '用工形式编号（hrm_employment_type.id）';
ALTER TABLE `hrm_employee_certificate` ADD COLUMN `review_date` date DEFAULT NULL COMMENT '复审日期';
ALTER TABLE `hrm_employee_certificate` ADD COLUMN `file_urls` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件扫描件/照片（逗号分隔地址串）';
ALTER TABLE `hrm_employee_certificate` ADD COLUMN `verify_status` tinyint NOT NULL DEFAULT '0' COMMENT '核验状态：0 未核验，1 已核验，2 核验不通过';
ALTER TABLE `hrm_employee_certificate` ADD COLUMN `verify_user_id` bigint DEFAULT NULL COMMENT '核验人（后台用户编号）';
ALTER TABLE `hrm_employee_certificate` ADD COLUMN `verify_time` datetime DEFAULT NULL COMMENT '核验时间';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `template_id` bigint DEFAULT NULL COMMENT '合同模板编号';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `source_offer_id` bigint DEFAULT NULL COMMENT '来源 Offer 编号';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `work_content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作内容（劳务内容）';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `work_location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作地点';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `work_task` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作任务（以完成任务为期限时）';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `pay_mode` tinyint DEFAULT NULL COMMENT '计薪方式：1 月薪，2 日薪，3 计件';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `pay_amount` decimal(12,2) DEFAULT NULL COMMENT '计薪金额/单价';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `pay_unit` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '计薪单位，如 元/月、元/日、元/件';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `pay_day` tinyint DEFAULT NULL COMMENT '每月发薪日';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `commercial_insurance` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商业保险说明';
ALTER TABLE `hrm_employee_contract` ADD COLUMN `attendance_mode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '考勤方式';
ALTER TABLE `hrm_recruit_post` ADD COLUMN `demand_id` bigint DEFAULT NULL COMMENT '关联用工需求单编号';

-- ============ 3. 缺失表的数据 ============
-- hrm_company_profile
INSERT INTO `hrm_company_profile` VALUES (1,'秦川化工产业园发展有限公司','91610100MA6TXGY001','陕西省西安市高新区科技路1号','张建国','2018-05-18','5000万元人民币','有限责任公司(自然人投资或控股)','防腐保温工程施工；管道设备安装；化工产品（不含危险品）销售；建筑材料销售。',126,'029-88886666','中国建设银行西安高新支行','61001234567890000000','雇主责任险 80 万元/人，团体意外险 30 万元/人',_binary '',0,'P1 自动生成','80732','2026-09-17 12:11:43','80950','2026-09-17 19:12:47',_binary '\0',2020),(2,'都是假的','23131',NULL,'也一样','2026-09-20','100万',NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',0,NULL,'80950','2026-09-20 09:38:56','80950','2026-09-20 09:39:03',_binary '',2020),(3,'都是假的','23131',NULL,'也一样',NULL,'100万',NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',0,NULL,'80950','2026-09-20 09:39:36','80950','2026-09-20 09:41:33',_binary '',2020);

-- hrm_contract_sign_record
INSERT INTO `hrm_contract_sign_record` VALUES (1,8,129,'HT-OFF-8b86fd5002434913',1,'秦川化工产业园发展有限公司','www',1,'1970-01-01 08:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-06-24%20200728.png',1,3,'2026-09-20 10:24:15','线下纸质合同待签','80732','2026-09-17 23:09:05','80950','2026-09-20 14:15:02',_binary '',2020),(2,9,130,'HT-OFF-6b2afdf4600d4e31',1,'秦川化工产业园发展有限公司','王保强',1,'2026-09-18 14:30:00','http://127.0.0.1:48080/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E4%BB%A5%E5%AE%8C%E6%88%90%E4%B8%80%E5%AE%9A%E5%B7%A5%E4%BD%9C%E4%BB%BB%E5%8A%A1%E4%B8%BA%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',1,0,NULL,'已收到纸质签署件（扫描件）','80950','2026-09-18 12:24:57','80950','2026-09-20 14:15:02',_binary '',2020),(3,12,135,'HT-OFF-971f690681e741c3',NULL,'秦川化工产业园发展有限公司','王五',2,'2026-09-20 12:41:58','http://127.0.0.1:48080/admin-api/infra/file/4/get/signed-contract.pdf',1,0,NULL,'E2E签署','80950','2026-09-20 12:41:59','80950','2026-09-20 14:15:02',_binary '',2020);

-- hrm_contract_template
INSERT INTO `hrm_contract_template` VALUES (1,'劳务合同（劳务关系）模板',1,5,'劳务',NULL,1,0,_binary '',5,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260917/labor-contract-template.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/labor-service.png','/admin-api/infra/file/4/preview/hrm/template/20260918/labor-service.pdf',NULL,NULL,NULL,NULL,NULL,'P1 劳务合同模板（v2 文案修订）','80732','2026-09-17 12:11:59','admin','2026-09-18 09:28:07',_binary '\0',2020),(2,'临时工用工协议模板（任务期限）',1,3,'临时工',1,2,0,_binary '\0',0,NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260917/labor-contract-template.docx?v=2',NULL,NULL,NULL,'${companyName},${employeeName},${jobNumber},${projectName}','按 2026 版示范文本修订第三条',80732,'2026-09-17 23:09:22',NULL,'80732','2026-09-17 23:09:22','80732','2026-09-17 23:09:22',_binary '',2020),(3,'固定期限劳动合同',1,1,'正式员工',NULL,1,0,_binary '',1,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/01_%E5%9B%BA%E5%AE%9A%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/01_%E5%9B%BA%E5%AE%9A%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/01_%E5%9B%BA%E5%AE%9A%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:33','admin','2026-09-18 09:28:07',_binary '\0',2020),(4,'无固定期限劳动合同',1,2,'正式长期',NULL,1,0,_binary '\0',2,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/02_%E6%97%A0%E5%9B%BA%E5%AE%9A%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/02_%E6%97%A0%E5%9B%BA%E5%AE%9A%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/02_%E6%97%A0%E5%9B%BA%E5%AE%9A%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:33','admin','2026-09-18 09:28:07',_binary '\0',2020),(5,'以完成一定工作任务为期限劳动合同',1,3,'临时工',NULL,1,0,_binary '\0',3,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/03_%E4%BB%A5%E5%AE%8C%E6%88%90%E4%B8%80%E5%AE%9A%E5%B7%A5%E4%BD%9C%E4%BB%BB%E5%8A%A1%E4%B8%BA%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E4%BB%A5%E5%AE%8C%E6%88%90%E4%B8%80%E5%AE%9A%E5%B7%A5%E4%BD%9C%E4%BB%BB%E5%8A%A1%E4%B8%BA%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E4%BB%A5%E5%AE%8C%E6%88%90%E4%B8%80%E5%AE%9A%E5%B7%A5%E4%BD%9C%E4%BB%BB%E5%8A%A1%E4%B8%BA%E6%9C%9F%E9%99%90%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:33','admin','2026-09-18 09:28:07',_binary '\0',2020),(6,'试用期劳动合同',1,1,'试用期',NULL,1,0,_binary '\0',4,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/04_%E8%AF%95%E7%94%A8%E6%9C%9F%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/04_%E8%AF%95%E7%94%A8%E6%9C%9F%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/04_%E8%AF%95%E7%94%A8%E6%9C%9F%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:33','admin','2026-09-18 09:28:07',_binary '\0',2020),(7,'劳务派遣劳动合同',1,7,'劳务派遣',NULL,1,0,_binary '\0',5,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/05_%E5%8A%B3%E5%8A%A1%E6%B4%BE%E9%81%A3%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/05_%E5%8A%B3%E5%8A%A1%E6%B4%BE%E9%81%A3%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/05_%E5%8A%B3%E5%8A%A1%E6%B4%BE%E9%81%A3%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:33','admin','2026-09-18 09:28:07',_binary '\0',2020),(8,'非全日制劳动合同',1,9,'兼职',NULL,1,0,_binary '\0',6,'劳动合同模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/06_%E9%9D%9E%E5%85%A8%E6%97%A5%E5%88%B6%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/06_%E9%9D%9E%E5%85%A8%E6%97%A5%E5%88%B6%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/06_%E9%9D%9E%E5%85%A8%E6%97%A5%E5%88%B6%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:33','admin','2026-09-18 09:28:07',_binary '\0',2020),(9,'个人基本信息档案',2,NULL,NULL,NULL,1,0,_binary '\0',1,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/01_%E4%B8%AA%E4%BA%BA%E5%9F%BA%E6%9C%AC%E4%BF%A1%E6%81%AF%E6%A1%A3%E6%A1%88.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/01_%E4%B8%AA%E4%BA%BA%E5%9F%BA%E6%9C%AC%E4%BF%A1%E6%81%AF%E6%A1%A3%E6%A1%88.png','/admin-api/infra/file/4/preview/hrm/template/20260918/01_%E4%B8%AA%E4%BA%BA%E5%9F%BA%E6%9C%AC%E4%BF%A1%E6%81%AF%E6%A1%A3%E6%A1%88.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(10,'员工人事档案',2,NULL,NULL,NULL,1,0,_binary '\0',2,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/02_%E5%91%98%E5%B7%A5%E4%BA%BA%E4%BA%8B%E6%A1%A3%E6%A1%88.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/02_%E5%91%98%E5%B7%A5%E4%BA%BA%E4%BA%8B%E6%A1%A3%E6%A1%88.png','/admin-api/infra/file/4/preview/hrm/template/20260918/02_%E5%91%98%E5%B7%A5%E4%BA%BA%E4%BA%8B%E6%A1%A3%E6%A1%88.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(11,'个人健康与体检档案',2,NULL,NULL,NULL,1,0,_binary '\0',3,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/03_%E4%B8%AA%E4%BA%BA%E5%81%A5%E5%BA%B7%E4%B8%8E%E4%BD%93%E6%A3%80%E6%A1%A3%E6%A1%88.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E4%B8%AA%E4%BA%BA%E5%81%A5%E5%BA%B7%E4%B8%8E%E4%BD%93%E6%A3%80%E6%A1%A3%E6%A1%88.png','/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E4%B8%AA%E4%BA%BA%E5%81%A5%E5%BA%B7%E4%B8%8E%E4%BD%93%E6%A3%80%E6%A1%A3%E6%A1%88.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(12,'电子档案索引（目录）',2,NULL,NULL,NULL,1,0,_binary '\0',4,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/04_%E7%94%B5%E5%AD%90%E6%A1%A3%E6%A1%88%E7%B4%A2%E5%BC%95%EF%BC%88%E7%9B%AE%E5%BD%95%EF%BC%89.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/04_%E7%94%B5%E5%AD%90%E6%A1%A3%E6%A1%88%E7%B4%A2%E5%BC%95%EF%BC%88%E7%9B%AE%E5%BD%95%EF%BC%89.png','/admin-api/infra/file/4/preview/hrm/template/20260918/04_%E7%94%B5%E5%AD%90%E6%A1%A3%E6%A1%88%E7%B4%A2%E5%BC%95%EF%BC%88%E7%9B%AE%E5%BD%95%EF%BC%89.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(13,'员工入职档案登记表',2,NULL,NULL,NULL,1,0,_binary '\0',5,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/05_%E5%91%98%E5%B7%A5%E5%85%A5%E8%81%8C%E6%A1%A3%E6%A1%88%E7%99%BB%E8%AE%B0%E8%A1%A8.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/05_%E5%91%98%E5%B7%A5%E5%85%A5%E8%81%8C%E6%A1%A3%E6%A1%88%E7%99%BB%E8%AE%B0%E8%A1%A8.png','/admin-api/infra/file/4/preview/hrm/template/20260918/05_%E5%91%98%E5%B7%A5%E5%85%A5%E8%81%8C%E6%A1%A3%E6%A1%88%E7%99%BB%E8%AE%B0%E8%A1%A8.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(14,'人事档案目录清单',2,NULL,NULL,NULL,1,0,_binary '\0',6,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/06_%E4%BA%BA%E4%BA%8B%E6%A1%A3%E6%A1%88%E7%9B%AE%E5%BD%95%E6%B8%85%E5%8D%95.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/06_%E4%BA%BA%E4%BA%8B%E6%A1%A3%E6%A1%88%E7%9B%AE%E5%BD%95%E6%B8%85%E5%8D%95.png','/admin-api/infra/file/4/preview/hrm/template/20260918/06_%E4%BA%BA%E4%BA%8B%E6%A1%A3%E6%A1%88%E7%9B%AE%E5%BD%95%E6%B8%85%E5%8D%95.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(15,'员工花名册',2,NULL,NULL,NULL,1,0,_binary '\0',7,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/07_%E5%91%98%E5%B7%A5%E8%8A%B1%E5%90%8D%E5%86%8C.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/07_%E5%91%98%E5%B7%A5%E8%8A%B1%E5%90%8D%E5%86%8C.png','/admin-api/infra/file/4/preview/hrm/template/20260918/07_%E5%91%98%E5%B7%A5%E8%8A%B1%E5%90%8D%E5%86%8C.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(16,'查阅借阅转递销毁登记',2,NULL,NULL,NULL,1,0,_binary '\0',8,'个人电子档案模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/08_%E6%9F%A5%E9%98%85%E5%80%9F%E9%98%85%E8%BD%AC%E9%80%92%E9%94%80%E6%AF%81%E7%99%BB%E8%AE%B0.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/08_%E6%9F%A5%E9%98%85%E5%80%9F%E9%98%85%E8%BD%AC%E9%80%92%E9%94%80%E6%AF%81%E7%99%BB%E8%AE%B0.png','/admin-api/infra/file/4/preview/hrm/template/20260918/08_%E6%9F%A5%E9%98%85%E5%80%9F%E9%98%85%E8%BD%AC%E9%80%92%E9%94%80%E6%AF%81%E7%99%BB%E8%AE%B0.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(17,'专项培训服务期协议',3,NULL,NULL,NULL,1,0,_binary '\0',1,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/01_%E4%B8%93%E9%A1%B9%E5%9F%B9%E8%AE%AD%E6%9C%8D%E5%8A%A1%E6%9C%9F%E5%8D%8F%E8%AE%AE.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/01_%E4%B8%93%E9%A1%B9%E5%9F%B9%E8%AE%AD%E6%9C%8D%E5%8A%A1%E6%9C%9F%E5%8D%8F%E8%AE%AE.png','/admin-api/infra/file/4/preview/hrm/template/20260918/01_%E4%B8%93%E9%A1%B9%E5%9F%B9%E8%AE%AD%E6%9C%8D%E5%8A%A1%E6%9C%9F%E5%8D%8F%E8%AE%AE.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(18,'员工培训与职业发展协议',3,NULL,NULL,NULL,1,0,_binary '\0',2,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/02_%E5%91%98%E5%B7%A5%E5%9F%B9%E8%AE%AD%E4%B8%8E%E8%81%8C%E4%B8%9A%E5%8F%91%E5%B1%95%E5%8D%8F%E8%AE%AE.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/02_%E5%91%98%E5%B7%A5%E5%9F%B9%E8%AE%AD%E4%B8%8E%E8%81%8C%E4%B8%9A%E5%8F%91%E5%B1%95%E5%8D%8F%E8%AE%AE.png','/admin-api/infra/file/4/preview/hrm/template/20260918/02_%E5%91%98%E5%B7%A5%E5%9F%B9%E8%AE%AD%E4%B8%8E%E8%81%8C%E4%B8%9A%E5%8F%91%E5%B1%95%E5%8D%8F%E8%AE%AE.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(19,'培训费用承担+保密+竞业限制协议',3,NULL,NULL,NULL,1,0,_binary '\0',3,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/03_%E5%9F%B9%E8%AE%AD%E8%B4%B9%E7%94%A8%E6%89%BF%E6%8B%85+%E4%BF%9D%E5%AF%86+%E7%AB%9E%E4%B8%9A%E9%99%90%E5%88%B6%E5%8D%8F%E8%AE%AE.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E5%9F%B9%E8%AE%AD%E8%B4%B9%E7%94%A8%E6%89%BF%E6%8B%85+%E4%BF%9D%E5%AF%86+%E7%AB%9E%E4%B8%9A%E9%99%90%E5%88%B6%E5%8D%8F%E8%AE%AE.png','/admin-api/infra/file/4/preview/hrm/template/20260918/03_%E5%9F%B9%E8%AE%AD%E8%B4%B9%E7%94%A8%E6%89%BF%E6%8B%85+%E4%BF%9D%E5%AF%86+%E7%AB%9E%E4%B8%9A%E9%99%90%E5%88%B6%E5%8D%8F%E8%AE%AE.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(20,'员工外派进修培训',3,NULL,NULL,NULL,1,0,_binary '\0',4,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/04_%E5%91%98%E5%B7%A5%E5%A4%96%E6%B4%BE%E8%BF%9B%E4%BF%AE%E5%9F%B9%E8%AE%AD.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/04_%E5%91%98%E5%B7%A5%E5%A4%96%E6%B4%BE%E8%BF%9B%E4%BF%AE%E5%9F%B9%E8%AE%AD.png','/admin-api/infra/file/4/preview/hrm/template/20260918/04_%E5%91%98%E5%B7%A5%E5%A4%96%E6%B4%BE%E8%BF%9B%E4%BF%AE%E5%9F%B9%E8%AE%AD.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(21,'师带徒（内部带教）',3,NULL,NULL,NULL,1,0,_binary '\0',5,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/05_%E5%B8%88%E5%B8%A6%E5%BE%92%EF%BC%88%E5%86%85%E9%83%A8%E5%B8%A6%E6%95%99%EF%BC%89.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/05_%E5%B8%88%E5%B8%A6%E5%BE%92%EF%BC%88%E5%86%85%E9%83%A8%E5%B8%A6%E6%95%99%EF%BC%89.png','/admin-api/infra/file/4/preview/hrm/template/20260918/05_%E5%B8%88%E5%B8%A6%E5%BE%92%EF%BC%88%E5%86%85%E9%83%A8%E5%B8%A6%E6%95%99%EF%BC%89.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(22,'新员工入职（岗前）培训',3,NULL,NULL,NULL,1,0,_binary '\0',6,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/06_%E6%96%B0%E5%91%98%E5%B7%A5%E5%85%A5%E8%81%8C%EF%BC%88%E5%B2%97%E5%89%8D%EF%BC%89%E5%9F%B9%E8%AE%AD.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/06_%E6%96%B0%E5%91%98%E5%B7%A5%E5%85%A5%E8%81%8C%EF%BC%88%E5%B2%97%E5%89%8D%EF%BC%89%E5%9F%B9%E8%AE%AD.png','/admin-api/infra/file/4/preview/hrm/template/20260918/06_%E6%96%B0%E5%91%98%E5%B7%A5%E5%85%A5%E8%81%8C%EF%BC%88%E5%B2%97%E5%89%8D%EF%BC%89%E5%9F%B9%E8%AE%AD.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(23,'培训费用报销+服务期',3,NULL,NULL,NULL,1,0,_binary '\0',7,'培训协议模板','http://127.0.0.1:48080/admin-api/infra/file/4/get/hrm/template/20260918/07_%E5%9F%B9%E8%AE%AD%E8%B4%B9%E7%94%A8%E6%8A%A5%E9%94%80+%E6%9C%8D%E5%8A%A1%E6%9C%9F.docx','/admin-api/infra/file/4/preview/hrm/template/20260918/07_%E5%9F%B9%E8%AE%AD%E8%B4%B9%E7%94%A8%E6%8A%A5%E9%94%80+%E6%9C%8D%E5%8A%A1%E6%9C%9F.png','/admin-api/infra/file/4/preview/hrm/template/20260918/07_%E5%9F%B9%E8%AE%AD%E8%B4%B9%E7%94%A8%E6%8A%A5%E9%94%80+%E6%9C%8D%E5%8A%A1%E6%9C%9F.pdf',NULL,NULL,NULL,NULL,NULL,'批量导入：劳动合同/档案/培训模板','admin','2026-09-18 09:16:46','admin','2026-09-18 09:28:07',_binary '\0',2020),(24,'测试',1,1,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 09:52:25',NULL,'80950','2026-09-20 09:52:25','80950','2026-09-20 09:52:37',_binary '',2020),(25,'测试',2,NULL,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h',NULL,NULL,NULL,NULL,80950,'2026-09-20 09:53:51',NULL,'80950','2026-09-20 09:53:51','80950','2026-09-20 09:53:58',_binary '',2020),(26,'测试',3,NULL,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 09:54:19',NULL,'80950','2026-09-20 09:54:19','80950','2026-09-20 09:56:02',_binary '',2020),(27,'测试',3,NULL,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 09:54:53',NULL,'80950','2026-09-20 09:54:53','80950','2026-09-20 09:56:03',_binary '',2020),(28,'培训协议',3,NULL,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 09:57:31',NULL,'80950','2026-09-20 09:57:31','80950','2026-09-20 09:57:40',_binary '',2020),(29,'培训协议',3,NULL,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 10:02:09',NULL,'80950','2026-09-20 10:02:09','80950','2026-09-20 10:02:20',_binary '',2020),(30,'劳动合同',1,1,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 10:03:26',NULL,'80950','2026-09-20 10:03:26','80950','2026-09-20 10:03:58',_binary '',2020),(31,'1',1,NULL,NULL,NULL,1,0,_binary '\0',0,NULL,'h','h','h',NULL,NULL,NULL,80950,'2026-09-20 10:05:45',NULL,'80950','2026-09-20 10:05:45','80950','2026-09-20 10:06:21',_binary '',2020);

-- hrm_dept_headcount
INSERT INTO `hrm_dept_headcount` VALUES (1,50138,6,'含劳务分包班组 2 人','80950','2026-09-18 19:36:14','80950','2026-09-18 19:36:14',_binary '\0',2020),(2,50133,3,NULL,'80950','2026-09-20 11:14:15','80950','2026-09-20 11:14:15',_binary '\0',2020);

-- hrm_employee_approval
INSERT INTO `hrm_employee_approval` VALUES (1,119,'赵敏','QC000001',1,'试用期考核合格，申请转正','1970-01-01 08:00:00','直属上级已同意',3,_binary '\0','HR1','2026-09-19 16:05:44','HR1','2026-09-19 16:05:45','考核材料不全，请补齐后再申请','80950','2026-09-19 16:05:44','80950','2026-09-20 14:15:02',_binary '',2020),(2,130,'王保强','QC000010',3,'合同到期不再续签，申请离职','1970-01-01 08:00:00',NULL,2,_binary '\0','HR1','2026-09-19 16:16:11','HR1','2026-09-20 10:56:31','','80950','2026-09-19 16:16:11','80950','2026-09-20 14:15:02',_binary '',2020),(3,134,'柳大壮','QC000014',1,'','2026-09-20 11:20:19','',2,_binary '\0','HR1','2026-09-20 11:20:27','HR1','2026-09-20 11:20:41','','80950','2026-09-20 11:20:27','80950','2026-09-20 14:15:02',_binary '',2020);

-- hrm_employee_blacklist
INSERT INTO `hrm_employee_blacklist` VALUES (1,131,'李保温','13900007799',NULL,'高空作业未系安全带，严重安全违规',2,'2026-09-18 18:37:47',NULL,_binary '\0',1,NULL,'80950','2026-09-18 18:37:47','80950','2026-09-20 14:15:02',_binary '',2020),(3,NULL,'张三','14725836995',NULL,'111',1,'2026-09-20 10:55:13',NULL,_binary '\0',1,NULL,'80950','2026-09-20 10:55:13','80950','2026-09-20 14:15:02',_binary '',2020),(4,NULL,'测试','15996000287',NULL,'上班玩手机',1,'2026-09-20 11:07:28','2026-09-20 11:07:41',_binary '\0',2,NULL,'80950','2026-09-20 11:07:28','80950','2026-09-20 11:07:45',_binary '',2020),(5,NULL,'刘海涛','18991020836','610100199001019999','E2E测试：结算纠纷未处理',1,'2026-09-20 00:00:00',NULL,_binary '\0',1,NULL,'80950','2026-09-20 12:41:59','80950','2026-09-20 14:15:02',_binary '',2020),(6,137,'E2E黑名单候选人','13700000120',NULL,'防腐保温测试：违反安全条例被拉黑',1,'2026-09-20 12:53:23',NULL,_binary '\0',1,NULL,'80950','2026-09-20 12:53:23','80950','2026-09-20 14:15:02',_binary '',2020);

-- hrm_employee_no_rule
INSERT INTO `hrm_employee_no_rule` VALUES (1,'默认工号规则（XAOP）','XAOP',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',1),(2,'默认工号规则（XZH）','XZH',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',121),(3,'默认工号规则（CSZH）','CSZH',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',122),(4,'默认工号规则（YADSJ）','YADSJ',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',162),(5,'默认工号规则（YADX）','YADX',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',163),(6,'默认工号规则（CRM3）','CRM3',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',164),(7,'默认工号规则（CRMZH）','CRMZH',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',165),(8,'默认工号规则（CRM）','CRM',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',166),(9,'默认工号规则（XBHTYL）','XBHTYL',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',167),(10,'默认工号规则（XBHT）','XBHT',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2001),(11,'默认工号规则（JC）','JC',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2002),(12,'默认工号规则（LD）','LD',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2003),(13,'默认工号规则（LDXS）','LDXS',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2004),(14,'默认工号规则（JY）','JY',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2005),(15,'默认工号规则（HT）','HT',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2006),(16,'默认工号规则（YA）','YA',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2007),(17,'默认工号规则（SAFF）','SAFF',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2008),(18,'默认工号规则（KD）','KD',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2009),(19,'默认工号规则（HH）','HH',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2010),(20,'默认工号规则（RJ）','RJ',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2011),(21,'默认工号规则（GB）','GB',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2012),(22,'默认工号规则（ZY）','ZY',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2013),(23,'默认工号规则（SAZA）','SAZA',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2014),(24,'默认工号规则（XY）','XY',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2015),(25,'默认工号规则（HX）','HX',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2016),(26,'默认工号规则（TY）','TY',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2017),(27,'默认工号规则（AK）','AK',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2018),(28,'默认工号规则（YHRL）','YHRL',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2019),(29,'默认工号规则（QC）','QC',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2020),(30,'默认工号规则（LY）','LY',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2021),(31,'默认工号规则（XBCT）','XBCT',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2022),(32,'默认工号规则（HHSH）','HHSH',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2023),(33,'默认工号规则（SB）','SB',6,'',0,_binary '\0',0,'格式：租户缩写 + 6 位顺序号；租户内独立计数、永不复用','admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2024);

-- hrm_employee_no_sequence
INSERT INTO `hrm_employee_no_sequence` VALUES (1,1,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',1),(2,2,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',121),(3,3,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',122),(4,4,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',162),(5,5,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',163),(6,6,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',164),(7,7,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',165),(8,8,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',166),(9,9,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',167),(10,10,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2001),(11,11,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2002),(12,12,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2003),(13,13,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2004),(14,14,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2005),(15,15,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2006),(16,16,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2007),(17,17,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2008),(18,18,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2009),(19,19,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2010),(20,20,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2011),(21,21,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2012),(22,22,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2013),(23,23,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2014),(24,24,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2015),(25,25,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2016),(26,26,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2017),(27,27,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2018),(28,28,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2019),(29,29,'ALL',8,'admin','2026-09-17 11:48:58','cleanup-20260920','2026-09-20 14:22:37',_binary '\0',2020),(30,30,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2021),(31,31,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2022),(32,32,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2023),(33,33,'ALL',0,'admin','2026-09-17 11:48:58','admin','2026-09-17 11:48:58',_binary '\0',2024);

-- hrm_employment_type
INSERT INTO `hrm_employment_type` VALUES (1,'正式员工','FORMAL',3,1,_binary '\0',_binary '\0',_binary '',1,0,'长期固定用工，签订固定期限劳动合同','admin','2026-09-18 11:53:13','80950','2026-09-20 11:06:22',_binary '\0',2020),(2,'正式长期','FORMAL_LONG',4,1,_binary '\0',_binary '\0',_binary '',2,0,'签订无固定期限劳动合同','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(3,'临时工','TEMPORARY',5,2,_binary '',_binary '',_binary '\0',3,0,'项目制短工，以完成一定工作任务为期限','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(4,'劳务用工','LABOR',1,1,_binary '\0',_binary '\0',_binary '\0',4,0,'签订劳务合同，按劳务关系管理','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(5,'劳务派遣','DISPATCH',7,1,_binary '\0',_binary '\0',_binary '\0',5,0,'派遣单位派入，签订劳务派遣劳动合同','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(6,'兼职','PART_TIME',8,2,_binary '',_binary '\0',_binary '\0',6,0,'非全日制用工，按日计酬','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(7,'实习','INTERN',NULL,1,_binary '',_binary '\0',_binary '\0',7,0,'在校生实习，签实习/三方协议','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(8,'返聘','REHIRE',NULL,1,_binary '\0',_binary '\0',_binary '\0',8,0,'退休返聘人员','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:24',_binary '\0',2020),(9,'日结工','DAILY',NULL,2,_binary '',_binary '',_binary '\0',9,0,'当日结算，按工日计酬，需人脸打卡','admin','2026-09-18 11:53:13','80950','2026-09-20 09:09:04',_binary '\0',2020),(10,'计件工','PIECE',NULL,3,_binary '',_binary '',_binary '\0',10,0,'按工程量计件计酬','admin','2026-09-18 11:53:13','admin','2026-09-18 11:53:13',_binary '\0',2020),(16,'测试','1我',3,1,_binary '\0',_binary '\0',_binary '',0,0,NULL,'80950','2026-09-20 11:05:51','80950','2026-09-20 11:05:56',_binary '',2020);

-- hrm_policy
INSERT INTO `hrm_policy` VALUES (1,'2026年西安市社保缴费基数通知','social_insurance','市人社发〔2026〕12号','西安市人力资源和社会保障局','2026-01-05','2026-01-01','2026-12-31','region','西安市','企业职工基本养老保险缴费基数下限 4200 元，上限 21000 元。',NULL,'[\"社保\", \"基数\"]',0,NULL,'80950','2026-09-14 19:26:38','80950','2026-09-14 19:26:38',_binary '\0',2020);

-- hrm_recruit_demand
INSERT INTO `hrm_recruit_demand` VALUES (1,'REQ-20260918-001','秦川化工园区二期 保温工用人需求',NULL,NULL,'保温工','高处作业证',20,0,'2026-10-08','320 元/天',2,1,'王工','13800000001',NULL,'80950','2026-09-18 18:37:39','80950','2026-09-20 14:15:02',_binary '',2020),(2,'REQ-20260920-001','秦川化工保温工用人需求',50134,1,'保温工','高处作业证',20,0,'2026-09-23',NULL,2,4,NULL,NULL,NULL,'80950','2026-09-20 09:39:47','80950','2026-09-20 14:15:02',_binary '',2020),(3,'REQ-20260920-002','秦川化工工业园 三期保温工用人需求',NULL,1,'保温工','高处作业证',20,1,NULL,NULL,1,2,NULL,NULL,NULL,'80950','2026-09-20 09:58:23','80950','2026-09-20 14:15:02',_binary '',2020),(4,'REQ-20260920-003','E2E测试-泾渭工业园保温工需求-09201235',NULL,NULL,'保温工','高处作业操作证',3,0,'2026-10-05','计件 45元/㎡',2,1,'李工','13900001111','端到端测试','80950','2026-09-20 12:35:33','80950','2026-09-20 14:15:02',_binary '',2020),(5,'REQ-20260920-004','E2E测试-泾渭工业园保温工需求-09201236',NULL,NULL,'保温工','高处作业操作证',3,0,'2026-10-05','计件 45元/㎡',2,1,'李工','13900001111','端到端测试','80950','2026-09-20 12:36:51','80950','2026-09-20 14:15:02',_binary '',2020),(6,'REQ-20260920-005','E2E测试-泾渭工业园保温工需求-09201237',NULL,NULL,'保温工','高处作业操作证',3,2,'2026-10-05','计件 45元/㎡',2,2,'李工','13900001111','端到端测试','80950','2026-09-20 12:37:15','80950','2026-09-20 14:15:02',_binary '',2020);

-- hrm_salary_attendance_rule
INSERT INTO `hrm_salary_attendance_rule` VALUES (6900,'防腐保温工程队标准计薪规则',26.00,1,100.00,10.00,100.00,60.00,3,_binary '',0,'满勤 26 天；月薪÷满勤得日薪；缺勤全额扣；满勤奖按日薪 10%；事假全额扣；病假扣 60%；迟到早退 3 次折算 1 天','salary-dev','2026-09-18 20:55:52','salary-dev','2026-09-18 20:55:52',_binary '\0',2020);
