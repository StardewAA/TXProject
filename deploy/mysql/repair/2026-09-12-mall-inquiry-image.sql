-- 执行前检查字段并备份product_purchase_inquiry_item，本脚本执行一次。
ALTER TABLE product_purchase_inquiry_item ADD COLUMN pic_url varchar(512) NULL COMMENT '询价物品参考图片地址';
