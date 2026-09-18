-- 克隆部署：采购入库单审批流程定义（基于报工审批 mes-feedback-approval:2）
-- 说明：BPMN XML 中 mes-feedback-approval 全量替换为新 key；中文展示名保持一致语义可后续在界面调整
-- 执行后需重启 bpm-server 刷新流程定义缓存

SET @old_pid  = 'mes-feedback-approval:2:f54f4440-b024-11f1-b536-7a3c09ea4e84';
SET @old_dep  = 'f4d7cb4d-b024-11f1-b536-7a3c09ea4e84';
SET @old_key  = 'mes-feedback-approval';
SET @new_key  = 'mes-wm-item-receipt-approval';
SET @new_dep  = REPLACE(UUID(), '-', '');
SET @pid      = CONCAT(@new_key, ':1:', REPLACE(UUID(), '-', ''));

-- 1. 部署记录
INSERT INTO ACT_RE_DEPLOYMENT (ID_, NAME_, CATEGORY_, KEY_, TENANT_ID_, DEPLOY_TIME_)
SELECT @new_dep, '采购入库单审批', CATEGORY_, NULL, TENANT_ID_, NOW(3)
FROM ACT_RE_PROCDEF WHERE ID_ = @old_pid;

-- 2. BPMN 资源（key 全量替换）
INSERT INTO ACT_GE_BYTEARRAY (ID_, REV_, NAME_, BYTES_, DEPLOYMENT_ID_, GENERATED_)
SELECT CONCAT('ir-ba-', REPLACE(UUID(), '-', '')), 1,
       CONCAT(@new_key, '.bpmn'),
       REPLACE(CONVERT(b.BYTES_ USING utf8mb4), @old_key, @new_key),
       @new_dep, 0
FROM ACT_GE_BYTEARRAY b
WHERE b.DEPLOYMENT_ID_ = @old_dep AND b.NAME_ = CONCAT(@old_key, '.bpmn');

-- 3. 流程定义
INSERT INTO ACT_RE_PROCDEF (ID_, REV_, CATEGORY_, NAME_, KEY_, VERSION_, DEPLOYMENT_ID_,
    RESOURCE_NAME_, DGRM_RESOURCE_NAME_, DESCRIPTION_, HAS_START_FORM_KEY_, HAS_GRAPHICAL_NOTATION_,
    SUSPENSION_STATE_, TENANT_ID_, ENGINE_VERSION_, DERIVED_FROM_, DERIVED_FROM_ROOT_, DERIVED_VERSION_)
SELECT @pid, REV_, CATEGORY_, '采购入库单审批', @new_key, 1, @new_dep,
       CONCAT(@new_key, '.bpmn'), NULL, DESCRIPTION_, HAS_START_FORM_KEY_, HAS_GRAPHICAL_NOTATION_,
       SUSPENSION_STATE_, TENANT_ID_, ENGINE_VERSION_, DERIVED_FROM_, DERIVED_FROM_ROOT_, DERIVED_VERSION_
FROM ACT_RE_PROCDEF WHERE ID_ = @old_pid;

-- 4. BPM 扩展信息（审批中心列表/详情使用）
INSERT INTO bpm_process_definition_info (process_definition_id, model_id, model_type, category, icon, description,
    form_type, form_id, form_conf, form_fields, form_custom_create_path, form_custom_view_path, simple_model,
    sort, visible, start_user_ids, start_dept_ids, manager_user_ids, allow_cancel_running_process,
    allow_withdraw_task, process_id_rule, auto_approval_type, title_setting, summary_setting,
    process_before_trigger_setting, process_after_trigger_setting, task_before_trigger_setting,
    task_after_trigger_setting, print_template_setting, creator, updater, deleted, tenant_id)
SELECT @pid, model_id, model_type, category, icon, description,
    form_type, form_id, form_conf, form_fields, form_custom_create_path, form_custom_view_path, simple_model,
    sort, visible, start_user_ids, start_dept_ids, manager_user_ids, allow_cancel_running_process,
    allow_withdraw_task, process_id_rule, auto_approval_type, title_setting, summary_setting,
    process_before_trigger_setting, process_after_trigger_setting, task_before_trigger_setting,
    task_after_trigger_setting, print_template_setting, creator, updater, deleted, tenant_id
FROM bpm_process_definition_info WHERE process_definition_id = @old_pid AND deleted = 0;

-- 5. 验证
SELECT ID_, KEY_, NAME_, VERSION_ FROM ACT_RE_PROCDEF WHERE KEY_ = @new_key;
SELECT id, process_definition_id, form_type, form_id FROM bpm_process_definition_info WHERE process_definition_id = @pid;
