-- 科盾商城写实商品图片替换（执行前将 kedun-real/*.png 放入 MySQL 容器
-- /var/lib/mysql-files/kedun-real/；可重复执行）
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_kedun_real_image;
CREATE TEMPORARY TABLE tmp_kedun_real_image (
  spu_id BIGINT PRIMARY KEY,
  image_name VARCHAR(64) NOT NULL
);
INSERT INTO tmp_kedun_real_image(spu_id,image_name) VALUES
(940201,'coating.png'),(940202,'coating.png'),(940203,'coating.png'),
(940204,'fireproof-coating.png'),(940205,'mineral-wool.png'),
(940206,'elastomeric-rubber.png'),(940207,'preinsulated-pipe.png'),
(940208,'3pe-pipe.png'),(940209,'ceramic-fiber.png'),(940210,'ceramic-fiber.png'),
(940211,'coating.png'),(940212,'coating.png'),(940213,'coating.png'),
(940214,'coating.png'),(940216,'coating.png'),(940217,'coating.png'),
(940218,'fireproof-coating.png'),(940219,'fireproof-coating.png'),
(940220,'coating.png'),(940221,'mineral-wool.png'),(940222,'mineral-wool.png'),
(940223,'ceramic-fiber.png'),(940224,'elastomeric-rubber.png'),
(940225,'rigid-foam-board.png'),(940226,'rigid-foam-board.png'),
(940227,'metal-cladding.png'),(940228,'metal-cladding.png'),
(940229,'mastic-adhesive.png'),(940230,'fiberglass-cloth.png'),
(940231,'preinsulated-pipe.png'),(940232,'preinsulated-pipe.png'),
(940233,'3pe-pipe.png'),(940234,'pipe-support.png'),
(940235,'mastic-adhesive.png');

DELETE FROM infra_file_content
WHERE config_id=4 AND path LIKE 'mall/catalog/20260917/kedun-real/%';
DELETE FROM infra_file
WHERE config_id=4 AND path LIKE 'mall/catalog/20260917/kedun-real/%';

INSERT INTO infra_file_content(config_id,path,content,creator,updater,deleted)
SELECT 4,
       CONCAT('mall/catalog/20260917/kedun-real/', image_name),
       LOAD_FILE(CONCAT('/var/lib/mysql-files/kedun-real/', image_name)),
       'mall-kedun-real', 'mall-kedun-real', b'0'
FROM (SELECT DISTINCT image_name FROM tmp_kedun_real_image) files;

INSERT INTO infra_file(config_id,name,path,url,type,size,creator,updater,deleted)
SELECT 4, image_name,
       CONCAT('mall/catalog/20260917/kedun-real/', image_name),
       CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/mall/catalog/20260917/kedun-real/', image_name),
       'image/png',
       OCTET_LENGTH(LOAD_FILE(CONCAT('/var/lib/mysql-files/kedun-real/', image_name))),
       'mall-kedun-real', 'mall-kedun-real', b'0'
FROM (SELECT DISTINCT image_name FROM tmp_kedun_real_image) files;

UPDATE product_spu spu
JOIN tmp_kedun_real_image fix ON fix.spu_id=spu.id
SET spu.pic_url=CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/mall/catalog/20260917/kedun-real/',fix.image_name),
    spu.slider_pic_urls=JSON_ARRAY(CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/mall/catalog/20260917/kedun-real/',fix.image_name)),
    spu.description=REGEXP_REPLACE(
      spu.description,
      'http://127\\.0\\.0\\.1:48080/admin-api/infra/file/4/get/mall/catalog/20260917/kedun/940[0-9]+\\.svg',
      CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/mall/catalog/20260917/kedun-real/',fix.image_name)
    ),
    spu.updater='mall-kedun-real',spu.update_time=NOW()
WHERE spu.tenant_id=2009 AND spu.deleted=b'0';

UPDATE product_sku sku JOIN product_spu spu
  ON spu.tenant_id=sku.tenant_id AND spu.id=sku.spu_id AND spu.deleted=b'0'
SET sku.pic_url=spu.pic_url,sku.updater='mall-kedun-real',sku.update_time=NOW()
WHERE sku.tenant_id=2009 AND sku.deleted=b'0' AND spu.id BETWEEN 940201 AND 940235;

UPDATE trade_order_item item JOIN product_spu spu
  ON spu.tenant_id=item.tenant_id AND spu.id=item.spu_id AND spu.deleted=b'0'
SET item.pic_url=spu.pic_url,item.updater='mall-kedun-real'
WHERE item.tenant_id=2009 AND spu.id BETWEEN 940201 AND 940235;

UPDATE product_comment comment JOIN product_spu spu
  ON spu.tenant_id=comment.tenant_id AND spu.id=comment.spu_id AND spu.deleted=b'0'
SET comment.sku_pic_url=spu.pic_url,comment.pic_urls=JSON_ARRAY(spu.pic_url),comment.updater='mall-kedun-real'
WHERE comment.tenant_id=2009 AND spu.id BETWEEN 940201 AND 940235;

UPDATE product_brand brand
JOIN (SELECT brand_id,MIN(pic_url) pic_url FROM product_spu
      WHERE tenant_id=2009 AND deleted=b'0' GROUP BY brand_id) spu ON spu.brand_id=brand.id
SET brand.pic_url=spu.pic_url,brand.updater='mall-kedun-real'
WHERE brand.tenant_id=2009 AND brand.id BETWEEN 940101 AND 940108;

UPDATE product_category category
JOIN (SELECT category_id,MIN(pic_url) pic_url FROM product_spu
      WHERE tenant_id=2009 AND deleted=b'0' GROUP BY category_id) spu ON spu.category_id=category.id
SET category.pic_url=spu.pic_url,category.updater='mall-kedun-real'
WHERE category.tenant_id=2009 AND category.id BETWEEN 940001 AND 940012;

COMMIT;
