-- ============================================================================
-- 修正 OAuth2 令牌的「租户 0」脏数据
-- 日期：2026-09-17
--
-- 现象：
--   前端使用过程中会「突然自动退出」，频率约等于访问令牌有效期（默认 30 分钟）。
--
-- 根因：
--   刷新令牌（system_oauth2_refresh_token）插入时未写入 tenant_id，落库为 0；
--   refreshAccessToken 直接沿用该 0 创建新的访问令牌（system_oauth2_access_token），
--   于是刷新后的令牌挂在「租户 0」上；而前端后续所有请求都携带 tenant-id 请求头，
--   后端按 (access_token, tenant_id) 查不到令牌 → 401 → 前端跳登录页。
--
-- 代码侧修复：
--   OAuth2TokenServiceImpl#createOAuth2RefreshToken  显式写入当前租户；
--   OAuth2TokenServiceImpl#createOAuth2AccessToken  把 tenant_id=0 视为「未记录租户」，
--     兜底为「令牌所属用户的实际租户 → 当前上下文租户」。
--
-- 本脚本：
--   把已存在的、tenant_id=0 的存量令牌，按令牌所属用户的实际租户纠正回来，
--   使当前已登录的会话立即恢复可用。
--
-- 幂等：仅更新 tenant_id=0 且对应用户租户非 0 的记录，可重复执行。
-- ============================================================================

USE `ruoyi-vue-pro`;

-- ---------------------------------------------------------------------------
-- 1. 刷新令牌
-- ---------------------------------------------------------------------------
UPDATE system_oauth2_refresh_token t
    JOIN system_users u ON u.id = t.user_id AND u.tenant_id <> 0
SET t.tenant_id = u.tenant_id
WHERE t.tenant_id = 0
  AND t.deleted = b'0';

-- ---------------------------------------------------------------------------
-- 2. 访问令牌
-- ---------------------------------------------------------------------------
UPDATE system_oauth2_access_token t
    JOIN system_users u ON u.id = t.user_id AND u.tenant_id <> 0
SET t.tenant_id = u.tenant_id
WHERE t.tenant_id = 0
  AND t.deleted = b'0';

-- ---------------------------------------------------------------------------
-- 3. 校验：不应再有「租户 0」的令牌
-- ---------------------------------------------------------------------------
SELECT '=== 校验：tenant_id=0 的令牌数（应为 0） ===' AS section;
SELECT (SELECT COUNT(*) FROM system_oauth2_refresh_token WHERE tenant_id = 0 AND deleted = b'0') AS refresh_token_tenant0,
       (SELECT COUNT(*) FROM system_oauth2_access_token  WHERE tenant_id = 0 AND deleted = b'0') AS access_token_tenant0;

SELECT '=== 校验：各租户令牌分布 ===' AS section;
SELECT tenant_id, COUNT(*) AS 刷新令牌数
FROM system_oauth2_refresh_token
WHERE deleted = b'0'
GROUP BY tenant_id
ORDER BY tenant_id;
