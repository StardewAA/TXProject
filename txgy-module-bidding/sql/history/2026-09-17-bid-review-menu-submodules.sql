-- =====================================================================
-- 智慧标书审查（管理端）：三级菜单定稿（用户 2026-09-17）
--  招投标(6735) → 智慧标书审查(60007000 目录) → 审查任务 / 审查规则库 / 报告与统计
-- 说明：此前管理端是「单页 + 页内左侧栏」；用户要求改成真正的三级菜单，
--   因此把 60007000 降级为目录、三个子页面分别指向独立面板组件：
--     bidding/bidReview/task/TaskPanel   （原任务看板，已从 index.vue 抽出）
--     bidding/bidReview/rule/RulePanel   （规则库）
--     bidding/bidReview/report/ReportPanel（报告与统计）
-- 幂等：UPDATE + INSERT ... ON DUPLICATE KEY UPDATE + NOT EXISTS 授权，可重复执行。
-- 执行库：ruoyi-vue-pro
-- =====================================================================
SET NAMES utf8mb4;

-- 1) 父目录：智慧标书审查
UPDATE `system_menu`
SET `name` = '智慧标书审查', `type` = 1, `parent_id` = 6735, `path` = 'bid-review',
    `component` = NULL, `component_name` = NULL, `permission` = '', `icon` = 'ep:document-checked',
    `sort` = 40, `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1', `deleted` = b'0'
WHERE `id` = 60007000;

-- 2) 子页面一：审查任务
UPDATE `system_menu`
SET `name` = '审查任务', `type` = 2, `parent_id` = 60007000, `path` = 'task',
    `component` = 'bidding/bidReview/task/TaskPanel', `component_name` = 'BidReviewTaskPanel',
    `permission` = 'bidding:bid-review:query', `sort` = 1, `status` = 0, `visible` = b'1', `deleted` = b'0'
WHERE `id` = 60007100;
-- 任务相关按钮挂到「审查任务」下
UPDATE `system_menu` SET `parent_id` = 60007100 WHERE `id` IN (60007101, 60007102, 60007103);

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 3) 子页面二：审查规则库（+ 维护按钮）
(60007200, '审查规则库', 'bidding:bid-review:rule:query', 2, 2, 60007000, 'rule', NULL,
 'bidding/bidReview/rule/RulePanel', 'BidReviewRulePanel', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007201, '规则维护', 'bidding:bid-review:rule:update', 3, 1, 60007200, NULL, NULL, NULL, NULL,
 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 4) 子页面三：报告与统计（+ 报告下载 / AI 配置维护按钮）
(60007300, '报告与统计', 'bidding:bid-review:query', 2, 3, 60007000, 'report', NULL,
 'bidding/bidReview/report/ReportPanel', 'BidReviewReportPanel', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007301, '报告下载', 'bidding:bid-review:query', 3, 1, 60007300, NULL, NULL, NULL, NULL,
 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007302, 'AI 配置维护', 'bidding:bid-review:config:update', 3, 2, 60007300, NULL, NULL, NULL, NULL,
 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 5) 建单权限点（管理端建任务；此前只在代码里，未做成权限点）
(60007104, '建单', 'bidding:bid-review:create', 3, 5, 60007100, NULL, NULL, NULL, NULL,
 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`),
    `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
    `component` = VALUES(`component`), `component_name` = VALUES(`component_name`), `deleted` = b'0';

-- 6) 授权：以上菜单与权限点授给**所有角色**（沿用 2026-09-16「给所有角色授权」口径）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (60007000, 60007100, 60007101, 60007102, 60007103, 60007104,
                                 60007200, 60007201, 60007300, 60007301, 60007302,
                                 60007400, 60007401, 60007402)
WHERE r.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0');
