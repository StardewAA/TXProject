-- =====================================================================
-- 全局规则配置中心（bidding_rule_config / history / audit）—— 电子资质七大模块前置依赖（方案 §8.1）
-- 依据：
--   1) 用户硬约束："所有数值参数必须支持动态配置，严禁硬编码"；
--   2) 《电子资质七大模块技术方案.md》§8.1（表结构/解析优先级/版本与灰度/VO 口径）；
--   3) 《招投标模块开发规范》§4.5（乐观锁）、§5 六-1（UK 含 tenant_id 与 deleted）、§4.8（审计要求）；
--   4) 《电子证件全流程管理_数据库设计.md》v3.3 全局约定（审计列由 BaseDO 提供）、§13（哈希链防篡改）；
--   5) 既有先例：cert_alert_config（租户级 + 平台默认行 + 多档阈值的三级回落范式）。
-- 设计说明：
--   * tenant_id = 0 表示"平台默认行"（与 cert_alert_config 同口径）；租户级行覆盖平台行；
--   * version_no 每次发布自增，历史版本保留（不物理删除），业务表记 rule_version 以便复算；
--   * status：DRAFT 草稿 / PUBLISHED 已发布 / GRAY 灰度 / ROLLED_BACK 已回滚；
--   * legal_floor 存放该键的允许边界（如天数 [0,365]），发布时校验，防误配把业务配死；
--   * history/audit 为只追加表 + 哈希链（record_hash/prev_hash），DB 账号对日志表仅授 INSERT。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE（本文件可重复执行，不覆盖运维已调整的值）。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 规则配置主表
-- 业务目标：所有风险阈值/宽限天数/法定下限/相似度阈值等数值集中管理，改配置即改行为，无需发版。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_rule_config` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（0=平台默认行；租户级行覆盖平台行）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（@TableLogic）',
    `rule_key` varchar(96) NOT NULL COMMENT '规则键（如 safety.abc.required；点分命名，见方案 §1.2~§7.2 各表）',
    `rule_group` varchar(32) NOT NULL COMMENT '规则分组 code：SAFETY/INSPECTION/ALERT/EQUIPMENT/ANNOTATION/BIDDOC/CRYPTO',
    `value_type` varchar(16) NOT NULL COMMENT '值类型 code：INT/DECIMAL/BOOL/STRING/JSON（发布时按此校验）',
    `config_value` json NOT NULL COMMENT '配置值（一切数值在此，代码禁止硬编码）',
    `legal_floor` json DEFAULT NULL COMMENT '该键允许边界（如 {"min":0,"max":365}；发布时校验，防误配）',
    `description` varchar(256) DEFAULT NULL COMMENT '配置说明（前端展示，业务人员可读）',
    `regulation_ref` varchar(256) DEFAULT NULL COMMENT '法规/文档出处（争议时可追溯）',
    `version_no` int NOT NULL DEFAULT 1 COMMENT '版本号（每次发布 +1；历史版本保留）',
    `status` varchar(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态 code：DRAFT/PUBLISHED/GRAY/ROLLED_BACK',
    `gray_scope` json DEFAULT NULL COMMENT '灰度范围：{"tenantIds":[...]} 或 {"percent":10}',
    `effective_from` datetime DEFAULT NULL COMMENT '生效开始（NULL=立即生效）',
    `effective_to` datetime DEFAULT NULL COMMENT '生效结束（NULL=长期有效）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_key` (`tenant_id`, `rule_key`, `version_no`, `deleted`),
    KEY `idx_rule_group` (`rule_group`, `status`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '全局规则配置（租户级可覆盖平台默认）';

-- ---------------------------------------------------------------------
-- 2) 规则变更历史（只追加 + 哈希链）
-- 业务目标：规则被谁在何时因为什么改动、从什么值改成什么值，全程不可篡改，满足等保三级追溯要求。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_rule_config_history` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（0=平台默认行）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（=变更人）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（@TableLogic；本表只追加，恒为 0）',
    `rule_key` varchar(96) NOT NULL COMMENT '规则键',
    `from_version` int DEFAULT NULL COMMENT '变更前版本号（新增时 NULL）',
    `to_version` int NOT NULL COMMENT '变更后版本号',
    `from_value` json DEFAULT NULL COMMENT '变更前值（新增时 NULL）',
    `to_value` json NOT NULL COMMENT '变更后值',
    `change_action` varchar(16) NOT NULL COMMENT '动作 code：CREATE/PUBLISH/GRAY/ROLLBACK',
    `changed_by` varchar(64) NOT NULL COMMENT '变更人（用户 ID 字符串；SYSTEM 表示自动）',
    `changed_at` datetime NOT NULL COMMENT '变更时刻',
    `change_reason` varchar(256) DEFAULT NULL COMMENT '变更原因（发布时必填，用于追责与回溯）',
    `record_hash` varchar(64) DEFAULT NULL COMMENT '本记录哈希（SHA-256，防篡改）',
    `prev_hash` varchar(64) DEFAULT NULL COMMENT '上一条记录哈希（哈希链）',
    PRIMARY KEY (`id`),
    KEY `idx_history_key` (`tenant_id`, `rule_key`, `to_version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '规则变更历史（只追加 + 哈希链）';

-- ---------------------------------------------------------------------
-- 3) 规则读取审计
-- 业务目标：争议时能回答"这次判定当时读到的是哪一版规则、来自租户/灰度/平台哪一级"。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_rule_config_audit` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（@TableLogic）',
    `rule_key` varchar(96) NOT NULL COMMENT '规则键',
    `rule_version` int NOT NULL COMMENT '实际命中的版本号',
    `hit_source` varchar(16) NOT NULL COMMENT '命中来源 code：TENANT/GRAY/PLATFORM/FALLBACK',
    `biz_scene` varchar(64) DEFAULT NULL COMMENT '业务场景（如 SAFETY_RISK_EVAL，便于按场景统计）',
    `read_by` varchar(64) DEFAULT NULL COMMENT '读取人（用户 ID；SYSTEM 表示定时任务）',
    `read_at` datetime NOT NULL COMMENT '读取时刻',
    `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪 ID',
    PRIMARY KEY (`id`),
    KEY `idx_audit_key` (`tenant_id`, `rule_key`, `read_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '规则读取审计（争议追溯）';

-- ---------------------------------------------------------------------
-- 4) 电子证照域默认配置种子（幂等：INSERT IGNORE，不覆盖运维已调整的值）
-- 依据：契约《电子证照模块-前端接口契约-v1.0.md》§1.2 + 《电子资质七大模块技术方案.md》§1.2/§5.2/§7.2；
--      法定下限依据：《排污许可管理条例》第十四条（60 日）、TSG 特种设备生产许可（期满前 6 个月=180）、
--      《安全生产许可证条例》（期满前 3 个月=90）、《注册建造师管理规定》（届满前 30 日延续 + 继续教育 90）。
-- 业务目标：前端所有阈值均由此表驱动，运营改配置即改行为，前端无需发版。
-- 说明：config_value 用 JSON 承载（标量也写 JSON），value_type 决定前端解释方式；legal_floor 限定可配边界防误配。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_rule_config`
(`id`, `tenant_id`, `rule_key`, `rule_group`, `value_type`, `config_value`, `legal_floor`, `description`, `regulation_ref`, `version_no`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(700000000000000001, 0, 'cert.expiring.window.days', 'CERT', 'INT', '60', '{"min":1,"max":365}',
 '距有效期止 ≤ 该天数视为“即将到期”（列表角标与筛选口径）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000002, 0, 'cert.alert.thresholds', 'ALERT', 'JSON', '[90,60,30,7]', '{"itemMin":1,"itemMax":365}',
 '到期预警多级提前天数（档位映射：大档 INFO → 临近 WARNING → 最紧 CRITICAL）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000003, 0, 'cert.alert.legal.floor.days', 'ALERT', 'JSON',
 '{"SEWAGE_PERMIT":60,"BOILER_REVIEW":60,"SPECIAL_EQUIPMENT":180,"SAFETY_PERMIT":90,"REG_PRACTICE_CERT":90,"ID_CARD":90}',
 '{"itemMin":1,"itemMax":365}',
 '各证照类别的法定预警下限（保存预警天数时不得低于此值）',
 '《排污许可管理条例》第十四条；《安全生产许可证条例》；《注册建造师管理规定》', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000004, 0, 'cert.verify.idempotent.window.seconds', 'CERT', 'INT', '60', '{"min":1,"max":3600}',
 '同证同渠道重复验真的幂等窗口（秒），窗口内重复请求返回同一条验真记录', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000005, 0, 'cert.file.max.size.mb', 'CERT', 'INT', '50', '{"min":1,"max":2048}',
 '证照文件单文件大小上限（MB），前端上传前校验', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000006, 0, 'cert.file.allow.formats', 'CERT', 'JSON', '["PDF","JPG","PNG","OFD"]', '{"itemMin":1}',
 '允许上传的文件格式（OFD 为陕西住建电子证书格式，GB/T 36905 版式）', 'GB/T 36905', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000007, 0, 'cert.annotation.max.days', 'CERT', 'INT', '90', '{"min":1,"max":90}',
 '加注件（电子证书短期授权）最长有效天数；过期须重新申请，不自动续期',
 'GB/T 36903 §5.4；用户口径“最长 90 天”', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000008, 0, 'cert.annotation.warn.days', 'ALERT', 'JSON', '[30,7,1]', '{"itemMin":1,"itemMax":365}',
 '加注件到期预警档位（天）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000009, 0, 'cert.annotation.cascade.on.revoke', 'CERT', 'BOOL', 'true', NULL,
 '主证书被吊销/暂扣时，其名下有效加注件是否自动级联失效', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000010, 0, 'personnel.cert.expiring.window.days', 'PERSONNEL', 'INT', '30', '{"min":1,"max":365}',
 '员工证书“临近到期”窗口（天），≤ 该值由预警扫描置 EXPIRING_SOON', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000011, 0, 'personnel.cert.review.warn.days', 'PERSONNEL', 'INT', '60', '{"min":1,"max":365}',
 '员工证书复审/年审提前提醒天数（复审是与到期日独立的时间维度）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 6) 安许 A/B/C 联动与 risk_flag 默认配置种子（方案 §1.2；幂等 INSERT IGNORE）
-- 业务目标：黄/红/失效/频繁四态、宽限、豁免、人员异常、关键岗位变更、多岗校验的参数全部可运营调整。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_rule_config`
(`id`, `tenant_id`, `rule_key`, `rule_group`, `value_type`, `config_value`, `legal_floor`, `description`, `regulation_ref`, `version_no`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(720000000000000001, 0, 'safety.abc.required', 'SAFETY', 'JSON', '{"A":1,"B":1,"C":1}', '{"itemMin":0,"itemMax":99}',
 'A/B/C 三类安全生产管理人员最低配备人数（按资质等级可覆盖）', '《安全生产许可证条例》（国务院令第653号修正）', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000002, 0, 'safety.abc.roles', 'SAFETY', 'JSON', '["A","B","C"]', '{"itemMin":1}',
 '参与判定的角色集合（新增角色类别时在此扩展）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000003, 0, 'safety.abc.exempt.ent.types', 'SAFETY', 'JSON', '{"LABOR_SUBCONTRACT":["B"]}', NULL,
 '豁免矩阵：企业类型 → 允许为 0 而不触发红色的角色（劳务分包企业 B 证为 0 不触发红）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000004, 0, 'safety.abc.grace.legal.person.change.days', 'SAFETY', 'INT', '180', '{"min":1,"max":365}',
 '法定代表人变更致人数不足时的宽限天数（宽限期内风险降一档）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000005, 0, 'safety.abc.grace.reason.whitelist', 'SAFETY', 'JSON', '["LEGAL_PERSON_CHANGE"]', '{"itemMin":1}',
 '允许触发宽限的原因白名单（防滥用宽限逃避整改）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000006, 0, 'safety.abc.frequent.alert.threshold', 'SAFETY', 'INT', '4', '{"min":1,"max":99}',
 '滑动窗口内预警次数达到该值即打"频繁预警"标签', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000007, 0, 'safety.abc.frequent.window.days', 'SAFETY', 'INT', '365', '{"min":1,"max":1095}',
 '频繁预警统计滑动窗口（天）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000008, 0, 'safety.abc.invalid.grace.days', 'SAFETY', 'INT', '90', '{"min":1,"max":365}',
 '三类全为 0 后允许整改的天数，逾期列失效（陕西口径：3 个月不整改列失效）', '06 文档 §二-1（附录A §2.6）', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000009, 0, 'safety.abc.block.flags', 'SAFETY', 'JSON', '["INVALID","RED"]', '{"itemMin":1}',
 '哪些风险档位阻断业务（招投标/施工许可）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000010, 0, 'personnel.job.change.threshold', 'SAFETY', 'INT', '4', '{"min":1,"max":99}',
 '人员 12 个月内更换受聘企业次数达到该值即打"一年异常"标签', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000011, 0, 'personnel.job.change.window.days', 'SAFETY', 'INT', '365', '{"min":1,"max":1095}',
 '人员更换受聘企业统计窗口（天，默认 12 个月）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000012, 0, 'project.key.post.change.threshold', 'SAFETY', 'INT', '2', '{"min":1,"max":99}',
 '同一项目同一关键岗位变更次数阈值（超阈值产出挂靠/管理失控待办）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000013, 0, 'project.key.post.change.window.days', 'SAFETY', 'INT', '90', '{"min":1,"max":1095}',
 '关键岗位变更统计窗口（天）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000014, 0, 'personnel.multi.post.require.cert', 'SAFETY', 'BOOL', 'true', NULL,
 '兼任两个及以上岗位是否必须持另一岗位考核合格证书', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(720000000000000015, 0, 'risk.level.mapping', 'SAFETY', 'JSON',
 '{"NORMAL":"LOW","YELLOW":"MEDIUM","RED":"HIGH","INVALID":"MAJOR","FREQUENT":"MEDIUM","ABNORMAL":"MEDIUM"}', NULL,
 '风险档 → 风险等级映射（供周检 Job 的提前量取值）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0');


-- ---------------------------------------------------------------------
-- 5) 业务字典表（电子证照域枚举的“唯一动态来源”）
-- 依据：用户硬约束“状态码/枚举值必须支持动态配置，严禁硬编码”；
--      契约《电子证照模块-前端接口契约-v1.0.md》§1.1（GET /bidding/meta/dicts 的返回结构）。
-- 业务目标：前端下拉/标签/筛选全部由本表驱动；运营在后台增改枚举（新增状态、改中文名、改标签色）后，
--          前端无需发版即生效；新增枚举值也不会因前端 switch 穷举而渲染异常。
-- 设计说明：不依赖 platform 侧 system_dict_data（跨库依赖会引入部署耦合），本模块自带字典表，
--          label/color_type 即契约里的 label/tagType，terminal 表示终态（前端据此禁用流转按钮）。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_dict_data` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（0=平台默认；租户可覆盖同名枚举）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（@TableLogic）',
    `dict_type` varchar(48) NOT NULL COMMENT '字典类型 code（如 cert_status/file_category；契约 §1.1 的类型名）',
    `dict_value` varchar(64) NOT NULL COMMENT '枚举值 code（提交给后端的值）',
    `dict_label` varchar(128) NOT NULL COMMENT '中文名（前端直接展示）',
    `color_type` varchar(16) NOT NULL DEFAULT 'default' COMMENT '标签色 code：success/warning/error/info/default',
    `terminal` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否终态（终态禁止再流转，前端据此禁用按钮）',
    `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '启用开关（关掉后前端不再展示该枚举）',
    `remark` varchar(256) DEFAULT NULL COMMENT '说明',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict` (`tenant_id`, `dict_type`, `dict_value`, `deleted`),
    KEY `idx_dict_type` (`dict_type`, `enabled`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务字典（电子证照域枚举动态来源）';

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 证照台账状态（对齐建办市〔2024〕51号 ZZZT；终态不可再流转）
(710000000000000001,0,'cert_status','ACTIVE','正常','success',b'0',1,b'1','有效可用，自查只认此状态','1',NOW(),'1',NOW(),b'0'),
(710000000000000002,0,'cert_status','EXPIRING_SOON','即将到期','warning',b'0',2,b'1','由到期扫描按配置窗口推导','1',NOW(),'1',NOW(),b'0'),
(710000000000000003,0,'cert_status','EXPIRED','已过期','error',b'0',3,b'1','到期扫描推导','1',NOW(),'1',NOW(),b'0'),
(710000000000000004,0,'cert_status','SUSPENDED','暂扣','warning',b'0',4,b'1','限期整改语义','1',NOW(),'1',NOW(),b'0'),
(710000000000000005,0,'cert_status','REVOKED','吊销','error',b'1',5,b'1','终态','1',NOW(),'1',NOW(),b'0'),
(710000000000000006,0,'cert_status','CANCELLED','注销','info',b'1',6,b'1','终态','1',NOW(),'1',NOW(),b'0'),
-- 验真状态
(710000000000000011,0,'verification_status','UNVERIFIED','未验真','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000012,0,'verification_status','VERIFYING','验真中','info',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000013,0,'verification_status','VERIFIED','已验真','success',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000014,0,'verification_status','INVALID','验真不通过','error',b'0',4,b'1','需人工核实真伪','1',NOW(),'1',NOW(),b'0'),
-- 文件类别（GB/T 36903 §5.4 加注件相关）
(710000000000000021,0,'file_category','ORIGINAL','原件','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000022,0,'file_category','COPY','加注件','info',b'0',2,b'1','需填加注件 4 元数据','1',NOW(),'1',NOW(),b'0'),
(710000000000000023,0,'file_category','CHANGE_PAGE','变更页','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000024,0,'file_category','RENEWAL_PAGE','延续页','default',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000025,0,'file_category','E_CERT','电子证照','success',b'0',5,b'1','OFD 版式','1',NOW(),'1',NOW(),b'0'),
-- 验真渠道（官方白名单，禁自由填 URL）
(710000000000000031,0,'verify_channel','FOUR_PLATFORM','四库一平台','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000032,0,'verify_channel','MINISTRY','部级平台','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000033,0,'verify_channel','PROVINCE','省级平台','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 数据来源
(710000000000000041,0,'source_channel','MANUAL','手工录入','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000042,0,'source_channel','IMPORT','批量导入','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000043,0,'source_channel','OCR','识别录入','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 通知渠道
(710000000000000051,0,'notify_channel','EMAIL','邮件','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000052,0,'notify_channel','SMS','短信','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000053,0,'notify_channel','IN_APP','站内信','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000054,0,'notify_channel','DINGTALK','钉钉','default',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000055,0,'notify_channel','WEBHOOK','Webhook','default',b'0',5,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 生命周期类型
(710000000000000061,0,'lifecycle_type','CHANGE','变更','info',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000062,0,'lifecycle_type','RENEW','续期','info',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000063,0,'lifecycle_type','REVOKE','注销吊销','warning',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000064,0,'lifecycle_type','ANNUAL','年检','info',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 生命周期状态（审批流）
(710000000000000071,0,'lifecycle_status','PENDING','待办','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000072,0,'lifecycle_status','REVIEWING','审核中','info',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000073,0,'lifecycle_status','APPROVED','已通过','success',b'0',3,b'1','可执行生效','1',NOW(),'1',NOW(),b'0'),
(710000000000000074,0,'lifecycle_status','REJECTED','已驳回','error',b'1',4,b'1','终态','1',NOW(),'1',NOW(),b'0'),
(710000000000000075,0,'lifecycle_status','EFFECTIVE','已生效','success',b'1',5,b'1','终态，已回写台账','1',NOW(),'1',NOW(),b'0'),
-- 流程作用对象域
(710000000000000081,0,'biz_domain','CERT','证照台账','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000082,0,'biz_domain','PERSON_CERT','员工证书','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000083,0,'biz_domain','ENTERPRISE_QUAL','企业资质','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000084,0,'biz_domain','SAFETY_PERMIT','安全生产许可证','default',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 员工证书状态
(710000000000000091,0,'person_cert_status','VALID','有效','success',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000092,0,'person_cert_status','EXPIRING_SOON','即将到期','warning',b'0',2,b'1','由预警扫描置位，人工不可设','1',NOW(),'1',NOW(),b'0'),
(710000000000000093,0,'person_cert_status','EXPIRED','已过期','error',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000094,0,'person_cert_status','SUSPENDED','暂扣','warning',b'0',4,b'1','年检不通过等，可恢复','1',NOW(),'1',NOW(),b'0'),
(710000000000000095,0,'person_cert_status','REVOKED','吊销','error',b'1',5,b'1','终态','1',NOW(),'1',NOW(),b'0'),
(710000000000000096,0,'person_cert_status','CANCELLED','注销','info',b'1',6,b'1','终态','1',NOW(),'1',NOW(),b'0'),
-- 员工证书类型
(710000000000000101,0,'person_cert_type','ID_CARD','身份证','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000102,0,'person_cert_type','EDUCATION_CERT','学历证书','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000103,0,'person_cert_type','DEGREE_CERT','学位证书','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000104,0,'person_cert_type','TITLE_CERT','职称证书','default',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000105,0,'person_cert_type','REG_PRACTICE_CERT','注册执业证书','default',b'0',5,b'1','如注册建造师','1',NOW(),'1',NOW(),b'0'),
(710000000000000106,0,'person_cert_type','SAFETY_ASSESS_CERT','安全生产考核合格证','default',b'0',6,b'1','A/B/C 三类人员','1',NOW(),'1',NOW(),b'0'),
(710000000000000107,0,'person_cert_type','SKILL_CERT','职业技能等级证书','default',b'0',7,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000108,0,'person_cert_type','TRAINING_CERT','培训合格证书','default',b'0',8,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000109,0,'person_cert_type','OTHER','其他','default',b'0',9,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 注销吊销方式 → 目标状态
(710000000000000111,0,'revoke_type','REVOKE','吊销','error',b'0',1,b'1','目标状态 REVOKED','1',NOW(),'1',NOW(),b'0'),
(710000000000000112,0,'revoke_type','CANCEL','注销','info',b'0',2,b'1','目标状态 CANCELLED','1',NOW(),'1',NOW(),b'0'),
(710000000000000113,0,'revoke_type','SUSPEND','暂扣','warning',b'0',3,b'1','目标状态 SUSPENDED','1',NOW(),'1',NOW(),b'0'),
(710000000000000114,0,'revoke_type','RESTORE','恢复','success',b'0',4,b'1','目标状态 VALID','1',NOW(),'1',NOW(),b'0'),
-- 续期方式
(710000000000000121,0,'renewal_type','RENEW','延续','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000000122,0,'renewal_type','REISSUE','补换证','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
-- 材料大类
(710000000000000131,0,'material_category','IDENTITY','身份类','default',b'0',1,b'1','营业执照/法人身份证明/授权委托书','1',NOW(),'1',NOW(),b'0'),
(710000000000000132,0,'material_category','QUALIFICATION','资质类','default',b'0',2,b'1','资质证书/安全生产许可证','1',NOW(),'1',NOW(),b'0'),
(710000000000000133,0,'material_category','STRENGTH','实力类','default',b'0',3,b'1','审计报告/业绩/社保证明','1',NOW(),'1',NOW(),b'0'),
(710000000000000134,0,'material_category','OTHER','其他','default',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0');


