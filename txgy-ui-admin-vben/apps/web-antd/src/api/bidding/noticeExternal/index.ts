import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace NoticeExternalApi {
  /** 管理端行（NoticeExternalManageRespVO，01 §2 manage/page） */
  export interface ManageItem {
    id?: number;
    sourceCode?: string;
    sourceName?: string;
    title?: string;
    origUrl?: string;
    contentType?: string;
    noticeTypeRaw?: string;
    legalFlag?: string;
    industryFlag?: string;
    industryConfidence?: number;
    issuerName?: string;
    projectName?: string;
    bidSectionCodes?: string;
    status?: string;
    auditStatus?: string;
    extractionStatus?: string;
    aiExtractionStatus?: string;
    bodyBlocksStatus?: string;
    bodyParserCode?: string;
    publishTime?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    fetchCount?: number;
    lastFetchTime?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 管理详情（NoticeExternalManageDetailRespVO：BR-7 manage/get，全状态预览） */
  export interface ManageDetail extends ManageItem {
    publicScope?: string;
    contentHtmlSafe?: string;
    bodyBlocksJson?: string;
    pdfFileId?: number;
    pdfSha256?: string;
    pdfSize?: number;
    pdfDownloadUrl?: string;
    extractionSummary?: string;
    aiMetaFieldCount?: number;
    projectKey?: string;
    tenderProjectKey?: string;
    bidSectionKeys?: string[];
    attachments?: Array<{
      id?: number | string;
      name?: string;
      ext?: string;
      size?: number;
      downloadUrl?: string;
      origDownloadUrl?: string;
      virusStatus?: string;
    }>;
  }

  /** manage/page 筛选（结构化过滤；勿传 keyword，管理列表无 ES 全文） */
  export interface ManagePageParams extends PageParam {
    status?: string;
    auditStatus?: string;
    sourceCode?: string;
    contentType?: string;
    noticeTypeRaw?: string;
    industryFlag?: string;
    projectCode?: string;
    beginTime?: string;
    endTime?: string;
  }
}

/** 管理端分页列表（01 §2：全状态 NEW/ACTIVE/INACTIVE/ARCHIVED × UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN） */
export function getManagePage(params: NoticeExternalApi.ManagePageParams) {
  return requestClient.get<PageResult<NoticeExternalApi.ManageItem>>(
    '/bidding/notice-external/manage/page',
    { params },
  );
}

/** 管理详情预览（BR-7：全状态读取，审核/运营预览用） */
export function getManageDetail(id: number | string) {
  return requestClient.get<NoticeExternalApi.ManageDetail>(
    '/bidding/notice-external/manage/get',
    { params: { id } },
  );
}

/** 审核通过：NEW→ACTIVE（触发关注扇出 + ES 建索引，幂等） */
export function approveNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/approve', undefined, {
    params: { id },
  });
}

/** 驳回（不展示） */
export function rejectNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/reject', undefined, {
    params: { id },
  });
}

/** 下线：ACTIVE→INACTIVE（展示随即消失） */
export function takeDownNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/take-down', undefined, {
    params: { id },
  });
}

/** AI 结构化抽取（含行业判定回写 industryFlag/industryConfidence） */
export function aiExtractNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/ai-extract', undefined, {
    params: { id },
  });
}

/** 正文结构化（HTML 解析或 AI 兜底排版 → bodyBlocks） */
export function bodyStructureNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/body-structure', undefined, {
    params: { id },
  });
}
