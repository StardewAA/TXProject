import { requestClient } from '#/api/request';

export interface CmsFinancePolicy {
  amountMax?: number;
  amountMin?: number;
  amountUnit: string;
  applicableEnterpriseTypes: string[];
  applicableIndustries: string[];
  applicableRegions: string[];
  applyEndTime?: string;
  applyStartTime?: string;
  interpretationJson?: string;
  interpretationStatus: 'APPROVED' | 'PENDING' | 'REJECTED' | 'REVIEWING';
  policyStatus: 1 | 2 | 3;
  reviewId: string;
  supportDescription?: string;
  supportRate?: number;
  supportType?: string;
  version: number;
}

export interface FinancePolicyInterpretation {
  amountDescription: string;
  applicableRegions: string[];
  applicationPeriod: { end?: string; start?: string };
  citations: Array<{ chunkId: string; pageNumber?: number; quote: string }>;
  eligibility: string[];
  risks: string[];
  summary: string;
  supportMethods: string[];
}

export interface FinancePolicyInterpretationView {
  disclaimer: string;
  interpretation: FinancePolicyInterpretation;
  localPdfPath?: string;
  modelId?: number;
  modelName?: string;
  reviewId: string;
  status: 'APPROVED' | 'REJECTED' | 'REVIEWING';
}

export type CmsFinancePolicySave = Omit<
  CmsFinancePolicy,
  'interpretationJson' | 'interpretationStatus' | 'reviewId'
>;

export const getCmsFinancePolicy = (reviewId: string) =>
  requestClient.get<CmsFinancePolicy>('/cms/finance-policy/get', {
    params: { reviewId },
  });

export const saveCmsFinancePolicy = (
  reviewId: string,
  data: CmsFinancePolicySave,
) =>
  requestClient.put<CmsFinancePolicy>('/cms/finance-policy/save', data, {
    params: { reviewId },
    timeout: 120_000,
  });

export const generateFinancePolicyInterpretation = (reviewId: string) =>
  requestClient.post<FinancePolicyInterpretationView>(
    '/cms/finance-policy/interpretation/generate',
    undefined,
    { params: { reviewId }, timeout: 180_000 },
  );

export const getFinancePolicyInterpretation = (
  reviewId: string,
  portal = false,
) =>
  requestClient.get<FinancePolicyInterpretationView>(
    portal
      ? '/cms/portal/law/finance-policy/interpretation'
      : '/cms/finance-policy/interpretation/get',
    { params: { reviewId } },
  );

export const reviewFinancePolicyInterpretation = (
  reviewId: string,
  approved: boolean,
) =>
  requestClient.post<FinancePolicyInterpretationView>(
    '/cms/finance-policy/interpretation/review',
    undefined,
    { params: { approved, reviewId } },
  );
