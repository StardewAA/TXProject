-- =====================================================================
-- 电子证照域菜单号段迁移：60002000/600021xx/600022xx/600023xx/600024xx → 60004000 段
-- =====================================================================
-- 依据：2026-09-13 两域菜单号段冲突核查结论——
--   1) 号段 60002000/60002100~60002106 的**原始归属**是外部公告采集域
--      （sql/history/2026-09-08-notice-external-menu.sql）；
--   2) 电子证照域脚本（2026-09-08-certificate-menu.sql、2026-09-12-certificate-file-menu.sql）
--      复用了同号段，导致 60002105 被改名成"文件版本登记"，
--      爬虫域"采集任务人工重试（bidding:crawl-task:update）"权限点在库里丢失名称/语义；
--   3) 处理原则（业务口径）：**证照域整体换号段，不删除爬虫域旧行**——
--      爬虫域 60002110 采集源管理页在 67xx 段没有对应物，删旧行会丢入口。
--
-- 目标号段：60004000 段（经全仓 sql/ 与源码检索确认 60003xxx 已被主体域占用、
--   60004xxx/60005xxx 完全空闲），保持"目录 + 页面 + 按钮"三层结构与原 sort 不变。
--
-- 执行库：**system**。幂等：全部 ON DUPLICATE KEY UPDATE，可重复执行。
-- 前置：建议先备份 system_menu / system_role_menu（见文末回滚段说明）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第 1 步：恢复爬虫域原始行（只 UPDATE 名称/权限/父级/排序，不删行、不动 60002110）
--        原值逐字取自 sql/history/2026-09-08-notice-external-menu.sql
-- ---------------------------------------------------------------------
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60002000, '外部公告采集管理', '', 1, 2000, 0, 'crawl-external', 'ep:monitor', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002100, '采集源查看', 'bidding:crawl-source:query', 3, 1, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002101, '采集源新建', 'bidding:crawl-source:create', 3, 2, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002102, '采集源修改/启停', 'bidding:crawl-source:update', 3, 3, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002103, '采集源删除', 'bidding:crawl-source:delete', 3, 4, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002104, '采集任务查看', 'bidding:crawl-task:query', 3, 5, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002105, '采集任务人工重试', 'bidding:crawl-task:update', 3, 6, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002106, '采集批次查看', 'bidding:crawl-job:query', 3, 7, 60002000, NULL, NULL, NULL, NULL, 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `type` = VALUES(`type`), `sort` = VALUES(`sort`), `deleted` = b'0';

-- ---------------------------------------------------------------------
-- 第 2 步：证照域迁入新号段（60004000 目录 + 四个页面 + 按钮，权限码/路径/组件逐字保留）
--        映射关系：60002000→60004000、600021xx→600041xx、600022xx→600042xx、
--                 600023xx→600043xx、600024xx→600044xx
-- ---------------------------------------------------------------------
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 目录
(60004000, '资质与证书管理', '', 1, 1100, 0, 'certificate', 'ep:medal', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 证照台账
(60004100, '证照台账', 'bidding:certificate:query', 2, 1, 60004000, 'list', NULL, 'bidding/certificate/index', 'CertificateIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004101, '证照新建', 'bidding:certificate:create', 3, 2, 60004100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004102, '证照修改/状态流转/文件版本', 'bidding:certificate:update', 3, 3, 60004100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004103, '证照验真', 'bidding:certificate:verify', 3, 4, 60004100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004104, '预警扫描/派发', 'bidding:certificate:alert-run', 3, 5, 60004100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004105, '文件版本登记', 'bidding:certificate:file', 3, 6, 60004100, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 企业资质
(60004200, '企业资质', 'bidding:qualification:query', 2, 6, 60004000, 'qualification', NULL, 'bidding/qualification/index', 'QualificationIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004201, '资质建档', 'bidding:qualification:create', 3, 7, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004202, '延续申报', 'bidding:qualification:renew', 3, 8, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004203, '延续审批通过', 'bidding:qualification:approve', 3, 9, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004204, '延续审批退回', 'bidding:qualification:reject', 3, 10, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004210, '资质标准登记', 'bidding:qualification:standard', 3, 11, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004211, '要素台账录入', 'bidding:qualification:element', 3, 12, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004212, '达标核查执行', 'bidding:qualification:review', 3, 13, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004213, '安许证管理', 'bidding:qualification:permit', 3, 14, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004214, '资质到期预警扫描', 'bidding:qualification:alert-run', 3, 15, 60004200, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 员工资质（父节点随目录迁移）
(60004300, '员工资质', 'bidding:personnel:profile:query', 2, 11, 60004000, 'personnel', 'ep:user', 'bidding/personnel/index', 'PersonnelIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004301, '员工建档（一人一档）', 'bidding:personnel:profile:create', 3, 12, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004302, '档案修改/在职状态流转', 'bidding:personnel:profile:update', 3, 13, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004303, '员工证书登记', 'bidding:personnel:cert:create', 3, 14, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004304, '证书修改/状态流转（吊销注销恢复）', 'bidding:personnel:cert:update', 3, 15, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004305, '员工证书删除', 'bidding:personnel:cert:delete', 3, 16, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004306, '证书检索/到期清单/细分统计', 'bidding:personnel:cert:query', 3, 17, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004307, '履历登记（培训/社保/劳动合同）', 'bidding:personnel:record:create', 3, 18, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004308, '履历查询（培训/社保/劳动合同）', 'bidding:personnel:record:query', 3, 19, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004309, '员工证件到期预警扫描', 'bidding:personnel:alert-run', 3, 20, 60004300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 证件全生命周期
(60004400, '证件全生命周期', 'bidding:certificate:lifecycle:query', 2, 21, 60004000, 'cert-lifecycle', 'ep:refresh', 'bidding/certificate/lifecycle/index', 'CertLifecycleIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004401, '生命周期发起（变更/续期/注销吊销/年检）', 'bidding:certificate:lifecycle:apply', 3, 22, 60004400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004402, '生命周期审核与生效（回写台账）', 'bidding:certificate:lifecycle:audit', 3, 23, 60004400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004403, '企业材料完备性自查（身份/资质/实力）', 'bidding:qualification:material:query', 3, 24, 60004400, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
-- 安许风险联动（v1.1 新增页面 + 本次补齐的权限点）
(60004500, '安许风险联动', 'bidding:qualification:permit:query', 2, 25, 60004000, 'safety-risk', 'ep:warning', 'bidding/safety-risk/index', 'SafetyRiskIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004501, '风险重算 / 宽限登记', 'bidding:qualification:permit:update', 3, 26, 60004500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004502, '多岗兼任合规校验', 'bidding:personnel:cert:query', 3, 27, 60004500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004503, '人员风险标签（一年异常）', 'bidding:personnel:profile:query', 3, 28, 60004500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60004504, '项目关键岗位变更监控', 'bidding:project:query', 3, 29, 60004500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `type` = VALUES(`type`), `sort` = VALUES(`sort`), `deleted` = b'0';

-- ---------------------------------------------------------------------
-- 第 3 步：角色授权随迁（role_menu）——只搬"证照域独占"的旧 id，爬虫域共用 id 的授权一律保留
--   3.1 把旧证照独占 id 的授权复制到新 id（角色原有的可见性不丢）
--   3.2 再删除旧证照独占 id 的授权行
--   共用 id（60002000/60002100~60002104/60002106）**不删**：它们恢复为爬虫域行，授权归爬虫域继续使用
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `role_id`, `menu_id` + 2000, `creator`, NOW(), `updater`, NOW(), b'0', `tenant_id`
FROM `system_role_menu` WHERE `menu_id` IN (60002200,60002201,60002202,60002203,60002204,60002210,60002211,60002212,60002213,60002214,
  60002300,60002301,60002302,60002303,60002304,60002305,60002306,60002307,60002308,60002309,
  60002400,60002401,60002402,60002403);

INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT `role_id`, 60004100 + (`menu_id` - 60002100), `creator`, NOW(), `updater`, NOW(), b'0', `tenant_id`
FROM `system_role_menu` WHERE `menu_id` IN (60002100,60002101,60002102,60002103,60002104);
-- 说明：60002100~60002104 的授权行是"两域共用同一 id"留下的，**无法从数据上区分归属**，
--      因此采取"复制到新 id、旧行保留给爬虫域"的策略：证照页不丢可见性，代价是爬虫域角色可能多出
--      几个证照按钮授权。多出的授权由管理员按需回收（见文末校验 SQL 第 3 条）。

DELETE FROM `system_role_menu` WHERE `menu_id` IN (60002200,60002201,60002202,60002203,60002204,60002210,60002211,60002212,60002213,60002214,
  60002300,60002301,60002302,60002303,60002304,60002305,60002306,60002307,60002308,60002309,
  60002400,60002401,60002402,60002403);

-- ---------------------------------------------------------------------
-- 第 4 步：删除旧"证照独占"菜单行（60002105 属爬虫域，**不删**；60002000/60002100~60002104/60002106 属爬虫域，不删）
--   注意：60002105 上的"文件版本登记"已被第 1 步恢复为爬虫域"采集任务人工重试"，
--        其权限点 bidding:certificate:file 已由新行 60004105 承载。
-- ---------------------------------------------------------------------
DELETE FROM `system_menu` WHERE `id` IN (60002200,60002201,60002202,60002203,60002204,60002210,60002211,60002212,60002213,60002214,
  60002300,60002301,60002302,60002303,60002304,60002305,60002306,60002307,60002308,60002309,
  60002400,60002401,60002402,60002403);

-- ---------------------------------------------------------------------
-- 第 5 步：安许风险联动新页面的授权（600045xx）——**不自动授权**，按需执行
--   原因：新页面给谁看属于业务授权决策，脚本不应替管理员决定；超级管理员（role_id=1）默认放行全部权限，
--        非超管角色需显式授权。下面给出"与安许证管理同权限范围的角色"批量授权示例（把 600045xx 给
--        已拥有 60004213 安许证管理的角色），需要时取消注释执行。
-- ---------------------------------------------------------------------
-- INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
-- SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
-- FROM `system_role_menu` rm JOIN `system_menu` m ON m.`id` BETWEEN 60004500 AND 60004504
-- WHERE rm.`menu_id` = 60004213 AND rm.`deleted` = b'0';

-- ---------------------------------------------------------------------
-- 校验（执行后应返回 0 行）
-- ---------------------------------------------------------------------
-- 1) 证照域权限点是否都已迁到新号段且唯一
-- SELECT permission, COUNT(*) FROM system_menu WHERE permission LIKE 'bidding:certificate%' OR permission LIKE 'bidding:personnel%'
--   GROUP BY permission HAVING COUNT(*) > 1;
-- 2) 爬虫域权限点是否仍存在（应返回 7 行）
-- SELECT id, name, permission FROM system_menu WHERE id IN (60002000,60002100,60002101,60002102,60002103,60002104,60002105,60002106);
-- 3) 新号段授权是否就位
-- SELECT COUNT(*) FROM system_role_menu WHERE menu_id BETWEEN 60004000 AND 60004599;

-- =====================================================================
-- 回滚（如需恢复迁移前状态，按以下顺序反向执行；执行前请确认没有新数据引用新号段）
-- =====================================================================
-- R1) 授权回迁：
-- INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
--   SELECT role_id, menu_id - 2000, creator, NOW(), updater, NOW(), b'0', tenant_id FROM system_role_menu
--   WHERE menu_id IN (60004200,60004201,60004202,60004203,60004204,60004210,60004211,60004212,60004213,60004214,
--     60004300,60004301,60004302,60004303,60004304,60004305,60004306,60004307,60004308,60004309,
--     60004400,60004401,60004402,60004403);
-- R2) 菜单行回迁：按第 2 步 VALUES 把 60004xxx 逐行改回 6000 2xxx（parent_id 60004000→60002000）
-- R3) 删除新号段行：DELETE FROM system_menu WHERE id BETWEEN 60004000 AND 60004599;
--     DELETE FROM system_role_menu WHERE menu_id BETWEEN 60004000 AND 60004599;
-- R4) 爬虫域行无需回滚（第 1 步就是把它们恢复成原始值）。

-- =====================================================================
-- 重要：以下历史脚本的号段**已废弃**，禁止再执行（再执行会把爬虫域行再次改名）
--   sql/history/2026-09-08-certificate-menu.sql        （60002000 / 600021xx / 600022xx）
--   sql/history/2026-09-12-certificate-file-menu.sql   （60002105）
--   sql/history/2026-09-11-qualification-ops.sql       （6000221x）
--   sql/history/2026-09-15-personnel-lifecycle-menu.sql（600023xx / 600024xx）
-- =====================================================================
