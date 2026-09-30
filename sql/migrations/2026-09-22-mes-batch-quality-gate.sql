-- ============================================================================
-- R4: 批次质量门禁 —— 质量状态字典扩展
--
-- 在 R3 的 0=待检 / 1=合格 / 2=不合格 基础上，新增：
--   3=冻结   禁止入库、禁止出库
--   4=隔离   禁止入库、禁止出库（等待质量判定）
--   5=让步   允许正常入库、出库流转
--
-- 说明：本脚本幂等（NOT EXISTS 兜底），不修改既有 0/1/2 字典数据。
-- ============================================================================

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '冻结', '3', 'mes_wm_quality_status', 0, 'warning', '', '禁止入库、禁止出库（R4）', '1', NOW(), '1', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'mes_wm_quality_status' AND `value` = '3' AND `deleted` = 0);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5, '隔离', '4', 'mes_wm_quality_status', 0, 'error', '', '禁止入库、禁止出库，等待质量判定（R4）', '1', NOW(), '1', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'mes_wm_quality_status' AND `value` = '4' AND `deleted` = 0);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 6, '让步', '5', 'mes_wm_quality_status', 0, 'success', '', '质量放行，允许正常流转（R4）', '1', NOW(), '1', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'mes_wm_quality_status' AND `value` = '5' AND `deleted` = 0);
