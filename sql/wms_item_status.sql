-- WMS 商品启用/停用状态：0 启用，1 停用
ALTER TABLE wms_item
    ADD COLUMN IF NOT EXISTS status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0启用，1停用' AFTER remark;
