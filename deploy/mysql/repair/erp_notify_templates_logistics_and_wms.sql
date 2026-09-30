-- ERP 右上角铃铛（站内信）通知模板补齐
--
-- 背景：
--  1) ErpLogisticsServiceImpl 已经在发 7 类物流通知，但 system_notify_template 里
--     一个都没有 → NotifySendServiceImpl 取不到模板会抛 NOTICE_NOT_FOUND，
--     又发生在 afterCommit 里，用户完全无感知（表现为「物流一条通知都收不到」）。
--  2) WMS 推送是提交后异步执行，失败只把 wms_status 回写成 FAILED 并写日志，
--     用户不主动回列表看就不知道单据没进 WMS，这里补一个失败通知模板。
--  3) 5 个审批模板共用一个 nickname「ERP 审批通知」，铃铛里标题一模一样、分不出轻重，
--     这里按场景拆开，并刷新历史消息里已快照的 template_nickname。
--
-- 可重复执行；模板是系统级配置，消息数据仍按 tenant_id 隔离。

-- ============================ 1) 物流通知（7 个） ============================
INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流任务已创建', 'erp_logistics_task_created', 'ERP 物流通知',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）已创建，可在物流任务中查看轨迹。', 2,
       '["logisticsTaskNo","sourceOrderNo","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_task_created' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流预计到达变更', 'erp_logistics_eta_updated', 'ERP 物流通知',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）预计到达时间已变更为 {estimatedArrivalTime}。', 2,
       '["logisticsTaskNo","sourceOrderNo","estimatedArrivalTime","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_eta_updated' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流已发运', 'erp_logistics_departed', 'ERP 物流通知',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）已发运，请关注在途轨迹。', 2,
       '["logisticsTaskNo","sourceOrderNo","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_departed' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流异常', 'erp_logistics_exception', 'ERP 物流异常',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）发生运输异常，请及时处理。', 2,
       '["logisticsTaskNo","sourceOrderNo","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_exception' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流已签收', 'erp_logistics_received', 'ERP 物流签收',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）已签收。', 2,
       '["logisticsTaskNo","sourceOrderNo","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_received' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流签收异常', 'erp_logistics_exception_receipt', 'ERP 物流异常',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）签收结果异常，请核实处理。', 2,
       '["logisticsTaskNo","sourceOrderNo","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_exception_receipt' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 物流异常已处理', 'erp_logistics_exception_resolved', 'ERP 物流异常',
       '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）的运输异常已处理完毕。', 2,
       '["logisticsTaskNo","sourceOrderNo","detailPath"]', 0,
       '收件人=来源单据负责人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_logistics_exception_resolved' AND deleted = b'0');

-- ==================== 2) WMS 推送失败通知（1 个） ====================
INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP WMS 推送失败', 'erp_wms_push_failed', 'ERP 推送失败',
       '{orderTypeName} {orderNo} 推送 WMS 失败：{reason}。请到单据列表重新发起推送。', 2,
       '["orderTypeName","orderNo","reason","detailPath"]', 0,
       '收件人=单据创建人；reason 超长已在代码侧截断到 100 字以适配 255 字段', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_wms_push_failed' AND deleted = b'0');

-- ==================== 3) 模板文案升级（重复部署也生效） ====================
UPDATE system_notify_template SET
    content = CASE code
        WHEN 'erp_logistics_task_created' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）已创建，可在物流任务中查看轨迹。'
        WHEN 'erp_logistics_eta_updated' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）预计到达时间已变更为 {estimatedArrivalTime}。'
        WHEN 'erp_logistics_departed' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）已发运，请关注在途轨迹。'
        WHEN 'erp_logistics_exception' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）发生运输异常，请及时处理。'
        WHEN 'erp_logistics_received' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）已签收。'
        WHEN 'erp_logistics_exception_receipt' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）签收结果异常，请核实处理。'
        WHEN 'erp_logistics_exception_resolved' THEN '物流任务 {logisticsTaskNo}（来源单号 {sourceOrderNo}）的运输异常已处理完毕。'
        WHEN 'erp_wms_push_failed' THEN '{orderTypeName} {orderNo} 推送 WMS 失败：{reason}。请到单据列表重新发起推送。'
    END,
    update_time = NOW()
WHERE code IN ('erp_logistics_task_created', 'erp_logistics_eta_updated', 'erp_logistics_departed',
               'erp_logistics_exception', 'erp_logistics_received', 'erp_logistics_exception_receipt',
               'erp_logistics_exception_resolved', 'erp_wms_push_failed')
  AND deleted = b'0';

-- ============ 4) 铃铛标题分场景（原来 5 个审批模板共用「ERP 审批通知」）============
UPDATE system_notify_template SET
    nickname = CASE code
        WHEN 'erp_order_submitted' THEN 'ERP 提交成功'
        WHEN 'erp_approval_task_assigned' THEN 'ERP 待我审批'
        WHEN 'erp_approval_task_completed' THEN 'ERP 审批回执'
        WHEN 'erp_approval_finished' THEN 'ERP 审批通过'
        WHEN 'erp_approval_rejected' THEN 'ERP 审批驳回'
    END,
    update_time = NOW()
WHERE code IN ('erp_order_submitted', 'erp_approval_task_assigned', 'erp_approval_task_completed',
               'erp_approval_finished', 'erp_approval_rejected')
  AND deleted = b'0';

-- system_notify_message.template_nickname 是发送时的快照，改模板不会影响已发出的消息，
-- 这里把历史消息一并刷新，否则铃铛里新旧消息标题会不一致。
UPDATE system_notify_message SET
    template_nickname = CASE template_code
        WHEN 'erp_order_submitted' THEN 'ERP 提交成功'
        WHEN 'erp_approval_task_assigned' THEN 'ERP 待我审批'
        WHEN 'erp_approval_task_completed' THEN 'ERP 审批回执'
        WHEN 'erp_approval_finished' THEN 'ERP 审批通过'
        WHEN 'erp_approval_rejected' THEN 'ERP 审批驳回'
    END
WHERE template_code IN ('erp_order_submitted', 'erp_approval_task_assigned', 'erp_approval_task_completed',
                        'erp_approval_finished', 'erp_approval_rejected')
  AND deleted = b'0';
