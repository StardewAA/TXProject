import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：01-接口逐一说明与设计依据 §1（公告/公示域，T 轨企业租户自营）+ 04-全链路接口说明 §1 + 08-公告公示前端交付-业务口径说明 §2/§3；枚举/字段对齐 NoticeTypeEnum、NoticeStatusEnum。 */
export namespace NoticeApi {
  /** 公告类型（NoticeTypeEnum：本期 4 个工程域数据集） */
  export type NoticeTypeCode =
    | 'CHANGE_NOTICE'
    | 'TENDER_NOTICE'
    | 'WIN_CANDIDATE_PUBLICITY'
    | 'WIN_RESULT_PUBLICITY';
  export type NoticeStatus = 'ARCHIVED' | 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED';

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

  /** 类型字段模板元数据（GET /bidding/notice/meta/types，2026-09-10 后端新增）：按类型渲染字段表单 */
  export interface TypeFieldMeta {
    code: string;
    label: string;
    required: boolean;
  }
  export interface NoticeTypeMeta {
    type: NoticeTypeCode;
    name: string;
    dataset?: string;
    fields: TypeFieldMeta[];
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

/** 契约：GET /bidding/notice/page —— 本企业公告/公示管理分页（类型/状态/内外/publicLevel/项目过滤）；权限 bidding:notice:query；依据 04-全链路 §1.1 / 08 口径 §1、§3 */
export function fetchNoticePage(params: NoticeApi.PageParams) {
  return requestClient.get<PageResult<NoticeApi.Row>>('/bidding/notice/page', { params });
}
/** 契约：GET /bidding/notice/get —— 公告详情（管理端：含正文版本/PDF 版式元数据）；权限 bidding:notice:query；依据 04-全链路 §1.3 / 08 口径 §3、§4 */
export function fetchNoticeDetail(id: number | string) {
  return requestClient.get<NoticeApi.Detail>('/bidding/notice/get', { params: { id } });
}
/** 契约：POST /bidding/notice/create —— 新建公告/公示（DRAFT 草稿入库，权威数据集必填校验 + 招标人发布门）；权限 bidding:notice:create；依据 04-全链路 §1.5 / 08 口径 §3 */
export function createNotice(data: NoticeApi.SaveParams) {
  return requestClient.post<number | string>('/bidding/notice/create', data);
}
/** 契约：PUT /bidding/notice/update —— 修改公告（已发布正文实质变更追加新版本并通知订阅者，受招标人发布门约束）；权限 bidding:notice:update；依据 04-全链路 §1.6 / 08 口径 §3 */
export function updateNotice(data: NoticeApi.SaveParams) {
  return requestClient.put<boolean>('/bidding/notice/update', data);
}
/** 契约：POST /bidding/notice/publish —— 发布公告（DRAFT 转 PUBLISHED 对外公示，前置校验正文与 PDF 版式）；权限 bidding:notice:publish；依据 04-全链路 §1.7 / 08 口径 §3 */
export function publishNotice(id: number | string) {
  return requestClient.post<boolean>('/bidding/notice/publish', undefined, { params: { id } });
}
/** 契约：POST /bidding/notice/unpublish —— 撤下公告（PUBLISHED 转 UNPUBLISHED，状态机约束，历史版本保留可再发）；权限 bidding:notice:update；依据 04-全链路 §1.8 / 08 口径 §3、§6 */
export function unpublishNotice(id: number | string) {
  return requestClient.post<boolean>('/bidding/notice/unpublish', undefined, { params: { id } });
}
/** 契约：GET /bidding/notice/meta/types —— 公告类型字段模板（必填优先+中文名）；权限 bidding:notice:query；依据 notice 域新文档 §3（2026-09-10 后端新增端点） */
export function fetchNoticeTypeMeta() {
  return requestClient.get<NoticeApi.NoticeTypeMeta[]>('/bidding/notice/meta/types');
}
/** 契约：DELETE /bidding/notice/delete —— 逻辑删除公告（仅草稿/已撤下态，发布/归档禁删，后端状态守卫）；权限 bidding:notice:delete；依据 04-全链路 §1.9 / 08 口径 §3、§8 */
export function deleteNotice(id: number | string) {
  return requestClient.delete<boolean>('/bidding/notice/delete', { params: { id } });
}
