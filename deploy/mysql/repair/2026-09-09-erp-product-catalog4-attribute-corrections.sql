-- ERP 商品属性文字纠正（租户 1）
--
-- 按 ERP“产品档案总览”的原文修正上一版导入脚本中的几个录入差异：
--   CP-2001/CP-2002 使用“δ50mm”，不是“Φ50mm”；
--   CP-4001/CP-5001/CP-5002 使用“幅宽”，不是“宽度”。
-- 属性项、属性值、SKU 快照、SPU 简介/详情和测试评价必须保持同一份文字，
-- 否则后台属性编辑、用户端规格选择和评论快照会出现不一致。
-- 脚本可重复执行，只处理租户 1 的 ERP 商品。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE product_property_value
SET name = CASE id
    WHEN 941007 THEN 'δ50mm 容重64kg/m³'
    WHEN 941008 THEN 'δ50mm 容重128kg/m³'
    WHEN 941012 THEN 'δ1.2mm 幅宽1m'
    WHEN 941014 THEN 'δ0.5mm 幅宽1000mm'
    WHEN 941015 THEN 'δ0.8mm 幅宽1000mm'
    ELSE name
  END,
  updater = 'erp-properties',
  update_time = NOW()
WHERE tenant_id = 1
  AND deleted = b'0'
  AND id IN (941007, 941008, 941012, 941014, 941015);

UPDATE product_sku
SET properties = CASE id
    WHEN 920009 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941007,"valueName":"δ50mm 容重64kg/m³"},{"propertyId":940002,"propertyName":"单位","valueId":942003,"valueName":"m³"}]'
    WHEN 920010 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941008,"valueName":"δ50mm 容重128kg/m³"},{"propertyId":940002,"propertyName":"单位","valueId":942003,"valueName":"m³"}]'
    WHEN 920014 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941012,"valueName":"δ1.2mm 幅宽1m"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
    WHEN 920016 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941014,"valueName":"δ0.5mm 幅宽1000mm"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
    WHEN 920017 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941015,"valueName":"δ0.8mm 幅宽1000mm"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
    ELSE properties
  END,
  updater = 'erp-properties',
  update_time = NOW()
WHERE tenant_id = 1
  AND deleted = b'0'
  AND id IN (920009, 920010, 920014, 920016, 920017);

UPDATE product_spu
SET introduction = REPLACE(REPLACE(introduction, 'Φ50mm', 'δ50mm'), '宽度', '幅宽'),
    description = REPLACE(REPLACE(description, 'Φ50mm', 'δ50mm'), '宽度', '幅宽'),
    updater = 'erp-properties',
    update_time = NOW()
WHERE tenant_id = 1
  AND deleted = b'0'
  AND id IN (910009, 910010, 910014, 910016, 910017);

-- 已生成的测试评价也要跟随 SKU 属性快照更新。
UPDATE product_comment c
JOIN product_sku s ON s.id = c.sku_id
  AND s.tenant_id = 1
  AND s.deleted = b'0'
SET c.spu_name = (SELECT p.name FROM product_spu p WHERE p.id = c.spu_id AND p.tenant_id = 1),
    c.sku_properties = s.properties,
    c.sku_pic_url = s.pic_url,
    c.user_avatar = s.pic_url,
    c.updater = 'erp-properties',
    c.update_time = NOW()
WHERE c.tenant_id = 1
  AND c.deleted = b'0'
  AND c.spu_id BETWEEN 910001 AND 910030;

COMMIT;
