import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';

export namespace BiddingContractApi {
  export interface Record {
    id: string; tenderProjectCode: string; bidSectionCode?: string; contractCode: string; contractName: string;
    contractPrice?: number | string; performanceBond?: number | string; winNoticeTime?: string; signTime?: string;
    fileId?: number; status?: string; remark?: string; createTime?: string;
  }
  export interface SaveReq { tenderProjectCode: string; bidSectionCode?: string; contractCode: string; contractName: string; contractPrice?: number | string; performanceBond?: number | string; winNoticeTime?: string; signTime?: string; fileId?: number; status?: string; remark?: string; }
  export interface PageParams extends PageParam { tenderProjectCode?: string; bidSectionCode?: string; }
}
export function saveBiddingContract(data: BiddingContractApi.SaveReq) { return requestClient.post<string>('/bidding/contract/save', data); }
export function getBiddingContract(id: string) { return requestClient.get<BiddingContractApi.Record>('/bidding/contract/get', { params: { id } }); }
export function getBiddingContractPage(params: BiddingContractApi.PageParams) { return requestClient.get<PageResult<BiddingContractApi.Record>>('/bidding/contract/page', { params }); }
export function archiveBiddingContract(id: number | string) { return requestClient.post<boolean>('/bidding/contract/archive?id=' + String(id)); }
