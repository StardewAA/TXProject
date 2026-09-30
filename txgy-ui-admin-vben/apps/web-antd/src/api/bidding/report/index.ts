import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';

export namespace BiddingReportApi {
  export interface Record {
    id: string; tenderProjectCode: string; bidSectionCode?: string; reportCode: string; reportName: string;
    fileId?: number; submitTime?: string; awardTime?: string; status?: string; reportOverdue?: boolean; remark?: string; createTime?: string;
  }
  export interface SubmitReq { tenderProjectCode: string; bidSectionCode?: string; reportCode: string; reportName: string; fileId?: number; remark?: string; }
  export interface PageParams extends PageParam { tenderProjectCode?: string; bidSectionCode?: string; }
}
export function submitBiddingReport(data: BiddingReportApi.SubmitReq) { return requestClient.post<string>('/bidding/report/submit', data); }
export function getBiddingReport(id: string) { return requestClient.get<BiddingReportApi.Record>('/bidding/report/get', { params: { id } }); }
export function getBiddingReportPage(params: BiddingReportApi.PageParams) { return requestClient.get<PageResult<BiddingReportApi.Record>>('/bidding/report/page', { params }); }
