-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- =====================================================================
-- 2026-09-28 电子证照域三台账信息架构落地（菜单终态调整）
--
-- 设计依据：docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md
--   §1.2 判据一（主实体原则）：功能归属看它操作的**主实体**，与名字/接口前缀无关；
--   §1.2 判据三（频率原则）：配置类能力不占台账页，收进「设置」。
--
-- 本次三件事：
--   ① 「证件全生命周期」(60005400) 不再独立占页 → 并入②企业证件台账的「流程」页签；
--   ② 「安许风险联动」(60005500) 不再独立占页 → 企业侧并入①企业资质台账「安许风险与规则」页签、
--      员工侧并入③员工证件台账「安许配备自查」页签、项目关键岗位并入项目台账；
--   ③ 新增「设置」目录，收纳主体档案 / 所属企业 / 规则配置 / CA 签章台账。
--
-- 【为什么不删行、只改父子与类型】沿用仓库既有先例（2026-09-10-menu-retire-crawl-task-page.sql）：
--   权限点是靠 system_menu 行承载的，直接 DELETE 会连带回收 system_role_menu 授权，
--   使已授权角色**静默失去权限**（接口 403、菜单消失但不报错）。
--   因此把"页面(type=2)"降级为"按钮(type=3)"并挂到新的父页面下：
--   **id 不变 → system_role_menu 授权行继续有效**，侧边栏不再出现该页，权限点完整保留。
--
-- 幂等：目标态 UPDATE / INSERT IGNORE，可重复执行。
-- 执行库：ruoyi-vue-pro（system_menu / system_role_menu 同库）。
-- 前置：建议先备份 system_menu / system_role_menu。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第 1 步：「证件全生命周期」页面 → 证照台账(60005100)下的按钮
--   口径：生命周期记录按证照维度查询，入口挂在证照台账下最自然；
--   权限码 bidding:certificate:lifecycle:query 原样保留（后端 @PreAuthorize 未变）。
-- ---------------------------------------------------------------------
UPDATE system_menu
   SET name = '生命周期流程查询',
       type = 3,
       sort = 7,
       parent_id = 60005100,
       path = '',
       component = '',
       component_name = '',
       updater = '1',
       update_time = NOW()
 WHERE id = 60005400
   AND deleted = b'0';

-- 生命周期下的 4 个按钮：父级从 60005400 迁到 60005100（id 与权限码均不变）
UPDATE system_menu
   SET parent_id = 60005100, updater = '1', update_time = NOW()
 WHERE id IN (60005401, 60005402, 60005403, 60005404)
   AND deleted = b'0';

-- ---------------------------------------------------------------------
-- 第 2 步：「安许风险联动」页面 → 企业资质(60005200)下的按钮
--   企业侧（风险卡片 / 风险大盘 / 风险工单 / 规则配置）归企业资质台账；
--   员工侧与项目侧已分别由各自台账承载（见前端 safety-risk 页的区块开关）。
-- ---------------------------------------------------------------------
UPDATE system_menu
   SET name = '安许风险联动（企业侧）',
       type = 3,
       sort = 16,
       parent_id = 60005200,
       path = '',
       component = '',
       component_name = '',
       updater = '1',
       update_time = NOW()
 WHERE id = 60005500
   AND deleted = b'0';

-- 安许页下的按钮：父级迁到 60005200（id 与权限码不变）
--   60005501 风险重算/宽限、60005502 多岗兼任、60005503 人员风险标签、
--   60005504 项目关键岗位、60005505/506 规则配置、60005509~512 风险工单
-- 注：60005502/503 的实际入口现在员工证件台账、60005504 在项目台账，
--   但**权限点本身仍属该用户角色能力**，故只改父级、不改权限码，避免授权失效。
UPDATE system_menu
   SET parent_id = 60005200, updater = '1', update_time = NOW()
 WHERE id IN (60005501, 60005502, 60005503, 60005504, 60005505, 60005506,
              60005509, 60005510, 60005511, 60005512)
   AND deleted = b'0';

-- ---------------------------------------------------------------------
-- 第 3 步：新增「设置」目录 + 4 个配置类页面
-- 号段：60006000 起（60005xxx 已被电子证照域页面占用）。各子块：主体档案 600061xx、
--   所属企业 600062xx、CA 签章台账 600064xx、规则配置 600065xx（600063xx 已让给权限审计批次，见下）。
--   判据三（频率原则）：这四项都**不管理某个业务实体**，而是被三本台账共同消费，故进设置。
--   组件路径与前端文件一一对应（前端 pageMap = import.meta.glob('../views/**/*.vue')）：
--     bidding/subjectProfile/index        → views/bidding/subjectProfile/index.vue（既有页）
--     bidding/settings/company/index      → views/bidding/settings/company/index.vue（本次新增）
--     bidding/settings/rule-config/index  → views/bidding/settings/rule-config/index.vue（本次新增）
--     bidding/caSeal/index                → views/bidding/caSeal/index.vue（既有页）
--   path 段用 settings- 前缀，避免与既有路由冲突。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006000, '设置', '', 1, 1200, 0, 'cert-settings', 'ep:setting', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006100, '主体档案（企业自维护）', 'bidding:subject:query', 2, 1, 60006000, 'settings-subject', NULL, 'bidding/subjectProfile/index', 'SubjectProfileIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006200, '所属企业', 'bidding:subject-company:query', 2, 2, 60006000, 'settings-company', NULL, 'bidding/settings/company/index', 'SettingsCompanyIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- ⚠️ 号段让位（2026-09-28 冲突排查）：规则配置原计划用 60006300/60006301，但
--   sql/history/2026-09-29-bidding-missing-perm-menus.sql（权限审计补全，另一批次产出）
--   已把 60006300~60006303 登记为「主体认证查询/审核」「招标文件下载名单/记录」。
--   两脚本同段不同义 = 后执行者覆盖前者，菜单名与权限会**静默错位** ——
--   正是 2026-09-18-cert-menu-renumber.sql 记录过的那类事故（60002105 被改名导致爬虫域权限丢失）。
--   按仓库既有口径「先登记再占用」让出：本域规则配置改用空闲的 60006500 段。
(60006500, '规则配置', 'bidding:rule-config:query', 2, 3, 60006000, 'settings-rule-config', NULL, 'bidding/settings/rule-config/index', 'SettingsRuleConfigIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006400, 'CA 签章台账', 'bidding:ca:query', 2, 4, 60006000, 'settings-ca-seal', NULL, 'bidding/caSeal/index', 'SettingsCaSealIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 写权限点：主体档案维护 / 所属企业增删改 / 规则配置修改 / CA 验签
--   （读权限由上面的页面行承载；写权限为按钮，便于按"只读角色"单独授权）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006101, '主体档案维护', 'bidding:subject:update', 3, 1, 60006100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- ⚠️ 号段让位（2026-09-28 冲突排查）：所属企业的三个写按钮原计划用 60006201~6203，
--   但 600062xx 已被 2026-09-28 那批流程子域脚本占满（tender-doc 6201~6208 / deposit 6209~6211 /
--   contract 6212~6213 / objection 6214~6216 / report 6217~6218 / ca-seal 6219~6220 /
--   prequal-committee 6221 / process-track 6222）。
--   同段不同义会让后执行者覆盖前者（菜单名与权限静默错位），故按「先登记再占用」让到空闲的 600066xx。
(60006601, '新增所属企业', 'bidding:subject-company:create', 3, 1, 60006200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006602, '修改所属企业', 'bidding:subject-company:update', 3, 2, 60006200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006603, '删除所属企业', 'bidding:subject-company:delete', 3, 3, 60006200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006501, '规则配置修改 / 发布 / 回滚', 'bidding:rule-config:update', 3, 1, 60006500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006401, 'CA 验签', 'bidding:ca:verify', 3, 1, 60006400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 第 3b 步：补注册两个"前端已判权但从未登记"的权限点
--   来源：sql/tools/check-permission-coverage.js 报出「3 个权限码未在 system_menu 注册」。
--   经查后端 SafetyPermitRiskController 当前只有 query/assign/handle/alert 四个码：
--     · bidding:safety-risk:scan ← POST /bidding/safety-risk/scan（按启用规则重算风险）
--     · bidding:safety-risk:rule ← /bidding/risk-rule/{page,create,update,status,delete}（风险规则维护）
--   这两个端点**后端尚未实现**（前端按契约先行接入，未就绪时页面空态）。现在登记的理由：
--   不登记 → 角色授权界面里看不到这两项 → 后端上线后**永远授不出去**，按钮对普通角色恒不可见。
--   （同类缺口此前已在 2026-09-21-bidding-rule-config-menu.sql 踩过一次，处理口径一致。）
--   归属：两者都作用于**企业安许风险**，故挂企业资质页(60005200)下。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60005513, '风险重算扫描', 'bidding:safety-risk:scan', 3, 35, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005514, '风险规则维护', 'bidding:safety-risk:rule', 3, 36, 60005200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ② 角色授权（属业务决策，不并入一键包；打包器在此截断）
-- ---------------------------------------------------------------------
-- 第 4 步：设置区授权（与 master-credential-sync.sql 的授权指引同口径）
--   规则配置与所属企业原本挂在安许页下（60005500 的既有授权可继承），
--   但主体档案 / CA 台账此前在别处，需要补授权行，否则设置区对非超管不可见。
--   授予租户 1 的「超级管理员(role_id=1)」与「普通角色(role_id=2)」——与
--   2026-09-28-credential-menu-grant.sql 同一口径（电子证照域是租户自有能力）。
--   幂等：INSERT ... SELECT ... WHERE NOT EXISTS（本表无业务唯一键）。
-- ---------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.role_id, m.id, '1', NOW(), '1', NOW(), b'0', 0
FROM system_menu m
CROSS JOIN (SELECT 1 AS role_id UNION ALL SELECT 2) r
WHERE m.id IN (60006000, 60006100, 60006101, 60006200, 60006201, 60006202, 60006203,
               60006500, 60006501, 60006400, 60006401, 60005513, 60005514,
               60006601, 60006602, 60006603)
  AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = r.role_id AND x.menu_id = m.id AND x.deleted = b'0');

-- =====================================================================
-- 回滚（如需）：
--   UPDATE system_menu SET name='证件全生命周期', type=2, sort=4, parent_id=60005000,
--          path='cert-lifecycle', component='bidding/certificate/lifecycle/index',
--          component_name='CertificateLifecycleIndex' WHERE id=60005400;
--   UPDATE system_menu SET parent_id=60005400 WHERE id IN (60005401,60005402,60005403,60005404);
--   UPDATE system_menu SET name='安许风险联动', type=2, sort=5, parent_id=60005000,
--          path='safety-risk', component='bidding/safety-risk/index',
--          component_name='SafetyRiskIndex' WHERE id=60005500;
--   UPDATE system_menu SET parent_id=60005500 WHERE id IN (60005501,...,60005512);
--   DELETE FROM system_role_menu WHERE menu_id BETWEEN 60006000 AND 60006499;
--   DELETE FROM system_menu WHERE id BETWEEN 60006000 AND 60006499;
-- =====================================================================
