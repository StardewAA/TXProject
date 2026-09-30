import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';

export namespace BiddingObjectionApi {
  export interface Record {
    id: string; tenderProjectCode: string; bidSectionCode?: string; objectionType: string; subjectId?: string; content: string;
    proposeTime?: string; status?: string; replyTime?: string; replyContent?: string; replyOverdue?: boolean; remark?: string; createTime?: string;
  }
  export interface ProposeReq { tenderProjectCode: string; bidSectionCode?: string; objectionType: string; subjectId?: string; content: string; remark?: string; }
  export interface ReplyReq { objectionId: string; replyContent: string; }
  export interface PageParams extends PageParam { tenderProjectCode?: string; bidSectionCode?: string; objectionType?: string; }
}
export function proposeBiddingObjection(data: BiddingObjectionApi.ProposeReq) { return requestClient.post<string>('/bidding/objection/propose', data); }
export function replyBiddingObjection(data: BiddingObjectionApi.ReplyReq) { return requestClient.post<string>('/bidding/objection/reply', data); }
export function getBiddingObjection(id: string) { return requestClient.get<BiddingObjectionApi.Record>('/bidding/objection/get', { params: { id } }); }
export function getBiddingObjectionPage(params: BiddingObjectionApi.PageParams) { return requestClient.get<PageResult<BiddingObjectionApi.Record>>('/bidding/objection/page', { params }); }
