-- =============================================================================
-- 售后问题工单「已超时」状态：配套字典数据
--   1) 补「待处理(1)」：原字典缺失该项，列表/筛选中「待处理」会显示为空
--   2) 新增「已超时(5)」：由系统超时检查任务自动标记（每日 02:30 + 读取侧懒同步兜底），
--      标记后出现在 CRM 待办事项的「工单超时预警」中，直到完成或关闭
--
-- 【配套代码】
--   CrmAfterSaleTicketStatusEnum.OVERDUE(5, "已超时")
--   CrmAfterSaleTicketServiceImpl#autoMarkOverdueTickets  扫描标记（定时 + 懒同步共用）
--   CrmAfterSaleScheduledJob#markOverdueTickets           每日 02:30
--   CrmAfterSaleJobHandler#ticketOverdueMarkJob           XXL-Job 版本
--
-- 【幂等】可重复执行。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
--   ⚠️ 执行后请清除字典缓存：redis-cli --scan --pattern '*dict*' | xargs -r redis-cli del
-- =============================================================================

-- ============ 1. 补「待处理(1)」+ 更新排序/颜色 ============
UPDATE system_dict_data
SET label = '待处理', sort = 1, status = 0, color_type = 'warning',
    remark = '工单待处理', updater = '1', update_time = NOW()
WHERE dict_type = 'crm_after_sale_ticket_status' AND value = '1' AND deleted = b'0';

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark,
                              creator, create_time, updater, update_time, deleted)
SELECT * FROM (
    SELECT 1 AS sort, '待处理' AS label, '1' AS value, 'crm_after_sale_ticket_status' AS dict_type,
           0 AS status, 'warning' AS color_type, '' AS css_class, '工单待处理' AS remark,
           '1' AS creator, NOW() AS create_time, '1' AS updater, NOW() AS update_time, b'0' AS deleted
) d
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data x
                  WHERE x.dict_type = 'crm_after_sale_ticket_status' AND x.value = '1' AND x.deleted = b'0');

-- ============ 2. 新增「已超时(5)」+ 更新排序/颜色 ============
UPDATE system_dict_data
SET label = '已超时', sort = 5, status = 0, color_type = 'error',
    remark = '系统超时检查任务自动标记；完成或关闭后退出超时预警', updater = '1', update_time = NOW()
WHERE dict_type = 'crm_after_sale_ticket_status' AND value = '5' AND deleted = b'0';

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark,
                              creator, create_time, updater, update_time, deleted)
SELECT * FROM (
    SELECT 5 AS sort, '已超时' AS label, '5' AS value, 'crm_after_sale_ticket_status' AS dict_type,
           0 AS status, 'error' AS color_type, '' AS css_class,
           '系统超时检查任务自动标记；完成或关闭后退出超时预警' AS remark,
           '1' AS creator, NOW() AS create_time, '1' AS updater, NOW() AS update_time, b'0' AS deleted
) d
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data x
                  WHERE x.dict_type = 'crm_after_sale_ticket_status' AND x.value = '5' AND x.deleted = b'0');

-- ============ 2.5 工单流转记录表：操作人允许为空 ============
-- 系统自动标记「已超时」时没有操作人，而 operator_user_id 原为 NOT NULL 且无默认值，
-- 会导致留痕插入失败（status 更新成功但留痕丢失）。改为可空，幂等可重复执行。
ALTER TABLE crm_after_sale_ticket_note
  MODIFY COLUMN operator_user_id bigint NULL COMMENT '操作人编号（系统自动操作时为空）';

-- ============ 3. 自查（应为 5 项：1待处理/2处理中/3已完成/4已关闭/5已超时）============
SELECT '--- 工单状态字典（应为 5 项）---' AS s;
SELECT id, sort, label, value, color_type, status FROM system_dict_data
WHERE dict_type = 'crm_after_sale_ticket_status' AND deleted = b'0' ORDER BY sort;
