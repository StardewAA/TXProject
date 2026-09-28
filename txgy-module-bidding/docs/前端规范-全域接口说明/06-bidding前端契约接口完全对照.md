# bidding 前端契约接口完全对照（2026-09-09 定版）

> 数据源：apps/web-antd/src/api/bidding/{crawlExternal,notice,noticeExternal,subject,processTrack}/index.ts
> 后端：txgy-module-bidding-server controller/admin/*。逐条核对路径/方法/权限串/主要字段，状态 = 完全对应；
> 凡有差异均已在 2026-09-09 修复并注明。

## 1. crawlExternal（爬虫契约，批次管理）

| 前端函数 | 后端端点 | 权限 | 状态 |
| --- | --- | --- | --- |
| fetchCrawlSourcePage/get | GET /bidding/crawl-source/{page,get} | bidding:crawl-source:query | ✅ |
| createCrawlSource | POST /bidding/crawl-source/create | …:create | ✅ |
| updateCrawlSource | PUT /bidding/crawl-source/update | …:update | ✅ |
| setCrawlSourceEnabled | POST /bidding/crawl-source/enabled?id=&enabled= | …:update | ✅ |
| deleteCrawlSource | DELETE /bidding/crawl-source/delete | …:delete | ✅ |
| fetchCrawlParserList | GET /bidding/crawl-parser/list | …:query | ✅ |
| crawlParserTest | POST /bidding/crawl-parser/test(multipart) | …:update | ✅ |
| runCrawlSource | POST /bidding/crawl-source/run?sourceCode= | …:update | ✅（批次落库见 §附） |
| fetchCrawlTaskPage/get | GET /bidding/crawl-task/{page,get} | bidding:crawl-task:query | ✅ |
| retryCrawlTask | POST /bidding/crawl-task/retry | …:update | ✅ |
| fetchCrawlJobPage/get | GET /bidding/crawl-job/{page,get} | bidding:crawl-job:query | ✅ |

## 2. notice（公告/公示管理，内部）

| 前端函数 | 后端端点 | 权限 | 状态 |
| --- | --- | --- | --- |
| fetchNoticePage | GET /bidding/notice/page | bidding:notice:query | ✅ |
| fetchNoticeDetail | GET /bidding/notice/get | bidding:notice:query | ✅ |
| createNotice | POST /bidding/notice/create | bidding:notice:create | ✅ |
| updateNotice | PUT /bidding/notice/update | bidding:notice:update | ✅ |
| publishNotice | POST /bidding/notice/publish?id= | bidding:notice:publish | ✅ |
| unpublishNotice | POST /bidding/notice/unpublish?id= | bidding:notice:update | ✅ |
| deleteNotice | DELETE /bidding/notice/delete?id= | bidding:notice:delete | ✅ |
| （大厅只读） | GET /bidding/notice/public/{list,detail} | PermitAll | ✅ |

## 3. noticeExternal（外部公告审核管理）

| 前端函数 | 后端端点 | 权限 | 状态 |
| --- | --- | --- | --- |
| getManagePage | GET /bidding/notice-external/manage/page | …:update | ✅ |
| getManageDetail | GET /bidding/notice-external/manage/get?id= | …:update | ✅ |
| approveNotice | POST /bidding/notice-external/approve | …:update | ✅ |
| rejectNotice | POST /bidding/notice-external/reject | …:update | ✅ |
| takeDownNotice | POST /bidding/notice-external/take-down | …:update | ✅ |
| aiExtractNotice | POST /bidding/notice-external/ai-extract | …:update | ✅ |
| bodyStructureNotice | POST /bidding/notice-external/body-structure | …:update | ✅ |
| （公开浏览） | GET /bidding/notice-external/page、/detail、/go | PermitAll（go 为外链跳转白名单） | ✅ |

## 4. subject（主体信息与授权管理）

| 前端函数 | 后端端点 | 权限 | 状态 |
| --- | --- | --- | --- |
| fetchSubjectProfile | GET /bidding/subject/profile/get?subjectType= | bidding:subject:query | ✅ |
| saveSubjectProfile | POST /bidding/subject/profile/save | bidding:subject:update | ✅ |
| fetchSubjectEligibility | GET /bidding/subject/profile/eligibility | bidding:subject:query | ✅ |
| fetchSubjectAuthPage | GET /bidding/subject-auth/page | bidding:subject-auth:query | ✅ |
| authorizeSubject | POST /bidding/subject-auth/authorize | …:update | ✅ |
| updateSubjectAuthStatus | PUT /bidding/subject-auth/status?id=&authStatus= | …:update | ✅ |
| fetchSubjectAuthEligibility | GET /bidding/subject-auth/eligibility | …:query | ✅ |
| fetchSubjectUserPage | GET /bidding/subject-auth/user/page | …:query | ✅ |
| bindSubjectUser | POST /bidding/subject-auth/user/bind | …:update | ✅ |
| unbindSubjectUser | DELETE /bidding/subject-auth/user/unbind?id= | …:update | ✅ |

## 5. processTrack（投标流程跟踪）

> ⚠️ **本节已过时（2026-09-17 起）**：页面已由「我的关注 + 本地阶段」重写为**管理端只读总览**
> （按公司盘点意向项目 / 投标流程 / 负责人，超管跨租户、租户只看本公司），不再调用 fetchMyFollowPage，
> 也不再使用 localStorage。**新的接口契约见 `docs/电子证照前前端契约/后端变更请求-投标流程跟踪管理端总览-2026-09-18.md`**
> （原在本目录排为「29 号」，经后端同学指定改放该目录，内容不变），
> 业务口径见 `09-投标流程跟踪前端交付-业务口径说明` §0。以下为 v1 历史记录。

契约现状：仅 1 个真实接口 fetchMyFollowPage → GET /bidding/notice-follow/item/page（支持 followType 过滤），
作为意向建档数据源；Track/TrackEvent/StateCode/EventCode 为前端本地跟踪类型（页面用 localStorage 维护，非后端接口）。

| 前端函数 | 后端端点 | 权限 | 状态 |
| --- | --- | --- | --- |
| fetchMyFollowPage | GET /bidding/notice-follow/item/page | bidding:notice-external:query | ✅（2026-09-09 对齐） |

同域可用（NoticeFollowController，供大厅/详情联动）：POST /bidding/notice-follow/follow、/unfollow（…:update）；
GET /status、/item/{itemId}/timeline、POST /item/{itemId}/read（…:query/…:update）。

## 6. 本次修复清单（2026-09-09）

- 菜单 6748（投标流程跟踪）permission：bidding:process-track:query → **bidding:notice-external:query**
  （页面唯一接口走 notice-follow/item/page；原权限串下无人有 notice-external:query → 页面必 403。已改并应用到本地库。）
- 菜单按钮 6749-6752（bidding:process-track:create/register/review/upload）为状态机落地预留：
  当前后端无对应端点/校验，不会产生错误，仅暂不控制按钮显隐之外的能力；待状态机实现后启用。
- 角色授权：role1/role2 已授招投标整棵 26 项（含 6748，改 permission 后重登即生效）。

## 7. 附：跑批口径（承接 2026-09-08 批次化）

每次执行（人工 MANUAL/调度 SCHEDULE）落一条 bidding_crawl_job（jobId），失败条目挂 jobId 进 bidding_crawl_task；
前端任务页按 jobId 过滤 + retry 单条。旧体系 task/job 记录已于 2026-09-09 清理（sql/history/2026-09-09-purge-legacy-crawl-records.sql）。

