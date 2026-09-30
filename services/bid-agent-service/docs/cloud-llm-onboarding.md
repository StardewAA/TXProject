# 标书 Agent · 云端大模型接入清单

> 目标：不本地部署大模型（vLLM），让 Agent 的生成能力走云端 OpenAI 兼容 API。
> 适用：`bid-agent-service`（Node 编排层）+ `txgy-module-bid`（Java 检索层）。
> 状态：**[ ] 进行中 / [x] 已完成（按小节勾选）**

---

## 0. 架构决策（先读）

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│ Node bid-agent-service      │  HTTP  │ Java txgy-server /bid/agent  │
│  - 生成 LLM（parse/outline/ │ ─────▶ │  - retrieve 向量检索          │
│    draft 正文）               │        │    (Embedding+Rerank+Qdrant)  │
│  - 任务编排/工具/HIL         │        │  - facts/context/draft         │
└─────────────────────────────┘        └──────────────────────────────┘
        │  生成走云端（本清单）               │  检索推荐本地 CPU（独立决策）
        ▼                                   ▼
   OpenAI 兼容云端 API                  TEI(cpu) + Qdrant 容器
```

- **生成 LLM 必须支持 function calling / tool_calls**（编排层靠 tool 做检索/写文件/提问）。国内主流模型均已支持，选择时确认。
- **检索层与生成层解耦**：检索不云化也能先跑通全流程（`txgy.bid.ai-enabled=false` 时 retrieve 转人工/仅必检，COPY_FACT/DERIVE 通道零幻觉不依赖模型）。
- 建议：**先完成第 1-3 节（生成上云）→ 跑通 parse 冒烟 → 再按第 4 节决定检索栈**。

---

## 1. 云端厂商选择（OpenAI 兼容 Chat）

| 厂商 | `LLM_BASE_URL` | 示例 `LLM_MODEL` | Function Calling | Embeddings OpenAI 兼容 | Rerank API | API Key 获取 |
|---|---|---|---|---|---|---|
| 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` / `qwen-max` / `qwen-turbo` | ✅ | 有原生 API（非 OpenAI 兼容） | 无标准兼容 | 百炼控制台 |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` / `deepseek-reasoner` | ✅ | ❌ | ❌ | platform.deepseek.com |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-plus` / `glm-4-air` | ✅ | 有（兼容 /v1/embeddings） | 无标准 | open.bigmodel.cn |
| Moonshot | `https://api.moonshot.cn/v1` | `moonshot-v1-32k` | ✅ | ❌ | ❌ | platform.moonshot.cn |
| 腾讯混元 | `https://api.hunyuan.cloud.tencent.com/v1` | `hunyuan-pro` / `hunyuan-lite` | ✅ | 有原生 API | ❌ | 腾讯云控制台 |
| SiliconFlow | `https://api.siliconflow.cn/v1` | `Qwen/Qwen2.5-7B-Instruct` | ✅ | ✅ 兼容 | 有自有 rerank（BGE 系列） | siliconflow.cn |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | ✅ | ✅ 兼容 | ❌ | platform.openai.com |
| Azure OpenAI | 见资源部署 | `gpt-4o` | ✅ | ✅ 兼容 | ❌ | Azure 门户 |

> 选择标准（按权重）：① Function Calling 稳定 → ② 中文/公文写作质量 → ③ 成本/限流 → ④ 数据合规（文档涉标书内容，建议选企业认可的云）。**默认推荐：阿里百炼 `qwen-plus`**。

---

## 2. Node Agent 接入步骤（生成上云）

### 2.1 配置

编辑本服务目录下的 `.env`（即 `services\bid-agent-service\.env`；不存在则复制同目录 `.env.example`）：

```env
# ---- LLM：云端 OpenAI 兼容 ----
SESSION_BACKEND=openai-compat
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen-plus
LLM_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
LLM_TEMPERATURE=0.2
LLM_MAX_TOKENS=8192
LLM_TIMEOUT_MS=180000
LLM_MAX_RETRIES=3

# ---- Java 侧（真实 txgy-server 在 48080；若用 mock 改 48081 并另起 mock）----
JAVA_BASE_URL=http://127.0.0.1:48080
JAVA_AGENT_KEY=            # 与 txgy-server 的 TXGY_BID_AGENT_KEY 一致（未设则留空）
JAVA_SHADOW_STATUS_PATH=/admin-api/bid/agent/task-status
```

启动前自检连通性（用任一 key 验证）：

```bash
curl -s https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer $LLM_API_KEY" -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"ping"}],"max_tokens":8}'
# 应返回 HTTP 200 与 choices[0]
```

### 2.2 启动与冒烟

```bash
install-runtime.bat          # 首次：环境
start-agent.bat              # 启动 http://127.0.0.1:8199
run-smoke.bat                # 另开窗口端到端（默认连 48081 mock；验证云端生成请设 JAVA_BASE_URL=48080）
```

- [ ] 冒烟任务 parse 达 `completed`，日志出现真实模型 token 用量
- [ ] 云端限流时任务可自动重试（LLM_MAX_RETRIES）

---

## 3. Java 检索层：三种部署模式（决策树）

```
是否需要向量召回（retrieve 出证据）？
├─ 暂不需要 → txgy.bid.ai-enabled=false（默认），retrieve 转人工。本步骤跳过。
├─ 需要且可用 CPU → 模式 A（推荐）：本地 TEI(cpu)+Qdrant
└─ 需要且只能全云 → 模式 B：云端 Embeddings+向量库（前提较多，见下）
```

### 模式 A：检索栈本地 CPU（推荐）

前置：本机 Docker 引擎运行。执行：

```bash
cd D:\天信管业防腐保温智慧平台\txgycloud.git\deploy\models
docker compose -f docker-compose.yml up -d qdrant tei-embedding tei-reranker
```

- 镜像国内拉取走已配置代理；TEI 首次启动自动从 HF 拉权重，**需容器内可用镜像源**：
  在 compose 的 `tei-embedding` / `tei-reranker` 增加环境变量：
  ```yaml
  environment:
    HF_ENDPOINT: https://hf-mirror.com   # 国内镜像，避免 huggingface.co 拉不动
  ```
- 启动后验证：
  ```bash
  curl -s http://127.0.0.1:8081/health      # tei-embedding → {"status":"ok"}
  curl -s http://127.0.0.1:8082/health      # tei-reranker  → ok
  ```
- Java 侧开启（重启 txgy-server 生效）：

  ```yaml
  # txgy-server application.yaml → txgy.bid
  ai-enabled: true
  embedding:
    base-url: http://127.0.0.1:8081/v1
  rerank:
    base-url: http://127.0.0.1:8082
  ```
  （或环境变量 `TXGY_BID_AI_ENABLED=true`、`TXGY_BID_EMBEDDING_URL`、`TXGY_BID_RERANK_URL`，重启后生效）
- 验证：先向 Java `POST /admin-api/bid/segment` 入库 1-2 个片段 → `POST /bid/agent/retrieve` 返回 `hit=true` 且有 `sourceBlockIds`。

### 模式 B：检索全云化（前提苛刻，逐项确认）

1. **Embedding**：需云提供 OpenAI 兼容 `POST /v1/embeddings`（智谱 / SiliconFlow / OpenAI / Azure 支持；DeepSeek、百炼、Moonshot 不兼容）。
2. **向量库**：本地仍起 Qdrant（云 Qdrant 集群需自备）——仅 embedding 与 rerank 上云。
3. **Rerank**：仅 SiliconFlow 提供 BGE rerank（走其自有 API），需在 Java `TeiRerankClient` 旁新增适配客户端或把 rerank 留本地 TEI。
4. 结论：模式 B 的「embedding 云端化」收益低于成本，**除非有合规/资源限制，否则选模式 A**。

---

## 4. 配置核对表（验收前逐项打勾）

- [ ] 云端账号已开通、API Key 已创建并确认额度/限流
- [ ] 模型确认支持 `function calling`（tools/tool_calls）
- [ ] `bid-agent-service/.env` 已配置且 `curl` 连通性自检通过
- [ ] （如检索真实化）TEI embedding/rerank + Qdrant 已启动，`/health` 返回 ok
- [ ] `txgy-server` 侧 `txgy.bid` 配置已生效（`agent-key` 若设了需在 Node 端 `JAVA_AGENT_KEY` 对齐）
- [ ] parse 冒烟 completed，日志含真实模型用量、无 `hit=false` 误转人工
- [ ] COPY_FACT / DERIVE 字段仍 100% 不经模型（回归抽查一致性快照）
- [ ] 安全项：云端请求无涉密明文外发（标书含客户信息时评估合规）

---

## 5. 常见问题

| 问题 | 处理 |
|---|---|
| LLM 报 401 | `LLM_API_KEY` 错/缺 Bearer 前缀 |
| 报 404 / 400 model 不存在 | `LLM_BASE_URL` 需以 `/v1` 结尾；`LLM_MODEL` 用厂商实际模型名 |
| 工具调用不稳定/无 tool_calls | 换支持 FC 更强的模型（qwen-max / glm-4-plus / gpt-4o）或提高 temperature 前先查厂商 FC 兼容文档 |
| 超时/重试耗时长 | 调低 `LLM_MAX_TOKENS`、`LLM_TIMEOUT_MS`；检查并发限流 |
| `retrieve` 恒 `hit=false` | 检索层未开：`txgy.bid.ai-enabled` 仍 false，或 TEI 未就绪（见模式 A） |
| 影子状态没上报 | `JAVA_SHADOW_STATUS_PATH` 留空即关闭；确认 Java 侧 permit-all 与 tenant ignore 已含该路径 |
