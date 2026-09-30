DELETE FROM cms_seo_meta WHERE tenant_id=1;
INSERT INTO cms_seo_meta (tenant_id,content_type,content_id,seo_title,seo_keywords,seo_description,creator)
SELECT 1,'FINANCE_POLICY',policy_id,
       CONCAT(LEFT(title, 60), ' - 天信之星政策服务平台'),
       REPLACE(LEFT(title, 80), ' ', ','),
       CONCAT(LEFT(COALESCE(NULLIF(summary,''), content), 150), '（来源：', COALESCE(publish_dept,'官方发布'), '，', COALESCE(publish_date, CURDATE()), '）'),
       'import'
FROM `ruoyi-vue-pro`.cms_policy
WHERE deleted=0 AND (content_type='金融政策' OR content_type='法律法规');