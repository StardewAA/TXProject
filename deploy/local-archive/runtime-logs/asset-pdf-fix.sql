DELETE FROM cms_asset WHERE tenant_id=1 AND asset_type='PDF';
INSERT INTO cms_asset (tenant_id,name,asset_type,url,size,mime,category_id,tags,creator) VALUES
(1,'（成交通知）子长采油厂2021年注水井井 下管柱内衬防腐.pdf','PDF','/files/assets/PDF文档/（成交通知）子长采油厂2021年注水井井 下管柱内衬防腐.pdf',453567,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'储油罐各部位腐蚀原理及防腐措施 ，腐蚀从此无处可逃.pdf','PDF','/files/assets/PDF文档/储油罐各部位腐蚀原理及防腐措施 ，腐蚀从此无处可逃.pdf',1200314,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'海洋钢结构防腐蚀技术综述.pdf','PDF','/files/assets/PDF文档/海洋钢结构防腐蚀技术综述.pdf',302734,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'石墨烯防腐涂层研究进展.pdf','PDF','/files/assets/PDF文档/石墨烯防腐涂层研究进展.pdf',612764,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'压力管道用金属材料的腐蚀有哪些？.pdf','PDF','/files/assets/PDF文档/压力管道用金属材料的腐蚀有哪些？.pdf',749714,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'一组图带你解读耐蚀钢发展方向.pdf','PDF','/files/assets/PDF文档/一组图带你解读耐蚀钢发展方向.pdf',2591713,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'长输管道内腐蚀外检测技术在检验中的研究与应用.pdf','PDF','/files/assets/PDF文档/长输管道内腐蚀外检测技术在检验中的研究与应用.pdf',293082,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'智能自修复防腐涂层研究进展综述.pdf','PDF','/files/assets/PDF文档/智能自修复防腐涂层研究进展综述.pdf',269751,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'子长釆油厂2021年注水井井下管柱内衬防腐成交公告.pdf','PDF','/files/assets/PDF文档/子长釆油厂2021年注水井井下管柱内衬防腐成交公告.pdf',151746,'application/pdf',NULL,'PDF文档/技术资料','import'),
(1,'子长采油厂转注井井下管柱超高分子聚乙烯内衬防腐工程.pdf','PDF','/files/assets/PDF文档/子长采油厂转注井井下管柱超高分子聚乙烯内衬防腐工程.pdf',126871,'application/pdf',NULL,'PDF文档/技术资料','import');
SELECT COUNT(*) AS pdf_assets FROM uoyi-vue-pro.cms_asset WHERE tenant_id=1 AND asset_type='PDF';