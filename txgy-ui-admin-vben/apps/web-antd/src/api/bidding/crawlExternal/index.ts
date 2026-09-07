import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrawlExternalApi {
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
    id?: number;
    jobId?: number;
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
    jobId?: number;
    sourceCode?: string;
    status?: string;
    channelCode?: string;
    url?: string;
  }
  /** 采集批次（CrawlJobRespVO，02 §6.3） */
  export interface JobItem {
    id?: number;
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
export function fetchCrawlSourcePage(params: CrawlExternalApi.SourcePageParams) {
  return requestClient.get<PageResult<CrawlExternalApi.SourceItem>>('/bidding/crawl-source/page', { params });
}
export function getCrawlSource(id: number) {
  return requestClient.get<CrawlExternalApi.SourceItem>('/bidding/crawl-source/get', { params: { id } });
}
export function createCrawlSource(data: CrawlExternalApi.SourcePayload) {
  return requestClient.post<number>('/bidding/crawl-source/create', data);
}
export function updateCrawlSource(data: CrawlExternalApi.SourcePayload) {
  return requestClient.put<boolean>('/bidding/crawl-source/update', data);
}
export function setCrawlSourceEnabled(id: number, enabled: boolean) {
  return requestClient.post('/bidding/crawl-source/enabled', undefined, { params: { id, enabled } });
}
export function deleteCrawlSource(id: number) {
  return requestClient.delete('/bidding/crawl-source/delete', { params: { id } });
}
export function runCrawlSource(sourceCode: string) {
  return requestClient.post<CrawlExternalApi.RunResult>('/bidding/crawl-source/run', undefined, { params: { sourceCode } });
}

// ===== 采集任务 =====
export function fetchCrawlTaskPage(params: CrawlExternalApi.TaskPageParams) {
  return requestClient.get<PageResult<CrawlExternalApi.TaskItem>>('/bidding/crawl-task/page', { params });
}
export function getCrawlTask(id: number) {
  return requestClient.get<CrawlExternalApi.TaskItem>('/bidding/crawl-task/get', { params: { id } });
}
export function retryCrawlTask(id: number) {
  return requestClient.post('/bidding/crawl-task/retry', undefined, { params: { id } });
}

// ===== 采集批次 =====
export function fetchCrawlJobPage(params: CrawlExternalApi.JobPageParams) {
  return requestClient.get<PageResult<CrawlExternalApi.JobItem>>('/bidding/crawl-job/page', { params });
}
export function getCrawlJob(id: number) {
  return requestClient.get<CrawlExternalApi.JobItem>('/bidding/crawl-job/get', { params: { id } });
}