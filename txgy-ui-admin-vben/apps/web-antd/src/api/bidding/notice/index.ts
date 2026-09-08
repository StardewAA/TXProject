import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace NoticeApi {
  /** 公告类型（NoticeTypeEnum：本期 4 个工程域数据集） */
  export type NoticeTypeCode =
    | 'TENDER_NOTICE'
    | 'CHANGE_NOTICE'
    | 'WIN_CANDIDATE_PUBLICITY'
    | 'WIN_RESULT_PUBLICITY';
  export type NoticeStatus = 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED' | 'ARCHIVED';

  export const NOTICE_TYPES: Array<{ code: NoticeTypeCode; label: string }> = [
    { code: 'TENDER_NOTICE', label: '招标公告' },
    { code: 'CHANGE_NOTICE', label: '变更公告' },
    { code: 'WIN_CANDIDATE_PUBLICITY', label: '中标候选人公示' },
    { code: 'WIN_RESULT_PUBLICITY', label: '中标结果公示' },
  ];
  export const NOTICE_STATUS_LABEL: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    UNPUBLISHED: '已撤下',
    ARCHIVED: '已归档',
  };

  export interface Row {
    id: number | string;
    noticeCode?: string;
    noticeType?: string;
    noticeTypeName?: string;
    title?: string;
    projectCode?: string;
    projectName?: string;
    publisherName?: string;
    publishTime?: number;
    status?: NoticeStatus;
    internalFlag?: boolean;
    publicLevel?: string;
    contentPreview?: string;
  }

  export interface Detail extends Row {
    content?: string;
    contentVersion?: number;
    payload?: Record<string, unknown>;
    pdfFileId?: number | string;
    pdfSha256?: string;
    pdfSize?: number;
  }

  export interface PageParams extends PageParam {
    noticeType?: string;
    status?: string;
    internalFlag?: boolean;
    publicLevel?: string;
    projectCode?: string;
  }

  export interface SaveParams {
    id?: number | string;
    noticeCode?: string;
    noticeType: string;
    title: string;
    projectCode?: string;
    projectName?: string;
    publisherName?: string;
    publishTime?: number | string;
    internalFlag?: boolean;
    publicLevel?: string;
    payload?: Record<string, unknown>;
    content?: string;
    pdfFileId?: number | string;
    pdfSha256?: string;
    pdfSize?: number;
  }
}

export function fetchNoticePage(params: NoticeApi.PageParams) {
  return requestClient.get<PageResult<NoticeApi.Row>>('/bidding/notice/page', { params });
}
export function fetchNoticeDetail(id: number | string) {
  return requestClient.get<NoticeApi.Detail>('/bidding/notice/get', { params: { id } });
}
export function createNotice(data: NoticeApi.SaveParams) {
  return requestClient.post<number | string>('/bidding/notice/create', data);
}
export function updateNotice(data: NoticeApi.SaveParams) {
  return requestClient.put<boolean>('/bidding/notice/update', data);
}
export function publishNotice(id: number | string) {
  return requestClient.post<boolean>('/bidding/notice/publish', undefined, { params: { id } });
}
export function unpublishNotice(id: number | string) {
  return requestClient.post<boolean>('/bidding/notice/unpublish', undefined, { params: { id } });
}
export function deleteNotice(id: number | string) {
  return requestClient.delete<boolean>('/bidding/notice/delete', { params: { id } });
}
