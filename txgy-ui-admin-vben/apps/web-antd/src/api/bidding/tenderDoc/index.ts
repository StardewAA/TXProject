import { requestClient } from '#/api/request';

export namespace TenderDocApi {
  export interface Record { id: string; tenderProjectCode: string; bidSectionCode?: string; attachmentId?: string; fileId?: number; subjectId?: string; userId?: string; downloadTime?: string; ip?: string; remark?: string; }
  export interface RecordReq { tenderProjectCode: string; bidSectionCode?: string; attachmentId?: string; fileId?: number; subjectId?: string; remark?: string; }
}
export function recordTenderDocDownload(data: TenderDocApi.RecordReq) { return requestClient.post<string>('/bidding/tender-doc/record', data); }
export function getTenderDocDownloaders(tenderProjectCode: string, bidSectionCode?: string) { return requestClient.get<TenderDocApi.Record[]>('/bidding/tender-doc/downloaders', { params: { tenderProjectCode, bidSectionCode } }); }
export function countTenderDocDownloaders(bidSectionCode: string) { return requestClient.get<number>('/bidding/tender-doc/count', { params: { bidSectionCode } }); }
