import type { PageResult } from '@vben/request';

import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

export interface LawSearchParams {
  asOfDate?: string;
  effectiveDateFrom?: string;
  effectiveDateTo?: string;
  industryCode?: string;
  keyword: string;
  lawType?: 'LAW' | 'POLICY' | 'STANDARD';
  issuingAuthority?: string;
  pageNo: number;
  pageSize: number;
  regionCode?: string;
  status?: 'EFFECTIVE' | 'EXPIRED' | 'NOT_YET_EFFECTIVE' | 'REPEALED' | 'UNKNOWN';
}

export interface LawSuggestion {
  documentNo?: string;
  issuingAuthority?: string;
  lawType?: string;
  title: string;
}

export interface LawSearchItem {
  articleId: number;
  articleNo?: string;
  content: string;
  documentNo?: string;
  effectiveDate?: string;
  expiryDate?: string;
  issuingAuthority?: string;
  lawId: number;
  score?: number;
  sourceId: string;
  sourceUrl: string;
  status: string;
  title: string;
  version?: string;
}

export interface LawCitation {
  articleId: number;
  articleNo?: string;
  documentNo?: string;
  index: number;
  quote: string;
  score?: number;
  sourceId: string;
  sourceUrl: string;
  title: string;
  reviewId?: string; // 法规原件（CMS 审核件）编号，用于打开本地原件
}

export interface LawAnswer {
  answer: string;
  citations: LawCitation[];
  generated: boolean;
  model?: string;
  question: string;
  semanticAvailable: boolean;
  warning?: string;
}

export function searchLaws(params: LawSearchParams, portal = false) {
  const prefix = portal ? '/cms/portal/law' : '/cms/law';
  return requestClient.get<PageResult<LawSearchItem>>(`${prefix}/search`, {
    params,
  });
}

export function answerLaw(question: string, topK = 5, portal = false) {
  const prefix = portal ? '/cms/portal/law' : '/cms/law';
  return requestClient.post<LawAnswer>(`${prefix}/answer`, { question, topK }, {
    timeout: 120_000,
  });
}

export function suggestLaws(keyword: string, portal = false) {
  const prefix = portal ? '/cms/portal/law' : '/cms/law';
  return requestClient.get<LawSuggestion[]>(`${prefix}/suggest`, { params: { keyword } });
}

/**
 * 下载/预览法规原件（二进制流）
 *
 * <p>后端原件接口为 `GET /cms/review/original?id={uuid}`（当前未提供门户专用通道，portal 入参保留以兼容调用方）。
 * 与合同预览同理：不能用 requestClient（全局拦截器会把二进制当 JSON 解析），
 * 也不能直接 window.open（新窗口不带 Authorization），因此用 fetch 手动带鉴权与租户头取 Blob。</p>
 */
export function downloadLawOriginal(reviewId: string, _portal = false) {
  const accessStore = useAccessStore();
  const base = (import.meta.env.VITE_GLOB_API_URL as string) || '/admin-api';
  const tenantId = String(
    accessStore.tenantId ??
      Number(import.meta.env.VITE_APP_DEFAULT_TENANT_ID || 1),
  );
  const url = `${base}/cms/review/original?id=${encodeURIComponent(reviewId)}`;
  return fetch(url, {
    headers: {
      Authorization: `Bearer ${accessStore.accessToken ?? ''}`,
      'tenant-id': tenantId,
      'visit-tenant-id': tenantId,
    },
    method: 'GET',
  }).then(async (res) => {
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} ${res.statusText}`);
    }
    return res.blob();
  });
}
