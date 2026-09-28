-- ---------------------------------------------------------------------
-- 待办「工单超时预警」实时查询 —— 索引优化
--
-- 背景：该待办不落库、不预生成，用户进入待办页面时由后端实时查询判定，条件为：
--   status IN (1=待处理, 2=处理中)      -- 未闭环
--   AND assignee_user_id IN （当前登录人本人 + 其下属）
--   AND create_time <= 今天 - 售后设置「工单超时预警天数」的日终
--
-- 说明：建表脚本中已有 KEY `idx_status_assignee` (`status`, `assignee_user_id`)，
--       可覆盖「状态 + 处理人」组合；本次按需求为「工单创建日期」补充单列索引，
--       用于按创建时间过滤/排序（积压时长判断）。
-- ---------------------------------------------------------------------

ALTER TABLE `crm_after_sale_ticket`
  ADD INDEX `idx_create_time` (`create_time`) USING BTREE;

-- 如后续该待办查询数据量较大、需要进一步优化，可考虑改为复合索引（按实际慢查询验证后执行）：
-- ALTER TABLE `crm_after_sale_ticket`
--   ADD INDEX `idx_status_assignee_create` (`status`, `assignee_user_id`, `create_time`) USING BTREE;
