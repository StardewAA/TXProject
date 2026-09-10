-- 新增 WMS 出库类型：其他出库
-- 可重复执行；已有字典数据时不会重复插入。
INSERT INTO system_dict_data
    (sort, label, value, dict_type, status, color_type, css_class, remark,
     creator, create_time, updater, update_time, deleted)
SELECT
    4, '其他出库', '203', 'wms_shipment_order_type', 0, 'info', '', '其他出库',
    '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_dict_data
    WHERE dict_type = 'wms_shipment_order_type'
      AND value = '203'
      AND deleted = b'0'
);
