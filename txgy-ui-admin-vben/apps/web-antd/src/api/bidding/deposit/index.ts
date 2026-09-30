import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';

export namespace BidDepositApi {
  export interface Record {
    id: string; bidSectionCode: string; tenderProjectCode?: string; subjectId?: string;
    amount?: number | string; currencyCode?: string; payType?: string; status?: string;
    fileId?: number; depositTime?: string; refundTime?: string; refundStatus?: string;
    refundOverdue?: boolean; remark?: string; createTime?: string;
  }
  export interface SaveReq { bidSectionCode: string; subjectId?: string; amount?: number | string; currencyCode?: string; payType?: string; status?: string; fileId?: number; remark?: string; }
  export interface RefundReq { depositId: string; refundStatus?: string; remark?: string; }
  export interface PageParams extends PageParam { tenderProjectCode?: string; bidSectionCode?: string; subjectId?: string; }
}
export function saveBidDeposit(data: BidDepositApi.SaveReq) { return requestClient.post<string>('/bidding/deposit/save', data); }
export function getBidDeposit(id: string) { return requestClient.get<BidDepositApi.Record>('/bidding/deposit/get', { params: { id } }); }
export function getBidDepositPage(params: BidDepositApi.PageParams) { return requestClient.get<PageResult<BidDepositApi.Record>>('/bidding/deposit/page', { params }); }
export function refundBidDeposit(data: BidDepositApi.RefundReq) { return requestClient.post<string>('/bidding/deposit/refund', data); }
