import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';

export namespace PrequalApi {
  export interface Record { id: string; prequalCode: string; tenderProjectCode: string; bidSectionCode?: string; title: string; noticeCode?: string; fileId?: number; applyStartTime?: string; applyDeadline?: string; status?: string; remark?: string; createTime?: string; }
  export interface SaveReq { id?: string; tenderProjectCode: string; bidSectionCode?: string; title: string; noticeCode?: string; fileId?: number; applyStartTime?: string; applyDeadline?: string; remark?: string; }
  export interface Apply { prequalId: string; subjectId?: string; fileId?: number; remark?: string; }
  export interface Review { applyId: string; passed: boolean; reviewReason?: string; }
  export interface ApplyRecord { id: string; prequalId: string; prequalCode?: string; tenderProjectCode?: string; bidSectionCode?: string; subjectId?: string; userId?: string; fileId?: number; applyTime?: string; status?: string; reviewResult?: string; reviewReason?: string; reviewTime?: string; /** 材料集附件（契约多文件化 R3：bizType=PREQUAL_APPLY，可为空数组） */ attachments?: any[]; }
  export interface PageParams extends PageParam { title?: string; tenderProjectCode?: string; bidSectionCode?: string; status?: string; }
  export interface ApplyPageParams extends PageParam { prequalId?: string; subjectId?: string; status?: string; }
  /** 资格审查委员会成员（后端变更通知-资格审查委员会-2026-09-28） */
  export interface CommitteeMember {
    id?: string;
    prequalId?: string;
    memberName: string;
    /** TENDERER_REP 招标人代表 / TECH_EXPERT 技术专家 / ECON_EXPERT 经济专家 */
    memberCategory: string;
    /** LEADER 组长 / MEMBER 成员（可空） */
    memberRole?: string;
    memberOrg?: string;
    memberTitle?: string;
    sort?: number;
    remark?: string;
    createTime?: string;
  }
  /** 委员会保存入参：整单覆盖（先删后插），prequalId + members[] */
  export interface CommitteeSaveReq { prequalId: string; members: CommitteeMember[]; }
}
/** 保存资格审查委员会（整单覆盖；组成校验：5 人以上单数、技术/经济专家≥2/3，不满足 1060115009） */
export function savePrequalCommittee(data: PrequalApi.CommitteeSaveReq) { return requestClient.post<boolean>('/bidding/prequal/committee/save', data); }
/** 资格审查委员会成员列表（按资格预审） */
export function listPrequalCommittee(prequalId: string) { return requestClient.get<PrequalApi.CommitteeMember[]>('/bidding/prequal/committee/list', { params: { prequalId } }); }
export function savePrequal(data: PrequalApi.SaveReq) { return requestClient.post<string>('/bidding/prequal/save', data); }
export function publishPrequal(id: string) { return requestClient.post<boolean>('/bidding/prequal/publish', null, { params: { id } }); }
export function closePrequal(id: string) { return requestClient.post<boolean>('/bidding/prequal/close', null, { params: { id } }); }
export function getPrequal(id: string) { return requestClient.get<PrequalApi.Record>('/bidding/prequal/get', { params: { id } }); }
export function getPrequalPage(params: PrequalApi.PageParams) { return requestClient.get<PageResult<PrequalApi.Record>>('/bidding/prequal/page', { params }); }
export function applyPrequal(data: PrequalApi.Apply) { return requestClient.post<string>('/bidding/prequal/apply', data); }
export function reviewPrequal(data: PrequalApi.Review) { return requestClient.post<boolean>('/bidding/prequal/review', data); }
export function getPrequalApplyList(prequalId: string) { return requestClient.get<PrequalApi.ApplyRecord[]>('/bidding/prequal/apply-list', { params: { prequalId } }); }
export function getPrequalApplyPage(params: PrequalApi.ApplyPageParams) { return requestClient.get<PageResult<PrequalApi.ApplyRecord>>('/bidding/prequal/apply-page', { params }); }
