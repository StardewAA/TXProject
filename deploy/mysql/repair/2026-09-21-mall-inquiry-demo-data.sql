-- 商城询价专栏演示数据：仅由审核有效的企业采购成员发布，每个采购企业生成 1 条公开询价。
-- 供应经营账号不得作为采购询价发布人；供应店铺只在后台按经营分类查看和报价。
-- 本脚本以 request_key/no 和明细 creator+code 实现幂等，可重复执行且不会重置已成交或已关闭询价。

SET NAMES utf8mb4;

-- 旧版脚本曾用 supplier 会员发布演示询价。若已经产生报价则停止并要求人工迁移；
-- 没有业务交互的旧演示行及明细可安全软删除，再由合法 buyer 重新生成。
SET @invalid_demo_quote_count=(
  SELECT COUNT(*) FROM product_purchase_inquiry_quote q
  JOIN product_purchase_inquiry i ON i.id=q.inquiry_id AND i.tenant_id=q.tenant_id
  LEFT JOIN product_enterprise_application a ON a.member_id=i.member_id AND a.enterprise_id=i.enterprise_id
    AND a.tenant_id=i.tenant_id AND a.deleted=b'0' AND a.status=1 AND a.identity='buyer'
  WHERE i.creator='mall-inquiry-demo-20260921' AND i.deleted=b'0' AND a.id IS NULL AND q.deleted=b'0');
SET @invalid_demo_quote_guard=IF(@invalid_demo_quote_count>0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''演示询价修复中止：非法供应商发布记录已有报价，请先人工迁移''',
  'SELECT 1');
PREPARE invalid_demo_quote_guard_stmt FROM @invalid_demo_quote_guard;
EXECUTE invalid_demo_quote_guard_stmt;
DEALLOCATE PREPARE invalid_demo_quote_guard_stmt;

UPDATE product_purchase_inquiry_item item
JOIN product_purchase_inquiry inquiry ON inquiry.id=item.inquiry_id AND inquiry.tenant_id=item.tenant_id
LEFT JOIN product_enterprise_application a ON a.member_id=inquiry.member_id
  AND a.enterprise_id=inquiry.enterprise_id AND a.tenant_id=inquiry.tenant_id
  AND a.deleted=b'0' AND a.status=1 AND a.identity='buyer'
SET item.deleted=b'1',item.updater='mall-section21',item.update_time=NOW()
WHERE inquiry.creator='mall-inquiry-demo-20260921' AND inquiry.deleted=b'0' AND a.id IS NULL;

UPDATE product_purchase_inquiry inquiry
LEFT JOIN product_enterprise_application a ON a.member_id=inquiry.member_id
  AND a.enterprise_id=inquiry.enterprise_id AND a.tenant_id=inquiry.tenant_id
  AND a.deleted=b'0' AND a.status=1 AND a.identity='buyer'
SET inquiry.deleted=b'1',inquiry.updater='mall-section21',inquiry.update_time=NOW()
WHERE inquiry.creator='mall-inquiry-demo-20260921' AND inquiry.deleted=b'0' AND a.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_mall_inquiry_seed_source;
CREATE TEMPORARY TABLE tmp_mall_inquiry_seed_source (
  tenant_id bigint NOT NULL,
  member_id bigint NOT NULL,
  enterprise_id bigint NOT NULL,
  application_id bigint NOT NULL,
  enterprise_name varchar(128) NOT NULL,
  contact_name varchar(50) NOT NULL,
  contact_mobile varchar(16) NOT NULL,
  address varchar(255) NOT NULL,
  template_no int NOT NULL,
  request_key varchar(64) NOT NULL,
  inquiry_no varchar(40) NOT NULL,
  PRIMARY KEY (tenant_id, application_id),
  UNIQUE KEY uk_seed_request (tenant_id, member_id, request_key),
  UNIQUE KEY uk_seed_no (inquiry_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_mall_inquiry_seed_source (
  tenant_id, member_id, enterprise_id, application_id, enterprise_name,
  contact_name, contact_mobile, address, template_no, request_key, inquiry_no
)
SELECT a.tenant_id,
       a.member_id,
       a.enterprise_id,
       a.id,
       e.name,
       a.contact_name,
       a.contact_mobile,
       a.address,
       MOD(a.id, 6),
       CONCAT('mall-inquiry-demo-20260921-', a.id),
       CONCAT('XJ-DEMO-', a.tenant_id, '-', a.id)
FROM product_enterprise_application a
JOIN (
  SELECT tenant_id, enterprise_id, MIN(id) AS application_id
  FROM product_enterprise_application
  WHERE deleted = b'0' AND status = 1 AND identity='buyer' AND enterprise_id IS NOT NULL
  GROUP BY tenant_id, enterprise_id
) chosen ON chosen.application_id = a.id AND chosen.tenant_id = a.tenant_id
JOIN member_user u
  ON u.tenant_id = a.tenant_id AND u.id = a.member_id
 AND u.deleted = b'0' AND u.status = 0 AND u.member_identity='buyer'
JOIN product_enterprise e
  ON e.tenant_id = a.tenant_id AND e.id = a.enterprise_id
 AND e.deleted = b'0' AND e.audit_status = 1
JOIN system_tenant t
  ON t.id = a.tenant_id AND t.deleted = b'0' AND t.status = 0;

SET @mall_inquiry_seed_source_count = (SELECT COUNT(*) FROM tmp_mall_inquiry_seed_source);
SET @mall_inquiry_seed_source_guard = IF(
  @mall_inquiry_seed_source_count = 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价演示数据生成中止：没有合法的企业会员与店铺关系''',
  'SELECT 1');
PREPARE mall_inquiry_seed_source_stmt FROM @mall_inquiry_seed_source_guard;
EXECUTE mall_inquiry_seed_source_stmt;
DEALLOCATE PREPARE mall_inquiry_seed_source_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_mall_inquiry_seed_item;
CREATE TEMPORARY TABLE tmp_mall_inquiry_seed_item (
  template_no int NOT NULL,
  line_no int NOT NULL,
  code varchar(100) NOT NULL,
  name varchar(200) NOT NULL,
  category varchar(100) NOT NULL,
  specification varchar(500) NOT NULL,
  quantity decimal(12,3) NOT NULL,
  unit varchar(20) NOT NULL,
  remark varchar(500) NOT NULL,
  PRIMARY KEY (template_no, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_mall_inquiry_seed_item
  (template_no, line_no, code, name, category, specification, quantity, unit, remark)
VALUES
  (0, 1, 'PAINT-ZINC', '环氧富锌底漆', '防腐涂料', '双组份，每组二十千克', 120.000, '组', '要求提供产品合格证明与配套施工参数'),
  (0, 2, 'PAINT-MIO', '环氧云铁中间漆', '防腐涂料', '双组份，每组二十千克', 100.000, '组', '与富锌底漆配套使用'),
  (0, 3, 'PAINT-PU', '丙烯酸聚氨酯面漆', '防腐涂料', '耐候型，每组二十千克', 80.000, '组', '颜色在成交后按项目色卡确认'),

  (1, 1, 'INS-CERAMIC', '硅酸铝针刺毯', '保温材料', '厚五十毫米，标准卷材', 200.000, '卷', '长期使用温度满足高温管网要求'),
  (1, 2, 'INS-GLASS-PIPE', '高温玻璃棉管壳', '保温材料', '厚五十毫米，管径按清单分批确认', 600.000, '米', '容重及导热系数须随货提供检测资料'),
  (1, 3, 'INS-AL-SHEET', '铝合金保护层卷材', '外护材料', '厚零点五毫米，标准宽度', 3.500, '吨', '表面平整，无明显划伤和氧化'),

  (2, 1, 'FIRE-CAST', '耐火浇注料', '耐火材料', '高铝型，袋装', 35.000, '吨', '适用于工业窑炉检修'),
  (2, 2, 'FIRE-MODULE', '陶瓷纤维模块', '耐火材料', '标准折叠模块', 480.000, '块', '配套锚固件并提供安装说明'),
  (2, 3, 'FIRE-BINDER', '高温粘结剂', '耐火辅料', '桶装耐高温型', 90.000, '桶', '与陶瓷纤维制品配套'),

  (3, 1, 'PIPE-TAPE', '聚乙烯防腐胶粘带', '管道防腐', '内带与外带配套卷材', 800.000, '卷', '适用于埋地钢质管道补口'),
  (3, 2, 'PIPE-FBE', '环氧粉末涂料', '管道防腐', '熔结环氧型，箱装', 5.000, '吨', '要求提供批次检验报告'),
  (3, 3, 'PIPE-SHRINK', '管道热收缩带', '管道防腐', '规格按管径清单分批供货', 320.000, '套', '补口材料需与主体防腐层相容'),

  (4, 1, 'RUBBER-SHEET', '橡塑保温板', '橡塑保温', '阻燃型，厚三十毫米', 1800.000, '平方米', '闭孔结构完整，表面无破损'),
  (4, 2, 'RUBBER-PIPE', '橡塑保温管', '橡塑保温', '阻燃型，管径按清单配套', 2400.000, '米', '壁厚及内径允许偏差符合行业要求'),
  (4, 3, 'RUBBER-GLUE', '橡塑保温专用胶水', '保温辅料', '桶装配套胶粘剂', 120.000, '桶', '需与板材和管材同体系配套'),

  (5, 1, 'MAINT-ROCK', '岩棉保温板', '保温材料', '憎水型，厚五十毫米', 1500.000, '平方米', '用于设备检维修保温层更换'),
  (5, 2, 'MAINT-GLASS', '玻璃棉毡', '保温材料', '卷毡，厚五十毫米', 260.000, '卷', '包装应满足现场防潮存放要求'),
  (5, 3, 'MAINT-BAND', '不锈钢扎带', '安装辅材', '宽十九毫米，盘装', 180.000, '盘', '边缘平整并配套锁扣');

START TRANSACTION;

INSERT INTO product_purchase_inquiry (
  member_id, enterprise_id, request_key, no, title,
  contact_name, contact_mobile, address, remark,
  deadline, expected_date, status, selected_quote_id,
  erp_status, erp_no, creator, updater, deleted, tenant_id
)
SELECT src.member_id,
       src.enterprise_id,
       src.request_key,
       src.inquiry_no,
       CONCAT(src.enterprise_name,
         CASE src.template_no
           WHEN 0 THEN '储罐防腐涂层采购'
           WHEN 1 THEN '管网保温材料采购'
           WHEN 2 THEN '窑炉耐火隔热材料采购'
           WHEN 3 THEN '管道防腐配套材料采购'
           WHEN 4 THEN '橡塑保温系统采购'
           ELSE '检维修保温材料补库'
         END),
       src.contact_name,
       src.contact_mobile,
       src.address,
       CASE src.template_no
         WHEN 0 THEN '面向合格企业公开询价，涂层体系应相互兼容，并随货提供质量证明。'
         WHEN 1 THEN '用于蒸汽管网保温改造，请按物料清单整体报价并注明合理交付周期。'
         WHEN 2 THEN '用于窑炉年度检修，要求材料批次稳定并提供施工技术资料。'
         WHEN 3 THEN '用于管道防腐工程备料，报价应包含包装、运输和配套技术资料。'
         WHEN 4 THEN '用于厂房管线节能改造，优先考虑同体系配套供货方案。'
         ELSE '用于检维修物资补库，要求分批供货并保证同批次质量稳定。'
       END,
       UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL (30 + MOD(src.application_id, 15)) DAY)) * 1000,
       UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL (60 + MOD(src.application_id, 20)) DAY)) * 1000,
       1,
       NULL,
       'WMS_DIRECT',
       NULL,
       'mall-inquiry-demo-20260921',
       'mall-inquiry-demo-20260921',
       b'0',
       src.tenant_id
FROM tmp_mall_inquiry_seed_source src
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(product_purchase_inquiry.id);

INSERT INTO product_purchase_inquiry_item (
  inquiry_id, sku_id, code, name, category, specification,
  quantity, unit, remark, pic_url,
  creator, updater, deleted, tenant_id
)
SELECT inquiry.id,
       NULL,
       item.code,
       item.name,
       item.category,
       item.specification,
       item.quantity,
       item.unit,
       item.remark,
       NULL,
       'mall-inquiry-demo-20260921',
       'mall-inquiry-demo-20260921',
       b'0',
       src.tenant_id
FROM tmp_mall_inquiry_seed_source src
JOIN product_purchase_inquiry inquiry
  ON inquiry.tenant_id = src.tenant_id
 AND inquiry.member_id = src.member_id
 AND inquiry.request_key = src.request_key
 AND inquiry.deleted = b'0'
JOIN tmp_mall_inquiry_seed_item item
  ON item.template_no = src.template_no
WHERE NOT EXISTS (
  SELECT 1
  FROM product_purchase_inquiry_item existing
  WHERE existing.tenant_id = inquiry.tenant_id
    AND existing.inquiry_id = inquiry.id
    AND existing.creator = 'mall-inquiry-demo-20260921'
    AND existing.code = item.code
    AND existing.deleted = b'0'
);

SET @mall_inquiry_seed_missing_items = (
  SELECT COUNT(*)
  FROM tmp_mall_inquiry_seed_source src
  JOIN product_purchase_inquiry inquiry
    ON inquiry.tenant_id = src.tenant_id
   AND inquiry.member_id = src.member_id
   AND inquiry.request_key = src.request_key
   AND inquiry.deleted = b'0'
  WHERE (SELECT COUNT(*)
         FROM product_purchase_inquiry_item item
         WHERE item.tenant_id = inquiry.tenant_id
           AND item.inquiry_id = inquiry.id
           AND item.creator = 'mall-inquiry-demo-20260921'
           AND item.deleted = b'0') <> 3
);
SET @mall_inquiry_seed_items_guard = IF(
  @mall_inquiry_seed_missing_items > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''询价演示数据生成中止：存在明细数量不是三项的询价''',
  'SELECT 1');
PREPARE mall_inquiry_seed_items_stmt FROM @mall_inquiry_seed_items_guard;
EXECUTE mall_inquiry_seed_items_stmt;
DEALLOCATE PREPARE mall_inquiry_seed_items_stmt;

COMMIT;

-- 验收：只统计合法采购成员发布的演示询价，每条询价 3 项物料。
SELECT COUNT(*) AS inquiry_count,
       COUNT(DISTINCT inquiry.tenant_id) AS tenant_count,
       COUNT(DISTINCT inquiry.enterprise_id) AS enterprise_count
FROM product_purchase_inquiry inquiry
WHERE inquiry.creator = 'mall-inquiry-demo-20260921' AND inquiry.deleted = b'0';

SELECT inquiry.tenant_id,
       COUNT(*) AS inquiry_count,
       SUM(item_stats.item_count) AS item_count
FROM product_purchase_inquiry inquiry
JOIN (
  SELECT tenant_id, inquiry_id, COUNT(*) AS item_count
  FROM product_purchase_inquiry_item
  WHERE creator = 'mall-inquiry-demo-20260921' AND deleted = b'0'
  GROUP BY tenant_id, inquiry_id
) item_stats
  ON item_stats.tenant_id = inquiry.tenant_id AND item_stats.inquiry_id = inquiry.id
WHERE inquiry.creator = 'mall-inquiry-demo-20260921' AND inquiry.deleted = b'0'
GROUP BY inquiry.tenant_id
ORDER BY inquiry.tenant_id;

SELECT COUNT(*) AS eligible_buyer_enterprise_without_inquiry
FROM tmp_mall_inquiry_seed_source src
LEFT JOIN product_purchase_inquiry inquiry
  ON inquiry.tenant_id = src.tenant_id
 AND inquiry.member_id = src.member_id
 AND inquiry.request_key = src.request_key
 AND inquiry.deleted = b'0'
WHERE inquiry.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_mall_inquiry_seed_item;
DROP TEMPORARY TABLE IF EXISTS tmp_mall_inquiry_seed_source;
