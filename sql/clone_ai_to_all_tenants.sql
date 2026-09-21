-- 将租户 2010 的 AI 全套数据（API Key/模型/工具/知识库/角色）克隆到所有其他租户
-- 每租户 id 偏移 = tenant_id * 1000000，幂等（IGNORE 防重跑冲突）
DROP PROCEDURE IF EXISTS clone_ai_to_tenants;
DELIMITER $$
CREATE PROCEDURE clone_ai_to_tenants()
BEGIN
  DECLARE tid BIGINT;
  DECLARE off_ BIGINT;
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR SELECT id FROM system_tenant WHERE deleted = 0 AND id <> 2010;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cur;
  loop_t: LOOP
    FETCH cur INTO tid;
    IF done THEN LEAVE loop_t; END IF;
    SET off_ = tid * 1000000;

    -- 1. API Key
    CREATE TEMPORARY TABLE tmp_k AS SELECT * FROM ai_api_key WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp_k SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO ai_api_key SELECT * FROM tmp_k;
    DROP TEMPORARY TABLE tmp_k;

    -- 2. 模型（key_id 同偏移）
    CREATE TEMPORARY TABLE tmp_m AS SELECT * FROM ai_model WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp_m SET id = id + off_, key_id = key_id + off_, tenant_id = tid;
    INSERT IGNORE INTO ai_model SELECT * FROM tmp_m;
    DROP TEMPORARY TABLE tmp_m;

    -- 3. 工具
    CREATE TEMPORARY TABLE tmp_t AS SELECT * FROM ai_tool WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp_t SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO ai_tool SELECT * FROM tmp_t;
    DROP TEMPORARY TABLE tmp_t;

    -- 4. 知识库 + 文档 + 段落（embedding_model_id 同偏移）
    CREATE TEMPORARY TABLE tmp_kn AS SELECT * FROM ai_knowledge WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp_kn SET id = id + off_, embedding_model_id = embedding_model_id + off_, tenant_id = tid;
    INSERT IGNORE INTO ai_knowledge SELECT * FROM tmp_kn;
    DROP TEMPORARY TABLE tmp_kn;

    CREATE TEMPORARY TABLE tmp_doc AS SELECT * FROM ai_knowledge_document WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp_doc SET id = id + off_, knowledge_id = knowledge_id + off_, tenant_id = tid;
    INSERT IGNORE INTO ai_knowledge_document SELECT * FROM tmp_doc;
    DROP TEMPORARY TABLE tmp_doc;

    CREATE TEMPORARY TABLE tmp_seg AS SELECT * FROM ai_knowledge_segment WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp_seg SET id = id + off_, knowledge_id = knowledge_id + off_, document_id = document_id + off_, tenant_id = tid;
    INSERT IGNORE INTO ai_knowledge_segment SELECT * FROM tmp_seg;
    DROP TEMPORARY TABLE tmp_seg;

    -- 5. 角色（model/tool/knowledge 映射到新 id）
    CREATE TEMPORARY TABLE tmp_role AS SELECT * FROM ai_chat_role WHERE id = 20 AND deleted = 0;
    UPDATE tmp_role
    SET id = id + off_,
        tenant_id = tid,
        user_id = NULL,
        model_id = model_id + off_;
    UPDATE tmp_role
    SET tool_ids = (SELECT GROUP_CONCAT(CONVERT(id + off_, CHAR) ORDER BY id) FROM ai_tool WHERE tenant_id = 2010 AND deleted = 0 AND FIND_IN_SET(id, tmp_role.tool_ids)),
        knowledge_ids = (SELECT GROUP_CONCAT(CONVERT(id + off_, CHAR) ORDER BY id) FROM ai_knowledge WHERE tenant_id = 2010 AND deleted = 0 AND FIND_IN_SET(id, tmp_role.knowledge_ids));
    INSERT IGNORE INTO ai_chat_role SELECT * FROM tmp_role;
    DROP TEMPORARY TABLE tmp_role;

  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL clone_ai_to_tenants();
DROP PROCEDURE IF EXISTS clone_ai_to_tenants;

-- 验证：各租户角色数
SELECT tenant_id, COUNT(*) AS roles FROM ai_chat_role WHERE deleted = 0 GROUP BY tenant_id ORDER BY tenant_id;
