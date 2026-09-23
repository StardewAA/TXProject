-- Tenant 2009 mall/WMS end-to-end verification stock.
-- Idempotent: fixed master-data ids and a single INIT ledger row are used so the
-- script can be safely applied again without duplicating the opening balance.

START TRANSACTION;

INSERT INTO wms_item_category
    (id, parent_id, code, name, sort, status, creator, updater, deleted, tenant_id)
VALUES
    (2009001, 0, 'KD-FG', '科盾成品', 10, 1, 'mall-e2e', 'mall-e2e', b'0', 2009)
ON DUPLICATE KEY UPDATE
    code = VALUES(code), name = VALUES(name), status = VALUES(status),
    updater = VALUES(updater), deleted = b'0', tenant_id = VALUES(tenant_id);

INSERT INTO wms_warehouse
    (id, code, name, remark, sort, creator, updater, deleted, tenant_id,
     warehouse_type, organization_name, city, address, principal, principal_phone,
     status, data_version)
VALUES
    (2009001, 'KD-FG-01', '科盾成品仓', '商城订单正式出库仓', 10,
     'mall-e2e', 'mall-e2e', b'0', 2009, 'FINISHED_GOODS',
     '科盾工业防护旗舰店', '西安市', '西安市科盾工业园成品仓',
     '仓储值班员', '13900002211', 0, 1)
ON DUPLICATE KEY UPDATE
    code = VALUES(code), name = VALUES(name), warehouse_type = VALUES(warehouse_type),
    status = 0, updater = VALUES(updater), deleted = b'0', tenant_id = VALUES(tenant_id);

INSERT INTO wms_item
    (id, code, name, category_id, unit, remark, creator, updater, deleted, tenant_id,
     material_type, quality_required, source_version, status, package_spec,
     min_package_unit, storage_condition, dangerous_goods)
VALUES
    (2009001, 'KD-EPZP', '环氧富锌底漆', 2009001, '组',
     '商城 SKU 940301 对应 WMS 成品主数据', 'mall-e2e', 'mall-e2e', b'0', 2009,
     'FINISHED_GOODS', b'0', 1, 0, '20kg/组', '组', '阴凉干燥处密封保存', b'0')
ON DUPLICATE KEY UPDATE
    code = VALUES(code), name = VALUES(name), category_id = VALUES(category_id),
    unit = VALUES(unit), material_type = VALUES(material_type), status = 0,
    updater = VALUES(updater), deleted = b'0', tenant_id = VALUES(tenant_id);

INSERT INTO wms_item_sku
    (id, name, item_id, bar_code, code, cost_price, selling_price, creator, updater,
     deleted, tenant_id, package_spec, min_package_unit)
VALUES
    (2009001, '20kg/组', 2009001, '6920090009401', 'KD-EPZP-20KG', 320.00, 398.00,
     'mall-e2e', 'mall-e2e', b'0', 2009, '20kg/组', '组')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), item_id = VALUES(item_id), code = VALUES(code),
    selling_price = VALUES(selling_price), updater = VALUES(updater),
    deleted = b'0', tenant_id = VALUES(tenant_id);

INSERT INTO wms_inventory
    (id, sku_id, warehouse_id, area_id, quantity, remark, creator, updater, deleted, tenant_id)
VALUES
    (2009001, 2009001, 2009001, 0, 50.00, '租户 2009 商城首批可售库存',
     'mall-e2e', 'mall-e2e', b'0', 2009)
ON DUPLICATE KEY UPDATE
    remark = VALUES(remark), updater = VALUES(updater), deleted = b'0', tenant_id = VALUES(tenant_id);

INSERT INTO wms_inventory_history
    (id, warehouse_id, area_id, sku_id, quantity, before_quantity, after_quantity,
     batch_no, price, total_price, remark, order_no, order_type,
     creator, updater, deleted, tenant_id)
VALUES
    (2009001, 2009001, 0, 2009001, 50.00, 0.00, 50.00,
     'KD-INIT-20260922', 398.00, 19900.00, '租户 2009 WMS 期初库存',
     'INIT-2009-20260922', 1, 'mall-e2e', 'mall-e2e', b'0', 2009)
ON DUPLICATE KEY UPDATE id = VALUES(id);

UPDATE product_sku
SET wms_sku_id = 2009001,
    wms_warehouse_id = 2009001,
    updater = 'mall-e2e',
    update_time = CURRENT_TIMESTAMP
WHERE id = 940301
  AND tenant_id = 2009
  AND deleted = b'0';

-- The compose service is named `trade`; callbacks must use the service's real
-- application prefix. The former `trade-server/admin-api/...` address cannot
-- resolve in the current compose network and leaves successful mock payments
-- waiting for a failed notification retry.
UPDATE pay_app
SET order_notify_url = 'http://trade:48080/app-api/trade/order/update-paid',
    updater = 'mall-e2e',
    update_time = CURRENT_TIMESTAMP
WHERE id = 940001
  AND tenant_id = 2009
  AND deleted = b'0';

COMMIT;
