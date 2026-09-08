# 外部公告 PDF 归档 / 源站跳转 / 采集窗口 契约补充（04）

> 对应后端改动批次：2026-09-10（PDF 归档回填 + pdf_download_url 列 + 采集首跑全量/增量分离）。
> 本文补 01-接口契约.md 未覆盖的三个新语义；01/00 仍为准，冲突以本文+OpenAPI 最新为准。

## 1. PDF 公告的预览 / 下载（不再依赖 pdf_file_id）

### 背景
- 后端爬取 PDF 后，通过平台文件服务（infra-file）上传归档，返回**可访问 URL**；该 URL 落库到新列 `pdf_download_url`。
- 平台 RPC `FileApi.createFile` **只返回访问 URL，不返回 Long fileId**（fileId 仅"前端直传登记"模式有），所以 `pdf_file_id` 对外部采集公告**恒为 NULL**——前端**不要**再以 `pdf_file_id` 判断有没有 PDF、也不要用它拼下载地址。
- 权威判断：`content_type == 'PDF'`；文件访问一律用 `pdf_download_url`。

### detail（公开）/ manage/get（管理）新增字段
| 字段 | 类型 | 说明 |
|---|---|---|
| `pdfDownloadUrl` | string? | PDF 文件访问地址（平台文件服务 URL，可浏览器直接打开/下载；本地存储下免签） |
| `pdfFileId` | long? | 恒 NULL（见上），保留兼容勿用 |
| `pdfSha256` / `pdfSize` | string/long | 哈希/大小，完整性展示 |
| `origUrl` | string | 外站**详情壳页**（注意：不是 PDF 本身；`pdfDownloadUrl` 才是 PDF） |

### 前端渲染建议（PDF 形态）
- 预览/内嵌查看器：`<iframe src=pdfDownloadUrl>` 或 PDF.js 加载 `pdfDownloadUrl`。
- 下载按钮：直接 `window.open(pdfDownloadUrl)` 或 a[href]（后端已按 attachment 头返回文件名）。
- 显示"原始出处"时跳**源站壳页**：必须走 `GET /bidding/notice-external/go?url=<encodeURIComponent(origUrl)>`（见下），**不要直开 origUrl**。
- 后端兼容：老数据（本列 NULL）正在回填；回填完成前前端应隐藏"预览/下载"按钮并显示"源文件整理中"，**不要**因 pdfDownloadUrl 为空就报错或直开 origUrl。

### 存量回填
- 管理端已提供人工触发：`POST /bidding/notice-external/backfill-pdf-urls`（权限 bidding:notice-external:update）→ 返回 `total=..,ok=..,failed=..,skipped=..`。
- 回填过程会重下载外站 PDF 再上传，14 条 GX 数据约几十秒内完成；期间 detail 的 pdfDownloadUrl 由空变有。

## 2. 源站跳转必须走 /go 中转（防开放重定向）

- 端点：`GET /bidding/notice-external/go?url=<url>`（**公开免登 @PermitAll**，@TenantIgnore）→ 302 Location 到目标。
- 守卫：目标必须 http(s) 且域名 ∈ **启用采集源**的 base_url 域名（含子域名）。若源在 `bidding_fetch_source` 里 `enabled=0`，或域名未配/换域名，跳转会 400 拒绝。
- 前端约定：**所有外链（origUrl、附件 origDownloadUrl）一律拼 /go 中转**；不要把原始 URL 直接塞 window.open / a[href]。
- 域名白名单配在哪：`bidding_fetch_source.base_url`（源启用时生效）。排查"跳不了"先看：a) 源是否 enabled=1；b) base_url 域名与实际公告域名一致（如 gx.e-bidding.org / qyzb.shccmg.com 各是各的源）。

## 3. 采集窗口：首跑全量（可配天数）→ 之后增量（同一接口，自动判定）

### 语义（2026-09-09 用户口径定版）
- **只有一个入口**：`POST /bidding/crawl-source/run?sourceCode=xxx`（人工）与调度器 tick 走**同一条** `runNow` 逻辑。
- **自动冷/热判定**：该源在 `bidding_fetch_job` 从未有过 `SUCCESS` 批次（=首次爬取，调度器无"上次爬到哪里"断点）→ 本次按**全量窗口**回溯补历史；一旦有 SUCCESS → 之后每次都按**增量小窗口**跑。
- 增量跑完会续写 SUCCESS（每轮都写），所以"跑了一次就转增量"：想再补历史只能等下次该源掉历史（不会自动发生），故首次全量务必让窗口覆盖到位。

### 窗口天数怎么配（都在 `entry_urls_json` 栏目级或全局配置）
- 栏目级字段：`lookbackDays`（增量窗口：每次从今天往前补这么多天，默认不启用=只抓最新页）；`fullLookbackDays`（首跑全量窗口：本次首跑回溯天数，缺省回退全局 `bidding.crawl.runner.full-lookback-days`，再缺省 60）。
- 首跑时若栏目只配了 `lookbackDays`（如 1），**不会**自动把 60 天当成全量——全量天数请显式写 `fullLookbackDays: 60`（或配全局 full-lookback-days）。
- 页限速（防封）：翻页间隔 5s（`bidding.crawl.runner.page-delay-ms`）、条目间隔 1.5s（`entry-delay-ms`），均已在配置文件默认，前端无需关心；但**不要**用自动化脚本高频调 run。

### 崩溃自愈（无需前端感知，记录备查）
- 进程崩溃遗留 RUNNING 批次：服务启动时统一置 FAILED（记 interrupted）；因该源仍无 SUCCESS → 下次 tick/run 自动按全量窗口**重跑**（幂等：已入库的 origUrlHash 跳过，只补缺口）。
- 并发防重：同源已有 RUNNING（含多实例）时，新 run/tick 直接跳过返回空汇总，不会重复投递。

## 4. 涉及端点速查

| 端点 | 鉴权 | 说明 |
|---|---|---|
| `GET /bidding/notice-external/detail?id=` | @PermitAll | 公开详情（含新 pdfDownloadUrl） |
| `GET /bidding/notice-external/manage/get?id=` | bidding:notice-external:update | 管理详情（含 pdfDownloadUrl） |
| `GET /bidding/notice-external/go?url=` | @PermitAll | 源站/附件中转跳转（唯一合法外链出口） |
| `POST /bidding/notice-external/backfill-pdf-urls` | bidding:notice-external:update | 存量 PDF 归档回填（人工触发一次即可） |
| `POST /bidding/crawl-source/run?sourceCode=` | bidding:crawl-source:update | 立即采集一轮（首跑自动全量/之后增量） |

## 5. 变更记录
- 2026-09-10：新增 pdf_download_url 列与 detail/manage 下发；新增 backfill 端点；go 端点语义不变但强调前端必走；run 语义补充首跑全量（fullLookbackDays，默认 60 天）→ 增量自动判定。