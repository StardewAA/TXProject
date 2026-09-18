-- 云培训学院：晋级门槛及子模块指标关联（来源：云培训学院-晋级门槛表.xlsx）
-- 可重复执行；规则 tenant_id=0 表示平台默认模板，企业可按相同 rule_key 覆盖。

CREATE TABLE IF NOT EXISTS `training_promotion_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '系统用户ID',
  `current_level` varchar(32) NOT NULL DEFAULT 'APPRENTICE',
  `level_since` date NOT NULL COMMENT '进入当前等级日期',
  `job_type` varchar(64) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `updater` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_promotion_profile` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员晋级档案';

CREATE TABLE IF NOT EXISTS `training_promotion_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_level` varchar(32) NOT NULL, `to_level` varchar(32) NOT NULL,
  `rule_key` varchar(64) NOT NULL, `rule_name` varchar(128) NOT NULL,
  `operator` varchar(4) NOT NULL, `rule_value` decimal(14,2) NOT NULL,
  `unit` varchar(20) DEFAULT NULL, `is_hard` bit(1) NOT NULL DEFAULT b'1',
  `source_module` varchar(32) NOT NULL, `description` varchar(255) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0, `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `updater` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_promotion_rule` (`tenant_id`,`from_level`,`to_level`,`rule_key`),
  KEY `idx_promotion_path` (`tenant_id`,`from_level`,`to_level`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='晋级门槛规则';

CREATE TABLE IF NOT EXISTS `training_promotion_metric` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL,
  `rule_key` varchar(64) NOT NULL, `metric_value` decimal(14,2) NOT NULL,
  `source_module` varchar(32) NOT NULL, `evidence` varchar(500) DEFAULT NULL,
  `metric_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `creator` varchar(64) DEFAULT '', `updater` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_promotion_metric` (`tenant_id`,`user_id`,`rule_key`),
  KEY `idx_promotion_metric_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='各子模块回传的晋级指标';

CREATE TABLE IF NOT EXISTS `training_promotion_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL,
  `from_level` varchar(32) NOT NULL, `to_level` varchar(32) NOT NULL,
  `qualified` bit(1) NOT NULL, `progress` decimal(5,2) NOT NULL,
  `detail_json` longtext NOT NULL, `evaluated_by` bigint DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `updater` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_promotion_evaluation` (`tenant_id`,`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='晋级评估快照';

INSERT INTO `training_promotion_rule`
(`from_level`,`to_level`,`rule_key`,`rule_name`,`operator`,`rule_value`,`unit`,`is_hard`,`source_module`,`description`,`sort`,`status`,`tenant_id`)
VALUES
('APPRENTICE','JUNIOR','required_course_rate','必修课程完成率','>=',100,'%',b'1','COURSE','完成全部必修课程',10,0,0),
('APPRENTICE','JUNIOR','map_entry_rate','入门成长地图完成率','>=',80,'%',b'1','COURSE','完成入门阶段成长地图',20,0,0),
('APPRENTICE','JUNIOR','exam_score_entry','入门考试成绩','>=',70,'分',b'1','EXAM','入门阶段考试成绩',30,0,0),
('APPRENTICE','JUNIOR','practical_pass_count','实操考核通过次数','>=',1,'次',b'1','VR','至少通过一次实操考核',40,0,0),
('APPRENTICE','JUNIOR','work_hours','累计工作小时','>=',320,'小时',b'1','HRM','由工时系统同步',50,0,0),
('APPRENTICE','JUNIOR','safety_violation','安全违规次数','=',0,'次',b'1','SAFETY','晋级周期内无安全违规',60,0,0),
('APPRENTICE','JUNIOR','months_in_level','本级任职月数','>=',1,'月',b'1','HRM','进入当前等级后的完整月数',70,0,0),
('JUNIOR','INTERMEDIATE','map_core_rate','核心成长地图完成率','>=',85,'%',b'1','COURSE','核心阶段成长地图完成率',10,0,0),
('JUNIOR','INTERMEDIATE','exam_score_mid','中级考试成绩','>=',75,'分',b'1','EXAM','中级考试成绩',20,0,0),
('JUNIOR','INTERMEDIATE','practical_pass_count','实操考核通过次数','>=',3,'次',b'1','VR','累计实操考核通过次数',30,0,0),
('JUNIOR','INTERMEDIATE','work_hours','累计工作小时','>=',1200,'小时',b'1','HRM','由工时系统同步',40,0,0),
('JUNIOR','INTERMEDIATE','rework_6m','近6个月返工次数','<=',1,'次',b'1','QUALITY','质量模块同步',50,0,0),
('JUNIOR','INTERMEDIATE','safety_violation','安全违规次数','=',0,'次',b'1','SAFETY','晋级周期内无安全违规',60,0,0),
('JUNIOR','INTERMEDIATE','months_in_level','本级任职月数','>=',6,'月',b'1','HRM','进入当前等级后的完整月数',70,0,0),
('INTERMEDIATE','SENIOR','map_advanced_rate','高级成长地图完成率','>=',90,'%',b'1','COURSE','高级阶段成长地图完成率',10,0,0),
('INTERMEDIATE','SENIOR','cross_domain_course','跨领域课程数','>=',2,'门',b'1','COURSE','完成跨领域课程',20,0,0),
('INTERMEDIATE','SENIOR','exam_score_senior','高级考试成绩','>=',80,'分',b'1','EXAM','高级考试成绩',30,0,0),
('INTERMEDIATE','SENIOR','practical_complex_pass','复杂实操通过次数','>=',1,'次',b'1','VR','通过复杂场景实操',40,0,0),
('INTERMEDIATE','SENIOR','work_hours','累计工作小时','>=',3000,'小时',b'1','HRM','由工时系统同步',50,0,0),
('INTERMEDIATE','SENIOR','rework_12m','近12个月返工次数','=',0,'次',b'1','QUALITY','近12个月无返工',60,0,0),
('INTERMEDIATE','SENIOR','project_count','参与项目数','>=',5,'个',b'1','PROJECT','项目模块同步',70,0,0),
('INTERMEDIATE','SENIOR','project_main_role','项目主要角色次数','>=',2,'次',b'1','PROJECT','担任主要角色',80,0,0),
('INTERMEDIATE','SENIOR','months_in_level','本级任职月数','>=',12,'月',b'1','HRM','进入当前等级后的完整月数',90,0,0),
('SENIOR','TECHNICIAN','map_all_rate','全成长地图完成率','=',100,'%',b'1','COURSE','完成全部成长地图',10,0,0),
('SENIOR','TECHNICIAN','mgmt_course_count','管理类课程数','>=',4,'门',b'1','COURSE','完成管理类课程',20,0,0),
('SENIOR','TECHNICIAN','digital_course_count','数字化课程数','>=',3,'门',b'1','COURSE','完成数字化课程',30,0,0),
('SENIOR','TECHNICIAN','exam_score_tech','技师考试成绩','>=',85,'分',b'1','EXAM','技师综合考试成绩',40,0,0),
('SENIOR','TECHNICIAN','defense_pass','答辩通过','=',1,'项',b'1','EXAM','技师答辩通过',50,0,0),
('SENIOR','TECHNICIAN','work_hours','累计工作小时','>=',6000,'小时',b'1','HRM','由工时系统同步',60,0,0),
('SENIOR','TECHNICIAN','apprentice_count','带教学徒数','>=',2,'人',b'1','LECTURER','讲师/师带徒模块同步',70,0,0),
('SENIOR','TECHNICIAN','apprentice_promoted','所带学徒晋级人数','>=',1,'人',b'1','LECTURER','师带徒成果',80,0,0),
('SENIOR','TECHNICIAN','improvement_adopted','改善提案采纳数','>=',1,'项',b'1','QUALITY','质量改善模块同步',90,0,0),
('SENIOR','TECHNICIAN','months_in_level','本级任职月数','>=',18,'月',b'1','HRM','进入当前等级后的完整月数',100,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','work_hours','累计工作小时','>=',12000,'小时',b'1','HRM','由工时系统同步',10,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','multi_job_l4','四级多工种能力数','>=',2,'项',b'1','SKILL','技能矩阵模块同步',20,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','apprentice_count','带教学徒数','>=',5,'人',b'1','LECTURER','师带徒累计人数',30,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','apprentice_promoted','所带学徒晋级人数','>=',3,'人',b'1','LECTURER','师带徒晋级成果',40,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','knowledge_output','知识成果输出数','>=',2,'项',b'1','KNOWLEDGE','课件/标准/专利等成果',50,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','tech_project_lead','牵头技术项目数','>=',2,'个',b'1','PROJECT','牵头技术项目',60,0,0),
('TECHNICIAN','SENIOR_TECHNICIAN','expert_review_pass','专家评审通过','=',1,'项',b'1','REVIEW','专家评审结论',70,0,0)
ON DUPLICATE KEY UPDATE `rule_name`=VALUES(`rule_name`),`operator`=VALUES(`operator`),
`rule_value`=VALUES(`rule_value`),`unit`=VALUES(`unit`),`is_hard`=VALUES(`is_hard`),
`source_module`=VALUES(`source_module`),`description`=VALUES(`description`),`sort`=VALUES(`sort`),`deleted`=b'0';
