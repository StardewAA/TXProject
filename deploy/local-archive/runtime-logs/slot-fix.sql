UPDATE cms_recommend_slot SET image_url='/files/assets/关于天信/集团简介/集团简介_664ef42c8b155817538c380f4d37fd76.png' WHERE code='home_top_about';
UPDATE cms_recommend_slot SET image_url='/files/assets/文章图/防腐保温知识/3PE管道防腐涂层质量控制关键因素分析_1686100699484903.jpg' WHERE code='home_top_tech';
UPDATE cms_recommend_slot SET image_url='/files/assets/关于天信/荣誉资质/荣誉资质_0033ad03a711223f9f2dedc382ce3815.jpg' WHERE code='side_honor';
UPDATE cms_recommend_slot SET image_url='/files/assets/文章图/通知公告/宝塔采油厂油管修复项目(四季度)成交结果公示_1701069911882505.png' WHERE code='list_news';
SELECT id, name, position, title, image_url FROM `ruoyi-vue-pro`.cms_recommend_slot WHERE deleted=0 ORDER BY id;