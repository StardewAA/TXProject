-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-10-02 环境同步（**通用版：一律按业务键定位，不写死菜单 id**）
--
-- 用途：把本轮在本地库做的数据库变更同步到任意环境（dev / 同事本地库 / 新库）。
-- 为什么必须有这一份：history 下既有脚本多按**各自环境的菜单 id 号段**写
--   （见 2026-09-24-remove-notice-and-subject-auth-local-ids.sql 注释：
--    「本地库菜单 id 与 dev 号段错位…严禁在本库执行上述 dev 脚本」），
--    跨环境直接执行会错挂父菜单甚至误删。本脚本一律按 permission / component /
--    业务编号定位，可在任何环境安全重复执行（幂等）。
-- 对应前端提交：14da0445f（bidding: 门户/审查修复——授权台账归位审查中心、来源筛选、唱标明细、userId 映射）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ① 菜单名乱码修复
--    bidding:notice-external:query 的 name 曾因 SQL 连接字符集事故写成
--    å¤–éƒ¨å…¬å‘Šå…³æ³¨æŸ¥è¯¢（UTF-8 字节被 CP1252 误解码后再按 UTF-8 存回的双重编码）
--    正确名：外部公告关注查询。全库 1530 条菜单仅此 1 条乱码（已逐条 HEX 抽验）。
-- ---------------------------------------------------------------------------
UPDATE system_menu
   SET name = '外部公告关注查询', updater = '1', update_time = NOW()
 WHERE permission = 'bidding:notice-external:query' AND deleted = b'0';

-- ---------------------------------------------------------------------------
-- ② 平台授权权限点归位：bidding:subject-auth:{query,update}
--    从「主体档案（企业自维护）」菜单移到「平台审查中心」菜单下。
--    依据（用户口径 2026-10-02）：授权/审查是**平台侧**动作，主体档案页是企业自维护，
--    平台台账不应出现在企业页；前端授权 UI 已迁入 review-center（component=bidding/review-center/index）。
--    权限码本身不变，故角色勾选不受影响，仅菜单树归属变化。
-- ---------------------------------------------------------------------------
UPDATE system_menu m
  JOIN system_menu p
    ON p.component = 'bidding/review-center/index' AND p.type = 2 AND p.deleted = b'0'
   SET m.parent_id = p.id, m.updater = '1', m.update_time = NOW()
 WHERE m.permission IN ('bidding:subject-auth:query', 'bidding:subject-auth:update')
   AND m.deleted = b'0';

-- ---------------------------------------------------------------------------
-- ③ 关注列表行修复（业务数据）
--    现象：门户「投标流程管理」看不到**已关注**的公告（用户报障）。
--    根因：关注列表行被批量软删（本库为 2026-09-25 12:42:47 updater='cleanup' 的一次操作），
--          而订阅表仍是 FOLLOWING；且后端 FollowServiceImpl.follow() 幂等分支
--          「订阅已 FOLLOWING 就直接 return」，不重建被软删的关注行 → 重新点关注也恢复不了。
--    契约：见 docs/电子证照前前端契约/契约跟进-关注列表与主体授权-问题核对清单-2026-10-02.md §1
-- ---------------------------------------------------------------------------
UPDATE bidding_follow_item i
  JOIN bidding_notice_subscription s
    ON i.user_id = s.user_id
   AND i.follow_type = s.target_type
   AND i.follow_key = s.target_key
   AND s.deleted = b'0'
   SET i.deleted = b'0',
       i.follow_status = 'FOLLOWING',
       i.unfollow_time = NULL,
       i.updater = 'env-sync-2026-10-02'
 WHERE CAST(i.deleted AS UNSIGNED) = 1
   AND s.follow_status = 'FOLLOWING';

-- ③-补：恢复行的标题/发布单位快照（否则门户列表只显示一串编号）
UPDATE bidding_follow_item i
  JOIN (SELECT project_code, MIN(title) AS t, MIN(issuer_name) AS iss
          FROM bidding_notice_external WHERE deleted = b'0' GROUP BY project_code) e
    ON e.project_code = i.follow_key
   SET i.display_name = e.t, i.issuer_name = e.iss, i.updater = 'env-sync-2026-10-02'
 WHERE i.follow_type = 'PROJECT'
   AND CAST(i.deleted AS UNSIGNED) = 0
   AND i.display_name = i.follow_key;

-- ---------------------------------------------------------------------------
-- ④ 主体授权（幂等示例）
--    资格门 SubjectEligibilityServiceImpl#assertBidderEligible 按**租户**校验
--    bidding_subject_auth；无记录 → 1060106031「主体未经平台授权或授权已停用」，
--    process-track 域（含 /my、/create）全部被拦（门户建档失败）。
--    正常路径：管理端「资质与证书管理 → 平台审查中心 → 主体授权」点「授权企业主体」
--    （前端已接线，POST /bidding/subject-auth/authorize），**不必手写 SQL**。
--    本段仅为无界面环境提供幂等兜底；按需修改 tenant_id / tenant_name。
-- ---------------------------------------------------------------------------
INSERT INTO bidding_subject_auth (id, tenant_id, tenant_name, subject_type, auth_status,
                                  authorize_user_id, authorize_time,
                                  creator, create_time, updater, update_time, deleted)
SELECT 2103789086966005762, 1, '西安鸥鹏互联科技有限公司', 'BIDDER', 'ENABLED',
       1, NOW(), '1', NOW(), '1', NOW(), b'0'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM bidding_subject_auth
                    WHERE tenant_id = 1 AND subject_type = 'BIDDER' AND deleted = b'0');

-- ============================================================================
-- 校验（逐条看输出是否符合期望）
-- ============================================================================
-- 期望①：三条权限记录存在，且 subject-auth 两条 parent 指向「平台审查中心」
SELECT m.id, m.name, m.permission, m.type, m.parent_id, p.name AS parent_name
  FROM system_menu m LEFT JOIN system_menu p ON p.id = m.parent_id
 WHERE m.permission IN ('bidding:subject-auth:query', 'bidding:subject-auth:update', 'bidding:notice-external:query')
   AND m.deleted = b'0';

-- 期望②：0 行（无 mojibake 残留）
SELECT COUNT(*) AS mojibake_left FROM system_menu WHERE name REGEXP '[à-ÿ]' AND deleted = b'0';

-- 期望③：可见关注行数 ≥ FOLLOWING 订阅数（差额为跨类型/已取关，不应再出现"订阅在、行被删"）
SELECT (SELECT COUNT(*) FROM bidding_follow_item
         WHERE CAST(deleted AS UNSIGNED) = 0 AND follow_status = 'FOLLOWING') AS visible_items,
       (SELECT COUNT(*) FROM bidding_notice_subscription
         WHERE deleted = b'0' AND follow_status = 'FOLLOWING') AS active_subscriptions;

-- 期望④：≥1（存在已授权投标主体）
SELECT COUNT(*) AS bidder_auth_enabled FROM bidding_subject_auth
 WHERE subject_type = 'BIDDER' AND auth_status = 'ENABLED' AND deleted = b'0';
