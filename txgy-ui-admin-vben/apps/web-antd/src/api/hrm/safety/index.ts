import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 三级安全教育级别（对齐后端 HrmSafetyEducationLevelEnum） */
export const HrmSafetyEducationLevel = {
  COMPANY: 1, // 公司级
  PROJECT: 2, // 项目级
  TEAM: 3, // 班组级
} as const;

export const HrmSafetyEducationLevelOptions = [
  { label: '公司级', value: HrmSafetyEducationLevel.COMPANY },
  { label: '项目级', value: HrmSafetyEducationLevel.PROJECT },
  { label: '班组级', value: HrmSafetyEducationLevel.TEAM },
] as const;

/** 体检结论（对齐后端 HrmHealthCheckResultEnum） */
export const HrmHealthCheckResult = {
  QUALIFIED: 1, // 合格
  UNQUALIFIED: 2, // 不合格
  RECHECK: 3, // 需复查
} as const;

export const HrmHealthCheckResultOptions = [
  { label: '合格', value: HrmHealthCheckResult.QUALIFIED },
  { label: '不合格', value: HrmHealthCheckResult.UNQUALIFIED },
  { label: '需复查', value: HrmHealthCheckResult.RECHECK },
] as const;

/** 体检类型 */
export const HrmHealthCheckTypeOptions = [
  { label: '入职', value: 1 },
  { label: '在岗', value: 2 },
  { label: '离职', value: 3 },
  { label: '职业健康', value: 4 },
] as const;

export namespace HrmSafetyApi {
  /** 三级安全教育记录 */
  export interface SafetyEducation {
    id?: number;
    employeeId?: number;
    employeeName?: string;
    jobNumber?: string;
    level?: number;
    levelName?: string;
    educationDate?: string;
    educatorName?: string;
    hours?: number;
    content?: string;
    examScore?: number;
    qualified?: boolean;
    fileUrls?: string[];
    remark?: string;
  }

  /** 体检记录 */
  export interface HealthCheck {
    id?: number;
    employeeId?: number;
    employeeName?: string;
    jobNumber?: string;
    checkType?: number;
    checkTypeName?: string;
    checkDate?: string;
    institution?: string;
    result?: number;
    resultName?: string;
    recheckDate?: string;
    validUntil?: string;
    valid?: boolean;
    fileUrls?: string[];
    remark?: string;
  }

}

/** 安全教育分页 */
export function getSafetyEducationPage(
  params: PageParam & {
    employeeId?: number;
    level?: number;
    qualified?: boolean;
  },
) {
  return requestClient.get<PageResult<HrmSafetyApi.SafetyEducation>>(
    '/hrm/safety-education/page',
    { params },
  );
}

export function createSafetyEducation(data: HrmSafetyApi.SafetyEducation) {
  return requestClient.post<number>('/hrm/safety-education/create', data);
}

export function updateSafetyEducation(data: HrmSafetyApi.SafetyEducation) {
  return requestClient.put<boolean>('/hrm/safety-education/update', data);
}

export function deleteSafetyEducation(id: number) {
  return requestClient.delete<boolean>('/hrm/safety-education/delete', {
    params: { id },
  });
}

/** 体检记录分页 */
export function getHealthCheckPage(
  params: PageParam & {
    checkType?: number;
    employeeId?: number;
    result?: number;
  },
) {
  return requestClient.get<PageResult<HrmSafetyApi.HealthCheck>>(
    '/hrm/health-check/page',
    { params },
  );
}

export function createHealthCheck(data: HrmSafetyApi.HealthCheck) {
  return requestClient.post<number>('/hrm/health-check/create', data);
}

export function updateHealthCheck(data: HrmSafetyApi.HealthCheck) {
  return requestClient.put<boolean>('/hrm/health-check/update', data);
}

export function deleteHealthCheck(id: number) {
  return requestClient.delete<boolean>('/hrm/health-check/delete', {
    params: { id },
  });
}
