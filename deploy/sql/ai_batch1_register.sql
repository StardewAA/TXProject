-- =====================================================================
-- MES AI 助手 第一批工具注册（pro 域 6 个）+ 角色绑定 + Prompt + 模型参数
-- 执行目标：ruoyi-vue-pro 库，tenant_id=2010
-- 幂等性：工具注册带 NOT EXISTS 保护；角色/模型为定点更新可重复执行
-- =====================================================================
SET NAMES utf8mb4;

-- 1) 注册 6 个新工具（name = Spring Bean 名，ai-server 通过 Bean 名解析）
INSERT INTO ai_tool (name, description, status, tenant_id)
SELECT v.name, v.description, 0, 2010 FROM (
    SELECT 'mes_andon_query' AS name, 'MES 安灯呼叫查询：按工作站编码、处置状态、呼叫时间范围查询安灯呼叫原因与处置情况' AS description
    UNION ALL SELECT 'mes_project_query', 'MES 项目查询：按项目编码、名称、状态、来源类型、优先级、负责人查询项目信息'
    UNION ALL SELECT 'mes_process_query', 'MES 生产工序查询：按工序编码、名称、状态查询工序信息与工艺要求'
    UNION ALL SELECT 'mes_route_query', 'MES 工艺路线查询：按工艺路线编码、名称、状态查询工艺路线与说明'
    UNION ALL SELECT 'mes_workrecord_query', 'MES 生产记录查询：查询人员当前上工状态、上下工记录流水'
    UNION ALL SELECT 'mes_card_query', 'MES 生产流转卡查询：按流转卡编码、批次号查询流转卡信息'
) v
WHERE NOT EXISTS (SELECT 1 FROM ai_tool t WHERE t.tenant_id = 2010 AND t.name = v.name);

-- 2) 角色 id=20 绑定全部 MES 工具（6 个新增 + 原 4 个）并保留 user_profile/weather
UPDATE ai_chat_role
SET tool_ids = (SELECT GROUP_CONCAT(id ORDER BY id) FROM ai_tool
                WHERE tenant_id = 2010 AND name IN (
                    'mes_work_order_query', 'mes_task_query', 'mes_feedback_query', 'mes_qc_ipqc_query',
                    'mes_andon_query', 'mes_project_query', 'mes_process_query', 'mes_route_query',
                    'mes_workrecord_query', 'mes_card_query',
                    'user_profile_query', 'weather_query'))
WHERE id = 20 AND tenant_id = 2010;

-- 3) 角色 id=20 更新系统提示词（告知 AI 其具备的全域查询能力与回复约束）
--    system_message 原为 varchar(1024)，提示词 1176 字符，先扩为 TEXT
ALTER TABLE ai_chat_role MODIFY COLUMN system_message TEXT NULL COMMENT '角色系统提示词';
UPDATE ai_chat_role
SET system_message = '你是天信管业防腐保温智慧平台的 MES 全域业务助手。

## 你的能力
你可以查询 MES 系统 8 大业务域、55+ 业务实体的实时数据，**仅做查询，不做任何写操作**：

  ① 生产管理：工单、任务、报工、工序、工艺路线、安灯呼叫、生产记录、生产卡、项目
  ② 质量管理：IQC 来料检验 / IPQC 制程检验 / OQC 出货检验 / RQC 退货检验、缺陷、质检指标
  ③ 仓储管理：到货通知、各类入库（成品/采购/外协）、各类出库（领料/杂发/外协/退货/退料）、
     销售/销售通知、库存、盘点、移库、批次、序列号、条码、物料消耗、包装、仓库档案
  ④ 设备管理：设备台账、点检计划/记录、保养记录、维修、保养科目
  ⑤ 排产日历：工厂日历、假期、排班计划、班组
  ⑥ 主数据：物料、客户、供应商、工位、计量单位
  ⑦ 安全环保：污染判定（CLEAN/POLLUTED/UNCERTAIN）、污染暂存台账
  ⑧ 工具管理、首页统计

## 回复原则
  - ≤ 3 句话 + 1 个数据表格（或关键字段摘要），总长不超过 200 字
  - 直入主题，不寒暄、不重复用户问题、不输出"让我分析一下"等元话语
  - 数据用 markdown 表格，单条用"字段：值"格式
  - 越权：礼貌一句"该操作不在 AI 助手权限范围内"，不解释
  - 信息不全：1 句反问要哪个字段，不要反复追问
  - 连续 2 次反问同一字段 → 第 3 次直接给兜底答案
  - 连续 3 次说"我需要更多信息" → 自动结束并提示换种问法
  - 超出 MES 业务范围：1 句拒答，不展开

## 强约束
  - 禁止 emoji、表情、装饰符号
  - 禁止重复用户问题、重复自己的答案
  - 禁止思考过程、推理链、"让我分析"等元话语
  - 所有数据自动按当前登录租户隔离，你看不到也说不出来其他租户的数据
  - 所有回答必须基于工具返回的真实数据，不编造字段值'
WHERE id = 20 AND tenant_id = 2010;

-- 4) 模型 max_tokens 500 -> 800（temperature 已是 0.3，保持）
UPDATE ai_model SET max_tokens = 800 WHERE id = 65 AND tenant_id = 2010;
-- 会话级覆盖同步到 800，避免截断
UPDATE ai_chat_conversation SET max_tokens = 800 WHERE role_id = 20 AND tenant_id = 2010;
