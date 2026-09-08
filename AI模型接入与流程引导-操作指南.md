# AI 模型接入与「流程引导」落地 —— 详细操作指南

> 适用：天信管业防腐保温智慧平台（多租户微服务）
> 目标：① 解决「找不到默认模型」报错；② 接入真实 AI 模型；③ 落地「流程引导」产品形态（纯配置 + 少量文档整理，无需写业务代码）
> 日期：2026-09-02

---

## 一、「找不到默认模型」问题定位

### 1.1 报错文案 → 代码出处

| 项 | 内容 |
|---|---|
| 页面提示 | `操作失败，找不到默认模型` |
| 错误码定义 | `ErrorCodeConstants.java` → `MODEL_DEFAULT_NOT_EXISTS = 1_040_001_002 "操作失败，找不到默认模型"` |
| 抛出点 | `txgy-module-ai-server` → `AiModelServiceImpl.getRequiredDefaultModel(Integer type)`（约第 99-105 行）：按 `type + status=启用` 查 `ai_model`，查不到即抛此异常 |
| 触发点 | `AiChatConversationServiceImpl.createChatConversationMy()`（约第 59-61 行）：创建对话时，**如果没有传聊天角色、或角色未绑定模型**，就走 `modalService.getRequiredDefaultModel(CHAT)` 取「默认对话模型」 |
| 前端动作 | 聊天页左侧点「新建对话」（`views/ai/chat/index/modules/conversation/list.vue` 的 `createConversation()`）→ 调用 `createChatConversationMy({})` **空参数** → 后端必须自己找默认模型 |

### 1.2 「默认模型」的确切定义

代码注释（`AiModelDO.java`）：**`ai_model` 表中 `type = 1(对话)`、`status = 1(启用)`、按 `sort` 升序排第一的那条记录**。
查询 SQL 等价于：

```sql
SELECT * FROM ai_model
WHERE type = 1 AND status = 1 AND deleted = 0
ORDER BY sort ASC LIMIT 1;
```

### 1.3 根因

平台是**多租户**的，MyBatis 租户插件会自动给 `ai_model` 查询拼上 `tenant_id = 当前租户`。
现在模型/密钥基本都配在**租户 1** 下，华翰租户（如 2010，以实际为准）下**没有一条启用的对话模型**，所以一创建对话就报「找不到默认模型」。

### 1.4 用 SQL 验证（在 mysql 执行，注意：会暴露所有租户的模型分布）

```sql
SELECT tenant_id, type, COUNT(*) AS cnt
FROM ai_model WHERE deleted = 0 GROUP BY tenant_id, type;

SELECT tenant_id, COUNT(*) AS cnt
FROM ai_api_key WHERE deleted = 0 GROUP BY tenant_id;
```

若结果里你的使用租户（如 2010）无 `ai_model` 记录 → 根因确认。

---

## 二、解决方案（二选一）

### 方式一：补齐目标租户的模型/密钥数据（推荐，不动代码）

目标租户下要有：1 条**对话模型（启用）** + 1 条**向量模型（启用，知识库要用）** + 对应 1~2 条 **API 密钥**。
做法见第三章（界面操作）或第五章（SQL 模板）。

### 方式二：改代码让模型全局共享（可选，不推荐）

如果希望「一个地方配模型、所有租户共用」，需改代码让 `ai_model` / `ai_api_key` 忽略租户过滤。ruoyi-vue-pro 提供了 `@TenantIgnore` 注解（需要引入 `tenant` 框架类）。**不推荐**：模型 Key 属于敏感配置，全局共享后任一租户都能看到所有密钥，且不符合平台"租户隔离"设计。默认建议走方式一。

> 小提示：也可以让「AI 助手」不依赖默认模型——前端新建对话时总是先选角色/模型即可，但聊天页的「新建对话」按钮就是空参调用的，所以**补数据是绕不开的一步**。

---

## 三、接入真实模型的详细步骤（界面操作）

> 管理后台菜单：**AI 大模型 → API 密钥 / 模型管理 / 聊天角色 / 知识库**（如菜单名略有差异，按模块找）。

### 3.1 前置：切换/确认租户

平台顶部一般有租户切换。**先切到使用方租户（华翰，如 2010）**，后续新增的密钥/模型都会自动归属该租户。否则加到了别的租户又会出现「找不到默认模型」。

### 3.2 新增 API 密钥（AI 大模型 → API 密钥 → 新增）

字段：
- **名称**：建议可识别，如 `华翰-DeepSeek`
- **平台**：选择你开通的平台，下拉值来自字典 `AI 平台`（见文末表）
- **密钥**：填该平台官网申请的 `sk-...`
- **API 地址**：一般留空走平台默认地址；只有用第三方中转/本地 Ollama 时才填（如 `http://192.168.x.x:11434`）
- **开启状态**：开启

### 3.3 新增「对话模型」（AI 大模型 → 模型管理 → 新增）

关键字段（`AiModelTypeEnum.CHAT = 1`）：
- 所属平台：与密钥一致
- 模型类型：**对话**
- API 密钥：选 3.2 新增的
- 模型名字：如 `DeepSeek-V3 对话`
- 模型标识：平台侧的模型名（如 `deepseek-chat`）——**必须与官方模型名一致**
- 模型排序：`1`（越小越优先，决定谁是"默认模型"）
- 开启状态：开启
- 温度参数：`0.7`；回复 Token 数：`2048~4096`；上下文数量：`10`（均必填，否则报"配置不完整"）

### 3.4 新增「向量模型」（做流程引导知识库必需，AI 大模型 → 模型管理 → 新增）

- 模型类型：**向量**（`EMBEDDING = 5`）
- 模型标识：该平台的 embedding 模型名
- 其余同上（排序 2、开启）

> 注意：**DeepSeek 官方没有 embedding 接口**。若对话用 DeepSeek，向量模型可另配一条通义/OpenAI/硅基流动等的 embedding 模型（同一个 API 密钥平台或再新增一条密钥）。

### 3.5 验证

1. 进入 **AI 大模型 → 对话** 页面，点「新建对话」——不再报错即修复；
2. 输入一句话发送，能收到 AI 回复即模型接入成功。

### 常用平台 + 模型标识参考

| 平台（枚举值） | 对话模型标识示例 | 向量模型标识示例 | 备注 |
|---|---|---|---|
| DeepSeek | `deepseek-chat` / `deepseek-reasoner` | 无 | 便宜、中文好，**无 embedding** |
| TongYi（通义） | `qwen-plus` / `qwen-turbo` | `text-embedding-v3` | 阿里百炼，Key 形如 `sk-` |
| OpenAI | `gpt-4o-mini` / `gpt-4o` | `text-embedding-3-small` | 需网络可达 |
| SiliconFlow（硅基流动） | `Qwen/Qwen2.5-7B-Instruct` | `BAAI/bge-m3` | 国内直连，模型多 |
| ZhiPu（智谱） | `glm-4-plus` | `embedding-2` | 国内直连 |
| Ollama（本地） | 如 `qwen2.5:7b` | 需支持 embedding 的模型 | API 地址填 Ollama 服务地址，且 ai-server 容器要能访问到该地址 |

---

## 四、落地「流程引导」产品形态（知识库 RAG）

> 原理：把 MES 操作手册/SOP 文档导入知识库（切片 + 向量化），创建一个「生产流程引导」聊天角色绑定该知识库。员工在对话里问"XX 怎么做"，AI 检索知识库后**按步骤**给出操作指引。
> **此形态无需开发 Java 代码**，全部走现成功能，配置完即可用。

### 4.1 创建知识库（AI 大模型 → 知识库 → 新增）

- 知识库名称：如 `MES 操作流程库`
- 描述：说明用途
- **向量模型**：选 3.4 新增的 embedding 模型（必须先有）
- 检索 topK：`3~5`（召回片段数，少了答不全、多了易串扰）
- 检索相似度阈值：`0.6~0.75`（低了易召回无关内容）
- 开启状态：开启

### 4.2 导入操作文档（知识库 → 文档）

支持 Word / PDF / Markdown / 文本 / URL。**强烈建议按流程拆分文件**，例如：
- `生产报工操作步骤.md`
- `生产工单创建与确认流程.md`
- `来料检验（IQC）流程.md`
- `产品入库（上架→入库）流程.md`
- `安灯异常上报流程.md`
- `设备点检流程.md`
- ……

每个文件内建议按「**适用场景 → 前置条件 → 分步骤操作（每步写明菜单/按钮/字段/注意事项）→ 常见问题**」组织，切片后 AI 才能给出可靠步骤。若暂时只有零散文档，可在「段落」里手动逐条补充关键步骤。

### 4.3 创建「生产流程引导」角色（AI 大模型 → 聊天角色 → 新增）

字段建议：
- 角色名称：`生产流程引导助手`
- 角色头像：可传一张图
- 绑定模型：选 3.3 的**对话模型**
- 角色类别：如 `MES`（便于检索）
- 角色描述：`指导用户完成 MES 中的操作流程`
- 角色设定（关键 Prompt）参考：
  > 你是【生产流程引导助手】。用户会询问 MES 系统里某项操作"怎么做"。请严格做到：1) 优先依据引用的知识库内容回答，给出清晰、分步骤的操作指引（标明菜单入口、要点字段）；2) 知识库里没有的内容，明确说明"操作手册暂未收录"，不要编造步骤；3) 回答简洁、用序号分步；4) 用户追问时基于知识库逐步细化。
- 引用知识库：勾选 4.1 的 `MES 操作流程库`
- 引用工具 / MCP：本期流程引导**不需要**
- 是否公开：开启（让所有员工都可用）；开启状态：开启

### 4.4 使用与验证

1. 进入 **AI 大模型 → 对话**，左侧底部「角色仓库」→ 选择「生产流程引导助手」→ 新建对话；
2. 提问测试：
   - 「生产报工怎么操作？」
   - 「检验不合格该怎么处理？」
   - 「产品入库的流程是什么，上架和入库有什么区别？」
3. 检查回答：步骤是否来自手册、是否完整。若不好 → 见 4.5 调优。

### 4.5 问答效果调优要点（由差到好依次排查）

1. **文档质量**：手册没写/写得不清晰 → AI 答不好，先补文档；
2. **切片粒度**：文档段落太乱时，可在「段落」里编辑拆分；
3. **检索参数**：答非所问 → 调高相似度阈值 / 降低 topK；
4. **角色 Prompt**：约束"只依据知识库、不编造、分步骤"；
5. 换更强对话模型（如 `deepseek-chat` → `qwen-plus`/`gpt-4o-mini`）对比效果。

---

## 五、参考 SQL（不想用界面时可走此路径）

> 前提：先确认目标租户号 `T`（华翰租户，执行 `SELECT id FROM system_tenant WHERE name LIKE '%华翰%';` 查得）。字段按上面 DO 结构补齐（`deleted=0`、`tenant_id=T`）。

```sql
-- 1) 新增 API 密钥（以 DeepSeek 为例；KEY 换成真实值）
INSERT INTO ai_api_key
  (name, api_key, platform, url, status, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
  ('华翰-DeepSeek', 'sk-真实Key', 'DeepSeek', NULL, 1, '1', NOW(), '1', NOW(), 0, 2010);
-- 记下新 id，假设 = 100（实际以 LAST_INSERT_ID() 为准）

-- 2) 新增对话模型（type=1 对话；sort=1 表示默认）
INSERT INTO ai_model
  (key_id, name, model, platform, type, sort, status, temperature, max_tokens, max_contexts,
   creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
  (100, 'DeepSeek-V3 对话', 'deepseek-chat', 'DeepSeek', 1, 1, 1, 0.7, 2048, 10,
   '1', NOW(), '1', NOW(), 0, 2010);

-- 3) 新增向量模型（type=5 向量；如无 DeepSeek embedding，新增一条通义密钥后绑定）
INSERT INTO ai_api_key
  (name, api_key, platform, url, status, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
  ('华翰-通义', 'sk-真实Key', 'TongYi', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 1,
   '1', NOW(), '1', NOW(), 0, 2010);

INSERT INTO ai_model
  (key_id, name, model, platform, type, sort, status, temperature, max_tokens, max_contexts,
   creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
  (101, '通义向量模型', 'text-embedding-v3', 'TongYi', 5, 2, 1, NULL, NULL, NULL,
   '1', NOW(), '1', NOW(), 0, 2010);
```

> 说明：`platform` 必须用枚举值（DeepSeek / TongYi / OpenAI / SiliconFlow…）；`model` 为平台官方模型名；对话模型建议补齐 temperature/max_tokens/max_contexts（不填会触发 `CHAT_CONVERSATION_MODEL_ERROR 配置不完整`）。

---

## 六、常见问题

| 现象 | 原因与处理 |
|---|---|
| 创建对话报「找不到默认模型」 | 当前租户无启用对话模型 → 按第三章补数据 |
| 发消息报「模型配置不完整」 | 模型 temperature/maxTokens/maxContexts 未填 → 补上 |
| 报「模型已禁用」 | 模型 status 为禁用 → 在模型管理开启 |
| 对话无回复 / 报连接错误 | API Key 无效、平台不可达、或 URL 填错 → 核对密钥与地址 |
| 知识库问答答非所问 | 向量模型/检索参数/文档质量问题 → 按 4.5 调优 |
| DeepSeek 无法建知识库 | DeepSeek 无 embedding → 对话用 DeepSeek，向量另配通义/硅基等 |

---

## 附：后续要做「真正的 AI 简化 MES 操作」（第二期，需开发）

「流程引导」只是问答。若要让 AI 进一步**直接查 MES 数据**（如"我这单报工通过没"）或**预填报工**，才需要开发：在 `txgy-module-ai-server` 引入 `txgy-module-mes-api`，新增 MES Tool（复用现成 Function Calling 机制），并给「生产流程引导助手」角色绑定工具。届时再进入编码阶段。
