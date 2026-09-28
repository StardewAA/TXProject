-- 第 22 节：平台账号冲突人工审核/归并后台闭环
-- 仅建表、菜单和租户 1 超级管理员权限；本脚本不会自动合并任何会员身份。
-- 幂等：可重复执行。

CREATE TABLE IF NOT EXISTS member_platform_account_identity_review (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '审计编号',
  review_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '线下人工身份审核单号',
  target_platform_account_id bigint NOT NULL COMMENT '目标平台账号编号',
  selected_link_ids varchar(2000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求选择的链接编号（升序）',
  link_snapshot_json text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作前链接快照；不含密码散列',
  review_note varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '人工核验依据摘要',
  operator_user_id bigint NOT NULL COMMENT '平台管理员编号',
  operator_tenant_id bigint NOT NULL COMMENT '操作租户，固定为 1',
  status varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'APPLIED',
  applied_at datetime NOT NULL COMMENT '生效时间',
  creator varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_platform_identity_review_no (review_no),
  KEY idx_member_platform_identity_review_account (target_platform_account_id, applied_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='平台账号人工身份归并审计（只追加）';

-- 身份归并是平台级敏感能力，页面只授予租户 1 的 super_admin。
SET @member_root_menu_id := (
  SELECT parent_id FROM system_menu
  WHERE component='member/user/index' AND deleted=b'0'
  ORDER BY id LIMIT 1
);

INSERT INTO system_menu
  (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,
   keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '平台账号审核','',2,90,@member_root_menu_id,'platform-account-review','ep:connection',
       'member/platform-account-review/index','MemberPlatformAccountReview',0,b'1',b'1',b'1',
       'section22',NOW(),'section22',NOW(),b'0'
WHERE @member_root_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu
    WHERE component='member/platform-account-review/index' AND deleted=b'0'
  );

SET @platform_review_menu_id := (
  SELECT id FROM system_menu
  WHERE component='member/platform-account-review/index' AND deleted=b'0'
  ORDER BY id LIMIT 1
);

INSERT INTO system_menu
  (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,
   keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '平台账号查询','member:platform-account:query',3,1,@platform_review_menu_id,'','','',NULL,
       0,b'1',b'1',b'1','section22',NOW(),'section22',NOW(),b'0'
WHERE @platform_review_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu
    WHERE permission='member:platform-account:query' AND deleted=b'0'
  );

INSERT INTO system_menu
  (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,
   keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '平台账号人工归并','member:platform-account:review',3,2,@platform_review_menu_id,'','','',NULL,
       0,b'1',b'1',b'1','section22',NOW(),'section22',NOW(),b'0'
WHERE @platform_review_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu
    WHERE permission='member:platform-account:review' AND deleted=b'0'
  );

INSERT INTO system_role_menu
  (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT r.id,m.id,'section22',NOW(),'section22',NOW(),b'0',1
FROM system_role r
JOIN system_menu m ON m.deleted=b'0'
  AND (m.id=@platform_review_menu_id
       OR m.permission IN ('member:platform-account:query','member:platform-account:review'))
WHERE r.tenant_id=1 AND r.code='super_admin' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.deleted=b'0' AND rm.tenant_id=1
  );

-- 安全验收：任何 tenant 3000 READY 链接都必须保持为 0；脚本不自动修复或归并。
SELECT COUNT(*) AS tenant_3000_ready_link_count
FROM member_platform_account_link
WHERE source_tenant_id=3000 AND link_status='READY' AND deleted=b'0';
