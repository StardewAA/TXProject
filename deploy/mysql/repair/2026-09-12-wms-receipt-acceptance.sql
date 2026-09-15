-- Run once after receipt source and approval migrations. Historical rows remain unknown and must be reviewed.
ALTER TABLE `wms_receipt_order` ADD COLUMN `simulation` boolean DEFAULT FALSE;
ALTER TABLE `wms_receipt_order` ADD COLUMN `notification_status` varchar(32);
ALTER TABLE `wms_receipt_order` ADD COLUMN `notification_error` varchar(1000);
ALTER TABLE `wms_receipt_order` ADD COLUMN `notification_attempts` int DEFAULT 0;
ALTER TABLE `wms_receipt_order` ADD COLUMN `next_notification_time` datetime;
ALTER TABLE `wms_receipt_order_detail` ADD COLUMN `actual_quantity` decimal(20,2);
ALTER TABLE `wms_receipt_order_detail` ADD COLUMN `qualified_quantity` decimal(20,2);
ALTER TABLE `wms_receipt_order_detail` ADD COLUMN `unqualified_quantity` decimal(20,2);
ALTER TABLE `wms_receipt_order_detail` ADD COLUMN `quality_images` varchar(8000);
ALTER TABLE `wms_receipt_order_detail` ADD COLUMN `quality_remark` varchar(1000);
ALTER TABLE `wms_receipt_order_detail` ADD COLUMN `quality_required_snapshot` boolean;
