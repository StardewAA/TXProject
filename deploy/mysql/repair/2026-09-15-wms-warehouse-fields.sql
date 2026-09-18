-- WMS 仓库字段补齐。执行前先通过 information_schema 确认这些列尚不存在。
ALTER TABLE `wms_warehouse`
    ADD COLUMN `warehouse_type` varchar(32) NOT NULL DEFAULT 'RAW_MATERIAL' COMMENT '原料仓/成品仓' AFTER `name`,
    ADD COLUMN `organization_name` varchar(100) NULL COMMENT '所属组织/部门' AFTER `warehouse_type`,
    ADD COLUMN `city` varchar(50) NULL COMMENT '所在城市' AFTER `organization_name`,
    ADD COLUMN `address` varchar(255) NULL COMMENT '仓库地址' AFTER `city`,
    ADD COLUMN `principal` varchar(64) NULL COMMENT '负责人' AFTER `address`,
    ADD COLUMN `principal_phone` varchar(20) NULL COMMENT '负责人联系电话' AFTER `principal`,
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '启用状态' AFTER `principal`,
    ADD COLUMN `data_version` bigint NOT NULL DEFAULT 1 COMMENT '主数据版本' AFTER `status`;

UPDATE `wms_warehouse`
SET `warehouse_type` = 'RAW_MATERIAL'
WHERE `warehouse_type` IS NULL OR `warehouse_type` = '';

UPDATE `wms_warehouse`
SET `status` = 0
WHERE `status` IS NULL;
