-- HRM 招聘沟通支持企业端手动将求职者转为候选人。
INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1647,CONVERT(0xE8BDACE4B8BAEE58099E98089E4BABAE4BABAE5ADA6 USING utf8mb4),'hrm:recruit:chat:convert-candidate',3,5,1641,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');

INSERT INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source.`role_id`,1647,'admin',NOW(),'admin',NOW(),b'0',source.`tenant_id`
FROM system_role_menu source
JOIN system_role role ON role.id=source.role_id
WHERE source.menu_id=1641 AND source.deleted=b'0' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id=source.role_id AND existing.menu_id=1647 AND existing.deleted=b'0'
  )
GROUP BY source.`role_id`,source.`tenant_id`;
