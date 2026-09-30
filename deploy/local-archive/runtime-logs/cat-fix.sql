DELETE FROM cms_category WHERE tenant_id=1 AND code='finance';
UPDATE cms_category c JOIN (SELECT id FROM cms_category WHERE tenant_id=1 AND code='product') p
  SET c.parent_id=p.id WHERE c.tenant_id=1 AND c.code LIKE 'product-%';
UPDATE cms_category c JOIN (SELECT id FROM cms_category WHERE tenant_id=1 AND code='news') p
  SET c.parent_id=p.id WHERE c.tenant_id=1 AND c.code LIKE 'news-%';
UPDATE cms_category c JOIN (SELECT id FROM cms_category WHERE tenant_id=1 AND code='about') p
  SET c.parent_id=p.id WHERE c.tenant_id=1 AND c.code LIKE 'about-%';
UPDATE cms_category c JOIN (SELECT id FROM cms_category WHERE tenant_id=1 AND code='tech') p
  SET c.parent_id=p.id WHERE c.tenant_id=1 AND c.code LIKE 'tech-%';
UPDATE cms_category SET sort=10 WHERE code='product-管道防腐';
UPDATE cms_category SET sort=20 WHERE code='product-管道检测';
UPDATE cms_category SET sort=30 WHERE code='product-保冷保温';
UPDATE cms_category SET sort=40 WHERE code='product-油管维修';
UPDATE cms_category SET sort=50 WHERE code='product-防腐材料';
UPDATE cms_category SET sort=10 WHERE code='news-公司新闻';
UPDATE cms_category SET sort=20 WHERE code='news-通知公告';
UPDATE cms_category SET sort=30 WHERE code='news-行业动态';
UPDATE cms_category SET sort=40 WHERE code='news-防腐保温知识';
UPDATE cms_category SET sort=10 WHERE code='about-集团简介';
UPDATE cms_category SET sort=20 WHERE code='about-荣誉资质';
UPDATE cms_category SET sort=30 WHERE code='about-企业文化';
UPDATE cms_category SET sort=40 WHERE code='about-员工风采';
UPDATE cms_category SET sort=50 WHERE code='about-厂区风貌';
UPDATE cms_category SET sort=10 WHERE code='tech-技术资料';