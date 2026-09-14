# 外部公告发布时间（publishTime）数据失真——实测证据与后端修复建议（2026-09-09）

> 性质：针对“外部公告审核管理（noticeExternal）发布时间列数据不规范、除日期外时分秒全 0 / 抓取时间冒充发布时间”的**实测闭环与修复动作单**。
> 触发：用户核对外部公告审核管理数据时指出“没有精确到毫秒、除了日期显示的都是 0，意味着并没有成功解析到发布时间的字段”。
> 方法：取真实线上数据（48080 公开列表）逐条解码 + 抓取外站原文核对 + 通读采集→入库→审核展示全链源码定位。每条均附文件:行号证据。
> 与本目录《后端审查-2026-09-09-外部公告域逻辑bug与风险清单.md》关系：§A5、§C8 已登记同根问题；本文补**线上数据实证**，并新增两条该清单未收录的根因（R3 QYZB JSON dateOf 截断毫秒、R4 AI 抽取不回填 publish_time）。
> 代码根目录（下文相对路径以此为基）：`txgy-module-bidding/txgy-module-bidding-server/src/main/java/cn/iocoder/txgy/module/bidding/`

## 一、现象：线上 publishTime 数据形态（全部失真）

接口 `GET /admin-api/bidding/notice-external/page?pageNo=1&pageSize=8`（无鉴权可复现），取前 8 行：

| 来源站 | 标题前缀 | publishTime 返回 | 解码后 |
| --- | --- | --- | --- |
| QYZB | 陕西煤业化工集团…2027年度集中采购 ×3 | 1788710400000 | 2026-09-07 00:00:00.000（时分秒毫秒全 0）|
| QYZB | 陕煤黄陵店头电厂三期… ×2 | 1788710400000 | 2026-09-07 00:00:00.000（同上）|
| EBIDDING | 陕西液化天然气…杨凌LNG工厂 ×2 | 1788668723000 | 2026-09-06 12:25:23.000 |
| EBIDDING | 炼化公司…2026年装置大修消缺… | 1788668722000 | 2026-09-06 12:25:22.000 |

- **QYZB 行**：只解析到日期（00:00:00），"发布到秒/毫秒"的目标完全没达到。
- **EBIDDING 行**：12:25:22 与 12:25:23 **相差 1 秒 = 同一批抓取的 `LocalDateTime.now()`**，不是公告发布时间（抓取时刻冒充发布时刻）。

### 外站原文实证（判断“有没有可解析的精确时间”）

- QYZB 详情页（`https://qyzb.shccmg.com/cms/default/webfile/3ywgg/20260907/1281690017894236160.html`，抓取 200）：
  - 页面明示仅 `发布时间：2026-09-07`（**外站详情页自身只显示日期**）；
  - 页面注释 `<!-- <span>2026-09-07 18:58:33</span> -->` 为模板注入的“页面生成时间”，**不是公告发布时间，不可采用**；
  - 但 QYZB **栏目 JSON 接口**（`QyzJsonListParser` 头注）行含 `publishDate`，格式 `2026-08-27T14:49:07.000+0800`——**毫秒级精确时间明明存在，入库时被截断成日期**。
- EBIDDING 详情页 curl 返回 404（`?doc=DYZB2025-11-G141-H`），无法直接核对页面；但其解析器为 v0 通用抽取，只扫正文前 800 字符，命中概率低（见 R2）。

## 二、根因链定位（4 处，含 2 条新发现）

### R1【与 C8 同根，附实证】解析器只取“窗口内第一个日期”且允许无时分秒 → 00:00:00
- 证据：`service/noticeexternal/NoticePublishTimeResolver.java:24-33,70-83`（正则 `(?:[ T]HH:mm(:ss))?` 时间部分可选；正文取前 HEAD_CHARS=800 字符内第一个合理过去时间；`toDateTime` 无时分时归 0）。
- 问题：命中“2026-09-07”这类纯日期 → 00:00:00.000；且“第一个合理过去时间”常把正文前部的项目起止/登记日期当发布时间（§C8）。
- 实证：QYZB 5 行全为 2026-09-07 00:00:00.000，与页面只显示日期吻合——**页面只有日期时 00:00:00 是“如实”的，真正的精确时间在栏目 JSON 接口（见 R3），链路没用它**。

### R2【同 A5 根因】EBIDDING 专用解析器是 v0 骨架 + 入库兜底 now() → 抓取时间冒充发布时间
- 证据：`service/externalfetch/parser/EbidNoticeParser.java:41-66`（v0 骨架仅委托通用抽取，自注“规则待真实样本校准”）；`HtmlNoticeExtractor.java:71`（只 `NoticePublishTimeResolver.resolveFromText` 一次）；入库兜底 `service/noticeexternal/ingest/ExternalNoticeIngestServiceImpl.java:372-379`（`resolvePublishTime`：null 或 <2000 年才解析原文，**解析不到直接 `LocalDateTime.now()`**）。
- 问题：ebidding 详情页发布时间不在正文前 800 字符或形态不匹配 → 解析 null → now() 兜底 → 线上 12:25:22/23 即此。§A5（runner:203,290 预填 now）与本文 ingest:378 兜底是两条 now() 注入点，叠加后列表时间几乎不可信。

### R3【新】QYZB 栏目 JSON 的 publishDate 含毫秒+时区，dateOf() 主动截成日期
- 证据：`service/externalfetch/parser/QyzJsonListParser.java:61,90-95`（`entry.setDate(dateOf(...))`；`dateOf` 注释自述 `publishDate（2026-08-27T14:49:07.000+0800）→ yyyy-MM-dd`，实现 `substring(0,10)`）。
- 问题：**权威精确发布时间在采集源 JSON 里现成可用，却被字符串截断丢弃**——这是“明明能精确到毫秒却只留日期”最直接的一处。
- 建议：解析 `publishDate` 全量（处理 `T…+0800` 时区）→ `LocalDateTime` 填入条目，替代/高于详情页日期级解析。

### R4【新】AI 结构化抽取出的 publishTime 从不回填 publish_time 列
- 证据：`service/noticeexternal/ai/NoticeExternalAiExtractServiceImpl.java:83-115`（SUCCESS 分支只回写 `industryFlag/industryConfidence` 到 DO，其余字段（含可能的 publishTime）落 `ai_meta_json`）；AI schema 见 `ai/AiChatNoticeExtractor.java:60-70`（SYSTEM_PROMPT 输出字段）。
- 问题：审核页手动触发 AI 抽取后，抽到的精确发布时间停留在 ai_meta_json，列表/详情 publishTime 永不更新 → 审核员看到的仍是抓取/日期值。
- 建议：AI 结果含发布时间且主档 publishTime 为空/仅为日期级时，回填 `publish_time`（注意与 §C1 并发回写风险同源——回填应限定单字段 updateById，勿整实体回写）。

### R5（连带，非根因）展示/排序层无校正
- 证据：`service/noticeexternal/NoticeExternalServiceImpl.java:186-191,223-229`（列表 `orderByDesc(publishTime)`）、`:274,351,401,455`（4 个 RespVO 原样 `setPublishTime`）。
- 影响：大量 00:00:00 与 now() 值参与倒序 → 列表排序失真；前端（noticeExternal/index.vue fmtT）只如实渲染后端字段，**前端无法自愈**（不臆造时间）。

## 三、修复建议（后端，按优先级）

| # | 动作 | 位置 | 说明 |
| --- | --- | --- | --- |
| P1 | 删除/冻结两条 `LocalDateTime.now()` 兜底注入 | `ExternalNoticeIngestServiceImpl.resolvePublishTime`、`CrawlSourceRunner:203,290`（§A5） | 解析不到就置 null 并进 warnings，宁可“时间未知”也不准用抓取时间冒充；列表空时间前端显示“—” |
| P2 | QYZB JSON publishDate 解析到全量时间（含毫秒/时区换算） | `QyzJsonListParser.dateOf` → 时间解析器 | 采集源接口带精确时间时必须保留（改后需存量重采或回填脚本校正历史行） |
| P3 | AI 抽取结果含 publishTime 时单字段回填 `publish_time` | `NoticeExternalAiExtractServiceImpl`（extract SUCCESS 分支） | 幂等：仅当主档时间为空/00:00:00 或早于 AI 时间才覆盖；单字段 update 防 §C1 并发覆盖 |
| P4 | 解析器收紧：锚定“发布/公告/公示日期”等关键词行、扩正文扫描窗（不再只前 800 字符） | `NoticePublishTimeResolver`（与 §C8 合并修） | 减少把起止日期/历史日当发布时间 |
| P5 | 存量脏数据回填 | 后端一次性脚本 | 依据 extra_raw/meta_json/ai_meta_json 中的可靠时间源回填并审计留痕 |

## 附：复现与验证

- 数据：`curl "http://localhost:48080/admin-api/bidding/notice-external/page?pageNo=1&pageSize=8"` → 观察 `publishTime`（epoch 毫秒）。
- QYZB 原文：`https://qyzb.shccmg.com/cms/default/webfile/3ywgg/20260907/1281690017894236160.html`（页面仅日期）。
- 本文与《后端审查-09-09》§A5/§C8 建议合并实施（A5 已列 P1 修复路径）。
