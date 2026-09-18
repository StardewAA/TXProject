# 爬虫契约对照与批次落库（2026-09-08 实现）

> 依据：前端 apps/web-antd/src/api/bidding/crawlExternal/index.ts（爬虫模块契约）逐项核对后端控制器，
> 并补齐契约页面所依赖但此前未接线的数据（采集批次/任务归属）。

## 一、契约端点 与 后端实现对照（本轮核对全部命中）

| 契约（前端） | 后端端点 | 实现 |
| --- | --- | --- |
| fetchCrawlSourcePage/get | GET /bidding/crawl-source/page、/get | 已有（bidding:crawl-source:query） |
| createCrawlSource | POST /bidding/crawl-source/create | 已有 |
| updateCrawlSource | PUT /bidding/crawl-source/update | 已有（sourceCode 不可改） |
| setCrawlSourceEnabled | POST /bidding/crawl-source/enabled?id=&enabled= | 已有（停用先守卫：未终态任务/ACTIVE 公告） |
| deleteCrawlSource | DELETE /bidding/crawl-source/delete | 已有（物理删配置行，行级快照保留） |
| fetchCrawlParserList | GET /bidding/crawl-parser/list | 已有 |
| crawlParserTest | POST /bidding/crawl-parser/test（multipart HTML/JSON/FILE） | 已有 |
| runCrawlSource | POST /bidding/crawl-source/run?sourceCode= | 已有（本轮升级见下） |
| fetchCrawlTaskPage/get、retryCrawlTask | /bidding/crawl-task/{page,get,retry} | 已有 |
| fetchCrawlJobPage/get | /bidding/crawl-job/{page,get} | 已有（本轮补写入方） |

## 二、本轮补齐的漏掉功能（批次落库）

问题：采集批次表 bidding_crawl_job 只有 page/get 查询，下载器执行流程从不写行 → 前端批次页永远空、
失败任务也无批次归属。

改动：
1. CrawlSourceRunner：新增 runSource(sourceCode, jobId) 重载（旧单参签名保留委托），失败任务
   recordTask 落库时写入 jobId，任务与批次可关联（前端任务页支持 jobId 过滤）。
2. CrawlRunService/Impl：runNow 升级为两次执行——人工 MANUAL 与调度 SCHEDULE 均落批次：
   - 开跑先插 RUNNING；
   - 结束按汇总转 SUCCESS（无失败）/ PARTIAL（有失败也有成功）/ FAILED（全失败）；
   - summaryJson 记 totalFetched/totalIngested/totalFailed/categories 明细；异常置 FAILED 后上抛。
3. 定时调度改走 runNow(code, SCHEDULE)，获得与人工一致的口径。

测试：新增 CrawlRunServiceImplTest（成功 PARTIAL/SCHEDULE SUCCESS/异常 FAILED 三路径 + jobId 透传），
runner 新增批次归属用例；相关 10 例全绿，全量回归见提交信息。

## 三、后续接口设计（未在契约中，前端如需再做）

1. 批次详情下钻任务：前端已具备 jobId 过滤参数，无需新端点；页面从 job 详情携带 jobId 调 crawl-task/page 即可。
2. 批次运行中进度：当前 run 为同步长跑。若前端需要轮询进度，可新增 GET /bidding/crawl-job/running（源维度）；P0 不需要，同步返回即可。
3. 失败任务批量重试：retry 单条已有；批量留 P2（POST /bidding/crawl-task/retry-batch?ids=）。
4. 调度状态持久化：lastRunAt 目前进程内，重启可能重复触发（频控按 >=120s 兜底，业务无害）；P2 迁 DB。
5. 国信 PDF 附件 fileId 回填：解析链路已留 fileId 位，接入 FileApi 属 P2。

