-- 询价专栏多状态演示数据。
-- 仅使用已审核采购企业作为发布方；全平台询价包含结构化交付地区。
-- 脚本按 request_key/no 幂等，可重复执行。

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO product_purchase_inquiry (
  member_id, enterprise_id, request_key, no, title,
  contact_name, contact_mobile,
  delivery_area_id, delivery_city_id, delivery_province_id,
  address, remark, deadline, expected_date, visibility, status,
  selected_quote_id, erp_status, erp_no,
  creator, updater, deleted, tenant_id
)
VALUES
  (300040122, 300000114, 'mall-column-demo-open-heating-20260922', 'XJ-ZL-2009-001',
   '城市供热管网保温材料年度采购', '张经理', '13800000001',
   130102, 130100, 130000, '河北省石家庄市项目物资中转库',
   '计划用于冬季供热管网检修，请按完整保温体系报价，并注明分批交付周期。',
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 15 DAY)) * 1000,
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 35 DAY)) * 1000,
   'PLATFORM_PUBLIC', 1, NULL, 'WMS_DIRECT', NULL,
   'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009),

  (300040122, 300000114, 'mall-column-demo-open-tank-20260922', 'XJ-ZL-2009-002',
   '化工储罐防腐涂层配套采购', '张经理', '13800000001',
   130102, 130100, 130000, '河北省石家庄市项目物资中转库',
   '底漆、中间漆和面漆须为兼容体系，报价需包含质量证明及施工参数。',
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 8 DAY)) * 1000,
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 28 DAY)) * 1000,
   'PLATFORM_PUBLIC', 1, NULL, 'WMS_DIRECT', NULL,
   'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009),

  (300040122, 300000114, 'mall-column-demo-open-kiln-20260922', 'XJ-ZL-2009-003',
   '工业窑炉耐火隔热材料采购', '张经理', '13800000001',
   130102, 130100, 130000, '河北省石家庄市项目物资中转库',
   '用于年度检修，要求批次稳定并提供耐温参数和安装技术资料。',
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 22 DAY)) * 1000,
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 45 DAY)) * 1000,
   'PLATFORM_PUBLIC', 1, NULL, 'WMS_DIRECT', NULL,
   'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009),

  (300040122, 300000114, 'mall-column-demo-closed-maintenance-20260922', 'XJ-ZL-2009-004',
   '厂区检维修保温材料补库', '张经理', '13800000001',
   130102, 130100, 130000, '河北省石家庄市项目物资中转库',
   '原检修计划调整，本轮询价已关闭，保留清单供专栏状态展示。',
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 5 DAY)) * 1000,
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 25 DAY)) * 1000,
   'PLATFORM_PUBLIC', 3, NULL, 'WMS_DIRECT', NULL,
   'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009),

  (300040122, 300000114, 'mall-column-demo-deal-insulation-20260922', 'XJ-ZL-2009-005',
   '蒸汽管线保温改造材料采购', '张经理', '13800000001',
   130102, 130100, 130000, '河北省石家庄市项目物资中转库',
   '本询价已完成比价并采纳供应商报价，保留成交状态和报价快照。',
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 10 DAY)) * 1000,
   UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 30 DAY)) * 1000,
   'PLATFORM_PUBLIC', 4, NULL, 'WMS_DIRECT', NULL,
   'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), remark = VALUES(remark),
  deadline = VALUES(deadline), expected_date = VALUES(expected_date),
  delivery_area_id = VALUES(delivery_area_id), delivery_city_id = VALUES(delivery_city_id),
  delivery_province_id = VALUES(delivery_province_id), visibility = VALUES(visibility),
  status = VALUES(status), updater = VALUES(updater), update_time = NOW(), deleted = b'0';

DROP TEMPORARY TABLE IF EXISTS tmp_inquiry_column_demo_item;
CREATE TEMPORARY TABLE tmp_inquiry_column_demo_item (
  request_key varchar(64) NOT NULL,
  line_no int NOT NULL,
  code varchar(100) NOT NULL,
  name varchar(200) NOT NULL,
  category varchar(100) NOT NULL,
  specification varchar(500) NOT NULL,
  quantity decimal(12,3) NOT NULL,
  unit varchar(20) NOT NULL,
  remark varchar(500) NOT NULL,
  PRIMARY KEY (request_key, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_inquiry_column_demo_item
  (request_key, line_no, code, name, category, specification, quantity, unit, remark)
VALUES
  ('mall-column-demo-open-heating-20260922', 1, 'DEMO-HEAT-PIPE', '预制直埋保温管', '保温材料', 'DN100，聚氨酯保温层', 800.000, '米', '按施工段分批交付'),
  ('mall-column-demo-open-heating-20260922', 2, 'DEMO-HEAT-SHELL', '憎水岩棉管壳', '保温材料', '厚五十毫米', 1200.000, '米', '提供导热系数检测报告'),
  ('mall-column-demo-open-heating-20260922', 3, 'DEMO-HEAT-SHEET', '铝合金保护层卷材', '外护材料', '厚零点五毫米', 4.000, '吨', '表面平整无明显划伤'),

  ('mall-column-demo-open-tank-20260922', 1, 'DEMO-PAINT-ZINC', '环氧富锌底漆', '防腐涂料', '双组份二十千克', 90.000, '组', '锌含量满足储罐防腐要求'),
  ('mall-column-demo-open-tank-20260922', 2, 'DEMO-PAINT-MIO', '环氧云铁中间漆', '防腐涂料', '双组份二十千克', 80.000, '组', '与底漆配套'),
  ('mall-column-demo-open-tank-20260922', 3, 'DEMO-PAINT-PU', '聚氨酯防腐面漆', '防腐涂料', '耐候型二十千克', 70.000, '组', '颜色成交后确认'),

  ('mall-column-demo-open-kiln-20260922', 1, 'DEMO-FIRE-BLANKET', '硅酸铝纤维毯', '耐火材料', '厚五十毫米', 600.000, '平方米', '长期耐温满足窑炉工况'),
  ('mall-column-demo-open-kiln-20260922', 2, 'DEMO-FIRE-MODULE', '耐火纤维模块', '耐火材料', '三百毫米标准模块', 360.000, '块', '配套锚固件'),
  ('mall-column-demo-open-kiln-20260922', 3, 'DEMO-FIRE-BINDER', '高温纤维粘结剂', '耐火辅料', '二十千克桶装', 60.000, '桶', '与纤维制品同体系'),

  ('mall-column-demo-closed-maintenance-20260922', 1, 'DEMO-CLOSED-ROCK', '岩棉保温板', '保温材料', '憎水型厚五十毫米', 1000.000, '平方米', '计划调整后关闭'),
  ('mall-column-demo-closed-maintenance-20260922', 2, 'DEMO-CLOSED-BAND', '不锈钢扎带', '安装辅材', '宽十九毫米', 100.000, '盘', '计划调整后关闭'),

  ('mall-column-demo-deal-insulation-20260922', 1, 'DEMO-DEAL-ROCK', '憎水岩棉板', '保温材料', '厚一百毫米', 60.000, '平方米', '已采纳报价'),
  ('mall-column-demo-deal-insulation-20260922', 2, 'DEMO-DEAL-CERAMIC', '硅酸铝纤维毯', '耐火材料', '厚五十毫米', 80.000, '平方米', '已采纳报价');

INSERT INTO product_purchase_inquiry_item (
  inquiry_id, sku_id, code, name, category, specification,
  quantity, unit, remark, pic_url,
  creator, updater, deleted, tenant_id
)
SELECT inquiry.id, NULL, item.code, item.name, item.category, item.specification,
       item.quantity, item.unit, item.remark, NULL,
       'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', inquiry.tenant_id
FROM tmp_inquiry_column_demo_item item
JOIN product_purchase_inquiry inquiry
  ON inquiry.request_key = item.request_key AND inquiry.tenant_id = 2009 AND inquiry.deleted = b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM product_purchase_inquiry_item existing
  WHERE existing.tenant_id = inquiry.tenant_id
    AND existing.inquiry_id = inquiry.id
    AND existing.code = item.code
    AND existing.deleted = b'0'
);

-- 为“已成交”询价保存一份真实结构的采纳报价快照。
INSERT INTO product_purchase_inquiry_quote (
  inquiry_id, shop_id, enterprise_id, supplier_tenant_id, admin_user_id,
  request_key, status, version, goods_price, freight_price, total_price,
  delivery_date, valid_until, remark, submitted_at, accepted_at,
  creator, updater, deleted, tenant_id
)
SELECT inquiry.id, 200800201, 200800101, 2008, NULL,
       'mall-column-demo-accepted-quote-20260922', 2, 1,
       1924000, 12000, 1936000,
       UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 30 DAY)) * 1000,
       UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 10 DAY)) * 1000,
       '材料参数及分批交付方案满足采购要求。', NOW(), NOW(),
       'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009
FROM product_purchase_inquiry inquiry
WHERE inquiry.tenant_id = 2009
  AND inquiry.request_key = 'mall-column-demo-deal-insulation-20260922'
  AND inquiry.deleted = b'0'
ON DUPLICATE KEY UPDATE
  status = 2, goods_price = VALUES(goods_price), freight_price = VALUES(freight_price),
  total_price = VALUES(total_price), accepted_at = NOW(), updater = VALUES(updater), deleted = b'0';

INSERT INTO product_purchase_inquiry_quote_item (
  quote_id, inquiry_item_id, sku_id, product_name, sku_name, sku_specification,
  spu_content_version, count, unit_price, total_price,
  creator, updater, deleted, tenant_id
)
SELECT quote.id, item.id,
       CASE item.code WHEN 'DEMO-DEAL-ROCK' THEN 200820005 ELSE 200820009 END,
       CASE item.code WHEN 'DEMO-DEAL-ROCK' THEN '陕安保温 憎水岩棉板 100mm' ELSE '陕安保温 硅酸铝纤维毯 50mm' END,
       CASE item.code WHEN 'DEMO-DEAL-ROCK' THEN '厚度100mm / 平方米' ELSE '厚度50mm / 平方米' END,
       CASE item.code WHEN 'DEMO-DEAL-ROCK' THEN '厚度100mm' ELSE '厚度50mm' END,
       1,
       CAST(item.quantity AS UNSIGNED),
       CASE item.code WHEN 'DEMO-DEAL-ROCK' THEN 8600 ELSE 17600 END,
       CAST(item.quantity AS UNSIGNED) * CASE item.code WHEN 'DEMO-DEAL-ROCK' THEN 8600 ELSE 17600 END,
       'mall-inquiry-column-demo-20260922', 'mall-inquiry-column-demo-20260922', b'0', 2009
FROM product_purchase_inquiry inquiry
JOIN product_purchase_inquiry_item item
  ON item.inquiry_id = inquiry.id AND item.tenant_id = inquiry.tenant_id
 AND item.deleted = b'0' AND item.code IN ('DEMO-DEAL-ROCK', 'DEMO-DEAL-CERAMIC')
JOIN product_purchase_inquiry_quote quote
  ON quote.inquiry_id = inquiry.id AND quote.tenant_id = inquiry.tenant_id
 AND quote.request_key = 'mall-column-demo-accepted-quote-20260922' AND quote.deleted = b'0'
WHERE inquiry.tenant_id = 2009
  AND inquiry.request_key = 'mall-column-demo-deal-insulation-20260922'
  AND inquiry.deleted = b'0'
ON DUPLICATE KEY UPDATE
  sku_id = VALUES(sku_id), product_name = VALUES(product_name), sku_name = VALUES(sku_name),
  sku_specification = VALUES(sku_specification), count = VALUES(count),
  unit_price = VALUES(unit_price), total_price = VALUES(total_price),
  updater = VALUES(updater), deleted = b'0';

UPDATE product_purchase_inquiry inquiry
JOIN product_purchase_inquiry_quote quote
  ON quote.inquiry_id = inquiry.id AND quote.tenant_id = inquiry.tenant_id
 AND quote.request_key = 'mall-column-demo-accepted-quote-20260922'
 AND quote.status = 2 AND quote.deleted = b'0'
SET inquiry.selected_quote_id = quote.id,
    inquiry.status = 4,
    inquiry.updater = 'mall-inquiry-column-demo-20260922',
    inquiry.update_time = NOW()
WHERE inquiry.tenant_id = 2009
  AND inquiry.request_key = 'mall-column-demo-deal-insulation-20260922'
  AND inquiry.deleted = b'0';

DROP TEMPORARY TABLE IF EXISTS tmp_inquiry_column_demo_item;

COMMIT;

-- 验收：应返回 5 条、3 种状态，并且每条至少有 2 项物料。
SELECT inquiry.status, COUNT(*) AS inquiry_count
FROM product_purchase_inquiry inquiry
WHERE inquiry.creator = 'mall-inquiry-column-demo-20260922' AND inquiry.deleted = b'0'
GROUP BY inquiry.status
ORDER BY inquiry.status;

SELECT inquiry.id, inquiry.no, inquiry.title, inquiry.status,
       COUNT(DISTINCT item.id) AS item_count,
       COUNT(DISTINCT quote.id) AS quote_count
FROM product_purchase_inquiry inquiry
JOIN product_purchase_inquiry_item item
  ON item.inquiry_id = inquiry.id AND item.tenant_id = inquiry.tenant_id AND item.deleted = b'0'
LEFT JOIN product_purchase_inquiry_quote quote
  ON quote.inquiry_id = inquiry.id AND quote.tenant_id = inquiry.tenant_id AND quote.deleted = b'0'
WHERE inquiry.creator = 'mall-inquiry-column-demo-20260922' AND inquiry.deleted = b'0'
GROUP BY inquiry.id, inquiry.no, inquiry.title, inquiry.status
ORDER BY inquiry.id;
