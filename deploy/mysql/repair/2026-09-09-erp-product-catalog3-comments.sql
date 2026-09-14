-- ERP 商品评价适配（租户 1 / 新导入商品）
--
-- 目的：新商品导入后，原 product_comment 仍指向已下线的旧 SPU（633、634 等），
--      用户端商品详情因此始终显示“期待您的第一个评价”。本迁移为每个 ERP 商品
--      建立一条可见的测试评价，并把评价中的 SPU、SKU、规格和图片从当前商品数据
--      读取，避免商品再次调整后出现“评价指向旧商品”的展示错误。
--
-- 安全性：只处理租户 1；旧商品评价采用软删除；固定评价编号可重复执行。
-- 评价是本地测试数据，实际交易产生的评价不会被覆盖。
SET NAMES utf8mb4;
START TRANSACTION;

-- 清理已经不属于本次 ERP 商品目录的旧评价（保留数据库记录以便审计）。
UPDATE product_comment
   SET deleted=b'1', updater='erp-import', update_time=NOW()
 WHERE tenant_id=1
   AND deleted=b'0'
   AND (spu_id < 910001 OR spu_id > 910030);

-- 每个新导入商品建立一条评价。SKU 属性、商品名称和图片均以当前表中的值为准。
INSERT INTO product_comment
  (id,user_id,user_nickname,user_avatar,anonymous,order_id,order_item_id,
   spu_id,spu_name,sku_id,sku_pic_url,sku_properties,visible,scores,
   description_scores,benefit_scores,content,pic_urls,reply_status,
   reply_user_id,reply_content,reply_time,creator,create_time,updater,
   update_time,deleted,tenant_id)
SELECT
  940000 + (p.id - 910000),
  0,
  CONCAT('ERP测试用户', LPAD(p.id - 910000, 2, '0')),
  -- 使用当前 SKU 的公开商品图作测试头像，避免浏览器访问宿主机回环地址失败。
  s.pic_url,
  b'0',
  0,
  0,
  p.id,
  p.name,
  s.id,
  s.pic_url,
  s.properties,
  b'1',
  5,
  5,
  5,
  CASE
    WHEN p.id BETWEEN 910001 AND 910008 THEN '涂料颜色和覆盖效果符合档案，适合防腐施工测试。'
    WHEN p.id BETWEEN 910009 AND 910010 THEN '保温材料规格清晰，包装完整，满足保温施工测试。'
    WHEN p.id BETWEEN 910011 AND 910013 THEN '保冷材料参数与商品详情一致，便于按规格选型。'
    WHEN p.id BETWEEN 910014 AND 910015 THEN '防潮辅材尺寸标注清楚，商品信息完整。'
    WHEN p.id BETWEEN 910016 AND 910018 THEN '外护材料外观和规格展示清楚，方便现场采购。'
    WHEN p.id BETWEEN 910019 AND 910022 THEN '管托规格齐全，详情页可按型号核对。'
    WHEN p.id BETWEEN 910023 AND 910026 THEN '施工辅材信息完整，价格和库存展示正常。'
    WHEN p.id BETWEEN 910027 AND 910030 THEN '劳保及工机具参数清楚，适合测试下单流程。'
    ELSE '商品档案信息完整，测试通过。'
  END,
  JSON_ARRAY(),
  b'0',
  NULL,
  NULL,
  NULL,
  'erp-import',
  NOW(),
  'erp-import',
  NOW(),
  b'0',
  1
FROM product_spu p
JOIN product_sku s
  ON s.spu_id=p.id
 AND s.tenant_id=1
 AND s.deleted=b'0'
 AND s.id=(SELECT MIN(s2.id)
             FROM product_sku s2
            WHERE s2.spu_id=p.id
              AND s2.tenant_id=1
              AND s2.deleted=b'0')
WHERE p.tenant_id=1
  AND p.deleted=b'0'
  AND p.id BETWEEN 910001 AND 910030
ON DUPLICATE KEY UPDATE
  spu_id=VALUES(spu_id),
  spu_name=VALUES(spu_name),
  sku_id=VALUES(sku_id),
  sku_pic_url=VALUES(sku_pic_url),
  sku_properties=VALUES(sku_properties),
  updater='erp-import',
  update_time=NOW(),
  deleted=b'0',
  tenant_id=1;

-- 兼容脚本已执行过的记录：同步可访问的商品图作为头像，避免遗留回环地址。
UPDATE product_comment c
JOIN product_sku s
  ON s.id = c.sku_id
 AND s.tenant_id = 1
 AND s.deleted = b'0'
SET c.user_avatar = s.pic_url,
    c.updater = 'erp-import',
    c.update_time = NOW()
WHERE c.tenant_id = 1
  AND c.id BETWEEN 940001 AND 940030
  AND c.deleted = b'0';

COMMIT;
