-- Additive schema. Business audit records have no scheduled retention cleanup.
CREATE TABLE IF NOT EXISTS infra_work_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 user_name VARCHAR(128) NOT NULL DEFAULT '',
 module VARCHAR(64) NOT NULL DEFAULT '',
 action VARCHAR(32) NOT NULL DEFAULT '',
 business_key VARCHAR(1000) NOT NULL DEFAULT '',
 summary VARCHAR(512) NOT NULL DEFAULT '',
 request_params LONGTEXT NULL,
 changes LONGTEXT NULL,
 result_code INT NULL,
 result_msg VARCHAR(1024) NULL,
 user_ip VARCHAR(64) NULL,
 user_agent TEXT NULL,
 begin_time DATETIME(3) NOT NULL,
 duration INT NULL,
 trace_id VARCHAR(128) NULL,
 request_url VARCHAR(1024) NULL,
 request_method VARCHAR(16) NULL,
 creator VARCHAR(64) DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT b'0',
 KEY idx_work_trace_person (tenant_id,user_id,begin_time,id),
 KEY idx_work_trace_time (tenant_id,begin_time,id),
 KEY idx_work_trace_module (tenant_id,module,action,begin_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作留痕归档';

-- Global menu metadata; grants below are explicitly limited to the local acceptance tenant.
SET @wms_parent = (SELECT id FROM system_menu WHERE path='/wms' AND deleted=0 LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT '留痕查询','',2,90,@wms_parent,'work-log','lucide:history','wms/work-log/index','WmsWorkLog',0,b'1',b'1',b'1','work-trace','work-trace'
WHERE @wms_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM system_menu WHERE component='wms/work-log/index' AND deleted=0);
SET @trace_menu=(SELECT id FROM system_menu WHERE component='wms/work-log/index' AND deleted=0 LIMIT 1);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater)
SELECT '查看全体成员留痕','infra:work-log:query-all',3,1,@trace_menu,'',0,b'1',b'1',b'1','work-trace','work-trace'
WHERE @trace_menu IS NOT NULL AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='infra:work-log:query-all' AND deleted=0);
SET @trace_all=(SELECT id FROM system_menu WHERE permission='infra:work-log:query-all' AND deleted=0 LIMIT 1);

-- Grant personal entry to active existing tenant roles; preserve every pre-existing grant.
INSERT INTO system_role_menu (role_id,menu_id,tenant_id,creator,updater)
SELECT r.id,m.id,r.tenant_id,'work-trace','work-trace' FROM system_role r
JOIN system_menu m ON m.id IN (@wms_parent,@trace_menu)
WHERE r.tenant_id=2005 AND r.status=0 AND r.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0);

-- Verified role identities: warehouse manager, decision approver, enterprise administrator.
INSERT INTO system_role_menu (role_id,menu_id,tenant_id,creator,updater)
SELECT r.id,@trace_all,r.tenant_id,'work-trace','work-trace' FROM system_role r
WHERE r.tenant_id=2005 AND r.status=0 AND r.deleted=0
AND ((r.id=60096 AND r.name='仓库管理员') OR (r.id=60085 AND r.name='决策审批人') OR (r.id=60084 AND r.code='tenant_admin'))
AND @trace_all IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=@trace_all AND rm.tenant_id=r.tenant_id AND rm.deleted=0);

-- Only the verified single-tenant package is extended. No general package API reassignment.
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',CAST(@trace_menu AS UNSIGNED))
WHERE id=120 AND deleted=0 AND @trace_menu IS NOT NULL
AND (SELECT COUNT(*) FROM system_tenant WHERE package_id=120 AND deleted=0)=1
AND EXISTS(SELECT 1 FROM system_tenant WHERE id=2005 AND package_id=120 AND deleted=0)
AND NOT JSON_CONTAINS(menu_ids,CAST(@trace_menu AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',CAST(@trace_all AS UNSIGNED))
WHERE id=120 AND deleted=0 AND @trace_all IS NOT NULL
AND (SELECT COUNT(*) FROM system_tenant WHERE package_id=120 AND deleted=0)=1
AND EXISTS(SELECT 1 FROM system_tenant WHERE id=2005 AND package_id=120 AND deleted=0)
AND NOT JSON_CONTAINS(menu_ids,CAST(@trace_all AS JSON));
