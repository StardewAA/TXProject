DELETE FROM cms_recommend_slot WHERE tenant_id=1;
INSERT INTO cms_recommend_slot (tenant_id,name,code,position,content_type,title,target_url,image_url,sort,start_time,status,remark,creator) VALUES
(1,'首页头条-集团简介','home_top_about','HOME_BANNER','ABOUT','天信管业集团简介','/portal/home','/files/assets/关于天信/集团简介/天信管业集团简介_1744449047077182.jpg',10,NOW(),0,'官网素材','import'),
(1,'首页头条-管道防腐','home_top_pipe','HOME_BANNER','PRODUCT','2PE/3PE 外防腐管道','/portal/content','/files/assets/产品图/管道防腐/2PE、3PE外防腐管道_1640944845350640.png',20,NOW(),0,'官网素材','import'),
(1,'首页头条-技术资料','home_top_tech','HOME_BANNER','TECH','海洋钢结构防腐蚀技术综述','/portal/content','/files/assets/PDF文档/海洋钢结构防腐蚀技术综述.pdf',30,NOW(),0,'官网素材','import'),
(1,'侧边推荐-荣誉资质','side_honor','PORTAL_SIDEBAR','ABOUT','企业荣誉资质','/portal/home','/files/assets/关于天信/荣誉资质/企业荣誉资质_1744449047077182.jpg',10,NOW(),0,'官网素材','import'),
(1,'列表推荐-公司新闻','list_news','PORTAL_LIST','NEWS','公司新闻动态','/portal/home','/files/assets/文章图/公司新闻/公司新闻_1744449047077182.jpg',10,NOW(),0,'官网素材','import');