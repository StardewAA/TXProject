-- ---------------------------------------------------------------------
-- 待办「质保即将到期」实时查询 —— 索引优化
--
-- 背景：该待办不落库、不预生成，用户进入待办页面时由后端实时查询判定，条件为：
--   status = 1（质保中）
--   AND service_user_id IN （当前登录人本人 + 其下属）
--   AND warranty_end_date BETWEEN 今天 AND 今天 + 售后设置「质保到期提前提醒天数」
--
-- 说明：
--   1. 建表脚本中已有 KEY `idx_status_end` (`status`, `warranty_end_date`)，
--      可覆盖「状态 + 到期日区间」的组合查询；
--   2. 本次按需求补充「质保到期日期」单列索引，便于按到期日单独扫描/排序的场景。
-- ---------------------------------------------------------------------

ALTER TABLE `crm_after_sale_warranty`
  ADD INDEX `idx_warranty_end_date` (`warranty_end_date`) USING BTREE;

-- 如后续该待办查询数据量较大、需要进一步优化，可考虑改为复合索引（按实际慢查询验证后执行）：
-- ALTER TABLE `crm_after_sale_warranty`
--   ADD INDEX `idx_status_service_end` (`status`, `service_user_id`, `warranty_end_date`) USING BTREE;
