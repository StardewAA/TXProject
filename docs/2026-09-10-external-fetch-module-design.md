# 外部信息获取模块设计方案（v1.0 定稿 2026-09-10）

> 状态：方案定稿（2026-09-10 用户口径汇总）。代码按本文落地；此前 docs/2026-09-10-crawl-async-queue-design.md
> 为过渡草案，与本文冲突处以本文为准。
> 本文回答三个问题：模块怎么改、外部公告的"必填"尺度、API 与爬虫怎么对接。

---

## 一、模块定位与更名

爬虫模块 → **外部信息获取模块**（对外部网站/公共服务平台信息的统一获取与结构化）：

| 项 | 更名 | 说明 |
|---|---|---|
| 语义 | 爬虫 → 外部信息获取 | 获取方式支持多种：API 对接、爬虫、后续可加 |
| 包 | crawlexternal → externalfetch | 物理移动 |
| 表 | bidding_crawl_source/job/task → bidding_fetch_source/job/task | 物理更名 |
| 任务中文名 | 采集任务 → **信息获取任务** | 管理端文案/注释 |
| URL/权限码 | /bidding/crawl-*、bidding:crawl-* **保留** | 前端契约只读不动，后端兼容 |

> 更名范围=代码包+表+中文名；对外 URL 与权限码不变（前端无需改）。

---

## 二、外部公告"必填"尺度（回答：28 必填对外部是否苛刻）

**结论：苛刻，外部公告不套内部公告的 28 必填。**

事实核查（代码证据）：
- 内部公告 NoticePayloadUtils.REQUIRED_BY_TYPE：TENDER_NOTICE **28 项必填**，NoticeServiceImpl.create/update 里 checkRequired **硬校验**（缺项拒绝落库）。
- 必填字段是《全国公共资源交易数据交换标准》字段（TENDER_PROJECT_CODE/UNIFIED_DEAL_CODES/PUB_SERVICE_PLAT_CODE…），因为内部公告是企业**主动发布并对外上报公示**（T 轨），法规要求模板齐全。
- 外部公告是**聚合记录别家平台的信息**（G 轨），源站给什么有什么：DDL 本身只把 source_code/title/orig_url/orig_url_hash/content_type 定为 NOT NULL（采集基线），**其余全可空**；meta_json 兜底不丢字段。

**外部公告字段策略（v1.0 定稿）：**
1. 采集基线必填（入库硬校验）：标题、来源、原 URL、原文/源文件、抓取时间；
2. **其余结构化字段：AI 尽力填**——抽到写列（如 issuer/projectCode/publishTime），抽不到 NULL；
3. **AI 只出结构化字段，不取正文**：正文/源文件由下载链路保存（FileApi 源文件对象），AI 的正文输入是"截取的纯文本"，输出仅结构化 JSON；**不要求 AI 转写/回传正文全文**；
4. 展示：有结构化字段展示字段，无则回退原文（净化 HTML）/PDF 预览，不因缺字段拒绝入库。

---

## 三、AI 结构化（平台管理员 AI + 可调温度）

- 复用现有通道：AiChatNoticeExtractor（ai-server chat REST，租户跟随 + 免鉴权本地 mock 联调），bidding.ai.enabled=true 生效；
- 正文输入：HTML 用 jsoup 截正文纯文本；PDF 用 PDFBox 文字层文本（无文字层的扫描件标记 SKIP 或走多模态 URL，本期 SKIP 留待 P2）；
- **提示词要求（本轮重点修订）**：
  1. **行业判定口径必须明确**：提示词要显式定义"管道防腐保温行业"边界——凡招标内容涉及
     **管道/储罐/装置/设备及其附属设施的防腐蚀、保温、保冷、隔热**（施工/维修/材料/检测/验收等）
     → BELONGS；明显无关 → NOT_BELONGS；信息不足 → UNCERTAIN（含置信度与命中关键词）；
  2. **只输出结构化字段 JSON，不输出/不转写正文全文**（省 token、防膨胀）；
  3. 结构化字段与 notice-external 主档列对齐（title/projectCode/tenderProjectCode/bidSectionCodes/issuerName/publishTime/金额/联系方式等），缺失给 null 不编造；
- **temperature 可配且需联调**：bidding.ai.temperature（默认按模型建议，AI 输出 md 代码块倾向与温度相关，配置化便于多次调试；解析侧剥 markdown fence 已有）；
- 输出落 ai_meta_json（结构化 JSON 全集留档）+ 映射主档可空列；industryFlag/Confidence 已有列直接写。

---

## 四、API 源与爬虫源（样板设计，参考公共服务平台通行定义）

### 4.1 调研：公共服务平台 API 通行形态（真实样例）

| 平台 | 形态 | 要点 |
|---|---|---|
| 山东公共数据开放网（烟台招投标） | REST + JSON | 接口地址 + 头鉴权：X-Client-Id / X-Timestamp(ms) / X-Nonce / X-Signature(HmacSHA256(ClientId+Timestamp+Nonce), Base64)；返回字段即全国交换标准（tender_project_code/pub_service_plat_code/bulletin_duty/data_timestamp…） |
| 金华公共资源交易（浙江政务数据） | 数据服务接口 | 平台化 API 目录 + 申请令牌 |
| 国家/地方公共资源交易平台 | 数据交换标准 | 字段名全国统一（28 必填同源），列表+详情/附件结构 |

通用结论（样板据此设计）：
1. 地址形态：GET https://<host>/gateway/api/<version>/<resource>，参数 query 或 POST JSON；
2. 鉴权：令牌头（Client-Id/Signature/Timestamp/Nonce）或简单 token——**必须配置化，不能写死**；
3. 分页：pageNo/pageSize 或 page/rows，响应带 total；
4. 响应信封：{code, msg, data:{rows:[...]|list:[...], total}}（不同平台键名不同，须可映射）；
5. 公告字段：多数按全国交换标准命名（snake_case），与内部数据集同源 → 天然可映射到统一列。

### 4.2 源配置扩展（fetch_type + api_config_json）

现有 bidding_fetch_source（原 crawl_source）字段已够承载，**不新增表**，加 1~2 列：

```sql
ALTER TABLE bidding_fetch_source
    ADD COLUMN fetch_type varchar(16) NOT NULL DEFAULT 'CRAWLER'
        COMMENT '获取方式：CRAWLER=爬虫(列表HTML) / API=接口直取(JSON)' AFTER channel_code,
    ADD COLUMN api_config_json json NULL
        COMMENT 'API 源配置：{method,url,headers,params,page:{pageNo/pageSize键,start},rowsPath,fieldMapping,envelope:{code,data}}'
        AFTER fetch_type;
```

- fetch_type=CRAWLER：现 QYZB/国信走的路（**不破坏现有**）——入口 URL 列表 + 解析器逐条抓；
- fetch_type=API：新样板（4.3）——声明接口、鉴权、分页、字段映射后**直取 JSON 入库**；
- 迁移：现有行 UPDATE 默认 CRAWLER，SQL 脚本里给。

### 4.3 API 源样板：QYZB 做成"API 对接样板"

QYZB 现状是"代码内嵌 POST 常量 + 固定 JSON 路径"，本轮把它**升级为配置驱动样板**：

```jsonc
// bidding_fetch_source.api_config_json 示例（QYZB 3 栏目之一）
{
  "method": "POST",
  "url": "https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage",
  "contentType": "application/json",
  "headers": {},
  "page": { "param": { "pageNo": "pageNo", "pageSize": "pageSize" }, "start": 1, "sizeDefault": 10, "sizeMax": 100 },
  "rowsPath": "res.rows",
  "totalPath": "res.total",
  "envelope": { "code": "code", "data": "res" },
  "fieldMapping": {
    "title": "title",
    "url": "url",
    "date": "publishDate",
    "category": "categoryName",
    "contentHtml": "text"
  }
}
```

- 列表页带全文（rows.text）→ contentHtml 映射直取 → 落源文件（FileApi）→ AI 结构化；
- 不带全文 → 每条详情再取（详情 URL 模板 / 详情字段映射，同表扩展）；
- **QYZB 三个栏目 = 三条 source 配置行**（fetch_type=API），原硬编码常量退役；
- API 通用化后，新增公共服务平台源只需管理端配一行，**零代码**（字段映射/分页/鉴权全配置）。

---

## 五、任务不丢（保留现有机制 + 轻量 Redis）

按用户定稿：**保留消息队列语义但用轻量 Redis（Redis Stream），不引重型 MQ**：

1. **MySQL bidding_fetch_task = 持久队列 + 断点**：每条 URL/API 条目一个 PENDING 行
   （唯一键 job+url_hash+source_code 防重；payload_json 轻量快照，不含大文本）；
2. **Redis Stream 轻信号**：消息仅 taskId/jobId（<200B），唤醒下载器；ACK 前不丢，
   进程崩溃由框架 RedisPendingMessageResendJob 5 分钟超时重投；
3. Redis 全丢/重启：DB task 还在 → 启动自愈扫 PENDING/RUNNING 重建消息（幂等，不重排链表）；
4. 指数退避重试 ≤3 → FAILED 死信转人工审核（CrawlTaskService 状态机接线，复用）；
5. 原文不占 Redis：HTML/PDF 一律 FileApi 归档（DB 只存引用+hash+size）。

---

## 六、任务与批次语义（更名后不变的核心）

- job（信息获取批次）：runNow/调度 → RUNNING → task 全终态后 SUCCESS/PARTIAL/FAILED + summary；
- task（信息获取任务）：PENDING→RUNNING→SUCCESS/FAILED/SKIPPED，workerId 归属，nextRetryTime 退避；
- 崩溃自愈：启动把孤儿 RUNNING job/task 按 §五 恢复；
- 频控保持：fetch_interval_seconds ≥120s（QYZB/GX=300s），pageDelay/entryDelay 防封不变。

---

## 七、实施顺序（评审后开工）

| 阶段 | 内容 | 验证 |
|---|---|---|
| 1 | 更名：包 crawlexternal→externalfetch、表 crawl_*→fetch_*、任务中文名"信息获取任务"（URL 权限码不动） | 全量回归绿 |
| 2 | 源配置：fetch_type + api_config_json 列；QYZB 升级为 API 配置驱动样板（3 栏目 3 行） | QYZB 真跑与现状等价 |
| 3 | AI 结构化：AiChatNoticeExtractor 提示词修订（行业口径明示 + 只出字段不出正文）+ temperature 配置化 | 单测 + 真实调用（mock 或 ai-server） |
| 4 | 异步接线：Redis Stream 轻信号 + task 状态机 + 启动自愈（§五） | kill 进程模拟崩溃续跑 |
| 5 | 文档 + commit（不含用户并行文件） | 中文汇报含 hash |

## 八、风险与对策

- 更名波及：git 友好（包移动+批量替换）；测试基线 293 例同步更新；
- API 字段异构：fieldMapping 配置化 + meta_json 兜底不丢字段；
- AI 输出不稳：temperature 可配 + 剥 fence + 失败走 task 重试/人工；
- 迁移顺序：先更名回归 → 再加 API/AI，避免混杂难定位。
