-- 商城产业链企业与店铺演示数据（tenant_id = 1）
-- 仅录入可销售商品的 7 家原材料供应商和 6 家生产制造企业。
-- 统一社会信用代码尚未提供，使用 TX-DEMO-* 临时档案编码，正式入驻时必须替换。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO product_enterprise
  (id, tenant_id, name, credit_code, type, license_url, contact_name, contact_mobile,
   address, introduction, audit_status, audit_remark, creator, updater, deleted)
VALUES
  (910101, 1, '西北恒泰新材料有限公司', 'TX-DEMO-UP-001', 1, NULL, NULL, NULL, NULL,
   '主营岩棉、玻璃棉保温基材及工业保温棉坯料。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910102, 1, '锦程树脂化工科技有限公司', 'TX-DEMO-UP-002', 1, NULL, NULL, NULL, NULL,
   '主营聚氨酯组合料、环氧树脂及防腐树脂原料。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910103, 1, '蓝盾涂料原料集团', 'TX-DEMO-UP-003', 1, NULL, NULL, NULL, NULL,
   '主营防腐涂料基料、固化剂、防锈粉体及颜填料。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910104, 1, '隆达橡塑材料股份有限公司', 'TX-DEMO-UP-004', 1, NULL, NULL, NULL, NULL,
   '主营橡塑海绵原料、丁基胶及密封胶带基材。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910105, 1, '金岩矿物棉实业有限公司', 'TX-DEMO-UP-005', 1, NULL, NULL, NULL, NULL,
   '主营玄武岩岩棉原料及矿渣棉原材料加工供货。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910106, 1, '汇泰玻纤科技有限公司', 'TX-DEMO-UP-006', 1, NULL, NULL, NULL, NULL,
   '主营玻璃纤维布、网格布及防腐增强玻纤基材。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910107, 1, '宇昂化学助剂有限公司', 'TX-DEMO-UP-007', 1, NULL, NULL, NULL, NULL,
   '主营防腐固化剂、阻燃剂、防水助剂及发泡剂。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910108, 1, '陕安防腐保温制品有限公司', 'TX-DEMO-MF-001', 2, NULL, NULL, NULL, NULL,
   '主营预制直埋保温管、聚氨酯保温板及管道保温构件。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910109, 1, '科盾防护科技股份有限公司', 'TX-DEMO-MF-002', 2, NULL, NULL, NULL, NULL,
   '主营工业防腐涂料、环氧重防腐漆及耐高温防腐涂层。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910110, 1, '华瀚节能建材有限公司', 'TX-DEMO-MF-003', 2, NULL, NULL, NULL, NULL,
   '主营岩棉板、橡塑保温板及硅酸铝保温制品。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910111, 1, '锐捷管道防护设备有限公司', 'TX-DEMO-MF-004', 2, NULL, NULL, NULL, NULL,
   '主营钢套钢保温管、弯头三通及预制保温管件。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910112, 1, '固邦防腐材料制造有限公司', 'TX-DEMO-MF-005', 2, NULL, NULL, NULL, NULL,
   '主营防腐卷材、环氧胶泥、防腐腻子及地坪防腐材料。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0'),
  (910113, 1, '卓远耐火保温实业公司', 'TX-DEMO-MF-006', 2, NULL, NULL, NULL, NULL,
   '主营耐高温硅酸铝模块及锅炉专用保温成品件。', 1, '平台演示企业，资质信息待正式入驻补充', 'mall-seed', 'mall-seed', b'0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name), type = VALUES(type), introduction = VALUES(introduction),
  audit_status = 1, audit_remark = VALUES(audit_remark), updater = 'mall-seed',
  update_time = NOW(), deleted = b'0';

INSERT INTO product_shop
  (id, enterprise_id, name, introduction, status, tenant_id, creator, updater, deleted)
VALUES
  (910101, 910101, '恒泰保温基材店', '西北恒泰新材料有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910102, 910102, '锦程树脂原料店', '锦程树脂化工科技有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910103, 910103, '蓝盾防腐涂料店', '蓝盾涂料原料集团官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910104, 910104, '隆达橡塑材料店', '隆达橡塑材料股份有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910105, 910105, '金岩矿物材料店', '金岩矿物棉实业有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910106, 910106, '汇泰玻纤材料店', '汇泰玻纤科技有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910107, 910107, '宇昂化学助剂店', '宇昂化学助剂有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910108, 910108, '陕安防腐保温制品店', '陕安防腐保温制品有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910109, 910109, '科盾工业防护店', '科盾防护科技股份有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910110, 910110, '华瀚节能建材店', '华瀚节能建材有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910111, 910111, '锐捷管道防护店', '锐捷管道防护设备有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910112, 910112, '固邦防腐材料店', '固邦防腐材料制造有限公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0'),
  (910113, 910113, '卓远耐火保温店', '卓远耐火保温实业公司官方商城店铺', 0, 1, 'mall-seed', 'mall-seed', b'0')
ON DUPLICATE KEY UPDATE
  enterprise_id = VALUES(enterprise_id), name = VALUES(name),
  introduction = VALUES(introduction), status = 0, tenant_id = 1,
  updater = 'mall-seed', update_time = NOW(), deleted = b'0';

-- 根据企业主营范围重新分配商品；未能合理归属的通用工具用品保留在测试1111。
UPDATE product_spu SET shop_id = 910109, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910001, 910002, 910003);
UPDATE product_spu SET shop_id = 910112, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910004, 910014);
UPDATE product_spu SET shop_id = 910102, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910005);
UPDATE product_spu SET shop_id = 910104, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910006);
UPDATE product_spu SET shop_id = 910103, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910007, 910008);
UPDATE product_spu SET shop_id = 910101, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910009, 910013);
UPDATE product_spu SET shop_id = 910113, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910010);
UPDATE product_spu SET shop_id = 910110, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910011);
UPDATE product_spu SET shop_id = 910108, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910012, 910031);
UPDATE product_spu SET shop_id = 910106, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910015, 910025);
UPDATE product_spu SET shop_id = 910111, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910016, 910017, 910018, 910019, 910020, 910021, 910022, 910032);
UPDATE product_spu SET shop_id = 910105, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910023);
UPDATE product_spu SET shop_id = 910107, updater = 'mall-seed', update_time = NOW()
WHERE tenant_id = 1 AND deleted = b'0' AND id IN (910024);

COMMIT;

SELECT COUNT(*) AS seeded_enterprise_count
FROM product_enterprise
WHERE tenant_id = 1 AND deleted = b'0' AND id BETWEEN 910101 AND 910113;

SELECT COUNT(*) AS seeded_shop_count
FROM product_shop
WHERE tenant_id = 1 AND deleted = b'0' AND id BETWEEN 910101 AND 910113;

SELECT s.id, s.name, COUNT(p.id) AS product_count
FROM product_shop s
LEFT JOIN product_spu p ON p.shop_id = s.id AND p.tenant_id = 1 AND p.deleted = b'0'
WHERE s.tenant_id = 1 AND s.deleted = b'0'
GROUP BY s.id, s.name
ORDER BY s.id;
