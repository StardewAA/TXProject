-- ERP/WMS 商品规格属性补充（租户 1）
--
-- 产品档案中的“规格”和“单位”原先仅以冗余文本写入 SKU JSON，
-- propertyId/valueId 使用了 0/1，后台属性编辑无法校验，且属性管理页看不到这些值。
-- 本脚本建立可复用的属性项/属性值，并将 30 个 ERP SKU 绑定到真实 ID。
-- 每个 ERP 产品当前只有一条 SKU，因此将 spec_type 设为多规格标记，
-- 这样后台和用户端都能展示“规格/单位”两个属性；不会改变价格或库存。
-- 脚本使用固定 ID，可重复执行；只处理租户 1 的 ERP 导入商品。
SET NAMES utf8mb4;
START TRANSACTION;

-- 属性项：规格、单位
INSERT INTO product_property
  (id,name,status,remark,creator,create_time,updater,update_time,deleted,tenant_id)
VALUES
  (940001,'规格',0,'ERP/WMS 产品规格，来源产品档案总览。','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (940002,'单位',0,'ERP/WMS 计量单位，来源产品档案总览。','erp-properties',NOW(),'erp-properties',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE
  name=VALUES(name),status=VALUES(status),remark=VALUES(remark),
  updater='erp-properties',update_time=NOW(),deleted=b'0',tenant_id=1;

-- 规格值。值按 ERP 产品编号固定，重复规格（25kg/袋、20kg/组）只保留一份。
INSERT INTO product_property_value
  (id,property_id,name,remark,creator,create_time,updater,update_time,deleted,tenant_id)
VALUES
  (941001,940001,'20kg/组(主剂16+固化剂4)','CP-1001 环氧富锌底漆','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941002,940001,'20kg/组','CP-1002 环氧云铁中间漆；CP-1003 脂肪族聚氨酯面漆','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941003,940001,'25kg/箱','CP-1006 环氧粉末涂料（FBE）','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941004,940001,'25kg/袋','CP-1007 共聚胶粘剂；CP-1004 室外非膨胀型钢结构防火涂料','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941005,940001,'25kg/卷','CP-1008 聚乙烯外层（PE黑色）','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941006,940001,'20kg/桶','CP-1005 室外膨胀型钢结构防火涂料','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941007,940001,'Φ50mm 容重64kg/m³','CP-2001 离心玻璃棉毡','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941008,940001,'Φ50mm 容重128kg/m³','CP-2002 硅酸铝针刺毡','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941009,940001,'δ80mm 容重45kg/m³ L2/L3级','CP-3001 PIR聚异氰脲酸酯保冷板','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941010,940001,'容重60kg/m³','CP-3002 PUF聚氨酯硬泡保冷管壳','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941011,940001,'δ10mm','CP-3003 纳米气凝胶保冷毡','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941012,940001,'δ1.2mm 宽度1m','CP-4001 CPU阻燃聚氨酯防潮卷材','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941013,940001,'宽50mm×50m','CP-4002 铝箔玻纤防潮胶带','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941014,940001,'δ0.5mm 宽度1000mm','CP-5001 镀铝锌彩钢板','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941015,940001,'δ0.8mm 宽度1000mm','CP-5002 铝皮（保温外护）','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941016,940001,'304材质 宽12.7mm','CP-5003 不锈钢扎带','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941017,940001,'DN100 L2/L3级 δ80mm','CP-6001 DN100 PIR保冷管托','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941018,940001,'DN200 L2/L3级 δ80mm','CP-6002 DN200 PIR保冷管托','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941019,940001,'DN300 L2/L3级 δ100mm','CP-6003 DN300 PIR保冷管托','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941020,940001,'DN350 L2/L3级 δ100mm','CP-6004 DN350 PIR保冷管托','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941021,940001,'20-40目','CP-7001 除锈砂（石英砂）','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941022,940001,'18L/桶','CP-7002 环氧稀释剂','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941023,940001,'宽1m×50m 160g/㎡','CP-7003 耐碱玻纤网格布','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941024,940001,'304 M4×16','CP-7004 自攻螺钉','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941025,940001,'全面型 配KN95滤盒','CP-8001 防尘防毒面罩','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941026,940001,'双钩 五点式','CP-8002 高空作业安全带','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941027,940001,'GH833 气动柱塞泵','CP-8003 无气喷涂机','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (941028,940001,'Φ48×3.0mm','CP-9001 脚手架钢管','erp-properties',NOW(),'erp-properties',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE
  property_id=VALUES(property_id),name=VALUES(name),remark=VALUES(remark),
  updater='erp-properties',update_time=NOW(),deleted=b'0',tenant_id=1;

-- 单位值，统一复用，避免同名单位出现多份。
INSERT INTO product_property_value
  (id,property_id,name,remark,creator,create_time,updater,update_time,deleted,tenant_id)
VALUES
  (942001,940002,'组','CP-1001/1002/1003','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942002,940002,'kg','CP-1004/1005/1006/1007/1008','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942003,940002,'m³','CP-2001/2002/3001/3002','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942004,940002,'㎡','CP-3003/4001/4002/5001/5002','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942005,940002,'卷','CP-4002/7003','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942006,940002,'m','CP-5003/9001','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942007,940002,'个','CP-6001/6002/6003/6004/7004','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942008,940002,'吨','CP-7001','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942009,940002,'桶','CP-7002','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942010,940002,'套','CP-8001/8002','erp-properties',NOW(),'erp-properties',NOW(),b'0',1),
  (942011,940002,'台','CP-8003','erp-properties',NOW(),'erp-properties',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE
  property_id=VALUES(property_id),name=VALUES(name),remark=VALUES(remark),
  updater='erp-properties',update_time=NOW(),deleted=b'0',tenant_id=1;

-- 绑定 SKU 的属性项和值。属性名称和值名称在 JSON 中继续冗余，
-- 以兼容现有用户端和评论历史数据。
UPDATE product_sku
SET properties = CASE id
  WHEN 920001 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941001,"valueName":"20kg/组(主剂16+固化剂4)"},{"propertyId":940002,"propertyName":"单位","valueId":942001,"valueName":"组"}]'
  WHEN 920002 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941002,"valueName":"20kg/组"},{"propertyId":940002,"propertyName":"单位","valueId":942001,"valueName":"组"}]'
  WHEN 920003 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941002,"valueName":"20kg/组"},{"propertyId":940002,"propertyName":"单位","valueId":942001,"valueName":"组"}]'
  WHEN 920004 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941003,"valueName":"25kg/箱"},{"propertyId":940002,"propertyName":"单位","valueId":942002,"valueName":"kg"}]'
  WHEN 920005 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941004,"valueName":"25kg/袋"},{"propertyId":940002,"propertyName":"单位","valueId":942002,"valueName":"kg"}]'
  WHEN 920006 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941005,"valueName":"25kg/卷"},{"propertyId":940002,"propertyName":"单位","valueId":942002,"valueName":"kg"}]'
  WHEN 920007 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941004,"valueName":"25kg/袋"},{"propertyId":940002,"propertyName":"单位","valueId":942002,"valueName":"kg"}]'
  WHEN 920008 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941006,"valueName":"20kg/桶"},{"propertyId":940002,"propertyName":"单位","valueId":942002,"valueName":"kg"}]'
  WHEN 920009 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941007,"valueName":"Φ50mm 容重64kg/m³"},{"propertyId":940002,"propertyName":"单位","valueId":942003,"valueName":"m³"}]'
  WHEN 920010 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941008,"valueName":"Φ50mm 容重128kg/m³"},{"propertyId":940002,"propertyName":"单位","valueId":942003,"valueName":"m³"}]'
  WHEN 920011 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941009,"valueName":"δ80mm 容重45kg/m³ L2/L3级"},{"propertyId":940002,"propertyName":"单位","valueId":942003,"valueName":"m³"}]'
  WHEN 920012 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941010,"valueName":"容重60kg/m³"},{"propertyId":940002,"propertyName":"单位","valueId":942003,"valueName":"m³"}]'
  WHEN 920013 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941011,"valueName":"δ10mm"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
  WHEN 920014 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941012,"valueName":"δ1.2mm 宽度1m"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
  WHEN 920015 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941013,"valueName":"宽50mm×50m"},{"propertyId":940002,"propertyName":"单位","valueId":942005,"valueName":"卷"}]'
  WHEN 920016 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941014,"valueName":"δ0.5mm 宽度1000mm"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
  WHEN 920017 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941015,"valueName":"δ0.8mm 宽度1000mm"},{"propertyId":940002,"propertyName":"单位","valueId":942004,"valueName":"㎡"}]'
  WHEN 920018 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941016,"valueName":"304材质 宽12.7mm"},{"propertyId":940002,"propertyName":"单位","valueId":942006,"valueName":"m"}]'
  WHEN 920019 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941017,"valueName":"DN100 L2/L3级 δ80mm"},{"propertyId":940002,"propertyName":"单位","valueId":942007,"valueName":"个"}]'
  WHEN 920020 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941018,"valueName":"DN200 L2/L3级 δ80mm"},{"propertyId":940002,"propertyName":"单位","valueId":942007,"valueName":"个"}]'
  WHEN 920021 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941019,"valueName":"DN300 L2/L3级 δ100mm"},{"propertyId":940002,"propertyName":"单位","valueId":942007,"valueName":"个"}]'
  WHEN 920022 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941020,"valueName":"DN350 L2/L3级 δ100mm"},{"propertyId":940002,"propertyName":"单位","valueId":942007,"valueName":"个"}]'
  WHEN 920023 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941021,"valueName":"20-40目"},{"propertyId":940002,"propertyName":"单位","valueId":942008,"valueName":"吨"}]'
  WHEN 920024 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941022,"valueName":"18L/桶"},{"propertyId":940002,"propertyName":"单位","valueId":942009,"valueName":"桶"}]'
  WHEN 920025 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941023,"valueName":"宽1m×50m 160g/㎡"},{"propertyId":940002,"propertyName":"单位","valueId":942005,"valueName":"卷"}]'
  WHEN 920026 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941024,"valueName":"304 M4×16"},{"propertyId":940002,"propertyName":"单位","valueId":942007,"valueName":"个"}]'
  WHEN 920027 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941025,"valueName":"全面型 配KN95滤盒"},{"propertyId":940002,"propertyName":"单位","valueId":942010,"valueName":"套"}]'
  WHEN 920028 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941026,"valueName":"双钩 五点式"},{"propertyId":940002,"propertyName":"单位","valueId":942010,"valueName":"套"}]'
  WHEN 920029 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941027,"valueName":"GH833 气动柱塞泵"},{"propertyId":940002,"propertyName":"单位","valueId":942011,"valueName":"台"}]'
  WHEN 920030 THEN '[{"propertyId":940001,"propertyName":"规格","valueId":941028,"valueName":"Φ48×3.0mm"},{"propertyId":940002,"propertyName":"单位","valueId":942006,"valueName":"m"}]'
  ELSE properties END,
  updater='erp-properties',update_time=NOW()
WHERE tenant_id=1 AND id BETWEEN 920001 AND 920030 AND deleted=b'0';

-- 让后台商品编辑器回显属性列，用户端规格选择器也能按属性分组。
UPDATE product_spu
SET spec_type=b'1',updater='erp-properties',update_time=NOW()
WHERE tenant_id=1 AND id BETWEEN 910001 AND 910030 AND deleted=b'0';

-- 评价迁移脚本可能先于本脚本执行。若固定评价已经存在，
-- 这里再次从 SKU 快照同步属性，保证脚本按任意顺序执行后都能正确回显规格。
UPDATE product_comment c
JOIN product_sku s
  ON s.id=c.sku_id
 AND s.tenant_id=1
 AND s.deleted=b'0'
SET c.sku_properties=s.properties,
    c.sku_pic_url=s.pic_url,
    c.updater='erp-properties',
    c.update_time=NOW()
WHERE c.tenant_id=1
  AND c.id BETWEEN 940001 AND 940030
  AND c.deleted=b'0';

COMMIT;
