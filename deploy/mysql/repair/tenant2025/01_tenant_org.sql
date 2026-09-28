-- ============================================================
-- 天信管业集团（租户 2025）组织架构与用户
-- 防腐保温产业链：施工安装企业
-- ============================================================
SET NAMES utf8mb4;

-- 1. 租户
INSERT INTO system_tenant (id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted)
VALUES (2025, '天信管业集团', NULL, '田新', '13909120001', 0, 'https://www.tianxinpipe.com', 119, '2099-12-31 23:59:59', 9999, '1', NOW(), '1', NOW(), b'0');

-- 2. 部门
INSERT INTO system_dept (id, name, parent_id, sort, leader_user_id, phone, email, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(51000, '天信管业集团', 0, 1, NULL, NULL, NULL, 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51001, '总经办', 51000, 1, 81000, '13909120001', 'tx@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51002, '财务部', 51000, 2, 81003, '13909120002', 'finance@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51003, '人力资源部', 51000, 3, 81006, '13909120003', 'hr@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51004, '项目部', 51000, 4, 81008, '13909120004', 'pm@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51005, '采购部', 51000, 5, 81013, '13909120005', 'purchase@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51006, '销售部', 51000, 6, 81016, '13909120006', 'sales@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51007, '生产部', 51000, 7, 81019, '13909120007', 'prod@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51008, '质检部', 51000, 8, 81022, '13909120008', 'qc@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025),
(51009, '仓储物流部', 51000, 9, 81024, '13909120009', 'wh@tianxinpipe.com', 0, '1', NOW(), '1', NOW(), b'0', 2025);

-- 3. 岗位
INSERT INTO system_post (id, code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(71000, 'tianxin_gm', '总经理', 1, 0, '公司总经理，全面负责经营管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71001, 'tianxin_secretary', '书记', 2, 0, '党组织书记，负责党建与思想工作', '1', NOW(), '1', NOW(), b'0', 2025),
(71002, 'tianxin_gm_assistant', '总经理助理', 3, 0, '协助总经理处理日常事务', '1', NOW(), '1', NOW(), b'0', 2025),
(71003, 'tianxin_hr_manager', 'HR主管', 4, 0, '负责人力资源规划、招聘、绩效', '1', NOW(), '1', NOW(), b'0', 2025),
(71004, 'tianxin_hr_staff', '人事专员', 5, 0, '负责人事档案、考勤、入离职办理', '1', NOW(), '1', NOW(), b'0', 2025),
(71005, 'tianxin_finance_manager', '财务主管', 6, 0, '负责财务核算、预算与资金管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71006, 'tianxin_accountant', '会计', 7, 0, '负责凭证、账务处理与报表', '1', NOW(), '1', NOW(), b'0', 2025),
(71007, 'tianxin_cashier', '出纳', 8, 0, '负责现金银行收付与票据管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71008, 'tianxin_pm', '项目经理', 9, 0, '负责防腐保温工程项目全过程管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71009, 'tianxin_pm_assistant', '项目助理', 10, 0, '协助项目经理进行现场与资料管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71010, 'tianxin_safety', '安全员', 11, 0, '负责施工现场安全文明管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71011, 'tianxin_docs', '资料员', 12, 0, '负责项目技术资料、报验资料归档', '1', NOW(), '1', NOW(), b'0', 2025),
(71012, 'tianxin_purchase_manager', '采购主管', 13, 0, '负责材料采购计划与供应商管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71013, 'tianxin_purchase_staff', '采购员', 14, 0, '负责询比价、下单、跟单', '1', NOW(), '1', NOW(), b'0', 2025),
(71014, 'tianxin_sales_manager', '销售主管', 15, 0, '负责市场开拓与销售团队管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71015, 'tianxin_sales_staff', '销售员', 16, 0, '负责客户开发、报价、跟单', '1', NOW(), '1', NOW(), b'0', 2025),
(71016, 'tianxin_prod_manager', '生产主管', 17, 0, '负责预制保温管、防腐管生产组织', '1', NOW(), '1', NOW(), b'0', 2025),
(71017, 'tianxin_tech', '防腐保温技术员', 18, 0, '负责工艺参数、排版套料与技术交底', '1', NOW(), '1', NOW(), b'0', 2025),
(71018, 'tianxin_qc_manager', '质检主管', 19, 0, '负责来料、过程、出货检验管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71019, 'tianxin_qc_staff', '质检员', 20, 0, '负责原材料与成品检验检测', '1', NOW(), '1', NOW(), b'0', 2025),
(71020, 'tianxin_wh_manager', '仓储主管', 21, 0, '负责仓库收发存与库存管理', '1', NOW(), '1', NOW(), b'0', 2025),
(71021, 'tianxin_wh_staff', '仓管员', 22, 0, '负责出入库作业与盘点', '1', NOW(), '1', NOW(), b'0', 2025);

-- 4. 角色
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(61000, '总经理角色', 'tenant_admin', 1, 1, '[]', 0, 2, '租户管理员，拥有套餐全部权限', '1', NOW(), '1', NOW(), b'0', 2025),
(61001, '财务角色', 'tianxin_finance', 2, 1, '[]', 0, 2, '财务部角色：FMS+ERP财务', '1', NOW(), '1', NOW(), b'0', 2025),
(61002, 'HR角色', 'tianxin_hr', 3, 1, '[]', 0, 2, '人力资源部角色：HRM', '1', NOW(), '1', NOW(), b'0', 2025),
(61003, '项目经理角色', 'tianxin_pm', 4, 1, '[]', 0, 2, '项目部角色：CRM+ERP+MES+WMS', '1', NOW(), '1', NOW(), b'0', 2025),
(61004, '采购角色', 'tianxin_purchase', 5, 1, '[]', 0, 2, '采购部角色：ERP采购+WMS+MES仓库', '1', NOW(), '1', NOW(), b'0', 2025),
(61005, '销售角色', 'tianxin_sales', 6, 1, '[]', 0, 2, '销售部角色：CRM+ERP销售+会员', '1', NOW(), '1', NOW(), b'0', 2025),
(61006, '质检角色', 'tianxin_qc', 7, 1, '[]', 0, 2, '质检部角色：MES质检', '1', NOW(), '1', NOW(), b'0', 2025),
(61007, '生产角色', 'tianxin_production', 8, 1, '[]', 0, 2, '生产部角色：MES+WMS+ERP', '1', NOW(), '1', NOW(), b'0', 2025),
(61008, '仓管角色', 'tianxin_warehouse', 9, 1, '[]', 0, 2, '仓储物流部角色：WMS+MES仓库', '1', NOW(), '1', NOW(), b'0', 2025);

-- 5. 用户（密码均为 admin123，BCrypt）
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(81000, 'tx_admin', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '田新', '总经理', 51001, '[71000]', 'tx_admin@tianxinpipe.com', '13909120001', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81001, 'tx_book', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '赵国强', '党支部书记', 51001, '[71001]', 'tx_book@tianxinpipe.com', '13909120010', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81002, 'tx_gmassist', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '李慧', '总经理助理', 51001, '[71002]', 'tx_gmassist@tianxinpipe.com', '13909120011', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81003, 'tx_finance', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '王芳', '财务主管', 51002, '[71005]', 'tx_finance@tianxinpipe.com', '13909120002', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81004, 'tx_accountant', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '刘敏', '会计', 51002, '[71006]', 'tx_accountant@tianxinpipe.com', '13909120012', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81005, 'tx_cashier', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '陈静', '出纳', 51002, '[71007]', 'tx_cashier@tianxinpipe.com', '13909120013', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81006, 'tx_hr', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '孙丽', 'HR主管', 51003, '[71003]', 'tx_hr@tianxinpipe.com', '13909120003', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81007, 'tx_hr2', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '周婷', '人事专员', 51003, '[71004]', 'tx_hr2@tianxinpipe.com', '13909120014', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81008, 'tx_pm', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '吴刚', '项目经理', 51004, '[71008]', 'tx_pm@tianxinpipe.com', '13909120004', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81009, 'tx_pm2', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '郑强', '项目经理', 51004, '[71008]', 'tx_pm2@tianxinpipe.com', '13909120015', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81010, 'tx_pmassist', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '冯雪', '项目助理', 51004, '[71009]', 'tx_pmassist@tianxinpipe.com', '13909120016', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81011, 'tx_safety', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '彭涛', '安全员', 51004, '[71010]', 'tx_safety@tianxinpipe.com', '13909120017', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81012, 'tx_docs', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '潘婷', '资料员', 51004, '[71011]', 'tx_docs@tianxinpipe.com', '13909120018', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81013, 'tx_purchase', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '何平', '采购主管', 51005, '[71012]', 'tx_purchase@tianxinpipe.com', '13909120005', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81014, 'tx_purchase1', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '高翔', '采购员', 51005, '[71013]', 'tx_purchase1@tianxinpipe.com', '13909120019', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81015, 'tx_purchase2', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '罗峰', '采购员', 51005, '[71013]', 'tx_purchase2@tianxinpipe.com', '13909120020', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81016, 'tx_sales', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '马涛', '销售主管', 51006, '[71014]', 'tx_sales@tianxinpipe.com', '13909120006', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81017, 'tx_sales1', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '梁艳', '销售员', 51006, '[71015]', 'tx_sales1@tianxinpipe.com', '13909120021', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81018, 'tx_sales2', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '宋佳', '销售员', 51006, '[71015]', 'tx_sales2@tianxinpipe.com', '13909120022', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81019, 'tx_prod', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '韩磊', '生产主管', 51007, '[71016]', 'tx_prod@tianxinpipe.com', '13909120007', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81020, 'tx_tech', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '董军', '防腐保温技术员', 51007, '[71017]', 'tx_tech@tianxinpipe.com', '13909120023', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81021, 'tx_tech2', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '崔志刚', '防腐保温技术员', 51007, '[71017]', 'tx_tech2@tianxinpipe.com', '13909120024', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81022, 'tx_qc', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '蒋华', '质检主管', 51008, '[71018]', 'tx_qc@tianxinpipe.com', '13909120008', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81023, 'tx_qc1', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '谢芳', '质检员', 51008, '[71019]', 'tx_qc1@tianxinpipe.com', '13909120025', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81024, 'tx_wh', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '曹亮', '仓储主管', 51009, '[71020]', 'tx_wh@tianxinpipe.com', '13909120009', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025),
(81025, 'tx_wh1', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '邓超', '仓管员', 51009, '[71021]', 'tx_wh1@tianxinpipe.com', '13909120026', 1, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 2025);

-- 6. 用户-角色
INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(81000, 61000, '1', NOW(), '1', NOW(), b'0', 2025),
(81001, 61000, '1', NOW(), '1', NOW(), b'0', 2025),
(81002, 61000, '1', NOW(), '1', NOW(), b'0', 2025),
(81003, 61001, '1', NOW(), '1', NOW(), b'0', 2025),
(81004, 61001, '1', NOW(), '1', NOW(), b'0', 2025),
(81005, 61001, '1', NOW(), '1', NOW(), b'0', 2025),
(81006, 61002, '1', NOW(), '1', NOW(), b'0', 2025),
(81007, 61002, '1', NOW(), '1', NOW(), b'0', 2025),
(81008, 61003, '1', NOW(), '1', NOW(), b'0', 2025),
(81009, 61003, '1', NOW(), '1', NOW(), b'0', 2025),
(81010, 61003, '1', NOW(), '1', NOW(), b'0', 2025),
(81011, 61003, '1', NOW(), '1', NOW(), b'0', 2025),
(81012, 61003, '1', NOW(), '1', NOW(), b'0', 2025),
(81013, 61004, '1', NOW(), '1', NOW(), b'0', 2025),
(81014, 61004, '1', NOW(), '1', NOW(), b'0', 2025),
(81015, 61004, '1', NOW(), '1', NOW(), b'0', 2025),
(81016, 61005, '1', NOW(), '1', NOW(), b'0', 2025),
(81017, 61005, '1', NOW(), '1', NOW(), b'0', 2025),
(81018, 61005, '1', NOW(), '1', NOW(), b'0', 2025),
(81019, 61007, '1', NOW(), '1', NOW(), b'0', 2025),
(81020, 61007, '1', NOW(), '1', NOW(), b'0', 2025),
(81021, 61007, '1', NOW(), '1', NOW(), b'0', 2025),
(81022, 61006, '1', NOW(), '1', NOW(), b'0', 2025),
(81023, 61006, '1', NOW(), '1', NOW(), b'0', 2025),
(81024, 61008, '1', NOW(), '1', NOW(), b'0', 2025),
(81025, 61008, '1', NOW(), '1', NOW(), b'0', 2025);

-- 7. 用户-岗位
INSERT INTO system_user_post (user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
(81000, 71000, '1', NOW(), '1', NOW(), b'0', 2025),
(81001, 71001, '1', NOW(), '1', NOW(), b'0', 2025),
(81002, 71002, '1', NOW(), '1', NOW(), b'0', 2025),
(81003, 71005, '1', NOW(), '1', NOW(), b'0', 2025),
(81004, 71006, '1', NOW(), '1', NOW(), b'0', 2025),
(81005, 71007, '1', NOW(), '1', NOW(), b'0', 2025),
(81006, 71003, '1', NOW(), '1', NOW(), b'0', 2025),
(81007, 71004, '1', NOW(), '1', NOW(), b'0', 2025),
(81008, 71008, '1', NOW(), '1', NOW(), b'0', 2025),
(81009, 71008, '1', NOW(), '1', NOW(), b'0', 2025),
(81010, 71009, '1', NOW(), '1', NOW(), b'0', 2025),
(81011, 71010, '1', NOW(), '1', NOW(), b'0', 2025),
(81012, 71011, '1', NOW(), '1', NOW(), b'0', 2025),
(81013, 71012, '1', NOW(), '1', NOW(), b'0', 2025),
(81014, 71013, '1', NOW(), '1', NOW(), b'0', 2025),
(81015, 71013, '1', NOW(), '1', NOW(), b'0', 2025),
(81016, 71014, '1', NOW(), '1', NOW(), b'0', 2025),
(81017, 71015, '1', NOW(), '1', NOW(), b'0', 2025),
(81018, 71015, '1', NOW(), '1', NOW(), b'0', 2025),
(81019, 71016, '1', NOW(), '1', NOW(), b'0', 2025),
(81020, 71017, '1', NOW(), '1', NOW(), b'0', 2025),
(81021, 71017, '1', NOW(), '1', NOW(), b'0', 2025),
(81022, 71018, '1', NOW(), '1', NOW(), b'0', 2025),
(81023, 71019, '1', NOW(), '1', NOW(), b'0', 2025),
(81024, 71020, '1', NOW(), '1', NOW(), b'0', 2025),
(81025, 71021, '1', NOW(), '1', NOW(), b'0', 2025);

-- 8. 角色-菜单
-- 8.1 总经理角色（租户管理员）：复制租户 2001 租户管理员的 1354 个菜单（即套餐 119 全部菜单）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 61000, menu_id, '1', NOW(), '1', NOW(), b'0', 2025
FROM system_role_menu WHERE tenant_id = 2001 AND role_id = 60000 AND deleted = b'0';

-- 8.2 财务角色：FMS + ERP + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (32000, 2563, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61001, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.3 HR 角色：HRM + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (1476, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61002, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.4 项目经理角色：CRM + ERP + MES + WMS + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (2397, 2563, 5100, 6400, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61003, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.5 采购角色：ERP + WMS + MES + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (2563, 6400, 5100, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61004, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.6 销售角色：CRM + ERP + 会员中心 + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (2397, 2563, 2262, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61005, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.7 质检角色：MES + WMS + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (5100, 6400, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61006, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.8 生产角色：MES + WMS + ERP + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (5100, 6400, 2563, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61007, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.9 仓管角色：WMS + MES + 系统管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (6400, 5100, 1)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61008, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 8.10 总经理角色补充：HRM 与 FMS 菜单（套餐 119 未包含，但租户按需求开通）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE sub AS (
  SELECT id FROM system_menu WHERE id IN (1476, 32000)
  UNION ALL
  SELECT m.id FROM system_menu m JOIN sub s ON m.parent_id = s.id
)
SELECT 61000, id, '1', NOW(), '1', NOW(), b'0', 2025 FROM sub;

-- 9. 回填租户联系人
UPDATE system_tenant SET contact_user_id = 81000 WHERE id = 2025;
