-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-10 AI 链路配置修复（公告AI解析 role 20 → deepseek-chat → test key）
-- 现象：ai-extract 依次报“聊天角色(公告AI解析)已禁用”→“模型({})已禁用”→“该聊天模型的配置不完整”
-- 根因：ai_chat_role(20)/ai_model(66)/ai_api_key(28) 三处启用位被打成禁用；role.model_id 悬空指向被删行
-- 目标配置（用户口径）：解析模型 = DeepSeek deepseek-chat，走 test key（sk-4f6f...）→ ai_model 66(key_id=28)
-- 幂等可重跑。

-- 1) 启用 AI 聊天角色「公告AI解析」（status: 0=启用 1=禁用）
UPDATE ai_chat_role SET status = 0 WHERE id = 20;

-- 2) 启用聊天模型 deepseek-chat（ai_model 平台模型行，role.model_id 指向它；status 0=启用）
UPDATE ai_model SET status = 0 WHERE id = 66;

-- 3) 启用 test key（DeepSeek sk-4f6f...）
UPDATE ai_api_key SET status = 0 WHERE id = 28;

-- 4) 校验
SELECT id, name, model_id, status FROM ai_chat_role WHERE id = 20;
SELECT id, key_id, model, status FROM ai_model WHERE id = 66;
SELECT id, name, status FROM ai_api_key WHERE id = 28;

-- 备注：勿在 ai_chat_model 表（无代码引用的旧表）重建模型行——角色 model_id 关联的是 ai_model；
--       ai_model 行必须满足 temperature/max_tokens/max_contexts 非空且 type=1(CHAT)，否则报 1040003001。
