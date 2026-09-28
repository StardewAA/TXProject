-- =============================================================================
-- 修复「登录后过一段时间自动退出（闪退）」：延长访问令牌有效期
--
-- 【问题现象】
--   用户登录后约 30 分钟被踢回登录页，需要重新登录，体验为「网站自己退出了」。
--
-- 【根因】
--   token 的有效期由 OAuth2 客户端配置决定（不在 application.yaml）：
--     system_oauth2_client.access_token_validity_seconds
--   项目默认给的是 1800 秒 = 30 分钟。
--   访问令牌过期后，前端会走 /system/auth/refresh-token 用刷新令牌换新令牌；
--   该链路存在时序竞争（多个并发请求同时过期时，部分请求会先拿到 401 并触发
--   doReAuthenticate 清空会话、跳转登录页），于是表现为「忽然闪退」。
--
-- 【修复】
--   把访问令牌有效期从 30 分钟延长到 7 天；刷新令牌保持 30 天。
--   这样即使某个请求刷新失败，也不会立刻把用户踢出去。
--
-- 【为什么改数据库而不是 yaml】
--   token 有效期只存在 system_oauth2_client 表，且被 OAuth2ClientService
--   缓存到 Redis（key: oauth_client:{clientId}），所以改库后必须清缓存才生效。
--
-- 【幂等】可重复执行。
-- 【执行】
--   mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
--   redis-cli --scan --pattern 'oauth_client*' | xargs -r redis-cli del
-- =============================================================================

SELECT '--- 修改前 ---' AS s;
SELECT id, client_id, access_token_validity_seconds,
       ROUND(access_token_validity_seconds / 60, 1) AS access_minutes,
       refresh_token_validity_seconds,
       ROUND(refresh_token_validity_seconds / 86400, 1) AS refresh_days
FROM system_oauth2_client WHERE deleted = b'0';

-- 访问令牌 30 分钟 → 7 天；刷新令牌统一到 30 天
UPDATE system_oauth2_client
SET access_token_validity_seconds = 604800,      -- 7 天
    refresh_token_validity_seconds = 2592000,    -- 30 天
    updater = '1',
    update_time = NOW()
WHERE client_id = 'default' AND deleted = b'0';

-- 其余内部客户端（test / SSO 演示）同步放宽，避免它们登录后也闪退
UPDATE system_oauth2_client
SET access_token_validity_seconds = 604800,
    refresh_token_validity_seconds = 2592000,
    updater = '1',
    update_time = NOW()
WHERE client_id IN ('test', 'txgy-sso-demo-by-code', 'txgy-sso-demo-by-password')
  AND deleted = b'0';

SELECT '--- 修改后（access 应为 7 天 / refresh 30 天）---' AS s;
SELECT id, client_id, access_token_validity_seconds,
       ROUND(access_token_validity_seconds / 86400, 1) AS access_days,
       refresh_token_validity_seconds,
       ROUND(refresh_token_validity_seconds / 86400, 1) AS refresh_days
FROM system_oauth2_client WHERE deleted = b'0';

-- ⚠️ 提醒：执行后必须清理 Redis 缓存，否则仍按旧值签发
-- redis-cli --scan --pattern 'oauth_client*' | xargs -r redis-cli del
