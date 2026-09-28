-- ============================================================================
-- CRM 商机状态：修正无法解析的创建人/更新人
-- 日期：2026-09-17
--
-- 背景：
--   crm_business_status_type / crm_business_status 的历史脚本
--   （2026-09-15-crm-business-status*.sql）写入的 creator/updater = '1'，
--   但本项目多租户下，租户 2012 的用户 id 从 8xxxx 起，并不存在 id=1 的用户。
--   「系统配置 → 商机状态配置」分页会按 creator 回填用户昵称，
--   取不到时抛 NPE 导致整页 500：
--     java.lang.NullPointerException: ... AdminUserRespDTO.getNickname() ...
--
-- 处理：
--   1. 代码侧已加判空（CrmBusinessStatusController#getBusinessStatusPage），
--      即使解析不到也不会再 500；
--   2. 本脚本把 creator/updater 指向该租户下的真实用户（取最小用户 id，
--      即该租户的管理员账号），让页面能显示创建人，而不是空白。
--
-- 幂等：只更新「creator 在当前租户下查不到对应用户」的行，可重复执行。
-- ============================================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 商机状态组
-- ---------------------------------------------------------------------------
UPDATE crm_business_status_type t
SET t.creator = (SELECT MIN(u.id) FROM system_users u WHERE u.tenant_id = t.tenant_id),
    t.updater = (SELECT MIN(u.id) FROM system_users u WHERE u.tenant_id = t.tenant_id)
WHERE t.deleted = b'0'
  AND NOT EXISTS (SELECT 1
                  FROM system_users u
                  WHERE u.tenant_id = t.tenant_id
                    AND u.id = CAST(t.creator AS UNSIGNED)
                    AND u.deleted = b'0');

-- ---------------------------------------------------------------------------
-- 2. 商机阶段
-- ---------------------------------------------------------------------------
UPDATE crm_business_status b
SET b.creator = (SELECT MIN(u.id) FROM system_users u WHERE u.tenant_id = b.tenant_id),
    b.updater = (SELECT MIN(u.id) FROM system_users u WHERE u.tenant_id = b.tenant_id)
WHERE b.deleted = b'0'
  AND NOT EXISTS (SELECT 1
                  FROM system_users u
                  WHERE u.tenant_id = b.tenant_id
                    AND u.id = CAST(b.creator AS UNSIGNED)
                    AND u.deleted = b'0');

-- ---------------------------------------------------------------------------
-- 3. 校验：不应再存在「创建人查不到」的行
-- ---------------------------------------------------------------------------
SELECT '=== 校验：仍无法解析创建人的行数（应为 0） ===' AS section;
SELECT (SELECT COUNT(*)
        FROM crm_business_status_type t
        WHERE t.deleted = b'0'
          AND NOT EXISTS (SELECT 1 FROM system_users u
                          WHERE u.tenant_id = t.tenant_id AND u.id = CAST(t.creator AS UNSIGNED) AND u.deleted = b'0'))
           AS status_type_bad,
       (SELECT COUNT(*)
        FROM crm_business_status b
        WHERE b.deleted = b'0'
          AND NOT EXISTS (SELECT 1 FROM system_users u
                          WHERE u.tenant_id = b.tenant_id AND u.id = CAST(b.creator AS UNSIGNED) AND u.deleted = b'0'))
           AS status_bad;

SELECT tenant_id, creator, updater, COUNT(*) AS 行数
FROM crm_business_status
WHERE deleted = b'0'
GROUP BY tenant_id, creator, updater;
