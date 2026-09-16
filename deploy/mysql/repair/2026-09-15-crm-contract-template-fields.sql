-- ============================================================================
-- CRM 合同模板化改造：合同主表 / 合同产品明细表 / 合同配置表 扩字段
-- 日期：2026-09-15
--
-- 背景：
--   业务提供两份正式合同模板（《采购合同-模板》《购销合同-模板》），要求：
--     1) 合同支持「模板类型」切换（1=采购合同，2=购销合同）；
--     2) 模板中所有需填写的空白项（签订地点/日期、双方主体信息、项目信息、
--        交货验收天数、付款比例与金额、质保售后、违约责任等）都可在系统中录入；
--     3) 生成合同时严格按模板格式输出，不缺项。
--
-- 执行方式（务必带 utf8mb4，否则中文注释与默认值会变成乱码）：
--   docker cp deploy/mysql/repair/2026-09-15-crm-contract-template-fields.sql <mysql容器>:/tmp/
--   docker exec <mysql容器> sh -c "mysql --default-character-set=utf8mb4 -uroot -p'xxx' < /tmp/2026-09-15-crm-contract-template-fields.sql"
--
-- 处理原则：
--   1. 只新增列，不修改/删除既有列，历史数据不受影响；
--   2. 脚本可重复执行（通过 add_col_if_missing 存储过程实现幂等）；
--   3. 新增列全部可空，由后端在保存时按「合同配置默认值 + 客户档案」自动带入。
-- ============================================================================

USE `ruoyi-vue-pro`;

-- ---------------------------------------------------------------------------
-- 0. 幂等加列工具：列不存在时才 ADD COLUMN
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_col_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_col_if_missing`(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------------
-- 1. crm_contract：合同主表
-- ---------------------------------------------------------------------------

-- 1.1 模板与项目信息
CALL add_col_if_missing('crm_contract', 'template_type',
    'template_type tinyint DEFAULT 1 COMMENT ''合同模板类型：1=采购合同，2=购销合同''');
CALL add_col_if_missing('crm_contract', 'sign_place',
    'sign_place varchar(128) NULL COMMENT ''签订地点''');
CALL add_col_if_missing('crm_contract', 'sign_date',
    'sign_date datetime NULL COMMENT ''签订日期''');
CALL add_col_if_missing('crm_contract', 'project_no',
    'project_no varchar(64) NULL COMMENT ''项目编号''');
CALL add_col_if_missing('crm_contract', 'project_name',
    'project_name varchar(255) NULL COMMENT ''采购项目名称/建设项目名称''');
CALL add_col_if_missing('crm_contract', 'project_area',
    'project_area varchar(64) NULL COMMENT ''地名（购销合同）''');
CALL add_col_if_missing('crm_contract', 'attachment_copies',
    'attachment_copies int NULL COMMENT ''附件份数''');
CALL add_col_if_missing('crm_contract', 'attachment_pages',
    'attachment_pages int NULL COMMENT ''附件页数''');
CALL add_col_if_missing('crm_contract', 'contract_copies',
    'contract_copies int NULL COMMENT ''合同份数''');
CALL add_col_if_missing('crm_contract', 'gpc_name',
    'gpc_name varchar(128) NULL COMMENT ''政府采购中心名称''');

-- 1.2 质量、交货与验收
CALL add_col_if_missing('crm_contract', 'quality_standard',
    'quality_standard varchar(255) NULL COMMENT ''国家（行业）标准''');
CALL add_col_if_missing('crm_contract', 'sample_days',
    'sample_days int NULL COMMENT ''送样天数''');
CALL add_col_if_missing('crm_contract', 'delivery_days',
    'delivery_days int NULL COMMENT ''交货期限天数''');
CALL add_col_if_missing('crm_contract', 'delivery_arrive_days',
    'delivery_arrive_days int NULL COMMENT ''交货到指定地点天数''');
CALL add_col_if_missing('crm_contract', 'install_days',
    'install_days int NULL COMMENT ''安装调试验收天数''');
CALL add_col_if_missing('crm_contract', 'delivery_deadline',
    'delivery_deadline datetime NULL COMMENT ''最迟完成安装调试验收日期''');
CALL add_col_if_missing('crm_contract', 'first_accept_days',
    'first_accept_days int NULL COMMENT ''初步验收天数''');
CALL add_col_if_missing('crm_contract', 'trial_days',
    'trial_days int NULL COMMENT ''试用期天数''');
CALL add_col_if_missing('crm_contract', 'final_accept_days',
    'final_accept_days int NULL COMMENT ''最终验收天数''');
CALL add_col_if_missing('crm_contract', 'auto_accept_days',
    'auto_accept_days int NULL COMMENT ''甲方不验收视同合格天数''');
CALL add_col_if_missing('crm_contract', 'max_repair_times',
    'max_repair_times int NULL COMMENT ''维修次数上限（达到可退货）''');

-- 1.3 付款方式
CALL add_col_if_missing('crm_contract', 'prepay_amount',
    'prepay_amount decimal(20,2) NULL COMMENT ''首付款金额''');
CALL add_col_if_missing('crm_contract', 'prepay_days',
    'prepay_days int NULL COMMENT ''首付支付天数''');
CALL add_col_if_missing('crm_contract', 'prepay_ratio',
    'prepay_ratio decimal(10,2) NULL COMMENT ''首付款比例(%)''');
CALL add_col_if_missing('crm_contract', 'accept_pay_days',
    'accept_pay_days int NULL COMMENT ''验收合格后付款天数/工作日''');
CALL add_col_if_missing('crm_contract', 'accept_pay_ratio',
    'accept_pay_ratio decimal(10,2) NULL COMMENT ''验收款比例(%)''');
CALL add_col_if_missing('crm_contract', 'accept_pay_amount',
    'accept_pay_amount decimal(20,2) NULL COMMENT ''验收款金额''');
CALL add_col_if_missing('crm_contract', 'deposit_months',
    'deposit_months int NULL COMMENT ''履约保证金月数''');
CALL add_col_if_missing('crm_contract', 'deposit_days',
    'deposit_days int NULL COMMENT ''保证金退还天数''');
CALL add_col_if_missing('crm_contract', 'deposit_amount',
    'deposit_amount decimal(20,2) NULL COMMENT ''保证金金额''');
CALL add_col_if_missing('crm_contract', 'deposit_ratio',
    'deposit_ratio decimal(10,2) NULL COMMENT ''保证金比例(%)''');

-- 1.4 售后服务
CALL add_col_if_missing('crm_contract', 'warranty_years',
    'warranty_years int NULL COMMENT ''质保年数''');
CALL add_col_if_missing('crm_contract', 'response_hours',
    'response_hours int NULL COMMENT ''响应到场小时数''');
CALL add_col_if_missing('crm_contract', 'repair_hours',
    'repair_hours int NULL COMMENT ''完成维修小时数''');
CALL add_col_if_missing('crm_contract', 'repair_times',
    'repair_times int NULL COMMENT ''质保期内维修次数上限''');
CALL add_col_if_missing('crm_contract', 'report_repair_days',
    'report_repair_days int NULL COMMENT ''报修无法解决提供代用品天数''');

-- 1.5 甲方（采购人）主体信息
CALL add_col_if_missing('crm_contract', 'party_a_name',
    'party_a_name varchar(255) NULL COMMENT ''甲方单位名称''');
CALL add_col_if_missing('crm_contract', 'party_a_legal_person',
    'party_a_legal_person varchar(64) NULL COMMENT ''甲方法定代表人''');
CALL add_col_if_missing('crm_contract', 'party_a_agent',
    'party_a_agent varchar(64) NULL COMMENT ''甲方授权代表/授权委托人''');
CALL add_col_if_missing('crm_contract', 'party_a_address',
    'party_a_address varchar(255) NULL COMMENT ''甲方地址''');
CALL add_col_if_missing('crm_contract', 'party_a_bank_name',
    'party_a_bank_name varchar(128) NULL COMMENT ''甲方开户银行''');
CALL add_col_if_missing('crm_contract', 'party_a_bank_account',
    'party_a_bank_account varchar(64) NULL COMMENT ''甲方银行账号''');
CALL add_col_if_missing('crm_contract', 'party_a_tel',
    'party_a_tel varchar(32) NULL COMMENT ''甲方电话''');
CALL add_col_if_missing('crm_contract', 'party_a_fax',
    'party_a_fax varchar(32) NULL COMMENT ''甲方传真''');

-- 1.6 乙方（供应商）主体信息
CALL add_col_if_missing('crm_contract', 'party_b_name',
    'party_b_name varchar(255) NULL COMMENT ''乙方单位名称''');
CALL add_col_if_missing('crm_contract', 'party_b_legal_person',
    'party_b_legal_person varchar(64) NULL COMMENT ''乙方法定代表人''');
CALL add_col_if_missing('crm_contract', 'party_b_agent',
    'party_b_agent varchar(64) NULL COMMENT ''乙方授权代表/授权委托人''');
CALL add_col_if_missing('crm_contract', 'party_b_address',
    'party_b_address varchar(255) NULL COMMENT ''乙方地址''');
CALL add_col_if_missing('crm_contract', 'party_b_bank_name',
    'party_b_bank_name varchar(128) NULL COMMENT ''乙方开户银行''');
CALL add_col_if_missing('crm_contract', 'party_b_bank_account',
    'party_b_bank_account varchar(64) NULL COMMENT ''乙方银行账号''');
CALL add_col_if_missing('crm_contract', 'party_b_tel',
    'party_b_tel varchar(32) NULL COMMENT ''乙方电话''');
CALL add_col_if_missing('crm_contract', 'party_b_fax',
    'party_b_fax varchar(32) NULL COMMENT ''乙方传真''');

-- 1.7 违约责任（JSON，默认取合同配置，允许单份合同覆盖）
CALL add_col_if_missing('crm_contract', 'penalty_terms',
    'penalty_terms text NULL COMMENT ''违约责任条款(JSON)''');

-- ---------------------------------------------------------------------------
-- 2. crm_contract_product：合同产品明细表（对应模板「一、合同货物」表格）
-- ---------------------------------------------------------------------------
CALL add_col_if_missing('crm_contract_product', 'spec',
    'spec varchar(255) NULL COMMENT ''规格型号''');
CALL add_col_if_missing('crm_contract_product', 'unit',
    'unit varchar(32) NULL COMMENT ''单位''');
CALL add_col_if_missing('crm_contract_product', 'fund_source',
    'fund_source tinyint NULL COMMENT ''资金来源：1=预算内，2=预算外，3=其他''');
CALL add_col_if_missing('crm_contract_product', 'accessories',
    'accessories varchar(255) NULL COMMENT ''随机配件''');
CALL add_col_if_missing('crm_contract_product', 'delivery_period',
    'delivery_period varchar(64) NULL COMMENT ''交货期''');

-- ---------------------------------------------------------------------------
-- 3. crm_contract_config：合同配置表（我方主体 + 各项默认值）
-- ---------------------------------------------------------------------------

-- 3.1 我方主体信息（创建合同时自动带入，允许单份合同覆盖）
CALL add_col_if_missing('crm_contract_config', 'our_company_name',
    'our_company_name varchar(255) NULL COMMENT ''我方公司名称''');
CALL add_col_if_missing('crm_contract_config', 'our_legal_person',
    'our_legal_person varchar(64) NULL COMMENT ''我方法定代表人''');
CALL add_col_if_missing('crm_contract_config', 'our_agent',
    'our_agent varchar(64) NULL COMMENT ''我方授权代表''');
CALL add_col_if_missing('crm_contract_config', 'our_address',
    'our_address varchar(255) NULL COMMENT ''我方地址''');
CALL add_col_if_missing('crm_contract_config', 'our_bank_name',
    'our_bank_name varchar(128) NULL COMMENT ''我方开户银行''');
CALL add_col_if_missing('crm_contract_config', 'our_bank_account',
    'our_bank_account varchar(64) NULL COMMENT ''我方银行账号''');
CALL add_col_if_missing('crm_contract_config', 'our_tel',
    'our_tel varchar(32) NULL COMMENT ''我方电话''');
CALL add_col_if_missing('crm_contract_config', 'our_fax',
    'our_fax varchar(32) NULL COMMENT ''我方传真''');

-- 3.2 通用条款默认值
CALL add_col_if_missing('crm_contract_config', 'default_quality_standard',
    'default_quality_standard varchar(255) NULL COMMENT ''默认国家（行业）标准''');
CALL add_col_if_missing('crm_contract_config', 'default_sample_days',
    'default_sample_days int NULL COMMENT ''默认送样天数''');
CALL add_col_if_missing('crm_contract_config', 'default_delivery_days',
    'default_delivery_days int NULL COMMENT ''默认交货期限天数''');
CALL add_col_if_missing('crm_contract_config', 'default_delivery_arrive_days',
    'default_delivery_arrive_days int NULL COMMENT ''默认到货天数''');
CALL add_col_if_missing('crm_contract_config', 'default_install_days',
    'default_install_days int NULL COMMENT ''默认安装调试验收天数''');
CALL add_col_if_missing('crm_contract_config', 'default_first_accept_days',
    'default_first_accept_days int NULL COMMENT ''默认初步验收天数''');
CALL add_col_if_missing('crm_contract_config', 'default_trial_days',
    'default_trial_days int NULL COMMENT ''默认试用期天数''');
CALL add_col_if_missing('crm_contract_config', 'default_final_accept_days',
    'default_final_accept_days int NULL COMMENT ''默认最终验收天数''');
CALL add_col_if_missing('crm_contract_config', 'default_auto_accept_days',
    'default_auto_accept_days int NULL COMMENT ''默认视同验收合格天数''');
CALL add_col_if_missing('crm_contract_config', 'default_max_repair_times',
    'default_max_repair_times int NULL COMMENT ''默认维修次数上限''');
CALL add_col_if_missing('crm_contract_config', 'default_prepay_ratio',
    'default_prepay_ratio decimal(10,2) NULL COMMENT ''默认首付款比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'default_prepay_days',
    'default_prepay_days int NULL COMMENT ''默认首付支付天数''');
CALL add_col_if_missing('crm_contract_config', 'default_accept_pay_ratio',
    'default_accept_pay_ratio decimal(10,2) NULL COMMENT ''默认验收款比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'default_accept_pay_days',
    'default_accept_pay_days int NULL COMMENT ''默认验收款支付天数''');
CALL add_col_if_missing('crm_contract_config', 'default_deposit_ratio',
    'default_deposit_ratio decimal(10,2) NULL COMMENT ''默认保证金比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'default_deposit_months',
    'default_deposit_months int NULL COMMENT ''默认保证金月数''');
CALL add_col_if_missing('crm_contract_config', 'default_deposit_days',
    'default_deposit_days int NULL COMMENT ''默认保证金退还天数''');
CALL add_col_if_missing('crm_contract_config', 'default_warranty_years',
    'default_warranty_years int NULL COMMENT ''默认质保年数''');
CALL add_col_if_missing('crm_contract_config', 'default_response_hours',
    'default_response_hours int NULL COMMENT ''默认响应小时数''');
CALL add_col_if_missing('crm_contract_config', 'default_repair_hours',
    'default_repair_hours int NULL COMMENT ''默认维修小时数''');
CALL add_col_if_missing('crm_contract_config', 'default_repair_times',
    'default_repair_times int NULL COMMENT ''默认维修次数''');
CALL add_col_if_missing('crm_contract_config', 'default_report_repair_days',
    'default_report_repair_days int NULL COMMENT ''默认报修天数''');
CALL add_col_if_missing('crm_contract_config', 'default_contract_copies',
    'default_contract_copies int NULL COMMENT ''默认合同份数''');
CALL add_col_if_missing('crm_contract_config', 'default_gpc_name',
    'default_gpc_name varchar(128) NULL COMMENT ''默认政府采购中心名称''');

-- 3.3 违约责任默认值 —— 采购合同
CALL add_col_if_missing('crm_contract_config', 'p_buyer_reject_ratio',
    'p_buyer_reject_ratio decimal(10,2) NULL COMMENT ''甲方拒收货物违约金比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'p_buyer_overdue_wan',
    'p_buyer_overdue_wan decimal(10,2) NULL COMMENT ''甲方逾期付款违约金(万分之/天)''');
CALL add_col_if_missing('crm_contract_config', 'p_buyer_overdue_days',
    'p_buyer_overdue_days int NULL COMMENT ''甲方逾期付款可终止合同天数''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_quality',
    'p_seller_quality decimal(10,2) NULL COMMENT ''乙方质量不合格违约金比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_overdue_wan',
    'p_seller_overdue_wan decimal(10,2) NULL COMMENT ''乙方逾期交货违约金(万分之/天)''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_overdue_days',
    'p_seller_overdue_days int NULL COMMENT ''乙方逾期交货可终止合同天数''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_compensate',
    'p_seller_compensate decimal(10,2) NULL COMMENT ''乙方终止合同赔偿金比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_replace_days',
    'p_seller_replace_days int NULL COMMENT ''检测不合格无条件更换天数''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_terminate',
    'p_seller_terminate decimal(10,2) NULL COMMENT ''逾期不能更换赔偿金比例(%)''');
CALL add_col_if_missing('crm_contract_config', 'p_seller_defect',
    'p_seller_defect decimal(10,2) NULL COMMENT ''权利瑕疵违约金比例(%)''');

-- 3.4 违约责任默认值 —— 购销合同
CALL add_col_if_missing('crm_contract_config', 's_buyer_late_ratio',
    's_buyer_late_ratio decimal(10,2) NULL COMMENT ''甲方逾期付款滞纳金比例(%/日)''');
CALL add_col_if_missing('crm_contract_config', 's_buyer_breach_ratio',
    's_buyer_breach_ratio decimal(10,2) NULL COMMENT ''甲方违约金比例(%)''');
CALL add_col_if_missing('crm_contract_config', 's_seller_late_ratio',
    's_seller_late_ratio decimal(10,2) NULL COMMENT ''乙方逾期供货滞纳金比例(%/日)''');
CALL add_col_if_missing('crm_contract_config', 's_seller_breach_ratio',
    's_seller_breach_ratio decimal(10,2) NULL COMMENT ''乙方违约金比例(%)''');

-- ---------------------------------------------------------------------------
-- 4. 初始化配置默认值（仅在字段为空时写入，避免覆盖已维护的数据）
-- ---------------------------------------------------------------------------
-- 质量标准不设默认值：模板原文为「符合或优于国家（行业）____标准」，留空即「国家（行业）标准」，
-- 填「国家」反而变成「国家（行业）国家标准」。需要具体标准号时由用户在配置页填写。
UPDATE crm_contract_config SET default_sample_days       = 5           WHERE default_sample_days IS NULL;
UPDATE crm_contract_config SET default_delivery_days     = 30          WHERE default_delivery_days IS NULL;
UPDATE crm_contract_config SET default_delivery_arrive_days = 30       WHERE default_delivery_arrive_days IS NULL;
UPDATE crm_contract_config SET default_install_days      = 10          WHERE default_install_days IS NULL;
UPDATE crm_contract_config SET default_first_accept_days = 7           WHERE default_first_accept_days IS NULL;
UPDATE crm_contract_config SET default_trial_days        = 30          WHERE default_trial_days IS NULL;
UPDATE crm_contract_config SET default_final_accept_days = 7           WHERE default_final_accept_days IS NULL;
UPDATE crm_contract_config SET default_auto_accept_days  = 15          WHERE default_auto_accept_days IS NULL;
UPDATE crm_contract_config SET default_max_repair_times  = 3           WHERE default_max_repair_times IS NULL;
UPDATE crm_contract_config SET default_prepay_ratio      = 30.00       WHERE default_prepay_ratio IS NULL;
UPDATE crm_contract_config SET default_prepay_days       = 15          WHERE default_prepay_days IS NULL;
UPDATE crm_contract_config SET default_accept_pay_ratio  = 60.00       WHERE default_accept_pay_ratio IS NULL;
UPDATE crm_contract_config SET default_accept_pay_days   = 15          WHERE default_accept_pay_days IS NULL;
UPDATE crm_contract_config SET default_deposit_ratio     = 10.00       WHERE default_deposit_ratio IS NULL;
UPDATE crm_contract_config SET default_deposit_months    = 12          WHERE default_deposit_months IS NULL;
UPDATE crm_contract_config SET default_deposit_days      = 15          WHERE default_deposit_days IS NULL;
UPDATE crm_contract_config SET default_warranty_years    = 1           WHERE default_warranty_years IS NULL;
UPDATE crm_contract_config SET default_response_hours    = 24          WHERE default_response_hours IS NULL;
UPDATE crm_contract_config SET default_repair_hours      = 48          WHERE default_repair_hours IS NULL;
UPDATE crm_contract_config SET default_repair_times      = 3           WHERE default_repair_times IS NULL;
UPDATE crm_contract_config SET default_report_repair_days = 3          WHERE default_report_repair_days IS NULL;
UPDATE crm_contract_config SET default_contract_copies   = 4           WHERE default_contract_copies IS NULL;

-- 采购合同违约责任默认值
UPDATE crm_contract_config SET p_buyer_reject_ratio      = 5.00        WHERE p_buyer_reject_ratio IS NULL;
UPDATE crm_contract_config SET p_buyer_overdue_wan       = 5.00        WHERE p_buyer_overdue_wan IS NULL;
UPDATE crm_contract_config SET p_buyer_overdue_days      = 30          WHERE p_buyer_overdue_days IS NULL;
UPDATE crm_contract_config SET p_seller_quality          = 5.00        WHERE p_seller_quality IS NULL;
UPDATE crm_contract_config SET p_seller_overdue_wan      = 5.00        WHERE p_seller_overdue_wan IS NULL;
UPDATE crm_contract_config SET p_seller_overdue_days     = 30          WHERE p_seller_overdue_days IS NULL;
UPDATE crm_contract_config SET p_seller_compensate       = 10.00       WHERE p_seller_compensate IS NULL;
UPDATE crm_contract_config SET p_seller_replace_days     = 15          WHERE p_seller_replace_days IS NULL;
UPDATE crm_contract_config SET p_seller_terminate        = 10.00       WHERE p_seller_terminate IS NULL;
UPDATE crm_contract_config SET p_seller_defect           = 10.00       WHERE p_seller_defect IS NULL;

-- 购销合同违约责任默认值
UPDATE crm_contract_config SET s_buyer_late_ratio        = 0.50        WHERE s_buyer_late_ratio IS NULL;
UPDATE crm_contract_config SET s_buyer_breach_ratio      = 5.00        WHERE s_buyer_breach_ratio IS NULL;
UPDATE crm_contract_config SET s_seller_late_ratio       = 0.50        WHERE s_seller_late_ratio IS NULL;
UPDATE crm_contract_config SET s_seller_breach_ratio     = 5.00        WHERE s_seller_breach_ratio IS NULL;

-- ---------------------------------------------------------------------------
-- 5. 清理临时存储过程
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_col_if_missing`;

-- ---------------------------------------------------------------------------
-- 6. 校验：确认新增列已生效
-- ---------------------------------------------------------------------------
SELECT '=== 校验：crm_contract 新增列数 ===' AS section;
SELECT COUNT(*) AS contract_new_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contract';

SELECT '=== 校验：crm_contract_product 新增列 ===' AS section;
SELECT COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contract_product'
  AND COLUMN_NAME IN ('spec','unit','fund_source','accessories','delivery_period');
