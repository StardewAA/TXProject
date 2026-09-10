-- ----------------------------------------------------------------------------
-- 支付管理 - 新增"钱包流水"菜单（位于 支付管理 -> 钱包管理 下）
-- 权限：pay:wallet:query（与后端 PayWalletTransactionController 一致）
-- 兼容性：不同环境"钱包管理"目录的菜单 ID 可能不同（如 585 / 2551），
--        本脚本自动以"钱包余额"(component = pay/wallet/balance/index) 的父级为准
-- 幂等：组件路径已存在时不重复插入，可重复执行
-- ----------------------------------------------------------------------------

SET @wallet_parent_id = (
  SELECT parent_id FROM `system_menu`
  WHERE `component` = 'pay/wallet/balance/index' AND `deleted` = b'0'
  LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`,
   `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '钱包流水', 'pay:wallet:query', 2, 3, IFNULL(@wallet_parent_id, 585),
       'wallet-transaction', 'ep:money', 'pay/wallet/transaction/index',
       'WalletTransaction', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu`
  WHERE `component` = 'pay/wallet/transaction/index' AND `deleted` = b'0'
);
