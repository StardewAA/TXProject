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
--   600053xx 员工资质 / 600056xx 平台审查中心）+ 60006000 段（设置）+ 60007000 段（智慧标书审查）。
--   2026-09-28 三台账重组：原「证件全生命周期」(60005400) 与「安许风险联动」(60005500) 已退役为按钮，
--   分别挂到证照台账(60005100)与企业资质(60005200)下；其内容并入对应台账的页签。
--   设计依据：docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md
--   口径：页面退役只改 type/parent，id 与权限码不变 —— system_role_menu 授权行继续有效。
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `issuing_authority` varchar(256) DEFAULT NULL COMMENT '证照颁发机构（GB/T 36903 5.2.5 ZZBFJG；L2）；契约 §2.3 标为可选 ⇒ 可空（原 NOT NULL 与契约冲突，端到端实测报 Field issuing_authority doesn not have a default value）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `voided` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否作废（F2 软删留痕：作废=版本不再有效但行保留；当前版本被作废则该类别失去当前版本）',
    `void_reason` varchar(512) DEFAULT NULL COMMENT '作废原因（留痕展示用）',
    `voided_at` datetime DEFAULT NULL COMMENT '作废时间（留痕用；操作人见 updater）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_file_hash` (`cert_id`, `file_hash`, `deleted`, `tenant_id`),
    UNIQUE KEY `uk_cert_cat_version` (`cert_id`, `file_category`, `version`, `deleted`, `tenant_id`),
    KEY `idx_cert_current` (`cert_id`, `is_current`, `deleted`),
    KEY `idx_cert_voided` (`cert_id`, `voided`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证照文件版本表（T 轨）';

-- 表3：cert_change_logs 变更审计日志（必建 · 等保2.0 三级 —— v3.3 §13 + v3.4 §18.3）
CREATE TABLE IF NOT EXISTS `cert_change_logs` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 alert_id）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（SYSTEM）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（=触发时间 triggered_at）',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（恒 0，只追加）',
    `cert_id` bigint DEFAULT NULL COMMENT '关联证照 certificate_info.id（EXPIRY 等证照类填；QUAL_EXPIRY 资质类置 NULL，用 ref_* 定位）',
    `ref_type` varchar(24) DEFAULT NULL COMMENT '业务对象大类 code（QUAL_EXPIRY 用）：QUAL 企业资质/SAFETY_PERMIT 安许/STAFF 资质人员',
    `ref_id` bigint DEFAULT NULL COMMENT '业务对象主键（qual_id/permit_id/staff_id）',
    `ref_name` varchar(256) DEFAULT NULL COMMENT '业务对象显示名（脱敏）',

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

) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预警记录（T 轨 · 幂等防重跑）';

-- 表12：cert_notify_outbox 通知发件箱（事务性发件箱：与业务同事务写入，通知不丢不重 —— v3.3 §十一）
-- 说明：发送扫描为全局任务（设计 INDEX 全局不分租户）；T 轨保留 tenant_id 以在任务中按租户执行
CREATE TABLE IF NOT EXISTS `cert_notify_outbox` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 outbox_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 verify_id）',
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
    `idempotent_key` varchar(64) NOT NULL COMMENT '幂等键：<种类>:cert_id:channel:<窗口桶起点秒>（种类=INFO/FILE），仅作并发兜底；幂等主判定是"滑动窗口内最近一次验真"，窗口来自配置 cert.verify.idempotent.window.seconds（v3.3 §17.5 UNIQUE）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 qual_id）',
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
    `issuing_authority` varchar(256) DEFAULT NULL COMMENT '发证/许可机关（契约标为可选 ⇒ 可空）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 apply_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 std_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 staff_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 perf_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 asset_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 review_id）',
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
-- 规范出处：本表主键由应用侧分配（**无 AUTO_INCREMENT**）⇒ 种子必须显式给 id。
-- 为什么必须：端到端实测报 "Field 'id' doesn't have a default value"，整包会在这一行中断。
-- 注意：本条修复此前被并发编辑覆盖过一次，故此处同时写明原因，便于后来者不再改回去。
INSERT INTO `cert_alert_config`
    (`id`, `tenant_id`, `creator`, `updater`, `cert_type`, `alert_type`, `thresholds`, `enabled`, `notify_channels`, `version`, `deleted`)
SELECT 1789432000000000001, 0, '1', '1', NULL, 'QUAL_EXPIRY', '[7,30,60,90]', b'1', '["EMAIL","IN_APP"]', 0, b'0'
SELECT 720000000000009001, 0, '1', '1', NULL, 'QUAL_EXPIRY', '[7,30,60,90]', b'1', '["EMAIL","IN_APP"]', 0, b'0'

WHERE NOT EXISTS (SELECT 1 FROM `cert_alert_config` WHERE `alert_type` = 'QUAL_EXPIRY' AND `cert_type` IS NULL AND `deleted` = b'0');

-- 3) 表15-8：qualification_safety_permit 安全生产许可证
CREATE TABLE IF NOT EXISTS `qualification_safety_permit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID；=设计 permit_id）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
(700000000000000014, 0, 'cert.expiring.status.days', 'CERT', 'INT', '30', '{"min":1,"max":365}',
 '扫描置“即将到期”状态的天数口径：距到期 ≤ N 天且状态为 ACTIVE → 状态置 EXPIRING_SOON。
  注意：与展示口径 cert.expiring.window.days（列表角标/筛选，前端传参、后端不读）是两件事，**不要合并**——
  流转口径调宽会改变全租户证照状态并可能联动其它流程，展示口径调宽只影响界面提醒。',
 '51 号文 ZZZT 口径（扫描 ≤30 天置即将到期）', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
(700000000000000015, 0, 'qual.material.expiring.window.days', 'QUAL', 'INT', '60', '{"min":1,"max":365}',
 '企业材料完备性自查的“临近到期”窗口（天）：材料有效期止距基准日 ≤ N 天 → 判 EXPIRING、整体 RISK。
  注意：与 cert.expiring.window.days 默认值同为 60 但**语义不同**（资格自查提前期 vs 列表角标/筛选），**不要合并**。',
 '《招投标模块开发规范》§4.6（资格审查提前期）', 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0'),
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
-- 4.1) 企业资质域：资质主档"临近到期"窗口（2026-09-13 新增）
-- 依据：《电子资质七大模块技术方案.md》§8.1（阈值集中受控）/§8.2（读取留证）；
--      与人员证书域 personnel.cert.expiring.window.days 同范式（同一语义，不同对象）。
-- 业务目标：资质到期扫描的状态联动窗口由运营调整，而不是写死在
--      QualificationAlertServiceImpl（此前硬编码 30 天，改配置不生效=假配置）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_rule_config`
(`id`, `tenant_id`, `rule_key`, `rule_group`, `value_type`, `config_value`, `legal_floor`, `description`, `regulation_ref`, `version_no`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(700000000000000012, 0, 'qual.expiring.window.days', 'QUAL', 'INT', '30', '{"min":1,"max":365}',
 '资质主档“临近到期”窗口（天），≤ 该值由资质到期扫描置 EXPIRING_SOON', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 4.2) 安许域：安全生产许可证"临近到期"窗口（2026-09-13 新增，与 4.1 同批）
-- 依据：《电子资质七大模块技术方案.md》§8.1/§8.2；《安全生产许可证条例》（安许法定预警下限 90 天，
--      见 cert.alert.legal.floor.days 的 SAFETY_PERMIT 项），本键是"状态联动窗口"，与法定下限是两个维度。
-- 业务目标：安许状态联动窗口可运营调整；独立成键，避免与资质窗口互相牵连。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_rule_config`
(`id`, `tenant_id`, `rule_key`, `rule_group`, `value_type`, `config_value`, `legal_floor`, `description`, `regulation_ref`, `version_no`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(720000000000000016, 0, 'safety.permit.expiring.window.days', 'SAFETY', 'INT', '30', '{"min":1,"max":365}',
 '安全生产许可证“临近到期”窗口（天），≤ 该值由安许到期扫描置 EXPIRING_SOON', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 4.3) 人员域：兼任岗位 → 安许考核合格证细分矩阵（2026-09-13 新增）
-- 依据：用户业务口径"兼任两个及以上岗位的，必须持另一岗位的考核合格证书"；
--      用户硬性要求"所有口径可动态配置、严禁硬编码"。
-- 业务目标：把原本写死在代码里的映射（原实现三元表达式两分支同值，等于全部 C 类）
--      搬到配置中心；业务确认矩阵后运营改配置即生效，不必改代码发版。
-- 说明：必须含 "DEFAULT" 缺省项；缺省项缺失时配证校验会**大声失败**（不静默放过）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_rule_config`
(`id`, `tenant_id`, `rule_key`, `rule_group`, `value_type`, `config_value`, `legal_floor`, `description`, `regulation_ref`, `version_no`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(700000000000000013, 0, 'personnel.multi.post.cert.subtype.mapping', 'PERSONNEL', 'JSON', '{"DEFAULT":"C","SAFETY_OFFICER":"C","PROJECT_MANAGER":"C","TECH_LEADER":"C","QUALITY_OFFICER":"C","CONSTRUCTION_ENGINEER":"C"}', NULL,
 '兼任岗位 → 安许考核合格证细分（A/B/C 类）矩阵；DEFAULT 为缺省项（当前全部 C 类，矩阵待业务确认）', NULL, 1, 'PUBLISHED', '1', NOW(), '1', NOW(), b'0');

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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
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
-- 2026-09-20 核对说明：本段**永远插不进去**，且这是正确的——表上有唯一键
--   uk_dict(tenant_id, dict_type, dict_value, deleted)，而 IDENTITY/QUALIFICATION/STRENGTH
--   三个值已由 2026-09-16-rule-config-center.sql 提供（id ...0131~0133，标签为 身份类/资质类/实力类）。
--   INSERT IGNORE 按唯一键跳过，不报错。若要改用下面这版更细的标签（身份证明/资质证明/实力证明），
--   得先 DELETE 旧行或 UPDATE 标签，不能靠本段生效。
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
-- 2026-09-20 号段修正：原用 ...2801~2803，已被 2026-09-18-subject-company.sql 的
--   subject_company_type 先行占用（同一主键 ⇒ INSERT IGNORE 静默跳过 ⇒ 该类型在库里 0 行，
--   后端 MetaDictServiceImpl 与前端 material-check.vue 都拿不到中文）。
--   按 check-seed-id-collision.js 的建议改为 ...2916~2918（紧接 2901~2915 之后，库/两包均空闲）。
(710000000000002916,0,'material_result','PASS','齐备','success',b'0',1,b'1','材料完备性总体结论：必备材料齐备','1',NOW(),'1',NOW(),b'0'),
(710000000000002917,0,'material_result','RISK','部分缺失或临近到期','warning',b'0',2,b'1','可投标但需提示风险','1',NOW(),'1',NOW(),b'0'),
(710000000000002918,0,'material_result','FAIL','必备材料缺失','danger',b'0',3,b'1','不满足投标条件','1',NOW(),'1',NOW(),b'0'),
-- material_status（4 条）
(710000000000002804,0,'material_status','READY','齐备','success',b'0',1,b'1','材料在册且未临近到期','1',NOW(),'1',NOW(),b'0'),
(710000000000002805,0,'material_status','EXPIRING','临近到期','warning',b'0',2,b'1','进入到期预警窗口，窗口天数由规则配置驱动','1',NOW(),'1',NOW(),b'0'),
(710000000000002806,0,'material_status','MISSING','缺失','danger',b'0',3,b'1','未匹配到任何在册材料','1',NOW(),'1',NOW(),b'0'),
(710000000000002807,0,'material_status','EXPIRED','已过期','danger',b'0',4,b'1','有效期止早于基准日','1',NOW(),'1',NOW(),b'0');

-- qual_status（6 条）：资质主档状态。出处：《电子证件全流程管理_数据库设计.md》表 15-1 line 453
--   "VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW(待续期)/LIMITED(限期)/REVOKED"，中文逐字取自该处。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002808,0,'qual_status','VALID','有效','success',b'0',1,b'1','资质在有效期内','1',NOW(),'1',NOW(),b'0'),
(710000000000002809,0,'qual_status','EXPIRING_SOON','即将到期','warning',b'0',2,b'1','进入到期预警窗口，窗口天数由规则配置驱动','1',NOW(),'1',NOW(),b'0'),
(710000000000002810,0,'qual_status','EXPIRED','已过期','danger',b'0',3,b'1','有效期止早于当前日期','1',NOW(),'1',NOW(),b'0'),
(710000000000002811,0,'qual_status','PENDING_RENEW','待续期','info',b'0',4,b'1','已发起延续申报，主档置待续期（表 15-7 联动）','1',NOW(),'1',NOW(),b'0'),
(710000000000002812,0,'qual_status','LIMITED','限期','danger',b'0',5,b'1','被主管部门限期整改','1',NOW(),'1',NOW(),b'0'),
(710000000000002813,0,'qual_status','REVOKED','已吊销','danger',b'1',6,b'1','终态：吊销后不可恢复，需重新申请','1',NOW(),'1',NOW(),b'0');
-- qual_apply_type（6 条）：延续/升级等申报类型。取值 = 代码枚举 QualificationApplyTypeEnum；
--   中文为常规译名（NEW 新办/UPGRADE 升级/RENEW 延续/ADDITION 增项/CHANGE 变更/CANCEL 注销），
--   如与业务口径不一致，以本表后续维护为准（前端一律读字典，不写死）。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002814,0,'qual_apply_type','NEW','新办','info',b'0',1,b'1','首次申请该资质','1',NOW(),'1',NOW(),b'0'),
(710000000000002815,0,'qual_apply_type','UPGRADE','升级','info',b'0',2,b'1','等级提升申报','1',NOW(),'1',NOW(),b'0'),
(710000000000002816,0,'qual_apply_type','RENEW','延续','info',b'0',3,b'1','到期延续；当前后端仅支持该类型','1',NOW(),'1',NOW(),b'0'),
(710000000000002817,0,'qual_apply_type','ADDITION','增项','info',b'0',4,b'1','增加资质类别','1',NOW(),'1',NOW(),b'0'),
(710000000000002818,0,'qual_apply_type','CHANGE','变更','info',b'0',5,b'1','企业名称/地址等变更换证','1',NOW(),'1',NOW(),b'0'),
(710000000000002819,0,'qual_apply_type','CANCEL','注销','danger',b'1',6,b'1','主动注销资质','1',NOW(),'1',NOW(),b'0');
-- qual_apply_status（8 条）：申报流状态。出处：表 15-7 与 QualificationApplyStatusEnum javadoc
--   "DRAFT/SUBMITTED/ACCEPTED 受理/REVIEWING 审核/APPROVED/PUBLISHED 公示/ISSUED 领证/REJECTED 退回"。
--   terminal：ISSUED 已办结、REJECTED 已退回（该申报单终态；可另行发起新申报）。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002820,0,'qual_apply_status','DRAFT','草稿','info',b'0',1,b'1','未提交，可编辑','1',NOW(),'1',NOW(),b'0'),
(710000000000002821,0,'qual_apply_status','SUBMITTED','已提交','info',b'0',2,b'1','待受理/待审批','1',NOW(),'1',NOW(),b'0'),
(710000000000002822,0,'qual_apply_status','ACCEPTED','已受理','info',b'0',3,b'1','主管部门已受理','1',NOW(),'1',NOW(),b'0'),
(710000000000002823,0,'qual_apply_status','REVIEWING','审核中','warning',b'0',4,b'1','实质审核阶段','1',NOW(),'1',NOW(),b'0'),
(710000000000002824,0,'qual_apply_status','APPROVED','已批准','success',b'0',5,b'1','批准但尚未办结','1',NOW(),'1',NOW(),b'0'),
(710000000000002825,0,'qual_apply_status','PUBLISHED','公示中','info',b'0',6,b'1','公示期','1',NOW(),'1',NOW(),b'0'),
(710000000000002826,0,'qual_apply_status','ISSUED','已办结','success',b'1',7,b'1','已领证，回写主档有效期','1',NOW(),'1',NOW(),b'0'),
(710000000000002827,0,'qual_apply_status','REJECTED','已退回','danger',b'1',8,b'1','退回原因进备注链，可重新申报','1',NOW(),'1',NOW(),b'0');
-- safety_permit_status（5 条）：安全生产许可证状态。出处：SafetyPermitStatusEnum 与安许表（14-8）。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002828,0,'safety_permit_status','VALID','有效','success',b'0',1,b'1','安许证在有效期内','1',NOW(),'1',NOW(),b'0'),
(710000000000002829,0,'safety_permit_status','EXPIRING_SOON','即将到期','warning',b'0',2,b'1','进入到期预警窗口','1',NOW(),'1',NOW(),b'0'),
(710000000000002830,0,'safety_permit_status','EXPIRED','已过期','danger',b'0',3,b'1','有效期止早于当前日期','1',NOW(),'1',NOW(),b'0'),
(710000000000002831,0,'safety_permit_status','SUSPENDED','暂扣','danger',b'0',4,b'1','暂扣期间不可用于投标，可恢复','1',NOW(),'1',NOW(),b'0'),
(710000000000002832,0,'safety_permit_status','REVOKED','已吊销','danger',b'1',5,b'1','终态：吊销后不可恢复','1',NOW(),'1',NOW(),b'0');

-- ---------------------------------------------------------------------
-- 校验（执行后：dict_type 数应为 27 = v1.0 的 14 + 09-19 的 7 + 材料 2 + 资质流程 4；行数 = v1.0 基线 + 70 行）
-- ---------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE deleted = b'0' GROUP BY dict_type ORDER BY dict_type;


-- #####################################################################
-- 【来源】2026-09-20-review-task-center.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 平台审查任务中心：bidding_review_task（契约《后端变更请求-平台审查任务中心-2026-09-20》）
-- =====================================================================
-- 目标流程：企业录入证照/资质 → 后端自动生成审查任务 → 超管在平台审查中心统一审查（通过/驳回）
--           → 结果与原因回传租户（只读查看本企业任务）。
-- 口径（契约 §3）：PENDING → APPROVED / REJECTED（驳回 reason 必填留痕）；驳回后可修正重新提交，
--           产生**新任务**、不覆盖原任务（历史可追溯）；通过后该证照/资质方可被其他接口引用。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。无 DROP/DELETE。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `bidding_review_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  -- 规范出处：平台 BaseDO/TenantBaseDO 约定（同库所有业务表均带 tenant_id，如 bidding_certificate、bidding_enterprise_qualification）。
  -- 为什么这样写（设计遵循）：ReviewTaskDO 继承 TenantBaseDO，MyBatis-Plus 租户插件会自动追加 `tenant_id = ?` 条件；
  --   若表里没有该列，任何查询/写入都会抛 Unknown column 'tenant_id' in 'where clause'（实测 500）。
  -- 业务目标：审查任务必须按租户隔离——租户只能看自己提交的任务，平台超管才跨租户审查。
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型：QUALIFICATION 企业资质 / CERTIFICATE 证照 / PERSON_CERT 员工证书（字典 review_biz_type）',
  `biz_id` bigint NOT NULL COMMENT '关联业务主键（资质 id / 证照 id）',
  `ent_id` bigint NOT NULL COMMENT '提交企业 id（租户隔离维度）',
  `ent_name` varchar(128) DEFAULT NULL COMMENT '提交企业名称（快照）',
  `ent_uscc` varchar(32) DEFAULT NULL COMMENT '提交企业统一社会信用代码（快照）',
  `ref_no` varchar(64) DEFAULT NULL COMMENT '关联证书编号/资质证书编号（快照）',
  `submit_time` datetime NOT NULL COMMENT '任务产生（企业录入）时间',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审查 / APPROVED 已通过 / REJECTED 已驳回（字典 review_task_status）',
  `reviewer` varchar(64) DEFAULT NULL COMMENT '审查人（通过/驳回后回填）',
  `review_time` datetime DEFAULT NULL COMMENT '审查时间（通过/驳回后回填）',
  `reason` varchar(512) DEFAULT NULL COMMENT '驳回原因（REJECTED 必填，租户可见）',
  `remark` varchar(512) DEFAULT NULL COMMENT '提交时的备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁（通过/驳回必须回传）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  -- 租户列打头：租户插件的 where 条件始终带 tenant_id，放最左列才能命中索引
  KEY `idx_review_tenant_status` (`tenant_id`, `status`),
  KEY `idx_review_ent_status` (`ent_id`, `status`),
  KEY `idx_review_status` (`status`),
  KEY `idx_review_submit_time` (`submit_time`),
  KEY `idx_review_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台审查任务（企业录入 → 超管审查）';

-- 字典：review_task_status / review_biz_type（前端角标与筛选依赖中文名与配色，不得硬编码）
--
-- ⚠️ 主键号段（2026-09-20 修正，真实事故）：
--   bidding_dict_data.id 由脚本**手工指派**（该表主键非自增，见 check-package-invariants ⑥），
--   号段按"域"划分：...0030xx 员工状态 / ...0031xx 社保 / ...0032xx 性别 / ...0033xx 学历 /
--   ...0034xx 资质序列 / ...0035xx 用工形式 / ...0036xx 备案状态 / ...0037xx 主体授权 /
--   ...0038xx 主体类型 / ...0039xx 溯源来源 / ...0040xx/0041xx/0042xx/0044xx 公告与状态 / ...0046xx 审查任务状态。
--   （选段过程本身就是这条注释存在的理由：本脚本连撞三次 —— 初版 ...0030xx 撞 personnel_status、
--     改 ...0040xx 撞 notice_external_status、再改 ...0045xx 撞 verify_channel；每次都是"看起来挑空了"。
--     最终用 `node sql/tools/check-seed-id-collision.js --list` 按同口径全量扫描才定到 ...0046xx。
--     结论：**手工指派主键的种子，选段必须用工具扫描，不能靠眼看**。）
--   本脚本初版误用 ...0030xx —— 与既有 personnel_status 冲突，而 INSERT IGNORE **不报错、只跳过**，
--   于是库里"看起来执行成功"却根本没有 review_task_status 三行 ⇒ /bidding/meta/dicts 报
--   1060108008「字典类型不支持」⇒ 审查页状态下拉与角标拿不到中文。
--   教训：手工指派主键的种子数据，新增前**必须先查号段占用**；INSERT IGNORE 的静默跳过
--   要靠"执行后回查行数"而不是"命令没报错"来确认。
--   ⚠️ 号段复查（2026-09-21，契约 §8 隐患修复）：初版把 review_biz_type 也放在 ...0030xx
--   （003011~003013，与 personnel_status 同段），当时只修了 review_task_status 而漏了它——
--   同段未来新增字典会 INSERT IGNORE 静默丢行。本次把 review_biz_type 迁到 ...004604~004606
--   （审查任务域 0046xx 空闲段），已执行库由配套迁移脚本 UPDATE id（见 sql/history/
--   2026-09-21-review-biz-type-renumber.sql）。
INSERT IGNORE INTO `bidding_dict_data`
  (`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (710000000000004601, 0, 'review_task_status', 'PENDING', '待审查', 'warning', b'0', 1, b'1', '审查任务状态', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004602, 0, 'review_task_status', 'APPROVED', '已通过', 'success', b'0', 2, b'1', '审查任务状态', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004603, 0, 'review_task_status', 'REJECTED', '已驳回', 'error', b'0', 3, b'1', '审查任务状态', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004604, 0, 'review_biz_type', 'QUALIFICATION', '企业资质', 'info', b'0', 1, b'1', '审查业务类型', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004605, 0, 'review_biz_type', 'CERTIFICATE', '证照', 'info', b'0', 2, b'1', '审查业务类型', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004606, 0, 'review_biz_type', 'PERSON_CERT', '员工证书', 'info', b'0', 3, b'1', '审查业务类型', '1', NOW(), '1', NOW(), b'0');


-- #####################################################################
-- 【来源】2026-09-20-review-center-menu.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 平台审查中心菜单（电子资质域 60005xxx 段补充）
-- =====================================================================
-- 用途：企业录入证照/资质后自动触发的审查任务中心（共享界面：超管筛选+审查；租户只读自己的）。
-- 依赖：父目录 60005000「资质与证书管理」已存在（master-credential-sync.sql）。
-- 权限码：bidding:review:query（共享查看）/ approve（超管通过）/ reject（超管驳回）。
-- 授权指引（按业务决策执行）：
--   · 超管角色：60005600 + 60005601 + 60005602 全授；
--   · 租户角色：仅授 60005600（query，只读查看本企业任务与结果）。
-- 幂等：INSERT IGNORE，可重复执行。
--
-- 【为什么从 sql/tools/ 移到 sql/history/】菜单属于"新装库必须具备"的终态数据，必须并入一键包；
--   而打包与门禁都只扫 sql/ 根、sql/history/ 与 master-*.sql（sql/tools/ 是**工具脚本目录**，不参与打包）。
--   留在 tools/ 的后果：本机手工执行过、库里有，但**新装库直接没有这个菜单**（页面在菜单树上消失，
--   且 check-dict-permission.js 也读不到页权限，会报"菜单页权限未登记"）。
--   文件名含 2026-09-20 供打包器按序并入；三处一致（脚本/一键包/库）由 check-permission-coverage.js 与
--   check-master-mirror.js 共同守。
-- =====================================================================
INSERT IGNORE INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
  (60005600, '平台审查中心', 'bidding:review:query', 2, 16, 60005000, 'review-center', NULL, 'bidding/review-center/index', 'ReviewCenterIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (60005601, '审查通过', 'bidding:review:approve', 3, 17, 60005600, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (60005602, '审查驳回', 'bidding:review:reject', 3, 18, 60005600, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');


-- #####################################################################
-- 【来源】2026-09-20-bidding-dict-register-personnel.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 员工资质域字典类型注册与种子（承接 2026-09-19-cert-dict-register.sql）
-- =====================================================================
-- 依据：逐条比对「员工资质页（员工档案 + 履历：培训·社保·劳动合同）」与「资质台账页」实际请求的字典类型
--       与 bidding_dict_data 已注册集，发现 5 类缺种子 —— 前端只能降级显示英文 code（拿不到中文名与标签色）。
-- 取值来源（不臆造，逐条给出出处）：
--   personnel_status   ← PersonnelStatusEnum（ACTIVE/LEAVE/SUSPENDED/RETIRED；RETIRED 按枚举迁移表为终态）
--   social_pay_status  ← PersonnelSocialSecurityStatusEnum（NORMAL/SUSPENDED/TRANSFERRED/NONE）
--   gender             ← PersonnelProfileDO.gender 注释口径（MALE/FEMALE/UNKNOWN）
--   edu_level          ← PersonnelEduLevelEnum（DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER；remark 记 rank）
--   qual_serial        ← EnterpriseQualificationDO.qualSerial 注释口径（CONSTRUCTION/SURVEY/DESIGN/SUPERVISION/TESTING）
-- 不登记的两类（有意）：
--   qual_grade         —— 值为国标表110 的等级（特级/一级/二级…），无稳定 code 口径，登记反而制造第二套编码；
--                         前端候选走 /bidding/qualification/standard/options，展示回落原码。
--   qual_apply_type / qual_apply_status —— 已于 2026-09-19 注册（本次仅修正前端引用名，见前端提交）。
-- 设计说明：
--   1) 沿用 09-19 文件的 id 分块约定，本次占用 7100000000000030xx ~ 7100000000000034xx，不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 前端一律 dictLabel/dictTag/dictOptions 优先、缺字典回落 code，本表是枚举的唯一动态来源
--      （用户硬约束：状态码/枚举值必须支持动态配置，严禁硬编码）。
-- 执行库：ruoyi-vue-pro（与 2026-09-19-cert-dict-register.sql 同库）。
-- 校验：执行后 dict_type 应新增 5 类；SELECT dict_type, COUNT(*) FROM bidding_dict_data
--       WHERE dict_type IN ('personnel_status','social_pay_status','gender','edu_level','qual_serial')
--       GROUP BY dict_type ORDER BY dict_type;  -- 期望 4 / 4 / 3 / 6 / 5
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- personnel_status（4 条）
(710000000000003001,0,'personnel_status','ACTIVE','在职','success',b'0',1,b'1','正常在职，可承担岗位与证书挂载','1',NOW(),'1',NOW(),b'0'),
(710000000000003002,0,'personnel_status','LEAVE','离职','default',b'0',2,b'1','离职后一般不再回岗，复职需重新登记','1',NOW(),'1',NOW(),b'0'),
(710000000000003003,0,'personnel_status','SUSPENDED','停职','warning',b'0',3,b'1','停职期间不计入在岗人数','1',NOW(),'1',NOW(),b'0'),
(710000000000003004,0,'personnel_status','RETIRED','退休','default',b'1',4,b'1','终态：按 PersonnelStatusEnum 迁移表不可再流转','1',NOW(),'1',NOW(),b'0'),
-- social_pay_status（4 条）
(710000000000003101,0,'social_pay_status','NORMAL','正常缴纳','success',b'0',1,b'1','社保缴纳单位与申报主体一致（人证合一证据链）','1',NOW(),'1',NOW(),b'0'),
(710000000000003102,0,'social_pay_status','SUSPENDED','断缴','warning',b'0',2,b'1','断缴月份不计入连续缴纳','1',NOW(),'1',NOW(),b'0'),
(710000000000003103,0,'social_pay_status','TRANSFERRED','已转出','info',b'0',3,b'1','人员已转出至其他缴纳主体','1',NOW(),'1',NOW(),b'0'),
(710000000000003104,0,'social_pay_status','NONE','无缴纳','default',b'0',4,b'1','查无缴纳记录（挂证风险信号）','1',NOW(),'1',NOW(),b'0'),
-- gender（3 条）
(710000000000003201,0,'gender','MALE','男','default',b'0',1,b'1','取值口径见 PersonnelProfileDO.gender 注释','1',NOW(),'1',NOW(),b'0'),
(710000000000003202,0,'gender','FEMALE','女','default',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003203,0,'gender','UNKNOWN','未知','default',b'0',3,b'1','未采集/不适用','1',NOW(),'1',NOW(),b'0'),
-- edu_level（6 条；remark 记 PersonnelEduLevelEnum 的 rank，供"学历层次不低于"类判定）
(710000000000003301,0,'edu_level','DOCTOR','博士','success',b'0',1,b'1','PersonnelEduLevelEnum rank=5','1',NOW(),'1',NOW(),b'0'),
(710000000000003302,0,'edu_level','MASTER','硕士','success',b'0',2,b'1','rank=4','1',NOW(),'1',NOW(),b'0'),
(710000000000003303,0,'edu_level','BACHELOR','本科','info',b'0',3,b'1','rank=3','1',NOW(),'1',NOW(),b'0'),
(710000000000003304,0,'edu_level','COLLEGE','大专','info',b'0',4,b'1','rank=2','1',NOW(),'1',NOW(),b'0'),
(710000000000003305,0,'edu_level','SECONDARY','中专/高中','default',b'0',5,b'1','rank=1','1',NOW(),'1',NOW(),b'0'),
(710000000000003306,0,'edu_level','OTHER','其他','default',b'0',6,b'1','rank=0','1',NOW(),'1',NOW(),b'0'),
-- qual_serial（5 条）
(710000000000003401,0,'qual_serial','CONSTRUCTION','施工','success',b'0',1,b'1','EnterpriseQualificationDO.qualSerial 口径','1',NOW(),'1',NOW(),b'0'),
(710000000000003402,0,'qual_serial','SURVEY','勘察','info',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003403,0,'qual_serial','DESIGN','设计','info',b'0',3,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003404,0,'qual_serial','SUPERVISION','监理','info',b'0',4,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003405,0,'qual_serial','TESTING','检测','default',b'0',5,b'1','同上','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-22-bidding-dict-employment-type.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 用工形式字典注册与种子（承接 2026-09-20-bidding-dict-register-personnel.sql）
-- =====================================================================
-- 依据：后端 PersonnelemploymentType 类（PersonnelEmploymentTypeEnum）与
--       PersonnelProfileDO.employment_type 列注释，两者 code 口径逐字一致：
--       FULL_TIME / PART_TIME / LABOR_DISPATCH / RETIRED_REHIRE / OTHER。
-- 取值来源（不臆造，逐条给出出处）：
--   FULL_TIME       ← 枚举注释"全职（本企业正式员工，资质指标可用）"；isFullTime(code) 仅此值为真
--   PART_TIME       ← 枚举注释"兼职"
--   LABOR_DISPATCH  ← 枚举注释"劳务派遣（劳动关系在派遣单位，属挂靠高风险形态）"
--   RETIRED_REHIRE  ← 枚举注释"退休返聘"
--   OTHER           ← 枚举注释"其他"
-- 风险着色依据（不是配色偏好）：PersonnelEmploymentTypeEnum.isOutsourceRisk(code) 为真的两种形态
--   （LABOR_DISPATCH / PART_TIME）标 danger —— 枚举 Javadoc 明写"劳务派遣/兼职人员即使证书齐全，
--   也应在核验中判为风险项"；RETIRED_REHIRE 标 warning（可上岗但不计入本单位全职口径）。
-- 为什么要注册：前端「员工档案」本次新增"用工形式"列与录入项（此前该字段前端既不能录也不能看），
--   若字典未注册，页面只能降级显示英文 code —— 与用户硬约束"枚举必须支持动态配置、严禁硬编码"冲突。
-- 设计说明：
--   1) 沿用 09-19/09-20 的 id 分块约定，本次占用 7100000000000035xx，不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 本表是"用工形式"枚举的唯一动态来源，前端一律 dictLabel/dictTag/dictOptions 优先、缺字典回落 code。
-- 执行库：ruoyi-vue-pro（与前面各字典注册脚本同库）。
-- 校验：SELECT dict_value, dict_label, color_type FROM bidding_dict_data
--       WHERE dict_type = 'employment_type' AND deleted = b'0' ORDER BY sort;  -- 期望 5 行
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000003501,0,'employment_type','FULL_TIME','全职','success',b'0',1,b'1','本企业正式员工，可计入资质人员指标（等价 15-3 is_full_time=TRUE）','1',NOW(),'1',NOW(),b'0'),
(710000000000003502,0,'employment_type','PART_TIME','兼职','danger',b'0',2,b'1','挂靠/人证分离高风险形态（PersonnelEmploymentTypeEnum.isOutsourceRisk）','1',NOW(),'1',NOW(),b'0'),
(710000000000003503,0,'employment_type','LABOR_DISPATCH','劳务派遣','danger',b'0',3,b'1','劳动关系在派遣单位，属挂靠高风险形态（isOutsourceRisk）','1',NOW(),'1',NOW(),b'0'),
(710000000000003504,0,'employment_type','RETIRED_REHIRE','退休返聘','warning',b'0',4,b'1','可承担岗位，但不计入本单位全职口径（is_full_time 为假）','1',NOW(),'1',NOW(),b'0'),
(710000000000003505,0,'employment_type','OTHER','其他','default',b'0',5,b'1','其它用工形式，核验时按非全职处理','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-23-bidding-dict-trace-and-notice-status.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 追溯/备案/主体/外部公告 状态类字典注册（承接 2026-09-22-bidding-dict-employment-type.sql）
-- =====================================================================
-- 背景：项目全景追溯抽屉（views/bidding/project）本轮补齐了"外部公告 / 备案记录 / 关注项 / 参与主体"四张表，
--       表里的状态、类型字段此前只能直出后端 code（英文码直出）。本脚本把这些枚举登记为字典，前端改为
--       dictLabel/dictTag 取值 —— 与用户硬约束一致："状态码/枚举值必须支持动态配置，严禁硬编码"。
-- 取值来源（不臆造，逐条给出出处）：
--   filing_status                ← FilingStatusEnum（PENDING/ACCEPTED/REJECTED；BID-ENT-REG-01 提交→PENDING，
--                                   监管 BID-SUP-FILE-01 回推→ACCEPTED/REJECTED）
--   subject_auth_status          ← SubjectAuthStatusEnum（ENABLED 已授权 / DISABLED 已停用；平台侧核发）
--   subject_type                 ← SubjectTypeEnum（BIDDER 投标人 / TENDERER 招标人 / AGENCY 招标代理机构，
--                                   其中 AGENCY 为扩展位"本期未落地，预留枚举防止脏数据入库"——照登记）
--   trace_subject_source         ← ProjectTraceRespVO.SubjectItem#source 的 Javadoc
--                                   （TENDER_PROJECT_SNAPSHOT=招标项目主档快照 / TENANT_AUTH=本租户平台授权主体）
--   notice_external_status       ← NoticeExternalStatusEnum（NEW/ACTIVE/INACTIVE/ARCHIVED）
--   notice_external_audit_status ← NoticeExternalAuditStatusEnum（UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN）
--   notice_external_content_type ← NoticeExternalContentTypeEnum（HTML/PDF/ATTACHMENT/IMAGE/OTHER）
-- 不登记（有意，避免制造第二套编码）：
--   tenderer_code_type —— TenderProjectDO.tendererCodeType 注释只写"招标人代码类型（TENDERER_CODE_TYPE）"，
--                         取值来自外部标准（《VO数据规范》/陕 V2.0 主体类别码），模块内无枚举可对齐；
--                         前端暂按原码展示，待口径明确后再登记。
-- 设计说明：
--   1) id 分块：每类占用一个 100 段（36xx filing / 37xx auth / 38xx subject_type / 39xx source /
--      40xx ext-status / 41xx ext-audit / 42xx ext-content），沿用 09-19~09-22 的约定，不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 着色依据业务语义而非配色偏好：驳回/停用/失效/下线标 danger 或 warning，终态标 default，
--      与既有字典（personnel_status 等）的用法保持一致。
-- 执行库：ruoyi-vue-pro（与前序字典注册脚本同库）。
-- 校验：SELECT dict_type, COUNT(*) FROM bidding_dict_data
--       WHERE dict_type IN ('filing_status','subject_auth_status','subject_type','trace_subject_source',
--                           'notice_external_status','notice_external_audit_status','notice_external_content_type')
--       GROUP BY dict_type ORDER BY dict_type;  -- 期望 3 / 2 / 3 / 2 / 4 / 4 / 5
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- filing_status（3 条）
(710000000000003601,0,'filing_status','PENDING','待监管受理','processing',b'0',1,b'1','已提交，等待监管平台 BID-SUP-FILE-01 回推','1',NOW(),'1',NOW(),b'0'),
(710000000000003602,0,'filing_status','ACCEPTED','受理通过','success',b'1',2,b'1','监管回推终态：受理通过','1',NOW(),'1',NOW(),b'0'),
(710000000000003603,0,'filing_status','REJECTED','受理驳回','danger',b'1',3,b'1','监管回推终态：驳回（需按驳回意见整改后重新提交）','1',NOW(),'1',NOW(),b'0'),
-- subject_auth_status（2 条）
(710000000000003701,0,'subject_auth_status','ENABLED','已授权','success',b'0',1,b'1','可进入后续流程（投标人可完善档案后投标；招标人可完善后发布公告）','1',NOW(),'1',NOW(),b'0'),
(710000000000003702,0,'subject_auth_status','DISABLED','已停用','danger',b'0',2,b'1','平台撤销授权：失去投标/发布资格，档案保留（留痕不删）','1',NOW(),'1',NOW(),b'0'),
-- subject_type（3 条）
(710000000000003801,0,'subject_type','BIDDER','投标人','info',b'0',1,b'1','响应招标、参加投标竞争的法人或其他组织（《招标投标法》第 25 条）','1',NOW(),'1',NOW(),b'0'),
(710000000000003802,0,'subject_type','TENDERER','招标人','success',b'0',2,b'1','依法提出招标项目、进行招标的法人或其他组织（《招标投标法》第 8 条）','1',NOW(),'1',NOW(),b'0'),
(710000000000003803,0,'subject_type','AGENCY','招标代理机构','default',b'0',3,b'1','扩展位：本期未落地，预留枚举防止脏数据入库','1',NOW(),'1',NOW(),b'0'),
-- trace_subject_source（2 条）
(710000000000003901,0,'trace_subject_source','TENDER_PROJECT_SNAPSHOT','招标项目主档快照','info',b'0',1,b'1','来自关联招标项目主档的招标人信息','1',NOW(),'1',NOW(),b'0'),
(710000000000003902,0,'trace_subject_source','TENANT_AUTH','本租户平台授权主体','success',b'0',2,b'1','来自本租户的主体授权记录（含授权状态与档案完备度）','1',NOW(),'1',NOW(),b'0'),
-- notice_external_status（4 条）
(710000000000004001,0,'notice_external_status','NEW','待审核','processing',b'0',1,b'1','采集入库，等待审核','1',NOW(),'1',NOW(),b'0'),
(710000000000004002,0,'notice_external_status','ACTIVE','生效中（可展示）','success',b'0',2,b'1','审核通过后生效并触发 ES 索引事件（MQ）','1',NOW(),'1',NOW(),b'0'),
(710000000000004003,0,'notice_external_status','INACTIVE','已失效','default',b'1',3,b'1','超生效期或源站下线','1',NOW(),'1',NOW(),b'0'),
(710000000000004004,0,'notice_external_status','ARCHIVED','已归档（只读留痕）','default',b'1',4,b'1','归档只读，不再展示','1',NOW(),'1',NOW(),b'0'),
-- notice_external_audit_status（4 条）
(710000000000004101,0,'notice_external_audit_status','UNREVIEWED','待审核','processing',b'0',1,b'1','采集入库后的初始审核态','1',NOW(),'1',NOW(),b'0'),
(710000000000004102,0,'notice_external_audit_status','APPROVED','已审核通过','success',b'0',2,b'1','审核通过并转 ACTIVE 展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004103,0,'notice_external_audit_status','REJECTED','已驳回','danger',b'0',3,b'1','驳回：不展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004104,0,'notice_external_audit_status','TAKEN_DOWN','已下线','warning',b'0',4,b'1','曾上线后被运营撤下，展示接口随即拒绝','1',NOW(),'1',NOW(),b'0'),
-- notice_external_content_type（5 条）
(710000000000004201,0,'notice_external_content_type','HTML','HTML 原文','default',b'0',1,b'1','原文原样展示 + 原网址','1',NOW(),'1',NOW(),b'0'),
(710000000000004202,0,'notice_external_content_type','PDF','PDF 原文件','default',b'0',2,b'1','pdfFileId 前端预览 + 下载 + 原网址','1',NOW(),'1',NOW(),b'0'),
(710000000000004203,0,'notice_external_content_type','ATTACHMENT','附件下载','default',b'0',3,b'1','以附件下载列表形式展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004204,0,'notice_external_content_type','IMAGE','图片','default',b'0',4,b'1','图片形态展示','1',NOW(),'1',NOW(),b'0'),
(710000000000004205,0,'notice_external_content_type','OTHER','其它','default',b'0',5,b'1','兜底：展示原网址','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-24-bidding-dict-key-post.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 关键岗位字典注册（承接 2026-09-23-bidding-dict-trace-and-notice-status.sql）
-- =====================================================================
-- 背景：安许风险联动页（views/bidding/safety-risk）的④关键岗位任职/变更登记，下拉选项此前是前端硬编码数组
--       KEY_POST_OPTIONS（"逐字取自 KeyPostEnum"）。第 11 道门禁（枚举硬编码棘轮）已把它登记在册，
--       本脚本把该枚举登记为字典，前端改走字典取值 —— 消费自己立的规矩。
-- 取值来源：KeyPostEnum（不臆造，逐字对齐；含 Javadoc 里"为什么做成枚举而不是自由文本"的口径：
--   频繁变更统计依赖"同一岗位"的确定性判定，自由文本会把"项目经理/项目负责人/项目经理(执行)"判成三个岗位）。
--   项目经理（注册建造师）= PROJECT_MANAGER、技术负责人 = TECH_LEADER、专职安全生产管理人员 = SAFETY_OFFICER、
--   质量负责人 = QUALITY_OFFICER、施工员 = CONSTRUCTION_ENGINEER。
-- 注意：后端 KeyPostEnum.isValid 只认这些 code（不接受自由文本），故本字典只做"展示与下拉"，
--       不改变入参校验；unknown code 后端 labelOf 回落原值。
-- 设计说明：
--   1) id 沿用 100 段分块约定：本类占 43xx（43 01~43 05），不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 前端保留 KEY_POST_OPTIONS 作为"字典尚未下发环境"的兜底（与备案状态下拉同一口径），
--      门禁基线里该文件仍计 1 处但理由改为"已降级为兜底、主路径走字典"。
-- 执行库：ruoyi-vue-pro。
-- 校验：SELECT dict_value, dict_label FROM bidding_dict_data WHERE dict_type = 'key_post' ORDER BY sort;  -- 期望 5 行
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000004301,0,'key_post','PROJECT_MANAGER','项目经理','processing',b'0',1,b'1','注册建造师；防挂靠监控的核心岗位','1',NOW(),'1',NOW(),b'0'),
(710000000000004302,0,'key_post','TECH_LEADER','技术负责人','processing',b'0',2,b'1','项目技术负责人','1',NOW(),'1',NOW(),b'0'),
(710000000000004303,0,'key_post','SAFETY_OFFICER','专职安全员','warning',b'0',3,b'1','专职安全生产管理人员（与安许证联动核查）','1',NOW(),'1',NOW(),b'0'),
(710000000000004304,0,'key_post','QUALITY_OFFICER','质量负责人','default',b'0',4,b'1','项目质量负责人','1',NOW(),'1',NOW(),b'0'),
(710000000000004305,0,'key_post','CONSTRUCTION_ENGINEER','施工员','default',b'0',5,b'1','施工现场管理人员','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-25-bidding-dict-verify-result-channel.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 验真结论字典 + 验真渠道补齐（承接 2026-09-24-bidding-dict-key-post.sql）
-- =====================================================================
-- 背景：本轮接线后端新加的"验真历史分页"读出口（GET /bidding/certificate-verify/history/page）。
--       该接口的 Javadoc 把病根写得很清楚："/verify 与 /verify-file 每次都返回验真历史主键，但此前没有任何接口
--       能把它读回来——留痕只进不出，主键等于一个再也打不开的句柄"。读出口补上了，前端要按 code 展示，
--       于是暴露两处字典缺口：
--         (1) verify_result 整个字典不存在（页面只能直出 MATCH/MISMATCH/UNAVAILABLE）；
--         (2) verify_channel 只有 3 行，缺 THIRD_PARTY —— 经查证枚举 CertVerifyChannelEnum 有 4 个值
--             （规范出处：设计文档 v3.3 §17.5"四库一平台/部级/省级/第三方"），属注册时漏行；
--             若真有证照走第三方通道核验，界面会直出英文 code。
-- 取值来源（不臆造）：
--   verify_result  ← CertVerifyResultEnum（MATCH/MISMATCH/UNAVAILABLE），
--                    口径见其 Javadoc：GB/T 36906—2018 §6.4 verifyinfo result 0=校验正确→MATCH；
--                    1/2/3（未激活/暂时失效/已失效）→MISMATCH；4（信息不全）/5（查无此信息）→UNAVAILABLE。
--   verify_channel ← CertVerifyChannelEnum.THIRD_PARTY（第三方核验通道）。
-- 注意：verify_result 与已有的 verification_status **不是一回事**——
--       verification_status 是"证照的验真状态"（UNVERIFIED/VERIFYING/VERIFIED/INVALID，一个证照一个当前态），
--       verify_result 是"每一次验真尝试的结论"（历史表逐行），二者语义不同，不能合并成一套编码。
-- 设计说明：id 沿用 100 段分块：verify_result 占 44xx（4401-4403），verify_channel 补行占 45 01，不复用旧 id；幂等 INSERT IGNORE。
-- 执行库：ruoyi-vue-pro。
-- 校验：SELECT dict_type, dict_value, dict_label FROM bidding_dict_data
--       WHERE dict_type IN ('verify_result','verify_channel') ORDER BY dict_type, sort;  -- 期望 3 + 4 行
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- verify_result（新增类型 3 条）
(710000000000004401,0,'verify_result','MATCH','与官方底账一致','success',b'1',1,b'1','GB/T 36906 verifyinfo result=0（校验正确）','1',NOW(),'1',NOW(),b'0'),
(710000000000004402,0,'verify_result','MISMATCH','与官方底账不一致','danger',b'1',2,b'1','result=1/2/3（未激活/暂时失效/已失效）→ 状态异常','1',NOW(),'1',NOW(),b'0'),
(710000000000004403,0,'verify_result','UNAVAILABLE','无法判定','warning',b'0',3,b'1','result=4/5（信息不全/查无此信息）→ 不可得，可重试','1',NOW(),'1',NOW(),b'0'),
-- verify_channel 补齐（第 4 个渠道）
(710000000000004501,0,'verify_channel','THIRD_PARTY','第三方核验通道','default',b'0',4,b'1','设计文档 v3.3 §17.5 四渠道之一（本轮按 CertVerifyChannelEnum 补齐漏行）','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-16-bid-review.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 智慧标书审查（前台智慧标书审查）建表 + 字典 + 菜单权限
-- 方案依据：docs/模块设计与规范/智慧标书审查-设计方案.md（V2.0 · DeepSeek 单通道）
-- 规范依据：《招投标模块开发规范》§4.5（幂等/乐观锁）、§4.7（错误码段 1-060-110）、
--           §4.8（密级/脱敏）、§5（建表：T 轨 TenantBaseDO 列、文件只存 file_id、
--           内容型文本版本化、索引命名 uk_/idx_）
-- 设计说明：
--   1) 7 张表：task（主表）/ rule_set / rule_item / issue / report / content / ai_log
--   2) 全部 T 轨（含 tenant_id），由 TenantDatabaseInterceptor 自动隔离
--   3) 文件不入库：只落 file_id + sha256 + size（红线 8）
--   4) 标书正文 / AI 原始返回 / 报告 Markdown 落 content 表，追加式版本化，不覆盖历史
--   5) ai_log 为只追加流水，保留 6 个月（归档任务见方案 §6.4）
--   6) 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，本文件可重复执行
-- 执行库：ruoyi-vue-pro
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 审查任务主表
--    一次上传 = 一个任务；承载状态机、进度、结论与归属人
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_task` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_no` varchar(64) NOT NULL COMMENT '任务编号（业务唯一键，形如 BR20260916000001）',
    `task_name` varchar(128) NOT NULL COMMENT '任务名称（默认取标书文件名）',
    `source_scene` varchar(16) NOT NULL COMMENT '来源场景：ADMIN_TASK 管理端建单 / PORTAL_SELF 个人端自助',
    `owner_user_id` bigint NOT NULL COMMENT '归属人（个人端即经办人本人；后端强制本人可见）',
    `owner_name` varchar(64) DEFAULT '' COMMENT '归属人名称快照',
    `project_id` bigint DEFAULT NULL COMMENT '关联投标项目 id（可空）',
    `tender_project_id` bigint DEFAULT NULL COMMENT '关联招标项目 id（可空）',
    `rule_set_id` bigint DEFAULT NULL COMMENT '采用的规则集 id',
    `tender_file_id` bigint DEFAULT NULL COMMENT '招标文件 file_id（可空：不传则只用平台规则库）',
    `tender_file_url` varchar(512) DEFAULT '' COMMENT '招标文件访问地址快照（仅作解析取流定位；一致性以 file_id + sha256 为准）',
    `tender_file_name` varchar(255) DEFAULT '' COMMENT '招标文件名快照',
    `tender_file_sha256` varchar(64) DEFAULT '' COMMENT '招标文件内容哈希（规则集复用依据）',
    `bid_file_id` bigint NOT NULL COMMENT '投标文件（标书）file_id',
    `bid_file_url` varchar(512) DEFAULT '' COMMENT '标书访问地址快照（仅作解析取流定位；一致性以 file_id + sha256 为准）',
    `bid_file_name` varchar(255) DEFAULT '' COMMENT '标书文件名快照',
    `bid_file_sha256` varchar(64) DEFAULT '' COMMENT '标书内容哈希（相同文件复用审查结果）',
    `parse_mode` varchar(16) DEFAULT '' COMMENT '解析方式：TEXT 文本层 / EMPTY 无文字层',
    `status` varchar(16) NOT NULL COMMENT '状态：QUEUED/RUNNING/SUCCESS/FAILED/CANCELED',
    `current_step` varchar(24) DEFAULT '' COMMENT '当前步骤：PARSE/RULE/GENERATE/REPORT',
    `progress` int NOT NULL DEFAULT 0 COMMENT '进度 0~100',
    `verdict` varchar(16) DEFAULT NULL COMMENT '结论：PASS/RISK/FAIL（仅 SUCCESS 时有值）',
    `score_total` decimal(6,2) DEFAULT NULL COMMENT '自评总分（AI 预估，报告需标注）',
    `score_json` json DEFAULT NULL COMMENT '分项得分（技术/商务/价格等）',
    `rule_cnt` int NOT NULL DEFAULT 0 COMMENT '参与比对的规则条数',
    `issue_cnt` int NOT NULL DEFAULT 0 COMMENT '问题条数',
    `high_issue_cnt` int NOT NULL DEFAULT 0 COMMENT '高危问题条数',
    `ai_called` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否发生过模型调用（扫描件失败时应为 0）',
    `error_code` varchar(32) DEFAULT '' COMMENT '失败错误码（1-060-110-xxx 对应常量名）',
    `error_msg` varchar(255) DEFAULT '' COMMENT '失败原因（面向用户的短文案）',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `started_time` datetime DEFAULT NULL COMMENT '开始执行时间',
    `finished_time` datetime DEFAULT NULL COMMENT '完成时间',
    `cost_ms` bigint DEFAULT NULL COMMENT '总耗时（毫秒）',
    `remark` varchar(255) DEFAULT '' COMMENT '备注（个人端可填，管理端可见）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本（规范 §4.5）',
    `idempotent_key` varchar(64) DEFAULT '' COMMENT '幂等键（前端一次点击一个键）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（@TableLogic）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`, `deleted`, `tenant_id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`, `deleted`, `tenant_id`),
    KEY `idx_owner_status` (`owner_user_id`, `status`, `deleted`, `tenant_id`),
    KEY `idx_tenant_create` (`tenant_id`, `create_time`),
    KEY `idx_bid_sha` (`bid_file_sha256`, `status`)
) COMMENT='智慧标书审查任务';

-- ---------------------------------------------------------------------
-- 2) 规则集（平台规则库模板 / 招标文件解析结果）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_rule_set` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `rule_set_code` varchar(64) NOT NULL COMMENT '规则集编码（业务唯一键）',
    `rule_set_name` varchar(128) NOT NULL COMMENT '规则集名称',
    `source` varchar(16) NOT NULL COMMENT '来源：PLATFORM 平台规则库 / TENDER_DOC 招标文件解析 / MIXED 混合',
    `tender_file_id` bigint DEFAULT NULL COMMENT '招标文件 file_id（source=TENDER_DOC 时必填）',
    `tender_file_sha256` varchar(64) DEFAULT '' COMMENT '招标文件哈希（同文件不重复解析）',
    `parse_status` varchar(16) DEFAULT 'SUCCESS' COMMENT '解析状态：SUCCESS/FAILED/PENDING',
    `rule_cnt` int NOT NULL DEFAULT 0 COMMENT '规则条数',
    `enable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `default_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否平台默认规则集（个人端未传招标文件时使用）',
    `remark` varchar(255) DEFAULT '' COMMENT '说明',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_set_code` (`rule_set_code`, `deleted`, `tenant_id`),
    KEY `idx_source_enable` (`source`, `enable`, `deleted`)
) COMMENT='智慧标书审查规则集';

-- ---------------------------------------------------------------------
-- 3) 规则项（废标项 / 格式要求 / 评分项）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_rule_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `rule_set_id` bigint NOT NULL COMMENT '规则集 id',
    `rule_code` varchar(64) NOT NULL COMMENT '规则编码（AI 输出必须命中白名单才入库）',
    `rule_name` varchar(128) NOT NULL COMMENT '规则名称（如"投标函是否盖章签字"）',
    `category` varchar(32) NOT NULL COMMENT '类别 code：FORM 形式评审 / QUALIFICATION 资格条件 / RESPONSE 响应性 / BUSINESS 商务 / TECH 技术 / PRICE 价格',
    `rule_type` varchar(16) NOT NULL COMMENT '类型 code：DISQUALIFY 废标项 / SCORING 评分项 / FORMAT 格式要求 / QUALIFICATION 资格要求',
    `check_mode` varchar(16) NOT NULL DEFAULT 'AI' COMMENT '检查方式 code：RULE 规则匹配 / AI 语义判断',
    `severity` varchar(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重级别 code：HIGH/MEDIUM/LOW',
    `score_max` decimal(6,2) DEFAULT NULL COMMENT '满分（rule_type=SCORING 时有值）',
    `requirement` text COMMENT '要求原文（来自招标文件或平台规则库）',
    `evidence_hint` varchar(500) DEFAULT '' COMMENT '检查点提示（告诉模型看哪里）',
    `source_ref` varchar(255) DEFAULT '' COMMENT '出处（招标文件条款号/页码）',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `enable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_set_id`, `rule_code`, `deleted`, `tenant_id`),
    KEY `idx_set_enable` (`rule_set_id`, `enable`, `deleted`)
) COMMENT='智慧标书审查规则项';

-- ---------------------------------------------------------------------
-- 4) 问题项（核心出参；evidence_text 为原文片段，供人工复核定位）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_issue` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_id` bigint NOT NULL COMMENT '审查任务 id',
    `rule_item_id` bigint DEFAULT NULL COMMENT '命中规则项 id（平台规则库来源时有值）',
    `rule_code` varchar(64) DEFAULT '' COMMENT '规则编码',
    `category` varchar(32) NOT NULL COMMENT '类别 code（同 rule_item.category）',
    `severity` varchar(16) NOT NULL COMMENT '严重级别 code：HIGH/MEDIUM/LOW',
    `issue_title` varchar(255) NOT NULL COMMENT '问题标题',
    `issue_desc` text COMMENT '问题说明',
    `hit_location` varchar(128) DEFAULT '' COMMENT '命中位置（页码/章节/段落）',
    `evidence_text` text COMMENT '标书原文片段（证据；AI 无法定位时为空并降级为"证据不足"）',
    `evidence_lack` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否证据不足（true 时不计入高危）',
    `suggestion` text COMMENT '整改建议',
    `source` varchar(16) NOT NULL COMMENT '来源 code：AI 语义判断 / RULE 规则匹配 / MANUAL 人工补录',
    `ai_confidence` decimal(4,3) DEFAULT NULL COMMENT 'AI 置信度 0~1',
    `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '复核状态 code：OPEN 待复核 / CONFIRMED 已确认 / IGNORED 已忽略 / FIXED 已整改',
    `reviewer_id` bigint DEFAULT NULL COMMENT '复核人',
    `review_remark` varchar(500) DEFAULT '' COMMENT '复核意见',
    `review_time` datetime DEFAULT NULL COMMENT '复核时间',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_severity` (`task_id`, `severity`, `deleted`),
    KEY `idx_task_status` (`task_id`, `status`, `deleted`)
) COMMENT='智慧标书审查问题项';

-- ---------------------------------------------------------------------
-- 5) 审查报告
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_id` bigint NOT NULL COMMENT '审查任务 id',
    `report_no` varchar(64) NOT NULL COMMENT '报告编号（业务唯一键）',
    `verdict` varchar(16) NOT NULL COMMENT '结论 code：PASS/RISK/FAIL',
    `score_total` decimal(6,2) DEFAULT NULL COMMENT '自评总分（AI 预估）',
    `score_json` json DEFAULT NULL COMMENT '分项得分',
    `issue_summary_json` json DEFAULT NULL COMMENT '问题摘要（按类别/级别计数 + 高频问题 TOP）',
    `conclusion` text COMMENT '总体结论与建议（Markdown）',
    `html_content` mediumtext COMMENT '报告正文（HTML，供页面渲染与打印）',
    `pdf_file_id` bigint DEFAULT NULL COMMENT '报告 PDF file_id（导出后回填）',
    `docx_file_id` bigint DEFAULT NULL COMMENT '报告 Word file_id（导出后回填）',
    `docx_file_url` varchar(512) DEFAULT '' COMMENT '报告 Word 访问地址（生成时快照，便于直接下载/预览）',
    `template_code` varchar(32) DEFAULT 'DEFAULT' COMMENT '报告模板 code',
    `ai_generated` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否 AI 生成（页脚需标注"AI 生成，需人工复核"）',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_no` (`report_no`, `deleted`, `tenant_id`),
    KEY `idx_task` (`task_id`, `deleted`)
) COMMENT='智慧标书审查报告';

-- ---------------------------------------------------------------------
-- 6) 版本化内容表（标书正文 / 结构化章节 / AI 原始返回 / 报告 Markdown）
--    规范 §5.3 第 2 款：内容每次变更追加新版本，旧版本只读
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_content` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；等同 content_id）',
    `biz_id` bigint NOT NULL COMMENT '业务记录 id（本域即 task_id）',
    `content_type` varchar(24) NOT NULL COMMENT '内容类型：BID_TEXT 标书正文 / BID_STRUCT 结构化章节 / TENDER_TEXT 招标文件正文 / AI_RAW AI 原始返回 / REPORT_MD 报告 Markdown',
    `content` mediumtext COMMENT '正文内容',
    `sha256` varchar(64) DEFAULT '' COMMENT '内容哈希（防篡改与去重）',
    `version` int NOT NULL DEFAULT 1 COMMENT '版本号（自增、只追加）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_content_version` (`biz_id`, `content_type`, `version`, `deleted`),
    KEY `idx_biz_type` (`biz_id`, `content_type`)
) COMMENT='智慧标书审查版本化内容';

-- ---------------------------------------------------------------------
-- 7) AI 调用流水（只追加；prompt 只留摘要哈希，不留全文）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bidding_bid_review_ai_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增；框架 IdType.AUTO）',
    `task_id` bigint DEFAULT NULL COMMENT '审查任务 id',
    `step` varchar(24) DEFAULT '' COMMENT '步骤：RULE/GENERATE/REPORT/PROBE',
    `model` varchar(64) DEFAULT '' COMMENT '模型名（如 deepseek-chat）',
    `prompt_digest` varchar(64) DEFAULT '' COMMENT '提示词摘要哈希（不存全文）',
    `request_chars` int DEFAULT 0 COMMENT '请求字符数',
    `response_chars` int DEFAULT 0 COMMENT '响应字符数',
    `cost_ms` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
    `status` varchar(16) DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/DEGRADED/REUSE',
    `error_msg` varchar(255) DEFAULT '' COMMENT '失败原因（脱敏，不含提示词正文）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_step` (`task_id`, `step`),
    KEY `idx_tenant_create` (`tenant_id`, `create_time`)
) COMMENT='智慧标书审查 AI 调用流水';

-- ---------------------------------------------------------------------
-- 8) 字典注册（前端一律走 /bidding/meta/dicts 取枚举，禁止硬编码）
--    id 分块：7100000000000060xx ~ 7100000000000069xx（不复用既有区块）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- bid_review_status（任务状态）
(710000000000006001,0,'bid_review_status','QUEUED','排队中','info',b'0',1,b'1','已接收，等待执行','1',NOW(),'1',NOW(),b'0'),
(710000000000006002,0,'bid_review_status','RUNNING','审查中','processing',b'0',2,b'1','流水线执行中（前端按此轮询进度）','1',NOW(),'1',NOW(),b'0'),
(710000000000006003,0,'bid_review_status','SUCCESS','已完成','success',b'1',3,b'1','终态：跑完即终态，结论另看 verdict','1',NOW(),'1',NOW(),b'0'),
(710000000000006004,0,'bid_review_status','FAILED','已失败','error',b'1',4,b'1','终态：可重试（限次）','1',NOW(),'1',NOW(),b'0'),
(710000000000006005,0,'bid_review_status','CANCELED','已取消','default',b'1',5,b'1','终态：用户或管理端取消','1',NOW(),'1',NOW(),b'0'),
-- bid_review_step（流水线步骤）
(710000000000006101,0,'bid_review_step','PARSE','解析文件','info',b'0',1,b'1','抽取标书文本与表格','1',NOW(),'1',NOW(),b'0'),
(710000000000006102,0,'bid_review_step','RULE','准备审查规则','info',b'0',2,b'1','招标文件抽规则或取平台规则库','1',NOW(),'1',NOW(),b'0'),
(710000000000006103,0,'bid_review_step','GENERATE','逐条审查','processing',b'0',3,b'1','规则×标书逐条比对，产出问题清单','1',NOW(),'1',NOW(),b'0'),
(710000000000006104,0,'bid_review_step','REPORT','生成报告','processing',b'0',4,b'1','汇总得分、结论与整改建议','1',NOW(),'1',NOW(),b'0'),
-- bid_review_scene（来源场景）
(710000000000006201,0,'bid_review_scene','PORTAL_SELF','个人端自助','info',b'0',1,b'1','门户上传 + 一键生成','1',NOW(),'1',NOW(),b'0'),
(710000000000006202,0,'bid_review_scene','ADMIN_TASK','管理端建单','default',b'0',2,b'1','管理端指定招标文件与标书','1',NOW(),'1',NOW(),b'0'),
-- bid_review_verdict（审查结论）
(710000000000006301,0,'bid_review_verdict','PASS','通过','success',b'1',1,b'1','未命中问题项','1',NOW(),'1',NOW(),b'0'),
(710000000000006302,0,'bid_review_verdict','RISK','存在风险','warning',b'1',2,b'1','命中中/低风险问题','1',NOW(),'1',NOW(),b'0'),
(710000000000006303,0,'bid_review_verdict','FAIL','不通过','error',b'1',3,b'1','命中高危废标项，需整改后重投','1',NOW(),'1',NOW(),b'0'),
-- bid_review_rule_category（规则类别）
(710000000000006401,0,'bid_review_rule_category','FORM','形式评审','info',b'0',1,b'1','投标函、签章、格式等','1',NOW(),'1',NOW(),b'0'),
(710000000000006402,0,'bid_review_rule_category','QUALIFICATION','资格条件','info',b'0',2,b'1','资质、业绩、人员等门槛','1',NOW(),'1',NOW(),b'0'),
(710000000000006403,0,'bid_review_rule_category','RESPONSE','响应性评审','warning',b'0',3,b'1','是否实质响应招标要求','1',NOW(),'1',NOW(),b'0'),
(710000000000006404,0,'bid_review_rule_category','BUSINESS','商务评审','default',b'0',4,b'1','商务分相关','1',NOW(),'1',NOW(),b'0'),
(710000000000006405,0,'bid_review_rule_category','TECH','技术评审','default',b'0',5,b'1','技术方案等','1',NOW(),'1',NOW(),b'0'),
(710000000000006406,0,'bid_review_rule_category','PRICE','价格评审','default',b'0',6,b'1','报价相关','1',NOW(),'1',NOW(),b'0'),
-- bid_review_rule_type（规则类型）
(710000000000006501,0,'bid_review_rule_type','DISQUALIFY','废标项','error',b'0',1,b'1','命中即为不通过','1',NOW(),'1',NOW(),b'0'),
(710000000000006502,0,'bid_review_rule_type','QUALIFICATION','资格要求','warning',b'0',2,b'1','资格门槛类要求','1',NOW(),'1',NOW(),b'0'),
(710000000000006503,0,'bid_review_rule_type','FORMAT','格式要求','info',b'0',3,b'1','格式与签章要求','1',NOW(),'1',NOW(),b'0'),
(710000000000006504,0,'bid_review_rule_type','SCORING','评分项','success',b'0',4,b'1','影响自评得分','1',NOW(),'1',NOW(),b'0'),
-- bid_review_check_mode（检查方式）
(710000000000006601,0,'bid_review_check_mode','RULE','规则匹配','info',b'0',1,b'1','关键词/正则/字段存在性等确定性检查','1',NOW(),'1',NOW(),b'0'),
(710000000000006602,0,'bid_review_check_mode','AI','AI 语义判断','processing',b'0',2,b'1','语义类要求交给大模型判断','1',NOW(),'1',NOW(),b'0'),
-- bid_review_severity（严重级别）
(710000000000006701,0,'bid_review_severity','HIGH','高危','error',b'0',1,b'1','多为废标项，必须整改','1',NOW(),'1',NOW(),b'0'),
(710000000000006702,0,'bid_review_severity','MEDIUM','中危','warning',b'0',2,b'1','影响得分或存在争议','1',NOW(),'1',NOW(),b'0'),
(710000000000006703,0,'bid_review_severity','LOW','低危','default',b'0',3,b'1','建议优化','1',NOW(),'1',NOW(),b'0'),
-- bid_review_issue_status（问题项复核状态）
(710000000000006801,0,'bid_review_issue_status','OPEN','待复核','info',b'0',1,b'1','AI/规则产出后默认状态','1',NOW(),'1',NOW(),b'0'),
(710000000000006802,0,'bid_review_issue_status','CONFIRMED','已确认','error',b'0',2,b'1','复核人确认是问题','1',NOW(),'1',NOW(),b'0'),
(710000000000006803,0,'bid_review_issue_status','IGNORED','已忽略','default',b'0',3,b'1','复核人判定为误报','1',NOW(),'1',NOW(),b'0'),
(710000000000006804,0,'bid_review_issue_status','FIXED','已整改','success',b'0',4,b'1','标书已修改，等待复核','1',NOW(),'1',NOW(),b'0'),
-- bid_review_issue_source（问题来源）
(710000000000006901,0,'bid_review_issue_source','AI','AI 判断','processing',b'0',1,b'1','语义类问题','1',NOW(),'1',NOW(),b'0'),
(710000000000006902,0,'bid_review_issue_source','RULE','规则匹配','info',b'0',2,b'1','确定性规则命中','1',NOW(),'1',NOW(),b'0'),
(710000000000006903,0,'bid_review_issue_source','MANUAL','人工补录','default',b'0',3,b'1','复核时人工添加','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-28-safety-risk-item.sql（已并入本包；原文件已归档至 sql/history/）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 安许风险条目工单表（五页改造缺接口契约 Q18/Q19 配套）
--
-- 规范依据：《电子资质域-待决策与残留事项清单》§4.15/契约 Q18（风险条目数据模型：等级/描述/关联证照/
--           影响主体/责任人/状态/指派）+ Q19（风险大盘 high/medium/low/handled/total）。
-- 设计遵循（Q20 口径裁定）："重新扫描风险"复用既有 POST /bidding/safety-permit/risk/recheck——
--   evaluateOne 落快照时按档位自动生成/点亮工单（同 permit 未闭环工单不叠加），不另设扫描入口；
--   责任人可指派、处理人乐观锁 CAS（version），与模块其他工单/台账同范式。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行；无 DROP/DELETE/TRUNCATE。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `safety_risk_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `ent_id` bigint NOT NULL COMMENT '影响企业主体 id（enterprise 主体档案）',
  `permit_id` bigint DEFAULT NULL COMMENT '关联安许证 id（qualification_safety_permit；空=非安许来源）',
  `risk_level` varchar(16) NOT NULL COMMENT '风险等级 code：LOW/MEDIUM/HIGH/MAJOR（字典 risk_level）',
  `risk_desc` varchar(512) NOT NULL COMMENT '风险描述',
  `cert_ref` varchar(256) DEFAULT NULL COMMENT '关联证照/证书名称快照（如安许证编号+企业名）',
  `assignee_id` bigint DEFAULT NULL COMMENT '责任人 id（指派后回填）',
  `assignee_name` varchar(64) DEFAULT NULL COMMENT '责任人姓名快照（展示免联查）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '工单状态 code：PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED（字典 risk_item_status）',
  `found_at` datetime NOT NULL COMMENT '发现时间（=扫描落库时刻）',
  `handled_at` datetime DEFAULT NULL COMMENT '处理完成时间',
  `handle_remark` varchar(512) DEFAULT NULL COMMENT '处理/指派备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（0 未删除 1 已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_item_ent_status` (`tenant_id`, `ent_id`, `status`),
  KEY `idx_item_level_status` (`tenant_id`, `risk_level`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安许风险条目工单（契约 Q18/Q19）';

-- =====================================================================
-- 已并入 master-credential-sync.sql（字典行 + 菜单权限点同步登记）
-- =====================================================================

-- ① 字典：工单状态（用户铁律：状态码/枚举动态配置，严禁硬编码）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- id 用 2901~905：2801~803 已被 material_result 字典占用（2026-09-19-cert-dict-register.sql），撞号会被 INSERT IGNORE 静默跳过
-- 字典名与取值对齐前端已发布类型（safetyRisk/index.ts 的 RiskStatus：PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED），
-- 后端 SafetyRiskItemStatusEnum 同源；改名自 2026-09-28 首版 safety_risk_item_status（三态），见通知文件第 27 条。
(710000000000002901,0,'risk_item_status','PENDING','待处理','warning',b'0',1,b'1','扫描生成，尚未指派','1',NOW(),'1',NOW(),b'0'),
(710000000000002902,0,'risk_item_status','ASSIGNED','已指派','info',b'0',2,b'1','已指定责任人，尚未开始处理','1',NOW(),'1',NOW(),b'0'),
(710000000000002903,0,'risk_item_status','HANDLING','处理中','processing',b'0',3,b'1','责任人处理中','1',NOW(),'1',NOW(),b'0'),
(710000000000002904,0,'risk_item_status','HANDLED','已处理','success',b'0',4,b'1','终态：处理闭环（扫描确认风险解除可自动关闭）','1',NOW(),'1',NOW(),b'0'),
(710000000000002905,0,'risk_item_status','IGNORED','已忽略','default',b'0',5,b'1','终态：经确认无需处理','1',NOW(),'1',NOW(),b'0');

-- ② 菜单权限点（挂 60005500 安许风险联动页；角色授权口径同 60005215 先例：超管默认全放行，其他角色按需授权）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- id 用 60005509~511：60005505/506 已被规则配置历史占用（2026-09-21-bidding-rule-config-menu.sql），撞号会被 INSERT IGNORE 静默跳过
(60005509, '风险条目查询', 'bidding:safety-risk:query', 3, 31, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005510, '风险条目指派', 'bidding:safety-risk:assign', 3, 32, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005511, '风险条目处理', 'bidding:safety-risk:handle', 3, 33, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');


-- #####################################################################
-- 【来源】2026-09-18-bidding-attachment-type-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- =====================================================================
-- 2026-09-18 附件类型字典（attachment_type）注册 —— 35 类全集
-- 依据：docs/前端规范-全域接口说明/29-bidding通用附件域与关注域-前端契约-2026-09-27.md §2.6
-- 幂等：INSERT IGNORE（uk_dict 唯一键：tenant_id+dict_type+dict_value+deleted）
-- id 段位：710000000000005001 起（50xx 段=附件域，不与既有 0001-46xx 撞号）
-- 镜像待办（2026-09-18 修正）：字典统一加入 sql/tools/build-credential-sync.js DDL_SOURCES 后重建 master-credential-sync.sql；
--   bidding_attachment 建表才手工并入 master-bidding-sync.sql（契约代理批次，见待落地清单 §6.1）
-- =====================================================================
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000005001,0,'attachment_type','PROJECT_APPROVAL_FILE','项目审批文件','default',b'0',1,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005002,0,'attachment_type','TENDER_FILE','招标文件','default',b'0',2,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005003,0,'attachment_type','TENDER_FILE_ATTACHS','招标文件附件','default',b'0',3,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005004,0,'attachment_type','TENDER_NOTICE','招标公告','info',b'0',4,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005005,0,'attachment_type','OTHER_FILE','其他文件','default',b'0',5,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005006,0,'attachment_type','FILE_EX_TDOC','电子文档（扩展）','default',b'0',6,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005007,0,'attachment_type','INVITE_DOC','邀请函','info',b'0',7,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005008,0,'attachment_type','PUBLICITY_DOC','公示文件','info',b'0',8,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005009,0,'attachment_type','WIN_BID_NOTICE','中标通知','success',b'0',9,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005010,0,'attachment_type','CONTRACT_DOC','合同','info',b'0',10,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005011,0,'attachment_type','QUALIFICATION_DOC','资格文件','default',b'0',11,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005012,0,'attachment_type','PROJECT_SOURCE_PROOF','项目来源证明','default',b'0',12,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005013,0,'attachment_type','Contract_FILE','合同文件（规范拼写原样）','info',b'0',13,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005014,0,'attachment_type','TENDER_CHANGE_NOTICE','招标变更通知','warning',b'0',14,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005015,0,'attachment_type','QUALIFICATION_NOTICE','资格审查通知','info',b'0',15,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005016,0,'attachment_type','QUALIFICATION_CLARIFY_FILE','资格审查澄清文件','default',b'0',16,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005017,0,'attachment_type','QUALIFICATION_LIST','资格审查名单','info',b'0',17,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005018,0,'attachment_type','BID_SIGN_LIST','开标签到表','default',b'0',18,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005019,0,'attachment_type','QUALIFICATION_REPORT','资格审查报告','info',b'0',19,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005020,0,'attachment_type','QUALIFICATION_RESULT_NOTICE','资格审查结果公示','success',b'0',20,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005021,0,'attachment_type','NET_QUESTION','网上提问','default',b'0',21,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005022,0,'attachment_type','SURVEY_RECORD','勘察记录','default',b'0',22,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005023,0,'attachment_type','CONTROL_FILE','控制价文件','default',b'0',23,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005024,0,'attachment_type','WIN_NOTICE_FILE','中标通知书文件','success',b'0',24,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005025,0,'attachment_type','APPROACH_PROVE','进场证明','default',b'0',25,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005026,0,'attachment_type','EXPERT_REVIEW_FILE','专家评审文件','info',b'0',26,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005027,0,'attachment_type','COMMENTS_FILE','评标意见文件','info',b'0',27,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005028,0,'attachment_type','QUANTITIES_CLEAR_REPORT','工程量清单澄清报告','default',b'0',28,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005029,0,'attachment_type','WIN_BID_TENDER_FILE','中标人投标文件','success',b'0',29,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005030,0,'attachment_type','EVALUATION_REPORT','评标报告','info',b'0',30,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005031,0,'attachment_type','CONTRACT_SING_FILE','合同签订文件','info',b'0',31,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005032,0,'attachment_type','PERFORMANCE_FILE','履约/业绩文件','default',b'0',32,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005033,0,'attachment_type','INQUIRY_FILE','质询文件','warning',b'0',33,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005034,0,'attachment_type','OBJECTION_FILE','异议函','warning',b'0',34,b'1',NULL,'1',NOW(),'1',NOW(),b'0'),
(710000000000005035,0,'attachment_type','COMPLAIN_FILE','投诉函','warning',b'0',35,b'1',NULL,'1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-20-cert-category-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 证件大类字典（cert_category）：给「证照类型」加一层业务分类
-- 依据：《招投标模块开发规范》§5 三-7（代码型字段存 code，字典 key 前缀 bidding_）
--   用户裁定（2026-09-20）：电子证照域证件按四类归口 —— 身份类 / 资质类 / 业绩类 / 资信类
-- 背景：cert_type 原把「身份/资质/业绩/资信」四种语义平铺为 11 项，前端筛选只能硬编码；
--   且「资质」「业绩」在 qualification 域另有独立台账，同一概念两处存在 → 增加本层归口。
-- 说明：cert_type 的新增大类归属见 cert_type_category 映射（下段），不改变 cert_type 既有取值。
-- ============================================================================

-- 1) 证件大类字典（4 类）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008101,0,'cert_category','IDENTITY','身份类','primary',b'0',1,b'1','主体身份证明类：营业执照/法定代表人身份证明/授权委托书','1',NOW(),'1',NOW(),b'0'),
(710000000000008102,0,'cert_category','QUALIFICATION','资质类','success',b'0',2,b'1','企业资质与许可类：体系认证/安全生产许可证/各类资质证书','1',NOW(),'1',NOW(),b'0'),
(710000000000008103,0,'cert_category','PERFORMANCE','PERFORMANCE','warning',b'0',3,b'1','业绩证明类：业绩证明/中标通知书/合同（与 qualification 业绩台账同源）','1',NOW(),'1',NOW(),b'0'),
(710000000000008104,0,'cert_category','CREDIT','资信类','info',b'0',4,b'1','资信与佐证类：财务审计报告/纳税信用/社保证明/荣誉证书','1',NOW(),'1',NOW(),b'0');

-- 2) cert_type → cert_category 归属映射字典（值=大类 code）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008111,0,'cert_type_category','ADMIN_LICENSE','IDENTITY','default',b'0',1,b'1','营业执照→身份类','1',NOW(),'1',NOW(),b'0'),
(710000000000008112,0,'cert_type_category','LEGAL_PERSON_ID','IDENTITY','default',b'0',2,b'1','法定代表人身份证明→身份类','1',NOW(),'1',NOW(),b'0'),
(710000000000008113,0,'cert_type_category','AUTH_LETTER','IDENTITY','default',b'0',3,b'1','法人授权委托书→身份类','1',NOW(),'1',NOW(),b'0'),
(710000000000008114,0,'cert_type_category','SYSTEM_CERT','QUALIFICATION','default',b'0',4,b'1','体系认证证书→资质类','1',NOW(),'1',NOW(),b'0'),
(710000000000008115,0,'cert_type_category','SAFETY_PERMIT','QUALIFICATION','default',b'0',5,b'1','安全生产许可证→资质类','1',NOW(),'1',NOW(),b'0'),
(710000000000008116,0,'cert_type_category','PERSON_QUAL','QUALIFICATION','default',b'0',6,b'1','人员资格证书→资质类','1',NOW(),'1',NOW(),b'0'),
(710000000000008117,0,'cert_type_category','PERFORMANCE','PERFORMANCE','default',b'0',7,b'1','业绩证明→业绩类','1',NOW(),'1',NOW(),b'0'),
(710000000000008118,0,'cert_type_category','AUDIT_REPORT','CREDIT','default',b'0',8,b'1','财务审计报告→资信类','1',NOW(),'1',NOW(),b'0'),
(710000000000008119,0,'cert_type_category','SOCIAL_SECURITY','CREDIT','default',b'0',9,b'1','社保证明→资信类','1',NOW(),'1',NOW(),b'0'),
(710000000000008120,0,'cert_type_category','HONOR','CREDIT','default',b'0',10,b'1','荣誉证书→资信类','1',NOW(),'1',NOW(),b'0'),
(710000000000008121,0,'cert_type_category','TAX_CREDIT','CREDIT','default',b'0',11,b'1','纳税信用证明→资信类','1',NOW(),'1',NOW(),b'0');

-- ============================================================================
-- 校验 SELECT（执行后人工核对）：应 4 行 + 11 行
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE dict_type IN ('cert_category','cert_type_category') AND deleted=0 GROUP BY dict_type;
-- ============================================================================


-- #####################################################################
-- 【来源】2026-09-21-cert-change-log-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 变更留痕字典（cert_change_type / cert_op_source）
-- 背景：cert_change_logs 一直有写入但**无读出口**；2026-09-21 补
--   GET /bidding/certificate/change-log/page 后，changeType/opSource 两个 code 字段
--   需要中文名（与证照域其它读接口同口径：字典是唯一权威来源，规范 §5 三-7）。
-- 取值来源：CertChangeLogDO 字段注释 + CertChangeTypeEnum / CertificateChangeSourceEnum。
-- ============================================================================

-- 1) 变更类型（对齐 CertChangeTypeEnum：CREATE/UPDATE/STATUS_CHANGE/REVOKE）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008201,0,'cert_change_type','CREATE','建档','success',b'0',1,b'1','首次建档（oldValue 为空）','1',NOW(),'1',NOW(),b'0'),
(710000000000008202,0,'cert_change_type','UPDATE','字段更新','primary',b'0',2,b'1','普通字段变更（留痕为字段级，一次更新多行）','1',NOW(),'1',NOW(),b'0'),
(710000000000008203,0,'cert_change_type','STATUS_CHANGE','状态流转','warning',b'0',3,b'1','台账状态变更（吊销/注销记 REVOKE）','1',NOW(),'1',NOW(),b'0'),
(710000000000008204,0,'cert_change_type','REVOKE','吊销/注销','error',b'0',4,b'1','吊销或注销（不可逆终态）','1',NOW(),'1',NOW(),b'0');

-- 2) 操作来源（对齐 CertificateChangeSourceEnum：MANUAL/IMPORT/OCR/API_SYNC/SYSTEM）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008211,0,'cert_op_source','MANUAL','手工操作','primary',b'0',1,b'1','用户在管理端手工录入/修改','1',NOW(),'1',NOW(),b'0'),
(710000000000008212,0,'cert_op_source','IMPORT','批量导入','success',b'0',2,b'1','Excel 批量导入（同批 batchId 归组）','1',NOW(),'1',NOW(),b'0'),
(710000000000008213,0,'cert_op_source','OCR','OCR 识别','info',b'0',3,b'1','扫描件 OCR 自动识别录入','1',NOW(),'1',NOW(),b'0'),
(710000000000008214,0,'cert_op_source','API_SYNC','接口同步','warning',b'0',4,b'1','外部平台/四库一平台接口同步','1',NOW(),'1',NOW(),b'0'),
(710000000000008215,0,'cert_op_source','SYSTEM','系统自动','default',b'0',5,b'1','定时任务/系统推导（如到期状态推导）','1',NOW(),'1',NOW(),b'0');

-- 校验 SELECT（执行后人工核对）：应 4 行 + 5 行
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE dict_type IN ('cert_change_type','cert_op_source') AND deleted=0 GROUP BY dict_type;


-- #####################################################################
-- 【来源】2026-09-21-dict-contract13.sql（字典种子；已并入本包）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：补注册契约 13 要求的枚举字典（19 类 / 75 行）
-- 登记日期：2026-09-21
-- 依据：《枚举字典注册请求-资质规则采集公告域-2026-09-14》（契约 13）
--       + 用户硬约束「状态码、枚举值、阈值必须支持动态配置，严禁硬编码」
--
-- [为什么] 前端棘轮门禁 check-hardcoded-enums.js 把"code→中文映射表"冻结为棘轮基线（只减不增）；
--   基线里剩余条目并非前端不肯改，而是**对应字典未注册** —— 字典缺席时纯字典化只会直出英文 code
--   （比硬编码更糟）。注册后前端即可「字典优先、映射表兜底」并逐条删除硬编码。
--
-- [号段] 710000000000009001 ~ 710000000000009075（接 cert_op_source 8215 之后；已核无冲突）。
--   ⚠️ 本模块 bidding_dict_data 主键为**手工指派**（无 AUTO_INCREMENT），撞号会**静默丢数据**，
--   故新增字典必须走本文件并跑 sql/tools/check-seed-id-collision.js 自检。
--
-- [A 类 vs B 类] 前 13 类有后端枚举类（值域权威，中文名取自契约 13 表格）；
--   后 6 类（rule_hit_source / crawl_job_status / crawl_trigger_type / qual_alert_ref_type /
--   crawl_parse_status / ai_review_status）原为「值域无权威来源」，本次按**既有实现/字段注释**书面固化，
--   如后续业务调整值域，改字典即可（这正是"动态配置"的意义）。
--
-- [幂等] 全部 INSERT ... ON DUPLICATE KEY UPDATE（按主键），可重复执行。
-- ============================================================================

INSERT INTO `bidding_dict_data`
  (`id`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`,
   `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `version`)
VALUES
('710000000000009001', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'REGISTERED_BUILDER', '注册建造师', 'default', b'0', 1, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009002', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'TECH_LEAD', '技术负责人', 'default', b'0', 2, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009003', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'MID_TITLE', '中级职称', 'default', b'0', 3, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009004', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'SKILLED_WORKER', '技术工人', 'default', b'0', 4, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009005', 0, '1', NOW(), '1', NOW(), b'0', 'qual_staff_role', 'SITE_MGR', '现场管理人员', 'default', b'0', 5, b'1', '资质人员角色（QualificationStaffRoleEnum 口径）', 0),
('710000000000009006', 0, '1', NOW(), '1', NOW(), b'0', 'qual_performance_role', 'GENERAL', '总承包', 'default', b'0', 1, b'1', '资质业绩角色（PerformanceRoleEnum 口径）', 0),
('710000000000009007', 0, '1', NOW(), '1', NOW(), b'0', 'qual_performance_role', 'SUB', '分包', 'default', b'0', 2, b'1', '资质业绩角色（PerformanceRoleEnum 口径）', 0),
('710000000000009008', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'NET_ASSET', '净资产', 'default', b'0', 1, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009009', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'PLANT', '厂房', 'default', b'0', 2, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009010', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'MAJOR_EQUIP', '主要设备', 'default', b'0', 3, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009011', 0, '1', NOW(), '1', NOW(), b'0', 'qual_asset_type', 'TEST_INSTR', '检测仪器', 'default', b'0', 4, b'1', '资质资产类型（QualificationAssetTypeEnum 口径）', 0),
('710000000000009012', 0, '1', NOW(), '1', NOW(), b'0', 'qual_review_type', 'SELF', '企业自查', 'default', b'0', 1, b'1', '资质核查类型（QualificationReviewTypeEnum 口径）', 0),
('710000000000009013', 0, '1', NOW(), '1', NOW(), b'0', 'qual_review_type', 'DYNAMIC', '动态核查', 'default', b'0', 2, b'1', '资质核查类型（QualificationReviewTypeEnum 口径）', 0),
('710000000000009014', 0, '1', NOW(), '1', NOW(), b'0', 'qual_review_type', 'PRE_APPLY', '申报前核查', 'default', b'0', 3, b'1', '资质核查类型（QualificationReviewTypeEnum 口径）', 0),
('710000000000009015', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'INT', '整数', 'default', b'0', 1, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009016', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'DECIMAL', '小数', 'default', b'0', 2, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009017', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'BOOL', '布尔', 'default', b'0', 3, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009018', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'STRING', '字符串', 'default', b'0', 4, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009019', 0, '1', NOW(), '1', NOW(), b'0', 'rule_value_type', 'JSON', 'JSON 结构', 'default', b'0', 5, b'1', '规则值类型（RuleConfigValueTypeEnum 口径）', 0),
('710000000000009020', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009021', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'PUBLISHED', '已发布', 'default', b'0', 2, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009022', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'GRAY', '灰度', 'default', b'0', 3, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009023', 0, '1', NOW(), '1', NOW(), b'0', 'rule_status', 'ROLLED_BACK', '已回滚', 'default', b'0', 4, b'1', '规则状态（RuleConfigStatusEnum 口径；ROLLED_BACK 仅由回滚产生，写入下拉请过滤）', 0),
('710000000000009024', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'CERT', '证照', 'default', b'0', 1, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009025', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'ALERT', '预警', 'default', b'0', 2, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009026', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'PERSONNEL', '人员', 'default', b'0', 3, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009027', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'QUAL', '资质', 'default', b'0', 4, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009028', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'SAFETY', '安许', 'default', b'0', 5, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009029', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'INSPECTION', '检查', 'default', b'0', 6, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009030', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'EQUIPMENT', '设备', 'default', b'0', 7, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009031', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'ANNOTATION', '批注', 'default', b'0', 8, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009032', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'BIDDOC', '投标文件', 'default', b'0', 9, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009033', 0, '1', NOW(), '1', NOW(), b'0', 'rule_group', 'CRYPTO', '密码应用', 'default', b'0', 10, b'1', '规则分组（RuleConfigGroupEnum 口径）', 0),
('710000000000009034', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009035', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'PUBLISHED', '已发布', 'default', b'0', 2, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009036', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'UNPUBLISHED', '已撤下', 'default', b'0', 3, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009037', 0, '1', NOW(), '1', NOW(), b'0', 'notice_status', 'ARCHIVED', '已归档', 'default', b'0', 4, b'1', '公告状态（NoticeStatusEnum 口径）', 0),
('710000000000009038', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'PENDING', '待执行', 'default', b'0', 1, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009039', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'RUNNING', '执行中', 'default', b'0', 2, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009040', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'SUCCESS', '成功', 'default', b'0', 3, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009041', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'FAILED', '失败', 'default', b'0', 4, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009042', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_task_status', 'SKIPPED', '已跳过', 'default', b'0', 5, b'1', '采集任务状态（CrawlTaskStatusEnum 口径）', 0),
('710000000000009043', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'HTML', 'HTML', 'default', b'0', 1, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009044', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'JSON', 'JSON', 'default', b'0', 2, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009045', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'FILE', '文件', 'default', b'0', 3, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009046', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_payload_type', 'PDF', 'PDF', 'default', b'0', 4, b'1', '采集载荷类型（CrawlParsePayloadTypeEnum 口径）', 0),
('710000000000009047', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_result', 'PASS', '齐备', 'default', b'0', 1, b'1', '材料齐备结论（EnterpriseMaterialResultEnum 口径）', 0),
('710000000000009048', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_result', 'RISK', '部分缺失或临近到期', 'default', b'0', 2, b'1', '材料齐备结论（EnterpriseMaterialResultEnum 口径）', 0),
('710000000000009049', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_result', 'FAIL', '必备材料缺失', 'default', b'0', 3, b'1', '材料齐备结论（EnterpriseMaterialResultEnum 口径）', 0),
('710000000000009050', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'READY', '齐备', 'default', b'0', 1, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009051', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'EXPIRING', '临近到期', 'default', b'0', 2, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009052', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'MISSING', '缺失', 'default', b'0', 3, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009053', 0, '1', NOW(), '1', NOW(), b'0', 'material_check_status', 'EXPIRED', '已过期', 'default', b'0', 4, b'1', '材料项状态（EnterpriseMaterialStatusEnum 口径）', 0),
('710000000000009054', 0, '1', NOW(), '1', NOW(), b'0', 'notice_audit_source', 'AI', 'AI 审查', 'default', b'0', 1, b'1', '公告审查来源（NoticeAuditSourceEnum 口径）', 0),
('710000000000009055', 0, '1', NOW(), '1', NOW(), b'0', 'notice_audit_source', 'HUMAN', '人工审查', 'default', b'0', 2, b'1', '公告审查来源（NoticeAuditSourceEnum 口径）', 0),
('710000000000009056', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'TENANT', '租户自定义', 'default', b'0', 1, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009057', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'PLATFORM', '平台默认', 'default', b'0', 2, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009058', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'GRAY', '灰度', 'default', b'0', 3, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009059', 0, '1', NOW(), '1', NOW(), b'0', 'rule_hit_source', 'FALLBACK', '兜底', 'default', b'0', 4, b'1', '规则命中来源（契约13 B类：值域取自 RuleConfigAuditPageReqVO 注释，已书面固化）', 0),
('710000000000009060', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'RUNNING', '执行中', 'default', b'0', 1, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009061', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'SUCCESS', '成功', 'default', b'0', 2, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009062', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'PARTIAL', '部分成功', 'default', b'0', 3, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009063', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_job_status', 'FAILED', '失败', 'default', b'0', 4, b'1', '采集批次状态（契约13 B类：值域取自批次页既有展示口径，已书面固化）', 0),
('710000000000009064', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_trigger_type', 'SCHEDULE', '定时', 'default', b'0', 1, b'1', '采集触发类型（契约13 B类：值域取自既有实现，已书面固化）', 0),
('710000000000009065', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_trigger_type', 'MANUAL', '手动', 'default', b'0', 2, b'1', '采集触发类型（契约13 B类：值域取自既有实现，已书面固化）', 0),
('710000000000009066', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_trigger_type', 'REPAIR', '补采', 'default', b'0', 3, b'1', '采集触发类型（契约13 B类：值域取自既有实现，已书面固化）', 0),
('710000000000009067', 0, '1', NOW(), '1', NOW(), b'0', 'qual_alert_ref_type', 'QUAL', '资质', 'default', b'0', 1, b'1', '资质预警来源类型（契约13 B类：值域取自现有实现，已书面固化）', 0),
('710000000000009068', 0, '1', NOW(), '1', NOW(), b'0', 'qual_alert_ref_type', 'SAFETY_PERMIT', '安许证', 'default', b'0', 2, b'1', '资质预警来源类型（契约13 B类：值域取自现有实现，已书面固化）', 0),
('710000000000009069', 0, '1', NOW(), '1', NOW(), b'0', 'qual_alert_ref_type', 'STAFF', '人员配置', 'default', b'0', 3, b'1', '资质预警来源类型（契约13 B类：值域取自现有实现，已书面固化）', 0),
('710000000000009070', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_parse_status', 'SUCCESS', '成功', 'default', b'0', 1, b'1', '采集解析状态（契约13 B类：值域取自 CrawlParseTestRespVO 注释，已书面固化）', 0),
('710000000000009071', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_parse_status', 'PARTIAL', '部分成功', 'default', b'0', 2, b'1', '采集解析状态（契约13 B类：值域取自 CrawlParseTestRespVO 注释，已书面固化）', 0),
('710000000000009072', 0, '1', NOW(), '1', NOW(), b'0', 'crawl_parse_status', 'FAILED', '失败', 'default', b'0', 3, b'1', '采集解析状态（契约13 B类：值域取自 CrawlParseTestRespVO 注释，已书面固化）', 0),
('710000000000009073', 0, '1', NOW(), '1', NOW(), b'0', 'ai_review_status', 'PASS', 'AI 审查通过', 'default', b'0', 1, b'1', '外部公告 AI 审查状态（契约13 B类：值域取自 NoticeExternalManageRespVO 注释，已书面固化）', 0),
('710000000000009074', 0, '1', NOW(), '1', NOW(), b'0', 'ai_review_status', 'REJECT', 'AI 审查未通过（转人工）', 'default', b'0', 2, b'1', '外部公告 AI 审查状态（契约13 B类：值域取自 NoticeExternalManageRespVO 注释，已书面固化）', 0),
('710000000000009075', 0, '1', NOW(), '1', NOW(), b'0', 'ai_review_status', 'FAILED', 'AI 审查执行失败', 'default', b'0', 3, b'1', '外部公告 AI 审查状态（契约13 B类：值域取自 NoticeExternalManageRespVO 注释，已书面固化）', 0)
AS new
ON DUPLICATE KEY UPDATE
  `dict_label` = new.`dict_label`,
  `remark`     = new.`remark`,
  `sort`       = new.`sort`,
  `enabled`    = new.`enabled`,
  `deleted`    = b'0';

-- 校验段：19 类字典的条数（期望与下方注释一致）
SELECT dict_type, COUNT(*) AS items
FROM bidding_dict_data
WHERE deleted = 0
  AND dict_type IN ('qual_staff_role', 'qual_performance_role', 'qual_asset_type', 'qual_review_type', 'rule_value_type', 'rule_status', 'rule_group', 'notice_status', 'crawl_task_status', 'crawl_payload_type', 'material_check_result', 'material_check_status', 'notice_audit_source', 'rule_hit_source', 'crawl_job_status', 'crawl_trigger_type', 'qual_alert_ref_type', 'crawl_parse_status', 'ai_review_status')
GROUP BY dict_type
ORDER BY dict_type;


-- #####################################################################
-- 【来源】2026-09-21-dict-coverage-gap6.sql（字典种子；已并入本包）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：补注册字典覆盖率门禁报出的 6 类（合同/资格预审/异议/人员/主体）
-- 登记日期：2026-09-21
-- 依据：sql/tools/check-dict-coverage.js 报 "6 类字典未注册（页面会退化成显示英文 code）"
--       —— alert_ref_type / contract_status / objection_type /
--          prequal_apply_status / prequal_status / subject_company_type
--
-- [为什么] 前端页面均为「字典优先 + 本地兜底」：字典注册后自动切换、兜底表可删；
--   未注册时页面只能回显英文 code（如 SIGNED / PASSED），运营不可读。
--
-- [号段] 710000000000009101 ~ 710000000000009118（9000 段空闲区；与契约13的 9001~9075 相邻不冲突）。
--
-- [值域来源] 前 3 类有后端枚举（中文名逐字取自枚举 getLabel()）；
--   后 3 类后端无枚举，值域**逐字取自前端既有兜底集**（已书面固化，如需调整改字典即可）。
--
-- [幂等] ON DUPLICATE KEY UPDATE，可重复执行。
-- ============================================================================

INSERT INTO `bidding_dict_data`
  (`id`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`,
   `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `version`)
VALUES
('710000000000009101', 0, '1', NOW(), '1', NOW(), b'0', 'contract_status', 'SIGNED', '已签', 'default', b'0', 1, b'1', 'ContractStatusEnum 口径（已签可编辑；已归档只读，存档≥15年）', 0),
('710000000000009102', 0, '1', NOW(), '1', NOW(), b'0', 'contract_status', 'ARCHIVED', '已归档', 'default', b'0', 2, b'1', 'ContractStatusEnum 口径（已签可编辑；已归档只读，存档≥15年）', 0),
('710000000000009103', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'DRAFT', '草稿', 'default', b'0', 1, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009104', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'PUBLISHED', '已发布', 'default', b'0', 2, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009105', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'APPLYING', '申请中', 'default', b'0', 3, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009106', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'REVIEWING', '审查中', 'default', b'0', 4, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009107', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'RESULTED', '已出结果', 'default', b'0', 5, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009108', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_status', 'CLOSED', '已关闭', 'default', b'0', 6, b'1', 'PrequalStatusEnum 口径', 0),
('710000000000009109', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_apply_status', 'SUBMITTED', '已提交', 'default', b'0', 1, b'1', 'PrequalApplyStatusEnum 口径', 0),
('710000000000009110', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_apply_status', 'PASSED', '通过', 'default', b'0', 2, b'1', 'PrequalApplyStatusEnum 口径', 0),
('710000000000009111', 0, '1', NOW(), '1', NOW(), b'0', 'prequal_apply_status', 'FAILED', '不通过', 'default', b'0', 3, b'1', 'PrequalApplyStatusEnum 口径', 0),
('710000000000009112', 0, '1', NOW(), '1', NOW(), b'0', 'alert_ref_type', 'PERSON_CERT', '员工证书', 'default', b'0', 1, b'1', '值域取自 personnel/index.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009113', 0, '1', NOW(), '1', NOW(), b'0', 'alert_ref_type', 'PERSON_CERT_REVIEW', '复审', 'default', b'0', 2, b'1', '值域取自 personnel/index.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009114', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'QUALIFICATION', '资格条件异议', 'default', b'0', 1, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009115', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'DOCUMENT', '招标文件异议', 'default', b'0', 2, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009116', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'OPENING', '开标异议', 'default', b'0', 3, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009117', 0, '1', NOW(), '1', NOW(), b'0', 'objection_type', 'EVALUATION', '评标结果异议', 'default', b'0', 4, b'1', '值域取自 ObjectionPanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009118', 0, '1', NOW(), '1', NOW(), b'0', 'subject_company_type', 'SELF', '本企业', 'default', b'0', 1, b'1', '值域取自 CompanyManagePanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009119', 0, '1', NOW(), '1', NOW(), b'0', 'subject_company_type', 'SUBSIDIARY', '子公司', 'default', b'0', 2, b'1', '值域取自 CompanyManagePanel.vue 既有兜底集（后端无枚举，已书面固化）', 0),
('710000000000009120', 0, '1', NOW(), '1', NOW(), b'0', 'subject_company_type', 'BRANCH', '分公司', 'default', b'0', 3, b'1', '值域取自 CompanyManagePanel.vue 既有兜底集（后端无枚举，已书面固化）', 0)
AS new
ON DUPLICATE KEY UPDATE
  `dict_label` = new.`dict_label`,
  `remark`     = new.`remark`,
  `sort`       = new.`sort`,
  `enabled`    = new.`enabled`,
  `deleted`    = b'0';

-- 校验段
SELECT dict_type, COUNT(*) AS items
FROM bidding_dict_data
WHERE deleted = 0 AND dict_type IN
  ('contract_status','prequal_status','prequal_apply_status','alert_ref_type','objection_type','subject_company_type')
GROUP BY dict_type ORDER BY dict_type;


-- #####################################################################
-- 【来源】2026-09-28-bidding-code-set-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- ---------------------------------------------------------------------
-- 招标交易代码集 123 类共 626 条下沉业务字典（bidding_dict_data）
-- 整改依据：用户核查结论③：规范照抄枚举数据改为数据库字典，禁止 Java 常量堆积
-- 出处：《VO 数据对象规范》第十章（陕西/国家 各代码表）
-- 幂等：INSERT IGNORE；类型经 MetaDictServiceImpl 按字典表数据自动支持（/bidding/meta/dicts）
-- id 分块：7100000000000079xx 起（避开已用 70xx/73xx/76xx 段）
-- 2026-10 号段修正（check-seed-id-collision.js 报出，本文件为"后出现"一方）：本文件与既有
--   2026-09-20-cert-category-dict.sql（710000000000008101~8121）、2026-09-21-cert-change-log-dict.sql
--   （710000000000008201~8215）撞号共 24 个 ⇒ 两者同时并入一键包时，后执行的 INSERT IGNORE 会**静默整行跳过**，
--   导致 national_reward_punish_type_136 / national_section_classify *_36~38 / shaanxi_abnormal* /
--   shaanxi_amount_unit_* 等字典在库里缺行。按门禁建议把撞号的 24 个 id 整体上移到空闲号段
--   7100000000000018101~18215（该段在 sql/ 与 sql/history/ 均无占用）。
-- dict_type 命名：类名去 Enum 转 snake_case（保留规范章节后缀如 _1/_413/_56）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAlertLevelEnum.java → national_alert_level（3 条）
(710000000000007901,0,'national_alert_level','01','违法','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007902,0,'national_alert_level','02','违规','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007903,0,'national_alert_level','99','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAmountUnit_8Enum.java → national_amount_unit_8（2 条）
(710000000000007904,0,'national_amount_unit_8','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007905,0,'national_amount_unit_8','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAssetTradeModeEnum.java → national_asset_trade_mode（6 条）
(710000000000007906,0,'national_asset_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007907,0,'national_asset_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007908,0,'national_asset_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007909,0,'national_asset_trade_mode','4','~~协议转让（非竞价）~~','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007910,0,'national_asset_trade_mode','5','~~动态报价~~','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007911,0,'national_asset_trade_mode','99','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAssetTypeEnum.java → national_asset_type（6 条）
(710000000000007912,0,'national_asset_type','1','房产','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007913,0,'national_asset_type','2','土地使用权','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007914,0,'national_asset_type','3','交通运输工具','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007915,0,'national_asset_type','4','设备','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007916,0,'national_asset_type','5','在建工程','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007917,0,'national_asset_type','99','其他资产','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalAuctionType_56Enum.java → national_auction_type_56（6 条）
(710000000000007918,0,'national_auction_type_56','0','招标出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007919,0,'national_auction_type_56','1','挂牌出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007920,0,'national_auction_type_56','2','拍卖出让','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007921,0,'national_auction_type_56','3','网上挂牌出让','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007922,0,'national_auction_type_56','4','网上拍卖出让','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007923,0,'national_auction_type_56','9','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalComplianceStatusEnum.java → national_compliance_status（3 条）
(710000000000007924,0,'national_compliance_status','1','合格','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007925,0,'national_compliance_status','2','不合格','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007926,0,'national_compliance_status','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalDissentTypeEnum.java → national_dissent_type（5 条）
(710000000000007927,0,'national_dissent_type','1','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007928,0,'national_dissent_type','2','招标文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007929,0,'national_dissent_type','3','开标过程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007930,0,'national_dissent_type','4','资格预审结果','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007931,0,'national_dissent_type','5','评标结果','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalEvaluationLevelEnum.java → national_evaluation_level（4 条）
(710000000000007932,0,'national_evaluation_level','1','优秀','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007933,0,'national_evaluation_level','2','良好','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007934,0,'national_evaluation_level','3','合格','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007935,0,'national_evaluation_level','4','不合格','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalEvaluationModeEnum.java → national_evaluation_mode（4 条）
(710000000000007936,0,'national_evaluation_mode','1','电子','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007937,0,'national_evaluation_mode','2','线下','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007938,0,'national_evaluation_mode','3','远程异地评标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007939,0,'national_evaluation_mode','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalEvaluationResultEnum.java → national_evaluation_result（3 条）
(710000000000007940,0,'national_evaluation_result','1','招标失败','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007941,0,'national_evaluation_result','2','重新招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007942,0,'national_evaluation_result','3','评标结果公示','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalExpertTypeEnum.java → national_expert_type（2 条）
(710000000000007943,0,'national_expert_type','1','在库专家','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007944,0,'national_expert_type','2','招标人代表','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalGenderEnum.java → national_gender（2 条）
(710000000000007945,0,'national_gender','0','男','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007946,0,'national_gender','1','女','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalIdCardTypeEnum.java → national_id_card_type（27 条）
(710000000000007947,0,'national_id_card_type','01','居民身份证','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007948,0,'national_id_card_type','02','军官证','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007949,0,'national_id_card_type','03','武警警官证','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007950,0,'national_id_card_type','04','士兵证','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007951,0,'national_id_card_type','05','军队离退休干部证','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007952,0,'national_id_card_type','06','残疾人证','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007953,0,'national_id_card_type','07','残疾军人证（1-8级）','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007954,0,'national_id_card_type','08','护照','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007955,0,'national_id_card_type','09','港澳同胞回乡证','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007956,0,'national_id_card_type','10','港澳居民来往内地通行证','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007957,0,'national_id_card_type','11','中华人民共和国往来港澳通行证','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007958,0,'national_id_card_type','12','台湾居民来往大陆通行证','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007959,0,'national_id_card_type','13','大陆居民往来台湾通行证','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007960,0,'national_id_card_type','14','外国人居留证','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007961,0,'national_id_card_type','15','外交官证','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007962,0,'national_id_card_type','16','领事馆证','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007963,0,'national_id_card_type','17','海员证','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007964,0,'national_id_card_type','18','香港身份证','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007965,0,'national_id_card_type','19','台湾身份证','default',b'0',19,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007966,0,'national_id_card_type','20','澳门身份证','default',b'0',20,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007967,0,'national_id_card_type','21','外国人身份证件','default',b'0',21,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007968,0,'national_id_card_type','22','高校毕业生自主创业证','default',b'0',22,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007969,0,'national_id_card_type','23','就业失业登记证','default',b'0',23,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007970,0,'national_id_card_type','24','台胞证','default',b'0',24,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007971,0,'national_id_card_type','25','退休证','default',b'0',25,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007972,0,'national_id_card_type','26','离休证','default',b'0',26,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007973,0,'national_id_card_type','99','其他证件','default',b'0',27,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalIllegalTypeEnum.java → national_illegal_type（3 条）
(710000000000007974,0,'national_illegal_type','1','违法','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007975,0,'national_illegal_type','2','违规','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007976,0,'national_illegal_type','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalLegalPersonCategoryEnum.java → national_legal_person_category（5 条）
(710000000000007977,0,'national_legal_person_category','1','企业','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007978,0,'national_legal_person_category','2','机关法人','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007979,0,'national_legal_person_category','3','事业单位','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007980,0,'national_legal_person_category','4','社会组织','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007981,0,'national_legal_person_category','9','其他','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalMarginPayType_39Enum.java → national_margin_pay_type_39（7 条）
(710000000000007982,0,'national_margin_pay_type_39','1','资金现金','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007983,0,'national_margin_pay_type_39','2','银行保函','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007984,0,'national_margin_pay_type_39','3','担保','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007985,0,'national_margin_pay_type_39','4','电汇','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007986,0,'national_margin_pay_type_39','5','汇票','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007987,0,'national_margin_pay_type_39','6','支票','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007988,0,'national_margin_pay_type_39','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalNoticeNatureEnum.java → national_notice_nature（4 条）
(710000000000007989,0,'national_notice_nature','1','正常公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007990,0,'national_notice_nature','2','更正公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007991,0,'national_notice_nature','3','重发公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007992,0,'national_notice_nature','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalNoticeType_33Enum.java → national_notice_type_33（4 条）
(710000000000007993,0,'national_notice_type_33','1','招标公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007994,0,'national_notice_type_33','2','资格预审公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007995,0,'national_notice_type_33','3','中标结果公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007996,0,'national_notice_type_33','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalNoticeType_58Enum.java → national_notice_type_58（3 条）
(710000000000007997,0,'national_notice_type_58','1','正常公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007998,0,'national_notice_type_58','2','补充公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000007999,0,'national_notice_type_58','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalOpenResultEnum.java → national_open_result（3 条）
(710000000000008000,0,'national_open_result','1','开标结束','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008001,0,'national_open_result','2','招标失败','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008002,0,'national_open_result','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPreQualifyMode_40Enum.java → national_pre_qualify_mode_40（2 条）
(710000000000008003,0,'national_pre_qualify_mode_40','1','资格预审','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008004,0,'national_pre_qualify_mode_40','2','资格后审','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPriceForm_9Enum.java → national_price_form_9（3 条）
(710000000000008005,0,'national_price_form_9','1','金额','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008006,0,'national_price_form_9','2','费率/比率/优惠率/合格率等','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008007,0,'national_price_form_9','3','其他形式','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalProjectCategoryRelEnum.java → national_project_category_rel（2 条）
(710000000000008008,0,'national_project_category_rel','0','无','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008009,0,'national_project_category_rel','1','固定资产类项目','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPublicityTypeEnum.java → national_publicity_type（3 条）
(710000000000008010,0,'national_publicity_type','1','正常','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008011,0,'national_publicity_type','2','更正','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008012,0,'national_publicity_type','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPurchaseMode_52Enum.java → national_purchase_mode_52（7 条）
(710000000000008013,0,'national_purchase_mode_52','1','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008014,0,'national_purchase_mode_52','2','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008015,0,'national_purchase_mode_52','3','竞争性谈判','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008016,0,'national_purchase_mode_52','4','单一来源采购','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008017,0,'national_purchase_mode_52','5','询价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008018,0,'national_purchase_mode_52','6','竞争性磋商','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008019,0,'national_purchase_mode_52','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalPurchaseOrganizeForm_53Enum.java → national_purchase_organize_form_53（3 条）
(710000000000008020,0,'national_purchase_organize_form_53','1','集中采购','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008021,0,'national_purchase_organize_form_53','2','分散采购','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008022,0,'national_purchase_organize_form_53','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalQualificationLevelEnum.java → national_qualification_level（13 条）
(710000000000008023,0,'national_qualification_level','01','特级','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008024,0,'national_qualification_level','02','一级','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008025,0,'national_qualification_level','03','二级','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008026,0,'national_qualification_level','04','三级','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008027,0,'national_qualification_level','05','四级','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008028,0,'national_qualification_level','06','甲级','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008029,0,'national_qualification_level','07','乙级','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008030,0,'national_qualification_level','08','丙级','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008031,0,'national_qualification_level','09','丁级','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008032,0,'national_qualification_level','10','暂定级（预乙级）','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008033,0,'national_qualification_level','11','初级','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008034,0,'national_qualification_level','12','中级','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008035,0,'national_qualification_level','13','高级','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalQualificationTypeEnum.java → national_qualification_type（49 条）
(710000000000008036,0,'national_qualification_type','010001','一级注册建筑师','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008037,0,'national_qualification_type','010002','二级注册建筑师','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008038,0,'national_qualification_type','010101','一级注册结构工程师','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008039,0,'national_qualification_type','010102','二级注册结构工程师','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008040,0,'national_qualification_type','010201','一级注册建造师','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008041,0,'national_qualification_type','010202','二级注册建造师','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008042,0,'national_qualification_type','010203','一级临时注册建造师','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008043,0,'national_qualification_type','010204','二级临时注册建造师','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008044,0,'national_qualification_type','010301','注册土木工程师（岩土）','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008045,0,'national_qualification_type','010302','注册土木工程师（港口与航道工程）','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008046,0,'national_qualification_type','010303','注册土木工程师（水利水电工程）','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008047,0,'national_qualification_type','010304','注册土木工程师（道路工程）','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008048,0,'national_qualification_type','010401','注册公用设备工程师（暖通空调）','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008049,0,'national_qualification_type','010402','注册公用设备工程师（给水排水）','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008050,0,'national_qualification_type','010403','注册公用设备工程师（动力）','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008051,0,'national_qualification_type','010501','注册电气工程师（发输变电）','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008052,0,'national_qualification_type','010502','注册电气工程师（供配电）','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008053,0,'national_qualification_type','010601','注册化工工程师','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008054,0,'national_qualification_type','010701','注册监理工程师','default',b'0',19,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008055,0,'national_qualification_type','010801','注册造价工程师','default',b'0',20,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008056,0,'national_qualification_type','010802','注册造价师','default',b'0',21,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008057,0,'national_qualification_type','010901','勘察设计注册工程师','default',b'0',22,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008058,0,'national_qualification_type','010902','勘察设计注册石油天然气工程师','default',b'0',23,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008059,0,'national_qualification_type','010903','勘察设计注册采矿/矿物工程师','default',b'0',24,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008060,0,'national_qualification_type','010904','勘察设计注册冶金工程师','default',b'0',25,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008061,0,'national_qualification_type','011001','注册环保工程师','default',b'0',26,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008062,0,'national_qualification_type','011002','注册城市规划师','default',b'0',27,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008063,0,'national_qualification_type','011201','注册安全工程师','default',b'0',28,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008064,0,'national_qualification_type','011301','注册咨询工程师','default',b'0',29,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008065,0,'national_qualification_type','011401','注册房地产估价师','default',b'0',30,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008066,0,'national_qualification_type','011501','注册机械工程师','default',b'0',31,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008067,0,'national_qualification_type','020101','注册会计师','default',b'0',32,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008068,0,'national_qualification_type','030101','注册水利工程师','default',b'0',33,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008069,0,'national_qualification_type','030102','注册水利监理工程师','default',b'0',34,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008070,0,'national_qualification_type','040101','注册测绘师','default',b'0',35,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008071,0,'national_qualification_type','050101','注册核安全工程师','default',b'0',36,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008072,0,'national_qualification_type','050201','注册环评工程师','default',b'0',37,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008073,0,'national_qualification_type','060101','注册税务师','default',b'0',38,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008074,0,'national_qualification_type','070101','注册咨询工程师（投资）','default',b'0',39,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008075,0,'national_qualification_type','080101','国际注册投资分析师','default',b'0',40,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008076,0,'national_qualification_type','090101','注册安全工程师','default',b'0',41,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008077,0,'national_qualification_type','100101','注册设备监理师','default',b'0',42,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008078,0,'national_qualification_type','100301','一级注册计量师','default',b'0',43,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008079,0,'national_qualification_type','100302','二级注册计量师','default',b'0',44,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008080,0,'national_qualification_type','130101','执业药师','default',b'0',45,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008081,0,'national_qualification_type','140101','执业医师','default',b'0',46,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008082,0,'national_qualification_type','140201','执业护士','default',b'0',47,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008083,0,'national_qualification_type','140301','注册营养师','default',b'0',48,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008084,0,'national_qualification_type','150101','执业律师','default',b'0',49,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRestrictedBehavior_119Enum.java → national_restricted_behavior_119（4 条）
(710000000000008085,0,'national_restricted_behavior_119','1','禁止参加招标代理活动','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008086,0,'national_restricted_behavior_119','2','禁止参加投标活动','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008087,0,'national_restricted_behavior_119','3','禁止参加评标、评审活动','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008088,0,'national_restricted_behavior_119','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRestrictedBehavior_125Enum.java → national_restricted_behavior_125（4 条）
(710000000000008089,0,'national_restricted_behavior_125','1','禁止参加招标代理活动','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008090,0,'national_restricted_behavior_125','2','禁止参加投标活动','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008091,0,'national_restricted_behavior_125','3','禁止参加评标活动','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008092,0,'national_restricted_behavior_125','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRestrictedBehavior_138Enum.java → national_restricted_behavior_138（4 条）
(710000000000008093,0,'national_restricted_behavior_138','1','禁止参加招标代理活动','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008094,0,'national_restricted_behavior_138','2','禁止参加投标活动','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008095,0,'national_restricted_behavior_138','3','禁止参加评标活动','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008096,0,'national_restricted_behavior_138','99','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRewardPunishType_118Enum.java → national_reward_punish_type_118（4 条）
(710000000000008097,0,'national_reward_punish_type_118','1','优良评标评审专家','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008098,0,'national_reward_punish_type_118','2','警告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008099,0,'national_reward_punish_type_118','3','撤销专家资格','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008100,0,'national_reward_punish_type_118','9','其他行政处罚或不良行为记录','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalRewardPunishType_136Enum.java → national_reward_punish_type_136（12 条）
(710000000000018101,0,'national_reward_punish_type_136','01','优良招标投标主体','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018102,0,'national_reward_punish_type_136','02','优良交易对象','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018103,0,'national_reward_punish_type_136','03','优良从业人员','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018104,0,'national_reward_punish_type_136','04','警告','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008105,0,'national_reward_punish_type_136','05','罚款','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008106,0,'national_reward_punish_type_136','06','没收非法所得、没收非法财物','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008107,0,'national_reward_punish_type_136','07','责令停产停业','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008108,0,'national_reward_punish_type_136','08','暂扣或吊销许可证、执照','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008109,0,'national_reward_punish_type_136','09','行政拘留','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008110,0,'national_reward_punish_type_136','10','降低资质等级','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018111,0,'national_reward_punish_type_136','11','暂停、取消职业资格证书','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018112,0,'national_reward_punish_type_136','99','其他行政处罚或不良行为记录','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSectionClassifyLevel2_36Enum.java → national_section_classify_level2_36（5 条）
(710000000000018113,0,'national_section_classify_level2_36','A-工程','01 规划；02 投资策划与决策；03 勘察；04 设计；05 监理；06 工程造价；07 项目管理(含代建)；08 工程施工；09 其他工程','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018114,0,'national_section_classify_level2_36','B-货物','01 机电产品类；02 医疗器材；03 金属材料；04 石油及其制品；05 煤炭煤层气及其制品；06 化工材料及其制品；07 建筑材料；08 医药；09 其他类','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018115,0,'national_section_classify_level2_36','C-服务','01 勘查与调查；02 公共咨询；03 经济管理；04 工商管理；05 金融；06 法律；07 运维、评估与修理；08 租赁；09 交通运输与物流；10 节能服务；11 高新技术服务；12 其他服务；13 招标代理服务；14 网络、电磁空间安全服务；99 应对气候变化服务','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018116,0,'national_section_classify_level2_36','D-产权','另行制定','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018117,0,'national_section_classify_level2_36','E-土地','另行制定','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSectionClassifyLevel3_37Enum.java → national_section_classify_level3_37（3 条）
(710000000000018118,0,'national_section_classify_level3_37','C-服务','13 招标代理服务（01 工程类代理；02 货物类代理；03 服务类代理；04 产权类代理；05 土地类代理）','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018119,0,'national_section_classify_level3_37','D-产权','另行制定（另行制定）','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018120,0,'national_section_classify_level3_37','E-土地','另行制定（另行制定）','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSectionClassifyLevel4_38Enum.java → national_section_classify_level4_38（3 条）
(710000000000018121,0,'national_section_classify_level4_38','C-服务','13 招标代理服务','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008122,0,'national_section_classify_level4_38','D-产权','另行制定','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008123,0,'national_section_classify_level4_38','E-土地','另行制定','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalStockEconomyTypeEnum.java → national_stock_economy_type（9 条）
(710000000000008124,0,'national_stock_economy_type','1','政府部门、机构','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008125,0,'national_stock_economy_type','2','国有独资公司（企业）','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008126,0,'national_stock_economy_type','3','国有控股企业','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008127,0,'national_stock_economy_type','4','国有事业单位，国有社团等','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008128,0,'national_stock_economy_type','5','国有参股企业','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008129,0,'national_stock_economy_type','6','非国有企业','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008130,0,'national_stock_economy_type','7','外资企业','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008131,0,'national_stock_economy_type','8','国有实际控制企业','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008132,0,'national_stock_economy_type','99','其他','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalStockReportTypeEnum.java → national_stock_report_type（3 条）
(710000000000008133,0,'national_stock_report_type','1','月报','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008134,0,'national_stock_report_type','2','季报','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008135,0,'national_stock_report_type','3','年报','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalStockTradeModeEnum.java → national_stock_trade_mode（6 条）
(710000000000008136,0,'national_stock_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008137,0,'national_stock_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008138,0,'national_stock_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008139,0,'national_stock_trade_mode','4','协议转让（非竞价）','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008140,0,'national_stock_trade_mode','5','动态报价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008141,0,'national_stock_trade_mode','99','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSubjectCategoryEnum.java → national_subject_category（8 条）
(710000000000008142,0,'national_subject_category','1','自然人','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008143,0,'national_subject_category','2','法人','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008144,0,'national_subject_category','3','联合体','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008145,0,'national_subject_category','4','部队','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008146,0,'national_subject_category','5','港澳企业','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008147,0,'national_subject_category','6','境外企业','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008148,0,'national_subject_category','7','二级单位（非法人）','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008149,0,'national_subject_category','9','其他','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSubjectRoleTypeEnum.java → national_subject_role_type（12 条）
(710000000000008150,0,'national_subject_role_type','01','招标人','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008151,0,'national_subject_role_type','02','招标代理机构','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008152,0,'national_subject_role_type','03','投标人','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008153,0,'national_subject_role_type','04','采购人','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008154,0,'national_subject_role_type','05','采购代理机构','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008155,0,'national_subject_role_type','06','供应商','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008156,0,'national_subject_role_type','07','出让人','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008157,0,'national_subject_role_type','08','受让人','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008158,0,'national_subject_role_type','09','竞得人','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008159,0,'national_subject_role_type','10','意向受让人','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008160,0,'national_subject_role_type','11','意向总得人','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008161,0,'national_subject_role_type','99','其他','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalSupplyModeEnum.java → national_supply_mode（3 条）
(710000000000008162,0,'national_supply_mode','1','协议出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008163,0,'national_supply_mode','2','招拍挂出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008164,0,'national_supply_mode','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTaxCertTypeEnum.java → national_tax_cert_type（3 条）
(710000000000008165,0,'national_tax_cert_type','1','国税','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008166,0,'national_tax_cert_type','2','地税','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008167,0,'national_tax_cert_type','3','国税和地税','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTenderModeEnum.java → national_tender_mode（3 条）
(710000000000008168,0,'national_tender_mode','1','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008169,0,'national_tender_mode','2','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008170,0,'national_tender_mode','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTenderOrganizeFormEnum.java → national_tender_organize_form（3 条）
(710000000000008171,0,'national_tender_organize_form','1','自行招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008172,0,'national_tender_organize_form','2','委托招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008173,0,'national_tender_organize_form','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTradeModeEnum.java → national_trade_mode（12 条）
(710000000000008174,0,'national_trade_mode','01','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008175,0,'national_trade_mode','02','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008176,0,'national_trade_mode','04','竞争性谈判','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008177,0,'national_trade_mode','05','询价采购','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008178,0,'national_trade_mode','06','单一来源采购','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008179,0,'national_trade_mode','07','挂牌','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008180,0,'national_trade_mode','08','拍卖','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008181,0,'national_trade_mode','09','协议转让','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008182,0,'national_trade_mode','10','竞价','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008183,0,'national_trade_mode','11','直接发包','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008184,0,'national_trade_mode','12','竞争性磋商','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008185,0,'national_trade_mode','99','其他','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalTransferModeEnum.java → national_transfer_mode（10 条）
(710000000000008186,0,'national_transfer_mode','131','探矿权招拍挂招标出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008187,0,'national_transfer_mode','132','探矿权招拍挂拍卖出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008188,0,'national_transfer_mode','133','探矿权招拍挂挂牌出让','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008189,0,'national_transfer_mode','210','探矿权转采矿权','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008190,0,'national_transfer_mode','220','采矿权招拍挂招标出让','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008191,0,'national_transfer_mode','230','采矿权招拍挂拍卖出让','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008192,0,'national_transfer_mode','240','采矿权招拍挂挂牌出让','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008193,0,'national_transfer_mode','250','协议出让','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008194,0,'national_transfer_mode','260','申请在先','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008195,0,'national_transfer_mode','999','其他','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalYesNoEnum.java → national_yes_no（2 条）
(710000000000008196,0,'national_yes_no','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008197,0,'national_yes_no','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- NationalYesNoStateOwnedEnum.java → national_yes_no_state_owned（2 条）
(710000000000008198,0,'national_yes_no_state_owned','T','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008199,0,'national_yes_no_state_owned','F','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAbnormalSectionTypeEnum.java → shaanxi_abnormal_section_type（4 条）
(710000000000008200,0,'shaanxi_abnormal_section_type','0','重新招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018201,0,'shaanxi_abnormal_section_type','2','终止招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018202,0,'shaanxi_abnormal_section_type','3','延后变更(变更招标方式)','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018203,0,'shaanxi_abnormal_section_type','5','重新评标','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAbnormalTypeEnum.java → shaanxi_abnormal_type（4 条）
(710000000000018204,0,'shaanxi_abnormal_type','1','现场变更','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008205,0,'shaanxi_abnormal_type','2','重新招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008206,0,'shaanxi_abnormal_type','3','延后变更','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008207,0,'shaanxi_abnormal_type','4','终止招标','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAmountUnit_1Enum.java → shaanxi_amount_unit_1（2 条）
(710000000000008208,0,'shaanxi_amount_unit_1','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008209,0,'shaanxi_amount_unit_1','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAmountUnit_2Enum.java → shaanxi_amount_unit_2（2 条）
(710000000000008210,0,'shaanxi_amount_unit_2','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018211,0,'shaanxi_amount_unit_2','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAmountUnit_56Enum.java → shaanxi_amount_unit_56（2 条）
(710000000000018212,0,'shaanxi_amount_unit_56','0','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018213,0,'shaanxi_amount_unit_56','1','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAssetTypeEnum.java → shaanxi_asset_type（6 条）
(710000000000018214,0,'shaanxi_asset_type','1','房产','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000018215,0,'shaanxi_asset_type','2','土地使用权','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008216,0,'shaanxi_asset_type','3','交通运输工具','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008217,0,'shaanxi_asset_type','4','设备','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008218,0,'shaanxi_asset_type','5','在建工程','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008219,0,'shaanxi_asset_type','99','其他资产','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiAuctionType_3Enum.java → shaanxi_auction_type_3（6 条）
(710000000000008220,0,'shaanxi_auction_type_3','0','招标出让','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008221,0,'shaanxi_auction_type_3','1','挂牌出让','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008222,0,'shaanxi_auction_type_3','2','拍卖出让','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008223,0,'shaanxi_auction_type_3','3','网上挂牌出让','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008224,0,'shaanxi_auction_type_3','4','网上拍卖出让','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008225,0,'shaanxi_auction_type_3','9','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidCurrencyUnit_3Enum.java → shaanxi_bid_currency_unit_3（2 条）
(710000000000008226,0,'shaanxi_bid_currency_unit_3','1','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008227,0,'shaanxi_bid_currency_unit_3','2','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidCurrencyUnit_413Enum.java → shaanxi_bid_currency_unit_413（2 条）
(710000000000008228,0,'shaanxi_bid_currency_unit_413','1','元','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008229,0,'shaanxi_bid_currency_unit_413','2','万元','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidStepTypeEnum.java → shaanxi_bid_step_type（2 条）
(710000000000008230,0,'shaanxi_bid_step_type','1','可变步长','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008231,0,'shaanxi_bid_step_type','2','固定步长','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBidTradeModeEnum.java → shaanxi_bid_trade_mode（6 条）
(710000000000008232,0,'shaanxi_bid_trade_mode','1','招投标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008233,0,'shaanxi_bid_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008234,0,'shaanxi_bid_trade_mode','3','挂牌','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008235,0,'shaanxi_bid_trade_mode','250','协议出让','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008236,0,'shaanxi_bid_trade_mode','260','申请在先','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008237,0,'shaanxi_bid_trade_mode','999','其他','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBiddingSituationEnum.java → shaanxi_bidding_situation（3 条）
(710000000000008238,0,'shaanxi_bidding_situation','1','国有资金控股或占主导地位的依法招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008239,0,'shaanxi_bidding_situation','2','其他情形的依法招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008240,0,'shaanxi_bidding_situation','3','自愿招标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiBusinessScaleEnum.java → shaanxi_business_scale（3 条）
(710000000000008241,0,'shaanxi_business_scale','01','大型','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008242,0,'shaanxi_business_scale','02','中型','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008243,0,'shaanxi_business_scale','03','小型','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiChallengeStageEnum.java → shaanxi_challenge_stage（5 条）
(710000000000008244,0,'shaanxi_challenge_stage','01','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008245,0,'shaanxi_challenge_stage','02','采购文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008246,0,'shaanxi_challenge_stage','03','开标过程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008247,0,'shaanxi_challenge_stage','04','资格预审结果','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008248,0,'shaanxi_challenge_stage','05','评标结果','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiCircuitBreakerTypeEnum.java → shaanxi_circuit_breaker_type（2 条）
(710000000000008249,0,'shaanxi_circuit_breaker_type','0','流标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008250,0,'shaanxi_circuit_breaker_type','1','新指标竞价','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiCommitteeRole_1Enum.java → shaanxi_committee_role_1（3 条）
(710000000000008251,0,'shaanxi_committee_role_1','0','技术标评委','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008252,0,'shaanxi_committee_role_1','1','经济标评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008253,0,'shaanxi_committee_role_1','2','经济技术标评委','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiCommitteeRole_2Enum.java → shaanxi_committee_role_2（3 条）
(710000000000008254,0,'shaanxi_committee_role_2','0','技术标评委','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008255,0,'shaanxi_committee_role_2','1','经济标评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008256,0,'shaanxi_committee_role_2','2','经济技术标评委','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiComplaintMeasureEnum.java → shaanxi_complaint_measure（4 条）
(710000000000008257,0,'shaanxi_complaint_measure','1','项目暂停','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008258,0,'shaanxi_complaint_measure','2','项目终止','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008259,0,'shaanxi_complaint_measure','3','项目流标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008260,0,'shaanxi_complaint_measure','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiComplianceStatusEnum.java → shaanxi_compliance_status（3 条）
(710000000000008261,0,'shaanxi_compliance_status','1','合格','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008262,0,'shaanxi_compliance_status','2','不合格','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008263,0,'shaanxi_compliance_status','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiContinueQuoteAllowedEnum.java → shaanxi_continue_quote_allowed（2 条）
(710000000000008264,0,'shaanxi_continue_quote_allowed','1','允许','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008265,0,'shaanxi_continue_quote_allowed','0','不允许','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiDepositStatus_3Enum.java → shaanxi_deposit_status_3（2 条）
(710000000000008266,0,'shaanxi_deposit_status_3','1','缴纳','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008267,0,'shaanxi_deposit_status_3','0','未缴纳','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiDepositStatus_413Enum.java → shaanxi_deposit_status_413（2 条）
(710000000000008268,0,'shaanxi_deposit_status_413','1','缴纳','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008269,0,'shaanxi_deposit_status_413','0','未缴纳','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiDissentTypeEnum.java → shaanxi_dissent_type（5 条）
(710000000000008270,0,'shaanxi_dissent_type','1','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008271,0,'shaanxi_dissent_type','2','招标文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008272,0,'shaanxi_dissent_type','3','开标过程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008273,0,'shaanxi_dissent_type','4','资格预审结果','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008274,0,'shaanxi_dissent_type','5','评标结果','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEconomyTypeEnum.java → shaanxi_economy_type（9 条）
(710000000000008275,0,'shaanxi_economy_type','1','政府部门、机构','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008276,0,'shaanxi_economy_type','2','国有独资公司（企业）','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008277,0,'shaanxi_economy_type','3','国有控股企业','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008278,0,'shaanxi_economy_type','4','国有事业单位，国有社团等','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008279,0,'shaanxi_economy_type','5','国有参股企业','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008280,0,'shaanxi_economy_type','6','非国有企业','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008281,0,'shaanxi_economy_type','7','外资企业','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008282,0,'shaanxi_economy_type','8','国有实际控制企业','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008283,0,'shaanxi_economy_type','99','其他','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEducationEnum.java → shaanxi_education（7 条）
(710000000000008284,0,'shaanxi_education','博士','博士','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008285,0,'shaanxi_education','硕士','硕士','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008286,0,'shaanxi_education','本科','本科','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008287,0,'shaanxi_education','大专','大专','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008288,0,'shaanxi_education','中专','中专','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008289,0,'shaanxi_education','高中','高中','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008290,0,'shaanxi_education','初中','初中','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEnrollModeEnum.java → shaanxi_enroll_mode（4 条）
(710000000000008291,0,'shaanxi_enroll_mode','1','线上','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008292,0,'shaanxi_enroll_mode','2','代理处','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008293,0,'shaanxi_enroll_mode','3','招标办','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008294,0,'shaanxi_enroll_mode','5','交易中心','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEvalDurationEnum.java → shaanxi_eval_duration（6 条）
(710000000000008295,0,'shaanxi_eval_duration','1','半天','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008296,0,'shaanxi_eval_duration','2','半天或一天','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008297,0,'shaanxi_eval_duration','3','一天','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008298,0,'shaanxi_eval_duration','4','一天或两天','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008299,0,'shaanxi_eval_duration','5','两天','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008300,0,'shaanxi_eval_duration','6','两天以上','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiEvaluationResultEnum.java → shaanxi_evaluation_result（3 条）
(710000000000008301,0,'shaanxi_evaluation_result','1','招标失败','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008302,0,'shaanxi_evaluation_result','2','重新招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008303,0,'shaanxi_evaluation_result','3','评标结果公示','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiExpertTypeEnum.java → shaanxi_expert_type（2 条）
(710000000000008304,0,'shaanxi_expert_type','1','在库专家','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008305,0,'shaanxi_expert_type','2','招标人代表','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiExtractCategoryEnum.java → shaanxi_extract_category（3 条）
(710000000000008306,0,'shaanxi_extract_category','0','本地抽取','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008307,0,'shaanxi_extract_category','1','全省抽取','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008308,0,'shaanxi_extract_category','2','客场抽取','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiExtractLevelEnum.java → shaanxi_extract_level（3 条）
(710000000000008309,0,'shaanxi_extract_level','0','资深评委','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008310,0,'shaanxi_extract_level','1','应急评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008311,0,'shaanxi_extract_level','2','备选库专家','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiFileType_1Enum.java → shaanxi_file_type_1（2 条）
(710000000000008312,0,'shaanxi_file_type_1','1','招标文件/资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008313,0,'shaanxi_file_type_1','2','招标文件澄清与修改/资格预审文件澄清与修改','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiFileType_2Enum.java → shaanxi_file_type_2（2 条）
(710000000000008314,0,'shaanxi_file_type_2','1','采购文件/资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008315,0,'shaanxi_file_type_2','2','采购文件澄清与修改/资格预审文件澄清与修改','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiHandleDecisionEnum.java → shaanxi_handle_decision（4 条）
(710000000000008316,0,'shaanxi_handle_decision','0','重新招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008317,0,'shaanxi_handle_decision','2','终止招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008318,0,'shaanxi_handle_decision','3','延后变更（变更招标方式）','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008319,0,'shaanxi_handle_decision','5','重新评标','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiIdCardTypeEnum.java → shaanxi_id_card_type（27 条）
(710000000000008320,0,'shaanxi_id_card_type','01','居民身份证','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008321,0,'shaanxi_id_card_type','02','军官证','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008322,0,'shaanxi_id_card_type','03','武警警官证','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008323,0,'shaanxi_id_card_type','04','士兵证','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008324,0,'shaanxi_id_card_type','05','军队离退休干部证','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008325,0,'shaanxi_id_card_type','06','残疾人证','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008326,0,'shaanxi_id_card_type','07','残疾军人证（1-8级）','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008327,0,'shaanxi_id_card_type','08','护照','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008328,0,'shaanxi_id_card_type','09','港澳同胞回乡证','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008329,0,'shaanxi_id_card_type','10','港澳居民来往内地通行证','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008330,0,'shaanxi_id_card_type','11','中华人民共和国往来港澳通行证','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008331,0,'shaanxi_id_card_type','12','台湾居民来往大陆通行证','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008332,0,'shaanxi_id_card_type','13','大陆居民往来台湾通行证','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008333,0,'shaanxi_id_card_type','14','外国人居留证','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008334,0,'shaanxi_id_card_type','15','外交官证','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008335,0,'shaanxi_id_card_type','16','领事馆证','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008336,0,'shaanxi_id_card_type','17','海员证','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008337,0,'shaanxi_id_card_type','18','香港身份证','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008338,0,'shaanxi_id_card_type','19','台湾身份证','default',b'0',19,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008339,0,'shaanxi_id_card_type','20','澳门身份证','default',b'0',20,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008340,0,'shaanxi_id_card_type','21','外国人身份证件','default',b'0',21,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008341,0,'shaanxi_id_card_type','22','高校毕业生自主创业证','default',b'0',22,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008342,0,'shaanxi_id_card_type','23','就业失业登记证','default',b'0',23,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008343,0,'shaanxi_id_card_type','24','台胞证','default',b'0',24,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008344,0,'shaanxi_id_card_type','25','退休证','default',b'0',25,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008345,0,'shaanxi_id_card_type','26','离休证','default',b'0',26,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008346,0,'shaanxi_id_card_type','99','其他证件','default',b'0',27,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiInquiryTypeEnum.java → shaanxi_inquiry_type（2 条）
(710000000000008347,0,'shaanxi_inquiry_type','1','资格预审文件','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008348,0,'shaanxi_inquiry_type','2','招标文件','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiInternalDecisionEnum.java → shaanxi_internal_decision（5 条）
(710000000000008349,0,'shaanxi_internal_decision','1','股东会决议','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008350,0,'shaanxi_internal_decision','2','董事会决议','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008351,0,'shaanxi_internal_decision','4','总经理办公会议决议','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008352,0,'shaanxi_internal_decision','5','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008353,0,'shaanxi_internal_decision','6','行政会议决议','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiJudgeTypeEnum.java → shaanxi_judge_type（2 条）
(710000000000008354,0,'shaanxi_judge_type','0','采购人代表','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008355,0,'shaanxi_judge_type','1','邀请评委','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMarginPayTypeEnum.java → shaanxi_margin_pay_type（7 条）
(710000000000008356,0,'shaanxi_margin_pay_type','1','资金现金','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008357,0,'shaanxi_margin_pay_type','2','银行保函','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008358,0,'shaanxi_margin_pay_type','3','担保','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008359,0,'shaanxi_margin_pay_type','4','电汇','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008360,0,'shaanxi_margin_pay_type','5','汇票','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008361,0,'shaanxi_margin_pay_type','6','支票','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008362,0,'shaanxi_margin_pay_type','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMarginPayType_2Enum.java → shaanxi_margin_pay_type_2（7 条）
(710000000000008363,0,'shaanxi_margin_pay_type_2','1','银行转账','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008364,0,'shaanxi_margin_pay_type_2','2','银行保函','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008365,0,'shaanxi_margin_pay_type_2','3','担保','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008366,0,'shaanxi_margin_pay_type_2','4','电汇','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008367,0,'shaanxi_margin_pay_type_2','5','汇票','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008368,0,'shaanxi_margin_pay_type_2','6','支票','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008369,0,'shaanxi_margin_pay_type_2','9','其他','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMiningAreaCategoryEnum.java → shaanxi_mining_area_category（2 条）
(710000000000008370,0,'shaanxi_mining_area_category','01','采矿权','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008371,0,'shaanxi_mining_area_category','02','探矿权','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiMiningMethodEnum.java → shaanxi_mining_method（3 条）
(710000000000008372,0,'shaanxi_mining_method','01','露天开采','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008373,0,'shaanxi_mining_method','02','地下开采','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008374,0,'shaanxi_mining_method','03','地露结合开采','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiNoticeNatureEnum.java → shaanxi_notice_nature（4 条）
(710000000000008375,0,'shaanxi_notice_nature','1','正常公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008376,0,'shaanxi_notice_nature','2','更正公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008377,0,'shaanxi_notice_nature','3','重发公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008378,0,'shaanxi_notice_nature','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiNoticeTypeEnum.java → shaanxi_notice_type（4 条）
(710000000000008379,0,'shaanxi_notice_type','1','招标公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008380,0,'shaanxi_notice_type','2','资格预审公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008381,0,'shaanxi_notice_type','3','中标结果公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008382,0,'shaanxi_notice_type','9','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiNoticeType_2Enum.java → shaanxi_notice_type_2（8 条）
(710000000000008383,0,'shaanxi_notice_type_2','1','采购公告','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008384,0,'shaanxi_notice_type_2','2','资格预审公告','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008385,0,'shaanxi_notice_type_2','3','采购更正公告','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008386,0,'shaanxi_notice_type_2','4','结果公告','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008387,0,'shaanxi_notice_type_2','5','结果更正公告','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008388,0,'shaanxi_notice_type_2','6','中止公告','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008389,0,'shaanxi_notice_type_2','7','终止公告','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008390,0,'shaanxi_notice_type_2','8','其他公告','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPreQualifyMode_2Enum.java → shaanxi_pre_qualify_mode_2（2 条）
(710000000000008391,0,'shaanxi_pre_qualify_mode_2','1','资格预审','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008392,0,'shaanxi_pre_qualify_mode_2','2','资格后审','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPreQualifyResult_1Enum.java → shaanxi_pre_qualify_result_1（2 条）
(710000000000008393,0,'shaanxi_pre_qualify_result_1','1','资审通过','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008394,0,'shaanxi_pre_qualify_result_1','2','资审不通过','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPreQualifyResult_2Enum.java → shaanxi_pre_qualify_result_2（2 条）
(710000000000008395,0,'shaanxi_pre_qualify_result_2','1','资审通过','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008396,0,'shaanxi_pre_qualify_result_2','2','资审不通过','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPriceFormEnum.java → shaanxi_price_form（4 条）
(710000000000008397,0,'shaanxi_price_form','1','金额','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008398,0,'shaanxi_price_form','2','费率/比率/优惠率/合格率等','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008399,0,'shaanxi_price_form','3','单价','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008400,0,'shaanxi_price_form','9','其他形式','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPriceForm_2Enum.java → shaanxi_price_form_2（4 条）
(710000000000008401,0,'shaanxi_price_form_2','1','金额','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008402,0,'shaanxi_price_form_2','2','费率/比率/优惠率/合格率等','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008403,0,'shaanxi_price_form_2','3','单价','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008404,0,'shaanxi_price_form_2','9','其他形式','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiProfessionalQualificationEnum.java → shaanxi_professional_qualification（12 条）
(710000000000008405,0,'shaanxi_professional_qualification','01','注册建筑师','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008406,0,'shaanxi_professional_qualification','02','注册结构工程师','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008407,0,'shaanxi_professional_qualification','03','注册工程师','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008408,0,'shaanxi_professional_qualification','04','注册建造师','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008409,0,'shaanxi_professional_qualification','05','注册监理工程师','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008410,0,'shaanxi_professional_qualification','06','注册造价工程师','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008411,0,'shaanxi_professional_qualification','07','造价工程师','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008412,0,'shaanxi_professional_qualification','08','注册结构工程师','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008413,0,'shaanxi_professional_qualification','09','注册土木工程师','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008414,0,'shaanxi_professional_qualification','10','监理工程师','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008415,0,'shaanxi_professional_qualification','11','施工员','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008416,0,'shaanxi_professional_qualification','12','全国公路水运工程试验检测专业技术人员','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiProjectTradeCategoryEnum.java → shaanxi_project_trade_category（12 条）
(710000000000008417,0,'shaanxi_project_trade_category','A','房屋建筑工程','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008418,0,'shaanxi_project_trade_category','B','市政基础设施工程','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008419,0,'shaanxi_project_trade_category','GD','轨道交通工程','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008420,0,'shaanxi_project_trade_category','F','水利工程','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008421,0,'shaanxi_project_trade_category','L','电力工程','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008422,0,'shaanxi_project_trade_category','E','公路工程','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008423,0,'shaanxi_project_trade_category','D','园林绿化','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008424,0,'shaanxi_project_trade_category','Y','其他工程','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008425,0,'shaanxi_project_trade_category','TL','铁路工程','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008426,0,'shaanxi_project_trade_category','TX','通讯工程','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008427,0,'shaanxi_project_trade_category','GLYH','公路养护','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008428,0,'shaanxi_project_trade_category','WZB','外资办','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPropertyTradeModeEnum.java → shaanxi_property_trade_mode（4 条）
(710000000000008429,0,'shaanxi_property_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008430,0,'shaanxi_property_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008431,0,'shaanxi_property_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008432,0,'shaanxi_property_trade_mode','99','其他','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPublicityTypeEnum.java → shaanxi_publicity_type（3 条）
(710000000000008433,0,'shaanxi_publicity_type','1','正常','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008434,0,'shaanxi_publicity_type','2','更正','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008435,0,'shaanxi_publicity_type','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchaseMode_2Enum.java → shaanxi_purchase_mode_2（7 条）
(710000000000008436,0,'shaanxi_purchase_mode_2','公','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008437,0,'shaanxi_purchase_mode_2','邀','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008438,0,'shaanxi_purchase_mode_2','竞','竞争性谈判','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008439,0,'shaanxi_purchase_mode_2','单','单一来源采购','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008440,0,'shaanxi_purchase_mode_2','询','询价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008441,0,'shaanxi_purchase_mode_2','磋','竞争性磋商','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008442,0,'shaanxi_purchase_mode_2','框','框架协议采购','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchaseOrganizeForm_2Enum.java → shaanxi_purchase_organize_form_2（3 条）
(710000000000008443,0,'shaanxi_purchase_organize_form_2','1','集中采购','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008444,0,'shaanxi_purchase_organize_form_2','2','分散采购','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008445,0,'shaanxi_purchase_organize_form_2','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchasePartyTypeEnum.java → shaanxi_purchase_party_type（8 条）
(710000000000008446,0,'shaanxi_purchase_party_type','1','采购人','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008447,0,'shaanxi_purchase_party_type','2','采购代理机构','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008448,0,'shaanxi_purchase_party_type','21','集中采购机构','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008449,0,'shaanxi_purchase_party_type','22','部门集中采购机构','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008450,0,'shaanxi_purchase_party_type','23','政府采购代理机构','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008451,0,'shaanxi_purchase_party_type','3','评审专家','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008452,0,'shaanxi_purchase_party_type','4','供应商','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008453,0,'shaanxi_purchase_party_type','9','其他','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiPurchaseTradeCategoryEnum.java → shaanxi_purchase_trade_category（3 条）
(710000000000008454,0,'shaanxi_purchase_trade_category','D01','货物类（含药品集中采购）','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008455,0,'shaanxi_purchase_trade_category','D02','工程类','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008456,0,'shaanxi_purchase_trade_category','D03','服务类','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiQualifyCheckModeEnum.java → shaanxi_qualify_check_mode（2 条）
(710000000000008457,0,'shaanxi_qualify_check_mode','1','资格预审','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008458,0,'shaanxi_qualify_check_mode','2','资格后审','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiQualityLevelEnum.java → shaanxi_quality_level（7 条）
(710000000000008459,0,'shaanxi_quality_level','1','合格','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008460,0,'shaanxi_quality_level','2','市优','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008461,0,'shaanxi_quality_level','3','省优','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008462,0,'shaanxi_quality_level','4','国优','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008463,0,'shaanxi_quality_level','5','优良','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008464,0,'shaanxi_quality_level','6','符合国家验收标准','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008465,0,'shaanxi_quality_level','7','满足国标合格条件','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiQuoteFormEnum.java → shaanxi_quote_form（2 条）
(710000000000008466,0,'shaanxi_quote_form','1','总价报价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008467,0,'shaanxi_quote_form','2','费率报价','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionCategoryEnum.java → shaanxi_section_category（18 条）
(710000000000008468,0,'shaanxi_section_category','A','货物类','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008469,0,'shaanxi_section_category','A03','一般设备','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008470,0,'shaanxi_section_category','A0301','电器设备','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008471,0,'shaanxi_section_category','A0302','办公自动化设备','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008472,0,'shaanxi_section_category','A0303','家俱','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008473,0,'shaanxi_section_category','A06','物资','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008474,0,'shaanxi_section_category','A07','专用材料','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008475,0,'shaanxi_section_category','A10','专用设备','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008476,0,'shaanxi_section_category','A11','交通工具及配件','default',b'0',9,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008477,0,'shaanxi_section_category','B','工程类','default',b'0',10,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008478,0,'shaanxi_section_category','B01','建筑工程','default',b'0',11,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008479,0,'shaanxi_section_category','B02','市政建设工程','default',b'0',12,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008480,0,'shaanxi_section_category','C','服务类','default',b'0',13,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008481,0,'shaanxi_section_category','C01','印刷','default',b'0',14,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008482,0,'shaanxi_section_category','C02','办公用纸','default',b'0',15,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008483,0,'shaanxi_section_category','C03','软件开发设计','default',b'0',16,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008484,0,'shaanxi_section_category','C04','网络建设或维护及系统集成','default',b'0',17,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008485,0,'shaanxi_section_category','C05','其他服务','default',b'0',18,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionClassifyLevel2_1Enum.java → shaanxi_section_classify_level2_1（5 条）
(710000000000008486,0,'shaanxi_section_classify_level2_1','A-工程','01 规划；02 投资策划与决策；03 勘察；04 设计；05 监理；06 工程造价；07 项目管理(含代建)；08 工程施工；09 其他工程；10 房建；11 交通组别；12 交通标段；13 水利','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008487,0,'shaanxi_section_classify_level2_1','B-货物','01 机电产品类；02 医疗器材；03 金属材料；04 石油及其制品；05 煤炭煤层气及其制品；06 化工材料及其制品；07 建筑材料；08 医药；09其他类','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008488,0,'shaanxi_section_classify_level2_1','C-服务','01 勘查与调查；02 公共咨询；03 经济管理；04 工商管理；05 金融；06 法律；07 运维、评估与修理；08 租赁；09 交通运输与物流；10 节能服务；11 高新技术服务；12 其他服务；13招标代理服务；14网络、电磁空间安全服务；99应对气候变化服务','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008489,0,'shaanxi_section_classify_level2_1','D-产权','另行制定','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008490,0,'shaanxi_section_classify_level2_1','E-土地','另行制定','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionClassifyLevel3_1Enum.java → shaanxi_section_classify_level3_1（4 条）
(710000000000008491,0,'shaanxi_section_classify_level3_1','A-工程','10 房建市政；11 交通组别；12 交通标段；13 水利','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008492,0,'shaanxi_section_classify_level3_1','C-服务','13 招标代理服务；','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008493,0,'shaanxi_section_classify_level3_1','D-产权','另行制定','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008494,0,'shaanxi_section_classify_level3_1','E-土地','另行制定','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiSectionClassifyLevel4_1Enum.java → shaanxi_section_classify_level4_1（3 条）
(710000000000008495,0,'shaanxi_section_classify_level4_1','C-服务','13 招标代理服务','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008496,0,'shaanxi_section_classify_level4_1','D-产权','另行制定','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008497,0,'shaanxi_section_classify_level4_1','E-土地','另行制定','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiTenderModeEnum.java → shaanxi_tender_mode（3 条）
(710000000000008498,0,'shaanxi_tender_mode','1','公开招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008499,0,'shaanxi_tender_mode','2','邀请招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008500,0,'shaanxi_tender_mode','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiTenderOrganizeFormEnum.java → shaanxi_tender_organize_form（3 条）
(710000000000008501,0,'shaanxi_tender_organize_form','1','自行招标','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008502,0,'shaanxi_tender_organize_form','2','委托招标','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008503,0,'shaanxi_tender_organize_form','9','其他','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiTradeModeEnum.java → shaanxi_trade_mode（8 条）
(710000000000008504,0,'shaanxi_trade_mode','1','网络竞价','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008505,0,'shaanxi_trade_mode','2','拍卖','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008506,0,'shaanxi_trade_mode','3','招投标','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008507,0,'shaanxi_trade_mode','4','协议转让（非竞价）','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008508,0,'shaanxi_trade_mode','5','动态报价','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008509,0,'shaanxi_trade_mode','6','谈判','default',b'0',6,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008510,0,'shaanxi_trade_mode','7','综合评议','default',b'0',7,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008511,0,'shaanxi_trade_mode','99','其他','default',b'0',8,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiVenueTypeEnum.java → shaanxi_venue_type（5 条）
(710000000000008512,0,'shaanxi_venue_type','1','资格审查评审室','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008513,0,'shaanxi_venue_type','2','开标室','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008514,0,'shaanxi_venue_type','3','评标室','default',b'0',3,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008515,0,'shaanxi_venue_type','4','抽签室','default',b'0',4,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008516,0,'shaanxi_venue_type','5','不见面开标室','default',b'0',5,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_1Enum.java → shaanxi_yes_no_1（2 条）
(710000000000008517,0,'shaanxi_yes_no_1','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008518,0,'shaanxi_yes_no_1','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_2Enum.java → shaanxi_yes_no_2（2 条）
(710000000000008519,0,'shaanxi_yes_no_2','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008520,0,'shaanxi_yes_no_2','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_3Enum.java → shaanxi_yes_no_3（2 条）
(710000000000008521,0,'shaanxi_yes_no_3','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008522,0,'shaanxi_yes_no_3','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_413Enum.java → shaanxi_yes_no_413（2 条）
(710000000000008523,0,'shaanxi_yes_no_413','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008524,0,'shaanxi_yes_no_413','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- ShaanxiYesNo_56Enum.java → shaanxi_yes_no_56（2 条）
(710000000000008525,0,'shaanxi_yes_no_56','1','是','default',b'0',1,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(710000000000008526,0,'shaanxi_yes_no_56','0','否','default',b'0',2,b'1','规范照抄枚举已下沉字典（核查结论③整改）；出处：《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-28-cert-alert-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 证书预警两类字典注册（alert_level / alert_type）
--
-- 规范依据：《招投标模块开发规范》用户硬约束"枚举/阈值一律动态配置，前端不得硬编码中文与颜色"；
--           门禁 sql/tools/check-dict-permission.js「字典覆盖率」——前端引用的 dict_type 必须已注册。
-- 前端消费：apps/web-antd/src/views/bidding/certificate/index.vue
--           dictOptions('alert_type') / dictLabel('alert_level', record.alertLevel) / dictLabel('alert_type', record.alertType)。
-- 后端同源：CertificateAlertLevelEnum（INFO/WARNING/CRITICAL）、CertificateAlertTypeEnum（EXPIRY/REVIEW/RENEWAL/QUAL_EXPIRY）；
--           档位由 cert_alert_config.thresholds 推导（v3.3 §九），QUAL_EXPIRY 对应 cert_type IS NULL 的资质类到期行。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 镜像：同批内容已并入 sql/master-credential-sync.sql（一键包），重建新库不会缺这两类字典。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- id 用 2906~912：2901~905 已是 risk_item_status（2026-09-28-safety-risk-item.sql），2801~803 是 material_result，撞号会被 INSERT IGNORE 静默跳过
-- alert_level（3 条）
(710000000000002906,0,'alert_level','INFO','提示','info',b'0',1,b'1','提前量首档（v3.3 §九阈值档位）','1',NOW(),'1',NOW(),b'0'),
(710000000000002907,0,'alert_level','WARNING','警告','warning',b'0',2,b'1','中档提醒','1',NOW(),'1',NOW(),b'0'),
(710000000000002908,0,'alert_level','CRITICAL','严重','error',b'0',3,b'1','临期/逾期高档提醒','1',NOW(),'1',NOW(),b'0'),
-- alert_type（4 条）
(710000000000002909,0,'alert_type','EXPIRY','到期预警','warning',b'0',1,b'1','valid_to 到期（v3.3 §九）','1',NOW(),'1',NOW(),b'0'),
(710000000000002910,0,'alert_type','REVIEW','年检预警','info',b'0',2,b'1','next_review_date 年检','1',NOW(),'1',NOW(),b'0'),
(710000000000002911,0,'alert_type','RENEWAL','续期预警','processing',b'0',3,b'1','续期窗口提醒','1',NOW(),'1',NOW(),b'0'),
(710000000000002912,0,'alert_type','QUAL_EXPIRY','资质到期预警','warning',b'0',4,b'1','企业资质/安许/人员注册到期（v3.3 §十五）','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-28-national-mineral-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- ---------------------------------------------------------------------
-- 国家矿产种类代码下沉业务字典（bidding_dict_data）
-- 整改依据：用户核查结论③：规范照抄枚举数据改为数据库字典，禁止 Java 常量堆积
-- 出处：《VO 数据对象规范》第十章（国家表73 矿产种类代码表）
-- 幂等：INSERT IGNORE；类型经 MetaDictServiceImpl 按字典表数据自动支持（/bidding/meta/dicts）
-- id 分块：7100000000000076xx（矿产 263 条）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 国家矿产种类代码（263 条）：dict_value=矿产代码，dict_label=矿产名称，储量单位保留在 remark
(7100000000000076001,0,'national_mineral_type','11001','煤','default',b'0',1,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076002,0,'national_mineral_type','11002','油页岩','default',b'0',2,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076003,0,'national_mineral_type','11003','石油','default',b'0',3,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076004,0,'national_mineral_type','11004','天然气','default',b'0',4,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076005,0,'national_mineral_type','11005','煤层气','default',b'0',5,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076006,0,'national_mineral_type','11006','石煤','default',b'0',6,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076007,0,'national_mineral_type','11008','油砂','default',b'0',7,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076008,0,'national_mineral_type','11034','石油天然气','default',b'0',8,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076009,0,'national_mineral_type','11041','页岩气','default',b'0',9,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076010,0,'national_mineral_type','12712','铀','default',b'0',10,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076011,0,'national_mineral_type','12713','钍','default',b'0',11,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076012,0,'national_mineral_type','14970','天然沥青','default',b'0',12,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076013,0,'national_mineral_type','17050','地热','default',b'0',13,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076014,0,'national_mineral_type','22001','铁矿','default',b'0',14,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076015,0,'national_mineral_type','22002','锰矿','default',b'0',15,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076016,0,'national_mineral_type','22003','铬铁矿','default',b'0',16,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076017,0,'national_mineral_type','22004','钛矿','default',b'0',17,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076018,0,'national_mineral_type','22005','钒矿','default',b'0',18,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076019,0,'national_mineral_type','22006','金红石','default',b'0',19,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076020,0,'national_mineral_type','32006','铜矿','default',b'0',20,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076021,0,'national_mineral_type','32007','铅矿','default',b'0',21,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076022,0,'national_mineral_type','32008','锌矿','default',b'0',22,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076023,0,'national_mineral_type','32009','铝土矿','default',b'0',23,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076024,0,'national_mineral_type','32011','镁矿','default',b'0',24,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076025,0,'national_mineral_type','32012','镍矿','default',b'0',25,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076026,0,'national_mineral_type','32013','钴矿','default',b'0',26,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076027,0,'national_mineral_type','32014','钨矿','default',b'0',27,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076028,0,'national_mineral_type','32015','锡矿','default',b'0',28,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076029,0,'national_mineral_type','32016','铋矿','default',b'0',29,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076030,0,'national_mineral_type','32017','钼矿','default',b'0',30,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076031,0,'national_mineral_type','32018','汞矿','default',b'0',31,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076032,0,'national_mineral_type','32019','锑矿','default',b'0',32,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076033,0,'national_mineral_type','32020','多金属','default',b'0',33,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076034,0,'national_mineral_type','42101','铂矿','default',b'0',34,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076035,0,'national_mineral_type','42102','钯矿','default',b'0',35,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076036,0,'national_mineral_type','42103','铱矿','default',b'0',36,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076037,0,'national_mineral_type','42104','铑矿','default',b'0',37,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076038,0,'national_mineral_type','42105','锇矿','default',b'0',38,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076039,0,'national_mineral_type','42106','钌矿','default',b'0',39,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076040,0,'national_mineral_type','42200','砂金','default',b'0',40,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076041,0,'national_mineral_type','42201','金矿','default',b'0',41,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076042,0,'national_mineral_type','42202','银矿','default',b'0',42,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076043,0,'national_mineral_type','52300','铌钽矿','default',b'0',43,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076044,0,'national_mineral_type','52301','铌矿','default',b'0',44,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076045,0,'national_mineral_type','52302','钽矿','default',b'0',45,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076046,0,'national_mineral_type','52401','铍矿','default',b'0',46,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076047,0,'national_mineral_type','52402','锂矿','default',b'0',47,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076048,0,'national_mineral_type','52403','锆矿','default',b'0',48,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076049,0,'national_mineral_type','52404','锶矿(天青石)','default',b'0',49,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076050,0,'national_mineral_type','52405','铷矿','default',b'0',50,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076051,0,'national_mineral_type','52406','铯矿','default',b'0',51,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076052,0,'national_mineral_type','52500','重稀土矿','default',b'0',52,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076053,0,'national_mineral_type','52501','钇矿','default',b'0',53,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076054,0,'national_mineral_type','52502','钆矿','default',b'0',54,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076055,0,'national_mineral_type','52503','铽矿','default',b'0',55,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076056,0,'national_mineral_type','52504','镝矿','default',b'0',56,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076057,0,'national_mineral_type','52505','钬矿','default',b'0',57,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076058,0,'national_mineral_type','52506','铒矿','default',b'0',58,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076059,0,'national_mineral_type','52507','铥矿','default',b'0',59,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076060,0,'national_mineral_type','52508','镱矿','default',b'0',60,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076061,0,'national_mineral_type','52509','镥矿','default',b'0',61,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076062,0,'national_mineral_type','52600','轻稀土矿','default',b'0',62,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076063,0,'national_mineral_type','52601','铈矿','default',b'0',63,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076064,0,'national_mineral_type','52602','镧矿','default',b'0',64,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076065,0,'national_mineral_type','52603','镨矿','default',b'0',65,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076066,0,'national_mineral_type','52604','钕矿','default',b'0',66,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076067,0,'national_mineral_type','52605','钐矿','default',b'0',67,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076068,0,'national_mineral_type','52606','铕矿','default',b'0',68,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076069,0,'national_mineral_type','52701','锗矿','default',b'0',69,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076070,0,'national_mineral_type','52702','镓矿','default',b'0',70,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076071,0,'national_mineral_type','52703','铟矿','default',b'0',71,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076072,0,'national_mineral_type','52704','铊矿','default',b'0',72,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076073,0,'national_mineral_type','52705','铪矿','default',b'0',73,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076074,0,'national_mineral_type','52706','铼矿','default',b'0',74,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076075,0,'national_mineral_type','52707','镉矿','default',b'0',75,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076076,0,'national_mineral_type','52708','钪矿','default',b'0',76,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076077,0,'national_mineral_type','52709','硒矿','default',b'0',77,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076078,0,'national_mineral_type','52711','碲矿','default',b'0',78,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076079,0,'national_mineral_type','63200','蓝晶石','default',b'0',79,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076080,0,'national_mineral_type','63210','矽线石','default',b'0',80,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076081,0,'national_mineral_type','63220','红柱石','default',b'0',81,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076082,0,'national_mineral_type','63640','菱镁矿','default',b'0',82,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076083,0,'national_mineral_type','63701','萤石(普通)','default',b'0',83,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076084,0,'national_mineral_type','63904','熔剂用石灰岩','default',b'0',84,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076085,0,'national_mineral_type','63941','冶金用白云岩','default',b'0',85,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076086,0,'national_mineral_type','63951','冶金用石英岩','default',b'0',86,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076087,0,'national_mineral_type','63971','冶金用砂岩','default',b'0',87,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076088,0,'national_mineral_type','63976','铸型用砂岩','default',b'0',88,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076089,0,'national_mineral_type','63992','铸型用砂','default',b'0',89,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076090,0,'national_mineral_type','64031','冶金用脉石英','default',b'0',90,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076091,0,'national_mineral_type','64190','耐火粘土','default',b'0',91,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076092,0,'national_mineral_type','64310','铁钒土','default',b'0',92,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076093,0,'national_mineral_type','64410','其它粘土','default',b'0',93,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076094,0,'national_mineral_type','64411','铸型用粘土','default',b'0',94,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076095,0,'national_mineral_type','64511','耐火用橄榄岩','default',b'0',95,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076096,0,'national_mineral_type','64531','熔剂用蛇纹岩','default',b'0',96,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076097,0,'national_mineral_type','73030','自然硫','default',b'0',97,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076098,0,'national_mineral_type','73070','硫铁矿','default',b'0',98,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076099,0,'national_mineral_type','73240','钠硝石','default',b'0',99,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076100,0,'national_mineral_type','73500','明矾石','default',b'0',100,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076101,0,'national_mineral_type','73510','芒硝(含钙芒硝)','default',b'0',101,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076102,0,'national_mineral_type','73530','重晶石','default',b'0',102,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076103,0,'national_mineral_type','73600','毒重石','default',b'0',103,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076104,0,'national_mineral_type','73610','天然碱(Na2CO3)','default',b'0',104,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076105,0,'national_mineral_type','73860','赭石','default',b'0',105,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076106,0,'national_mineral_type','73870','颜料矿物','default',b'0',106,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076107,0,'national_mineral_type','73880','颜料黄土','default',b'0',107,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076108,0,'national_mineral_type','73901','电石用灰岩','default',b'0',108,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076109,0,'national_mineral_type','73902','制碱用灰岩','default',b'0',109,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076110,0,'national_mineral_type','73903','化肥用石灰岩','default',b'0',110,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076111,0,'national_mineral_type','73942','化肥用白云岩','default',b'0',111,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076112,0,'national_mineral_type','73953','化肥用石英岩','default',b'0',112,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076113,0,'national_mineral_type','73975','化肥用砂岩','default',b'0',113,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076114,0,'national_mineral_type','74080','含钾岩石','default',b'0',114,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076115,0,'national_mineral_type','74090','含钾砂页岩','default',b'0',115,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076116,0,'national_mineral_type','74512','化肥用橄榄岩','default',b'0',116,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076117,0,'national_mineral_type','74532','化肥用蛇纹岩','default',b'0',117,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076118,0,'national_mineral_type','74950','泥炭','default',b'0',118,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076119,0,'national_mineral_type','75510','矿盐','default',b'0',119,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076120,0,'national_mineral_type','75511','岩盐','default',b'0',120,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076121,0,'national_mineral_type','75512','湖盐','default',b'0',121,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076122,0,'national_mineral_type','75530','镁盐','default',b'0',122,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076123,0,'national_mineral_type','75540','天然卤水','default',b'0',123,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076124,0,'national_mineral_type','75550','钾盐','default',b'0',124,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076125,0,'national_mineral_type','75610','碘','default',b'0',125,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076126,0,'national_mineral_type','75630','溴','default',b'0',126,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076127,0,'national_mineral_type','75650','砷','default',b'0',127,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076128,0,'national_mineral_type','75690','磷矿','default',b'0',128,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076129,0,'national_mineral_type','83010','金刚石','default',b'0',129,b'1','矿产种类代码（国家表73）；储量单位：万克拉/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076130,0,'national_mineral_type','83020','石墨','default',b'0',130,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076131,0,'national_mineral_type','83100','水晶','default',b'0',131,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076132,0,'national_mineral_type','83101','压电水晶','default',b'0',132,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076133,0,'national_mineral_type','83102','熔炼水晶','default',b'0',133,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076134,0,'national_mineral_type','83103','光学水晶','default',b'0',134,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076135,0,'national_mineral_type','83104','工艺水晶','default',b'0',135,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076136,0,'national_mineral_type','83110','刚玉','default',b'0',136,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076137,0,'national_mineral_type','83230','硅灰石','default',b'0',137,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076138,0,'national_mineral_type','83250','滑石','default',b'0',138,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076139,0,'national_mineral_type','83260','石棉(温石棉)','default',b'0',139,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076140,0,'national_mineral_type','83270','蓝石棉','default',b'0',140,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076141,0,'national_mineral_type','83280','云母','default',b'0',141,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076142,0,'national_mineral_type','83290','长石','default',b'0',142,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076143,0,'national_mineral_type','83300','电气石','default',b'0',143,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076144,0,'national_mineral_type','83310','石榴子石','default',b'0',144,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076145,0,'national_mineral_type','83320','黄玉','default',b'0',145,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076146,0,'national_mineral_type','83330','叶腊石','default',b'0',146,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076147,0,'national_mineral_type','83340','透辉石','default',b'0',147,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076148,0,'national_mineral_type','83350','蛭石','default',b'0',148,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076149,0,'national_mineral_type','83360','沸石','default',b'0',149,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076150,0,'national_mineral_type','83370','透闪石','default',b'0',150,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076151,0,'national_mineral_type','83520','石膏','default',b'0',151,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076152,0,'national_mineral_type','83620','方解石','default',b'0',152,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076153,0,'national_mineral_type','83630','冰洲石','default',b'0',153,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076154,0,'national_mineral_type','83702','光学萤石','default',b'0',154,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076155,0,'national_mineral_type','83750','宝石','default',b'0',155,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076156,0,'national_mineral_type','83800','玉石','default',b'0',156,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076157,0,'national_mineral_type','83850','玛瑙','default',b'0',157,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076158,0,'national_mineral_type','83900','石灰岩','default',b'0',158,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076159,0,'national_mineral_type','83905','玻璃用石灰岩','default',b'0',159,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076160,0,'national_mineral_type','83906','水泥用石灰岩','default',b'0',160,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076161,0,'national_mineral_type','83907','建筑石料用灰岩','default',b'0',161,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076162,0,'national_mineral_type','83908','饰面用灰岩','default',b'0',162,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076163,0,'national_mineral_type','83909','制灰用石灰岩','default',b'0',163,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076164,0,'national_mineral_type','83920','泥灰岩','default',b'0',164,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076165,0,'national_mineral_type','83930','白垩','default',b'0',165,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076166,0,'national_mineral_type','83940','白云岩','default',b'0',166,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076167,0,'national_mineral_type','83943','玻璃用白云岩','default',b'0',167,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076168,0,'national_mineral_type','83945','建筑用白云岩','default',b'0',168,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076169,0,'national_mineral_type','83950','石英岩','default',b'0',169,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076170,0,'national_mineral_type','83952','玻璃用石英岩','default',b'0',170,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076171,0,'national_mineral_type','83970','砂岩','default',b'0',171,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076172,0,'national_mineral_type','83972','玻璃用砂岩','default',b'0',172,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076173,0,'national_mineral_type','83973','水泥配料用砂岩','default',b'0',173,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076174,0,'national_mineral_type','83974','砖瓦用砂岩','default',b'0',174,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076175,0,'national_mineral_type','83977','陶瓷用砂岩','default',b'0',175,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076176,0,'national_mineral_type','83978','建筑用砂岩','default',b'0',176,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076177,0,'national_mineral_type','83990','天然石英砂','default',b'0',177,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076178,0,'national_mineral_type','83991','玻璃用砂','default',b'0',178,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076179,0,'national_mineral_type','83992','海砂','default',b'0',179,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076180,0,'national_mineral_type','83993','建筑用砂','default',b'0',180,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076181,0,'national_mineral_type','83994','水泥配料用砂','default',b'0',181,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076182,0,'national_mineral_type','83995','水泥标准砂','default',b'0',182,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076183,0,'national_mineral_type','83996','砖瓦用砂','default',b'0',183,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076184,0,'national_mineral_type','84030','脉石英','default',b'0',184,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076185,0,'national_mineral_type','84032','玻璃用脉石英','default',b'0',185,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076186,0,'national_mineral_type','84050','粉石英','default',b'0',186,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076187,0,'national_mineral_type','84070','天然油石','default',b'0',187,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076188,0,'national_mineral_type','84110','硅藻土','default',b'0',188,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076189,0,'national_mineral_type','84130','页岩','default',b'0',189,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076190,0,'national_mineral_type','84131','陶粒页岩','default',b'0',190,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076191,0,'national_mineral_type','84132','砖瓦用页岩','default',b'0',191,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076192,0,'national_mineral_type','84133','水泥配料用页岩','default',b'0',192,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076193,0,'national_mineral_type','84150','高岭土','default',b'0',193,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076194,0,'national_mineral_type','84170','陶瓷土','default',b'0',194,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076195,0,'national_mineral_type','84210','凹凸棒石粘土','default',b'0',195,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076196,0,'national_mineral_type','84230','海泡石粘土','default',b'0',196,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076197,0,'national_mineral_type','84250','伊利石粘土','default',b'0',197,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076198,0,'national_mineral_type','84270','累托石粘土','default',b'0',198,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076199,0,'national_mineral_type','84290','膨润土','default',b'0',199,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076200,0,'national_mineral_type','84412','砖瓦用粘土','default',b'0',200,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076201,0,'national_mineral_type','84413','陶粒用粘土','default',b'0',201,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076202,0,'national_mineral_type','84414','水泥用粘土','default',b'0',202,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076203,0,'national_mineral_type','84415','水泥配料用红土','default',b'0',203,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076204,0,'national_mineral_type','84416','水泥配料用黄土','default',b'0',204,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076205,0,'national_mineral_type','84417','水泥配料用泥岩','default',b'0',205,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076206,0,'national_mineral_type','84418','保温材料用粘土','default',b'0',206,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076207,0,'national_mineral_type','84510','橄榄岩','default',b'0',207,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076208,0,'national_mineral_type','84513','建筑用橄榄岩','default',b'0',208,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076209,0,'national_mineral_type','84530','蛇纹岩','default',b'0',209,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076210,0,'national_mineral_type','84533','饰面用蛇纹岩','default',b'0',210,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076211,0,'national_mineral_type','84550','玄武岩','default',b'0',211,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076212,0,'national_mineral_type','84551','铸石用玄武岩','default',b'0',212,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076213,0,'national_mineral_type','84552','岩棉用玄武岩','default',b'0',213,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076214,0,'national_mineral_type','84555','建筑用玄武岩','default',b'0',214,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076215,0,'national_mineral_type','84570','辉绿岩','default',b'0',215,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076216,0,'national_mineral_type','84571','水泥用辉绿岩','default',b'0',216,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076217,0,'national_mineral_type','84572','铸石用辉绿岩','default',b'0',217,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076218,0,'national_mineral_type','84573','建筑用辉绿岩','default',b'0',218,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076219,0,'national_mineral_type','84574','饰面用辉绿岩','default',b'0',219,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076220,0,'national_mineral_type','84590','安山岩','default',b'0',220,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076221,0,'national_mineral_type','84591','饰面用安山岩','default',b'0',221,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076222,0,'national_mineral_type','84592','建筑用安山岩','default',b'0',222,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076223,0,'national_mineral_type','84593','水泥混合材料用安山岩','default',b'0',223,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076224,0,'national_mineral_type','84610','闪长岩','default',b'0',224,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076225,0,'national_mineral_type','84611','建筑用闪长岩','default',b'0',225,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076226,0,'national_mineral_type','84612','水泥混合材料用闪长玢岩','default',b'0',226,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076227,0,'national_mineral_type','84710','花岗岩','default',b'0',227,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076228,0,'national_mineral_type','84711','建筑用花岗岩','default',b'0',228,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076229,0,'national_mineral_type','84712','饰面用花岗岩','default',b'0',229,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076230,0,'national_mineral_type','84720','麦饭石','default',b'0',230,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076231,0,'national_mineral_type','84730','珍珠岩','default',b'0',231,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076232,0,'national_mineral_type','84750','黑曜岩','default',b'0',232,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076233,0,'national_mineral_type','84770','松脂岩','default',b'0',233,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076234,0,'national_mineral_type','84790','浮石','default',b'0',234,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076235,0,'national_mineral_type','84810','粗面岩','default',b'0',235,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076236,0,'national_mineral_type','84811','水泥用粗面岩','default',b'0',236,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076237,0,'national_mineral_type','84812','铸石用粗面岩','default',b'0',237,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076238,0,'national_mineral_type','84830','霞石正长岩','default',b'0',238,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076239,0,'national_mineral_type','84850','凝灰岩','default',b'0',239,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076240,0,'national_mineral_type','84851','玻璃用凝灰岩','default',b'0',240,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076241,0,'national_mineral_type','84852','水泥用凝灰岩','default',b'0',241,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076242,0,'national_mineral_type','84853','建筑用石料（凝灰岩）','default',b'0',242,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076243,0,'national_mineral_type','84870','火山灰','default',b'0',243,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076244,0,'national_mineral_type','84871','水泥用火山灰','default',b'0',244,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076245,0,'national_mineral_type','84890','火山渣','default',b'0',245,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076246,0,'national_mineral_type','84910','大理岩','default',b'0',246,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076247,0,'national_mineral_type','84911','饰面用石料（大理石）','default',b'0',247,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076248,0,'national_mineral_type','84912','建筑用大理岩','default',b'0',248,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076249,0,'national_mineral_type','84913','水泥用大理石','default',b'0',249,b'1','矿产种类代码（国家表73）；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076250,0,'national_mineral_type','84914','玻璃用大理石','default',b'0',250,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076251,0,'national_mineral_type','84920','板岩','default',b'0',251,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076252,0,'national_mineral_type','84921','饰面用板岩','default',b'0',252,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076253,0,'national_mineral_type','84922','水泥配料用板岩','default',b'0',253,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076254,0,'national_mineral_type','84930','片麻岩','default',b'0',254,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076255,0,'national_mineral_type','84940','角闪岩','default',b'0',255,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076256,0,'national_mineral_type','85670','硼矿','default',b'0',256,b'1','矿产种类代码（国家表73）；储量单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076257,0,'national_mineral_type','97010','矿泉水','default',b'0',257,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076258,0,'national_mineral_type','97030','地下水','default',b'0',258,b'1','矿产种类代码（国家表73）；储量单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076259,0,'national_mineral_type','97070','二氧化碳气','default',b'0',259,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076260,0,'national_mineral_type','97090','硫化氢气','default',b'0',260,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076261,0,'national_mineral_type','97110','氦气','default',b'0',261,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076262,0,'national_mineral_type','97120','氢气','default',b'0',262,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000076263,0,'national_mineral_type','97130','氡气','default',b'0',263,b'1','矿产种类代码（国家表73）；储量单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-28-risk-rule-cond-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 风险规则「触发条件类型」字典注册（risk_rule_cond_type）
--
-- 规范依据：《招投标模块开发规范》用户硬约束"枚举/阈值一律动态配置，前端不得硬编码中文与颜色"；
--           门禁 sql/tools/check-dict-permission.js「字典覆盖率」——前端引用的 dict_type 必须已注册。
-- 前端消费：apps/web-antd/src/views/bidding/safety-risk/index.vue —— condTypeOptions 优先取
--           dictOptions('risk_rule_cond_type')，未注册时回落到**硬编码** COND_TYPE_FALLBACK（本脚本消除该硬编码）。
-- 口径衔接：Q23 裁定维持"阈值键值"（Q23a，见《后端变更通知-给前端.md》第 25 条）——
--           THRESHOLD=与 rule-config 的 ruleKey 阈值比较（当前唯一后端已实现形态）；
--           EXPRESSION/NATURAL 为前端预留的展示形态，后端暂不落库（仅登记字典，避免中文硬编码）。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 镜像：同批内容已并入 sql/master-credential-sync.sql（一键包），重建新库不会缺该类字典。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- id 用 2913~915：2901~905 risk_item_status、2906~912 alert_level/alert_type 已占用（2026-09-28 同日两脚本）
(710000000000002913,0,'risk_rule_cond_type','THRESHOLD','阈值比较','info',b'0',1,b'1','与 rule-config 的 ruleKey 阈值比较（Q23a 当前唯一落库形态）','1',NOW(),'1',NOW(),b'0'),
(710000000000002914,0,'risk_rule_cond_type','EXPRESSION','表达式','warning',b'0',2,b'1','表达式形态（前端展示预留，后端暂不落库）','1',NOW(),'1',NOW(),b'0'),
(710000000000002915,0,'risk_rule_cond_type','NATURAL','自然语言','default',b'0',3,b'1','自然语言描述形态（前端展示预留，后端暂不落库）','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-28-shaanxi-mineral-dict.sql（字典种子；已并入本包）
-- #####################################################################
-- ---------------------------------------------------------------------
-- 陕西矿产种类代码 + 储量单位代码下沉业务字典（bidding_dict_data）
-- 整改依据：用户核查结论③：规范照抄枚举数据改为数据库字典，禁止 Java 常量堆积
-- 出处：《VO 数据对象规范》第十章（陕西 4.13 矿产种类代码表 / 储量单位代码表）
-- 幂等：INSERT IGNORE；类型经 MetaDictServiceImpl 按字典表数据自动支持（/bidding/meta/dicts）
-- id 分块：7100000000000070xx（矿产 263 条）+ 7100000000000073xx（储量单位 263 条）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 矿产种类代码（263 条）：dict_value=矿产代码，dict_label=矿产名称
(7100000000000070001,0,'shaanxi_mineral_type','11001','煤','default',b'0',1,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070002,0,'shaanxi_mineral_type','11002','油页岩','default',b'0',2,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070003,0,'shaanxi_mineral_type','11003','石油','default',b'0',3,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070004,0,'shaanxi_mineral_type','11004','天然气','default',b'0',4,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070005,0,'shaanxi_mineral_type','11005','煤层气','default',b'0',5,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070006,0,'shaanxi_mineral_type','11006','石煤','default',b'0',6,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070007,0,'shaanxi_mineral_type','11008','油砂','default',b'0',7,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070008,0,'shaanxi_mineral_type','11034','石油天然气','default',b'0',8,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070009,0,'shaanxi_mineral_type','11041','页岩气','default',b'0',9,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070010,0,'shaanxi_mineral_type','12712','铀','default',b'0',10,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070011,0,'shaanxi_mineral_type','12713','钍','default',b'0',11,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070012,0,'shaanxi_mineral_type','14970','天然沥青','default',b'0',12,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070013,0,'shaanxi_mineral_type','17050','地热','default',b'0',13,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070014,0,'shaanxi_mineral_type','22001','铁矿','default',b'0',14,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070015,0,'shaanxi_mineral_type','22002','锰矿','default',b'0',15,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070016,0,'shaanxi_mineral_type','22003','铬铁矿','default',b'0',16,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070017,0,'shaanxi_mineral_type','22004','钛矿','default',b'0',17,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070018,0,'shaanxi_mineral_type','22005','钒矿','default',b'0',18,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070019,0,'shaanxi_mineral_type','22006','金红石','default',b'0',19,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070020,0,'shaanxi_mineral_type','32006','铜矿','default',b'0',20,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070021,0,'shaanxi_mineral_type','32007','铅矿','default',b'0',21,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070022,0,'shaanxi_mineral_type','32008','锌矿','default',b'0',22,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070023,0,'shaanxi_mineral_type','32009','铝土矿','default',b'0',23,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070024,0,'shaanxi_mineral_type','32011','镁矿','default',b'0',24,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070025,0,'shaanxi_mineral_type','32012','镍矿','default',b'0',25,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070026,0,'shaanxi_mineral_type','32013','钴矿','default',b'0',26,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070027,0,'shaanxi_mineral_type','32014','钨矿','default',b'0',27,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070028,0,'shaanxi_mineral_type','32015','锡矿','default',b'0',28,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070029,0,'shaanxi_mineral_type','32016','铋矿','default',b'0',29,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070030,0,'shaanxi_mineral_type','32017','钼矿','default',b'0',30,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070031,0,'shaanxi_mineral_type','32018','汞矿','default',b'0',31,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070032,0,'shaanxi_mineral_type','32019','锑矿','default',b'0',32,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070033,0,'shaanxi_mineral_type','32020','多金属','default',b'0',33,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070034,0,'shaanxi_mineral_type','42101','铂矿','default',b'0',34,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070035,0,'shaanxi_mineral_type','42102','钯矿','default',b'0',35,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070036,0,'shaanxi_mineral_type','42103','铱矿','default',b'0',36,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070037,0,'shaanxi_mineral_type','42104','铑矿','default',b'0',37,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070038,0,'shaanxi_mineral_type','42105','锇矿','default',b'0',38,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070039,0,'shaanxi_mineral_type','42106','钌矿','default',b'0',39,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070040,0,'shaanxi_mineral_type','42200','砂金','default',b'0',40,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070041,0,'shaanxi_mineral_type','42201','金矿','default',b'0',41,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070042,0,'shaanxi_mineral_type','42202','银矿','default',b'0',42,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070043,0,'shaanxi_mineral_type','52300','铌钽矿','default',b'0',43,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070044,0,'shaanxi_mineral_type','52301','铌矿','default',b'0',44,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070045,0,'shaanxi_mineral_type','52302','钽矿','default',b'0',45,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070046,0,'shaanxi_mineral_type','52401','铍矿','default',b'0',46,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070047,0,'shaanxi_mineral_type','52402','锂矿','default',b'0',47,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070048,0,'shaanxi_mineral_type','52403','锆矿','default',b'0',48,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070049,0,'shaanxi_mineral_type','52404','锶矿(天青石)','default',b'0',49,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070050,0,'shaanxi_mineral_type','52405','铷矿','default',b'0',50,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070051,0,'shaanxi_mineral_type','52406','铯矿','default',b'0',51,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070052,0,'shaanxi_mineral_type','52500','重稀土矿','default',b'0',52,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070053,0,'shaanxi_mineral_type','52501','钇矿','default',b'0',53,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070054,0,'shaanxi_mineral_type','52502','钆矿','default',b'0',54,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070055,0,'shaanxi_mineral_type','52503','铽矿','default',b'0',55,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070056,0,'shaanxi_mineral_type','52504','镝矿','default',b'0',56,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070057,0,'shaanxi_mineral_type','52505','钬矿','default',b'0',57,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070058,0,'shaanxi_mineral_type','52506','铒矿','default',b'0',58,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070059,0,'shaanxi_mineral_type','52507','铥矿','default',b'0',59,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070060,0,'shaanxi_mineral_type','52508','镱矿','default',b'0',60,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070061,0,'shaanxi_mineral_type','52509','镥矿','default',b'0',61,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070062,0,'shaanxi_mineral_type','52600','轻稀土矿','default',b'0',62,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070063,0,'shaanxi_mineral_type','52601','铈矿','default',b'0',63,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070064,0,'shaanxi_mineral_type','52602','镧矿','default',b'0',64,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070065,0,'shaanxi_mineral_type','52603','镨矿','default',b'0',65,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070066,0,'shaanxi_mineral_type','52604','钕矿','default',b'0',66,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070067,0,'shaanxi_mineral_type','52605','钐矿','default',b'0',67,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070068,0,'shaanxi_mineral_type','52606','铕矿','default',b'0',68,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070069,0,'shaanxi_mineral_type','52701','锗矿','default',b'0',69,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070070,0,'shaanxi_mineral_type','52702','镓矿','default',b'0',70,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070071,0,'shaanxi_mineral_type','52703','铟矿','default',b'0',71,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070072,0,'shaanxi_mineral_type','52704','铊矿','default',b'0',72,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070073,0,'shaanxi_mineral_type','52705','铪矿','default',b'0',73,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070074,0,'shaanxi_mineral_type','52706','铼矿','default',b'0',74,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070075,0,'shaanxi_mineral_type','52707','镉矿','default',b'0',75,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070076,0,'shaanxi_mineral_type','52708','钪矿','default',b'0',76,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070077,0,'shaanxi_mineral_type','52709','硒矿','default',b'0',77,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070078,0,'shaanxi_mineral_type','52711','碲矿','default',b'0',78,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070079,0,'shaanxi_mineral_type','63200','蓝晶石','default',b'0',79,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070080,0,'shaanxi_mineral_type','63210','矽线石','default',b'0',80,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070081,0,'shaanxi_mineral_type','63220','红柱石','default',b'0',81,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070082,0,'shaanxi_mineral_type','63640','菱镁矿','default',b'0',82,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070083,0,'shaanxi_mineral_type','63701','萤石(普通)','default',b'0',83,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070084,0,'shaanxi_mineral_type','63904','熔剂用石灰岩','default',b'0',84,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070085,0,'shaanxi_mineral_type','63941','冶金用白云岩','default',b'0',85,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070086,0,'shaanxi_mineral_type','63951','冶金用石英岩','default',b'0',86,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070087,0,'shaanxi_mineral_type','63971','冶金用砂岩','default',b'0',87,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070088,0,'shaanxi_mineral_type','63976','铸型用砂岩','default',b'0',88,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070089,0,'shaanxi_mineral_type','63992','铸型用砂','default',b'0',89,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070090,0,'shaanxi_mineral_type','64031','冶金用脉石英','default',b'0',90,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070091,0,'shaanxi_mineral_type','64190','耐火粘土','default',b'0',91,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070092,0,'shaanxi_mineral_type','64310','铁钒土','default',b'0',92,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070093,0,'shaanxi_mineral_type','64410','其它粘土','default',b'0',93,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070094,0,'shaanxi_mineral_type','64411','铸型用粘土','default',b'0',94,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070095,0,'shaanxi_mineral_type','64511','耐火用橄榄岩','default',b'0',95,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070096,0,'shaanxi_mineral_type','64531','熔剂用蛇纹岩','default',b'0',96,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070097,0,'shaanxi_mineral_type','73030','自然硫','default',b'0',97,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070098,0,'shaanxi_mineral_type','73070','硫铁矿','default',b'0',98,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070099,0,'shaanxi_mineral_type','73240','钠硝石','default',b'0',99,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070100,0,'shaanxi_mineral_type','73500','明矾石','default',b'0',100,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070101,0,'shaanxi_mineral_type','73510','芒硝(含钙芒硝)','default',b'0',101,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070102,0,'shaanxi_mineral_type','73530','重晶石','default',b'0',102,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070103,0,'shaanxi_mineral_type','73600','毒重石','default',b'0',103,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070104,0,'shaanxi_mineral_type','73610','天然碱(Na2CO3)','default',b'0',104,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070105,0,'shaanxi_mineral_type','73860','赭石','default',b'0',105,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070106,0,'shaanxi_mineral_type','73870','颜料矿物','default',b'0',106,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070107,0,'shaanxi_mineral_type','73880','颜料黄土','default',b'0',107,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070108,0,'shaanxi_mineral_type','73901','电石用灰岩','default',b'0',108,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070109,0,'shaanxi_mineral_type','73902','制碱用灰岩','default',b'0',109,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070110,0,'shaanxi_mineral_type','73903','化肥用石灰岩','default',b'0',110,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070111,0,'shaanxi_mineral_type','73942','化肥用白云岩','default',b'0',111,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070112,0,'shaanxi_mineral_type','73953','化肥用石英岩','default',b'0',112,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070113,0,'shaanxi_mineral_type','73975','化肥用砂岩','default',b'0',113,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070114,0,'shaanxi_mineral_type','74080','含钾岩石','default',b'0',114,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070115,0,'shaanxi_mineral_type','74090','含钾砂页岩','default',b'0',115,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070116,0,'shaanxi_mineral_type','74512','化肥用橄榄岩','default',b'0',116,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070117,0,'shaanxi_mineral_type','74532','化肥用蛇纹岩','default',b'0',117,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070118,0,'shaanxi_mineral_type','74950','泥炭','default',b'0',118,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070119,0,'shaanxi_mineral_type','75510','矿盐','default',b'0',119,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070120,0,'shaanxi_mineral_type','75511','岩盐','default',b'0',120,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070121,0,'shaanxi_mineral_type','75512','湖盐','default',b'0',121,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070122,0,'shaanxi_mineral_type','75530','镁盐','default',b'0',122,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070123,0,'shaanxi_mineral_type','75540','天然卤水','default',b'0',123,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070124,0,'shaanxi_mineral_type','75550','钾盐','default',b'0',124,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070125,0,'shaanxi_mineral_type','75610','碘','default',b'0',125,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070126,0,'shaanxi_mineral_type','75630','溴','default',b'0',126,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070127,0,'shaanxi_mineral_type','75650','砷','default',b'0',127,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070128,0,'shaanxi_mineral_type','75690','磷矿','default',b'0',128,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070129,0,'shaanxi_mineral_type','83010','金刚石','default',b'0',129,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070130,0,'shaanxi_mineral_type','83020','石墨','default',b'0',130,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070131,0,'shaanxi_mineral_type','83100','水晶','default',b'0',131,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070132,0,'shaanxi_mineral_type','83101','压电水晶','default',b'0',132,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070133,0,'shaanxi_mineral_type','83102','熔炼水晶','default',b'0',133,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070134,0,'shaanxi_mineral_type','83103','光学水晶','default',b'0',134,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070135,0,'shaanxi_mineral_type','83104','工艺水晶','default',b'0',135,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070136,0,'shaanxi_mineral_type','83110','刚玉','default',b'0',136,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070137,0,'shaanxi_mineral_type','83230','硅灰石','default',b'0',137,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070138,0,'shaanxi_mineral_type','83250','滑石','default',b'0',138,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070139,0,'shaanxi_mineral_type','83260','石棉(温石棉)','default',b'0',139,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070140,0,'shaanxi_mineral_type','83270','蓝石棉','default',b'0',140,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070141,0,'shaanxi_mineral_type','83280','云母','default',b'0',141,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070142,0,'shaanxi_mineral_type','83290','长石','default',b'0',142,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070143,0,'shaanxi_mineral_type','83300','电气石','default',b'0',143,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070144,0,'shaanxi_mineral_type','83310','石榴子石','default',b'0',144,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070145,0,'shaanxi_mineral_type','83320','黄玉','default',b'0',145,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070146,0,'shaanxi_mineral_type','83330','叶腊石','default',b'0',146,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070147,0,'shaanxi_mineral_type','83340','透辉石','default',b'0',147,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070148,0,'shaanxi_mineral_type','83350','蛭石','default',b'0',148,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070149,0,'shaanxi_mineral_type','83360','沸石','default',b'0',149,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070150,0,'shaanxi_mineral_type','83370','透闪石','default',b'0',150,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070151,0,'shaanxi_mineral_type','83520','石膏','default',b'0',151,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070152,0,'shaanxi_mineral_type','83620','方解石','default',b'0',152,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070153,0,'shaanxi_mineral_type','83630','冰洲石','default',b'0',153,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070154,0,'shaanxi_mineral_type','83702','光学萤石','default',b'0',154,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070155,0,'shaanxi_mineral_type','83750','宝石','default',b'0',155,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070156,0,'shaanxi_mineral_type','83800','玉石','default',b'0',156,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070157,0,'shaanxi_mineral_type','83850','玛瑙','default',b'0',157,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070158,0,'shaanxi_mineral_type','83900','石灰岩','default',b'0',158,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070159,0,'shaanxi_mineral_type','83905','玻璃用石灰岩','default',b'0',159,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070160,0,'shaanxi_mineral_type','83906','水泥用石灰岩','default',b'0',160,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070161,0,'shaanxi_mineral_type','83907','建筑石料用灰岩','default',b'0',161,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070162,0,'shaanxi_mineral_type','83908','饰面用灰岩','default',b'0',162,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070163,0,'shaanxi_mineral_type','83909','制灰用石灰岩','default',b'0',163,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070164,0,'shaanxi_mineral_type','83920','泥灰岩','default',b'0',164,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070165,0,'shaanxi_mineral_type','83930','白垩','default',b'0',165,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070166,0,'shaanxi_mineral_type','83940','白云岩','default',b'0',166,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070167,0,'shaanxi_mineral_type','83943','玻璃用白云岩','default',b'0',167,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070168,0,'shaanxi_mineral_type','83945','建筑用白云岩','default',b'0',168,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070169,0,'shaanxi_mineral_type','83950','石英岩','default',b'0',169,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070170,0,'shaanxi_mineral_type','83952','玻璃用石英岩','default',b'0',170,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070171,0,'shaanxi_mineral_type','83970','砂岩','default',b'0',171,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070172,0,'shaanxi_mineral_type','83972','玻璃用砂岩','default',b'0',172,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070173,0,'shaanxi_mineral_type','83973','水泥配料用砂岩','default',b'0',173,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070174,0,'shaanxi_mineral_type','83974','砖瓦用砂岩','default',b'0',174,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070175,0,'shaanxi_mineral_type','83977','陶瓷用砂岩','default',b'0',175,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070176,0,'shaanxi_mineral_type','83978','建筑用砂岩','default',b'0',176,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070177,0,'shaanxi_mineral_type','83990','天然石英砂','default',b'0',177,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070178,0,'shaanxi_mineral_type','83991','玻璃用砂','default',b'0',178,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070179,0,'shaanxi_mineral_type','83992','海砂','default',b'0',179,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070180,0,'shaanxi_mineral_type','83993','建筑用砂','default',b'0',180,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070181,0,'shaanxi_mineral_type','83994','水泥配料用砂','default',b'0',181,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070182,0,'shaanxi_mineral_type','83995','水泥标准砂','default',b'0',182,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070183,0,'shaanxi_mineral_type','83996','砖瓦用砂','default',b'0',183,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070184,0,'shaanxi_mineral_type','84030','脉石英','default',b'0',184,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070185,0,'shaanxi_mineral_type','84032','玻璃用脉石英','default',b'0',185,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070186,0,'shaanxi_mineral_type','84050','粉石英','default',b'0',186,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070187,0,'shaanxi_mineral_type','84070','天然油石','default',b'0',187,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070188,0,'shaanxi_mineral_type','84110','硅藻土','default',b'0',188,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070189,0,'shaanxi_mineral_type','84130','页岩','default',b'0',189,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070190,0,'shaanxi_mineral_type','84131','陶粒页岩','default',b'0',190,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070191,0,'shaanxi_mineral_type','84132','砖瓦用页岩','default',b'0',191,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070192,0,'shaanxi_mineral_type','84133','水泥配料用页岩','default',b'0',192,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070193,0,'shaanxi_mineral_type','84150','高岭土','default',b'0',193,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070194,0,'shaanxi_mineral_type','84170','陶瓷土','default',b'0',194,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070195,0,'shaanxi_mineral_type','84210','凹凸棒石粘土','default',b'0',195,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070196,0,'shaanxi_mineral_type','84230','海泡石粘土','default',b'0',196,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070197,0,'shaanxi_mineral_type','84250','伊利石粘土','default',b'0',197,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070198,0,'shaanxi_mineral_type','84270','累托石粘土','default',b'0',198,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070199,0,'shaanxi_mineral_type','84290','膨润土','default',b'0',199,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070200,0,'shaanxi_mineral_type','84412','砖瓦用粘土','default',b'0',200,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070201,0,'shaanxi_mineral_type','84413','陶粒用粘土','default',b'0',201,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070202,0,'shaanxi_mineral_type','84414','水泥用粘土','default',b'0',202,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070203,0,'shaanxi_mineral_type','84415','水泥配料用红土','default',b'0',203,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070204,0,'shaanxi_mineral_type','84416','水泥配料用黄土','default',b'0',204,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070205,0,'shaanxi_mineral_type','84417','水泥配料用泥岩','default',b'0',205,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070206,0,'shaanxi_mineral_type','84418','保温材料用粘土','default',b'0',206,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070207,0,'shaanxi_mineral_type','84510','橄榄岩','default',b'0',207,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070208,0,'shaanxi_mineral_type','84513','建筑用橄榄岩','default',b'0',208,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070209,0,'shaanxi_mineral_type','84530','蛇纹岩','default',b'0',209,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070210,0,'shaanxi_mineral_type','84533','饰面用蛇纹岩','default',b'0',210,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070211,0,'shaanxi_mineral_type','84550','玄武岩','default',b'0',211,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070212,0,'shaanxi_mineral_type','84551','铸石用玄武岩','default',b'0',212,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070213,0,'shaanxi_mineral_type','84552','岩棉用玄武岩','default',b'0',213,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070214,0,'shaanxi_mineral_type','84555','建筑用玄武岩','default',b'0',214,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070215,0,'shaanxi_mineral_type','84570','辉绿岩','default',b'0',215,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070216,0,'shaanxi_mineral_type','84571','水泥用辉绿岩','default',b'0',216,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070217,0,'shaanxi_mineral_type','84572','铸石用辉绿岩','default',b'0',217,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070218,0,'shaanxi_mineral_type','84573','建筑用辉绿岩','default',b'0',218,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070219,0,'shaanxi_mineral_type','84574','饰面用辉绿岩','default',b'0',219,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070220,0,'shaanxi_mineral_type','84590','安山岩','default',b'0',220,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070221,0,'shaanxi_mineral_type','84591','饰面用安山岩','default',b'0',221,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070222,0,'shaanxi_mineral_type','84592','建筑用安山岩','default',b'0',222,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070223,0,'shaanxi_mineral_type','84593','水泥混合材料用安山岩','default',b'0',223,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070224,0,'shaanxi_mineral_type','84610','闪长岩','default',b'0',224,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070225,0,'shaanxi_mineral_type','84611','建筑用闪长岩','default',b'0',225,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070226,0,'shaanxi_mineral_type','84612','水泥混合材料用闪长玢岩','default',b'0',226,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070227,0,'shaanxi_mineral_type','84710','花岗岩','default',b'0',227,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070228,0,'shaanxi_mineral_type','84711','建筑用花岗岩','default',b'0',228,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070229,0,'shaanxi_mineral_type','84712','饰面用花岗岩','default',b'0',229,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070230,0,'shaanxi_mineral_type','84720','麦饭石','default',b'0',230,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070231,0,'shaanxi_mineral_type','84730','珍珠岩','default',b'0',231,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070232,0,'shaanxi_mineral_type','84750','黑曜岩','default',b'0',232,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070233,0,'shaanxi_mineral_type','84770','松脂岩','default',b'0',233,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070234,0,'shaanxi_mineral_type','84790','浮石','default',b'0',234,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070235,0,'shaanxi_mineral_type','84810','粗面岩','default',b'0',235,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070236,0,'shaanxi_mineral_type','84811','水泥用粗面岩','default',b'0',236,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070237,0,'shaanxi_mineral_type','84812','铸石用粗面岩','default',b'0',237,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070238,0,'shaanxi_mineral_type','84830','霞石正长岩','default',b'0',238,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070239,0,'shaanxi_mineral_type','84850','凝灰岩','default',b'0',239,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070240,0,'shaanxi_mineral_type','84851','玻璃用凝灰岩','default',b'0',240,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070241,0,'shaanxi_mineral_type','84852','水泥用凝灰岩','default',b'0',241,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070242,0,'shaanxi_mineral_type','84853','建筑用石料（凝灰岩）','default',b'0',242,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070243,0,'shaanxi_mineral_type','84870','火山灰','default',b'0',243,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070244,0,'shaanxi_mineral_type','84871','水泥用火山灰','default',b'0',244,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070245,0,'shaanxi_mineral_type','84890','火山渣','default',b'0',245,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070246,0,'shaanxi_mineral_type','84910','大理岩','default',b'0',246,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070247,0,'shaanxi_mineral_type','84911','饰面用石料（大理石）','default',b'0',247,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070248,0,'shaanxi_mineral_type','84912','建筑用大理岩','default',b'0',248,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070249,0,'shaanxi_mineral_type','84913','水泥用大理石','default',b'0',249,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070250,0,'shaanxi_mineral_type','84914','玻璃用大理石','default',b'0',250,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070251,0,'shaanxi_mineral_type','84920','板岩','default',b'0',251,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070252,0,'shaanxi_mineral_type','84921','饰面用板岩','default',b'0',252,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070253,0,'shaanxi_mineral_type','84922','水泥配料用板岩','default',b'0',253,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070254,0,'shaanxi_mineral_type','84930','片麻岩','default',b'0',254,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070255,0,'shaanxi_mineral_type','84940','角闪岩','default',b'0',255,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070256,0,'shaanxi_mineral_type','85670','硼矿','default',b'0',256,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070257,0,'shaanxi_mineral_type','97010','矿泉水','default',b'0',257,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070258,0,'shaanxi_mineral_type','97030','地下水','default',b'0',258,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070259,0,'shaanxi_mineral_type','97070','二氧化碳气','default',b'0',259,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070260,0,'shaanxi_mineral_type','97090','硫化氢气','default',b'0',260,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070261,0,'shaanxi_mineral_type','97110','氦气','default',b'0',261,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070262,0,'shaanxi_mineral_type','97120','氢气','default',b'0',262,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0'),
(7100000000000070263,0,'shaanxi_mineral_type','97130','氡气','default',b'0',263,b'1','矿产种类代码（陕西4.13，照录《VO 数据对象规范》第十章）','1',NOW(),'1',NOW(),b'0');

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 储量单位代码（263 条）：dict_value=规范代码，dict_label=单位名；折算单位保留在 remark
(7100000000000073001,0,'shaanxi_mineral_unit','135','万吨/年','default',b'0',1,b'1','储量单位代码 135；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073002,0,'shaanxi_mineral_unit','136','万吨/年','default',b'0',2,b'1','储量单位代码 136；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073003,0,'shaanxi_mineral_unit','137','万吨/年','default',b'0',3,b'1','储量单位代码 137；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073004,0,'shaanxi_mineral_unit','138','亿立方米/年','default',b'0',4,b'1','储量单位代码 138；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073005,0,'shaanxi_mineral_unit','139','亿立方米/年','default',b'0',5,b'1','储量单位代码 139；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073006,0,'shaanxi_mineral_unit','140','万吨/年','default',b'0',6,b'1','储量单位代码 140；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073007,0,'shaanxi_mineral_unit','141','亿立方米/年','default',b'0',7,b'1','储量单位代码 141；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073008,0,'shaanxi_mineral_unit','142','万吨/年','default',b'0',8,b'1','储量单位代码 142；折算单位：亿立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073009,0,'shaanxi_mineral_unit','143','亿立方米/年','default',b'0',9,b'1','储量单位代码 143；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073010,0,'shaanxi_mineral_unit','144','万吨/年','default',b'0',10,b'1','储量单位代码 144；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073011,0,'shaanxi_mineral_unit','145','万吨/年','default',b'0',11,b'1','储量单位代码 145；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073012,0,'shaanxi_mineral_unit','146','万吨/年','default',b'0',12,b'1','储量单位代码 146；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073013,0,'shaanxi_mineral_unit','147','万立方米/年','default',b'0',13,b'1','储量单位代码 147；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073014,0,'shaanxi_mineral_unit','148','万吨/年','default',b'0',14,b'1','储量单位代码 148；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073015,0,'shaanxi_mineral_unit','149','万吨/年','default',b'0',15,b'1','储量单位代码 149；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073016,0,'shaanxi_mineral_unit','150','万吨/年','default',b'0',16,b'1','储量单位代码 150；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073017,0,'shaanxi_mineral_unit','151','万立方米/年','default',b'0',17,b'1','储量单位代码 151；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073018,0,'shaanxi_mineral_unit','152','万吨/年','default',b'0',18,b'1','储量单位代码 152；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073019,0,'shaanxi_mineral_unit','153','万吨/年','default',b'0',19,b'1','储量单位代码 153；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073020,0,'shaanxi_mineral_unit','154','万吨/年','default',b'0',20,b'1','储量单位代码 154；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073021,0,'shaanxi_mineral_unit','155','万吨/年','default',b'0',21,b'1','储量单位代码 155；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073022,0,'shaanxi_mineral_unit','156','万吨/年','default',b'0',22,b'1','储量单位代码 156；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073023,0,'shaanxi_mineral_unit','157','万吨/年','default',b'0',23,b'1','储量单位代码 157；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073024,0,'shaanxi_mineral_unit','158','万吨/年','default',b'0',24,b'1','储量单位代码 158；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073025,0,'shaanxi_mineral_unit','159','万吨/年','default',b'0',25,b'1','储量单位代码 159；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073026,0,'shaanxi_mineral_unit','160','万吨/年','default',b'0',26,b'1','储量单位代码 160；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073027,0,'shaanxi_mineral_unit','161','万吨/年','default',b'0',27,b'1','储量单位代码 161；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073028,0,'shaanxi_mineral_unit','162','万吨/年','default',b'0',28,b'1','储量单位代码 162；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073029,0,'shaanxi_mineral_unit','163','万吨/年','default',b'0',29,b'1','储量单位代码 163；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073030,0,'shaanxi_mineral_unit','164','万吨/年','default',b'0',30,b'1','储量单位代码 164；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073031,0,'shaanxi_mineral_unit','165','万吨/年','default',b'0',31,b'1','储量单位代码 165；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073032,0,'shaanxi_mineral_unit','166','万吨/年','default',b'0',32,b'1','储量单位代码 166；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073033,0,'shaanxi_mineral_unit','167','万吨/年','default',b'0',33,b'1','储量单位代码 167；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073034,0,'shaanxi_mineral_unit','168','万吨/年','default',b'0',34,b'1','储量单位代码 168；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073035,0,'shaanxi_mineral_unit','169','万吨/年','default',b'0',35,b'1','储量单位代码 169；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073036,0,'shaanxi_mineral_unit','170','万吨/年','default',b'0',36,b'1','储量单位代码 170；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073037,0,'shaanxi_mineral_unit','171','万吨/年','default',b'0',37,b'1','储量单位代码 171；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073038,0,'shaanxi_mineral_unit','172','万吨/年','default',b'0',38,b'1','储量单位代码 172；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073039,0,'shaanxi_mineral_unit','173','万吨/年','default',b'0',39,b'1','储量单位代码 173；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073040,0,'shaanxi_mineral_unit','174','万立方米/年','default',b'0',40,b'1','储量单位代码 174；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073041,0,'shaanxi_mineral_unit','175','万吨/年','default',b'0',41,b'1','储量单位代码 175；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073042,0,'shaanxi_mineral_unit','176','万吨/年','default',b'0',42,b'1','储量单位代码 176；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073043,0,'shaanxi_mineral_unit','177','万吨/年','default',b'0',43,b'1','储量单位代码 177；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073044,0,'shaanxi_mineral_unit','178','万吨/年','default',b'0',44,b'1','储量单位代码 178；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073045,0,'shaanxi_mineral_unit','179','万吨/年','default',b'0',45,b'1','储量单位代码 179；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073046,0,'shaanxi_mineral_unit','180','万吨/年','default',b'0',46,b'1','储量单位代码 180；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073047,0,'shaanxi_mineral_unit','181','万吨/年','default',b'0',47,b'1','储量单位代码 181；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073048,0,'shaanxi_mineral_unit','182','万立方米/年','default',b'0',48,b'1','储量单位代码 182；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073049,0,'shaanxi_mineral_unit','183','万吨/年','default',b'0',49,b'1','储量单位代码 183；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073050,0,'shaanxi_mineral_unit','184','万吨/年','default',b'0',50,b'1','储量单位代码 184；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073051,0,'shaanxi_mineral_unit','185','万吨/年','default',b'0',51,b'1','储量单位代码 185；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073052,0,'shaanxi_mineral_unit','186','万吨/年','default',b'0',52,b'1','储量单位代码 186；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073053,0,'shaanxi_mineral_unit','187','万吨/年','default',b'0',53,b'1','储量单位代码 187；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073054,0,'shaanxi_mineral_unit','188','万吨/年','default',b'0',54,b'1','储量单位代码 188；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073055,0,'shaanxi_mineral_unit','189','万吨/年','default',b'0',55,b'1','储量单位代码 189；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073056,0,'shaanxi_mineral_unit','190','万吨/年','default',b'0',56,b'1','储量单位代码 190；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073057,0,'shaanxi_mineral_unit','191','万吨/年','default',b'0',57,b'1','储量单位代码 191；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073058,0,'shaanxi_mineral_unit','192','万吨/年','default',b'0',58,b'1','储量单位代码 192；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073059,0,'shaanxi_mineral_unit','193','万吨/年','default',b'0',59,b'1','储量单位代码 193；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073060,0,'shaanxi_mineral_unit','194','万吨/年','default',b'0',60,b'1','储量单位代码 194；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073061,0,'shaanxi_mineral_unit','195','万吨/年','default',b'0',61,b'1','储量单位代码 195；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073062,0,'shaanxi_mineral_unit','196','万吨/年','default',b'0',62,b'1','储量单位代码 196；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073063,0,'shaanxi_mineral_unit','197','万吨/年','default',b'0',63,b'1','储量单位代码 197；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073064,0,'shaanxi_mineral_unit','198','万吨/年','default',b'0',64,b'1','储量单位代码 198；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073065,0,'shaanxi_mineral_unit','199','万吨/年','default',b'0',65,b'1','储量单位代码 199；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073066,0,'shaanxi_mineral_unit','200','万吨/年','default',b'0',66,b'1','储量单位代码 200；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073067,0,'shaanxi_mineral_unit','201','万吨/年','default',b'0',67,b'1','储量单位代码 201；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073068,0,'shaanxi_mineral_unit','202','万吨/年','default',b'0',68,b'1','储量单位代码 202；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073069,0,'shaanxi_mineral_unit','203','万吨/年','default',b'0',69,b'1','储量单位代码 203；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073070,0,'shaanxi_mineral_unit','204','万吨/年','default',b'0',70,b'1','储量单位代码 204；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073071,0,'shaanxi_mineral_unit','205','万吨/年','default',b'0',71,b'1','储量单位代码 205；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073072,0,'shaanxi_mineral_unit','206','万吨/年','default',b'0',72,b'1','储量单位代码 206；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073073,0,'shaanxi_mineral_unit','207','万吨/年','default',b'0',73,b'1','储量单位代码 207；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073074,0,'shaanxi_mineral_unit','208','万吨/年','default',b'0',74,b'1','储量单位代码 208；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073075,0,'shaanxi_mineral_unit','209','万吨/年','default',b'0',75,b'1','储量单位代码 209；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073076,0,'shaanxi_mineral_unit','210','万吨/年','default',b'0',76,b'1','储量单位代码 210；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073077,0,'shaanxi_mineral_unit','211','万吨/年','default',b'0',77,b'1','储量单位代码 211；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073078,0,'shaanxi_mineral_unit','212','万吨/年','default',b'0',78,b'1','储量单位代码 212；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073079,0,'shaanxi_mineral_unit','213','万吨/年','default',b'0',79,b'1','储量单位代码 213；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073080,0,'shaanxi_mineral_unit','214','万吨/年','default',b'0',80,b'1','储量单位代码 214；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073081,0,'shaanxi_mineral_unit','215','万吨/年','default',b'0',81,b'1','储量单位代码 215；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073082,0,'shaanxi_mineral_unit','216','万吨/年','default',b'0',82,b'1','储量单位代码 216；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073083,0,'shaanxi_mineral_unit','217','万吨/年','default',b'0',83,b'1','储量单位代码 217；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073084,0,'shaanxi_mineral_unit','218','万吨/年','default',b'0',84,b'1','储量单位代码 218；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073085,0,'shaanxi_mineral_unit','219','万吨/年','default',b'0',85,b'1','储量单位代码 219；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073086,0,'shaanxi_mineral_unit','220','万吨/年','default',b'0',86,b'1','储量单位代码 220；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073087,0,'shaanxi_mineral_unit','221','万吨/年','default',b'0',87,b'1','储量单位代码 221；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073088,0,'shaanxi_mineral_unit','222','万吨/年','default',b'0',88,b'1','储量单位代码 222；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073089,0,'shaanxi_mineral_unit','223','万吨/年','default',b'0',89,b'1','储量单位代码 223；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073090,0,'shaanxi_mineral_unit','224','万吨/年','default',b'0',90,b'1','储量单位代码 224；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073091,0,'shaanxi_mineral_unit','225','万吨/年','default',b'0',91,b'1','储量单位代码 225；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073092,0,'shaanxi_mineral_unit','226','万吨/年','default',b'0',92,b'1','储量单位代码 226；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073093,0,'shaanxi_mineral_unit','227','万吨/年','default',b'0',93,b'1','储量单位代码 227；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073094,0,'shaanxi_mineral_unit','228','万吨/年','default',b'0',94,b'1','储量单位代码 228；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073095,0,'shaanxi_mineral_unit','229','万吨/年','default',b'0',95,b'1','储量单位代码 229；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073096,0,'shaanxi_mineral_unit','230','万吨/年','default',b'0',96,b'1','储量单位代码 230；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073097,0,'shaanxi_mineral_unit','231','万吨/年','default',b'0',97,b'1','储量单位代码 231；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073098,0,'shaanxi_mineral_unit','232','万吨/年','default',b'0',98,b'1','储量单位代码 232；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073099,0,'shaanxi_mineral_unit','233','万吨/年','default',b'0',99,b'1','储量单位代码 233；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073100,0,'shaanxi_mineral_unit','234','万吨/年','default',b'0',100,b'1','储量单位代码 234；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073101,0,'shaanxi_mineral_unit','235','万吨/年','default',b'0',101,b'1','储量单位代码 235；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073102,0,'shaanxi_mineral_unit','236','万吨/年','default',b'0',102,b'1','储量单位代码 236；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073103,0,'shaanxi_mineral_unit','237','万吨/年','default',b'0',103,b'1','储量单位代码 237；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073104,0,'shaanxi_mineral_unit','238','万吨/年','default',b'0',104,b'1','储量单位代码 238；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073105,0,'shaanxi_mineral_unit','239','万吨/年','default',b'0',105,b'1','储量单位代码 239；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073106,0,'shaanxi_mineral_unit','240','万吨/年','default',b'0',106,b'1','储量单位代码 240；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073107,0,'shaanxi_mineral_unit','241','万吨/年','default',b'0',107,b'1','储量单位代码 241；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073108,0,'shaanxi_mineral_unit','242','万吨/年','default',b'0',108,b'1','储量单位代码 242；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073109,0,'shaanxi_mineral_unit','243','万吨/年','default',b'0',109,b'1','储量单位代码 243；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073110,0,'shaanxi_mineral_unit','244','万吨/年','default',b'0',110,b'1','储量单位代码 244；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073111,0,'shaanxi_mineral_unit','245','万吨/年','default',b'0',111,b'1','储量单位代码 245；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073112,0,'shaanxi_mineral_unit','246','万吨/年','default',b'0',112,b'1','储量单位代码 246；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073113,0,'shaanxi_mineral_unit','247','万吨/年','default',b'0',113,b'1','储量单位代码 247；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073114,0,'shaanxi_mineral_unit','248','万吨/年','default',b'0',114,b'1','储量单位代码 248；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073115,0,'shaanxi_mineral_unit','249','万吨/年','default',b'0',115,b'1','储量单位代码 249；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073116,0,'shaanxi_mineral_unit','250','万吨/年','default',b'0',116,b'1','储量单位代码 250；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073117,0,'shaanxi_mineral_unit','251','万吨/年','default',b'0',117,b'1','储量单位代码 251；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073118,0,'shaanxi_mineral_unit','252','万吨/年','default',b'0',118,b'1','储量单位代码 252；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073119,0,'shaanxi_mineral_unit','253','万吨/年','default',b'0',119,b'1','储量单位代码 253；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073120,0,'shaanxi_mineral_unit','254','万吨/年','default',b'0',120,b'1','储量单位代码 254；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073121,0,'shaanxi_mineral_unit','255','万立方米/年','default',b'0',121,b'1','储量单位代码 255；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073122,0,'shaanxi_mineral_unit','256','万吨/年','default',b'0',122,b'1','储量单位代码 256；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073123,0,'shaanxi_mineral_unit','257','万立方米/年','default',b'0',123,b'1','储量单位代码 257；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073124,0,'shaanxi_mineral_unit','258','万吨/年','default',b'0',124,b'1','储量单位代码 258；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073125,0,'shaanxi_mineral_unit','259','万吨/年','default',b'0',125,b'1','储量单位代码 259；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073126,0,'shaanxi_mineral_unit','260','万立方米/年','default',b'0',126,b'1','储量单位代码 260；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073127,0,'shaanxi_mineral_unit','261','万吨/年','default',b'0',127,b'1','储量单位代码 261；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073128,0,'shaanxi_mineral_unit','262','万吨/年','default',b'0',128,b'1','储量单位代码 262；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073129,0,'shaanxi_mineral_unit','263','万克拉/年','default',b'0',129,b'1','储量单位代码 263；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073130,0,'shaanxi_mineral_unit','264','万吨/年','default',b'0',130,b'1','储量单位代码 264；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073131,0,'shaanxi_mineral_unit','265','万吨/年','default',b'0',131,b'1','储量单位代码 265；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073132,0,'shaanxi_mineral_unit','266','万吨/年','default',b'0',132,b'1','储量单位代码 266；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073133,0,'shaanxi_mineral_unit','267','万吨/年','default',b'0',133,b'1','储量单位代码 267；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073134,0,'shaanxi_mineral_unit','268','万吨/年','default',b'0',134,b'1','储量单位代码 268；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073135,0,'shaanxi_mineral_unit','398','万吨/年','default',b'0',135,b'1','储量单位代码 398；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073136,0,'shaanxi_mineral_unit','399','万吨/年','default',b'0',136,b'1','储量单位代码 399；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073137,0,'shaanxi_mineral_unit','400','万吨/年','default',b'0',137,b'1','储量单位代码 400；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073138,0,'shaanxi_mineral_unit','401','万吨/年','default',b'0',138,b'1','储量单位代码 401；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073139,0,'shaanxi_mineral_unit','402','万吨/年','default',b'0',139,b'1','储量单位代码 402；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073140,0,'shaanxi_mineral_unit','403','万吨/年','default',b'0',140,b'1','储量单位代码 403；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073141,0,'shaanxi_mineral_unit','404','万吨/年','default',b'0',141,b'1','储量单位代码 404；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073142,0,'shaanxi_mineral_unit','405','万吨/年','default',b'0',142,b'1','储量单位代码 405；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073143,0,'shaanxi_mineral_unit','406','万吨/年','default',b'0',143,b'1','储量单位代码 406；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073144,0,'shaanxi_mineral_unit','407','万吨/年','default',b'0',144,b'1','储量单位代码 407；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073145,0,'shaanxi_mineral_unit','408','万吨/年','default',b'0',145,b'1','储量单位代码 408；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073146,0,'shaanxi_mineral_unit','409','万吨/年','default',b'0',146,b'1','储量单位代码 409；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073147,0,'shaanxi_mineral_unit','410','万吨/年','default',b'0',147,b'1','储量单位代码 410；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073148,0,'shaanxi_mineral_unit','411','万吨/年','default',b'0',148,b'1','储量单位代码 411；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073149,0,'shaanxi_mineral_unit','412','万吨/年','default',b'0',149,b'1','储量单位代码 412；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073150,0,'shaanxi_mineral_unit','413','万吨/年','default',b'0',150,b'1','储量单位代码 413；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073151,0,'shaanxi_mineral_unit','414','万吨/年','default',b'0',151,b'1','储量单位代码 414；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073152,0,'shaanxi_mineral_unit','415','万吨/年','default',b'0',152,b'1','储量单位代码 415；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073153,0,'shaanxi_mineral_unit','416','万吨/年','default',b'0',153,b'1','储量单位代码 416；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073154,0,'shaanxi_mineral_unit','417','万吨/年','default',b'0',154,b'1','储量单位代码 417；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073155,0,'shaanxi_mineral_unit','418','万吨/年','default',b'0',155,b'1','储量单位代码 418；折算单位：吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073156,0,'shaanxi_mineral_unit','419','万吨/年','default',b'0',156,b'1','储量单位代码 419；折算单位：吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073157,0,'shaanxi_mineral_unit','420','万吨/年','default',b'0',157,b'1','储量单位代码 420；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073158,0,'shaanxi_mineral_unit','421','万吨/年','default',b'0',158,b'1','储量单位代码 421；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073159,0,'shaanxi_mineral_unit','422','万吨/年','default',b'0',159,b'1','储量单位代码 422；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073160,0,'shaanxi_mineral_unit','423','万吨/年','default',b'0',160,b'1','储量单位代码 423；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073161,0,'shaanxi_mineral_unit','424','万吨/年','default',b'0',161,b'1','储量单位代码 424；折算单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073162,0,'shaanxi_mineral_unit','425','万吨/年','default',b'0',162,b'1','储量单位代码 425；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073163,0,'shaanxi_mineral_unit','426','万吨/年','default',b'0',163,b'1','储量单位代码 426；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073164,0,'shaanxi_mineral_unit','427','万吨/年','default',b'0',164,b'1','储量单位代码 427；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073165,0,'shaanxi_mineral_unit','428','万吨/年','default',b'0',165,b'1','储量单位代码 428；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073166,0,'shaanxi_mineral_unit','429','万吨/年','default',b'0',166,b'1','储量单位代码 429；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073167,0,'shaanxi_mineral_unit','430','万吨/年','default',b'0',167,b'1','储量单位代码 430；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073168,0,'shaanxi_mineral_unit','431','万吨/年','default',b'0',168,b'1','储量单位代码 431；折算单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073169,0,'shaanxi_mineral_unit','432','万吨/年','default',b'0',169,b'1','储量单位代码 432；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073170,0,'shaanxi_mineral_unit','433','万吨/年','default',b'0',170,b'1','储量单位代码 433；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073171,0,'shaanxi_mineral_unit','434','万吨/年','default',b'0',171,b'1','储量单位代码 434；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073172,0,'shaanxi_mineral_unit','435','万吨/年','default',b'0',172,b'1','储量单位代码 435；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073173,0,'shaanxi_mineral_unit','436','万吨/年','default',b'0',173,b'1','储量单位代码 436；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073174,0,'shaanxi_mineral_unit','437','万吨/年','default',b'0',174,b'1','储量单位代码 437；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073175,0,'shaanxi_mineral_unit','438','万吨/年','default',b'0',175,b'1','储量单位代码 438；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073176,0,'shaanxi_mineral_unit','439','万立方米/年','default',b'0',176,b'1','储量单位代码 439；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073177,0,'shaanxi_mineral_unit','440','万立方米/年','default',b'0',177,b'1','储量单位代码 440；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073178,0,'shaanxi_mineral_unit','441','万立方米/年','default',b'0',178,b'1','储量单位代码 441；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073179,0,'shaanxi_mineral_unit','442','万立方米/年','default',b'0',179,b'1','储量单位代码 442；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073180,0,'shaanxi_mineral_unit','443','万立方米/年','default',b'0',180,b'1','储量单位代码 443；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073181,0,'shaanxi_mineral_unit','444','万立方米/年','default',b'0',181,b'1','储量单位代码 444；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073182,0,'shaanxi_mineral_unit','445','万立方米/年','default',b'0',182,b'1','储量单位代码 445；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073183,0,'shaanxi_mineral_unit','446','万立方米/年','default',b'0',183,b'1','储量单位代码 446；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073184,0,'shaanxi_mineral_unit','447','万吨/年','default',b'0',184,b'1','储量单位代码 447；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073185,0,'shaanxi_mineral_unit','448','万吨/年','default',b'0',185,b'1','储量单位代码 448；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073186,0,'shaanxi_mineral_unit','449','万吨/年','default',b'0',186,b'1','储量单位代码 449；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073187,0,'shaanxi_mineral_unit','450','万吨/年','default',b'0',187,b'1','储量单位代码 450；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073188,0,'shaanxi_mineral_unit','451','万吨/年','default',b'0',188,b'1','储量单位代码 451；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073189,0,'shaanxi_mineral_unit','452','万吨/年','default',b'0',189,b'1','储量单位代码 452；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073190,0,'shaanxi_mineral_unit','453','万吨/年','default',b'0',190,b'1','储量单位代码 453；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073191,0,'shaanxi_mineral_unit','454','万吨/年','default',b'0',191,b'1','储量单位代码 454；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073192,0,'shaanxi_mineral_unit','455','万吨/年','default',b'0',192,b'1','储量单位代码 455；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073193,0,'shaanxi_mineral_unit','456','万吨/年','default',b'0',193,b'1','储量单位代码 456；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073194,0,'shaanxi_mineral_unit','457','万立方米/年','default',b'0',194,b'1','储量单位代码 457；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073195,0,'shaanxi_mineral_unit','458','万立方米/年','default',b'0',195,b'1','储量单位代码 458；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073196,0,'shaanxi_mineral_unit','459','万立方米/年','default',b'0',196,b'1','储量单位代码 459；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073197,0,'shaanxi_mineral_unit','460','万立方米/年','default',b'0',197,b'1','储量单位代码 460；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073198,0,'shaanxi_mineral_unit','461','万立方米/年','default',b'0',198,b'1','储量单位代码 461；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073199,0,'shaanxi_mineral_unit','462','万立方米/年','default',b'0',199,b'1','储量单位代码 462；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073200,0,'shaanxi_mineral_unit','463','万立方米/年','default',b'0',200,b'1','储量单位代码 463；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073201,0,'shaanxi_mineral_unit','464','万立方米/年','default',b'0',201,b'1','储量单位代码 464；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073202,0,'shaanxi_mineral_unit','465','万立方米/年','default',b'0',202,b'1','储量单位代码 465；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073203,0,'shaanxi_mineral_unit','466','万立方米/年','default',b'0',203,b'1','储量单位代码 466；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073204,0,'shaanxi_mineral_unit','467','万立方米/年','default',b'0',204,b'1','储量单位代码 467；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073205,0,'shaanxi_mineral_unit','468','万立方米/年','default',b'0',205,b'1','储量单位代码 468；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073206,0,'shaanxi_mineral_unit','469','万立方米/年','default',b'0',206,b'1','储量单位代码 469；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073207,0,'shaanxi_mineral_unit','470','万吨/年','default',b'0',207,b'1','储量单位代码 470；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073208,0,'shaanxi_mineral_unit','471','万吨/年','default',b'0',208,b'1','储量单位代码 471；折算单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073209,0,'shaanxi_mineral_unit','472','万吨/年','default',b'0',209,b'1','储量单位代码 472；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073210,0,'shaanxi_mineral_unit','473','万吨/年','default',b'0',210,b'1','储量单位代码 473；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073211,0,'shaanxi_mineral_unit','474','万吨/年','default',b'0',211,b'1','储量单位代码 474；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073212,0,'shaanxi_mineral_unit','475','万吨/年','default',b'0',212,b'1','储量单位代码 475；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073213,0,'shaanxi_mineral_unit','476','万吨/年','default',b'0',213,b'1','储量单位代码 476；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073214,0,'shaanxi_mineral_unit','477','万立方米/年','default',b'0',214,b'1','储量单位代码 477；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073215,0,'shaanxi_mineral_unit','478','万吨/年','default',b'0',215,b'1','储量单位代码 478；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073216,0,'shaanxi_mineral_unit','479','万吨/年','default',b'0',216,b'1','储量单位代码 479；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073217,0,'shaanxi_mineral_unit','480','万吨/年','default',b'0',217,b'1','储量单位代码 480；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073218,0,'shaanxi_mineral_unit','481','万吨/年','default',b'0',218,b'1','储量单位代码 481；折算单位：万立方米/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073219,0,'shaanxi_mineral_unit','482','万吨/年','default',b'0',219,b'1','储量单位代码 482；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073220,0,'shaanxi_mineral_unit','483','万立方米/年','default',b'0',220,b'1','储量单位代码 483；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073221,0,'shaanxi_mineral_unit','484','万立方米/年','default',b'0',221,b'1','储量单位代码 484；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073222,0,'shaanxi_mineral_unit','485','万立方米/年','default',b'0',222,b'1','储量单位代码 485；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073223,0,'shaanxi_mineral_unit','486','万立方米/年','default',b'0',223,b'1','储量单位代码 486；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073224,0,'shaanxi_mineral_unit','487','万立方米/年','default',b'0',224,b'1','储量单位代码 487；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073225,0,'shaanxi_mineral_unit','488','万立方米/年','default',b'0',225,b'1','储量单位代码 488；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073226,0,'shaanxi_mineral_unit','489','万立方米/年','default',b'0',226,b'1','储量单位代码 489；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073227,0,'shaanxi_mineral_unit','490','万立方米/年','default',b'0',227,b'1','储量单位代码 490；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073228,0,'shaanxi_mineral_unit','491','万立方米/年','default',b'0',228,b'1','储量单位代码 491；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073229,0,'shaanxi_mineral_unit','492','万立方米/年','default',b'0',229,b'1','储量单位代码 492；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073230,0,'shaanxi_mineral_unit','493','万吨/年','default',b'0',230,b'1','储量单位代码 493；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073231,0,'shaanxi_mineral_unit','494','万吨/年','default',b'0',231,b'1','储量单位代码 494；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073232,0,'shaanxi_mineral_unit','495','万吨/年','default',b'0',232,b'1','储量单位代码 495；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073233,0,'shaanxi_mineral_unit','496','万吨/年','default',b'0',233,b'1','储量单位代码 496；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073234,0,'shaanxi_mineral_unit','497','万立方米/年','default',b'0',234,b'1','储量单位代码 497；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073235,0,'shaanxi_mineral_unit','498','万立方米/年','default',b'0',235,b'1','储量单位代码 498；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073236,0,'shaanxi_mineral_unit','499','万立方米/年','default',b'0',236,b'1','储量单位代码 499；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073237,0,'shaanxi_mineral_unit','500','万立方米/年','default',b'0',237,b'1','储量单位代码 500；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073238,0,'shaanxi_mineral_unit','501','万吨/年','default',b'0',238,b'1','储量单位代码 501；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073239,0,'shaanxi_mineral_unit','502','万立方米/年','default',b'0',239,b'1','储量单位代码 502；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073240,0,'shaanxi_mineral_unit','503','万立方米/年','default',b'0',240,b'1','储量单位代码 503；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073241,0,'shaanxi_mineral_unit','504','万立方米/年','default',b'0',241,b'1','储量单位代码 504；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073242,0,'shaanxi_mineral_unit','505','万立方米/年','default',b'0',242,b'1','储量单位代码 505；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073243,0,'shaanxi_mineral_unit','506','万吨/年','default',b'0',243,b'1','储量单位代码 506；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073244,0,'shaanxi_mineral_unit','507','万吨/年','default',b'0',244,b'1','储量单位代码 507；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073245,0,'shaanxi_mineral_unit','508','万吨/年','default',b'0',245,b'1','储量单位代码 508；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073246,0,'shaanxi_mineral_unit','509','万立方米/年','default',b'0',246,b'1','储量单位代码 509；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073247,0,'shaanxi_mineral_unit','510','万立方米/年','default',b'0',247,b'1','储量单位代码 510；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073248,0,'shaanxi_mineral_unit','511','万立方米/年','default',b'0',248,b'1','储量单位代码 511；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073249,0,'shaanxi_mineral_unit','512','万立方米/年','default',b'0',249,b'1','储量单位代码 512；折算单位：万吨/年；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073250,0,'shaanxi_mineral_unit','513','万立方米/年','default',b'0',250,b'1','储量单位代码 513；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073251,0,'shaanxi_mineral_unit','514','万立方米/年','default',b'0',251,b'1','储量单位代码 514；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073252,0,'shaanxi_mineral_unit','515','万立方米/年','default',b'0',252,b'1','储量单位代码 515；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073253,0,'shaanxi_mineral_unit','516','万立方米/年','default',b'0',253,b'1','储量单位代码 516；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073254,0,'shaanxi_mineral_unit','517','万立方米/年','default',b'0',254,b'1','储量单位代码 517；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073255,0,'shaanxi_mineral_unit','518','万立方米/年','default',b'0',255,b'1','储量单位代码 518；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073256,0,'shaanxi_mineral_unit','519','万吨/年','default',b'0',256,b'1','储量单位代码 519；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073257,0,'shaanxi_mineral_unit','520','万立方米/年','default',b'0',257,b'1','储量单位代码 520；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073258,0,'shaanxi_mineral_unit','521','万立方米/年','default',b'0',258,b'1','储量单位代码 521；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073259,0,'shaanxi_mineral_unit','522','亿立方米/年','default',b'0',259,b'1','储量单位代码 522；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073260,0,'shaanxi_mineral_unit','523','亿立方米/年','default',b'0',260,b'1','储量单位代码 523；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073261,0,'shaanxi_mineral_unit','524','亿立方米/年','default',b'0',261,b'1','储量单位代码 524；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073262,0,'shaanxi_mineral_unit','525','亿立方米/年','default',b'0',262,b'1','储量单位代码 525；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0'),
(7100000000000073263,0,'shaanxi_mineral_unit','526','亿立方米/年','default',b'0',263,b'1','储量单位代码 526；照录《VO 数据对象规范》第十章','1',NOW(),'1',NOW(),b'0');


-- #####################################################################
-- 【来源】2026-09-18-subject-company.sql（字典种子投影；仅抽取 bidding_dict_data 段，建表/菜单/ALTER 已被后续脚本取代）
-- #####################################################################
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002801, 0, 'subject_company_type', 'SELF', '本企业', 'blue', b'0', 1, b'1', '每租户唯一', '1', NOW(), '1', NOW(), b'0'),
(710000000000002802, 0, 'subject_company_type', 'SUBSIDIARY', '子公司', 'default', b'0', 2, b'1', '', '1', NOW(), '1', NOW(), b'0'),
(710000000000002803, 0, 'subject_company_type', 'BRANCH', '分公司', 'default', b'0', 3, b'1', '', '1', NOW(), '1', NOW(), b'0');


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

-- #####################################################################
-- 【来源】2026-09-21-bidding-rule-config-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-21 补齐「规则配置中心」权限点（安许风险联动页 60005500 下的 ⑤ 面板）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 问题（由 sql/tools/check-permission-coverage.js 发现）：
--   后端 RuleConfigController 用 bidding:rule-config:query / bidding:rule-config:update 校验，
--   前端 ⑤ 规则配置面板也用这两个码判权，但 system_menu 里**从未注册**这两个权限点。
--   后果：非超管角色在「角色授权」界面里看不到这两项 → 永远授不出去 →
--         ⑤ 面板（阈值生效值 / 发布与灰度 / 版本历史与回滚 / 读取审计）对普通角色整块不可见，
--         直接调接口也是 403。这类缺口不报错，功能只是"安静地消失"。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 两个按钮型权限点（type=3，挂安许风险联动页 60005500，sort 接在既有 26~29 之后）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005505, '规则配置查询', 'bidding:rule-config:query', 3, 30, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005506, '规则配置修改 / 发布 / 回滚', 'bidding:rule-config:update', 3, 31, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-26-qualification-alert-handle-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-26 新增「企业资质到期预警处理」独立权限点（60005215）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）；
--           《电子资质域-待决策与残留事项清单》④（岗位职责分离审核要求：发现与核销分权）。
--
-- 问题（待决策清单 ④，拍板 2026-09-26）：
--   POST /bidding/qualification/alert/handle 此前复用 bidding:qualification:alert-run
--   （"执行一次扫描"的运维码）。同一码同时控制"触发扫描"与"核销预警"：
--   ① 审计要求"发现"与"核销"分权 —— 给经办人授"处理预警"权限时，连带拿到了
--      "补跑扫描"这种运维动作，权限面过宽；
--   ② 运维补跑扫描的角色（平台运维）不需要也不应该默认能核销企业的预警闭环。
--   故登记独立码 bidding:qualification:alert-handle（本质是按钮级职责分离，
--   与 60005214 alert-run（运维扫描）解耦）。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- 版本：契约《电子证照模块-前端接口契约-v1.0》§1.2 预警清单 / 精读摘要 row 60 同步更新；
--       后端 QualificationController#handleAlert 已切换 @PreAuthorize。
-- ============================================================================

-- ① 按钮型权限点（type=3，挂企业资质页 60005200，sort 接在既有 15（60005214 alert-run）之后）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005215, '预警处理', 'bidding:qualification:alert-handle', 3, 16, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-credential-three-ledger-ia.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- =====================================================================
-- 2026-09-28 电子证照域三台账信息架构落地（菜单终态调整）
--
-- 设计依据：docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md
--   §1.2 判据一（主实体原则）：功能归属看它操作的**主实体**，与名字/接口前缀无关；
--   §1.2 判据三（频率原则）：配置类能力不占台账页，收进「设置」。
--
-- 本次三件事：
--   ① 「证件全生命周期」(60005400) 不再独立占页 → 并入②企业证件台账的「流程」页签；
--   ② 「安许风险联动」(60005500) 不再独立占页 → 企业侧并入①企业资质台账「安许风险与规则」页签、
--      员工侧并入③员工证件台账「安许配备自查」页签、项目关键岗位并入项目台账；
--   ③ 新增「设置」目录，收纳主体档案 / 所属企业 / 规则配置 / CA 签章台账。
--
-- 【为什么不删行、只改父子与类型】沿用仓库既有先例（2026-09-10-menu-retire-crawl-task-page.sql）：
--   权限点是靠 system_menu 行承载的，直接 DELETE 会连带回收 system_role_menu 授权，
--   使已授权角色**静默失去权限**（接口 403、菜单消失但不报错）。
--   因此把"页面(type=2)"降级为"按钮(type=3)"并挂到新的父页面下：
--   **id 不变 → system_role_menu 授权行继续有效**，侧边栏不再出现该页，权限点完整保留。
--
-- 幂等：目标态 UPDATE / INSERT IGNORE，可重复执行。
-- 执行库：ruoyi-vue-pro（system_menu / system_role_menu 同库）。
-- 前置：建议先备份 system_menu / system_role_menu。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第 1 步：「证件全生命周期」页面 → 证照台账(60005100)下的按钮
--   口径：生命周期记录按证照维度查询，入口挂在证照台账下最自然；
--   权限码 bidding:certificate:lifecycle:query 原样保留（后端 @PreAuthorize 未变）。
-- ---------------------------------------------------------------------
UPDATE system_menu
   SET name = '生命周期流程查询',
       type = 3,
       sort = 7,
       parent_id = 60005100,
       path = '',
       component = '',
       component_name = '',
       updater = '1',
       update_time = NOW()
 WHERE id = 60005400
   AND deleted = b'0';

-- 生命周期下的 4 个按钮：父级从 60005400 迁到 60005100（id 与权限码均不变）
UPDATE system_menu
   SET parent_id = 60005100, updater = '1', update_time = NOW()
 WHERE id IN (60005401, 60005402, 60005403, 60005404)
   AND deleted = b'0';

-- ---------------------------------------------------------------------
-- 第 2 步：「安许风险联动」页面 → 企业资质(60005200)下的按钮
--   企业侧（风险卡片 / 风险大盘 / 风险工单 / 规则配置）归企业资质台账；
--   员工侧与项目侧已分别由各自台账承载（见前端 safety-risk 页的区块开关）。
-- ---------------------------------------------------------------------
UPDATE system_menu
   SET name = '安许风险联动（企业侧）',
       type = 3,
       sort = 16,
       parent_id = 60005200,
       path = '',
       component = '',
       component_name = '',
       updater = '1',
       update_time = NOW()
 WHERE id = 60005500
   AND deleted = b'0';

-- 安许页下的按钮：父级迁到 60005200（id 与权限码不变）
--   60005501 风险重算/宽限、60005502 多岗兼任、60005503 人员风险标签、
--   60005504 项目关键岗位、60005505/506 规则配置、60005509~512 风险工单
-- 注：60005502/503 的实际入口现在员工证件台账、60005504 在项目台账，
--   但**权限点本身仍属该用户角色能力**，故只改父级、不改权限码，避免授权失效。
UPDATE system_menu
   SET parent_id = 60005200, updater = '1', update_time = NOW()
 WHERE id IN (60005501, 60005502, 60005503, 60005504, 60005505, 60005506,
              60005509, 60005510, 60005511, 60005512)
   AND deleted = b'0';

-- ---------------------------------------------------------------------
-- 第 3 步：新增「设置」目录 + 4 个配置类页面
-- 号段：60006000 起（60005xxx 已被电子证照域页面占用）。各子块：主体档案 600061xx、
--   所属企业 600062xx、CA 签章台账 600064xx、规则配置 600065xx（600063xx 已让给权限审计批次，见下）。
--   判据三（频率原则）：这四项都**不管理某个业务实体**，而是被三本台账共同消费，故进设置。
--   组件路径与前端文件一一对应（前端 pageMap = import.meta.glob('../views/**/*.vue')）：
--     bidding/subjectProfile/index        → views/bidding/subjectProfile/index.vue（既有页）
--     bidding/settings/company/index      → views/bidding/settings/company/index.vue（本次新增）
--     bidding/settings/rule-config/index  → views/bidding/settings/rule-config/index.vue（本次新增）
--     bidding/caSeal/index                → views/bidding/caSeal/index.vue（既有页）
--   path 段用 settings- 前缀，避免与既有路由冲突。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006000, '设置', '', 1, 1200, 0, 'cert-settings', 'ep:setting', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006100, '主体档案（企业自维护）', 'bidding:subject:query', 2, 1, 60006000, 'settings-subject', NULL, 'bidding/subjectProfile/index', 'SubjectProfileIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006200, '所属企业', 'bidding:subject-company:query', 2, 2, 60006000, 'settings-company', NULL, 'bidding/settings/company/index', 'SettingsCompanyIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- ⚠️ 号段让位（2026-09-28 冲突排查）：规则配置原计划用 60006300/60006301，但
--   sql/history/2026-09-29-bidding-missing-perm-menus.sql（权限审计补全，另一批次产出）
--   已把 60006300~60006303 登记为「主体认证查询/审核」「招标文件下载名单/记录」。
--   两脚本同段不同义 = 后执行者覆盖前者，菜单名与权限会**静默错位** ——
--   正是 2026-09-18-cert-menu-renumber.sql 记录过的那类事故（60002105 被改名导致爬虫域权限丢失）。
--   按仓库既有口径「先登记再占用」让出：本域规则配置改用空闲的 60006500 段。
(60006500, '规则配置', 'bidding:rule-config:query', 2, 3, 60006000, 'settings-rule-config', NULL, 'bidding/settings/rule-config/index', 'SettingsRuleConfigIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006400, 'CA 签章台账', 'bidding:ca:query', 2, 4, 60006000, 'settings-ca-seal', NULL, 'bidding/caSeal/index', 'SettingsCaSealIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 写权限点：主体档案维护 / 所属企业增删改 / 规则配置修改 / CA 验签
--   （读权限由上面的页面行承载；写权限为按钮，便于按"只读角色"单独授权）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006101, '主体档案维护', 'bidding:subject:update', 3, 1, 60006100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- ⚠️ 号段让位（2026-09-28 冲突排查）：所属企业的三个写按钮原计划用 60006201~6203，
--   但 600062xx 已被 2026-09-28 那批流程子域脚本占满（tender-doc 6201~6208 / deposit 6209~6211 /
--   contract 6212~6213 / objection 6214~6216 / report 6217~6218 / ca-seal 6219~6220 /
--   prequal-committee 6221 / process-track 6222）。
--   同段不同义会让后执行者覆盖前者（菜单名与权限静默错位），故按「先登记再占用」让到空闲的 600066xx。
(60006601, '新增所属企业', 'bidding:subject-company:create', 3, 1, 60006200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006602, '修改所属企业', 'bidding:subject-company:update', 3, 2, 60006200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006603, '删除所属企业', 'bidding:subject-company:delete', 3, 3, 60006200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006501, '规则配置修改 / 发布 / 回滚', 'bidding:rule-config:update', 3, 1, 60006500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006401, 'CA 验签', 'bidding:ca:verify', 3, 1, 60006400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 第 3b 步：补注册两个"前端已判权但从未登记"的权限点
--   来源：sql/tools/check-permission-coverage.js 报出「3 个权限码未在 system_menu 注册」。
--   经查后端 SafetyPermitRiskController 当前只有 query/assign/handle/alert 四个码：
--     · bidding:safety-risk:scan ← POST /bidding/safety-risk/scan（按启用规则重算风险）
--     · bidding:safety-risk:rule ← /bidding/risk-rule/{page,create,update,status,delete}（风险规则维护）
--   这两个端点**后端尚未实现**（前端按契约先行接入，未就绪时页面空态）。现在登记的理由：
--   不登记 → 角色授权界面里看不到这两项 → 后端上线后**永远授不出去**，按钮对普通角色恒不可见。
--   （同类缺口此前已在 2026-09-21-bidding-rule-config-menu.sql 踩过一次，处理口径一致。）
--   归属：两者都作用于**企业安许风险**，故挂企业资质页(60005200)下。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005513, '风险重算扫描', 'bidding:safety-risk:scan', 3, 35, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005514, '风险规则维护', 'bidding:safety-risk:rule', 3, 36, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-29-权限审计批次-菜单与授权统一.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-29 权限审计批次 —— 菜单登记 + 菜单项归位/去重 + 授权（统一脚本）
--
-- 合并来源（原文件已删除，仅保留本终态）：
--   · 2026-09-29-bidding-missing-perm-menus.sql（4 个缺失权限点登记）
--   · 2026-09-29-菜单祖先授权补齐.sql（菜单祖先授权断链修复）
--
-- 【打包约定】本文件按 sql/tools/build-credential-sync.js 的 MENU_EXTRA_SOURCES 口径编写：
--   ① 授权段（节标题为「角色授权」的那一行）之前 = 菜单/权限点注册 → 并入一键包 master-credential-sync.sql；
--   ② 该行之后 = 授权语句，属部署态业务决策，**不进一键包**；
--   ③ 校验段（节标题为「校验」的那一行）处打包器截断。
--   ⚠️ 教训：打包器用 indexOf 找那两个节标题标记，**注释里也不能写标记原文** ——
--      本文件首版把标记原文写进了本段说明，结果正文被"在注释里"提前截断、整段菜单注册被静默丢弃
--      （2026-09-29 实测：镜像门禁报「缺 4 项」）。故本段一律用「」描述，不复制标记。
--   改动本文件后须重跑：node sql/tools/build-credential-sync.js（重建）→ check-master-mirror / check-permission-coverage。
--
-- 【与三台账重构的关系】2026-09-28-credential-three-ledger-ia.sql 把 主体档案 从 6760 迁到
--   设置目录(60006000)下的新页 60006100。故本脚本：登记时用**重构前的既有父页 6760**（保证在任何库都能挂上），
--   再用条件 UPDATE 在「重构已落地（60006100 存在）」时把主体认证两行归位到 60006100 —— 两种库都正确，可重复执行。
--
-- 【号段归属（先登记再占用）】60006300~60006303 = 本批次；60006000~60006101 / 60006200 /
--   60006400~60006501 / 60006601~60006603 = 三台账重构批次；600062xx 其余 = 流程子域批次。
--   禁止再向 600063xx 登记其他语义（同段不同义会导致菜单名与权限静默错位）。
-- ============================================================================

-- ========== ① 4 个缺失权限点登记（审计：代码 @PreAuthorize 有、system_menu 无承载 → 非超管恒 403） ==========
-- 主体认证 SubjectAuthController → 主体档案页 6760 下；招标文件下载 TenderDocDownloadController → 项目台账 6771 下。
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006300, '主体认证查询', 'bidding:subject-auth:query', 3, 100, 6760, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006301, '主体认证审核', 'bidding:subject-auth:update', 3, 101, 6760, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006302, '招标文件下载名单', 'bidding:tender-doc:query', 3, 102, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006303, '记录招标文件下载', 'bidding:tender-doc:download', 3, 103, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ========== ①b 菜单项归位：三台账重构落地后，主体认证两行迁到新主体档案页 60006100 ==========
-- 条件 UPDATE：重构未执行时不动（当前库即此状态），可重复执行。
UPDATE `system_menu` SET `parent_id` = 60006100, `updater` = '1', `update_time` = NOW()
 WHERE `id` IN (60006300, 60006301) AND `deleted` = b'0'
   AND EXISTS (SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `id` = 60006100 AND `deleted` = b'0') t);

-- ========== ①c 菜单项去重：三台账重构落地后，旧页 6760/6761 让位给 60006100/60006101 ==========
-- 问题：重构把 主体档案 新建成 60006100（permission 与旧页 6760 **同码** bidding:subject:query），
--   旧页仍在 → 侧边栏出现两个「主体档案」、角色授权界面出现两个同码权限点。
-- 口径（沿用仓库先例，勿改成 DELETE）：权限点靠 system_menu 行承载，直接 DELETE 会连带回收
--   system_role_menu 授权 → 已授权角色**静默失权**（接口 403、菜单消失但不报错）；
--   故把旧页**降级为按钮**（type=3、清空 path/component，id 不变 → 授权行继续有效），侧边栏不再重复。
UPDATE `system_menu`
   SET `type` = 3, `sort` = 99, `path` = '', `component` = '', `component_name` = '',
       `name` = '主体档案（旧入口·已并入设置）', `updater` = '1', `update_time` = NOW()
 WHERE `id` = 6760 AND `deleted` = b'0'
   AND EXISTS (SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `id` = 60006100 AND `deleted` = b'0') t);

UPDATE `system_menu` SET `parent_id` = 60006100, `updater` = '1', `update_time` = NOW()
 WHERE `id` = 6761 AND `deleted` = b'0'
   AND EXISTS (SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `id` = 60006100 AND `deleted` = b'0') t);

-- #####################################################################
-- 【来源】2026-09-16-bid-review-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- =====================================================================
-- 智慧标书审查：管理端菜单 + 权限点（含个人端自助权限点）
-- 方案依据：docs/模块设计与规范/智慧标书审查-设计方案.md §5（接口清单）/ §7.3（菜单与权限 SQL）
-- 规范依据：《招投标模块开发规范》§4.4（权限注解 @PreAuthorize + bidding:xxx:yyy）
-- 设计说明：
--   1) 管理端菜单挂在既有「招投标」目录（id=6735）下，新目录 id 段 60007000 起
--      （60002xxx/60005000 段已被证照/资质占用，避免复用造成"同名不同物"）；
--   2) 个人端（门户）页面是硬编码路由（router/routes/core.ts 的 PortalBidding*），
--      不建菜单，只在这里登记权限点，供角色授权后由 usePortalCan 控制按钮；
--   3) 幂等：INSERT IGNORE，可重复执行；
--   4) 超级管理员默认拥有全部菜单，普通角色需另行授权 system_role_menu（属业务决策，本脚本不代授）。
-- 执行库：ruoyi-vue-pro
-- =====================================================================

INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 页面：智慧标书审查（直接挂在「招投标」6735 下，**一级页面**，点一下就进）
-- 说明：原先做成"二级目录 + 子页面"，在「招投标」展开后的长列表里既排在中段、又要再点一层，
--   用户反馈"管理端看不到"（2026-09-16），故拍平为直接可点的页面菜单，URL = /bidding/bid-review。
(60007000, '智慧标书审查', 'bidding:bid-review:query', 2, 40, 6735, 'bid-review', 'ep:document-checked', 'bidding/bidReview/task/index', 'BidReviewTaskIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 按钮权限（挂在页面菜单下；60007100 保留为"查询"权限位，避免与历史行冲突）
(60007100, '审查任务查询', 'bidding:bid-review:query', 3, 1, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007101, '一键生成/重跑', 'bidding:bid-review:generate', 3, 2, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007102, '任务取消/重试', 'bidding:bid-review:update', 3, 3, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007103, '结果复核', 'bidding:bid-review:audit', 3, 4, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 个人端（门户）自助权限点：无菜单，仅授权位（type=3）
(60007400, '个人端-上传并一键生成', 'bidding:bid-review:self:create', 3, 9, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007401, '个人端-查看我的审查记录', 'bidding:bid-review:self:query', 3, 10, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007402, '个人端-删除我的记录', 'bidding:bid-review:self:delete', 3, 11, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 说明：文件上传复用既有 POST /bidding/file/upload，其权限点为 bidding:file:upload，
--      若个人端角色未授予该权限，可将 60007400 的权限码改为 bidding:file:upload 另建一条：
-- INSERT IGNORE INTO `system_menu` (...) VALUES (60007403, '个人端-上传文件', 'bidding:file:upload', 3, 12, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 结构校正（幂等）：已按旧结构（二级目录 + 子页面）建过菜单的环境，执行以下语句即可拍平。
-- 新环境直接跑上面的 INSERT 即为终态，本段不会产生额外影响。
-- ---------------------------------------------------------------------
-- ---------------------------------------------------------------------
-- 授权：把本功能的菜单与权限点授予【所有角色】（用户 2026-09-16 明确要求"给所有角色授权"）。
-- 说明：
--   1) 会连租户角色一起授权 —— 即租户用户也能在管理后台看到「智慧标书审查」；
--      只想给特定角色时，把 WHERE 改成 r.code IN ('common') 之类即可。
--   2) 幂等：system_role_menu 没有 (role_id, menu_id) 唯一键，故用 NOT EXISTS 防重复插入。
--   3) 回滚（如需）：
--      DELETE rm FROM `system_role_menu` rm JOIN `system_menu` m ON m.id = rm.menu_id
--      WHERE m.id BETWEEN 60007000 AND 60007999;
-- ---------------------------------------------------------------------

-- #####################################################################
-- 【来源】2026-09-17-bid-review-admin-reorg.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-17 标书审查 · 管理端重新设计（前四个模块落地菜单）
--
-- 方案依据：docs/模块设计与规范/标书审查-管理端重新设计.md
--   §4.2 菜单与权限点定稿；第六章 SQL 骨架。
--
-- 本次改动（只做前四个模块：审查任务 / 问题复核 / 审查规则库 / 报告与统计）：
--   ① 新增页面 60007450「问题复核」——跨任务待复核工作台（前端 issue/IssuePanel.vue）；
--   ② 重排目录下页面顺序：审查任务(1) / 问题复核(2) / 审查规则库(3) / 报告与统计(4)；
--   ③ 60007103「结果复核」→ 改名「问题项复核」并上移到目录 60007000：
--      该动作现在被「审查任务详情」与「问题复核」两个页面共用，
--      挂在各自页面下会产生两个同权限码的重复点（正是本次要消除的问题）；
--   ④ 新页面按现有口径授权（与 60007000 目录的授权角色对齐）。
--
-- 为什么 id 一律不变：system_role_menu 的 (role_id, menu_id) 语义必须保持，
-- 只改 parent_id / sort / name，才能做到"零授权回归"。
--
-- 未包含（属第五个模块「AI 与运行配置」，按用户要求本次不做）：
--   60007460 页面、60007302「AI 配置维护」的父级迁移。
--
-- 幂等：INSERT IGNORE + UPDATE，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-bidding-sync.sql 同库）。
-- ============================================================================

-- ① 新增页面：问题复核（path=issue，组件 bidding/bidReview/issue/IssuePanel.vue）
INSERT IGNORE INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
   `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (60007450, '问题复核', 'bidding:bid-review:query', 2, 2, 60007000, 'issue', NULL,
   'bidding/bidReview/issue/IssuePanel', 'BidReviewIssuePanel', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 页面顺序：审查任务(1) / 问题复核(2) / 审查规则库(3) / 报告与统计(4)
UPDATE `system_menu` SET `sort` = 3, `updater` = '1', `update_time` = NOW() WHERE `id` = 60007200;
UPDATE `system_menu` SET `sort` = 4, `updater` = '1', `update_time` = NOW() WHERE `id` = 60007300;

-- ③ 「结果复核」→「问题项复核」，挂到两页的共同父目录 60007000
UPDATE `system_menu`
   SET `parent_id` = 60007000, `name` = '问题项复核', `type` = 3,
       `updater` = '1', `update_time` = NOW()
 WHERE `id` = 60007103 AND `deleted` = b'0';

-- ④ 授权：新页面与 60007000 目录的现有授权对齐（不可见目录 → 不插入任何行，属预期）

-- #####################################################################
-- 【来源】2026-09-20-qualification-update-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-20 补登「企业资质修改」权限点（60005216）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）；
--           《企业资质页-页面级契约-v1.0.md》§4 G2（详情/编辑独立页）。
--
-- 问题（资质域权限点漏登记）：
--   前端「企业资质」详情页的「编辑资质 / 保存 / 保存并提交审批」以及新增的
--   PUT /bidding/qualification/update，权限码为 bidding:qualification:update。
--   但该码此前**从未登记进 system_menu** —— 资质域的既有登记只到
--   query/create/renew/approve/reject/standard/element/review/permit/alert-run/alert-handle
--   （见 master-credential-sync.sql 60005200~60005215）。
--
--   对照先例：证照台账（bidding:certificate:update）早在 60005102 就登记了。
--   本码属于同类漏登记，不是新增业务能力。
--
--   影响（为什么要补）：前端 can() 的实现是「accessCodes 为空则放行，否则 hasAccessByCodes([code])」。
--   未登记的权限码对普通角色恒为 false → 「编辑资质」按钮永不渲染，
--   只有超管（*:*:*）能看到并使用，形成「超管能用、业务角色全看不见」的畸形状态。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- 版本：后端 QualificationController#update 同步以 @PreAuthorize('bidding:qualification:update') 落地。
-- ============================================================================

-- ① 按钮型权限点（type=3，挂企业资质页 60005200，sort 接在既有 16（60005215 alert-handle）之后）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005216, '资质修改', 'bidding:qualification:update', 3, 17, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-prequal-committee.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- ============================================================================
-- 2026-09-28 资格审查委员会强制组建（《实施条例》第18条，国有资金项目）
--
-- 规范依据：《电子招标投标全流程说明书》§6.3 第 3 条（国有资金项目强制要求组建
--   资格审查委员会，否则禁止进入审查）；《实施条例》第18条（国有资金占控股或主导
--   的依法必招项目应组建资格审查委员会）、第37条（评标/资格审查委员会由招标人代表
--   与技术经济专家组成，5人以上单数，专家≥2/3）。
--
-- 背景（说明书 §6.6 待补项）：此前资格预审域无「资格审查委员会」实体与强制校验，
--   国有资金项目未组建委员会即可审查，违反条例第18条。本脚本补齐：
--   ① bidding_prequal_committee 资格审查委员会成员表（子表，按资格预审挂）；
--   ② fund_source 资金来源字典（bidding_dict_data 种子；项目登记 FUND_SOURCE 字段
--      由自由文本改为字典代码，01/02/03 为国有资金，供后端判定强制组建）；
--   ③ 菜单权限点 bidding:prequal:committee（挂项目台账 6771）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- 执行库：ruoyi-vue-pro（与既有 bidding_* 表同库）。
-- ============================================================================

-- ① 资格审查委员会成员表（子表）
CREATE TABLE IF NOT EXISTS `bidding_prequal_committee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `prequal_id` bigint NOT NULL COMMENT '资格预审ID（bidding_prequal.id）',
  `member_name` varchar(64) NOT NULL COMMENT '成员姓名',
  `member_category` varchar(32) NOT NULL COMMENT '成员类别（TENDERER_REP 招标人代表 / TECH_EXPERT 技术专家 / ECON_EXPERT 经济专家）',
  `member_role` varchar(32) DEFAULT NULL COMMENT '委员会内角色（LEADER 组长 / MEMBER 成员）',
  `member_org` varchar(256) DEFAULT NULL COMMENT '所在单位',
  `member_title` varchar(128) DEFAULT NULL COMMENT '职称/职务',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_prequal` (`prequal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资格审查委员会成员表（条例第18条：国有资金项目强制组建）';

-- ② 资金来源字典（bidding_dict_data 种子；01/02/03 = 国有资金占控股或主导）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000007701,0,'fund_source','01','财政性资金','primary',b'0',1,b'1','国有资金（财政预算内/外，含转移支付）；条例第18条触发资格审查委员会强制组建','1',NOW(),'1',NOW(),b'0'),
(710000000000007702,0,'fund_source','02','国有企事业单位资金','primary',b'0',2,b'1','国有资金（国有企事业单位自有资金）；触发资格审查委员会强制组建','1',NOW(),'1',NOW(),b'0'),
(710000000000007703,0,'fund_source','03','国有控股/主导混合资金','primary',b'0',3,b'1','国有资金（国有资本占控股或主导地位的混合资金）；触发资格审查委员会强制组建','1',NOW(),'1',NOW(),b'0'),
(710000000000007704,0,'fund_source','04','非国有资金','default',b'0',4,b'1','非国有资金（民营/社会资本）','1',NOW(),'1',NOW(),b'0'),
(710000000000007705,0,'fund_source','99','其他','default',b'0',5,b'1','其他资金来源','1',NOW(),'1',NOW(),b'0');

-- ③ 菜单权限点：资格审查委员会组建（type=3 按钮，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006221, '资格审查委员会组建', 'bidding:prequal:committee', 3, 18, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ④ 角色授权：沿用所属页面菜单 6771（项目台账）的既有授权 ——
--    能看到「项目台账」页的角色，同时拿到这个按钮权限（按钮继承页面授权，手法同
--    2026-09-28 tender-doc-prequal-menu）。之后仍可在「角色授权」界面单独调整。

-- #####################################################################
-- 【来源】2026-09-28-bid-deposit-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-28 补齐「投标保证金」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（《电子招标投标全流程说明书》§7 保证金登记断点补齐后）：
--   后端新增 BidDepositController（bidding:deposit:save/query），共 2 个 @PreAuthorize 权限码；
--   若不在 system_menu 注册，非超管角色在「角色授权」界面看不到这些权限点 → 授不出去 → 403。
--
-- 挂载口径：投标保证金是「招标项目」（项目台账 6771）的子功能，故 2 个按钮权限点统一挂
--   6771 下，sort 接在既有资格预审（17）之后（18~19）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 2 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006209, '登记投标保证金', 'bidding:deposit:save', 3, 18, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006210, '投标保证金台账', 'bidding:deposit:query', 3, 19, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006211, '退还投标保证金', 'bidding:deposit:refund', 3, 20, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-contract-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-28 补齐「工程合同」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（《电子招标投标全流程说明书》§11 合同签署断点补齐后）：
--   后端新增 ContractController（bidding:contract:save/query），共 2 个 @PreAuthorize 权限码。
--
-- 挂载口径：工程合同是「招标项目」（项目台账 6771）的子功能，故 2 个按钮权限点统一挂
--   6771 下，sort 接在既有保证金退还（20）之后（21~22）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 2 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006212, '登记工程合同', 'bidding:contract:save', 3, 21, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006213, '工程合同台账', 'bidding:contract:query', 3, 22, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-objection-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-28 补齐「投标异议」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（条例第22条 异议子域补齐后）：
--   后端新增 ObjectionController（bidding:objection:propose/reply/query），共 3 个 @PreAuthorize 权限码。
--
-- 挂载口径：异议是「招标项目」（项目台账 6771）的子功能，故 3 个按钮权限点统一挂
--   6771 下，sort 接在既有工程合同（22）之后（23~25）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 3 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006214, '提出异议', 'bidding:objection:propose', 3, 23, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006215, '答复异议', 'bidding:objection:reply', 3, 24, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006216, '异议台账', 'bidding:objection:query', 3, 25, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-report-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- 编码声明：强制 utf8mb4（防乱码）
-- ============================================================================
-- 2026-09-28 补齐「定标书面报告」权限点（挂项目台账 6771 下）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）。
--
-- 背景（招标投标法第47条 定标书面报告子域补齐后）：
--   后端新增 ReportController（bidding:report:submit/query），共 2 个 @PreAuthorize 权限码。
--
-- 挂载口径：报告是「招标项目」（项目台账 6771）的子功能，故 2 个按钮权限点统一挂
--   6771 下，sort 接在既有异议（25）之后（26~27）。
-- 幂等：菜单 INSERT IGNORE（按 id 去重）+ 授权按 (role_id, menu_id) 语义去重，可重复执行。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- ============================================================================

-- ① 2 个按钮型权限点（type=3，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006217, '提交书面报告', 'bidding:report:submit', 3, 26, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006218, '报告台账', 'bidding:report:query', 3, 27, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-cert-lifecycle-urge-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 生命周期催办权限点（五页改造缺接口契约 Q15 配套）
--
-- 规范依据：《招投标模块开发规范》4.2（权限点复用与菜单同步）、4.4（菜单/权限码与 sql/ 脚本同步）；
--           五页改造缺接口契约 Q15（催办 POST /bidding/cert-lifecycle/urge；同日幂等不叠加）。
--
-- 幂等：菜单 INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1，租户 0）默认放行全部权限，无需授权（先例 60005215 alert-handle 同口径）。
-- 执行库：ruoyi-vue-pro（与 master-credential-sync.sql 同库）。
-- =====================================================================

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005404, '生命周期催办（提醒审核）', 'bidding:certificate:lifecycle:urge', 3, 30, 60005400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-certificate-import-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 证照 Excel 导入权限点（五页改造缺接口契约 Q1 配套）
--
-- 规范依据：Q1 契约——POST /bidding/certificate/import（Excel、行级回执、模板下载）；
--           权限点 bidding:certificate:import。
-- 配套端点：GET /bidding/certificate/import-template（模板下载，无需授权）。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005105 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005106, '证照导入', 'bidding:certificate:import', 3, 7, 60005100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-personnel-cert-renew-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 员工证书批量续期权限点（五页改造缺接口契约 Q11 配套）
--
-- 规范依据：Q11 契约——POST /bidding/personnel-certificate/batch-renew（逐条回执）；
--           权限点建议 bidding:personnel:cert:renew。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005215 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005310, '员工证书批量续期', 'bidding:personnel:cert:renew', 3, 21, 60005300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-qualification-import-export-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 资质导入/导出权限点（五页改造缺接口契约 Q6 配套）
--
-- 规范依据：Q6 契约——资质导入/导出，权限 bidding:qualification:import/export。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005204 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005205, '资质导入', 'bidding:qualification:import', 3, 10, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005206, '资质导出', 'bidding:qualification:export', 3, 10, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- #####################################################################
-- 【来源】2026-09-28-safety-risk-alert-menu.sql（菜单与权限点补充；已并入本包；授权段已按口径截除）
-- #####################################################################
-- =====================================================================
-- 2026-09-28 安许风险条目一键预警权限点（五页改造缺接口契约 Q22 配套）
--
-- 规范依据：Q22 契约——POST /bidding/safety-permit/risk/alert/batch（所选条目批量预警，幂等）；
--           权限点 bidding:safety-risk:alert（与 Q18/Q19 同父级 60005500）。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 授权：超级管理员（role_id=1）默认放行全部权限，无需授权（先例 60005511 同口径）。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- id 用 60005512（60005508 号段整理：Q18/Q19 菜单改用 60005509~511，本按钮顺延 512）
(60005512, '一键预警', 'bidding:safety-risk:alert', 3, 34, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

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
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('bidding_bid_review_ai_log','bidding_bid_review_content','bidding_bid_review_issue','bidding_bid_review_report','bidding_bid_review_rule_item','bidding_bid_review_rule_set','bidding_bid_review_task','bidding_dict_data','bidding_personnel_profile','bidding_prequal_committee','bidding_review_task','bidding_rule_config','bidding_rule_config_audit','bidding_rule_config_history','cert_alert_config','cert_alert_records','cert_change_logs','cert_file_versions','cert_lifecycle_record','cert_notify_outbox','cert_verify_history','certificate_info','enterprise_qualification','personnel_certificate','personnel_job_change_log','personnel_labor_contract','personnel_multi_post_check','personnel_social_security','personnel_training_record','project_key_post_assignment','qualification_apply_record','qualification_asset','qualification_compliance_review','qualification_performance','qualification_safety_permit','qualification_staff_config','qualification_standard','safety_permit_risk_snapshot','safety_risk_item')
-- 期望：39 张表（包内 CREATE TABLE 数）
UNION ALL SELECT '菜单与权限点(60005~60007 段)', COUNT(*) FROM system_menu WHERE id BETWEEN 60005000 AND 60007999 AND deleted = b'0'
-- 期望：98 行（目录 + 页面 + 按钮权限点；覆盖 60005/60006/60007 全部号段）
UNION ALL SELECT '默认规则阈值', COUNT(*) FROM bidding_rule_config WHERE deleted = b'0'
-- 期望：31 行（证照阈值 + 安许风险参数；重复执行时已存在行走 ON DUPLICATE KEY UPDATE，不新增）
UNION ALL SELECT '枚举字典行', COUNT(*) FROM bidding_dict_data WHERE deleted = b'0';
-- 期望：1827 行（证照/资质/人员/合同/公告/代码表等全部枚举字典；含 ON DUPLICATE KEY UPDATE 类型的幂等种子）
