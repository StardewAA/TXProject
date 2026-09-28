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
    `idempotent_key` varchar(64) NOT NULL COMMENT '幂等键：<种类>:cert_id:channel:<窗口桶起点秒>（种类=INFO/FILE），仅作并发兜底；幂等主判定是"滑动窗口内最近一次验真"，窗口来自配置 cert.verify.idempotent.window.seconds（v3.3 §17.5 UNIQUE）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_verify_idempotent` (`idempotent_key`),
    KEY `idx_verify_cert_time` (`tenant_id`, `cert_id`, `verified_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '验真历史表（T 轨 · 只追加 · 幂等防重）';
