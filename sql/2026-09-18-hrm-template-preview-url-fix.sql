-- ============================================================
-- 修复：模板缩略图 / 预览件地址用错接口，导致点「预览」只下载不显示
--   原因：文件服务有两个读取接口
--         /infra/file/{configId}/get/**      -> Content-Disposition: attachment（下载）
--         /infra/file/{configId}/preview/**  -> Content-Disposition: inline（浏览器内嵌渲染）
--         之前把预览件与缩略图也存成了 get 地址，iframe 里就被当成下载，页面空白。
--   同时把地址改为「相对路径」：去掉 http://127.0.0.1:48080 前缀，
--   这样页面从 localhost 或局域网 IP 打开都能正确加载文件。
-- 注意：file_url（docx 下载 + 后端按模板渲染合同）仍保留绝对地址不动，
--       因为后端 HrmContractRenderService 需要通过绝对 URL 读取模板内容。
-- 执行库：ruoyi-vue-pro；可重复执行
-- ============================================================

UPDATE `hrm_contract_template`
SET `preview_url` = REPLACE(
        REGEXP_REPLACE(`preview_url`, '^https?://[^/]+', ''),
        '/get/', '/preview/'),
    `thumbnail_url` = REPLACE(
        REGEXP_REPLACE(`thumbnail_url`, '^https?://[^/]+', ''),
        '/get/', '/preview/'),
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (`preview_url` LIKE 'http%' OR `thumbnail_url` LIKE 'http%');

-- 核对：应全部形如 /admin-api/infra/file/4/preview/...
SELECT `id`, `name`, `category`,
       `thumbnail_url`,
       `preview_url`
FROM `hrm_contract_template`
WHERE `deleted` = b'0'
ORDER BY `category`, `sort`, `id`
LIMIT 5;

SELECT COUNT(*) AS total,
       SUM(`thumbnail_url` LIKE '/admin-api/%/preview/%') AS thumb_ok,
       SUM(`preview_url`   LIKE '/admin-api/%/preview/%') AS preview_ok
FROM `hrm_contract_template`
WHERE `deleted` = b'0';
