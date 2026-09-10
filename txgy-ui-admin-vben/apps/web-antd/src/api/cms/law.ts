import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export interface LawSearchParams {
  asOfDate?: string;
  industryCode?: string;
  keyword: string;
  lawType?: 'LAW' | 'POLICY' | 'STANDARD';
  pageNo: number;
  pageSize: number;
  regionCode?: string;
  status?: 'EFFECTIVE' | 'EXPIRED' | 'NOT_YET_EFFECTIVE' | 'REPEALED' | 'UNKNOWN';
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
