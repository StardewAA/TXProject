-- ============================================================
-- CMS 专家聘请流程
-- 企业管理员聘请本企业用户成为专家，提交后直接启用，不再认证审核。
-- ============================================================

UPDATE system_menu
SET name='聘请专家',
    permission='cms:expert:invite',
    path='invite',
    icon='ep:user-check',
    component='cms/expert/invite/index',
    component_name='CmsExpertInvite',
    status=0,
    visible=b'1',
    deleted=b'0',
    updater='1',
    update_time=NOW()
WHERE id=70012;

-- 门户咨询请求是权限点，由“CMS 专家”角色控制，不显示为后台菜单。
INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(70052,'查看咨询请求','cms:expert:request',3,50,70011,'','',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
 status=0,deleted=b'0',update_time=NOW();

-- 历史待审核申请按新流程直接启用，避免遗留在已经删除的审核队列中。
UPDATE cms_expert_profile
SET audit_status=1,
    audit_remark=NULL,
    enabled=b'1',
    version=version+1,
    updater='1',
    update_time=NOW()
WHERE audit_status=0 AND deleted=b'0';

-- 为已有专家建立可在“系统管理 - 角色管理”中继续维护的角色。
INSERT INTO system_role
(`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'CMS 专家','cms_expert',90,1,'',0,2,'控制专家门户咨询请求权限',
       '1',NOW(),'1',NOW(),b'0',p.tenant_id
FROM (SELECT DISTINCT tenant_id FROM cms_expert_profile WHERE deleted=b'0') p
WHERE NOT EXISTS (
  SELECT 1 FROM system_role r
  WHERE r.tenant_id=p.tenant_id AND r.code='cms_expert' AND r.deleted=b'0'
);

INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,70052,'1',NOW(),'1',NOW(),b'0',r.tenant_id
FROM system_role r
WHERE r.code='cms_expert' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id=r.id AND rm.menu_id=70052
      AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
  );

INSERT INTO system_user_role
(`user_id`,`role_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT p.user_id,r.id,'1',NOW(),'1',NOW(),b'0',p.tenant_id
FROM cms_expert_profile p
JOIN system_role r ON r.tenant_id=p.tenant_id AND r.code='cms_expert'
 AND r.status=0 AND r.deleted=b'0'
WHERE p.audit_status=1 AND p.enabled=b'1' AND p.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_user_role ur
    WHERE ur.user_id=p.user_id AND ur.role_id=r.id
      AND ur.tenant_id=p.tenant_id AND ur.deleted=b'0'
  );

-- 平台超级管理员与各企业租户管理员获得“聘请专家”入口。
INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,70012,'1',NOW(),'1',NOW(),b'0',r.tenant_id
FROM system_role r
WHERE r.code IN ('super_admin','tenant_admin') AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id=r.id AND rm.menu_id=70012
      AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
  );
