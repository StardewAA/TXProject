-- 科盾商城快递公司数据整理：移除测试项，并从平台公共租户同步正式快递公司。
-- 可重复执行，仅影响 tenant_id=2009。
START TRANSACTION;

DELETE FROM trade_delivery_express
WHERE tenant_id=2009
  AND (code='325467' OR name IN ('测试快递公司', '测试物流公司'));

UPDATE trade_delivery_express target
JOIN trade_delivery_express source
  ON source.tenant_id=1 AND source.deleted=b'0' AND source.code=target.code
SET target.name=source.name,
    target.logo=source.logo,
    target.sort=source.sort,
    target.status=0,
    target.deleted=b'0',
    target.updater='mall-delivery-fix-20260918',
    target.update_time=NOW()
WHERE target.tenant_id=2009
  AND target.code IN ('SF', 'STO', 'ZTO', 'YD');

INSERT INTO trade_delivery_express
  (code,name,logo,sort,status,creator,updater,deleted,tenant_id)
SELECT source.code,source.name,source.logo,source.sort,0,
       'mall-delivery-fix-20260918','mall-delivery-fix-20260918',b'0',2009
FROM trade_delivery_express source
WHERE source.tenant_id=1
  AND source.deleted=b'0'
  AND source.code IN ('SF', 'STO', 'ZTO', 'YD')
  AND NOT EXISTS (
    SELECT 1
    FROM trade_delivery_express target
    WHERE target.tenant_id=2009 AND target.code=source.code
  );

-- 平台历史 Logo 文件已失效，为当前商城写入自有的轻量 SVG 标识，避免外链破图。
DELETE FROM infra_file
WHERE config_id=4 AND path LIKE 'mall/delivery/20260918/%';
DELETE FROM infra_file_content
WHERE config_id=4 AND path LIKE 'mall/delivery/20260918/%';

INSERT INTO infra_file_content(config_id,path,content,creator,updater,deleted) VALUES
(4,'mall/delivery/20260918/sf.svg',CONVERT(_utf8mb4'<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180"><rect width="180" height="180" rx="34" fill="#171717"/><path d="M24 52h132v54H24z" fill="#e31e24"/><text x="90" y="91" text-anchor="middle" font-family="Arial" font-size="44" font-weight="700" fill="white">SF</text><text x="90" y="139" text-anchor="middle" font-family="Microsoft YaHei" font-size="20" font-weight="700" fill="white">顺丰速运</text></svg>' USING binary),'mall-delivery-fix-20260918','mall-delivery-fix-20260918',b'0'),
(4,'mall/delivery/20260918/sto.svg',CONVERT(_utf8mb4'<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180"><rect width="180" height="180" rx="34" fill="#1768ac"/><circle cx="90" cy="72" r="47" fill="white"/><text x="90" y="84" text-anchor="middle" font-family="Arial" font-size="34" font-weight="700" fill="#1768ac">STO</text><text x="90" y="143" text-anchor="middle" font-family="Microsoft YaHei" font-size="20" font-weight="700" fill="white">申通快递</text></svg>' USING binary),'mall-delivery-fix-20260918','mall-delivery-fix-20260918',b'0'),
(4,'mall/delivery/20260918/zto.svg',CONVERT(_utf8mb4'<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180"><rect width="180" height="180" rx="34" fill="#1684c7"/><path d="M29 44h122v64H29z" rx="15" fill="white"/><text x="90" y="88" text-anchor="middle" font-family="Arial" font-size="39" font-weight="700" fill="#1684c7">ZTO</text><text x="90" y="143" text-anchor="middle" font-family="Microsoft YaHei" font-size="20" font-weight="700" fill="white">中通快递</text></svg>' USING binary),'mall-delivery-fix-20260918','mall-delivery-fix-20260918',b'0'),
(4,'mall/delivery/20260918/yd.svg',CONVERT(_utf8mb4'<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180"><rect width="180" height="180" rx="34" fill="#ed1b2f"/><path d="M28 42h124v68H28z" fill="#ffd329"/><text x="90" y="88" text-anchor="middle" font-family="Arial" font-size="41" font-weight="700" fill="#d71920">YD</text><text x="90" y="143" text-anchor="middle" font-family="Microsoft YaHei" font-size="20" font-weight="700" fill="white">韵达快递</text></svg>' USING binary),'mall-delivery-fix-20260918','mall-delivery-fix-20260918',b'0');

INSERT INTO infra_file(config_id,name,path,url,type,size,creator,updater,deleted)
SELECT config_id,SUBSTRING_INDEX(path,'/',-1),path,
       CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/',path),
       'image/svg+xml',OCTET_LENGTH(content),
       'mall-delivery-fix-20260918','mall-delivery-fix-20260918',b'0'
FROM infra_file_content
WHERE config_id=4 AND path LIKE 'mall/delivery/20260918/%' AND deleted=b'0';

UPDATE trade_delivery_express
SET logo=CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/mall/delivery/20260918/',LOWER(code),'.svg'),
    updater='mall-delivery-fix-20260918',update_time=NOW()
WHERE tenant_id=2009 AND code IN ('SF','STO','ZTO','YD') AND deleted=b'0';

COMMIT;
