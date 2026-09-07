-- ERP 审批管理：右上角铃铛站内信模板
-- 可重复执行；模板为系统级配置，消息数据仍由 tenant_id 自动隔离。

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 订单提交成功', 'erp_order_submitted', 'ERP 审批通知',
       '{orderTypeName} {orderNo} 已发布，等待订单管理员审核并发布。', 2,
       '["orderTypeName","orderNo","orderType","orderId","detailPath"]', 0,
       '订单发起人提交成功回执', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_order_submitted' AND deleted = b'0');

-- 已存在模板时同步最新文案，保证重复部署也能完成升级。
UPDATE system_notify_template
SET content = '{orderTypeName} {orderNo} 已发布，等待订单管理员审核并发布。',
    update_time = NOW()
WHERE code = 'erp_order_submitted' AND deleted = b'0';

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 新订单待审批', 'erp_approval_task_assigned', 'ERP 审批通知',
       '您有一张新的{orderTypeName}待审批：{orderNo}，当前节点：{taskName}，发起人：{startUserNickname}。', 2,
       '["orderTypeName","orderNo","orderType","taskName","startUserNickname","detailPath"]', 0,
       '仅发送给 Flowable 当前任务实际审批人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_approval_task_assigned' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 审批操作回执', 'erp_approval_task_completed', 'ERP 审批通知',
       '您已审批{orderTypeName} {orderNo}，节点：{taskName}，审批结果：{result}。', 2,
       '["orderTypeName","orderNo","orderType","taskName","result","detailPath"]', 0,
       '发送给本次实际执行审批的账号', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_approval_task_completed' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 订单审批完成', 'erp_approval_finished', 'ERP 审批通知',
       '{orderTypeName} {orderNo} 已完成全部审批。', 2,
       '["orderTypeName","orderNo","orderType","detailPath"]', 0,
       '全部节点通过后发送给订单发起人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_approval_finished' AND deleted = b'0');

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'ERP 订单审批驳回', 'erp_approval_rejected', 'ERP 审批通知',
       '{orderTypeName} {orderNo} 已被驳回，原因：{reason}。', 2,
       '["orderTypeName","orderNo","orderType","reason","detailPath"]', 0,
       '流程驳回后发送给订单发起人', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code = 'erp_approval_rejected' AND deleted = b'0');

UPDATE system_notify_template SET
    name = CASE code
        WHEN 'erp_order_submitted' THEN 'ERP 订单提交成功'
        WHEN 'erp_approval_task_assigned' THEN 'ERP 新订单待审批'
        WHEN 'erp_approval_task_completed' THEN 'ERP 审批操作回执'
        WHEN 'erp_approval_finished' THEN 'ERP 订单审批完成'
        WHEN 'erp_approval_rejected' THEN 'ERP 订单审批驳回'
    END,
    nickname = 'ERP 审批通知',
    content = CASE code
        WHEN 'erp_order_submitted' THEN '{orderTypeName} {orderNo} 已发布，等待订单管理员审核并发布。'
        WHEN 'erp_approval_task_assigned' THEN '您有一张新的{orderTypeName}待审批：{orderNo}，当前节点：{taskName}，发起人：{startUserNickname}。'
        WHEN 'erp_approval_task_completed' THEN '您已审批{orderTypeName} {orderNo}，节点：{taskName}，审批结果：{result}。'
        WHEN 'erp_approval_finished' THEN '{orderTypeName} {orderNo} 已完成全部审批。'
        WHEN 'erp_approval_rejected' THEN '{orderTypeName} {orderNo} 已被驳回，原因：{reason}。'
    END,
    params = CASE code
        WHEN 'erp_order_submitted' THEN '["orderTypeName","orderNo","orderType","orderId","detailPath"]'
        WHEN 'erp_approval_task_assigned' THEN '["orderTypeName","orderNo","orderType","taskName","startUserNickname","detailPath"]'
        WHEN 'erp_approval_task_completed' THEN '["orderTypeName","orderNo","orderType","taskName","result","detailPath"]'
        WHEN 'erp_approval_finished' THEN '["orderTypeName","orderNo","orderType","detailPath"]'
        WHEN 'erp_approval_rejected' THEN '["orderTypeName","orderNo","orderType","reason","detailPath"]'
    END,
    update_time = NOW()
WHERE code IN ('erp_order_submitted', 'erp_approval_task_assigned', 'erp_approval_task_completed',
               'erp_approval_finished', 'erp_approval_rejected')
  AND deleted = b'0';
