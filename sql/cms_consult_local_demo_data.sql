-- M3/M4 本地联调数据：一个已审核通过的专家账号（密码与 admin 相同）
INSERT INTO system_users (username, password, nickname, status, creator, tenant_id)
SELECT 'expert01', u.password, '防腐保温专家01', 0, '1', 1
FROM system_users u WHERE u.id = 1
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_users WHERE username='expert01') t);

INSERT INTO cms_expert_profile (tenant_id, user_id, expert_name, professional_title, company,
                                service_fields, introduction, fixed_price, audit_status, enabled)
SELECT 1, u.id, '防腐保温专家01', '高级工程师', '天星管业集团',
       '防腐保温施工、材料选型、标准解读', '20 年防腐保温工程经验，熟悉 GB/T 50126 等标准。',
       10000, 1, b'1'
FROM system_users u WHERE u.username = 'expert01' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM cms_expert_profile WHERE user_id = u.id) t);