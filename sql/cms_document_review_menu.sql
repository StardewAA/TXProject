-- 文档审查已并入 CMS 审核管理，不再保留独立“文档审查”菜单。
-- 导入请使用：mysql --default-character-set=utf8mb4
SET @legacy_review_menu = (SELECT id FROM system_menu WHERE permission='cms:review:query' AND component='cms/review/index' AND deleted=b'0' ORDER BY id LIMIT 1);
DELETE FROM system_menu WHERE parent_id=@legacy_review_menu AND deleted=b'0';
DELETE FROM system_menu WHERE id=@legacy_review_menu AND deleted=b'0';

UPDATE system_menu SET name='提交审查', permission='cms:review:query', visible=b'0', sort=5, path='submit', component='cms/review/parts/submit/index', component_name='CmsReviewSubmit', icon='ep:document-add', updater='1', update_time=NOW() WHERE id=70008 AND deleted=b'0';
UPDATE system_menu SET name='审查复核', permission='cms:review:query', visible=b'1', sort=10, path='record', component='cms/review/parts/record/index', component_name='CmsReviewRecord', icon='ep:tickets', updater='1', update_time=NOW() WHERE id=70009 AND deleted=b'0';
UPDATE system_menu SET name='审查报告', permission='cms:review:query', visible=b'0', sort=20, path='report', component='cms/review/parts/report/index', component_name='CmsReviewReport', icon='ep:document-checked', updater='1', update_time=NOW() WHERE id=70010 AND deleted=b'0';

DELETE FROM system_menu WHERE parent_id IN (70008,70009,70010) AND deleted=b'0' AND permission IN ('cms:review:submit','cms:review:query','cms:review:verify');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '提交文档审查','cms:review:submit',3,1,70008,'',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='cms:review:submit' AND parent_id=70008 AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '核验官方来源与效力','cms:review:verify',3,1,70010,'',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='cms:review:verify' AND parent_id=70010 AND deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '修改并重新提交','cms:review:submit',3,2,70010,'',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='cms:review:submit' AND parent_id=70010 AND deleted=b'0');
