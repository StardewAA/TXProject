-- CMS 管理员工作区：智能检索、审查管理、发布管理。
-- 企业端使用前端固定路由 /portal/content，不在此处绑定企业角色。

INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(70000,'CMS系统','',1,340,0,'/cms','ep:notebook',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70029,'智能检索','cms:law:query',2,10,70000,'search','ep:search','cms/home/index','CmsHome',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70007,'审查管理','',1,20,70000,'review','ep:document-checked',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70008,'提交审查','cms:review:query',2,10,70007,'submit','ep:document-add','cms/review/parts/submit/index','CmsReviewSubmit',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70009,'审查记录','cms:review:query',2,20,70007,'record','ep:tickets','cms/review/parts/record/index','CmsReviewRecord',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70010,'审查报告','cms:review:query',2,30,70007,'report','ep:document-checked','cms/review/parts/report/index','CmsReviewReport',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),type=VALUES(type),sort=VALUES(sort),
 parent_id=VALUES(parent_id),path=VALUES(path),icon=VALUES(icon),component=VALUES(component),
 component_name=VALUES(component_name),status=0,visible=b'1',deleted=b'0',update_time=NOW();

INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(70030,'发布管理','cms:knowledge:query',2,30,70000,'publish','ep:promotion','cms/publish/index','CmsPublishManagement',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),sort=VALUES(sort),parent_id=VALUES(parent_id),
 path=VALUES(path),icon=VALUES(icon),component=VALUES(component),component_name=VALUES(component_name),status=0,visible=b'1',deleted=b'0',update_time=NOW();

-- 保留旧业务页面与数据，但 CMS 一级导航只展示本期三个模块。
UPDATE system_menu
SET visible=b'0', update_time=NOW()
WHERE parent_id=70000 AND id NOT IN (70007,70029,70030) AND deleted=b'0';

INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(70031,'发布已审内容','cms:knowledge:publish',3,1,70030,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70032,'下架已发内容','cms:knowledge:delete',3,2,70030,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70033,'重建检索索引','cms:knowledge:reindex',3,3,70030,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70034,'提交文档审查','cms:review:submit',3,1,70008,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70035,'核验官方来源与效力','cms:review:verify',3,1,70010,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(70036,'修改并重新提交','cms:review:submit',3,2,70010,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),status=0,deleted=b'0',update_time=NOW();
