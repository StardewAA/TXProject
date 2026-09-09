-- 2026-09-10 超管公司（tenant 1 西安鸥鹏互联科技有限公司）发布门数据补齐
-- 资格门（NoticeService create/update/publish → assertTendererIssuanceAllowed）要求：
--   bidding_subject_auth 存在 (tenant_id=1, subject_type=TENDERER, auth_status=ENABLED)
--   + bidding_subject_profile (tenant_id=1, subject_type=TENDERER) 七必填非空
-- 菜单权限 role1/2 已含 6754-6758（公告管理整枝），仅缺主体资格数据；USCC 为按 GB32100 校验位算法生成的合法样例，
-- 法人/联系人等占位值请按营业执照在主体档案页维护（save 走格式强校验）。

INSERT INTO bidding_subject_auth (id, tenant_id, tenant_name, subject_type, auth_status, authorize_user_id, authorize_time, creator)
VALUES (182143816674784, 1, '西安鸥鹏互联科技有限公司', 'TENDERER', 'ENABLED', 1, NOW(), 'admin');

INSERT INTO bidding_subject_profile (id, tenant_id, subject_type, subject_name, uscc, legal_person_name,
                                      legal_person_type_code, region_code, contact_name, contact_phone, complete_flag, creator)
VALUES (182143816674785, 1, 'TENDERER', '西安鸥鹏互联科技有限公司', '91610100MAM94DYHJ0', '示例法人（请按营业执照维护）',
        '1', '610100', 'OPENLAB', '18625372606', 1, 'admin');

-- 校验
SELECT id, tenant_id, tenant_name, subject_type, auth_status FROM bidding_subject_auth WHERE tenant_id = 1;
SELECT id, tenant_id, subject_type, subject_name, complete_flag FROM bidding_subject_profile WHERE tenant_id = 1;
