# WMS AI 客服 —— 部署与启用说明

> 本文写给「拉取代码后要跑起来的人」以及「协助他操作的 AI Agent」。
> 请按 §1 判断属于哪种库，再按对应步骤执行。**所有步骤都是幂等的，不确定就全做一遍。**

---

## §1 先判断：你的数据库是新库还是已有库

执行下面这条 SQL 判断（能查到 `ai-service` 就是已有库，功能已就绪）：

```sql
SELECT COUNT(*) AS ready
FROM `ruoyi-vue-pro`.system_menu
WHERE deleted = b'0' AND path = 'ai-service';
```

| 查到的结果 | 你的库 | 要做的事 | 预计耗时 |
|---|---|---|---|
| `0` | **新库**（首次初始化） | 走 §2-A | 0 分钟 |
| `>= 1` | **已有库**（功能已就绪） | 走 §2-B | 0 分钟 |
| 报表不存在 / 查不到 | **已有库，且从未装过 AI 功能** | 走 §2-C | 1 分钟 |

> 判断不了或不放心：**直接走 §2-C**（幂等，重复执行无副作用）。

---

## §2 初始化数据库

### §2-A 新库（首次启动）

**什么都不用做。**

`docker-compose.yml` 已把 `./mysql/init` 挂到 MySQL 的 `/docker-entrypoint-initdb.d`，
容器首次启动会自动按文件名顺序执行全部脚本，其中：

```
deploy/mysql/init/0080-wms-ai-service.sql
```

会自动完成：建表 + 建菜单 + 授权 + 加套餐。执行完直接看 §3。

### §2-B 已有库（已装过 AI 功能）

**什么都不用做。** 菜单与表都已存在，直接看 §3。

### §2-C 已有库，但从未装过 AI 功能

**手动执行一次**（`/docker-entrypoint-initdb.d` 只在数据目录为空时自动执行，
已有数据的库不会重跑 init 目录，所以必须手工执行这一条）：

```bash
docker exec -i txgy-mysql mysql -uroot -proot ruoyi-vue-pro --default-character-set=utf8mb4 \
  < deploy/mysql/init/0080-wms-ai-service.sql
```

Windows / 不方便用重定向时，用文件拷贝方式：

```powershell
docker cp deploy/mysql/init/0080-wms-ai-service.sql txgy-mysql:/tmp/0080.sql
docker exec txgy-mysql sh -c "mysql -uroot -proot ruoyi-vue-pro --default-character-set=utf8mb4 < /tmp/0080.sql"
```

**该脚本做什么**（与具体自增 ID 无关，可重复执行）：

1. 建表 `wms_ai_config`（AI 助手选模型用）
2. 建表 `ai_chat_role_permission`、`ai_chat_role_user`（AI 权限体系）
3. 建「AI 客服管理」菜单 + 6 个子页面 + 2 个按钮权限
4. 授权给「已拥有 `ai:model:query` 的角色」（各租户分别处理）
5. 把新菜单追加进「各租户当前所用套餐」（否则授权了也看不见菜单）

**不会删除任何既有数据、既有菜单或既有授权。**

**验证是否成功**（脚本末尾会打印，也可单独查）：

```sql
SELECT '父菜单' AS item, CAST(id AS CHAR) AS value FROM `ruoyi-vue-pro`.system_menu
  WHERE deleted = b'0' AND path = 'ai-service';
```

查到一行即为成功。

---

## §3 配置 API Key 与模型（界面操作，必需）

> 数据库就绪后，菜单里能看到「智慧仓储系统 → AI 客服管理」。
> **如果没有 Key，AI 助手仍不可用** —— 这一步不可省。

### §3.1 新增 API 密钥

```
智慧仓储系统 → AI 客服管理 → API 密钥 → 新增 API 密钥
    所属平台：选择你的厂商（DeepSeek / 字节豆包 / 通义千问 / 硅基流动 / OpenAI 等）
    名称：随意，如「我的 DeepSeek」
    密钥：粘贴你的 API Key
    自定义 API URL：留空即用厂商官方地址（走第三方中转时才填）
    → 保存
```

### §3.2 新增模型

```
智慧仓储系统 → AI 客服管理 → 模型配置 → 新增模型
    模型名（模型标识）：如 deepseek-chat
    平台：与上一步一致
    绑定密钥：选择 §3.1 建的密钥
    → 保存
```

> 常见模型标识：`deepseek-chat`、`deepseek-reasoner`、
> `doubao-seed-2-1-pro-260915`、`qwen-plus`、`deepseek-ai/DeepSeek-V3`、`gpt-4o-mini`

### §3.3 选中并测试

```
智慧仓储系统 → AI 客服管理 → AI 助手配置
    使用的模型：选中 §3.2 建的模型（下拉里会标注「当前生效」）
    → 点「测试连通性」   ← 必须通过，失败会显示具体原因（HTTP 状态 + 响应体）
    → 保存
```

**保存后立即生效，无需重启服务。**

---

## §4 验收

打开「智慧仓储系统 → 仓储首页」，输入框出现即为部署成功。可直接试：

```
商品分类有哪些
有哪些仓库
采购入库 岩棉板 20 件 到 金岩原辅材料库
```

出现真实数据即通过。

---

## §5 排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 看不到「AI 客服管理」菜单 | 库未初始化，或菜单未加进租户套餐 | 重跑 §2-C；若仍无，检查该租户套餐的菜单是否包含 WMS 根菜单 |
| 配置页打开报错 / 500 | `wms_ai_config` 表不存在 | 重跑 §2-C |
| 助手回复「系统尚未配置大模型 API Key」 | 未配 Key，或界面配置未启用 | 走 §3；确认「AI 助手配置」里已选中模型并保存 |
| 助手回复「调用失败（HTTP 403）… AccountOverdue」 | 厂商账号欠费 | 去厂商控制台充值，或改用其他厂商 |
| 回复为空 | 模型返回非 JSON 且无本地兜底命中 | 看 `docker logs txgy-wms` 中 `[chat] 大模型调用失败` 一行 |
| 切换厂商后仍报旧厂商的错 | 旧版本意图路由只读环境变量 | 确认代码已更新到含 `resolveEffectiveRuntime()` 的版本 |
| 图片识别不可用 | 所选模型不支持视觉（如 DeepSeek 系列） | 换支持多模态的模型（如豆包），或只用文字交互 |

**查看日志**：

```bash
docker logs --tail 200 txgy-wms
docker logs --tail 200 txgy-ai
```

---

## §6 环境变量（可选，非必需）

不配任何环境变量也能用（界面配置优先于环境变量）。仅在「不想用界面配置」时才需要：

| 变量 | 说明 |
|---|---|
| `TXGY_AI_SILICONFLOW_API_KEY` | API Key |
| `TXGY_AI_SILICONFLOW_BASE_URL` | 调用地址（OpenAI 兼容的 `/chat/completions` 完整地址） |
| `TXGY_AI_SILICONFLOW_MODEL` | 文本模型标识 |
| `TXGY_AI_SILICONFLOW_VISION_MODEL` | 视觉模型标识（图片识别用） |
| `TXGY_AI_SILICONFLOW_THINKING_TYPE` | 传 `disabled` 可关闭思考链，显著降低延迟 |
| `TXGY_AI_SILICONFLOW_TIMEOUT_SECONDS` | 调用超时（秒），默认 240 |

**配置优先级**：界面配置（`wms_ai_config` + `ai_model` + `ai_api_key`）→ 环境变量 → 不可用。
