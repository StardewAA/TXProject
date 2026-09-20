import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmRecruitDemandApi {
  /** 用工需求单 */
  export interface RecruitDemand {
    id?: number;
    demandNo?: string;
    demandName?: string;
    deptId?: number;
    deptName?: string;
    companyProfileId?: number;
    companyProfileName?: string;
    workType?: string;
    certRequire?: string;
    headcount?: number;
    onboardCount?: number;
    gapCount?: number;
    planEntryDate?: string;
    expectSalary?: string;
    urgency?: number;
    urgencyName?: string;
    status?: number;
    statusName?: string;
    applicant?: string;
    contactMobile?: string;
    remark?: string;
    createTime?: string;
  }
}

/** 需求状态（对齐后端 HrmRecruitDemandStatusEnum） */
export const HrmRecruitDemandStatus = {
  PENDING: 1,
  RECRUITING: 2,
  FINISHED: 3,
  CANCELED: 4,
} as const;

export const HrmRecruitDemandStatusOptions = [
  { label: '待招聘', value: HrmRecruitDemandStatus.PENDING },
  { label: '招聘中', value: HrmRecruitDemandStatus.RECRUITING },
  { label: '已完成', value: HrmRecruitDemandStatus.FINISHED },
  { label: '已取消', value: HrmRecruitDemandStatus.CANCELED },
] as const;

/** 紧急程度（对齐后端 HrmRecruitDemandUrgencyEnum） */
export const HrmRecruitDemandUrgencyOptions = [
  { label: '普通', value: 1 },
  { label: '紧急', value: 2 },
  { label: '特急', value: 3 },
] as const;

/** 用工需求分页 */
export function getRecruitDemandPage(
  params: PageParam & {
    demandName?: string;
    demandNo?: string;
    deptId?: number;
    status?: number;
    urgency?: number;
    workType?: string;
  },
) {
  return requestClient.get<PageResult<HrmRecruitDemandApi.RecruitDemand>>(
    '/hrm/recruit/demand/page',
    { params },
  );
}

/** 用工需求详情 */
export function getRecruitDemand(id: number) {
  return requestClient.get<HrmRecruitDemandApi.RecruitDemand>(
    '/hrm/recruit/demand/get',
    { params: { id } },
  );
}

/** 可招聘需求精简列表（待招聘 / 招聘中） */
export function getRecruitingDemandSimpleList() {
  return requestClient.get<HrmRecruitDemandApi.RecruitDemand[]>(
    '/hrm/recruit/demand/simple-list',
  );
}

export function createRecruitDemand(data: HrmRecruitDemandApi.RecruitDemand) {
  return requestClient.post<number>('/hrm/recruit/demand/create', data);
}

export function updateRecruitDemand(data: HrmRecruitDemandApi.RecruitDemand) {
  return requestClient.put<boolean>('/hrm/recruit/demand/update', data);
}

export function updateRecruitDemandStatus(id: number, status: number) {
  return requestClient.put<boolean>(
    '/hrm/recruit/demand/update-status',
    undefined,
    { params: { id, status } },
  );
}

export function deleteRecruitDemand(id: number) {
  return requestClient.delete<boolean>('/hrm/recruit/demand/delete', {
    params: { id },
  });
}
