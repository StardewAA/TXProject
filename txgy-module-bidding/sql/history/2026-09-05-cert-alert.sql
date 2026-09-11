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
    `cert_id` bigint NOT NULL COMMENT '关联证照 certificate_info.id',
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
    KEY `idx_alert_handle` (`tenant_id`, `handled`, `triggered_at` DESC)
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
