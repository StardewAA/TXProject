-- =====================================================================
-- 平台审查中心菜单（电子资质域 60005xxx 段补充）
-- =====================================================================
-- 用途：企业录入证照/资质后自动触发的审查任务中心（共享界面：超管筛选+审查；租户只读自己的）。
-- 依赖：父目录 60005000「资质与证书管理」已存在（master-credential-sync.sql）。
-- 权限码：bidding:review:query（共享查看）/ approve（超管通过）/ reject（超管驳回）。
-- 授权指引（按业务决策执行）：
--   · 超管角色：60005600 + 60005601 + 60005602 全授；
--   · 租户角色：仅授 60005600（query，只读查看本企业任务与结果）。
-- 幂等：INSERT IGNORE，可重复执行。
--
-- 【为什么从 sql/tools/ 移到 sql/history/】菜单属于"新装库必须具备"的终态数据，必须并入一键包；
--   而打包与门禁都只扫 sql/ 根、sql/history/ 与 master-*.sql（sql/tools/ 是**工具脚本目录**，不参与打包）。
--   留在 tools/ 的后果：本机手工执行过、库里有，但**新装库直接没有这个菜单**（页面在菜单树上消失，
--   且 check-dict-permission.js 也读不到页权限，会报"菜单页权限未登记"）。
--   文件名含 2026-09-20 供打包器按序并入；三处一致（脚本/一键包/库）由 check-permission-coverage.js 与
--   check-master-mirror.js 共同守。
-- =====================================================================
INSERT IGNORE INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
  (60005600, '平台审查中心', 'bidding:review:query', 2, 16, 60005000, 'review-center', NULL, 'bidding/review-center/index', 'ReviewCenterIndex', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (60005601, '审查通过', 'bidding:review:approve', 3, 17, 60005600, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (60005602, '审查驳回', 'bidding:review:reject', 3, 18, 60005600, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
