-- WMS 入库、盘点审批及执行状态字段（可重复执行）
-- 仅增加字段，不迁移或改写历史库存数据。

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wms_receipt_order' AND column_name = 'approval_status'),
    'SELECT 1',
    'ALTER TABLE wms_receipt_order
        ADD COLUMN approval_status int NOT NULL DEFAULT -1 COMMENT ''审批状态：-1未提交，1审批中，2已通过，3已拒绝，4已取消'' AFTER status,
        ADD COLUMN process_instance_id varchar(64) NULL COMMENT ''BPM 流程实例编号'' AFTER approval_status,
        ADD COLUMN approval_time datetime NULL COMMENT ''审批完成时间'' AFTER process_instance_id,
        ADD COLUMN approval_reason varchar(512) NULL COMMENT ''审批结果原因'' AFTER approval_time,
        ADD COLUMN execution_status int NOT NULL DEFAULT 0 COMMENT ''执行状态：0未执行，1成功，2失败'' AFTER approval_reason,
        ADD COLUMN execution_time datetime NULL COMMENT ''执行完成时间'' AFTER execution_status,
        ADD COLUMN execution_error varchar(512) NULL COMMENT ''执行错误'' AFTER execution_time'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wms_check_order' AND column_name = 'approval_status'),
    'SELECT 1',
    'ALTER TABLE wms_check_order
        ADD COLUMN approval_status int NOT NULL DEFAULT -1 COMMENT ''审批状态：-1未提交，1审批中，2已通过，3已拒绝，4已取消'' AFTER status,
        ADD COLUMN process_instance_id varchar(64) NULL COMMENT ''BPM 流程实例编号'' AFTER approval_status,
        ADD COLUMN approval_time datetime NULL COMMENT ''审批完成时间'' AFTER process_instance_id,
        ADD COLUMN approval_reason varchar(512) NULL COMMENT ''审批结果原因'' AFTER approval_time,
        ADD COLUMN execution_status int NOT NULL DEFAULT 0 COMMENT ''执行状态：0未执行，1成功，2失败'' AFTER approval_reason,
        ADD COLUMN execution_time datetime NULL COMMENT ''执行完成时间'' AFTER execution_status,
        ADD COLUMN execution_error varchar(512) NULL COMMENT ''执行错误'' AFTER execution_time'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wms_shipment_order' AND column_name = 'execution_status'),
    'SELECT 1',
    'ALTER TABLE wms_shipment_order
        ADD COLUMN execution_status int NOT NULL DEFAULT 0 COMMENT ''执行状态：0未执行，1成功，2失败'' AFTER approval_reason,
        ADD COLUMN execution_time datetime NULL COMMENT ''执行完成时间'' AFTER execution_status,
        ADD COLUMN execution_error varchar(512) NULL COMMENT ''执行错误'' AFTER execution_time'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

