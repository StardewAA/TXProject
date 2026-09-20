-- HRM 政策库：社保/公积金/劳动合同/培训等政策制度，供社保方案、合同模板等关联引用。
-- 幂等：建表用 IF NOT EXISTS，菜单用 INSERT IGNORE，授权用 NOT EXISTS 保护重复插入。

CREATE TABLE IF NOT EXISTS `hrm_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '政策编号',
  `name` varchar(100) NOT NULL COMMENT '政策名称',
  `category` varchar(32) NOT NULL DEFAULT 'other' COMMENT '类别：social_insurance 社保，provident_fund 公积金，labor_contract 劳动合同，training 培训，other 其他',
  `doc_no` varchar(64) DEFAULT NULL COMMENT '文号',
  `publish_org` varchar(100) DEFAULT NULL COMMENT '发布机关',
  `publish_date` date DEFAULT NULL COMMENT '发布日期',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expire_date` date DEFAULT NULL COMMENT '失效日期',
  `scope` varchar(16) NOT NULL DEFAULT 'tenant' COMMENT '适用范围：group 集团，tenant 租户，region 地区',
  `scope_value` varchar(64) DEFAULT NULL COMMENT '范围明细（地区名称等）',
  `content` text COMMENT '政策正文',
  `file_urls` json DEFAULT NULL COMMENT '附件地址（数组）',
  `tags` json DEFAULT NULL COMMENT '标签（数组）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用，1 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_hrm_policy_category` (`category`),
  KEY `idx_hrm_policy_expire` (`expire_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 政策库';

-- 菜单：挂在 HRM 一级目录（1476）下，ID 段 1661-1665（经核查 1660-1700 未占用）
INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1661,'政策制度','hrm:policy:query',2,65,1476,'policy','ep:document','hrm/policy/index','HrmPolicy',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1662,'政策查询','hrm:policy:query',3,1,1661,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1663,'政策新增','hrm:policy:create',3,2,1661,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1664,'政策修改','hrm:policy:update',3,3,1661,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1665,'政策删除','hrm:policy:delete',3,4,1661,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

-- 授权给 HR 角色（各租户 hr_role_*），幂等
INSERT INTO system_role_menu (
    role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT role.id, menu.id, 'admin', NOW(), 'admin', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu
  ON menu.id IN (1661, 1662, 1663, 1664, 1665)
 AND menu.deleted = b'0'
WHERE role.code LIKE 'hr_role_%'
  AND role.deleted = b'0'
  AND role.status = 0
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = menu.id
        AND existing.tenant_id = role.tenant_id
        AND existing.deleted = b'0'
  );
