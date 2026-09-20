CREATE TABLE IF NOT EXISTS `wms_warehouse_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `role_type` varchar(16) NOT NULL COMMENT 'MANAGER/MEMBER',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wms_warehouse_user` (`tenant_id`,`warehouse_id`,`user_id`,`deleted`),
  KEY `idx_wms_warehouse_user_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_wms_warehouse_user_role` (`tenant_id`,`warehouse_id`,`role_type`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WMS 仓库人员关系';

SET @tenant_id := 2005;
SET @dept_id := (SELECT id FROM system_dept WHERE tenant_id=@tenant_id AND deleted=b'0' AND name='仓储物流部' LIMIT 1);
SET @password_hash := (SELECT password FROM system_users WHERE tenant_id=@tenant_id AND deleted=b'0' AND username='jinyankuangwu' LIMIT 1);

INSERT INTO system_users (username,password,nickname,dept_id,status,remark,creator,updater,deleted,tenant_id)
SELECT 'xuxiao',@password_hash,'徐小',@dept_id,0,'WMS 仓库人员初始化','system','system',b'0',@tenant_id
WHERE @dept_id IS NOT NULL AND @password_hash IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND deleted=b'0' AND username='xuxiao');
INSERT INTO system_users (username,password,nickname,dept_id,status,remark,creator,updater,deleted,tenant_id)
SELECT 'liuxiao',@password_hash,'刘小',@dept_id,0,'WMS 仓库人员初始化','system','system',b'0',@tenant_id
WHERE @dept_id IS NOT NULL AND @password_hash IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND deleted=b'0' AND username='liuxiao');
INSERT INTO system_users (username,password,nickname,dept_id,status,remark,creator,updater,deleted,tenant_id)
SELECT 'panxiao',@password_hash,'潘小',@dept_id,0,'WMS 仓库人员初始化','system','system',b'0',@tenant_id
WHERE @dept_id IS NOT NULL AND @password_hash IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_users WHERE tenant_id=@tenant_id AND deleted=b'0' AND username='panxiao');

UPDATE system_users SET dept_id=@dept_id,status=0,deleted=b'0'
WHERE tenant_id=@tenant_id AND username IN ('xuxiao','liuxiao','panxiao');

-- 新账号复制潘振瑞的普通 WMS 使用角色，审批人仍只由仓库负责人关系决定。
INSERT INTO system_user_role (user_id,role_id,creator,updater,deleted,tenant_id)
SELECT target.id, source_role.role_id, 'system','system',b'0',@tenant_id
FROM system_users target
JOIN system_users source_user ON source_user.tenant_id=@tenant_id AND source_user.username='panzhenrui' AND source_user.deleted=b'0'
JOIN system_user_role source_role ON source_role.user_id=source_user.id AND source_role.tenant_id=@tenant_id AND source_role.deleted=b'0'
WHERE target.tenant_id=@tenant_id AND target.deleted=b'0' AND target.username IN ('xuxiao','liuxiao','panxiao')
  AND NOT EXISTS (SELECT 1 FROM system_user_role existing
                  WHERE existing.tenant_id=@tenant_id AND existing.deleted=b'0'
                    AND existing.user_id=target.id AND existing.role_id=source_role.role_id);

SET @raw_warehouse_id := (SELECT id FROM wms_warehouse WHERE tenant_id=@tenant_id AND deleted=b'0' AND code='W17533332' LIMIT 1);
SET @finished_warehouse_id := (SELECT id FROM wms_warehouse WHERE tenant_id=@tenant_id AND deleted=b'0' AND code='W64672393' LIMIT 1);

DELETE FROM wms_warehouse_user
WHERE tenant_id=@tenant_id AND warehouse_id IN (@raw_warehouse_id,@finished_warehouse_id);

INSERT INTO wms_warehouse_user (warehouse_id,user_id,role_type,creator,updater,deleted,tenant_id)
SELECT @raw_warehouse_id,id,
       IF(username='xuwenbo','MANAGER','MEMBER'),'system','system',b'0',@tenant_id
FROM system_users
WHERE tenant_id=@tenant_id AND deleted=b'0' AND status=0
  AND username IN ('xuwenbo','panzhenrui','liujian','panxiao');

INSERT INTO wms_warehouse_user (warehouse_id,user_id,role_type,creator,updater,deleted,tenant_id)
SELECT @finished_warehouse_id,id,
       IF(username='xuxiao','MANAGER','MEMBER'),'system','system',b'0',@tenant_id
FROM system_users
WHERE tenant_id=@tenant_id AND deleted=b'0' AND status=0
  AND username IN ('xuxiao','zhangqianqian','lichenduo','songjialiang','liuxiao');

UPDATE wms_warehouse warehouse
JOIN wms_warehouse_user relation ON relation.warehouse_id=warehouse.id AND relation.role_type='MANAGER'
JOIN system_users manager_user ON manager_user.id=relation.user_id
SET warehouse.principal=manager_user.nickname
WHERE warehouse.tenant_id=@tenant_id AND warehouse.id IN (@raw_warehouse_id,@finished_warehouse_id);

SELECT warehouse.name, MAX(CASE WHEN relation.role_type='MANAGER' THEN member_user.nickname END) AS manager_name,
       SUM(relation.role_type IN ('MANAGER','MEMBER')) AS member_count
FROM wms_warehouse warehouse
JOIN wms_warehouse_user relation ON relation.warehouse_id=warehouse.id AND relation.tenant_id=@tenant_id AND relation.deleted=b'0'
JOIN system_users member_user ON member_user.id=relation.user_id
WHERE warehouse.id IN (@raw_warehouse_id,@finished_warehouse_id)
GROUP BY warehouse.id,warehouse.name;
