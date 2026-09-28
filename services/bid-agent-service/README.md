# bid-agent-service · 标书生成 Agent 编排层

> 对应《标书Agent_最终架构设计》第 3.2 / 4 章。Node 22 + 零运行时依赖（HTTP、JSON Schema 校验、日志、HTTP 客户端全部自建），
> 模型经 OpenAI 兼容接口直连私有化 vLLM，指令通道通过双向 OpenAPI 与 Java 侧（txgy-module-bid）对接。

一句话：**可靠性放在 Node 层**（状态机 / HIL / 检查点 / 重试降级 / 可观测），Java 侧不变，业务系统零侵入。

---

## 1. 目录结构

```
bid-agent-service/
  src/
    index.ts                     启动入口（配置加载、优雅停机）
    config.ts                    env + .env 配置
    logger.ts / errors.ts        结构化日志、错误归一化（含重试判定）
    lib/                         jsonSchema 校验器、原子写文件、指数退避、HTTP 客户端
    client/javaApi.ts            Agent → Java（§3.1 五个接口 + 影子状态）
    session/
      types.ts                   会话后端抽象（隔离 Pi SDK / LangGraph）
      providers/openaiCompat.ts  直连 vLLM
      providers/piSdk.ts         Pi SDK 适配器 + 后端工厂
      piSessionFactory.ts        会话 Transcript 持久化（断点续跑基础）
    tools/                       bid-retrieve / ask-user / json-validation / task-failure + 受限文件工具
    orchestrator/
      stateMachine.ts            四类任务的阶段序列
      checkpoints.ts             检查点落盘与恢复
      hil.ts                     人工确认闸门
      runner.ts                  Agent 回合控制器（工具调度 + 结构化输出修复）
      taskService.ts             任务生命周期（创建/查询/确认/中断/回退）
      prompts.ts                 系统提示词与反幻觉约束
    tasks/                       parse / outline / draft
    http/server.ts               Java → Agent（§3.2 六个接口）
  tools/
    mock-java-api.mjs            Java 侧契约 Mock
    mock-llm.mjs                 OpenAI 兼容 LLM Mock（按 schema 生成样例输出）
    smoke-test.mjs               端到端冒烟脚本（三类任务 + HIL + 回滚 + 中断）
    e2e-generate.mjs             标书生成 E2E（走 Java 网关，UTF-8 安全；中文 remark 不会乱码）
  workspace/{tenantId}/{projectId}/    运行中生成的 Project 工作区
  sessions/{taskId}/                   会话 transcript + 检查点 + 任务状态
```

---

## 1.5 在 txgycloud 仓库内运行（当前推荐）

本服务已随招投标分支下发到仓库内 `services/bid-agent-service/`，队友拉分支即可使用（**不需要**外部目录 `D:\招投标项目\bid-agent-service`）。

```powershell
cd services\bid-agent-service

# 1) 装依赖（只有 @modelcontextprotocol/sdk 一个运行时依赖；装不上也能启动，只是 MCP 工具会跳过并告警）
npm i

# 2) 编译 + 启动（监听 8199）
npm run build
npm run start          # 等价于 node dist/index.js

# 3) 自检
npm run typecheck
curl http://127.0.0.1:8199/agent/tasks   # 返回 {"tasks":[...]} 即通
```

`.env`（从 `.env.example` 复制）里与联调相关的关键项：

| 键 | 取值 | 说明 |
|---|---|---|
| `AGENT_HOST` / `AGENT_PORT` | `127.0.0.1` / `8199` | 底座监听；Java 侧 `bidding.bid-agent.orchestrator.base-url` 默认指向它 |
| `JAVA_BASE_URL` | `http://127.0.0.1:48080`（网关）或 `http://127.0.0.1:48110`（直连 bidding） | 契约端 `/admin-api/bidding/bid-agent/contract/**` |
| `JAVA_AGENT_KEY` | 与 Java 侧 `bidding.bid-agent.agent-key`（环境变量 `TXGY_BID_AGENT_KEY`）一致 | 两边都为空＝开发态不校验 |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | 百炼（DashScope）OpenAI 兼容端点 / key / 模型名 | 后台「智能体管理 → 云端模型」启用后会被平台下发的配置覆盖 |
| `SESSION_BACKEND` | `openai-compat` | 默认会话后端 |

运行态目录（`node_modules/`、`dist/`、`.env`、`sessions*/`、`workspace*/`、`uploads/`、`*.log`）都已在同目录 `.gitignore` 中排除，**只有源码与 `.env.example` 入库**。

> 下面第 2 节的路径写法（`..\tools\node`、仓库根 `start-agent.bat`）来自该服务早期独立目录（`D:\招投标项目\bid-agent-service`）的部署方式，作为独立目录部署时仍适用；在 txgycloud 仓库内请用本节的命令。

---

## 2. 快速开始

运行环境已内置在项目 `.tools\node`（Node 22.20），无需系统级安装。

```powershell
# 编译
cd bid-agent-service
..\tools\node\node.exe node_modules\typescript\bin\tsc -p tsconfig.json

# 启动（依赖服务见下）
..\tools\node\node.exe dist\index.js
```

或者双击仓库根目录的 `start-agent.bat`（自动完成编译并启动）。

依赖服务：

| 服务 | 地址 | 本地替代 |
|---|---|---|
| Java 侧 `txgy-module-bid` | `JAVA_BASE_URL` | `node tools/mock-java-api.mjs` |
| 私有化 LLM（vLLM Qwen2.5） | `LLM_BASE_URL` | `node tools/mock-llm.mjs` |

冒烟验证（自动拉起两个 Mock 并跑通全部链路）：

```powershell
..\tools\node\node.exe tools\smoke-test.mjs
```

预期输出：

```
✓ parse     status=completed  HIL=1  解析完成：废标条款 N 条，全局事实 N 条
✓ outline   status=completed  HIL=1  大纲已生成，共 N 个章节
✓ draft     status=completed         已撰写 N 个章节，其中待复核 0 个
✓ rollback  status=completed（回滚到 WRITE 后重跑）
✓ interrupt status=interrupted
冒烟测试全部通过 ✅
```

---

## 3. 环境变量

复制 `.env.example` 为 `.env` 后按需调整，进程环境变量优先级更高。

| 变量 | 默认 | 说明 |
|---|---|---|
| `AGENT_HOST` / `AGENT_PORT` | `127.0.0.1` / `8080` | 服务监听 |
| `AGENT_API_KEYS` | 空（不校验） | 入站鉴权；`key` 或 `key:租户A\|租户B`，配置后须带 `X-Agent-Key` 请求头 |
| `AGENT_REQUIRE_AUTH` | `false` | 未配置 Key 时是否强制 401 |
| `AGENT_ALLOWED_ORIGINS` | `http://127.0.0.1:5180,http://localhost:5180` | CORS 白名单；`*` 表示放开 |
| `AGENT_PUBLIC_BASE_URL` | 空（按本机端口推断） | Java 回拉上传文件的对外地址，跨机/容器部署必须配置 |
| `JAVA_BASE_URL` | `http://127.0.0.1:48081` | Java 侧基地址 |
| `JAVA_API_KEY` | 空 | Bearer 鉴权 |
| `JAVA_AGENT_KEY` | 空 | 机对机鉴权密钥，作为 `X-Agent-Key` 请求头，对应 Java 侧 `txgy.bid.agent-key` |
| `JAVA_SHADOW_STATUS_PATH` | 空 | 任务状态影子上报路径，留空关闭 |
| `JAVA_ADMIN_USER` / `JAVA_ADMIN_PASS` | `admin` / `admin123` | 知识库入库用的 Java 管理员凭据；未覆盖时沿用内置默认账号（启动会告警） |
| `LLM_BASE_URL` / `LLM_MODEL` / `LLM_API_KEY` | vLLM | OpenAI 兼容接口 |
| `SESSION_BACKEND` | `openai-compat` | `pi-sdk` 时加载 `PI_SDK_MODULE` |
| `PI_SDK_MODULE` | 空 | Pi SDK 适配器模块路径 |
| `RETRIEVE_TOPK` | 8 | 检索条数（相似度阈值由 Java 侧检索服务决定） |
| `MAX_TOOL_ROUNDS` / `MAX_FIX_ATTEMPTS` | 40 / 3 | 回合上限与结构化输出修复次数 |
| `HIL_WAIT_TIMEOUT_MS` | 24h | 人工确认等待上限 |
| `MAX_CONCURRENT_TASKS` / `MAX_QUEUE_DEPTH` | 2 / 50 | 同时执行的任务数与排队深度；超出队列上限时创建任务返回 429 |
| `MAX_TASK_TOKENS` | 0（不限制） | 单任务 token 预算，超过后任务以 `output_invalid` 中止 |
| `RESUME_HISTORY_MESSAGES` / `RESUME_HISTORY_CHARS` | 12 / 6000 | 断点续跑时回灌的会话历史上限 |
| `SSE_MAX_IDLE_MS` | 30min | SSE 长连接空闲上限，到期关闭由客户端重连 |
| `PDF_VIA_WORD` | `false` | 是否允许调用本机 Word 转 PDF（推荐改用独立转换服务） |
| `WORKSPACE_DIR` / `SESSIONS_DIR` | 服务目录内 | **必须挂持久化卷** |

---

## 4. 对外接口（§3.2）

```http
POST /agent/tasks                     body: { tenantId|头X-Tenant-Id, projectId, taskType, payload }
GET  /agent/tasks?projectId=&status=  任务列表
GET  /agent/tasks/{taskId}            → { status, phase, progress, currentNodeId, pendingQuestions[] }
POST /agent/tasks/{taskId}/confirm    body: { answers:[{ questionId, value }] }
POST /agent/tasks/{taskId}/interrupt
POST /agent/tasks/{taskId}/rollback   body: { toPhase, keepSession, nodeId? }
GET  /healthz        存活探针（无需鉴权）
GET  /readyz         就绪探针：Java 可达性 + 工作区/会话目录可写（无需鉴权）
GET  /metrics        Prometheus 文本指标（无需鉴权）
GET  /agent/tools
```

`interrupt` 只接受非终态任务，终态（completed/failed/interrupted）返回 409；`rollback` 的 `nodeId`
用于章节类任务（draft）按节点回退，配合“跳过已提交章节”实现单章重跑。

配置 `AGENT_API_KEYS` 后，除 `/healthz`、`/readyz`、`/metrics` 与 `/kb/files/{fileId}` 外的接口都需要携带
`X-Agent-Key`（或 `Authorization: Bearer <key>`）；Key 形如 `key:租户A|租户B` 时，只能访问绑定租户的数据，
跨租户的任务详情/产物一律按 404 返回。错误响应统一为
`{ error, code, status, retryable, action, details, traceId }`，其中 `action` 取值 `retry | fix_request | manual`。

示例：

```bash
ID=$(curl -s localhost:8080/agent/tasks -H 'Content-Type: application/json' -H 'X-Tenant-Id: t1' \
  -H 'Idempotency-Key: 2026-parse-001' \
  -d '{"projectId":"P1","taskType":"parse"}' | jq -r .taskId)

curl "localhost:8080/agent/tasks/$ID"
curl -X POST "localhost:8080/agent/tasks/$ID/confirm" -H 'Content-Type: application/json' \
  -d '{"answers":[{"questionId":"q_xxx","value":"确认"}]}'
```

幂等：所有写接口支持 `Idempotency-Key`；`/draft/chapter` 按 `(projectId, nodeId, version)` 幂等。

---

## 5. 任务类型与阶段

| 任务 | 阶段序列 | 是否调用模型 | 产出 |
|---|---|---|---|
| `parse` | INIT → RETRIEVE → EXTRACT → CONFIRM → PERSIST → DONE | EXTRACT（结构化抽取） | `项目事实卡.md`、`招标文件解析.md`、`tenderParse.json` |
| `outline` | INIT → LOAD → PLAN → CONFIRM → PERSIST → DONE | PLAN | `outline.json` |
| `draft` | 每节点 SELECT → RETRIEVE → WRITE → VERIFY → COMMIT，末尾 DONE | WRITE / 修复轮 | `章节草稿/{nodeId}.md` + Java 回写 |
| `generate` | PARSE → OUTLINE → DRAFT → DONE（一键生成全流程） | 各阶段 | 阶段产物 + 导出交付件 |

模板字段回填不在任务阶段内：导出阶段（`/bid-file/docx`）按「标签留白 / 书签占位」两种模板形态确定性回填（见 §9 交付排版）。

每个阶段完成即落检查点（`sessions/{taskId}/checkpoints.json`），`rollback` 会丢弃该阶段之后的检查点并按 `keepSession` 决定是否清空会话。

- **章节级续跑**：`draft` 启动时会跳过检查点中已 `COMMIT` 的章节，因此 `rollback` 后可只重跑缺失/需要修改的章节；传 `payload.redo=true` 可强制全部重写。
- **部分检索未命中**：整章无任何证据时任务仍以 `retrieval_miss` 中止；仅部分子主题未命中时降级为“完成 + 章节标记待复核”，并在章节文件与任务结果中留痕。
- **断点续跑**：任务重启/回退后会把该任务此前的会话记录按上限回灌给模型（受 `RESUME_HISTORY_*` 约束）；首次执行无历史，行为不变。

---

## 6. 零幻觉设计

- **模板字段回填不经模型**：字段取值走事实卡 + 招标解析的确定性换算（报价大写走央行规则、工期走开工/竣工推算），由导出阶段完成；签字/盖章/法人代表等 `manual` 字段一律不写。
- **数字一致性**：draft 的 `VERIFY` 会抽取正文中的有效数字，凡在检索证据与项目事实卡中都查不到实据的一律判违规，触发修复轮；二次不通过即挂 HIL 交给人工。
- **检索未命中即中止**：`hit=false` 时不得生成，任务以 `retrieval_miss` 失败并给出 `completed` 清单，便于从中间节点重跑。

---

## 7. 工具集

| 工具 | 来源 | 说明 |
|---|---|---|
| `bid-retrieve` | 新增 | 调 Java 检索服务，结果落 `检索结果/{name}.json`（按需检索，不做全量预置） |
| `ask-user` | 易标语义 | HIL 闸门，调用后任务进入 `awaiting_human` |
| `json-validation` | 易标语义 | 结构化输出自检 |
| `task-failure` | 易标语义 | 失败上报，编排层立即收束并转人工 |
| `read-file` / `write-file` / `list-files` | 收敛版 | 限制在任务工作区内 |

> 与文档 §4.3 的**有意差异**：服务端不提供 `bash`。体现代价为少量灵活性，换取多租户场景下的进程隔离与可审计性。

---

## 8. Pi SDK / LangGraph 切换

编排层只依赖 `SessionBackend`（`src/session/types.ts`）：

```ts
interface SessionBackend { readonly name: string; chat(req: ChatRequest): Promise<ChatResponse> }
```

- `SESSION_BACKEND=openai-compat`（默认）：直连私有化 vLLM/网关，零第三方依赖；
- `SESSION_BACKEND=pi-sdk` + `PI_SDK_MODULE=...`：加载易标 `piSessionFactory` 剥离 Electron 后的产物；会话格式（transcript.jsonl）与工作区格式由本服务自定义，因此升级或更换编排框架不需要改动编排层与业务代码。

---

## 9. 生产部署注意

- **有状态**：`sessions/` 必须挂持久化卷；多副本需按 `taskId` 做会话亲和，否则断点续跑失效。
- **HIL 实现**：当前为进程内 Promise 等待 `/confirm`，适用于单副本（P0–P2）。**进程启动时会做一次对账**：上一次运行遗留的 `running`/`pending`/`awaiting_human` 任务会被收敛为 `interrupted` 并给出说明，避免任务永久悬挂；需要继续时用 `rollback` 从检查点续跑。多副本时应替换为「独立进程 + 共享应答通道（Redis/DB/消息总线）」，接口契约不变。
- **并发与项目隔离**：同一时刻最多执行 `MAX_CONCURRENT_TASKS` 个任务，其余排队；同一 `tenantId/projectId` 的任务串行执行，避免并发写坏共享的事实卡/大纲/检索结果。
- **成本可观测**：任务累计 token 用量写入 `task.json` 的 `usage` 字段，并在任务详情接口返回。
- **影子状态**：配置 `JAVA_SHADOW_STATUS_PATH` 后任务状态会在 Java 侧留一份记录，便于故障后对照恢复。
- **任务清理**：任务记录常驻内存索引（磁盘 `task.json` 仍是事实源），进程重启后重新加载；建议按保留策略定期归档 `sessions/`。
- **鉴权与租户隔离**：生产环境必须配置 `AGENT_API_KEYS`（并按租户绑定），同时把 `AGENT_ALLOWED_ORIGINS` 收敛到真实前端来源；未配置 Key 时服务只做告警、不拦截，等同于开放接口。
- **知识库凭据**：默认沿用内置账号 `admin/admin123`（开箱可用，启动会告警）；生产环境请用 `JAVA_ADMIN_USER` / `JAVA_ADMIN_PASS` 覆盖为专用服务账号。token 按租户缓存并设 TTL，遇到 Java 重启会自动重登重试一次。
- **可观测**：`/readyz` 用于编排存活/就绪探针；`/metrics` 暴露请求数、任务创建/完成/失败、运行与排队数、累计 token；所有日志与出站 Java 调用都会带上 `X-Request-Id`（也可由调用方传入）。
- **交付排版（两条路径，定位不同，勿混用）**：
  - **预览件（已可用）**：`/bid-file/export` 由内置零依赖排版引擎生成（接口返回 `deliveryMode: preview`），含封面、TOC 域、页脚 PAGE 域及 `settings.xml` 的 `updateFields`；**目录需在 Word 中"更新域"（F9）后才显示页码**。定位为**内部审阅 / 快速成型**：未覆盖图片、公式、脚注、编号体系，不保证与招标格式章逐条一致，**不得直接递交**。
  - **正式交付（标书模板回填，已可用）**：`/bid-file/docx` 在存在标书模板（`BID_TEMPLATE_DOCX`，默认 `templates/投标标书_主模板.docx`，2026-09-22 起旧模板已移除）时，直接**回填该模板作为最终交付件**（接口返回 `deliveryMode: template`）。主模板为**书签占位模板**（变量字典方案，1334 个书签，配套 `templates/投标标书_变量字典.md` 与机器可读字典 `templates/bookmark-dict.json`）：按 `word/document.xml` 里的 `bookmarkStart/bookmarkEnd` 书签名回填，基础变量名自动覆盖 `_2/_3/_appendix` 等同名变体，并吃掉完整日期后的「年月日」骨架。字段取值四通道：标准字段命中（事实卡/招标解析，`bookmarkFields.buildBookmarkValues`）、确定性换算（大写金额/日期拆分）、**报价数据（COPY_PRICING：Java 侧 PRICING 材料 Excel 解析入库 → `/contract/pricing` → 按字典 table/row/col 展开书签名）**、章节正文（插入到「六、施工组织设计」标题之后）。交付前执行**废标红线一致性断言**（bid_total/bidder_name 等 9 个共享变量全处一致，不一致 422 拦截）。仍不处理：签字/盖章/法人代表等 `manual` 字段、图片/公式/编号体系；模板缺失时回退预览件。
  - **书签模板 CLI 与回归资产**：`node tools/fill-bookmark-template.mjs --inspect <模板.docx>` 列出模板全部书签名；`node tools/fill-all-bookmarks.mjs` 为 P0 冒烟标准（字典全量回填 1297 基础变量 0 跳过 + 五项审计 + 交付链路 + 红线断言 + 报价通道，四阶段全绿）；`node tools/gen-bookmark-dict.mjs` 重新生成字典（字典唯一维护入口）；`node tools/expand-template-rows.mjs <N>` 生成 N 行版模板（行数超限方案）；`node tools/audit-filled-docx.mjs` 独立五项审计。
  - 2026-09-22 起**不再有 Java 侧 SDT 回填路径**（`/contract/docx/invoke` 五动作、Node `fill` 任务与 `bid-docx` 工具已删除）：真实标书模板无 SDT 内容控件，该路径在生产链路中从不生效，回填一律走导出阶段（上两条）。
- **PDF 转换**：默认关闭，只有显式设置 `PDF_VIA_WORD=true` 才会调用本机 Word；生产建议改用独立转换服务（LibreOffice/docx4j）。

---

## 10. 已知差距（相较 P3 目标）

| 项 | 现状 |
|---|---|
| 任务元数据存储 | 文件 JSON（`sessions/{taskId}/task.json`），未接 MySQL |
| 按节点续跑 | 已支持：draft 跳过已 `COMMIT` 章节，`rollback` 可带 `nodeId` 定位单章 |
| 多副本 HIL | 需替换为共享应答通道 |
| golden set 评测 | 未内置，建议对接 Java 侧的 Recall 评测脚本 |
