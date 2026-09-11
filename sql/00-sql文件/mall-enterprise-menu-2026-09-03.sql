INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业入驻', '', 2, 1, COALESCE((SELECT id FROM system_menu WHERE (path = 'mall' OR name = '商城系统') AND type = 1 AND deleted = b'0' ORDER BY id LIMIT 1), 2000), 'enterprise', 'ep:office-building', 'mall/product/enterprise/index', 'ProductEnterprise', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'mall/product/enterprise/index' AND deleted = b'0');
-- 企业入驻操作权限（执行后由基座组按角色授权）
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业查询', 'product:enterprise:query', 3, 1, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/enterprise/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:enterprise:query' AND x.deleted = b'0') LIMIT 1;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业创建', 'product:enterprise:create', 3, 2, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/enterprise/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:enterprise:create' AND x.deleted = b'0') LIMIT 1;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业更新', 'product:enterprise:update', 3, 3, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/enterprise/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:enterprise:update' AND x.deleted = b'0') LIMIT 1;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业审核', 'product:enterprise:audit', 3, 4, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/enterprise/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:enterprise:audit' AND x.deleted = b'0') LIMIT 1;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '企业删除', 'product:enterprise:delete', 3, 5, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/enterprise/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:enterprise:delete' AND x.deleted = b'0') LIMIT 1;

-- 企业店铺的最小操作权限，保证“企业审核通过 -> 建店”流程可直接验收。
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '店铺创建', 'product:shop:create', 3, 1, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/shop/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:shop:create' AND x.deleted = b'0') LIMIT 1;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '店铺更新', 'product:shop:update', 3, 2, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/shop/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:shop:update' AND x.deleted = b'0') LIMIT 1;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '店铺删除', 'product:shop:delete', 3, 3, id, '', '', '', '', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM system_menu p WHERE p.component = 'mall/product/shop/index' AND p.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission = 'product:shop:delete' AND x.deleted = b'0') LIMIT 1;

-- 缺货采购联调入口：提交 ERP、查看备货中、模拟库存就绪。
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '缺货采购联调', '', 2, 6, COALESCE((SELECT id FROM system_menu WHERE (path = 'mall' OR name = '商城系统') AND type = 1 AND deleted = b'0' ORDER BY id LIMIT 1), 2000), 'erp-shortage', 'ep:truck', 'mall/erp/shortage/index', 'MallErpShortage', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'mall/erp/shortage/index' AND deleted = b'0');
