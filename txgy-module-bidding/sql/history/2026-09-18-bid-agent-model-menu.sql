-- 标书智能体：补充「云端模型」管理页菜单（占号 6815）
-- 背景：BidAgentModelController（/bidding/bid-agent/model/*）与前端页面 bidding/bidAgent/model/index 已就绪，
--       但菜单段 6790 下未注册该页面入口；运行时回退提示也引用「智能体管理 → 云端模型」。
-- 权限码：复用 bidding:bid-agent:query（列表/详情）与 bidding:bid-agent:update（保存/自检），不新增按钮权限。
-- 说明：super_admin 走旁路；普通角色由管理员在角色界面勾选（与 2026-09-16 技能/MCP 菜单同口径）。

INSERT IGNORE INTO `system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
    (6815, '云端模型', 'bidding:bid-agent:query', 2, 5, 6790, 'model', 'ep:cpu',
     'bidding/bidAgent/model/index', 'BiddingBidModel', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
