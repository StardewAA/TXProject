import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace SubjectApi {
  export type SubjectTypeCode = 'BIDDER' | 'TENDERER';
  export const SUBJECT_TYPE_LABEL: Record<string, string> = {
    BIDDER: '投标人',
    TENDERER: '招标人',
  };

  /** 主体档案（SubjectProfileRespVO） */
  export interface Profile {
    id?: number | string;
    subjectType?: string;
    subjectName?: string;
    uscc?: string;
    legalPersonName?: string;
    legalPersonTypeCode?: string;
    regionCode?: string;
    industryCode?: string;
    regCapital?: number;
    regCapCurrency?: string;
    contactName?: string;
    contactPhone?: string;
    licenseFileId?: number | string;
    licenseFileName?: string;
    extJson?: string;
    complete?: boolean;
    missingRequiredCodes?: string[];
    missingRequiredNames?: string[];
    completeTime?: number;
    createTime?: number;
    updateTime?: number;
  }

  /** 资格（SubjectEligibilityRespVO） */
  export interface Eligibility {
    tenantId?: number | string;
    tenantName?: string;
    subjectType?: string;
    authorized?: boolean;
    authStatus?: 'ENABLED' | 'DISABLED' | 'NOT_AUTHORIZED';
    profileComplete?: boolean;
    eligible?: boolean;
    reasonCodes?: string[];
    missingRequiredNames?: string[];
    authorizeTime?: number;
    disableTime?: number;
    message?: string;
  }

  /** 授权行（SubjectAuthRespVO） */
  export interface AuthRow {
    id?: number | string;
    tenantId?: number | string;
    tenantName?: string;
    subjectType?: string;
    authStatus?: 'ENABLED' | 'DISABLED';
    profileExists?: boolean;
    profileComplete?: boolean;
    missingRequiredCodes?: string[];
    missingRequiredNames?: string[];
    authorizeTime?: number;
    disableTime?: number;
    remark?: string;
    createTime?: number;
  }

  export interface AuthPageParams extends PageParam {
    tenantId?: number | string;
    subjectType?: string;
    authStatus?: string;
    tenantName?: string;
  }

  export interface AuthSave {
    tenantId: number | string;
    tenantName?: string;
    subjectType: string;
    remark?: string;
  }

  /** 经办用户（SubjectUserRespVO） */
  export interface UserRow {
    id?: number | string;
    tenantId?: number | string;
    subjectType?: string;
    userId?: number | string;
    userNickname?: string;
    userRole?: 'OPERATOR' | 'CONTACT';
    enabled?: boolean;
    remark?: string;
    createTime?: number;
  }

  export interface UserPageParams extends PageParam {
    tenantId: number | string;
    subjectType?: string;
    userRole?: string;
    enabled?: boolean;
  }

  export interface UserBind {
    tenantId: number | string;
    subjectType: string;
    userId: number | string;
    userRole?: string;
    remark?: string;
  }
}

// ===== 企业侧档案自维护（subject/profile）=====
export function fetchSubjectProfile(subjectType: string) {
  return requestClient.get<SubjectApi.Profile>('/bidding/subject/profile/get', { params: { subjectType } });
}
export function saveSubjectProfile(data: Record<string, unknown>) {
  return requestClient.post<number | string>('/bidding/subject/profile/save', data);
}
export function fetchSubjectEligibility(subjectType: string) {
  return requestClient.get<SubjectApi.Eligibility>('/bidding/subject/profile/eligibility', { params: { subjectType } });
}

// ===== 平台侧授权管理（subject-auth）=====
export function fetchSubjectAuthPage(params: SubjectApi.AuthPageParams) {
  return requestClient.get<PageResult<SubjectApi.AuthRow>>('/bidding/subject-auth/page', { params });
}
export function authorizeSubject(data: SubjectApi.AuthSave) {
  return requestClient.post<number | string>('/bidding/subject-auth/authorize', data);
}
export function updateSubjectAuthStatus(id: number | string, authStatus: string) {
  return requestClient.put<boolean>('/bidding/subject-auth/status', undefined, { params: { id, authStatus } });
}
export function fetchSubjectAuthEligibility(tenantId: number | string, subjectType: string) {
  return requestClient.get<SubjectApi.Eligibility>('/bidding/subject-auth/eligibility', { params: { tenantId, subjectType } });
}
export function fetchSubjectUserPage(params: SubjectApi.UserPageParams) {
  return requestClient.get<PageResult<SubjectApi.UserRow>>('/bidding/subject-auth/user/page', { params });
}
export function bindSubjectUser(data: SubjectApi.UserBind) {
  return requestClient.post<number | string>('/bidding/subject-auth/user/bind', data);
}
export function unbindSubjectUser(id: number | string) {
  return requestClient.delete<boolean>('/bidding/subject-auth/user/unbind', { params: { id } });
}
