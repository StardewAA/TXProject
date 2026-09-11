-- =====================================================================
-- 电子资质域 · 一键初始化包（master-credential-sync.sql）
-- =====================================================================
-- 业务库：ruoyi-vue-pro（电子资质全部业务表；本环境 system_menu / system_role_menu 同库）。
--
-- 【用途】新环境一次性建好"电子资质"全部功能：电子证照台账 + 文件版本 + 验真 + 到期预警、企业资质与
--   资质标准/要素/达标核查、员工资质（档案/证书/履历/材料完备性）、证件全生命周期（变更/续期/注销吊销/年检）、
--   规则配置中心与业务字典、安许 A/B/C 风险联动。执行后菜单、权限点、默认阈值、枚举字典齐备。
--
-- 【与 master-bidding-sync.sql 的关系】后者是招投标主流程初始包（不含电子资质域，避免互相覆盖）；
--   本包只补电子资质域，两者可分别独立执行，无先后依赖（本包不引用招投标主流程的表）。
--
-- 【口径】SQL 只保留表结构 + 最基础数据（默认阈值、枚举字典、菜单与权限点）；业务数据不入脚本。
--   全部语句幂等（CREATE TABLE IF NOT EXISTS / INSERT IGNORE / ON DUPLICATE KEY UPDATE），
--   历史 ALTER TABLE（加列/改列）已折叠进对应基表 CREATE TABLE —— 包内无 ALTER，重复执行不会报"列已存在"；
--   包内无 DROP / TRUNCATE / DELETE（历史迁移动作已在本包中"固化终态"，不再需要执行迁移脚本）。
--
-- 【执行】
--   mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/master-credential-sync.sql
--   结构自检：node sql/tools/validate-sql-structure.js sql/master-credential-sync.sql
--
-- 【菜单号段】证书与资质台账使用 60005000 段（60005000 目录 / 600051xx 证照台账 / 600052xx 企业资质 /
--   600053xx 员工资质 / 600054xx 证件全生命周期 / 600055xx 安许风险联动）。
--   原 60002000/600021xx/600022xx/600023xx/600024xx 号段已废弃（与外部公告采集域冲突）；
--   60005000/600042xx 段已被《docs/前端规范-全域接口说明/投标流程跟踪功能设计方案.md》§8.3 预约
--   （投标流程跟踪：父级 60005000 + 页面 60005200 + 按钮 60005201~203），按"先登记再占用"让出，
--   故本域改用 60005000 段（登记时 SQL 侧 60005000~60005999 无任何占用）。
--   本包直接落终态，不产生废弃号段的行。
--
-- 【不包含】角色菜单授权（除超级管理员）——授权属业务决策；见包尾"授权指引"按需执行。
--   也不包含：演示/业务数据、文件实体（file_id 由 FileApi 上传产生）、AI 渠道与密钥。
-- =====================================================================

-- #####################################################################
-- 【来源】2026-09-04-certificate.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 资质/证书域（certificate）建表脚本 —— txgy-module-bidding 小模块 · M1 证照台账域
-- 依据：
--   1) 《招投标模块开发规范》第五章（表命名 cert_ 例外已登记 §5.1.4；T 轨 TenantBaseDO；
--      审计列 BaseDO 四-1；文件 FileApi 三-5；代码列三-7；UK 含 deleted+tenant_id 六-1；
--      只追加表归档六-3）；§4.5（乐观锁 version 列）；§4.8（密级列）
--   2) 《电子证件全流程管理_数据库设计.md》v3.3 及 §18 v3.4 对齐说明（certificate 分支）
--   3) GB/T 36903—2018（证照元数据 5.2/5.4）、GB/T 36904—2018（证照标识）、
--      GB/T 36905—2018（OFD 文件）、建办市〔2024〕51 号（建设工程企业资质证书 17 信息项/ZZZT）
-- 表前缀例外登记：开发规范 §5 一-4（cert_/qualification_ 已登记，不套 bidding_）
-- 本文件为 M1 首批：certificate_info（表1）、cert_file_versions（表2）、cert_change_logs（表13）
-- 其余表（审查/自查/续期/变更/吊销/预警/企业资质等）随 M 里程碑分批落 sql/
-- =====================================================================

-- 表1：certificate_info 证照台账主表（T 轨；只存证照事实，审查结论不落本表 —— v3.3 §二）
CREATE TABLE IF NOT EXISTS `certificate_info` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（录入人）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除 @TableLogic；删痕走 cert_change_logs）',
    `org_id` bigint DEFAULT NULL COMMENT '组织归属（v3.3 §二）',
    `cert_name` varchar(256) NOT NULL COMMENT '证照名称（GB/T 36903 5.2.1 ZZMC/certificateName；AI 自查名称匹配）',
    `cert_no` varchar(128) NOT NULL COMMENT '证书编号（建办市51号 ZSBH；随证型/码制浮动按 varchar(128) 落，开发规范 §5.2；租户内唯一）',
    `cert_type` varchar(16) NOT NULL COMMENT '证照大类（台账分类 code：ADMIN_LICENSE/SYSTEM_CERT/PERSON_QUAL/HONOR/OTHER，v3.3 §二）',
    `cert_category_code` varchar(32) DEFAULT NULL COMMENT '证照类型代码（GB/T 36902 5.2.2 ZZLXDM 21 位/行标，如 11100000000013338W002）',
    `cert_level` varchar(64) DEFAULT NULL COMMENT '资质等级（一级/甲级…）',
    `cert_scope` text COMMENT '许可/业务范围（较长文本随表存储，§5 三-1）',
    `holder_name` varchar(256) NOT NULL COMMENT '持证主体（GB/T 36903 5.2.8 CZZT/certificateHolderName；AI 自查一票否决）',
    `holder_type` varchar(16) NOT NULL DEFAULT 'ORG' COMMENT '持证主体类别 code：ORG/PERSON/EXTERNAL（v3.3 §二）',
    `holder_id` bigint DEFAULT NULL COMMENT '关联档案 ID（人员/企业档案）',
    `issuing_authority` varchar(256) NOT NULL COMMENT '证照颁发机构（GB/T 36903 5.2.5 ZZBFJG；L2）',
    `issuing_authority_code` varchar(32) DEFAULT NULL COMMENT '证照颁发机构代码（GB/T 36903 5.2.6 ZZBFJGDM，统一社会信用代码 GB 32100）',
    `issue_date` date DEFAULT NULL COMMENT '证照颁发日期（GB/T 36903 5.2.7 ZZBFRQ；展示用）',
    `valid_from` date DEFAULT NULL COMMENT '证照有效期起始日期（GB/T 36903 5.2.12 ZZYXQQSRQ；自查覆盖判断）',
    `valid_to` date DEFAULT NULL COMMENT '证照有效期截止日期（GB/T 36903 5.2.13 ZZYXQJZRQ；NULL=长期；自查余期+到期预警扫描）',
    `applicable_industries` json DEFAULT NULL COMMENT '适用行业（结构化 JSON）',
    `applicable_regions` json DEFAULT NULL COMMENT '适用地区（结构化 JSON）',
    `annual_review_cycle` smallint DEFAULT NULL COMMENT '年检周期（月），预警用',
    `next_review_date` date DEFAULT NULL COMMENT '下次年检日（自查+年检预警）',
    `is_exclusive_locked` bit(1) NOT NULL DEFAULT b'0' COMMENT '排他锁定（在建项目占用）',
    `lock_info` json DEFAULT NULL COMMENT '锁定明细（结构化 JSON）',
    `e_cert_ident` varchar(96) DEFAULT NULL COMMENT '电子证照标识（GB/T 36904 6 段编码+MOD37,36，≤74 位；51号 DZZZBZ；v3.4 起仅存标识，文件实体见 cert_file_versions）',
    `cert_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '台账状态 code（对齐 51号 ZZZT：ACTIVE/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED/CANCELLED；自查只认 ACTIVE）',
    `verification_status` varchar(16) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '验真状态 code：UNVERIFIED/VERIFYING/VERIFIED/INVALID（与 cert_status 独立，自查不认 INVALID）',
    `last_verified_at` datetime DEFAULT NULL COMMENT '最近验真时间',
    `verify_api_source` varchar(128) DEFAULT NULL COMMENT '验真来源',
    `official_verify_url` varchar(512) DEFAULT NULL COMMENT '官方核验链接（展示）',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/OCR/API_SYNC',
    `extra_props` json DEFAULT NULL COMMENT '动态扩展（结构化 JSON，如 cmmi_level）',
    `remark` text COMMENT '备注（较长文本随表存储，§5 三-1）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5 乐观锁款；更新走 WHERE id=? AND version=?）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_no` (`cert_no`, `deleted`, `tenant_id`),
    KEY `idx_cert_status` (`cert_status`),
    KEY `idx_cert_valid_to` (`valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照台账主表（T 轨 · cert_ 前缀例外）';

-- 表2：cert_file_versions 文件版本表（材料/文件管理 —— v3.3 §三 + v3.4 §18.2）
CREATE TABLE IF NOT EXISTS `cert_file_versions` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（上传人）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（上传时间）',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（is_current 切换等）',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `cert_id` bigint NOT NULL COMMENT '关联证照（冗余父键 certificate_info.id，§5 四-2）',
    `file_category` varchar(16) NOT NULL DEFAULT 'ORIGINAL' COMMENT '文件类别 code：ORIGINAL 原件/COPY 副本/CHANGE_PAGE 变更页/RENEWAL_PAGE 延续页/E_CERT 电子证照/OTHER（自查“有原件/有电子证照”按 ORIGINAL/E_CERT 且 is_current=TRUE 判定，v3.4 §18.1-2）',
    `file_name` varchar(256) NOT NULL COMMENT '文件名',
    `file_id` bigint NOT NULL COMMENT '文件对象主键（FileApi 上传，§5 三-5；URL 仅签名回显不作一致性依据）',
    `file_hash` varchar(64) NOT NULL COMMENT 'SHA-256（防篡改/去重，§5 三-5）',
    `file_format` varchar(16) DEFAULT NULL COMMENT '文件格式：PDF/JPG/PNG/OFD（OFD=GB/T 36905 版式电子证照）',
    `file_size` bigint NOT NULL COMMENT '字节数',
    `copy_creation_time` datetime DEFAULT NULL COMMENT '加注件制作时间（GB/T 36903 5.4.1 JZJZZSJ；仅加注件行填写）',
    `copy_creator` varchar(256) DEFAULT NULL COMMENT '加注件制作者（GB/T 36903 5.4.2 JZJZZZ）',
    `copy_cause` varchar(512) DEFAULT NULL COMMENT '加注件制作事由（GB/T 36903 5.4.3 JZJZZSY，一般同步水印）',
    `copy_expiring_time` datetime DEFAULT NULL COMMENT '加注件有效期截止时间（GB/T 36903 5.4.4 JZJYXQJZSJ）',
    `version` int NOT NULL DEFAULT 1 COMMENT '同类别文件版本号（只追加，旧版本只读保留）',
    `is_current` bit(1) NOT NULL DEFAULT b'1' COMMENT '当前生效版本（自查只取 TRUE 行）',
    `virus_scan_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '病毒扫描状态 code：PENDING/CLEAN/INFECTED（上传须 CLEAN 才可预览/下载）',
    `remark` text COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_file_hash` (`cert_id`, `file_hash`, `deleted`, `tenant_id`),
    UNIQUE KEY `uk_cert_cat_version` (`cert_id`, `file_category`, `version`, `deleted`, `tenant_id`),
    KEY `idx_cert_current` (`cert_id`, `is_current`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照文件版本表（T 轨）';

-- 表3：cert_change_logs 变更审计日志（必建 · 等保2.0 三级 —— v3.3 §13 + v3.4 §18.3）
CREATE TABLE IF NOT EXISTS `cert_change_logs` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（操作人 ID 字符串）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（操作时间）',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（只追加，业务不置 1）',
    `cert_id` bigint NOT NULL COMMENT '关联证照（冗余父键 certificate_info.id）',
    `field_name` varchar(64) NOT NULL COMMENT '变更字段名（STATUS_CHANGE 时为 cert_status 等）',
    `old_value` text COMMENT '变更前值（CREATE 行为空）',
    `new_value` text COMMENT '变更后值',
    `change_type` varchar(16) NOT NULL COMMENT '变更类型 code：CREATE/UPDATE/STATUS_CHANGE/REVOKE',
    `op_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '操作来源 code：MANUAL/IMPORT/OCR/API_SYNC/SYSTEM',
    `op_ip` varchar(45) DEFAULT NULL COMMENT '操作 IP（支持 IPv6；审计追溯，日志侧脱敏展示）',
    `batch_id` varchar(64) DEFAULT NULL COMMENT '批次号（整批导入/回滚归组）',
    `record_hash` varchar(64) NOT NULL COMMENT '本行哈希 SHA-256(关键字段拼接)，防篡改',
    `prev_hash` varchar(64) NOT NULL DEFAULT 'ROOT' COMMENT '哈希链前驱 record_hash（链首=ROOT）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_change_prev` (`cert_id`, `prev_hash`, `deleted`, `tenant_id`),
    KEY `idx_change_cert_time` (`cert_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照变更审计日志（只追加·哈希链；保留≥3年，按月归档任务见 v3.4 §18.3-2）';


-- #####################################################################
-- 【来源】2026-09-05-cert-alert.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 资质/证书域（certificate）建表脚本 —— txgy-module-bidding 小模块 · M3 到期预警域
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §九（表 8/9 cert_alert_config + cert_alert_records，
--      阈值档位映射与幂等键）＋§十一 表12（cert_notify_outbox 事务性发件箱）＋§十四（索引/唯一约束）
--   2) 《招投标模块开发规范》§4.5（乐观锁 version 列）§5 一-4（cert_ 前缀例外已登记）四-1（审计列）
--   3) 51 号文 ZZZT/EXPIRING_SOON 语义（台账状态由预警扫描驱动进入 EXPIRING_SOON）
-- 本文件为 M3：cert_alert_config（表8）、cert_alert_records（表9）、cert_notify_outbox（表12）
-- 说明：v3.3 已含三表设计（§九/§十一）；本脚本按平台 T 轨列约定落地（主键 id 雪花、
--   BaseDO 审计列、deleted 逻辑删；预警/发件行实际只追加不软删，deleted 恒 0）
-- =====================================================================

-- 表8：cert_alert_config 到期/年检/续期预警配置（租户级；cert_type IS NULL=全局，行数由服务层约束为 1）
CREATE TABLE IF NOT EXISTS `cert_alert_config` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨；NULL=平台默认由服务层默认常量承担，v3.3 §九）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `cert_type` varchar(16) DEFAULT NULL COMMENT '差异化配置的证照大类 code；NULL=全局（v3.3 §九）',
    `alert_type` varchar(16) NOT NULL DEFAULT 'EXPIRY' COMMENT '预警类型 code：EXPIRY 到期/REVIEW 年检/RENEWAL 续期（v3.3 §九）',
    `thresholds` json NOT NULL COMMENT '提前天数多级预警阈值（JSON 数组，默认 [90,60,30,7]；扫描按档位映射生成 INFO/WARNING/CRITICAL）',
    `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '开关',
    `notify_channels` json NOT NULL COMMENT '通知渠道（JSON 数组，默认 ["EMAIL","IN_APP"]；EMAIL/SMS/IN_APP/DINGTALK/WEBHOOK）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5）',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type` (`tenant_id`, `cert_type`, `alert_type`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '到期/年检/续期预警配置（T 轨）';

-- 表9：cert_alert_records 预警记录（幂等：同一 (cert_id, alert_type, 触发日期) 只落一条 —— v3.3 §九）
CREATE TABLE IF NOT EXISTS `cert_alert_records` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 alert_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（SYSTEM）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（=触发时间 triggered_at）',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（恒 0，只追加）',
    `cert_id` bigint DEFAULT NULL COMMENT '关联证照 certificate_info.id（EXPIRY 等证照类填；QUAL_EXPIRY 资质类置 NULL，用 ref_* 定位）'
    `alert_type` varchar(16) NOT NULL DEFAULT 'EXPIRY' COMMENT '预警类型 code：EXPIRY/REVIEW/RENEWAL',
    `alert_level` varchar(16) NOT NULL DEFAULT 'WARNING' COMMENT '档位 code：INFO/WARNING/CRITICAL（90/60→INFO、30→WARNING、7→CRITICAL，多档取最严重）',
    `idempotent_key` varchar(64) NOT NULL COMMENT '幂等键：cert_id:alert_type:yyyyMMdd 触发日期，防定时任务重启/重跑重复（v3.3 §九）',
    `alert_message` varchar(512) NOT NULL COMMENT '预警内容（如“将于 30 天后到期”）',
    `triggered_at` datetime NOT NULL COMMENT '触发时间',
    `notify_channel` json DEFAULT NULL COMMENT '实际通知渠道多选（JSON，镜像配置，含落库快照语义）',
    `notify_target` varchar(256) DEFAULT NULL COMMENT '接收人 ID/角色（脱敏；当前默认 TENANT_ADMIN 占位）',
    `notify_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '通知状态 code：PENDING/SENDING/SUCCESS/FAILED/DEAD（与 cert_notify_outbox 联动可靠投递+失败补偿）',
    `handled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已处理（处理闭环，如“已续期”）',
    `handled_by` bigint DEFAULT NULL COMMENT '处理人',
    `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
    `handle_note` varchar(512) DEFAULT NULL COMMENT '处理备注',
    `archive_status` tinyint NOT NULL DEFAULT 0 COMMENT '归档状态：0 在库 /1 已归档（已处理满 6 个月归档、保留 2 年，定时任务处理）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（handled/notify_status CAS 更新防并发覆盖，v3.3 §九）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_idempotent` (`idempotent_key`),
    KEY `idx_alert_handle` (`tenant_id`, `handled`, `triggered_at` DESC),
    `ref_type` varchar(24) DEFAULT NULL COMMENT '业务对象大类 code（QUAL_EXPIRY 用）：QUAL 企业资质/SAFETY_PERMIT 安许/STAFF 资质人员' AFTER `cert_id`,
    `ref_id` bigint DEFAULT NULL COMMENT '业务对象主键（qual_id/permit_id/staff_id）' AFTER `ref_type`,
    `ref_name` varchar(256) DEFAULT NULL COMMENT '业务对象显示名（脱敏）' AFTER `ref_id`
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预警记录（T 轨 · 幂等防重跑）';

-- 表12：cert_notify_outbox 通知发件箱（事务性发件箱：与业务同事务写入，通知不丢不重 —— v3.3 §十一）
-- 说明：发送扫描为全局任务（设计 INDEX 全局不分租户）；T 轨保留 tenant_id 以在任务中按租户执行
CREATE TABLE IF NOT EXISTS `cert_notify_outbox` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 outbox_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨；发送任务按租户执行）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（业务写入方）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者（发送器）',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（恒 0）',
    `biz_type` varchar(16) NOT NULL COMMENT '业务类型 code：ALERT/CHECK_REMIND/REVIEW_REMIND',
    `biz_id` bigint NOT NULL COMMENT '关联业务主键（alert_id / check_id / review_id）',
    `channel` varchar(16) NOT NULL COMMENT '渠道 code：EMAIL/SMS/IN_APP/DINGTALK/WEBHOOK',
    `receiver` varchar(256) NOT NULL COMMENT '接收方（L3，落库脱敏）',
    `payload` json NOT NULL COMMENT '发送内容快照（防事后二次渲染不一致）',
    `send_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '发送状态 code：PENDING/SENDING/SUCCESS/FAILED/DEAD（DEAD=死信转人工+告警）',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retry` int NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间（指数退避 1/5/30 分、2/6 时）',
    `last_error` varchar(512) DEFAULT NULL COMMENT '最近一次错误信息（排障）',
    `sent_at` datetime DEFAULT NULL COMMENT '发送成功时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_biz` (`biz_type`, `biz_id`, `channel`, `receiver`),
    KEY `idx_outbox_retry` (`send_status`, `next_retry_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知发送发件箱（T 轨 · 可靠投递）';


-- #####################################################################
-- 【来源】2026-09-06-cert-verify-history.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 资质/证书域（certificate）建表脚本 —— txgy-module-bidding 小模块 · M4 验真历史域
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §17.5（新增表 D cert_verify_history 验真历史表（必建））：
--      主表 verification_status 仅承载最近一次验真状态；历史多次调用落本表，
--      配合表 11 usage_type=VERIFY 形成验真调用链审计
--   2) GB/T 36906—2018 §6.4/§6.5（verifyinfo/verifyfile 调用；head/data JSON 报文）＋§5.2.2（认证）
--   3) 《招投标模块开发规范》§4.5（幂等）§5 一-4（cert_ 前缀例外）四-1（审计列）
-- =====================================================================

-- 表D：cert_verify_history 验真历史表（只追加 · 幂等防重）
CREATE TABLE IF NOT EXISTS `cert_verify_history` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 verify_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（=发起人，可 SYSTEM）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（恒 0，只追加）',
    `cert_id` bigint NOT NULL COMMENT '被验真证照 certificate_info.id',
    `verify_channel` varchar(16) NOT NULL COMMENT '验真渠道 code：FOUR_PLATFORM 四库一平台/MINISTRY 部级/PROVINCE 省级/THIRD_PARTY 第三方（v3.3 §17.5）',
    `verify_result` varchar(16) NOT NULL COMMENT '验真结论 code：MATCH 一致/MISMATCH 不一致/UNAVAILABLE 不可得（v3.3 §17.5）',
    `request_snapshot` json NOT NULL COMMENT '请求参数快照（L3，含证书编号等敏感项，报文原文留痕）',
    `result_snapshot` json NOT NULL COMMENT '返回结果快照（L3，36906 报文原文留痕）',
    `verify_by` bigint DEFAULT NULL COMMENT '发起人（含 SYSTEM，用户 ID）',
    `verified_at` datetime NOT NULL COMMENT '验真完成时间',
    `idempotent_key` varchar(64) NOT NULL COMMENT '幂等键：cert_id:channel:yyyyMMddHHmm 防重复调用写入（v3.3 §17.5 UNIQUE）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_verify_idempotent` (`idempotent_key`),
    KEY `idx_verify_cert_time` (`tenant_id`, `cert_id`, `verified_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '验真历史表（T 轨 · 只追加 · 幂等防重）';


-- #####################################################################
-- 【来源】2026-09-07-enterprise-qualification.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 资质/证书域（qualification_）建表脚本 —— txgy-module-bidding 小模块 · M5 企业资质域
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §十五（表 15-1 enterprise_qualification 资质主档 /
--      表 15-7 qualification_apply_record 资质业务申报流（状态机 + 乐观锁））＋全局约定
--      （公共列：tenant_id/审计列/idempotent_key/version/archive_status；T 轨）
--   2) 《招投标模块开发规范》§5 一-4（qualification_ 前缀例外已登记）四-1（审计列）§4.5（乐观锁/状态流转走接口）
--   3) 建办市〔2024〕51 号 ZZZT 状态口径（台账状态）与国标表110/表111（资质等级/类别，随主系统字典）
-- 本文件为 M5：enterprise_qualification（表15-1）、qualification_apply_record（表15-7）
-- 说明：主档与申报流状态机 + 乐观锁；申报办结回写主档 cert_no/valid_to（同事务，表15-7 注释口径）；
--   主档 valid_to 到期预警复用见 v3.3 §十五（alert_type 扩展 QUAL_EXPIRY，后续里程碑接入扫描）
-- =====================================================================

-- 表15-1：enterprise_qualification 企业资质主档
CREATE TABLE IF NOT EXISTS `enterprise_qualification` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 qual_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `ent_id` bigint NOT NULL COMMENT '关联企业主体（组织架构/租户）',
    `qual_serial` varchar(16) NOT NULL COMMENT '资质序列 code：CONSTRUCTION 施工/SURVEY 勘察/DESIGN 设计/SUPERVISION 监理/TESTING 检测（v3.3 15-1）',
    `qual_category` varchar(64) NOT NULL COMMENT '资质类别：总承包/专业承包/劳务，如“市政公用工程施工总承包”',
    `qual_grade` varchar(32) NOT NULL COMMENT '资质等级（特级/一级/二级…，对齐国标表110 资质等级）',
    `cert_no` varchar(128) NOT NULL COMMENT '资质证书编号；UK(tenant,serial,category,grade,cert_no)',
    `issuing_authority` varchar(256) NOT NULL COMMENT '发证/许可机关',
    `issue_date` date DEFAULT NULL COMMENT '发证日期',
    `valid_to` date DEFAULT NULL COMMENT '有效期至（NULL=长期；驱动到期预警扫描）',
    `approval_scope` text COMMENT '批准承包/承揽范围（长文本随表存储）',
    `qual_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '资质状态 code：VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW 待续期/LIMITED 限期/REVOKED（v3.3 15-1）',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联 certificate_info（证书影像/验真复用通用台账）',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/API_SYNC/PLATFORM',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qual_cert_no` (`tenant_id`, `qual_serial`, `qual_category`, `qual_grade`, `cert_no`, `deleted`),
    KEY `idx_qual_ent_status` (`tenant_id`, `ent_id`, `qual_status`),
    KEY `idx_qual_valid_to` (`valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质主档（qualification_ 前缀例外）';

-- 表15-7：qualification_apply_record 资质业务申报流（状态机 + 乐观锁；办结回写主档）
CREATE TABLE IF NOT EXISTS `qualification_apply_record` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 apply_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `qual_id` bigint DEFAULT NULL COMMENT '目标资质（新办可空，表15-7）',
    `apply_type` varchar(16) NOT NULL COMMENT '申报类型 code：NEW 新办/UPGRADE 升级/RENEW 延续/ADDITION 增项/CHANGE 变更/CANCEL 注销',
    `target_grade` varchar(32) DEFAULT NULL COMMENT '目标等级（升级/增项用）',
    `submit_date` date DEFAULT NULL COMMENT '提交日期',
    `due_date` date DEFAULT NULL COMMENT '预计办结日期',
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '申报状态 code：DRAFT/SUBMITTED/ACCEPTED 受理/REVIEWING 审核/APPROVED/PUBLISHED 公示/ISSUED 领证/REJECTED 退回',
    `material_snapshot` json DEFAULT NULL COMMENT '申报材料清单快照（四要素佐证引用）',
    `review_ids` json DEFAULT NULL COMMENT '关联达标核查记录（表15-6）',
    `result_no` varchar(128) DEFAULT NULL COMMENT '批复/许可决定文号',
    `new_cert_no` varchar(128) DEFAULT NULL COMMENT '办结后回写资质主档 cert_no（同事务 + version 乐观锁）',
    `new_valid_to` date DEFAULT NULL COMMENT '办结后回写资质主档 valid_to（延续 RENEW 用）',
    `owner_by` bigint DEFAULT NULL COMMENT '经办人',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（状态机 CAS，表15-7）',
    PRIMARY KEY (`id`),
    KEY `idx_qual_apply_ent` (`tenant_id`, `ent_id`, `status`),
    KEY `idx_qual_apply_qual` (`qual_id`, `apply_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质业务申报流（状态机+乐观锁）';


-- #####################################################################
-- 【来源】2026-09-09-qualification-compliance.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 资质/证书域（qualification_）建表脚本 —— txgy-module-bidding 小模块 · M7 达标四要素核查域
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §十五：表15-2 资质标准库（可版本化）/15-3 人员配置台账/
--      15-4 工程业绩台账（qual_id 归属，§17.6 修订）/15-5 资产与技术装备要素/15-6 达标动态核查（只追加·防抵赖）
--   2) 《招投标模块开发规范》§5 一-4（qualification_ 前缀例外）四-1（审计列）三-7（code/JSON 列存储口径）
-- 说明：本文件仅 M7 五表；15-6 按季度 RANGE 分区生产可另行部署（评审留痕结论可复算，std_version 依据）
-- =====================================================================

-- 表15-2：qualification_standard 资质标准库（核查依据，可版本化）
CREATE TABLE IF NOT EXISTS `qualification_standard` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 std_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `std_version` varchar(32) NOT NULL COMMENT '标准版本（如“建市〔2014〕159号+2021 修订”）',
    `qual_serial` varchar(16) NOT NULL COMMENT '适用的资质序列 code',
    `qual_category` varchar(64) NOT NULL COMMENT '适用的资质类别',
    `qual_grade` varchar(32) NOT NULL COMMENT '适用的资质等级',
    `req_assets` json DEFAULT NULL COMMENT '资产指标：净资产 min 等（JSON）',
    `req_staff` json NOT NULL COMMENT '人员指标：注册建造师/职称/技工/技术负责人条件（JSON）',
    `req_performance` json DEFAULT NULL COMMENT '工程业绩指标（类型/规模/数量/年限，JSON）',
    `req_equipment` json DEFAULT NULL COMMENT '技术装备/检测仪器指标（JSON）',
    `effective_from` date NOT NULL COMMENT '标准生效起始',
    `effective_to` date DEFAULT NULL COMMENT '标准生效截止（NULL=现行）',
    `source_ref` varchar(256) DEFAULT NULL COMMENT '依据文号/国标表110等级/表111类型来源',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_std_scope_version` (`tenant_id`, `qual_serial`, `qual_category`, `qual_grade`, `std_version`, `deleted`),
    KEY `idx_std_effective` (`effective_from`, `effective_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质标准库（可版本化核查依据）';

-- 表15-3：qualification_staff_config 资质人员配置台账
CREATE TABLE IF NOT EXISTS `qualification_staff_config` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 staff_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `qual_id` bigint DEFAULT NULL COMMENT '关联资质（分资质核算人数）',
    `staff_role` varchar(24) NOT NULL COMMENT '角色 code：REGISTERED_BUILDER 注册建造师/TECH_LEAD 技术负责人/MID_TITLE 中级职称/SKILLED_WORKER 技工/SITE_MGR 现场管理人员',
    `person_name` varchar(128) NOT NULL COMMENT '姓名（脱敏展示，保留姓氏）',
    `id_card_enc` varchar(256) DEFAULT NULL COMMENT '身份证加密（L4）',
    `id_card_blind_index` varchar(64) DEFAULT NULL COMMENT '身份证盲索引判重（一人一岗）',
    `major` varchar(128) DEFAULT NULL COMMENT '专业（对口资质专业）',
    `reg_no` varchar(128) DEFAULT NULL COMMENT '注册/执业编号',
    `title_level` varchar(32) DEFAULT NULL COMMENT '职称等级',
    `is_full_time` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否本单位在职（挂靠核查）',
    `social_security_status` varchar(16) DEFAULT NULL COMMENT '社保状态 code',
    `social_security_tenant` varchar(256) DEFAULT NULL COMMENT '社保缴纳单位（须与投标主体一致）',
    `edu_hours` decimal(6,1) DEFAULT NULL COMMENT '继续教育学时',
    `edu_hours_required` decimal(6,1) DEFAULT NULL COMMENT '所需学时（达标要素之一）',
    `reg_valid_to` date DEFAULT NULL COMMENT '注册有效期止（NULL 即不满足）',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联个人证书 certificate_info',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    KEY `idx_qual_staff_ent` (`tenant_id`, `ent_id`, `qual_id`, `staff_role`),
    KEY `idx_qual_staff_reg_valid` (`reg_valid_to`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质人员配置台账';

-- 表15-4：qualification_performance 企业工程业绩台账（§17.6 修订：qual_id 必填归属具体资质）
CREATE TABLE IF NOT EXISTS `qualification_performance` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 perf_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `qual_id` bigint NOT NULL COMMENT '归属资质主档（按资质聚合业绩核查，§17.6）',
    `project_name` varchar(256) NOT NULL COMMENT '业绩项目名称',
    `project_type` varchar(64) DEFAULT NULL COMMENT '工程类型（对口资质类别）',
    `scale_metrics` json DEFAULT NULL COMMENT '规模指标 JSON（面积/跨度/长度/层数/单跨等）',
    `contract_amount` decimal(18,2) DEFAULT NULL COMMENT '合同额',
    `completion_date` date DEFAULT NULL COMMENT '竣工验收时间（业绩年限窗口）',
    `role` varchar(16) DEFAULT NULL COMMENT '角色 code：GENERAL 总承包/SUB 分包',
    `quality_result` varchar(64) DEFAULT NULL COMMENT '质量验收结论',
    `owner_name` varchar(256) DEFAULT NULL COMMENT '业主单位',
    `is_platform_verified` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已录入四库一平台可线上核验',
    `evidence_file_ids` json DEFAULT NULL COMMENT '中标通知/合同/竣工验收等佐证（文件 id 列表）',
    PRIMARY KEY (`id`),
    KEY `idx_qual_perf_qual` (`tenant_id`, `qual_id`, `completion_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业工程业绩台账';

-- 表15-5：qualification_asset 资产与技术装备要素
CREATE TABLE IF NOT EXISTS `qualification_asset` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 asset_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `asset_type` varchar(16) NOT NULL COMMENT '类型 code：NET_ASSET 净资产/PLANT 厂房/MAJOR_EQUIP 主要设备/TEST_INSTR 检测仪器',
    `name` varchar(256) NOT NULL COMMENT '名称',
    `spec` varchar(256) DEFAULT NULL COMMENT '规格型号',
    `quantity` decimal(12,2) DEFAULT NULL COMMENT '数量',
    `value_amount` decimal(18,2) DEFAULT NULL COMMENT '原值/净值（净资产核查取审计值）',
    `stat_year` smallint DEFAULT NULL COMMENT '统计年度（净资产按申报前一年度审计报告）',
    `evidence_file_ids` json DEFAULT NULL COMMENT '审计报告/购置发票/权属证明（文件 id 列表）',
    PRIMARY KEY (`id`),
    KEY `idx_qual_asset_ent` (`tenant_id`, `ent_id`, `asset_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资产与技术装备要素';

-- 表15-6：qualification_compliance_review 达标动态核查（只追加 · 防抵赖）
CREATE TABLE IF NOT EXISTS `qualification_compliance_review` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 review_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '企业',
    `qual_id` bigint DEFAULT NULL COMMENT '被核查资质',
    `std_version` varchar(32) NOT NULL COMMENT '依据的资质标准版本（结论可复算）',
    `review_type` varchar(16) NOT NULL DEFAULT 'SELF' COMMENT '类型 code：SELF 达标自查/DYNAMIC 动态核查/PRE_APPLY 申报前核查',
    `element_snapshot` json NOT NULL COMMENT '资产/人员/业绩/装备四要素时点快照（防事后篡改，类比自查双快照）',
    `review_result` varchar(8) NOT NULL DEFAULT 'PASS' COMMENT '结论 code：PASS/RISK/FAIL 三档',
    `diff_items` json DEFAULT NULL COMMENT '不达标/风险项明细（如“一级建造师少2人”“净资产未达”）',
    `reviewer` bigint DEFAULT NULL COMMENT '核查人',
    `reviewed_at` datetime DEFAULT NULL COMMENT '核查时间（生产按季度 RANGE 分区）',
    `linked_apply_id` bigint DEFAULT NULL COMMENT '若为申报前核查，关联申报流',
    PRIMARY KEY (`id`),
    KEY `idx_qual_review_qual` (`tenant_id`, `qual_id`, `reviewed_at`),
    KEY `idx_qual_review_ent` (`tenant_id`, `ent_id`, `reviewed_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '企业资质达标动态核查（只追加·防抵赖）';


-- #####################################################################
-- 【来源】2026-09-10-qual-expiry-alert.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 资质/证书域增量脚本 —— txgy-module-bidding 小模块 · M8 安许 15-8 + 资质到期并入预警（QUAL_EXPIRY）
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 §十五 表15-8（qualification_safety_permit：permit_no UK、
--      permit_status VALID/EXPIRING_SOON/EXPIRED/SUSPENDED/REVOKED、3 年一延续驱动预警）
--   2) v3.3 §十五-到期预警复用：enterprise_qualification.valid_to / qualification_safety_permit.valid_to /
--      qualification_staff_config.reg_valid_to 一并纳入 cert_alert_config/cert_alert_records 扫描，
--      alert_type 扩展 QUAL_EXPIRY；经 cert_notify_outbox 可靠送达
--   3) 《招投标模块开发规范》§5 一-4（qualification_ 前缀例外已登记）
-- 说明：cert_alert_records.cert_id 语义为 certificate_info.id，资质类业务对象无对应证照 → 增量加
--   ref_type/ref_id/ref_name 可空列（QUAL_EXPIRY 行填 ref_*、cert_id 置 NULL；既有 EXPIRY 行不受影响）。
--   幂等键泛化：资质行用 ref_type:ref_id:yyyyMMdd（uk_alert_idempotent 已全局唯一，无需改动）。
-- =====================================================================

-- 1) cert_alert_records 泛化：cert_id 允许 NULL（资质类对象无 certificate_info 对应），增 ref 定位列
-- [已折叠进基表 CREATE TABLE] 原 ALTER TABLE `cert_alert_records` 语句在此移除（保证包可重复执行）

-- 2) QUAL_EXPIRY 全局预警配置行（平台默认常量同款；cert_type IS NULL=全局，与 EXPIRY 行并存）
INSERT INTO `cert_alert_config`
    (`tenant_id`, `creator`, `updater`, `cert_type`, `alert_type`, `thresholds`, `enabled`, `notify_channels`, `version`, `deleted`)
SELECT 0, '1', '1', NULL, 'QUAL_EXPIRY', '[7,30,60,90]', b'1', '["EMAIL","IN_APP"]', 0, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `cert_alert_config` WHERE `alert_type` = 'QUAL_EXPIRY' AND `cert_type` IS NULL AND `deleted` = b'0');

-- 3) 表15-8：qualification_safety_permit 安全生产许可证
CREATE TABLE IF NOT EXISTS `qualification_safety_permit` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID；=设计 permit_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业（建筑施工企业必备，常与资质联动核查/预警）',
    `permit_no` varchar(128) NOT NULL COMMENT '安全生产许可证编号；UK(tenant, permit_no)',
    `issuing_authority` varchar(256) NOT NULL COMMENT '发证机关（省级住建主管部门）',
    `issue_date` date DEFAULT NULL COMMENT '发证日期',
    `valid_to` date DEFAULT NULL COMMENT '有效期至（3 年一延续；驱动到期预警）',
    `permit_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '状态 code：VALID/EXPIRING_SOON/EXPIRED/SUSPENDED 暂扣/REVOKED',
    `legal_person` varchar(128) DEFAULT NULL COMMENT '法定代表人（脱敏）',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联 certificate_info 影像',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（状态机 CAS）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permit_no` (`tenant_id`, `permit_no`, `deleted`),
    KEY `idx_permit_valid` (`valid_to`),
    KEY `idx_permit_ent_status` (`tenant_id`, `ent_id`, `permit_status`),
    `risk_flag` varchar(16) DEFAULT NULL COMMENT '企业级风险档 code：NORMAL/YELLOW/RED/INVALID/FREQUENT；NULL=未评估（不阻断）',
    `risk_level` varchar(16) DEFAULT NULL COMMENT '档位映射 code：LOW/MEDIUM/HIGH/MAJOR（供周检 Job 直接使用）',
    `risk_evaluated_at` datetime DEFAULT NULL COMMENT '最近风险评估时刻',
    `grace_expire_at` datetime DEFAULT NULL COMMENT '宽限截止（NULL=无宽限）',
    `grace_reason` varchar(256) DEFAULT NULL COMMENT '宽限原因（须在配置白名单内）',
    `grace_source_ref` varchar(128) DEFAULT NULL COMMENT '宽限依据（变更单号/决定书号）',
    `frequent_alert_count` int DEFAULT NULL COMMENT '滑动窗口内预警次数（频繁判定用）',
    `frequent_window_start` datetime DEFAULT NULL COMMENT '频繁统计滑动窗口起点',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '本次评估所用规则版本（可复算）'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安全生产许可证（v3.3 表15-8）';


-- #####################################################################
-- 【来源】2026-09-13-personnel-qualification.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 员工资质（人员资格）域建表脚本 —— txgy-module-bidding 小模块 · M11 员工资质管理
-- 依据：
--   1) 用户业务口径《资质管理规范文件》目录 + 电子资质管理功能范围：
--      企业身份证明/资质证明/实力证明 + 员工自己的身份证明、资质证明，覆盖招投标全流程；
--   2) 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（qualification_staff_config：注册人员台账，
--      在职/社保/注册有效期=挂靠核查）、表 15-6（PASS/RISK/FAIL 三档 + diff_items 明细）、
--      表 1/2（certificate_info/cert_file_versions：证照事实与文件实体，本域经 binded_cert_id/file_id 复用）；
--   3) 《建筑业企业资质管理规定》（住建部令第22号）+《注册建造师管理规定》（建设部令第153号）
--      —— 人员指标须为本企业正式员工、注册单位须与申报企业一致（人证合一，防挂证）；
--   4) 《电子招标投标办法》（20号令）第42条 —— 应公开并提交从业人员职称/职业资格与电子证书编号；
--   5) 《招投标模块开发规范》§5：一（表结构分轨，本域沿用 qualification_ 前缀例外并新增 bidding_personnel_ 前缀）、
--      四-1（审计列由 BaseDO 提供）、五（T 轨继承 TenantBaseDO，自动租户隔离）、六-1（UK 含 deleted 与 tenant_id）。
-- 说明：本文件 5 张新表（员工档案/证书/培训/社保/劳动合同），全部幂等（CREATE TABLE IF NOT EXISTS）；
--       范围口径（用户 2026-09-13 确认）：做到“证照全生命周期管理（中级）”，不做签发；
--       故原设计的“一键配证/人证合一合规核验”判定引擎不在本期落地（要求库/配证结果表暂不建）。
--       证件生命周期（变更/续期/注销吊销/年检）统一落 sql/history/2026-09-14-cert-lifecycle.sql 的 cert_lifecycle_record。
-- 业务目标：把"员工资质"从 Excel 台账升级为可检索、可预警、可核验的电子化人员主数据，
--          并与企业资质（15-1/15-3 人员配置）、社保/劳动合同数据互联互通（关联列在后续配证阶段再增，本期不增）。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 表 1：bidding_personnel_profile 员工档案（人员主数据，一人一档）
-- 依据：v3.3 表 15-3 person_name/id_card_enc/id_card_blind_index/edu_hours 等人员口径的"人"侧主档；
--       证件号等敏感字段 L4 只存密文 + 盲索引（开发规范 §4.8 密级分级）。
-- 目标：集中管理员工基本信息，作为证书、培训、社保、劳动合同的挂载点与"一人一档"判重依据。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_personnel_profile` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业（组织/租户下属主体）',
    `person_no` varchar(64) NOT NULL COMMENT '员工编号（企业内部唯一，UK 组成）',
    `person_name` varchar(128) NOT NULL COMMENT '姓名（L3 脱敏展示，保留姓氏）',
    `id_card_enc` varchar(256) DEFAULT NULL COMMENT '身份证号密文（L4，AES-256-GCM + KMS，业务侧不落明文）',
    `id_card_blind_index` varchar(64) DEFAULT NULL COMMENT '身份证盲索引（HMAC，用于一人一档等值判重，不解密）',
    `id_card_valid_to` date DEFAULT NULL COMMENT '身份证有效期止（驱动身份证到期预警，用户明确要求项）',
    `gender` varchar(8) DEFAULT NULL COMMENT '性别 code：MALE/FEMALE/UNKNOWN',
    `birth_date` date DEFAULT NULL COMMENT '出生日期',
    `mobile` varchar(32) DEFAULT NULL COMMENT '手机（L3，日志/出参脱敏）',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱（L3）',
    `edu_level` varchar(16) DEFAULT NULL COMMENT '学历层次 code：DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER（PersonnelEduLevelEnum）',
    `edu_major` varchar(128) DEFAULT NULL COMMENT '所学专业（人岗匹配度分析用）',
    `school` varchar(256) DEFAULT NULL COMMENT '毕业院校',
    `graduate_date` date DEFAULT NULL COMMENT '毕业时间',
    `title_level` varchar(32) DEFAULT NULL COMMENT '职称等级 code（初级/中级/高级/正高级，与 15-3 title_level 同口径）',
    `title_major` varchar(128) DEFAULT NULL COMMENT '职称专业',
    `work_start_date` date DEFAULT NULL COMMENT '参加工作时间（工龄/工作年限计算基准）',
    `join_date` date DEFAULT NULL COMMENT '入职日期',
    `dept_name` varchar(128) DEFAULT NULL COMMENT '所属部门',
    `position_name` varchar(128) DEFAULT NULL COMMENT '岗位名称',
    `employment_type` varchar(24) NOT NULL DEFAULT 'FULL_TIME' COMMENT '用工形式 code：FULL_TIME/PART_TIME/LABOR_DISPATCH/RETIRED_REHIRE/OTHER（挂靠风险提示）',
    `is_full_time` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否本单位在职（与 15-3 is_full_time 同口径：仅 FULL_TIME 为真）',
    `personnel_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '在职状态 code：ACTIVE/LEAVE/SUSPENDED/RETIRED（仅 ACTIVE 可配证）',
    `status_change_date` date DEFAULT NULL COMMENT '最近状态变更日期（离职/复职留痕）',
    `binded_auth_user_id` bigint DEFAULT NULL COMMENT '关联平台登录用户（员工可自助维护本人证书时使用；无账号为空）',
    `archive_no` varchar(128) DEFAULT NULL COMMENT '人事档案号',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（开发规范 §4.5）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_person_no` (`tenant_id`, `ent_id`, `person_no`, `deleted`),
    UNIQUE KEY `uk_profile_id_card` (`tenant_id`, `id_card_blind_index`, `deleted`),
    KEY `idx_profile_ent_status` (`tenant_id`, `ent_id`, `personnel_status`),
    KEY `idx_profile_id_card_valid` (`id_card_valid_to`),
    `person_risk_flag` varchar(16) DEFAULT NULL COMMENT '人员级标签 code：NORMAL/ABNORMAL/FREQUENT',
    `job_change_count12m` int DEFAULT NULL COMMENT '12 个月内更换受聘企业次数（列名与 MyBatis-Plus 默认驼峰映射一致，勿加下划线）',
    `job_change_window_start` date DEFAULT NULL COMMENT '统计窗口起点',
    `person_risk_evaluated_at` datetime DEFAULT NULL COMMENT '最近人员风险评估时刻'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工档案（人员主数据，一人一档）';

-- ---------------------------------------------------------------------
-- 表 2：personnel_certificate 员工证书台账
-- 依据：用户要求"身份证、学历证、职称证等证明文件数字化管理"；v3.3 §一（PERSON 类证书走人员扩展）、
--       表 15-3（注册/执业编号、注册有效期）；《注册建造师管理规定》——注册单位须与聘用企业一致（防挂证）。
-- 目标：证书集中管理与分类检索；有效期驱动预警；register_unit 支撑人证合一核验。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_certificate` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `person_id` bigint NOT NULL COMMENT '持证人（bidding_personnel_profile.id）',
    `cert_type` varchar(24) NOT NULL COMMENT '证书类型 code：ID_CARD/EDUCATION_CERT/DEGREE_CERT/TITLE_CERT/REG_PRACTICE_CERT/SAFETY_ASSESS_CERT/SKILL_CERT/TRAINING_CERT/OTHER',
    `cert_name` varchar(256) NOT NULL COMMENT '证书名称（照面全称）',
    `cert_no` varchar(128) NOT NULL COMMENT '证书编号（电子证书编号，20号令第42条要求可公开）',
    `cert_level` varchar(64) DEFAULT NULL COMMENT '等级（一级/二级，或安管人员 A/B/C 证）',
    `cert_sub_type` varchar(16) DEFAULT NULL COMMENT '证书细分 code：安管人员 A/B/C（C 可再分 C1 机械/C2 土建/C3 综合）、特种设备作业 G1/G2/G3、RT/UT 等级等（06 文档 §二-3；码表 bidding_ 前缀）',
    `major` varchar(128) DEFAULT NULL COMMENT '专业（与资质要求专业比对，人岗匹配）',
    `next_review_date` date DEFAULT NULL COMMENT '下次复审/年审日期（与有效期独立的时间维度：司炉证届满前 1 个月复审、ISO 监督审核 ≤12 个月一次；06 文档 §二-4）',
    `edu_level` varchar(16) DEFAULT NULL COMMENT '学历层次 code（学历/学位证书用）',
    `issuing_authority` varchar(256) DEFAULT NULL COMMENT '发证机关',
    `issue_date` date DEFAULT NULL COMMENT '发证日期',
    `valid_from` date DEFAULT NULL COMMENT '有效期起始',
    `valid_to` date DEFAULT NULL COMMENT '有效期止（NULL=长期；驱动到期预警扫描）',
    `register_unit` varchar(256) DEFAULT NULL COMMENT '注册/执业单位名称（防挂证核验：须与申报企业一致）',
    `register_unit_code` varchar(64) DEFAULT NULL COMMENT '注册单位统一社会信用代码（GB 32100-2015，一致性核验键）',
    `register_no` varchar(128) DEFAULT NULL COMMENT '注册号/执业印章号（与 15-3 reg_no 同口径）',
    `cert_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '台账状态 code：VALID/EXPIRING_SOON/EXPIRED/SUSPENDED 暂扣/REVOKED/CANCELLED（对齐建办市〔2024〕51号 ZZZT 口径）',
    `platform_verified` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已在四库一平台线上核验（jzsc.mohurd.gov.cn）',
    `verify_url` varchar(512) DEFAULT NULL COMMENT '官方核验链接（照面二维码/查询网址）',
    `verified_at` datetime DEFAULT NULL COMMENT '最近核验时间',
    `binded_cert_id` bigint DEFAULT NULL COMMENT '关联 certificate_info（复用通用台账的影像/验真/变更日志能力）',
    `file_id` bigint DEFAULT NULL COMMENT '证书扫描件文件 id（经 FileApi 上传，业务表只存 id，开发规范 §5 三-5）',
    `is_current` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否当前有效记录（换证后旧证置 0，历史留痕）',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/OCR/API_SYNC',
    `extra_props` json DEFAULT NULL COMMENT '动态扩展（如继续教育学时、批准专业明细）',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pcert_no` (`tenant_id`, `person_id`, `cert_type`, `cert_no`, `deleted`),
    KEY `idx_pcert_person` (`tenant_id`, `person_id`, `cert_type`, `is_current`),
    KEY `idx_pcert_ent_type` (`tenant_id`, `ent_id`, `cert_type`, `cert_status`),
    KEY `idx_pcert_valid` (`cert_status`, `valid_to`),
    KEY `idx_pcert_review` (`cert_status`, `next_review_date`),
    KEY `idx_pcert_sub_type` (`tenant_id`, `ent_id`, `cert_type`, `cert_sub_type`, `cert_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工证书台账（身份/学历/职称/执业/安全/技能类）';

-- ---------------------------------------------------------------------
-- 表 3：personnel_training_record 员工培训记录
-- 依据：用户要求"记录员工基本信息、资质证书、培训记录等"；v3.3 表 15-3 edu_hours/edu_hours_required（继续教育学时）。
-- 目标：培训与继续教育学时留痕，作为资质人员指标与安全生产教育的佐证材料。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_training_record` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '归属企业',
    `person_id` bigint NOT NULL COMMENT '参训员工',
    `training_name` varchar(256) NOT NULL COMMENT '培训名称',
    `training_type` varchar(32) NOT NULL DEFAULT 'OTHER' COMMENT '培训类型 code：SAFETY 安全培训/CONTINUE_EDU 继续教育/SKILL 技能/PRE_JOB 岗前/OTHER',
    `organizer` varchar(256) DEFAULT NULL COMMENT '培训机构/组织方',
    `start_date` date DEFAULT NULL COMMENT '开始日期',
    `end_date` date DEFAULT NULL COMMENT '结束日期',
    `edu_hours` decimal(6,1) DEFAULT NULL COMMENT '学时（继续教育达标要素）',
    `result` varchar(32) DEFAULT NULL COMMENT '结果 code：QUALIFIED 合格/UNQUALIFIED 不合格/ATTENDING 在读',
    `score` decimal(5,2) DEFAULT NULL COMMENT '考核成绩',
    `cert_no` varchar(128) DEFAULT NULL COMMENT '培训合格证编号',
    `cert_valid_to` date DEFAULT NULL COMMENT '培训合格证有效期止（可并入到期预警）',
    `file_id` bigint DEFAULT NULL COMMENT '培训证明文件 id',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    KEY `idx_ptrain_person` (`tenant_id`, `person_id`, `training_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工培训记录';

-- ---------------------------------------------------------------------
-- 表 4：personnel_social_security 员工社保缴纳记录
-- 依据：用户要求"核验员工社保缴纳单位、劳动合同主体是否与申报企业完全一致，杜绝人证分离和挂证"；
--       v3.3 表 15-3 social_security_status/social_security_tenant。
-- 目标：以"缴纳单位主体 + 缴纳月份"为证据链，判定申报/投标主体一致性。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_social_security` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '缴纳单位（本企业主体 id，一致性核验锚点）',
    `person_id` bigint NOT NULL COMMENT '参保员工',
    `payer_name` varchar(256) DEFAULT NULL COMMENT '缴纳单位名称（照抄社保明细，供人工比对）',
    `payer_credit_code` varchar(64) DEFAULT NULL COMMENT '缴纳单位统一社会信用代码（不一致即判人证分离）',
    `pay_month` varchar(7) NOT NULL COMMENT '缴纳月份（yyyy-MM）',
    `pay_status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '缴纳状态 code：NORMAL/SUSPENDED/TRANSFERRED/NONE',
    `pay_base` decimal(18,2) DEFAULT NULL COMMENT '缴费基数',
    `pay_amount` decimal(18,2) DEFAULT NULL COMMENT '缴费金额',
    `social_no` varchar(64) DEFAULT NULL COMMENT '社保个人编号',
    `source_channel` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源 code：MANUAL/IMPORT/API_SYNC',
    `evidence_file_id` bigint DEFAULT NULL COMMENT '参保证明文件 id（社保缴纳证明）',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pss_month` (`tenant_id`, `person_id`, `payer_credit_code`, `pay_month`, `deleted`),
    KEY `idx_pss_person` (`tenant_id`, `person_id`, `pay_month`),
    KEY `idx_pss_payer` (`tenant_id`, `payer_credit_code`, `pay_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工社保缴纳记录（主体一致性核验证据）';

-- ---------------------------------------------------------------------
-- 表 5：personnel_labor_contract 劳动合同
-- 依据：用户要求核验"劳动合同主体是否与申报企业完全一致"；《劳动合同法》第十条（书面劳动合同）。
-- 目标：与社保共同构成劳动关系双证据；合同到期亦进入提醒范围（人员到期提醒的组成部分）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_labor_contract` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `ent_id` bigint NOT NULL COMMENT '用人单位主体 id（一致性核验锚点）',
    `person_id` bigint NOT NULL COMMENT '员工',
    `contract_no` varchar(128) NOT NULL COMMENT '劳动合同编号',
    `contract_type` varchar(24) DEFAULT NULL COMMENT '合同类型 code：FIXED 固定期限/OPEN 无固定期限/COMPLETE 完成一定工作',
    `sign_date` date DEFAULT NULL COMMENT '签订日期',
    `start_date` date NOT NULL COMMENT '合同起始日',
    `end_date` date DEFAULT NULL COMMENT '合同终止日（NULL=无固定期限）',
    `contract_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '合同状态 code：ACTIVE/EXPIRED/TERMINATED',
    `position_name` varchar(128) DEFAULT NULL COMMENT '合同岗位',
    `file_id` bigint DEFAULT NULL COMMENT '合同扫描件文件 id',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plc_no` (`tenant_id`, `person_id`, `contract_no`, `deleted`),
    KEY `idx_plc_person` (`tenant_id`, `person_id`, `contract_status`, `end_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工劳动合同（劳动关系证据）';


-- #####################################################################
-- 【来源】2026-09-14-cert-lifecycle.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 证件/资质生命周期记录建表脚本 —— txgy-module-bidding 小模块 · M12 全生命周期（变更/续期/注销吊销/年检）
-- 依据：
--   1) 《电子证件全流程管理_数据库设计.md》v3.3 表 3（cert_review_record 资质审查，review_type 含 ANNUAL 年检）、
--      表 5（cert_renewal_record 续期/补换证：old/new 有效期、新文件版本、办结回写）、
--      表 6（cert_change_record 变更：change_group_id 归组、field_name/old_value/new_value 字段级留痕、status 流转）、
--      表 7（cert_revoke_record 吊销/注销/暂扣/恢复）；
--   2) 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理（中级）"——签发、变更、注销、年检、到期提醒、
--      版本管理，**不做签发**；
--   3) 《招投标模块开发规范》§5 四-1（审计列由 BaseDO 提供）、五（T 轨 TenantBaseDO 自动租户隔离）、
--      六-1（UK 含 deleted 与 tenant_id）、§4.5（状态机 + 乐观锁 CAS）。
-- 设计说明（与 v3.3 四张专表的差异，登记在 docs/模块设计与规范/员工资质与证件生命周期-设计说明.md）：
--   v3.3 把生命周期拆成 cert_review_record / cert_renewal_record / cert_change_record / cert_revoke_record 四表，
--   但四表约八成字段同构（业务对象 + 申请人/时间 + 状态 + 结论 + 新文件 + 备注），且本模块还要同时承载
--   "通用证照（certificate_info）"与"员工证书（personnel_certificate）"两个域的生命周期；
--   若按四表 × 两域展开即为 8 张近乎同构的表与 8 套 Mapper/Service。
--   故本模块归并为**一张 cert_lifecycle_record**，用 biz_domain 区分业务对象、lifecycle_type 区分四类流程，
--   与既有 cert_alert_records 的 ref_type/ref_id 泛化范式保持一致（sql/history/2026-09-10-qual-expiry-alert.sql 先例）。
--   如后续评审要求拆表，按 lifecycle_type 过滤即可无损拆分（字段已按四类分组排列）。
-- 业务目标：每一次证件/证书的变更、续期、注销吊销、年检都留痕可追溯，并与主档同事务回写，
--          替代"只在 Excel 改个数字、事后说不清谁改的"的台账模式。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `cert_lifecycle_record` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    -- 业务对象定位（泛化：一表服务于证照台账/员工证书/企业资质/安许证四类对象）
    `biz_domain` varchar(24) NOT NULL COMMENT '业务对象域 code：CERT 证照台账/PERSON_CERT 员工证书/ENTERPRISE_QUAL 企业资质/SAFETY_PERMIT 安许证',
    `biz_id` bigint NOT NULL COMMENT '业务对象主键（certificate_info.id / personnel_certificate.id / enterprise_qualification.id / qualification_safety_permit.id）',
    `ref_name` varchar(256) DEFAULT NULL COMMENT '业务对象名称快照（防主档改名后无法复算）',
    `ref_no` varchar(128) DEFAULT NULL COMMENT '证书编号快照',
    `ent_id` bigint DEFAULT NULL COMMENT '归属企业（便于按企业聚合生命周期台账）',
    -- 流程定位
    `lifecycle_type` varchar(16) NOT NULL COMMENT '生命周期类型 code：CHANGE 变更/RENEW 续期/REVOKE 注销吊销/ANNUAL 年检',
    `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态 code：PENDING 待办/REVIEWING 审核中/APPROVED 通过/REJECTED 驳回/EFFECTIVE 已生效',
    `apply_no` varchar(64) DEFAULT NULL COMMENT '申请单号（对外可引用；同类型同日同对象唯一）',
    -- 变更（v3.3 表 6）：一行一字段，同一次申请多字段共用 change_group_id
    `change_group_id` varchar(64) DEFAULT NULL COMMENT '变更归组号（同一次变更申请涉及多字段时共用一组）',
    `field_name` varchar(64) DEFAULT NULL COMMENT '变更字段名（台账列名，如 cert_status/valid_to/cert_level）',
    `old_value` text COMMENT '变更前值',
    `new_value` text COMMENT '变更后值（生效时同步主档）',
    `change_summary` varchar(512) DEFAULT NULL COMMENT '变更摘要',
    -- 续期（v3.3 表 5）
    `renewal_type` varchar(16) DEFAULT NULL COMMENT '续期类型 code：RENEW 延续/REISSUE 补换证（补换证可能产生新编号）',
    `old_valid_from` date DEFAULT NULL COMMENT '续期前有效期起始（快照）',
    `old_valid_to` date DEFAULT NULL COMMENT '续期前有效期止（快照）',
    `new_valid_from` date DEFAULT NULL COMMENT '续期后有效期起始（生效回写主档）',
    `new_valid_to` date DEFAULT NULL COMMENT '续期后有效期止（生效回写主档；驱动下一轮到期预警）',
    `new_next_review_date` date DEFAULT NULL COMMENT '下次年检日（生效回写主档 next_review_date）',
    `new_cert_no` varchar(128) DEFAULT NULL COMMENT '新证书编号（补换证产生新编号时填写）',
    `result_no` varchar(128) DEFAULT NULL COMMENT '批复/许可决定文号',
    -- 注销/吊销（v3.3 表 7）
    `revoke_type` varchar(16) DEFAULT NULL COMMENT '注销吊销类型 code：REVOKE 吊销/CANCEL 注销/SUSPEND 暂扣/RESTORE 恢复',
    `revoke_authority` varchar(256) DEFAULT NULL COMMENT '决定机关',
    `revoke_reason` varchar(512) DEFAULT NULL COMMENT '决定事由',
    `revoke_effective_at` date DEFAULT NULL COMMENT '决定生效日期',
    -- 年检（v3.3 表 3 ANNUAL）
    `review_year` smallint DEFAULT NULL COMMENT '年检年度（如 2026）',
    `review_date` date DEFAULT NULL COMMENT '年检日期',
    `review_result` tinyint DEFAULT NULL COMMENT '年检/审查结论：NULL 未出结论 / 0 不通过 / 1 通过',
    -- 附件与过程留痕
    `new_cert_file_id` bigint DEFAULT NULL COMMENT '新文件 id（变更页/延续页/新证扫描件，经 FileApi 上传后登记）',
    `evidence_file_ids` json DEFAULT NULL COMMENT '佐证材料文件 id 列表（决定书/年检材料/变更证明）',
    `applied_by` bigint DEFAULT NULL COMMENT '申请人（用户 id）',
    `applied_at` datetime DEFAULT NULL COMMENT '申请时间',
    `handled_by` bigint DEFAULT NULL COMMENT '办理人（审批/登记人）',
    `handled_at` datetime DEFAULT NULL COMMENT '办理时间',
    `effective_at` datetime DEFAULT NULL COMMENT '生效时间（回写主档的时间，审计用）',
    `idempotent_key` varchar(160) DEFAULT NULL COMMENT '幂等键（biz_domain:biz_id:lifecycle_type:apply_no），防重复提交',
    `remark` text COMMENT '备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（状态机 CAS）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lifecycle_idem` (`tenant_id`, `idempotent_key`, `deleted`),
    KEY `idx_lifecycle_biz` (`tenant_id`, `biz_domain`, `biz_id`, `lifecycle_type`, `status`),
    KEY `idx_lifecycle_status` (`tenant_id`, `lifecycle_type`, `status`, `applied_at`),
    KEY `idx_lifecycle_group` (`tenant_id`, `change_group_id`),
    KEY `idx_lifecycle_review` (`tenant_id`, `review_year`, `review_result`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证件/资质生命周期记录（变更/续期/注销吊销/年检，只追加）';


-- #####################################################################
-- 【来源】2026-09-16-rule-config-center.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
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


-- #####################################################################
-- 【来源】2026-09-17-safety-permit-risk.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 安许 A/B/C 三类人员联动与 risk_flag（方案 §1 全量落地）
-- 依据：
--   1) 用户业务口径：A/B/C 任一不达标→黄；任一为 0→红；全为 0→失效；一年预警 ≥4 次→频繁；
--      法定代表人变更致仅剩 1 名 A 证给 180 天宽限；劳务分包企业 B 证为 0 不触发红色；
--      企业级 ent_risk_flag 与人员级 person_risk_flag 分离；人员 12 个月内更换受聘企业 ≥4 次打异常标签；
--      同一项目关键岗位人员频繁变更监控；兼任 ≥2 岗须持另一岗位考核合格证；
--      招投标/施工许可/现场监督检查读取同一风险状态。
--   2) 《资质管理/06-电子证照设计材料对比与推进依据-2026-09-10.md》§二-1/§二-2（陕西 2026-03-26 起
--      A/B/C 动态核查、黄色/红色/失效预警、3 个月不整改列失效）、§二-3（C1/C2/C3、G1/G2/G3 细分）、
--      §二-4（复审独立时间维度）；
--   3) 《建筑业企业资质管理规定》（住建部令第22号）+《安全生产许可证条例》（国务院令第653号修正）；
--   4) 《电子资质七大模块技术方案.md》§1.1~§1.5（表结构/判定顺序/Job/接口/拦截点/迁移回滚）；
--   5) 《招投标模块开发规范》§4.5（乐观锁）、§5 六-1（UK 含 tenant_id 与 deleted）、§4.8（L4 只回掩码）。
-- 数值参数：全部走 bidding_rule_config（见文末 §5 种子），代码禁止硬编码。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE；ALTER 部分为一次性增量（重复执行会报 Duplicate column，
--       可忽略或先按 information_schema 核查）。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 安许证主档扩展：企业级风险（与人员级 person_risk_flag 分离）
-- 业务目标：招投标、施工许可、现场监督检查读同一份风险状态，避免各页面各自算、口径不一。
-- ---------------------------------------------------------------------
-- [已折叠进基表 CREATE TABLE] 原 ALTER TABLE `qualification_safety_permit` 语句在此移除（保证包可重复执行）

-- ---------------------------------------------------------------------
-- 2) 员工档案扩展：人员级风险（不与企业级混用）
-- 业务目标：企业级回答"这家企业能不能投标"，人员级回答"这名人员能不能上岗"，两种处置互不覆盖。
-- ---------------------------------------------------------------------
-- [已折叠进基表 CREATE TABLE] 原 ALTER TABLE `bidding_personnel_profile` 语句在此移除（保证包可重复执行）

-- ---------------------------------------------------------------------
-- 3) 风险快照（只追加 + 哈希链）：方案 §1.1(c)
-- 业务目标：每次评估可复算（hit_rules_json + rule_version），且记录不可篡改（等保三级 ≥3 年）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `safety_permit_risk_snapshot` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（SYSTEM=定时任务）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（本表只追加，恒为 0）',
    `permit_id` bigint NOT NULL COMMENT '安许证主键',
    `ent_id` bigint DEFAULT NULL COMMENT '企业主体',
    `risk_flag` varchar(16) NOT NULL COMMENT '本次风险档',
    `prev_risk_flag` varchar(16) DEFAULT NULL COMMENT '上次风险档（识别升降级）',
    `risk_level` varchar(16) NOT NULL COMMENT '档位映射结果',
    `a_count` int NOT NULL DEFAULT 0 COMMENT 'A 类有效人数',
    `b_count` int NOT NULL DEFAULT 0 COMMENT 'B 类有效人数',
    `c_count` int NOT NULL DEFAULT 0 COMMENT 'C 类有效人数',
    `min_required_json` json DEFAULT NULL COMMENT '本次判定所用配备要求（留证）',
    `grace_applied` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否处于宽限期',
    `hit_rules_json` json DEFAULT NULL COMMENT '命中的规则键与取值（可复算）',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '规则版本',
    `batch_no` varchar(48) DEFAULT NULL COMMENT '评估批次号',
    `evaluated_at` datetime NOT NULL COMMENT '评估时刻',
    `record_hash` varchar(64) DEFAULT NULL COMMENT '本记录哈希（SHA-256）',
    `prev_hash` varchar(64) DEFAULT NULL COMMENT '上一条哈希（哈希链）',
    PRIMARY KEY (`id`),
    KEY `idx_risk_permit` (`permit_id`, `evaluated_at`),
    KEY `idx_risk_ent` (`tenant_id`, `ent_id`, `evaluated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安许风险快照（只追加 + 哈希链）';

-- ---------------------------------------------------------------------
-- 4) 人员受聘企业变更流水：方案 §1.1(d)
-- 业务目标：12 个月内换企业 ≥N 次 → 打"一年异常"标签，识别挂证/职业化挂靠。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_job_change_log` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `person_id` bigint NOT NULL COMMENT '员工档案主键',
    `from_ent_id` bigint DEFAULT NULL COMMENT '原受聘企业（首次入职为空）',
    `to_ent_id` bigint DEFAULT NULL COMMENT '新受聘企业',
    `change_type` varchar(16) NOT NULL DEFAULT 'TRANSFER' COMMENT '类型 code：TRANSFER 变更单位/RESIGN 离职/REHIRE 复职',
    `effective_date` date NOT NULL COMMENT '生效日期',
    `source_ref` varchar(128) DEFAULT NULL COMMENT '来源单据号（劳动合同/社保变更单）',
    `remark` varchar(256) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_job_change_person` (`person_id`, `effective_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '人员受聘企业变更流水';

-- ---------------------------------------------------------------------
-- 5) 项目关键岗位任职流水：方案 §1.1(e)
-- 业务目标：同一项目同一关键岗位频繁换人 = 挂靠或管理失控信号，需可统计、可预警。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `project_key_post_assignment` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `project_id` bigint NOT NULL COMMENT '项目主键 bidding_project.id',
    `tender_project_code` varchar(64) DEFAULT NULL COMMENT '招标项目编号（追溯）',
    `post_code` varchar(32) NOT NULL COMMENT '关键岗位 code：PROJECT_MANAGER/TECH_LEADER/SAFETY_OFFICER/QUALITY_OFFICER 等',
    `person_id` bigint NOT NULL COMMENT '任职人员',
    `start_at` date NOT NULL COMMENT '任职开始',
    `end_at` date DEFAULT NULL COMMENT '任职结束（NULL=在任）',
    `change_seq` int NOT NULL DEFAULT 1 COMMENT '该岗位第几次变更（从 1 起）',
    `change_reason` varchar(256) DEFAULT NULL COMMENT '变更原因',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_seq` (`tenant_id`, `project_id`, `post_code`, `change_seq`, `deleted`),
    KEY `idx_post_project` (`project_id`, `post_code`, `start_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目关键岗位任职流水';

-- ---------------------------------------------------------------------
-- 6) 多岗合规校验结论（只追加）：方案 §1.1(f)
-- 业务目标：兼任两个及以上岗位的，必须持另一岗位的考核合格证书，校验结论留痕可追溯。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `personnel_multi_post_check` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（本表只追加）',
    `person_id` bigint NOT NULL COMMENT '员工档案主键',
    `primary_post` varchar(32) NOT NULL COMMENT '主岗位 code',
    `secondary_post` varchar(32) NOT NULL COMMENT '兼任岗位 code',
    `required_cert_type` varchar(32) DEFAULT NULL COMMENT '兼任岗位要求的证书类型 code',
    `required_cert_sub_type` varchar(16) DEFAULT NULL COMMENT '要求的证书细分（如 A/B/C）',
    `matched_cert_id` bigint DEFAULT NULL COMMENT '命中的员工证书主键（校验通过时）',
    `check_result` varchar(16) NOT NULL COMMENT '结论 code：PASS/FAIL',
    `fail_reason` varchar(256) DEFAULT NULL COMMENT '不通过原因',
    `checked_at` datetime NOT NULL COMMENT '校验时刻',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '规则版本',
    PRIMARY KEY (`id`),
    KEY `idx_multi_post_person` (`person_id`, `checked_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '多岗合规校验结论（只追加）';


-- #####################################################################
-- 【来源】2026-09-19-cert-dict-register.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 电子证照域字典类型注册与种子（补齐契约 v1.0 §1.1 与 v1.1 §12.0 的字典缺口）
-- =====================================================================
-- 依据：用户核查结论第 3 条——cert_type / holder_type / material_* / review_result 以及
--       安许风险联动的 safety_risk_flag / person_risk_flag / risk_level **未注册且无种子**，
--       前端只能降级显示 code（拿不到中文名、标签色、终态标记）；
--       用户硬约束"状态码/枚举值必须支持动态配置，严禁硬编码"。
-- 取值来源（不臆造）：
--   cert_type        ← EnterpriseMaterialCategoryEnum.resolveByCertType（11 个分支值）
--   material_type    ← EnterpriseMaterialTypeEnum 常量（7 个：code/中文名/对应 cert_type）
--   material_category← EnterpriseMaterialCategoryEnum（IDENTITY/QUALIFICATION/STRENGTH）
--   holder_type      ← CertificateDO.holderType 的 @Schema 口径（ORG/PERSON/EXTERNAL）
--   review_result    ← 年检结论字段口径（1 通过 / 0 不通过）
--   safety_risk_flag / person_risk_flag / risk_level ← 本次新增枚举类（SafetyRiskFlagEnum /
--                      PersonRiskFlagEnum / RiskLevelEnum）
-- 设计说明：
--   1) id 块预留 7100000000000020xx ~ 7100000000000029xx —— v1.0 的 68 条种子占用 001~134，
--      本文件不复用其 id，避免 INSERT IGNORE 静默丢行；
--   2) color_type 即契约 §1.1 的 tagType，terminal 供前端禁用流转按钮；
--   3) 幂等：INSERT IGNORE，可重复执行。
-- 执行库：ruoyi-vue-pro（与 2026-09-16-rule-config-center.sql 同库；bidding_dict_data 由该脚本建表）。
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- cert_type（11 条）
(710000000000002001,0,'cert_type','ADMIN_LICENSE','营业执照','success',b'0',1,b'1','企业身份证明；材料分类=身份','1',NOW(),'1',NOW(),b'0'),
(710000000000002002,0,'cert_type','LEGAL_PERSON_ID','法定代表人身份证明','success',b'0',2,b'1','材料分类=身份','1',NOW(),'1',NOW(),b'0'),
(710000000000002003,0,'cert_type','AUTH_LETTER','法人授权委托书','success',b'0',3,b'1','员工代表企业投标的效力凭证；材料分类=身份','1',NOW(),'1',NOW(),b'0'),
(710000000000002004,0,'cert_type','SYSTEM_CERT','体系认证证书','info',b'0',4,b'1','材料分类=资质','1',NOW(),'1',NOW(),b'0'),
(710000000000002005,0,'cert_type','SAFETY_PERMIT','安全生产许可证','info',b'0',5,b'1','材料分类=资质','1',NOW(),'1',NOW(),b'0'),
(710000000000002006,0,'cert_type','PERSON_QUAL','人员资格证书','info',b'0',6,b'1','材料分类=资质；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
(710000000000002007,0,'cert_type','AUDIT_REPORT','财务审计报告','default',b'0',7,b'1','材料分类=实力','1',NOW(),'1',NOW(),b'0'),
(710000000000002008,0,'cert_type','PERFORMANCE','业绩证明','default',b'0',8,b'1','材料分类=实力；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
(710000000000002009,0,'cert_type','SOCIAL_SECURITY','社保证明','default',b'0',9,b'1','材料分类=实力','1',NOW(),'1',NOW(),b'0'),
(710000000000002010,0,'cert_type','HONOR','荣誉证书','default',b'0',10,b'1','材料分类=实力；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
(710000000000002011,0,'cert_type','TAX_CREDIT','纳税信用证明','default',b'0',11,b'1','材料分类=实力；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
-- holder_type（3 条）
(710000000000002101,0,'holder_type','ORG','单位','info',b'0',1,b'1','持证主体为企业/机构','1',NOW(),'1',NOW(),b'0'),
(710000000000002102,0,'holder_type','PERSON','个人','info',b'0',2,b'1','持证主体为自然人','1',NOW(),'1',NOW(),b'0'),
(710000000000002103,0,'holder_type','EXTERNAL','外部机构','default',b'0',3,b'1','非本企业主体的外部持证方','1',NOW(),'1',NOW(),b'0'),
-- review_result（2 条）
(710000000000002201,0,'review_result','1','通过','success',b'0',1,b'1','年检结论取值以字符串提交（后端字段为 Integer）','1',NOW(),'1',NOW(),b'0'),
(710000000000002202,0,'review_result','0','不通过','error',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
-- material_category（3 条）
(710000000000002301,0,'material_category','IDENTITY','身份证明','info',b'0',1,b'1','对应 EnterpriseMaterialCategoryEnum.IDENTITY','1',NOW(),'1',NOW(),b'0'),
(710000000000002302,0,'material_category','QUALIFICATION','资质证明','info',b'0',2,b'1','对应 QUALIFICATION','1',NOW(),'1',NOW(),b'0'),
(710000000000002303,0,'material_category','STRENGTH','实力证明','info',b'0',3,b'1','对应 STRENGTH','1',NOW(),'1',NOW(),b'0'),
-- material_type（7 条）
(710000000000002401,0,'material_type','BUSINESS_LICENSE','营业执照','default',b'0',1,b'1','cert_type=ADMIN_LICENSE','1',NOW(),'1',NOW(),b'0'),
(710000000000002402,0,'material_type','LEGAL_PERSON_ID','法定代表人身份证明','default',b'0',2,b'1','cert_type=LEGAL_PERSON_ID','1',NOW(),'1',NOW(),b'0'),
(710000000000002403,0,'material_type','AUTH_LETTER','法人授权委托书','default',b'0',3,b'1','cert_type=AUTH_LETTER','1',NOW(),'1',NOW(),b'0'),
(710000000000002404,0,'material_type','QUAL_CERT','建筑业企业资质证书','default',b'0',4,b'1','cert_type=SYSTEM_CERT','1',NOW(),'1',NOW(),b'0'),
(710000000000002405,0,'material_type','SAFETY_PERMIT','安全生产许可证','default',b'0',5,b'1','cert_type=SAFETY_PERMIT','1',NOW(),'1',NOW(),b'0'),
(710000000000002406,0,'material_type','AUDIT_REPORT','财务审计报告','default',b'0',6,b'1','cert_type=AUDIT_REPORT','1',NOW(),'1',NOW(),b'0'),
(710000000000002407,0,'material_type','SOCIAL_SECURITY','社保证明','default',b'0',7,b'1','cert_type=SOCIAL_SECURITY','1',NOW(),'1',NOW(),b'0'),
-- safety_risk_flag（5 条）
(710000000000002501,0,'safety_risk_flag','NORMAL','正常','success',b'0',1,b'1','三类人员配备达标','1',NOW(),'1',NOW(),b'0'),
(710000000000002502,0,'safety_risk_flag','YELLOW','黄色预警','warning',b'0',2,b'1','任一类低于配备要求（宽限期内由后端降档）','1',NOW(),'1',NOW(),b'0'),
(710000000000002503,0,'safety_risk_flag','RED','红色预警','error',b'0',3,b'1','任一类为 0（劳务分包豁免类型除外）','1',NOW(),'1',NOW(),b'0'),
(710000000000002504,0,'safety_risk_flag','INVALID','失效','error',b'1',4,b'1','三类全为 0 且逾期不整改；终态','1',NOW(),'1',NOW(),b'0'),
(710000000000002505,0,'safety_risk_flag','FREQUENT','频繁预警','warning',b'0',5,b'1','窗口内预警次数达阈值（趋势标签，不阻断）','1',NOW(),'1',NOW(),b'0'),
-- person_risk_flag（3 条）
(710000000000002601,0,'person_risk_flag','NORMAL','正常','success',b'0',1,b'1','窗口内更换受聘企业次数未达阈值','1',NOW(),'1',NOW(),b'0'),
(710000000000002602,0,'person_risk_flag','ABNORMAL','一年异常','error',b'0',2,b'1','12 个月内更换受聘企业次数达阈值（挂证嫌疑）','1',NOW(),'1',NOW(),b'0'),
(710000000000002603,0,'person_risk_flag','FREQUENT','频繁异常','warning',b'0',3,b'1','多次触发异常','1',NOW(),'1',NOW(),b'0'),
-- risk_level（4 条）
(710000000000002701,0,'risk_level','LOW','低','success',b'0',1,b'1','周检提前量取 risk.level 映射','1',NOW(),'1',NOW(),b'0'),
(710000000000002702,0,'risk_level','MEDIUM','一般','info',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000002703,0,'risk_level','HIGH','较大','warning',b'0',3,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000002704,0,'risk_level','MAJOR','重大','error',b'0',4,b'1','同上','1',NOW(),'1',NOW(),b'0');

-- ---------------------------------------------------------------------
-- 校验（执行后：dict_type 数应为 21 = v1.0 的 14 + 本次 7；行数应 +38）
-- ---------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE deleted = b'0' GROUP BY dict_type ORDER BY dict_type;
-- SELECT COUNT(*) FROM bidding_dict_data WHERE id BETWEEN 710000000000002001 AND 710000000000002799;

-- ---------------------------------------------------------------------
-- 补充（2026-09-13）：材料完备性「总体结论」与「逐项状态」两类字典
--   取值逐字来自 EnterpriseMaterialCompletenessRespVO 的 @Schema 说明：
--     总体结论 PASS 齐备 / RISK 部分缺失或临近到期 / FAIL 必备材料缺失；
--     逐项状态 READY 齐备 / EXPIRING 临近到期 / MISSING 缺失 / EXPIRED 已过期。
--   规范出处：契约 §1.1 字典类型表；用户硬约束"枚举与阈值一律动态配置"（前端不得硬编码中文与颜色）。
--   同期后端：MetaDictServiceImpl 新增 material_result / material_status 枚举映射，
--   并把"受支持类型"改为「枚举 ∪ 字典表已配置」，使纯字面量字典（cert_type 等）也能下发。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- material_result（3 条）
(710000000000002801,0,'material_result','PASS','齐备','success',b'0',1,b'1','材料完备性总体结论：必备材料齐备','1',NOW(),'1',NOW(),b'0'),
(710000000000002802,0,'material_result','RISK','部分缺失或临近到期','warning',b'0',2,b'1','可投标但需提示风险','1',NOW(),'1',NOW(),b'0'),
(710000000000002803,0,'material_result','FAIL','必备材料缺失','danger',b'0',3,b'1','不满足投标条件','1',NOW(),'1',NOW(),b'0'),
-- material_status（4 条）
(710000000000002804,0,'material_status','READY','齐备','success',b'0',1,b'1','材料在册且未临近到期','1',NOW(),'1',NOW(),b'0'),
(710000000000002805,0,'material_status','EXPIRING','临近到期','warning',b'0',2,b'1','进入到期预警窗口，窗口天数由规则配置驱动','1',NOW(),'1',NOW(),b'0'),
(710000000000002806,0,'material_status','MISSING','缺失','danger',b'0',3,b'1','未匹配到任何在册材料','1',NOW(),'1',NOW(),b'0'),
(710000000000002807,0,'material_status','EXPIRED','已过期','danger',b'0',4,b'1','有效期止早于基准日','1',NOW(),'1',NOW(),b'0');

-- ---------------------------------------------------------------------
-- 校验（执行后：dict_type 数应为 23 = v1.0 的 14 + 09-19 的 7 + 本次 2；行数 = v1.0 基线 + 45 行）
-- ---------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE deleted = b'0' GROUP BY dict_type ORDER BY dict_type;


-- #####################################################################
-- 【来源】2026-09-18-cert-menu-renumber.sql 第 2 步（菜单终态段）——原脚本的迁移/恢复动作不再需要
-- #####################################################################
-- 第 2 步：证照域迁入新号段（60005000 目录 + 四个页面 + 按钮，权限码/路径/组件逐字保留）
--        映射关系：60002000→60005000、600021xx→600041xx、600022xx→600042xx、
--                 600023xx→600043xx、600024xx→600044xx
-- ---------------------------------------------------------------------
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 目录
(60005000, '资质与证书管理', '', 1, 1100, 0, 'certificate', 'ep:medal', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 证照台账
(60005100, '证照台账', 'bidding:certificate:query', 2, 1, 60005000, 'list', NULL, 'bidding/certificate/index', 'CertificateIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005101, '证照新建', 'bidding:certificate:create', 3, 2, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005102, '证照修改/状态流转/文件版本', 'bidding:certificate:update', 3, 3, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005103, '证照验真', 'bidding:certificate:verify', 3, 4, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005104, '预警扫描/派发', 'bidding:certificate:alert-run', 3, 5, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005105, '文件版本登记', 'bidding:certificate:file', 3, 6, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 企业资质
(60005200, '企业资质', 'bidding:qualification:query', 2, 6, 60005000, 'qualification', NULL, 'bidding/qualification/index', 'QualificationIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005201, '资质建档', 'bidding:qualification:create', 3, 7, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005202, '延续申报', 'bidding:qualification:renew', 3, 8, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005203, '延续审批通过', 'bidding:qualification:approve', 3, 9, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005204, '延续审批退回', 'bidding:qualification:reject', 3, 10, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005210, '资质标准登记', 'bidding:qualification:standard', 3, 11, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005211, '要素台账录入', 'bidding:qualification:element', 3, 12, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005212, '达标核查执行', 'bidding:qualification:review', 3, 13, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005213, '安许证管理', 'bidding:qualification:permit', 3, 14, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005214, '资质到期预警扫描', 'bidding:qualification:alert-run', 3, 15, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 员工资质（父节点随目录迁移）
(60005300, '员工资质', 'bidding:personnel:profile:query', 2, 11, 60005000, 'personnel', 'ep:user', 'bidding/personnel/index', 'PersonnelIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005301, '员工建档（一人一档）', 'bidding:personnel:profile:create', 3, 12, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005302, '档案修改/在职状态流转', 'bidding:personnel:profile:update', 3, 13, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005303, '员工证书登记', 'bidding:personnel:cert:create', 3, 14, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005304, '证书修改/状态流转（吊销注销恢复）', 'bidding:personnel:cert:update', 3, 15, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005305, '员工证书删除', 'bidding:personnel:cert:delete', 3, 16, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005306, '证书检索/到期清单/细分统计', 'bidding:personnel:cert:query', 3, 17, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005307, '履历登记（培训/社保/劳动合同）', 'bidding:personnel:record:create', 3, 18, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005308, '履历查询（培训/社保/劳动合同）', 'bidding:personnel:record:query', 3, 19, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005309, '员工证件到期预警扫描', 'bidding:personnel:alert-run', 3, 20, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 证件全生命周期
(60005400, '证件全生命周期', 'bidding:certificate:lifecycle:query', 2, 21, 60005000, 'cert-lifecycle', 'ep:refresh', 'bidding/certificate/lifecycle/index', 'CertLifecycleIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005401, '生命周期发起（变更/续期/注销吊销/年检）', 'bidding:certificate:lifecycle:apply', 3, 22, 60005400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005402, '生命周期审核与生效（回写台账）', 'bidding:certificate:lifecycle:audit', 3, 23, 60005400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005403, '企业材料完备性自查（身份/资质/实力）', 'bidding:qualification:material:query', 3, 24, 60005400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 安许风险联动（v1.1 新增页面 + 本次补齐的权限点）
(60005500, '安许风险联动', 'bidding:qualification:permit:query', 2, 25, 60005000, 'safety-risk', 'ep:warning', 'bidding/safety-risk/index', 'SafetyRiskIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005501, '风险重算 / 宽限登记', 'bidding:qualification:permit:update', 3, 26, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005502, '多岗兼任合规校验', 'bidding:personnel:cert:query', 3, 27, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005503, '人员风险标签（一年异常）', 'bidding:personnel:profile:query', 3, 28, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005504, '项目关键岗位变更监控', 'bidding:project:query', 3, 29, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `type` = VALUES(`type`), `sort` = VALUES(`sort`), `deleted` = b'0';

-- ---------------------------------------------------------------------

-- =====================================================================
-- 菜单与权限点已就位。以下为"授权指引"（默认不执行）：
--   1) 超级管理员（role_id=1，租户 0）默认放行全部权限，无需授权；
--   2) 其他角色需按需授权，示例见下（给"已拥有企业资质菜单"的角色补齐电子资质全量菜单）。
-- =====================================================================
-- 第 5 步：安许风险联动新页面的授权（600045xx）——**不自动授权**，按需执行
--   原因：新页面给谁看属于业务授权决策，脚本不应替管理员决定；超级管理员（role_id=1）默认放行全部权限，
--        非超管角色需显式授权。下面给出"与安许证管理同权限范围的角色"批量授权示例（把 600045xx 给
--        已拥有 60005213 安许证管理的角色），需要时取消注释执行。
-- ---------------------------------------------------------------------
-- INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
-- SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
-- FROM `system_role_menu` rm JOIN `system_menu` m ON m.`id` BETWEEN 60005500 AND 60005504
-- WHERE rm.`menu_id` = 60005213 AND rm.`deleted` = b'0';

-- ---------------------------------------------------------------------
-- 校验段：执行后应打印本包的规模（与包内实际条数一致即正常）
-- ---------------------------------------------------------------------
SELECT '电子资质域建表' AS item, COUNT(*) AS cnt FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('bidding_dict_data','bidding_personnel_profile','bidding_rule_config','bidding_rule_config_audit','bidding_rule_config_history','cert_alert_config','cert_alert_records','cert_change_logs','cert_file_versions','cert_lifecycle_record','cert_notify_outbox','cert_verify_history','certificate_info','enterprise_qualification','personnel_certificate','personnel_job_change_log','personnel_labor_contract','personnel_multi_post_check','personnel_social_security','personnel_training_record','project_key_post_assignment','qualification_apply_record','qualification_asset','qualification_compliance_review','qualification_performance','qualification_safety_permit','qualification_staff_config','qualification_standard','safety_permit_risk_snapshot')
-- 期望：29 张表（包内 CREATE TABLE 数）
UNION ALL SELECT '菜单与权限点(60004xxx)', COUNT(*) FROM system_menu WHERE id BETWEEN 60005000 AND 60005599 AND deleted = b'0'
-- 期望：45 行（目录 + 页面 + 按钮权限点）
UNION ALL SELECT '默认规则阈值', COUNT(*) FROM bidding_rule_config WHERE deleted = b'0'
-- 期望：26 行（证照阈值 + 安许风险参数；重复执行时已存在行走 ON DUPLICATE KEY UPDATE，不新增）
UNION ALL SELECT '枚举字典行', COUNT(*) FROM bidding_dict_data WHERE deleted = b'0';
-- 期望：109 行（cert_type / holder_type / review_result / material_category / material_type / safety_risk_flag / person_risk_flag / risk_level）
