-- HRM 招聘沟通菜单（新增 ID 从 1641 起；仅 HRM）
INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1641,CONVERT(0xE68B9BE88198E6B29FE9809A USING utf8mb4),'hrm:recruit:chat:query',2,10,1486,'chat','ep:chat-line-round','hrm/recruit/chat/index','HrmRecruitChat',0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
INSERT IGNORE INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1642,CONVERT(0xE68B9BE88198E6B29FE9809AE69FA5E8AFA2 USING utf8mb4),'hrm:recruit:chat:query',3,1,1641,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1643,'招聘沟通回复','hrm:recruit:chat:reply',3,2,1641,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0');
INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1644,'招聘沟通结束','hrm:recruit:chat:end',3,3,1641,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=1644);
UPDATE system_menu SET sort=20 WHERE id=1491 AND parent_id=1486;
UPDATE system_menu SET sort=30 WHERE id=1487 AND parent_id=1486;
UPDATE system_menu SET sort=40 WHERE id=1505 AND parent_id=1486;
