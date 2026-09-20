-- =============================================================================
-- 文件访问地址改为「相对路径」+ 清洗存量绝对地址
--
-- 【问题】
--   master 文件配置（infra_file_config）的 domain 被写死为 http://127.0.0.1:48080，
--   上传后返回的地址形如：
--       http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/xxx.png
--   于是只有「上传者本机」能打开；局域网同事、或部署到服务器后，
--   浏览器会去请求「访问者自己电脑」的 127.0.0.1，导致图片 / 附件全部加载失败。
--
-- 【方案】
--   把 domain 置空 → 返回相对路径：
--       /admin-api/infra/file/4/get/20260920/xxx.png
--   前端开发环境（vite 已代理 /admin-api）与生产环境（nginx 同理）都会把
--   /admin-api 转发到后端网关，因此无论用 localhost、内网 IP 还是域名访问，
--   浏览器都会自动拼成正确地址，彻底摆脱「机器相关配置」。
--
-- 【特性】幂等，可重复执行。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
-- =============================================================================

-- 1. 文件配置：清掉写死的本机地址（domain 变空串 → 走相对路径）
UPDATE infra_file_config
SET config = REGEXP_REPLACE(config, 'https?://[^",]+:48080', ''),
    updater = '1',
    update_time = NOW()
WHERE deleted = 0
  AND config REGEXP 'https?://[^",]+:48080';

-- 2. 存量文件记录：url 去掉绝对前缀（只清本机 48080 的历史数据，
--    七牛 / 阿里云等对象存储的绝对地址保持不动）
UPDATE infra_file
SET url = REGEXP_REPLACE(url, '^https?://[^/]+:48080', ''),
    updater = '1',
    update_time = NOW()
WHERE deleted = 0
  AND url REGEXP '^https?://[^/]+:48080';

-- 3. CRM 业务表里冗余存储的文件地址（跟进记录 / 售后回访记录）
UPDATE crm_follow_up_record
SET pic_urls  = REGEXP_REPLACE(pic_urls,  'https?://[^",]+:48080', ''),
    file_urls = REGEXP_REPLACE(file_urls, 'https?://[^",]+:48080', '')
WHERE deleted = 0
  AND (pic_urls REGEXP 'https?://[^",]+:48080' OR file_urls REGEXP 'https?://[^",]+:48080');

UPDATE crm_after_sale_visit_record
SET pic_urls  = REGEXP_REPLACE(pic_urls,  'https?://[^",]+:48080', ''),
    file_urls = REGEXP_REPLACE(file_urls, 'https?://[^",]+:48080', '')
WHERE deleted = 0
  AND (pic_urls REGEXP 'https?://[^",]+:48080' OR file_urls REGEXP 'https?://[^",]+:48080');

-- 4. 其他模块（erp / wms / hrm 等）如也有同样问题，可按同一套路自查后清洗：
--    SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND (COLUMN_NAME LIKE '%url%' OR COLUMN_NAME LIKE '%pic%' OR COLUMN_NAME LIKE '%file%');

-- 5. 自查
SELECT id, name, JSON_UNQUOTE(JSON_EXTRACT(config, '$.domain')) AS domain
FROM infra_file_config
WHERE deleted = 0 AND master = 1;

SELECT COUNT(*) AS remaining_local_absolute_url
FROM infra_file
WHERE deleted = 0 AND url LIKE '%:48080/%';
