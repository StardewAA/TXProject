-- 积分活动返库业务流水：部署 promotion 新版本之前执行；不修造历史活动或商品。
-- 同一租户的同一订单项只能返库一次，整单取消与售后共用此业务键。
CREATE TABLE IF NOT EXISTS `promotion_point_stock_return` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL,
    `order_item_id` BIGINT NOT NULL,
    `activity_id` BIGINT NOT NULL,
    `sku_id` BIGINT NOT NULL,
    `return_count` INT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_order_item` (`tenant_id`, `order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分活动按订单项幂等返库流水';

-- 不回填无法确认的历史返库事实；有旧数据异常的订单必须先对账。
-- 回滚应用时保留本表和流水，禁止删除流水以免已返库的订单被重复返还。
