-- 在现有业务库执行。仅新增菜单，不删除或重置已有CMS菜单和授权。
-- 管理员随后给操作员分配 query/submit，给核验人员分配 verify。
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '文档审查','cms:review:query',2,350,0,'/cms/review','ep:document-checked','cms/review/index','CmsDocumentReview',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='cms:review:query' AND deleted=b'0');
SET @cms_review_menu = (SELECT id FROM system_menu WHERE permission='cms:review:query' AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '提交文档审查','cms:review:submit',3,1,@cms_review_menu,'',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='cms:review:submit' AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '核验官方来源与效力','cms:review:verify',3,2,@cms_review_menu,'',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='cms:review:verify' AND deleted=b'0');
