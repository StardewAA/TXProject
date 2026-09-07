-- ----------------------------------------------------------------------------
-- 支付管理 - 新增"钱包流水"菜单
-- 位置：支付管理 -> 钱包管理（parent_id = 585）下，排序在"钱包余额"(591)之后
-- 权限：pay:wallet:query（与后端 PayWalletTransactionController 一致）
-- 幂等：组件路径已存在时不重复插入，可重复执行
-- ----------------------------------------------------------------------------

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`,
   `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '钱包流水', 'pay:wallet:query', 2, 2, 585, 'wallet-transaction', 'ep:money',
       'pay/wallet/transaction/index', 'WalletTransaction', 0, b'1', b'1',
       b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu`
  WHERE `component` = 'pay/wallet/transaction/index' AND `deleted` = b'0'
);
