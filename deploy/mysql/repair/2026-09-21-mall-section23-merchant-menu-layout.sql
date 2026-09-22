-- 商城企业/店铺后台菜单职责调整（在 section21 之后执行）：
-- 1. “销售管理”仅保留询价机会、报价和沟通；
-- 2. 店铺级订单、售后和结算移动到“订单中心”；
-- 3. 企业管理员和店铺运营可维护商品、分类、品牌、属性及评论；
-- 4. 继续使用 supplier-* 店铺数据范围接口，不向商家开放平台全租户订单页面。
--
-- 本脚本可重复执行，需在 mall-section21 脚本之后执行。

SET NAMES utf8mb4;
START TRANSACTION;

SET @mall_root_id := (
    SELECT id FROM system_menu
    WHERE deleted = b'0' AND id = 2362
    LIMIT 1
);
SET @trade_menu_id := (
    SELECT id FROM system_menu
    WHERE deleted = b'0' AND id = 2072
    LIMIT 1
);
SET @supplier_sales_menu_id := (
    SELECT id FROM system_menu
    WHERE deleted = b'0' AND id = 25200
    LIMIT 1
);

-- 明确两个中心的业务含义和显示顺序。
UPDATE system_menu
SET name = '订单中心', sort = 65, always_show = b'1',
    updater = 'mall-menu-layout', update_time = NOW()
WHERE id = @trade_menu_id;

UPDATE system_menu
SET name = '销售管理', sort = 80, always_show = b'1',
    updater = 'mall-menu-layout', update_time = NOW()
WHERE id = @supplier_sales_menu_id;

-- 销售管理仅保留询价链路。
UPDATE system_menu
SET sort = CASE id WHEN 25201 THEN 10 WHEN 25202 THEN 20 WHEN 25203 THEN 30 ELSE sort END,
    updater = 'mall-menu-layout', update_time = NOW()
WHERE id IN (25201, 25202, 25203) AND deleted = b'0';

-- 店铺订单、售后和结算进入订单中心；组件仍是店铺范围专用页面。
UPDATE system_menu
SET name = '店铺订单', parent_id = @trade_menu_id, path = 'shop-order', sort = 10,
    component = 'mall/trade/supplier/order/index', component_name = 'MallSupplierOrder',
    updater = 'mall-menu-layout', update_time = NOW()
WHERE id = 25204 AND deleted = b'0';

UPDATE system_menu
SET name = '售后退款', parent_id = @trade_menu_id, path = 'shop-after-sale', sort = 20,
    component = 'mall/trade/supplier/after-sale/index', component_name = 'MallSupplierAfterSale',
    updater = 'mall-menu-layout', update_time = NOW()
WHERE id = 25205 AND deleted = b'0';

UPDATE system_menu
SET name = '店铺结算', parent_id = @trade_menu_id, path = 'merchant-settlement', sort = 30,
    component = 'mall/trade/supplier/settlement/index', component_name = 'MallSupplierSettlement',
    updater = 'mall-menu-layout', update_time = NOW()
WHERE id = 25206 AND deleted = b'0';

-- 企业管理员、店铺运营和店铺财务获得订单中心父目录。
-- 具体页面和按钮权限沿用原有授权，不扩大业务数据范围。
UPDATE system_role_menu mapping
JOIN system_role role_data
  ON role_data.id = mapping.role_id
 AND role_data.tenant_id = mapping.tenant_id
SET mapping.deleted = b'0', mapping.updater = 'mall-menu-layout', mapping.update_time = NOW()
WHERE mapping.menu_id = @trade_menu_id
  AND role_data.deleted = b'0' AND role_data.status = 0
  AND role_data.code IN ('mall_enterprise_admin', 'mall_shop_merchant', 'mall_shop_finance');

INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT role_data.id, @trade_menu_id,
       'mall-menu-layout', NOW(), 'mall-menu-layout', NOW(), b'0', role_data.tenant_id
FROM system_role role_data
WHERE role_data.deleted = b'0' AND role_data.status = 0
  AND role_data.code IN ('mall_enterprise_admin', 'mall_shop_merchant', 'mall_shop_finance')
  AND @trade_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing_mapping
      WHERE existing_mapping.role_id = role_data.id
        AND existing_mapping.menu_id = @trade_menu_id
  );

-- 财务角色无需显示询价销售中心，只保留订单中心下的店铺结算。
UPDATE system_role_menu mapping
JOIN system_role role_data
  ON role_data.id = mapping.role_id
 AND role_data.tenant_id = mapping.tenant_id
SET mapping.deleted = b'1', mapping.updater = 'mall-menu-layout', mapping.update_time = NOW()
WHERE mapping.menu_id = @supplier_sales_menu_id
  AND role_data.deleted = b'0'
  AND role_data.code = 'mall_shop_finance';

-- 企业后台需要完整的商品经营入口。这里开放商品资料维护，不开放商品审核权限。
-- 分类、品牌和属性是同租户共享资料；评论仍按当前租户的数据隔离规则处理。
UPDATE system_role_menu mapping
JOIN system_role role_data
  ON role_data.id = mapping.role_id
 AND role_data.tenant_id = mapping.tenant_id
SET mapping.deleted = b'0', mapping.updater = 'mall-menu-layout', mapping.update_time = NOW()
WHERE mapping.menu_id IN (
    2000,
    2002, 2003, 2004, 2005, 2006,
    2008, 2009, 2010, 2011, 2012,
    2014, 2015, 2016, 2017, 2018,
    2019, 2020, 2021, 2022, 2023,
    2336, 2337, 2338, 2339, 2340
)
  AND role_data.deleted = b'0' AND role_data.status = 0
  AND role_data.code IN ('mall_enterprise_admin', 'mall_shop_merchant');

INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT role_data.id, granted_menu.menu_id,
       'mall-menu-layout', NOW(), 'mall-menu-layout', NOW(), b'0', role_data.tenant_id
FROM system_role role_data
CROSS JOIN (
    SELECT 2000 menu_id
    UNION ALL SELECT 2002 UNION ALL SELECT 2003 UNION ALL SELECT 2004 UNION ALL SELECT 2005 UNION ALL SELECT 2006
    UNION ALL SELECT 2008 UNION ALL SELECT 2009 UNION ALL SELECT 2010 UNION ALL SELECT 2011 UNION ALL SELECT 2012
    UNION ALL SELECT 2014 UNION ALL SELECT 2015 UNION ALL SELECT 2016 UNION ALL SELECT 2017 UNION ALL SELECT 2018
    UNION ALL SELECT 2019 UNION ALL SELECT 2020 UNION ALL SELECT 2021 UNION ALL SELECT 2022 UNION ALL SELECT 2023
    UNION ALL SELECT 2336 UNION ALL SELECT 2337 UNION ALL SELECT 2338 UNION ALL SELECT 2339 UNION ALL SELECT 2340
) granted_menu
WHERE role_data.deleted = b'0' AND role_data.status = 0
  AND role_data.code IN ('mall_enterprise_admin', 'mall_shop_merchant')
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing_mapping
      WHERE existing_mapping.role_id = role_data.id
        AND existing_mapping.menu_id = granted_menu.menu_id
  );

COMMIT;

-- 验收：销售管理只有 3 个询价页面；店铺订单/售后/结算位于订单中心。
SELECT id, name, type, sort, parent_id, path, component
FROM system_menu
WHERE deleted = b'0'
  AND (id IN (@trade_menu_id, @supplier_sales_menu_id)
       OR parent_id IN (@trade_menu_id, @supplier_sales_menu_id))
ORDER BY parent_id, sort, id;

-- 验收：企业管理员和店铺运营均拥有商品中心 5 个页面及其经营操作权限。
SELECT role_data.tenant_id, role_data.code,
       GROUP_CONCAT(CONCAT(menu_data.id, ':', menu_data.name)
                    ORDER BY menu_data.type, menu_data.id SEPARATOR ', ') AS product_permissions
FROM system_role role_data
JOIN system_role_menu mapping
  ON mapping.role_id = role_data.id
 AND mapping.tenant_id = role_data.tenant_id
 AND mapping.deleted = b'0'
JOIN system_menu menu_data
  ON menu_data.id = mapping.menu_id
 AND menu_data.deleted = b'0'
WHERE role_data.deleted = b'0'
  AND role_data.code IN ('mall_enterprise_admin', 'mall_shop_merchant')
  AND menu_data.id IN (
      2000,
      2002, 2003, 2004, 2005, 2006,
      2008, 2009, 2010, 2011, 2012,
      2014, 2015, 2016, 2017, 2018,
      2019, 2020, 2021, 2022, 2023,
      2336, 2337, 2338, 2339, 2340
  )
GROUP BY role_data.tenant_id, role_data.code
ORDER BY role_data.tenant_id, role_data.code;
