import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：txgy-module-bidding 项目域接口（2026-09-10-project-domain.sql 权限点 + Controller）
 *  项目 /bidding/project/*（bidding:project:query|create|update）
 *  招标项目 /bidding/tender-project/*（复用 bidding:project:*）
 *  标段（包）/bidding/bid-section/*（bidding:bid-section:query|create|update）
 *  追溯：/trace 一次聚合 项目→招标项目/标段/公告/外部公告/备案/关注/主体/统计。
 *  写入口径：命名主列（camelCase）+ payload（key=VO 规范英文名，服务端 canonicalize 按数据集补 null、丢多余键）。 */
export namespace ProjectApi {
  /** 内部管理态（本模块自建，非数据集字段） */
  export type State = 'ACTIVE' | 'CLOSED';

  /** 项目主档（ProjectRespVO） */
  export interface Project {
    id?: number;
    projectCode: string;
    projectName: string;
    regionCode?: string;
    investProjectCode?: string;
    relateCode?: string;
    address?: string;
    legalPerson?: string;
    industriesType?: string;
    fundSource?: string;
    contributionScale?: string;
    projectScale?: string;
    approvalDeptName?: string;
    approvalDeptCode?: string;
    approvalDocNo?: string;
    state?: State | string;
    remark?: string;
    /** ProjectVO 数据集全量字段（key=规范英文名，缺失补 null） */
    fields?: Record<string, unknown>;
    createTime?: number;
  }

  /** 招标项目主档（TenderProjectRespVO） */
  export interface TenderProject {
    id?: number;
    tenderProjectCode: string;
    projectCode?: string;
    tenderProjectName: string;
    tenderProjectType?: string;
    regionCode?: string;
    tenderContent?: string;
    tendererName?: string;
    tendererCodeType?: string;
    tendererCode?: string;
    tenderAgencyName?: string;
    tenderAgencyCodeType?: string;
    tenderAgencyCode?: string;
    tenderMode?: string;
    qulificationCheckMode?: string;
    tenderOrganizeForm?: string;
    superviseDeptName?: string;
    superviseDeptCode?: string;
    approveDeptName?: string;
    approveDeptCode?: string;
    state?: State | string;
    remark?: string;
    fields?: Record<string, unknown>;
    createTime?: number;
  }

  /** 标段（包）主档（BidSectionRespVO） */
  export interface BidSection {
    id?: number;
    bidSectionCode: string;
    tenderProjectCode: string;
    bidSectionName: string;
    unifiedDealCode?: string;
    bidEctionNum?: string;
    bidTenderMode?: string;
    regionCode?: string;
    traPlaceCode?: string;
    bidSectionContent?: string;
    bidSectionType?: string;
    contractReckonPrice?: string;
    currencyCode?: string;
    priceUnit?: string;
    timeLimit?: string;
    bidQualification?: string;
    isWebBidding?: string;
    isNomeetBidding?: string;
    state?: State | string;
    remark?: string;
    fields?: Record<string, unknown>;
    createTime?: number;
  }

  export interface PageParams extends PageParam {
    projectCode?: string;
    projectName?: string;
    regionCode?: string;
    industriesType?: string;
    state?: string;
  }
  export interface TenderProjectPageParams extends PageParam {
    tenderProjectCode?: string;
    tenderProjectName?: string;
    projectCode?: string;
    tendererName?: string;
    tenderMode?: string;
    state?: string;
  }
  export interface BidSectionPageParams extends PageParam {
    bidSectionCode?: string;
    bidSectionName?: string;
    tenderProjectCode?: string;
    state?: string;
  }

  /** 全景追溯出参（ProjectTraceRespVO） */
  export interface TraceNotice {
    id?: number;
    noticeCode?: string;
    noticeType?: string;
    noticeTypeName?: string;
    title?: string;
    status?: string;
    projectCode?: string;
    tenderProjectCode?: string;
    publishTime?: number;
  }
  export interface Trace {
    queryCode?: string;
    queryType?: 'BID_SECTION' | 'PROJECT' | 'TENDER_PROJECT';
    project?: Project;
    tenderProjects?: TenderProject[];
    sections?: BidSection[];
    notices?: TraceNotice[];
    externalNotices?: Array<Record<string, unknown>>;
    filings?: Array<Record<string, unknown>>;
    follows?: Array<Record<string, unknown>>;
    subjects?: Array<Record<string, unknown>>;
    stats?: Record<string, number>;
  }
}

/* ================= 项目 ================= */
export function getProjectPage(params: ProjectApi.PageParams) {
  return requestClient.get<PageResult<ProjectApi.Project>>('/bidding/project/page', { params });
}
export function getProject(id: number | string) {
  return requestClient.get<ProjectApi.Project>('/bidding/project/get', { params: { id } });
}
export function getProjectByCode(projectCode: string) {
  return requestClient.get<ProjectApi.Project | null>('/bidding/project/get-by-code', { params: { projectCode } });
}
export function saveProject(data: ProjectApi.Project) {
  return requestClient.post<number>('/bidding/project/save', data);
}
export function deleteProject(id: number | string) {
  return requestClient.post<boolean>('/bidding/project/delete', undefined, { params: { id } });
}
export function traceProject(projectCode: string) {
  return requestClient.get<ProjectApi.Trace>('/bidding/project/trace', { params: { projectCode } });
}

/* ================= 招标项目 ================= */
export function getTenderProjectPage(params: ProjectApi.TenderProjectPageParams) {
  return requestClient.get<PageResult<ProjectApi.TenderProject>>('/bidding/tender-project/page', { params });
}
export function getTenderProject(id: number | string) {
  return requestClient.get<ProjectApi.TenderProject>('/bidding/tender-project/get', { params: { id } });
}
export function getTenderProjectByCode(tenderProjectCode: string) {
  return requestClient.get<ProjectApi.TenderProject | null>('/bidding/tender-project/get-by-code', { params: { tenderProjectCode } });
}
export function saveTenderProject(data: ProjectApi.TenderProject) {
  return requestClient.post<number>('/bidding/tender-project/save', data);
}
export function deleteTenderProject(id: number | string) {
  return requestClient.post<boolean>('/bidding/tender-project/delete', undefined, { params: { id } });
}
export function traceTenderProject(tenderProjectCode: string) {
  return requestClient.get<ProjectApi.Trace>('/bidding/tender-project/trace', { params: { tenderProjectCode } });
}

/* ================= 标段（包） ================= */
export function getBidSectionPage(params: ProjectApi.BidSectionPageParams) {
  return requestClient.get<PageResult<ProjectApi.BidSection>>('/bidding/bid-section/page', { params });
}
export function getBidSection(id: number | string) {
  return requestClient.get<ProjectApi.BidSection>('/bidding/bid-section/get', { params: { id } });
}
export function getBidSectionByCode(bidSectionCode: string) {
  return requestClient.get<ProjectApi.BidSection | null>('/bidding/bid-section/get-by-code', { params: { bidSectionCode } });
}
export function saveBidSection(data: ProjectApi.BidSection) {
  return requestClient.post<number>('/bidding/bid-section/save', data);
}
/** 标段维度全景追溯（标段 → 招标项目 → 项目 + 公告/外部公告/备案/关注/主体/统计）
 *  L3 进入标段后的「公告列表」即取此接口的 notices，无需按域多次请求。 */
export function traceBidSection(bidSectionCode: string) {
  return requestClient.get<ProjectApi.Trace>('/bidding/bid-section/trace', { params: { bidSectionCode } });
}
