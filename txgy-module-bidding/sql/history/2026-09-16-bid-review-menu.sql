-- =====================================================================
-- 智慧标书审查：管理端菜单 + 权限点（含个人端自助权限点）
-- 方案依据：docs/模块设计与规范/智慧标书审查-设计方案.md §5（接口清单）/ §7.3（菜单与权限 SQL）
-- 规范依据：《招投标模块开发规范》§4.4（权限注解 @PreAuthorize + bidding:xxx:yyy）
-- 设计说明：
--   1) 管理端菜单挂在既有「招投标」目录（id=6735）下，新目录 id 段 60007000 起
--      （60002xxx/60005000 段已被证照/资质占用，避免复用造成"同名不同物"）；
--   2) 个人端（门户）页面是硬编码路由（router/routes/core.ts 的 PortalBidding*），
--      不建菜单，只在这里登记权限点，供角色授权后由 usePortalCan 控制按钮；
--   3) 幂等：INSERT IGNORE，可重复执行；
--   4) 超级管理员默认拥有全部菜单，普通角色需另行授权 system_role_menu（属业务决策，本脚本不代授）。
-- 执行库：ruoyi-vue-pro
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 页面：智慧标书审查（直接挂在「招投标」6735 下，**一级页面**，点一下就进）
-- 说明：原先做成"二级目录 + 子页面"，在「招投标」展开后的长列表里既排在中段、又要再点一层，
--   用户反馈"管理端看不到"（2026-09-16），故拍平为直接可点的页面菜单，URL = /bidding/bid-review。
(60007000, '智慧标书审查', 'bidding:bid-review:query', 2, 40, 6735, 'bid-review', 'ep:document-checked', 'bidding/bidReview/task/index', 'BidReviewTaskIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 按钮权限（挂在页面菜单下；60007100 保留为"查询"权限位，避免与历史行冲突）
(60007100, '审查任务查询', 'bidding:bid-review:query', 3, 1, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007101, '一键生成/重跑', 'bidding:bid-review:generate', 3, 2, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007102, '任务取消/重试', 'bidding:bid-review:update', 3, 3, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007103, '结果复核', 'bidding:bid-review:audit', 3, 4, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 个人端（门户）自助权限点：无菜单，仅授权位（type=3）
(60007400, '个人端-上传并一键生成', 'bidding:bid-review:self:create', 3, 9, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007401, '个人端-查看我的审查记录', 'bidding:bid-review:self:query', 3, 10, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60007402, '个人端-删除我的记录', 'bidding:bid-review:self:delete', 3, 11, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 说明：文件上传复用既有 POST /bidding/file/upload，其权限点为 bidding:file:upload，
--      若个人端角色未授予该权限，可将 60007400 的权限码改为 bidding:file:upload 另建一条：
-- INSERT IGNORE INTO `system_menu` (...) VALUES (60007403, '个人端-上传文件', 'bidding:file:upload', 3, 12, 60007000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 结构校正（幂等）：已按旧结构（二级目录 + 子页面）建过菜单的环境，执行以下语句即可拍平。
-- 新环境直接跑上面的 INSERT 即为终态，本段不会产生额外影响。
-- ---------------------------------------------------------------------
-- ---------------------------------------------------------------------
-- 授权：把本功能的菜单与权限点授予【所有角色】（用户 2026-09-16 明确要求"给所有角色授权"）。
-- 说明：
--   1) 会连租户角色一起授权 —— 即租户用户也能在管理后台看到「智慧标书审查」；
--      只想给特定角色时，把 WHERE 改成 r.code IN ('common') 之类即可。
--   2) 幂等：system_role_menu 没有 (role_id, menu_id) 唯一键，故用 NOT EXISTS 防重复插入。
--   3) 回滚（如需）：
--      DELETE rm FROM `system_role_menu` rm JOIN `system_menu` m ON m.id = rm.menu_id
--      WHERE m.id BETWEEN 60007000 AND 60007999;
-- ---------------------------------------------------------------------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM `system_role` r
JOIN `system_menu` m ON m.id IN (60007000, 60007100, 60007101, 60007102, 60007103, 60007400, 60007401, 60007402)
WHERE r.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0');

UPDATE `system_menu`
SET `name` = '智慧标书审查', `permission` = 'bidding:bid-review:query', `type` = 2,
    `parent_id` = 6735, `path` = 'bid-review', `component` = 'bidding/bidReview/task/index',
    `component_name` = 'BidReviewTaskIndex', `sort` = 40, `status` = 0, `visible` = b'1', `deleted` = b'0'
WHERE `id` = 60007000;

UPDATE `system_menu`
SET `name` = '审查任务查询', `permission` = 'bidding:bid-review:query', `type` = 3,
    `parent_id` = 60007000, `path` = NULL, `component` = NULL, `component_name` = NULL
WHERE `id` = 60007100;

UPDATE `system_menu` SET `parent_id` = 60007000 WHERE `id` IN (60007101, 60007102, 60007103);

-- ---------------------------------------------------------------------
-- P1 预留菜单（规则库页 / 报告与统计页）：**对应前端页面落地后再执行本段**，
-- 现在不插入是为了避免出现"点进去空白"的死菜单（管理端任务页已内置 AI 配置自检与报告查看）。
-- ---------------------------------------------------------------------
-- INSERT IGNORE INTO `system_menu`
-- (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- (60007200, '审查规则库', 'bidding:bid-review:rule:query', 2, 5, 60007000, 'rule', NULL, 'bidding/bidReview/rule/index', 'BidReviewRuleIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- (60007201, '规则维护', 'bidding:bid-review:rule:update', 3, 6, 60007200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- (60007300, '审查报告与统计', 'bidding:bid-review:query', 2, 7, 60007000, 'report', NULL, 'bidding/bidReview/report/index', 'BidReviewReportIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- (60007301, '报告导出', 'bidding:bid-review:generate', 3, 8, 60007300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- (60007302, 'AI 配置维护', 'bidding:bid-review:config:update', 3, 9, 60007300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
