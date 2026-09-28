-- ============================================================================
-- 修复：MES 生产助手角色未公开，导致 AI 面板报「聊天角色不存在」
-- 日期：2026-09-21
-- 幂等：可重复执行
-- ============================================================================
-- 【现象】
--   打开「智慧生产执行系统 → 生产工作台」的 AI 面板，提示：
--   「会话初始化失败：聊天角色不存在。请点击右上角「重新开始会话」重试。」
--
-- 【根因】
--   1. 前端查角色时固定传 publicStatus=false，后端
--      AiChatRoleMapper.selectPageByMy 走「私有」分支，只按 user_id = 当前用户 过滤；
--   2. sql/clone_ai_to_all_tenants.sql 克隆角色时把 user_id 置为 NULL，
--      但没有把 public_status 同步改成 1（公开），于是形成
--      「标称私有、却无归属人」的矛盾数据 —— 任何用户按 user_id 都查不到；
--   3. 前端只能回退到硬编码的 fallbackRoleId = 20，而该 id 仅在 2010 租户存在
--      （其余租户的角色 id = tenant_id * 1000000 + 20）。
--
-- 【处理】
--   把各租户的「MES 生产助手」统一改为公开角色（public_status = 1），
--   使其能被 /ai/chat-role/my-page?publicStatus=true 正常检索到。
-- ============================================================================

SET NAMES utf8mb4;

UPDATE ai_chat_role
SET public_status = 1,
    update_time   = NOW()
WHERE deleted = 0
  AND name = 'MES 生产助手'
  AND (public_status IS NULL OR public_status <> 1);

-- 验证：各租户「MES 生产助手」的 public_status_num 应全为 1
SELECT id,
       tenant_id,
       name,
       public_status + 0 AS public_status_num,
       user_id,
       model_id,
       status
FROM ai_chat_role
WHERE deleted = 0
  AND name LIKE 'MES%'
ORDER BY tenant_id;
