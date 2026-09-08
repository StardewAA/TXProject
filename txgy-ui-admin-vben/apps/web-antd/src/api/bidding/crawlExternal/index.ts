import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：爬虫任务工作台-前后端契约-2026-09-07（父仓 docs/bidding-notice-external/）+ 04-全链路接口说明 §5 采集管理域 + 06-接口完全对照 §1；解析器端点复用 crawl-source 权限（见 06 对照 §1）。 */
export namespace CrawlExternalApi {
// ===== 解析器（手动录入/测试链路，02 契约 §1.3 / §3.2） =====
  export interface ParserInfo {
    code?: string;
    name?: string;
    description?: string;
    supportedTypes?: string[];
  }
  export interface ParseAttachment {
    name?: string;
    url?: string;
  }
  export interface ParseEntry {
    title?: string;
    url?: string;
  }
  /** 解析测试回执（CrawlParseTestRespVO）——手动录入即真实解析一次 */
  export interface ParseTestResp {
    parserCode?: string;
    parserName?: string;
    payloadType?: string;
    fileName?: string;
    url?: string;
    contentLength?: number;
    durationMs?: number;
    status?: string;
    error?: string;
    title?: string;
    origUrl?: string;
    contentType?: string;
    noticeTypeRaw?: string;
    legalFlag?: string;
    projectKey?: string;
    projectName?: string;
    tenderProjectKey?: string;
    bidSectionCodes?: string;
    issuerName?: string;
    publishTime?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    contentTextPreview?: string;
    contentHtmlLength?: number;
    attachments?: ParseAttachment[];
    pageType?: string;
    entries?: ParseEntry[];
    meta?: Record<string, string>;
    warnings?: string[];
  }


  /** 采集源（CrawlSourceRespVO，02 §6.1） */
  export interface SourceItem {
    id?: number;
    sourceCode?: string;
    sourceName?: string;
    baseUrl?: string;
    allowedHosts?: string;
    entryUrlsJson?: string;
    channelCode?: string;
    parserCodeDefault?: string;
    fetchIntervalSeconds?: number;
    robotsOk?: boolean;
    enabled?: boolean;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }
  /** 采集源保存入参（sourceCode 仅新建可传） */
  export interface SourcePayload {
    id?: number;
    sourceCode: string;
    sourceName: string;
    baseUrl: string;
    allowedHosts?: string;
    entryUrlsJson?: string;
    channelCode: string;
    parserCodeDefault?: string;
    fetchIntervalSeconds?: number;
    robotsOk?: boolean;
    enabled?: boolean;
    remark?: string;
  }
  export interface SourcePageParams extends PageParam {
    sourceCode?: string;
    sourceName?: string;
    channelCode?: string;
    enabled?: boolean;
  }
  /** 立即采集汇总（CrawlRunRespVO） */
  export interface RunResult {
    sourceCode?: string;
    startedAt?: string;
    finishedAt?: string;
    totalIngested?: number;
    totalFailed?: number;
    categories?: Array<{ name?: string; fetched?: number; ingested?: number; failed?: number; error?: string }>;
  }
  /** 采集任务（CrawlTaskRespVO，02 §6.2） */
  export interface TaskItem {
    /** 任务主键（雪花 ID，后端 Long 序列化为字符串时按 string 兼容） */
    id?: number | string;
    jobId?: number | string;
    sourceCode?: string;
    url?: string;
    urlHash?: string;
    channelCode?: string;
    parserCode?: string;
    priority?: number;
    status?: string;
    attempt?: number;
    nextRetryTime?: string;
    workerId?: string;
    startedAt?: string;
    finishedAt?: string;
    message?: string;
    createTime?: string;
    updateTime?: string;
  }
  export interface TaskPageParams extends PageParam {
    taskType?: string;
    jobId?: number;
    sourceCode?: string;
    status?: string;
    channelCode?: string;
    url?: string;
  }
  /** 采集批次（CrawlJobRespVO，02 §6.3） */
  export interface JobItem {
    /** 批次主键（雪花 ID） */
    id?: number | string;
    sourceCode?: string;
    triggerType?: string;
    scope?: string;
    status?: string;
    summaryJson?: string;
    finishedAt?: string;
    createTime?: string;
    updateTime?: string;
  }
  export interface JobPageParams extends PageParam {
    sourceCode?: string;
    triggerType?: string;
    status?: string;
  }
}

// ===== 采集源 =====
/** 契约：GET /bidding/crawl-source/page —— 采集源分页（爬虫源配置管理列表）；权限 bidding:crawl-source:query；依据 04-全链路 §5.1 */
export function fetchCrawlSourcePage(params: CrawlExternalApi.SourcePageParams) {
  return requestClient.get<PageResult<CrawlExternalApi.SourceItem>>('/bidding/crawl-source/page', { params });
}
/** 契约：GET /bidding/crawl-source/get —— 采集源详情（编辑回显）；权限 bidding:crawl-source:query；依据 04-全链路 §5.2 */
export function getCrawlSource(id: number) {
  return requestClient.get<CrawlExternalApi.SourceItem>('/bidding/crawl-source/get', { params: { id } });
}
/** 契约：POST /bidding/crawl-source/create —— 新建采集源（默认不启用，sourceCode 唯一）；权限 bidding:crawl-source:create；依据 04-全链路 §5.3 */
export function createCrawlSource(data: CrawlExternalApi.SourcePayload) {
  return requestClient.post<number>('/bidding/crawl-source/create', data);
}
/** 契约：PUT /bidding/crawl-source/update —— 修改采集源（sourceCode 不可改）；权限 bidding:crawl-source:update；依据 04-全链路 §5.4 */
export function updateCrawlSource(data: CrawlExternalApi.SourcePayload) {
  return requestClient.put<boolean>('/bidding/crawl-source/update', data);
}
/** 契约：POST /bidding/crawl-source/enabled —— 启停采集源（启用进入外链 /go 白名单；停用须先下线其 ACTIVE 公告）；权限 bidding:crawl-source:update；依据 04-全链路 §5.5 */
export function setCrawlSourceEnabled(id: number, enabled: boolean) {
  return requestClient.post('/bidding/crawl-source/enabled', undefined, { params: { id, enabled } });
}
/** 契约：DELETE /bidding/crawl-source/delete —— 删除采集源（须无未终态任务且无 ACTIVE 公告引用）；权限 bidding:crawl-source:delete；依据 04-全链路 §5.6 */
export function deleteCrawlSource(id: number) {
  return requestClient.delete('/bidding/crawl-source/delete', { params: { id } });
}
/** 契约：GET /bidding/crawl-parser/list —— 解析器插件清单（code/名称/支持类型，手动录入与解析测试下拉用）；权限 bidding:crawl-source:query；依据 爬虫工作台契约 §0 复用 / 06 对照 §1 */
export function fetchCrawlParserList() {
  return requestClient.get<CrawlExternalApi.ParserInfo[]>('/bidding/crawl-parser/list');
}
/** 契约：POST /bidding/crawl-parser/test —— 手动录入解析测试（multipart：对粘贴原文或上传文件真实解析一次，返回 CrawlParseTestRespVO）；权限 bidding:crawl-source:update；依据 爬虫工作台契约 §0 复用 / §1.3 字段 / 06 对照 §1 */
export function crawlParserTest(data: {
  parserCode: string;
  payloadType: 'HTML' | 'JSON' | 'FILE';
  content?: string;
  fileName?: string;
  url?: string;
  file?: File;
}) {
  // 走框架官方 upload 通道（与 infra 文件上传同实现）：自动构造 FormData 并强制
  // multipart/form-data，字段名与后端 CrawlParserController.test 的 @RequestParam/@RequestPart 一一对应
  return requestClient.upload<CrawlExternalApi.ParseTestResp>(
    '/bidding/crawl-parser/test',
    data as unknown as { file: Blob | File },
  );
}
/** 契约：POST /bidding/crawl-source/run —— 采集源立即同步爬取一轮（人工触发，批次落库见 06 对照 §附）；权限 bidding:crawl-source:update；依据 爬虫工作台契约 §0 复用 / 06 对照 §1、§附 */
export function runCrawlSource(sourceCode: string) {
  return requestClient.post<CrawlExternalApi.RunResult>('/bidding/crawl-source/run', undefined, { params: { sourceCode } });
}

// ===== 采集任务 =====
/** 契约：GET /bidding/crawl-task/page —— 采集任务分页（批次/源/状态/URL/任务类型检索）；权限 bidding:crawl-task:query；依据 04-全链路 §5.7 / 爬虫工作台契约 §1.2 */
export function fetchCrawlTaskPage(params: CrawlExternalApi.TaskPageParams) {
  return requestClient.get<PageResult<CrawlExternalApi.TaskItem>>('/bidding/crawl-task/page', { params });
}
/** 契约：GET /bidding/crawl-task/get —— 采集任务详情；权限 bidding:crawl-task:query；依据 04-全链路 §5.8 */
export function getCrawlTask(id: number) {
  return requestClient.get<CrawlExternalApi.TaskItem>('/bidding/crawl-task/get', { params: { id } });
}
/** 契约：POST /bidding/crawl-task/retry —— 人工重试（仅 FAILED 死信任务转 PENDING，attempt 归零）；权限 bidding:crawl-task:update；依据 04-全链路 §5.9 / 爬虫工作台契约 §2 */
export function retryCrawlTask(id: number) {
  return requestClient.post('/bidding/crawl-task/retry', undefined, { params: { id } });
}

// ===== 采集批次 =====
/** 契约：GET /bidding/crawl-job/page —— 采集批次分页（每次人工/调度执行落一条 job，任务按 jobId 挂批）；权限 bidding:crawl-job:query；依据 04-全链路 §5.10 / 06 对照 §附 */
export function fetchCrawlJobPage(params: CrawlExternalApi.JobPageParams) {
  return requestClient.get<PageResult<CrawlExternalApi.JobItem>>('/bidding/crawl-job/page', { params });
}
/** 契约：GET /bidding/crawl-job/get —— 采集批次详情（批次汇总/触发类型/状态）；权限 bidding:crawl-job:query；依据 04-全链路 §5.11 / 06 对照 §附 */
export function getCrawlJob(id: number) {
  return requestClient.get<CrawlExternalApi.JobItem>('/bidding/crawl-job/get', { params: { id } });
}