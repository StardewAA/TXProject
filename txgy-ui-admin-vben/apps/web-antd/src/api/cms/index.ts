import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** CMS 内容通用记录 */
export interface CmsContentItem {
  auditRemark?: string;
  auditStatus?: number;
  createTime?: string;
  id?: number;
  publishStatus?: number;
  title?: string;
  updateTime?: string;
}

/** 内容审核参数 */
export interface CmsAuditReq {
  auditRemark?: string;
  auditStatus: number;
  contentId: number;
  contentType: string;
}

/** 标准规范 */
export function getCmsStandardPage(params: PageParam) {
  return requestClient.get<PageResult<CmsContentItem>>('/cms/standard/page', {
    params,
  });
}

export function createCmsStandard(data: Recordable<any>) {
  return requestClient.post<number>('/cms/standard/create', data);
}

export function updateCmsStandard(data: Recordable<any>) {
  return requestClient.put<boolean>('/cms/standard/update', data);
}

export function deleteCmsStandard(data: { id: number }) {
  return requestClient.delete<boolean>('/cms/standard/delete', { data });
}

/** 材料参数 */
export function getCmsMaterialPage(params: PageParam) {
  return requestClient.get<PageResult<CmsContentItem>>('/cms/material/page', {
    params,
  });
}

export function createCmsMaterial(data: Recordable<any>) {
  return requestClient.post<number>('/cms/material/create', data);
}

export function updateCmsMaterial(data: Recordable<any>) {
  return requestClient.put<boolean>('/cms/material/update', data);
}

export function deleteCmsMaterial(data: { id: number }) {
  return requestClient.delete<boolean>('/cms/material/delete', { data });
}

/** 工艺案例 */
export function getCmsProcessPage(params: PageParam) {
  return requestClient.get<PageResult<CmsContentItem>>('/cms/process/page', {
    params,
  });
}

export function createCmsProcess(data: Recordable<any>) {
  return requestClient.post<number>('/cms/process/create', data);
}

export function updateCmsProcess(data: Recordable<any>) {
  return requestClient.put<boolean>('/cms/process/update', data);
}

export function deleteCmsProcess(data: { id: number }) {
  return requestClient.delete<boolean>('/cms/process/delete', { data });
}

/** 定额资料 */
export function getCmsQuotaPage(params: PageParam) {
  return requestClient.get<PageResult<CmsContentItem>>('/cms/quota/page', {
    params,
  });
}

export function createCmsQuota(data: Recordable<any>) {
  return requestClient.post<number>('/cms/quota/create', data);
}

export function updateCmsQuota(data: Recordable<any>) {
  return requestClient.put<boolean>('/cms/quota/update', data);
}

export function deleteCmsQuota(data: { id: number }) {
  return requestClient.delete<boolean>('/cms/quota/delete', { data });
}

/** 政策资讯 */
export function getCmsPolicyPage(params: PageParam) {
  return requestClient.get<PageResult<CmsContentItem>>('/cms/policy/page', {
    params,
  });
}

export function createCmsPolicy(data: Recordable<any>) {
  return requestClient.post<number>('/cms/policy/create', data);
}

export function updateCmsPolicy(data: Recordable<any>) {
  return requestClient.put<boolean>('/cms/policy/update', data);
}

export function deleteCmsPolicy(data: { id: number }) {
  return requestClient.delete<boolean>('/cms/policy/delete', { data });
}

/** 统一审核 */
export function auditCmsContent(data: CmsAuditReq) {
  return requestClient.put<boolean>('/cms/content/audit', data);
}
