-- =============================================================================
-- 售后质保状态自动流转：配套数据
--   1) 新增「质保已到期」站内信模板（状态流转为「已到期终止」时发送）
--   2) 回访任务状态字典补全：补「待处理」、新增「已取消」
--      （「已取消」用于质保到期后自动取消「到期日之后」的未完成回访任务）
--
-- 【配套代码】
--   CrmAfterSaleWarrantyServiceImpl#autoUpdateWarrantyStatus  定时流转（每日 02:00）
--   CrmAfterSaleWarrantyServiceImpl#syncWarrantyStatus        手动立即刷新
--   CrmAfterSaleWarrantyServiceImpl#updateWarrantyStatus      手动修正单条状态
--
-- 【特性】幂等，可重复执行。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
--   ⚠️ 执行后请清除字典缓存：redis-cli --scan --pattern '*dict*' | xargs -r redis-cli del
-- =============================================================================

-- ============ 1. 「质保已到期」站内信模板 ============
INSERT INTO system_notify_template (name, code, nickname, content, type, params, status, remark,
                                    creator, create_time, updater, update_time, deleted)
SELECT '质保已到期通知', 'crm_after_sale_warranty_expired', '售后系统',
       '您好，客户【{customerName}】的项目【{projectName}】质保已于 {endDate} 到期，系统已自动停用关联回访计划并取消到期后的未完成回访任务，请及时安排到期回访与后续续保/转有偿跟进。',
       2, '["customerName","projectName","endDate"]', 1,
       'CRM 售后-质保状态自动流转为「已到期终止」时的通知（每日 02:00 定时任务或手动刷新触发）',
       '1', NOW(), '1', NOW(), b'0'
FROM (SELECT 1 AS x) t
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template x
                  WHERE x.code = 'crm_after_sale_warranty_expired' AND x.deleted = b'0');

-- ============ 2. 回访任务状态字典补全 ============
-- 2.1 补「待处理」(1)：原字典只配了「已完成 / 已逾期」，导致列表里「待处理」显示为空
UPDATE system_dict_data
SET label = '待处理', sort = 1, status = 0, color_type = 'processing',
    remark = '回访任务待处理', updater = '1', update_time = NOW()
WHERE dict_type = 'crm_after_sale_visit_task_status' AND value = '1';

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark,
                              creator, create_time, updater, update_time, deleted)
SELECT * FROM (
    SELECT 1 AS sort, '待处理' AS label, '1' AS value, 'crm_after_sale_visit_task_status' AS dict_type,
           0 AS status, 'processing' AS color_type, '' AS css_class, '回访任务待处理' AS remark,
           '1' AS creator, NOW() AS create_time, '1' AS updater, NOW() AS update_time, b'0' AS deleted
) d
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data x
                  WHERE x.dict_type = 'crm_after_sale_visit_task_status' AND x.value = '1' AND x.deleted = b'0');

-- 2.2 新增「已取消」(4)：质保到期后自动取消的「到期日之后」未完成回访任务
INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark,
                              creator, create_time, updater, update_time, deleted)
SELECT * FROM (
    SELECT 4 AS sort, '已取消' AS label, '4' AS value, 'crm_after_sale_visit_task_status' AS dict_type,
           0 AS status, 'default' AS color_type, '' AS css_class,
           '质保到期后自动取消的到期后回访任务' AS remark,
           '1' AS creator, NOW() AS create_time, '1' AS updater, NOW() AS update_time, b'0' AS deleted
) d
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data x
                  WHERE x.dict_type = 'crm_after_sale_visit_task_status' AND x.value = '4' AND x.deleted = b'0');

-- ============ 3. 自查 ============
SELECT '--- 质保到期通知模板 ---' AS s;
SELECT id, name, code, type, status FROM system_notify_template
WHERE code = 'crm_after_sale_warranty_expired' AND deleted = b'0';

SELECT '--- 回访任务状态字典（应为 4 项）---' AS s;
SELECT id, sort, label, value, color_type FROM system_dict_data
WHERE dict_type = 'crm_after_sale_visit_task_status' AND deleted = b'0' ORDER BY sort;
