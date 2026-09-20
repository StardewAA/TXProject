import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmEmployeeBlacklistApi {
  /** 黑名单记录 */
  export interface Blacklist {
    id?: number;
    employeeId?: number;
    jobNumber?: string;
    name?: string;
    mobile?: string;
    idCardNo?: string;
    reason?: string;
    source?: number;
    sourceName?: string;
    joinTime?: string;
    releaseTime?: string;
    permanentFlag?: boolean;
    status?: number;
    statusName?: string;
    remark?: string;
    createTime?: string;
  }
}

export const HrmBlacklistSourceOptions = [
  { label: 'HRM 手工登记', value: 1 },
  { label: '现场上报', value: 2 },
  { label: '其他', value: 3 },
] as const;

export const HrmBlacklistStatusOptions = [
  { label: '生效中', value: 1 },
  { label: '已解除', value: 2 },
] as const;

/** 黑名单分页 */
export function getEmployeeBlacklistPage(
  params: PageParam & {
    employeeId?: number;
    idCardNo?: string;
    mobile?: string;
    name?: string;
    source?: number;
    status?: number;
  },
) {
  return requestClient.get<PageResult<HrmEmployeeBlacklistApi.Blacklist>>(
    '/hrm/employee/blacklist/page',
    { params },
  );
}

export function getEmployeeBlacklist(id: number) {
  return requestClient.get<HrmEmployeeBlacklistApi.Blacklist>(
    '/hrm/employee/blacklist/get',
    { params: { id } },
  );
}

export function createEmployeeBlacklist(
  data: HrmEmployeeBlacklistApi.Blacklist,
) {
  return requestClient.post<number>('/hrm/employee/blacklist/create', data);
}

export function updateEmployeeBlacklist(
  data: HrmEmployeeBlacklistApi.Blacklist,
) {
  return requestClient.put<boolean>('/hrm/employee/blacklist/update', data);
}

/** 解除黑名单 */
export function releaseEmployeeBlacklist(id: number) {
  return requestClient.put<boolean>('/hrm/employee/blacklist/release', undefined, {
    params: { id },
  });
}

export function deleteEmployeeBlacklist(id: number) {
  return requestClient.delete<boolean>('/hrm/employee/blacklist/delete', {
    params: { id },
  });
}
