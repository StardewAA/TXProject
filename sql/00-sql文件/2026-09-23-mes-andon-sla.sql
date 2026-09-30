-- =============================================================
-- MES 安灯 SLA + 超时升级 + 通知 + 业务事件自动建单
-- 对应三件套：① 安灯 SLA/升级/通知 ② 业务事件自动转安灯 ③ 现场异常统一看板
-- 执行时机：在已存在 mes_pro_andon_config / mes_pro_andon_record 表之后
-- =============================================================

-- 1) 安灯配置表：增加 SLA / 升级角色 / 通知模板 / 触发类型
ALTER TABLE mes_pro_andon_config
    ADD COLUMN sla_minutes      INT          NULL COMMENT 'SLA 超时分钟数（超时未处置触发升级通知）；空表示不启用',
    ADD COLUMN escalate_role_id BIGINT       NULL COMMENT '升级责任角色编号（超时后责任升级到的角色，仅记录展示）',
    ADD COLUMN notify_template_code VARCHAR(64) NULL COMMENT '通知模板编码（超时升级/系统自动建单时发送站内信）',
    ADD COLUMN trigger_type     INT          NOT NULL DEFAULT 0 COMMENT '触发类型：0 人工呼叫 / 1 质检不合格 / 2 物料缺料 / 3 工单超期';

-- 2) 安灯记录表：增加应升级时间 / 已升级 / 触发类型
ALTER TABLE mes_pro_andon_record
    ADD COLUMN should_escalate_time DATETIME NULL COMMENT '应升级时间（创建时间 + 配置 SLA 分钟）',
    ADD COLUMN escalated        INT          NOT NULL DEFAULT 0 COMMENT '是否已超时升级：0 未升级 / 1 已升级',
    ADD COLUMN trigger_type     INT          NOT NULL DEFAULT 0 COMMENT '触发类型：0 人工呼叫 / 1 质检不合格 / 2 物料缺料 / 3 工单超期';

-- 3) 字典：安灯触发类型（与 DictTypeConstants.MES_PRO_ANDON_TRIGGER_TYPE 对应）
INSERT INTO system_dict_data (dict_type, label, value, sort, status, remark, create_time)
SELECT 'mes_pro_andon_trigger_type', '人工呼叫', '0', 1, 0, '人工点灯', NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'mes_pro_andon_trigger_type' AND value = '0');
INSERT INTO system_dict_data (dict_type, label, value, sort, status, remark, create_time)
SELECT 'mes_pro_andon_trigger_type', '质检不合格', '1', 2, 0, '质检不合格自动建单', NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'mes_pro_andon_trigger_type' AND value = '1');
INSERT INTO system_dict_data (dict_type, label, value, sort, status, remark, create_time)
SELECT 'mes_pro_andon_trigger_type', '物料缺料', '2', 3, 0, '缺料自动建单', NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'mes_pro_andon_trigger_type' AND value = '2');
INSERT INTO system_dict_data (dict_type, label, value, sort, status, remark, create_time)
SELECT 'mes_pro_andon_trigger_type', '工单超期', '3', 4, 0, '工单超期自动建单', NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'mes_pro_andon_trigger_type' AND value = '3');

-- 4) 通知模板：超时升级 / 系统自动建单（芋道 system_notify_template；模板内容可按实际调整）
--    注意：芋道通知模板需在「系统管理-通知管理」配置，或插入 system_notify_template 并关联 system_notify_template_type。
--    以下仅给出参考，实际模板编码需与 MesProAndonConfigDO.notifyTemplateCode 一致（示例 MES_ANDON_TIMEOUT）。
INSERT INTO system_notify_template (code, name, nickname, content, `type`, status, remark, create_time, update_time)
SELECT 'MES_ANDON_TIMEOUT', 'MES 安灯超时/异常提醒', '安灯提醒', '您有一个未处置的安灯（编号 ${andonId}），原因：${reason}，已超时请尽快处理。', 1, 1, 'MES 安灯 SLA 超时升级/系统自动建单通知', NOW(), NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'MES_ANDON_TIMEOUT');
