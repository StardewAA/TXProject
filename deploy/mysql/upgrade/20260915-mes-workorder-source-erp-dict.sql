-- ============================================================================
-- MES 工单来源类型字典：补充「ERP订单(3)」
-- ----------------------------------------------------------------------------
-- 背景：MesProWorkOrderSourceTypeEnum 已扩展 ERP(3, "ERP订单")，
--       用于标记由 ERP 销售订单同步自动生成的工单；
--       但 system_dict_data 中 mes_pro_work_order_source_type 仅有
--       1=客户订单、2=库存备货，导致该来源在界面显示异常（空/原始数字），
--       且无法按「ERP订单」筛选。
-- 影响范围：仅新增一条字典数据，不改表结构、不影响既有数据。
-- 幂等：先按 dict_type + value 删除再插入，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

DELETE FROM `system_dict_data`
WHERE `dict_type` = 'mes_pro_work_order_source_type' AND `value` = '3';

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (3, 'ERP订单', '3', 'mes_pro_work_order_source_type', 0, 'success', '', 'ERP 销售订单同步自动生成的生产工单',
     '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0');

-- 执行结果校验：应返回 3 行（1 客户订单、2 库存备货、3 ERP订单）
SELECT `value`, `label` FROM `system_dict_data`
WHERE `dict_type` = 'mes_pro_work_order_source_type' AND `deleted` = 0
ORDER BY `value`;
