-- ============================================================================
-- CRM 商机标准化改造：商机阶段 / 商机状态组 / 预估赢单率 / 丢单原因 / 赢单签约信息
-- 日期：2026-09-18
--
-- 标准方案：
--   1. 「商机阶段」只表达推进过程（固定 5 个），赢单/输单不再作为阶段，改由「赢单」「输单」按钮驱动；
--   2. 「商机状态组」为只读派生字段：未开始 / 进行中 / 已赢单 / 已丢单；
--   3. 「预估赢单率」由阶段自动计算（5/10/40/60/70），赢单=100、输单=0；
--   4. 「丢单原因」仅输单弹窗必填，表单隐藏；
--   5. 赢单需填写签约日期与签约金额。
--
-- 说明：
--   1. 旧的 status_type_id / status_id（可配置状态组+阶段）保留为历史字段，商机不再使用；
--      旧「商机状态组配置」页与接口保留，避免影响历史数据。
--   2. 存量数据按原阶段名迁移到新阶段；原「赢单/输单」阶段行按结束处理；
--      迁移为「已丢单」的历史数据没有丢单原因，统一置为 6（其他），可按需修正。
--   3. 本脚本幂等，可重复执行。
--
-- 执行方式（务必带 utf8mb4）：
--   docker cp sql/2026-09-18-crm-business-standard-stage.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' ruoyi-vue-pro < /tmp/2026-09-18-crm-business-standard-stage.sql"
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. crm_business 新增字段（幂等）
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_business' AND COLUMN_NAME = 'stage');
SET @ddl := IF(@c = 0,
    'ALTER TABLE `crm_business`
        ADD COLUMN `stage` tinyint NULL COMMENT ''商机阶段：1商机待评估 2初步接洽 3需求确认 4方案评估 5商务谈判'' AFTER `status_id`,
        ADD COLUMN `business_state` tinyint NULL COMMENT ''商机状态组：1未开始 2进行中 3已赢单 4已丢单'' AFTER `stage`,
        ADD COLUMN `win_rate` tinyint NULL COMMENT ''预估赢单率(%)：随阶段自动计算，赢单=100 输单=0'' AFTER `business_state`,
        ADD COLUMN `lost_reason` tinyint NULL COMMENT ''丢单原因：1客户预算不足/预算取消 2客户选择竞品 3项目无限期搁置 4客户内部决策人变更 5客户需求重大变更 6其他'' AFTER `win_rate`,
        ADD COLUMN `lost_remark` varchar(500) NULL COMMENT ''丢单备注'' AFTER `lost_reason`,
        ADD COLUMN `sign_date` datetime NULL COMMENT ''签约日期（赢单时填写）'' AFTER `lost_remark`,
        ADD COLUMN `sign_amount` decimal(24,6) NULL COMMENT ''签约金额（赢单时填写）'' AFTER `sign_date`,
        ADD COLUMN `closed_at` datetime NULL COMMENT ''关闭时间（赢单/丢单时间）'' AFTER `sign_amount`,
        ADD COLUMN `closed_by` bigint NULL COMMENT ''关闭操作人'' AFTER `closed_at`',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 2. 字典类型（幂等）
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `updater`)
SELECT 'CRM 商机阶段', 'crm_business_stage', 0, 'CRM 商机阶段（仅推进过程，不含赢单/输单）', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_business_stage');

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `updater`)
SELECT 'CRM 商机状态组', 'crm_business_state', 0, 'CRM 商机状态组（只读派生：未开始/进行中/已赢单/已丢单）', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_business_state');

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `updater`)
SELECT 'CRM 商机丢单原因', 'crm_business_lost_reason', 0, 'CRM 商机丢单原因（仅输单时必填）', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_business_lost_reason');

-- ---------------------------------------------------------------------------
-- 3. 字典数据（幂等）
-- ---------------------------------------------------------------------------
-- 3.1 商机阶段（备注里写明对应赢单率）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 1, '商机待评估', '1', 'crm_business_stage', 0, 'default', '', '预估赢单率 5% → 状态组=未开始', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_stage' AND `value` = '1');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 2, '初步接洽', '2', 'crm_business_stage', 0, 'blue', '', '预估赢单率 10% → 状态组=进行中', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_stage' AND `value` = '2');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 3, '需求确认', '3', 'crm_business_stage', 0, 'cyan', '', '预估赢单率 40% → 状态组=进行中', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_stage' AND `value` = '3');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 4, '方案评估', '4', 'crm_business_stage', 0, 'purple', '', '预估赢单率 60% → 状态组=进行中', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_stage' AND `value` = '4');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 5, '商务谈判', '5', 'crm_business_stage', 0, 'gold', '', '预估赢单率 70% → 状态组=进行中', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_stage' AND `value` = '5');

-- 3.2 商机状态组
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 1, '未开始', '1', 'crm_business_state', 0, 'default', '', '商机待评估', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_state' AND `value` = '1');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 2, '进行中', '2', 'crm_business_state', 0, 'processing', '', '初步接洽/需求确认/方案评估/商务谈判', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_state' AND `value` = '2');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 3, '已赢单', '3', 'crm_business_state', 0, 'success', '', '点击「赢单」后，赢单率=100', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_state' AND `value` = '3');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 4, '已丢单', '4', 'crm_business_state', 0, 'error', '', '点击「输单」后，赢单率=0', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_state' AND `value` = '4');

-- 3.3 丢单原因
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 1, '客户预算不足/预算取消', '1', 'crm_business_lost_reason', 0, 'default', '', '', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_lost_reason' AND `value` = '1');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 2, '客户选择竞品', '2', 'crm_business_lost_reason', 0, 'default', '', '', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_lost_reason' AND `value` = '2');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 3, '项目无限期搁置', '3', 'crm_business_lost_reason', 0, 'default', '', '', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_lost_reason' AND `value` = '3');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 4, '客户内部决策人变更', '4', 'crm_business_lost_reason', 0, 'default', '', '', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_lost_reason' AND `value` = '4');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 5, '客户需求重大变更', '5', 'crm_business_lost_reason', 0, 'default', '', '', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_lost_reason' AND `value` = '5');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 6, '其他', '6', 'crm_business_lost_reason', 0, 'default', '', '需在丢单备注中说明', '1', '1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'crm_business_lost_reason' AND `value` = '6');

-- ---------------------------------------------------------------------------
-- 4. 存量数据迁移（仅处理尚未迁移的行：stage IS NULL）
--    阶段映射：待评估→1；初步接洽→2；需求确认/需求沟通→3；方案评估/方案报价→4；商务谈判/赢单/输单→5
-- ---------------------------------------------------------------------------
UPDATE `crm_business` b
LEFT JOIN `crm_business_status` s ON s.id = b.status_id
SET
    b.stage = CASE
        WHEN s.name LIKE '%待评估%' THEN 1
        WHEN s.name LIKE '%初步接洽%' THEN 2
        WHEN s.name LIKE '%需求确认%' OR s.name LIKE '%需求沟通%' THEN 3
        WHEN s.name LIKE '%方案评估%' OR s.name LIKE '%方案报价%' THEN 4
        WHEN s.name LIKE '%商务谈判%' THEN 5
        WHEN s.name LIKE '%赢单%' OR s.name LIKE '%输单%' THEN 5
        ELSE 1 END,
    b.business_state = CASE
        WHEN b.end_status = 1 OR s.name LIKE '%赢单%' THEN 3
        WHEN b.end_status IN (2, 3) OR s.name LIKE '%输单%' THEN 4
        WHEN s.name LIKE '%待评估%' THEN 1
        ELSE 2 END,
    b.win_rate = CASE
        WHEN b.end_status = 1 OR s.name LIKE '%赢单%' THEN 100
        WHEN b.end_status IN (2, 3) OR s.name LIKE '%输单%' THEN 0
        WHEN s.name LIKE '%待评估%' THEN 5
        WHEN s.name LIKE '%初步接洽%' THEN 10
        WHEN s.name LIKE '%需求确认%' OR s.name LIKE '%需求沟通%' THEN 40
        WHEN s.name LIKE '%方案评估%' OR s.name LIKE '%方案报价%' THEN 60
        WHEN s.name LIKE '%商务谈判%' THEN 70
        ELSE 5 END,
    -- 注意：closed_at 必须在 end_status 赋值之前，才能读到原始的 end_status
    b.closed_at = CASE
        WHEN b.end_status IN (1, 2, 3) OR s.name LIKE '%赢单%' OR s.name LIKE '%输单%'
            THEN COALESCE(b.update_time, b.create_time)
        ELSE NULL END,
    -- 历史丢单数据没有原因，统一置为 6（其他）
    b.lost_reason = CASE
        WHEN (b.end_status IN (2, 3) OR s.name LIKE '%输单%') AND b.lost_reason IS NULL THEN 6
        ELSE b.lost_reason END,
    b.end_status = CASE
        WHEN b.end_status = 1 OR s.name LIKE '%赢单%' THEN 1
        WHEN b.end_status IN (2, 3) OR s.name LIKE '%输单%' THEN 2
        ELSE NULL END
WHERE b.stage IS NULL;

-- ---------------------------------------------------------------------------
-- 5. 迁移结果自查
-- ---------------------------------------------------------------------------
SELECT '=== 迁移后：状态组分布 ===' AS info;
SELECT business_state, COUNT(*) AS cnt FROM crm_business GROUP BY business_state;
SELECT '=== 迁移后：阶段分布 ===' AS info;
SELECT stage, COUNT(*) AS cnt FROM crm_business GROUP BY stage;
SELECT '=== 异常自查（未迁移/赢单率与状态组不匹配，应为空） ===' AS info;
SELECT id, name, stage, business_state, win_rate FROM crm_business
WHERE stage IS NULL
   OR business_state IS NULL
   OR win_rate IS NULL
   OR (business_state = 3 AND win_rate <> 100)
   OR (business_state = 4 AND win_rate <> 0);
