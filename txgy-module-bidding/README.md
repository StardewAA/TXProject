# 招投标模块（txgy-module-bidding）

> 陕西 txgy 平台的招投标业务模块：**外部公告采集（爬虫）→ 解析入库 → 审核发布 → 检索/展示**，
> 以及项目 / 招标项目 / 标段、主体信息与授权、投标流程跟踪、备案等业务能力。
> 本仓库为**独立 git 仓库**，内嵌于平台工程 `txgycloud/txgy-module-bidding`。
>
> 技术栈：JDK 25 · Spring Boot 3 · MyBatis-Plus · Redis（缓存 + Stream 队列）· Elasticsearch 8.10 · PDFBox · MySQL 8.4

---

## 目录

1. [项目结构与技术栈](#1-项目结构与技术栈)
2. [服务清单与端口（先看这个）](#2-服务清单与端口)
3. [从零启动（首次跑通全流程）](#3-从零启动)
4. [配置方式（各服务怎么配）](#4-配置方式)
5. [爬虫：外部公告采集](#5-爬虫外部公告采集)
6. [解析器：站点规则插件化](#6-解析器站点规则插件化)
7. [公告：采集→审核→发布→检索](#7-公告采集审核发布检索)
8. [如何使用（管理端菜单与主要流程）](#8-如何使用)
9. [常用运维动作](#9-常用运维动作)
10. [故障排查（FAQ）](#10-故障排查faq)
11. [文档索引](#11-文档索引)

---

## 1. 项目结构与技术栈

```
txgycloud/txgy-module-bidding/            ← 本仓库根（独立 git）
├── txgy-module-bidding-api/              对外契约：DTO / VO / 枚举 / Feign 接口
├── txgy-module-bidding-server/           业务实现：controller / service / dal / job / mq
│   └── src/main/resources/
│       ├── application.yaml              ★ 主配置（端口、爬虫频率、ES、AI 等，随代码走）
│       ├── application-local.yaml        本地 profile：数据源、Nacos 地址、Redis
│       └── application-dev.yaml          开发环境 profile
├── sql/
│   ├── master-bidding-sync.sql           ★ 初始状态包（结构 + 最小启动数据，一键初始化）
│   ├── history/                          历史增量脚本（老库追平用）
│   ├── tools/                            校验 / 归零 / 恢复 / 初始包生成脚本
│   └── README-完整执行清单.md            SQL 索引与口径
├── docs/                                 设计、联调、运维、前端契约文档（见 §11）
├── deploy-bidding-local.override.yml     本地联调 compose override（端口映射 + 空 namespace）
└── build.cmd                             一键构建（内置 JDK/Maven 绝对路径）
```

**关键约定**

| 约定 | 说明 |
| --- | --- |
| 构建 | JDK **25**（`D:\Java\jdk-25`）+ Maven 3.9（`D:\maven\apache-maven-3.9.11`）；`build.cmd` 已写死绝对路径，不依赖 PATH |
| Mapper | `@MapperScan(annotationClass = Mapper.class)` —— **每个 Mapper 必须显式标注 `@Mapper`**，否则不注册 Bean |
| 数据库 | 库名 `ruoyi-vue-pro`（MySQL 8.4 容器）；SQL 执行统一加 `--default-character-set=utf8mb4` |
| 任务队列 | 公告原文下载走 **Redis Stream**（`CrawlNoticeFetchMessage`），非线程池直跑 |
| 检索 | 外部公告索引 `bidding-notice-external`（状态转 ACTIVE 时投递索引事件） |

---

## 2. 服务清单与端口

| 服务 / 依赖 | 宿主地址 | 运行方式 | 说明 |
| --- | --- | --- | --- |
| **bidding-server（本模块）** | `127.0.0.1:48110` | **宿主机 JVM**（不进容器） | 健康检查 `/actuator/health` |
| MySQL | `127.0.0.1:3306` | 容器 `txgy-mysql` | 库 `ruoyi-vue-pro`，root/123456 |
| Redis | `127.0.0.1:6379` | 容器 `txgy-redis` | 缓存 + Stream 队列 |
| Nacos | `127.0.0.1:8848`（gRPC 9848/9849） | 容器 `txgy-nacos-1` | 注册/配置中心，本地用 **public（namespace 留空）** |
| Gateway | `127.0.0.1:48080` | 容器 `txgy-gateway` | `/admin-api/bidding/**` 路由到本模块；**文件下载 url 也走它** |
| System | `127.0.0.1:48087`（容器内 48080） | 容器（需 override 映射） | 本模块 Feign 直连：菜单/权限/租户 |
| Infra | `127.0.0.1:48088`（容器内 48080） | 容器（需 override 映射） | 本模块 Feign 直连：文件服务（`file_id` 契约） |
| Elasticsearch | `http://127.0.0.1:9200` | 容器 `elasticsearch`（8.10.0） | 基本认证；索引 `bidding-notice-external` |
| 管理后台前端 | 另仓 `txgy-ui/txgy-ui-admin-vue3` | Node 独立启动 | 本模块只提供接口与菜单数据 |

> ⚠️ **Gateway 必须运行**：爬虫任务里的原文地址是 `http://127.0.0.1:48080/admin-api/infra/file/4/get/...`（infra 文件服务经网关），
> 网关不在时会**秒失败**并报 `HTTP 请求失败: null`（连接被拒），看起来像"站点封禁"，实则本地依赖缺失。

---

## 3. 从零启动

### 3.1 前置条件

- Docker Desktop 已启动（建议内存 ≥ 7 GB：Nacos/MySQL/ES 同时跑）；
- JDK 25、Maven 3.9（或直接用 `build.cmd`）；
- 端口 48110 未被占用（Windows 下运行中的 jar 会锁文件，重新打包前先停进程）。

### 3.2 第一步：启动中间件与兄弟服务（Docker）

在**全项目目录** `D:\Code\IDEA\txgy\txgycloud\deploy` 执行（override 文件在本模块目录）：

```bash
# 中间件（首次或未起时）
docker compose -f docker-compose.yml -p txgy up -d mysql redis nacos

# 兄弟服务（system/infra/gateway），带本模块的本地 override（空 namespace + 端口映射）
docker compose -f docker-compose.yml -f ../txgy-module-bidding/deploy-bidding-local.override.yml -p txgy up -d gateway system infra

# Elasticsearch（独立容器，不在 compose 内）
docker start elasticsearch
```

确认：

```bash
docker ps --format "{{.Names}}\t{{.Ports}}"
# 期望看到：txgy-mysql 3306 / txgy-redis 6379 / txgy-nacos-1 8848 / txgy-gateway 48080 / txgy-infra 48088 / elasticsearch 9200
```

### 3.3 第二步：初始化数据库（初始状态包）

`sql/master-bidding-sync.sql` 是**初始状态包**：21 张业务表结构 + 菜单 37 条 + 角色授权 135 条 +
采集源 3 条 + 限速闸位 1 行（**不含**任务/公告等业务数据）。幂等，可重复执行。

```bash
docker exec -i txgy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro \
  < sql/master-bidding-sync.sql
```

执行完包尾自带校验，期望输出：

| tables_ok | menus_ok | role_menus_ok | sources_ok | gate_ok |
| --- | --- | --- | --- | --- |
| 21 | 37 | 135 | 3 | 1 |

> 老库追平不要用本包，见 `sql/README-完整执行清单.md`（history 增量）。

### 3.4 第三步：启动 bidding-server

**方式 A：IDEA（推荐日常调试）**
运行 `cn.iocoder.txgy.module.bidding.BiddingServerApplication`，VM options：

```text
-Dspring.cloud.nacos.discovery.namespace=
-Dspring.cloud.nacos.config.namespace=
-Dspring.cloud.nacos.discovery.ip=host.docker.internal
-Dbidding.es.username=elastic
-Dbidding.es.password=<ES密码>
-Dspring.cloud.openfeign.client.config.system-server.url=http://127.0.0.1:48087
-Dspring.cloud.openfeign.client.config.infra-server.url=http://127.0.0.1:48088
```

**方式 B：命令行（等价，实测可用）**

```bash
cd txgy-module-bidding-server
# 首次：生成依赖 classpath（也可用 mvn dependency:build-classpath）
JAVA_HOME='D:\Java\jdk-25' mvn -o dependency:build-classpath -Dmdep.outputFile=../cp.txt -q
CP="target/classes;$(cat ../cp.txt)"
'D:\Java\jdk-25\bin\java.exe' -Xms256m -Xmx768m \
  -Dspring.cloud.nacos.discovery.namespace= \
  -Dspring.cloud.nacos.config.namespace= \
  -Dspring.cloud.nacos.discovery.ip=host.docker.internal \
  -Dbidding.es.username=elastic -Dbidding.es.password=<ES密码> \
  -Dspring.cloud.openfeign.client.config.system-server.url=http://127.0.0.1:48087 \
  -Dspring.cloud.openfeign.client.config.infra-server.url=http://127.0.0.1:48088 \
  -cp "$CP" cn.iocoder.txgy.module.bidding.BiddingServerApplication
```

**构建**：`build.cmd`（= `mvn clean install -DskipTests`，写死 JDK/Maven 绝对路径）；单测 `mvn -o test`。

### 3.5 第四步：验证

| 检查 | 命令 / 位置 | 期望 |
| --- | --- | --- |
| 健康检查 | `curl http://127.0.0.1:48110/actuator/health` | `{"status":"UP"}` |
| 启动日志 | 控制台 | `Started BiddingServerApplication in ~60s` + `[EsStartupHealthCheck][OK] ES 集群可达` |
| 接口文档 | `http://127.0.0.1:48110/swagger-ui` | 可打开，含 `bidding/crawl-*` 等分组 |
| 菜单 | 管理后台 → 招投标 | 6 个二级菜单（见 §8） |
| 爬虫 | 管理后台 → 招投标 → **信息聚合任务管理** → 爬取任务工作台 | 采集源 3 条（EBIDDING / QYZB / GX） |

---

## 4. 配置方式

### 4.1 配置分层与优先级（从低到高）

| 层级 | 位置 | 用途 |
| --- | --- | --- |
| ① 模块默认 | `txgy-module-bidding-server/src/main/resources/application.yaml` | 端口、爬虫频率、ES、AI 等**随代码走**的默认值 |
| ② 本地 profile | `application-local.yaml`（`spring.profiles.active=local`） | 数据源、Redis、Nacos 地址 |
| ③ Nacos | `bidding-server-local.yaml`（可覆盖 ①②） | 环境级覆盖，不改代码即可生效 |
| ④ 启动参数 / 环境变量 | `-Dxxx`、`ES_PASSWORD` 等 | **最高优先级**，本地联调推荐（不落库、不入 git） |

> 本地联调为什么必须把 `spring.cloud.nacos.*.namespace` 置空：容器里的 nacos 是 public 命名空间，
> 而 `application-local.yaml` 里若带 namespace，会注册到不存在的命名空间（表现为服务发现/配置拉取异常）。

### 4.2 业务配置（`bidding.*`，全部在 `application.yaml`）

**爬虫 HTTP 客户端 `bidding.crawl.http`**

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `connect-timeout-seconds` | 10 | 连接超时 |
| `read-timeout-seconds` | 20 | 单请求读超时 |
| `download-timeout-seconds` | 60 | PDF/附件下载超时 |
| `max-download-bytes` | 52428800 | 单文件上限（50 MB），超限即失败并审计 |
| `speed-warn-bps` / `speed-slow-warn-bps` | 2097152 / 2048 | 速率上下限告警（≠限速；超限记 WARN + 审计） |
| `robots-enabled` | true | 抓取前检查 robots.txt；robots 不可达时放行并记审计 |
| `robots-cache-minutes` | 360 | robots.txt 缓存时长（**404/否定结果同样缓存**，避免反复骚扰站点） |
| `user-agent` | Chrome 120 | 浏览器 UA（裸 HttpClient 会被站点拒绝） |
| `download-tmp-dir` | `./data/crawl-tmp` | PDF 落临时目录后做 PDFBox 解析 |

**爬取频率 `bidding.crawl.runner`（唯一事实源）**

| 键 | 默认 | 语义 |
| --- | --- | --- |
| `consumer-delay-ms` | **120000** | **下载"公告原文文件"的最小间隔 = 2 分钟**。实际间隔 = `max(本值, 合规下限 120s)`；由库表 `bidding_crawl_download_gate` 原子占位保证，**进程重启/多实例都不会放宽** |
| `page-delay-ms` | 60000 | 列表翻页间隔（每页之间 1 分钟） |
| `entry-delay-ms` | 1500 | 页内条目本地处理节拍（无网络下载） |
| `async-mode` | true | true = 列表只入队，原文下载由消费端按闸位逐条执行（**推荐合规形态**） |
| `full-lookback-days` / `full-max-pages` | 60 / 300 | 全量回溯窗口与最大页数 |
| `bidding.crawl.scheduler.enabled` | true | 定时调度总开关 |

**其他**

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `bidding.es.enabled` / `uris` | true / `http://127.0.0.1:9200` | 无 ES 时置 false，服务仍可启动 |
| `bidding.es.username` / `password` | `elastic` / `${ES_PASSWORD:}` | 本地用环境变量或 `-D` 注入（**口令不入库/不入 git**；见 §10 FAQ 取法） |
| `bidding.ai.enabled` | true | 关闭则 `/notice-external/ai-extract` 报"未配置"，`HTML_AI`/`PDF_AI` 解析器不可用 |
| `bidding.ai.base-url` / `admin-token` / `role-id` | 网关 / `test1` / 20 | AI 结构化抽取走平台 AI 模块（经网关） |
| `bidding.ext-notice.pdf.open-data-loader.loader-class` | `PdfBoxOpenDataLoader` | PDF 正文加载器（可替换实现） |

---

## 5. 爬虫：外部公告采集

### 5.1 全链路

```
采集源(bidding_fetch_source)          ← 站点/栏目/解析器/频率配置
   │  列表页/接口（page-delay 60s 翻页）
   ▼
采集任务(bidding_fetch_task)          ← PENDING → RUNNING → SUCCESS / FAILED / SKIPPED
   │  async-mode=true：入 Redis Stream 队列
   ▼
限速闸(bidding_crawl_download_gate)   ← 下载原文前原子占位：全局 ≥120s 才放行（2 分钟/次）
   │
   ▼
下载器(channel GENERIC → HttpFetchClient)  →  robots 检查 → UA/超时/大小上限 → SHA256 校验
   │
   ▼
解析器(parserCode)                    →  HTML_GENERIC / QYZB_JSON / GX_E_BIDDING / PDF_TEXT_GENERIC ...
   │
   ▼
入库(bidding_notice_external + _attachment + _meta) → 审核 → ACTIVE → ES 索引
   │
   └─ 审计(bidding_crawl_download_audit)：状态码/字节数/耗时/速率/SHA256/完整性/存储状态
```

### 5.2 采集源（当前 3 个，初始包已含）

| source_code | 名称 | channel | parser（默认） | fetch_type | 列表节奏 |
| --- | --- | --- | --- | --- | --- |
| `EBIDDING` | 秦源招标电子招标平台 | GENERIC | （按 payload 选） | CRAWLER | 360 s |
| `QYZB` | 秦源电子招标平台 | GENERIC | `QYZB_JSON` | API | 300 s |
| `GX` | 国信e采（山西） | GENERIC | `GX_E_BIDDING` | CRAWLER | 300 s |

- `channel_code` 走 `ChannelRegistry`（当前仅 `GENERIC` 通用 HTTP 下载通道）；
- `parser_code_default` 是**列表页**解析器；详情页可由源配置或内容类型改选（如 QYZB 详情用 `QYZB_SHCCMG`、GX 详情正文在 PDF 里用 `PDF_TEXT_GENERIC`）；
- `fetch_interval_seconds` 只约束**列表页**节奏，**不参与**原文下载闸位计算（避免两处配置互相影响）；
- `enabled` / `robots_ok` 控制是否参与调度。

### 5.3 频率与合规（这是硬要求，不是性能问题）

- **原文下载 1 次 / 120 秒（全局）**，由单行表 `bidding_crawl_download_gate` 的**条件 UPDATE 原子占位**实现，
  并发/多实例/重启均无法绕过；未到点抛 `CrawlDownloadNotDueException`（同步路径）或回 PENDING 排队（异步路径）；
- **被 robots 禁止 → SKIPPED**（`CrawlSkipException`）：不消耗间隔额度、不计失败、不进重试/DLQ；
- **触发站点反爬（429/403 等）→ 冷却 ≥600 秒**（`Retry-After` 更大时取更大值）；
- 失败重试用**任务级指数退避**：`60s × 2^attempt`（120s / 240s），最多 3 次后进 DLQ 并落审计；
- 审计表 `bidding_crawl_download_audit` 记录 `kind/http_status/byte_size/duration_ms/speed_bps/sha256/integrity_verdict/storage_status`，
  **不存 HTML 正文**（正文在 `bidding_notice_external.content_html`，PDF 在 infra 文件服务）。

### 5.4 管理端使用

| 页面 | 菜单 id | 权限码 | 能做什么 |
| --- | --- | --- | --- |
| 爬取任务工作台 | 6738 | `bidding:crawl-source:query` | 采集源增删改查、手动触发采集、查看执行情况 |
| 采集任务 | 6739 | `bidding:crawl-task:query` / `:update` | 任务分页/详情/**重试** |
| 采集批次 | 6740 | `bidding:crawl-job:query` | 批次（每轮采集）统计与明细 |

接口前缀：`/admin-api/bidding/crawl-source|/crawl-task|/crawl-job|/crawl-parser`。

---

## 6. 解析器：站点规则插件化

### 6.1 架构

- SPI：`service/externalfetch/parser/NoticeParser`（`getParserCode/getParserName/supportedPayloadTypes/parse`）；
- 注册表：`ParserRegistry` 构造注入自动收集全部解析器 Bean —— **重复 code 启动即失败**，未知 code 明确报错（防静默走错策略）；
- 下载侧对称：`DownloadChannelAdapter` + `ChannelRegistry`（按 `channel_code` 路由）。

### 6.2 已注册解析器

| parserCode | 名称 | 支持输入 | 用途 |
| --- | --- | --- | --- |
| `HTML_GENERIC` | 通用 HTML 解析器 | HTML | h1/title、正文纯文本、发布日期、附件候选、"招标人/采购人："发布单位 |
| `JSON_GENERIC` | 通用 JSON 解析器 | JSON | 键名归一（中英文/大小写/下划线别名集），支持 data/result/content 包装；未命中输出 `unmatched.*` 便于校准 |
| `PDF_TEXT_GENERIC` | PDF 文本层解析器 | PDF | PDFBox 抽正文（GX 公告正文在 PDF 内） |
| `EBIDDING` | 秦源站点解析器 | HTML | EBIDDING 详情页定向规则 |
| `QYZB_JSON` | 秦源栏目 JSON 接口解析器 | JSON | QYZB 列表（栏目接口，六栏目模板） |
| `QYZB_SHCCMG` | 秦源详情页解析器 | HTML | 导航标题/发布时间/招标编号/招标人/正文区间；支持"查看源代码"保存物自动还原 |
| `GX_E_BIDDING` | 国信 e 采（山西）解析器 | HTML | 列表 `ul.newslist` → 条目；详情 `pdf.js` → PDF 直链（正文交 `PDF_TEXT_GENERIC`） |
| `HTML_AI` | HTML + AI 结构化 | HTML | 规则截正文 → 平台 AI 抽取 + 行业相关判定（需 `bidding.ai.enabled=true`） |
| `PDF_AI` | PDF + AI 结构化 | PDF | 同上（输入为 PDF 文字层） |

### 6.3 解析器测试接口（校准站点规则用）

```bash
# 清单
curl http://127.0.0.1:48110/admin-api/bidding/crawl-parser/list

# HTML 文本测试
curl -F parserCode=HTML_GENERIC -F payloadType=HTML \
     -F "content=<h1>某项目招标公告</h1>招标人：陕西某公司" \
     -F url=https://example.com/n/1 \
     http://127.0.0.1:48110/admin-api/bidding/crawl-parser/test

# 上传文件测试
curl -F parserCode=EBIDDING -F payloadType=FILE -F fileName=page.html \
     -F file=@page.html -F url=https://www.ebidding.com.cn/notice/detail?doc=xxx \
     http://127.0.0.1:48110/admin-api/bidding/crawl-parser/test
```

校准工作流：① 浏览器"查看源代码"复制 HTML / F12 抓接口 JSON / 另存 .html 文件 →
② 先试 `HTML_GENERIC`/`JSON_GENERIC`，看 `status/warnings` 与抽取字段 →
③ JSON 键名没命中就看 `unmatched.*`，把真实键名补进别名集或新建站点专用解析器固化规则。

### 6.4 新增一个站点解析器

1. 在 `service/externalfetch/parser/` 新建类实现 `NoticeParser`，返回**唯一** `parserCode`（重复会启动失败）；
2. 添加单测（参考 `EbidNoticeParserTest` / `CalibrationSamplesTest`，用真实样本校准）；
3. 在采集源上把 `parser_code_default` 指向新 code（或让内容类型自动路由）；
4. 用 §6.3 的测试接口回归，再手动触发一次采集验证落库。

---

## 7. 公告：采集→审核→发布→检索

### 7.1 生命周期

```
采集入库 NEW(待审核) ──审核通过──▶ ACTIVE(生效中/可展示) ──超期/下线──▶ INACTIVE ──▶ ARCHIVED(只读留痕)
                     └──审核驳回──▶ REJECTED（需重新入库/重抓，不能直接改回通过）
```

- 审核字段与状态字段分离：`audit_status`（UNREVIEWED / APPROVED / REJECTED）与 `status`（NEW/ACTIVE/INACTIVE/ARCHIVED）；
- **转 ACTIVE 才投递 ES 索引事件**（索引 `bidding-notice-external`），列表/展示默认只放行 ACTIVE；
- 正文存储：`content_html`（入库清洗后正文）+ `extraction_text`（解析抽取文本）；AI 取材优先级 `extraction_text` → `Jsoup(content_html).text()`。

### 7.2 文件与 PDF 契约（重要）

- 所有上传/归档文件统一返回 **全局唯一 `file_id`**（infra 文件服务），前端凭 `file_id` 预览/下载；**不再有手工"文件 ID"表单字段**；
- 后端 URL 形态：`http://127.0.0.1:48080/admin-api/infra/file/4/get/<path>`（**必须经网关**）；
- PDF 预览：本地文件优先；无本地文件时**回退外站直链**，但同样**先过限速闸与 robots**，未到点一律 404 且不发请求；
- 归档审计：`pdf_file_id` / `pdf_download_url` / `pdf_sha256` / `pdf_size`。

### 7.3 AI 结构化抽取

- 开关 `bidding.ai.enabled`，经网关调平台 AI（`bidding.ai.base-url`），租户 1、角色 20；
- 抽取内容：标题/项目名称/发布单位/发布时间 + **行业相关判定**（`industryFlag` / `industryConfidence`）；
- 扫描件 PDF 支持全文转写（`PDF_AI`）；AI 不可用时接口报"未配置"而不是静默失败。

---

## 8. 如何使用

### 8.1 菜单入口（管理后台 → 招投标 6735）

| 一级/二级菜单 | id | 路径 | 用途 |
| --- | --- | --- | --- |
| 外部公告审核管理 | 6736 | `noticeExternal` | 外部公告审核：通过/驳回、发布到展示 |
| **信息聚合任务管理** | 6737 | `crawl` | 采集源 / 采集任务 / 采集批次（原"爬虫管理"改名，路由未变） |
| 投标流程跟踪 | 6748 | `processTrack` | 意向→报名→审核→上传标书→备案 |
| 项目与标段 | 6770 | `project` | 项目 / 招标项目 / 标段主档 |
| 公告/公示管理 | 6753 | `notice` | 本平台自有公告 |
| 主体信息与授权管理 | 6759 | `subject-auth` | 主体档案、授权、经办用户 |

> 菜单与权限由初始状态包写入（`system_menu` / `system_role_menu`）；新增菜单后需重新登录或清缓存才可见。

### 8.2 典型操作流

1. **让爬虫跑起来**：确认采集源 `enabled=true` → 等调度，或手动触发一轮：

   ```bash
   # 启停采集源（启用 = 进入外链 /go 白名单；停用须先下线其 ACTIVE 公告）
   curl -X POST "http://127.0.0.1:48110/admin-api/bidding/crawl-source/enabled?id=690010001&enabled=true"
   # 立即采集一轮（同步执行并返回汇总）
   curl -X POST "http://127.0.0.1:48110/admin-api/bidding/crawl-source/run?id=690010001"
   ```

   然后在「采集批次 / 采集任务」看进度；
2. **看下载是否合规**：查审计表（见 §9），确认相邻两次 `create_time` 间隔 ≥120 秒；
3. **校准解析**：用 §6.3 的测试接口贴真实样本，字段不对就调解析器；
4. **审核公告**：外部公告审核管理 → 通过 → 状态 ACTIVE → 前台大厅可见、ES 可检索；
5. **预览原文**：详情页 PDF 预览（本地 `file_id` 优先），未归档时会触发一次受闸位约束的抓取。

---

## 9. 常用运维动作

**查看下载审计（合规证据）**

```bash
docker exec txgy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 -t -e "
USE \`ruoyi-vue-pro\`;
SELECT create_time, download_kind, http_status, byte_size, duration_ms, ROUND(speed_bps/1024) kbps,
       integrity_verdict, storage_status
FROM bidding_crawl_download_audit ORDER BY id DESC LIMIT 20;"
```

**采集任务状态分布 / 队列健康**

```bash
# 任务分布
docker exec txgy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 -t -e "
USE \`ruoyi-vue-pro\`; SELECT status, COUNT(*) FROM bidding_fetch_task GROUP BY status;"
# 限速闸当前占位
docker exec txgy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 -t -e "
USE \`ruoyi-vue-pro\`; SELECT * FROM bidding_crawl_download_gate;"
```

**重建 ES 索引**（ACTIVE 公告逐条 PUT 覆盖，幂等补偿；权限 `bidding:notice-external:update`）：

```bash
curl -X POST http://127.0.0.1:48110/admin-api/bidding/notice-external/reindex
```

**采集域归零（破坏性，需人工确认）**：`sql/tools/reset-crawl-to-zero.sh`（需 `CONFIRM=YES`；同步清 Redis/ES）。

**SQL 自检**：`node sql/tools/validate-sql-structure.js`（括号/引号闭合、语句截断、重复建表、意外 DROP/TRUNCATE）。

**日志**：`${user.home}/logs/bidding-server.log`（`logging.file.name`）。

---

## 10. 故障排查（FAQ）

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 任务全部**秒失败**，消息 `HTTP 请求失败: null` | 网关容器没起（原文 URL 走 48080）或 infra 未起 | `docker start txgy-gateway`；确认 48088 | 
| 启动报 ES 401 / `security_exception` | 未传 ES 密码 | 传 `-Dbidding.es.password=<ES密码>` 或环境变量 `ES_PASSWORD`；口令取法：`docker inspect elasticsearch --format "{{range .Config.Env}}{{println .}}{{end}}" | grep ELASTIC` |
| `NoSuchBeanDefinitionException: xxxMapper` | Mapper 缺 `@Mapper` 注解（本模块按注解扫描） | 给 Mapper 补 `@Mapper` |
| Nacos 容器启动数分钟后退出（137 / OOMKilled） | 镜像默认堆大，容器 cgroup 限额小 | 用 `deploy-bidding-local.override.yml`（已固定 `JVM_XMS=256m/JVM_XMX=512m`） |
| 服务注册到错误命名空间 / 配置拉不到 | 本地应为 public | `-Dspring.cloud.nacos.*.namespace=`（置空） |
| 编译报找不到 `...-api` 里的类/方法 | 本地仓库里的 api 快照过期 | `mvn -o -pl txgy-module-bidding-api install -DskipTests` |
| 重新打包报文件被占用 | Windows 下运行中的 jar/进程锁 target | 先停 48110 上的进程（`Stop-Process`）再打包 |
| 下载"看起来同时在跑" | ①消费端每 30s 尝试取闸位，未到点立即回 PENDING；②失败任务的 started/finished 同秒 | 看审计表相邻 `create_time`，**间隔 ≥120s 即合规** |
| 采集结果里出现 SKIPPED | robots 禁止（规则跳过） | 正常合规行为：不占额度、不重试；如需采集需站点许可 |

---

## 11. 文档索引

**启动与运维**
- `docs/开发联调与运维/部署与启动方式.md` —— 部署细节 + 排障清单（本文的详尽版）
- `docs/开发联调与运维/爬虫测试与监控指标-2026-09-10.md` —— 实测记录、监控字段、六项合规复查结论
- `docs/开发联调与运维/下载限速与合规频控设计-2026-09-10.md` —— 限速闸设计依据
- `docs/开发联调与运维/爬虫归零重置与三下载器AI化-2026-09-10.md` —— 归零与重置流程

**爬虫与公告设计**
- `docs/模块设计与规范/爬虫解析器插件化架构与测试接口.md` —— 解析器 SPI 与测试接口
- `docs/模块设计与规范/2026-09-10-external-fetch-module-design.md` —— 采集模块设计
- `docs/模块设计与规范/外部公告采集与展示设计方案.md` —— 采集→展示全链路
- `docs/模块设计与规范/2026-09-08-crawl-contract-batch.md` —— 采集契约与批次
- `docs/开发联调与运维/公告创建接口与类型字段模板说明.md` —— 公告字段模板

**接口契约（前端对接）**
- `docs/前端规范-全域接口说明/03-本地运行与联调说明.md` —— 本地联调（48110 直连）
- `docs/前端规范-全域接口说明/04-全链路接口说明.md`、`11-文件上传与file_id契约-2026-09-10.md`
- `docs/外部公告-前端对接/` —— 外部公告接口调用说明

**SQL**
- `sql/README-完整执行清单.md` —— 初始状态包 / 历史增量 / 工具说明

---

*维护约定：配置改动优先改 `application.yaml`（唯一事实源）；SQL 改动落在 `sql/history/` 并在形成基线后更新初始状态包；本文档与代码同仓，改动请同步更新对应章节。*
