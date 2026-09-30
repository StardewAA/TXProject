-- ============================================================
-- 专家入驻（一期）：专家资料扩展字段 + 自助申请菜单权限
-- 由 Codex 生成 2026-09-23
-- ============================================================

-- 1) 专家资料扩展：来源 / 等级 / 联系方式 / 从业年限 / 核验信息
ALTER TABLE `cms_expert_profile`
  ADD COLUMN `apply_channel` varchar(16) NOT NULL DEFAULT 'INVITE' COMMENT '来源：INVITE 邀请 / SELF_APPLY 自助申请',
  ADD COLUMN `expert_level` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '等级：NORMAL 基础 / CERTIFIED 专业 / INVITED 特邀',
  ADD COLUMN `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  ADD COLUMN `years_of_experience` int DEFAULT NULL COMMENT '从业年限',
  ADD COLUMN `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  ADD COLUMN `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  ADD COLUMN `audit_by` bigint DEFAULT NULL COMMENT '审核人用户编号',
  ADD COLUMN `verify_result` varchar(1000) DEFAULT NULL COMMENT '自动核验结果（JSON，实名/公开信息比对）';

-- 已存在的邀请制专家标记为特邀
UPDATE `cms_expert_profile` SET `apply_channel`='INVITE', `expert_level`='INVITED' WHERE `apply_channel`='INVITE';

-- 2) 后台菜单：专家管理下新增「专家入驻审核」
INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(70053,'专家入驻审核','cms:expert:apply:audit',2,25,70011,'apply','ep:document-checked','cms/expert/apply/index','CmsExpertApplyAudit',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  path=VALUES(path),component=VALUES(component),component_name=VALUES(component_name),
  status=0,visible=b'1',deleted=b'0',update_time=NOW();

-- 3) 给平台超级管理员授权
INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'1',NOW(),'1',NOW(),b'0',r.tenant_id
FROM system_role r
JOIN system_menu m ON m.id=70053 AND m.deleted=b'0'
WHERE r.code='super_admin' AND r.tenant_id=1 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
  );