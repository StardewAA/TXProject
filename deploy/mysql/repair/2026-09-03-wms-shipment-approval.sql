-- WMS 出库审批接入。
-- 流程模型需要在「工作流程 -> 流程模型」中创建并部署，模型标识固定为：wms_shipment_approval

ALTER TABLE `wms_shipment_order`
    ADD COLUMN `approval_status` int NOT NULL DEFAULT -1 COMMENT '审批状态：-1未提交，1审批中，2已通过，3已拒绝，4已取消' AFTER `status`,
    ADD COLUMN `process_instance_id` varchar(64) NULL COMMENT 'BPM 流程实例编号' AFTER `approval_status`,
    ADD COLUMN `approval_time` datetime NULL COMMENT '审批完成时间' AFTER `process_instance_id`,
    ADD COLUMN `approval_reason` varchar(512) NULL COMMENT '审批结果原因' AFTER `approval_time`;

-- 出库单菜单编号在不同初始化库中可能不同，因此按权限定位父菜单。
INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
                           `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`,
                           `create_time`, `updater`, `update_time`, `deleted`)
SELECT '出库单提交审批', 'wms:shipment-order:submit-approval', 3, 8, id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM `system_menu` parent
WHERE parent.id = (
      SELECT sibling.parent_id
      FROM `system_menu` sibling
      WHERE sibling.permission = 'wms:shipment-order:query'
        AND sibling.deleted = b'0'
      LIMIT 1
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu` child
      WHERE child.permission = 'wms:shipment-order:submit-approval'
        AND child.deleted = b'0'
  )
LIMIT 1;
